package studio.trc.bukkit.litesignin.thread;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;

import studio.trc.bukkit.litesignin.Main;
import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.message.MessageUtil;

/**
 * Single-thread asynchronous IO executor used by LiteSignIn.
 *
 * <p>The historical class name is retained for source compatibility, but the
 * polling thread and mutable task list have been replaced by a scheduled
 * executor with explicit startup and shutdown semantics.</p>
 */
public final class LiteSignInThread {
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private static volatile ScheduledThreadPoolExecutor executor;

    private LiteSignInThread() {}

    public static void initialize() {
        shutdownAndAwait(10, TimeUnit.SECONDS);
        synchronized (LIFECYCLE_LOCK) {
            ScheduledThreadPoolExecutor created = new ScheduledThreadPoolExecutor(1, new TaskThreadFactory());
            created.setRemoveOnCancelPolicy(true);
            created.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            created.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
            executor = created;
        }
        MessageUtil.sendConsoleMessage("Console-Messages.Async-Thread-Started", ConfigurationType.MESSAGES,
                MessageUtil.getDefaultPlaceholders());
    }

    public static boolean isRunning() {
        ScheduledThreadPoolExecutor current = executor;
        return current != null && !current.isShutdown();
    }

    public static void runTask(Runnable task) {
        tryRunTask(task);
    }

    public static boolean tryRunTask(Runnable task) {
        return submit(task, 0L, TimeUnit.MILLISECONDS);
    }

    public static void runTask(Runnable task, double seconds) {
        long delayMillis = Math.max(0L, Math.round(seconds * 1000D));
        submit(task, delayMillis, TimeUnit.MILLISECONDS);
    }

    public static <T> CompletableFuture<T> supplyTask(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!submit(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable error) {
                future.completeExceptionally(error);
                logTaskFailure(error);
            }
        }, 0L, TimeUnit.MILLISECONDS)) {
            future.completeExceptionally(new RejectedExecutionException("LiteSignIn executor is not running"));
        }
        return future;
    }

    /**
     * Stops accepting new tasks and waits for already-running work. Delayed
     * tasks that have not started are cancelled by executor policy.
     */
    public static boolean shutdownAndAwait(long timeout, TimeUnit unit) {
        ScheduledThreadPoolExecutor current;
        synchronized (LIFECYCLE_LOCK) {
            current = executor;
            executor = null;
        }
        if (current == null) {
            return true;
        }

        current.shutdown();
        boolean terminated = awaitTermination(current, timeout, unit);
        if (!terminated) {
            List<Runnable> cancelled = current.shutdownNow();
            if (!cancelled.isEmpty() && Main.getInstance() != null) {
                Main.getInstance().getLogger().warning("Cancelled " + cancelled.size()
                        + " pending LiteSignIn asynchronous task(s) during shutdown.");
            }
            terminated = awaitTermination(current, Math.min(unit.toMillis(timeout), 2000L), TimeUnit.MILLISECONDS);
        }
        return terminated;
    }

    private static boolean submit(Runnable task, long delay, TimeUnit unit) {
        if (task == null) {
            return false;
        }
        ScheduledThreadPoolExecutor current = executor;
        if (current == null || current.isShutdown()) {
            return false;
        }
        try {
            current.schedule(() -> {
                try {
                    task.run();
                } catch (Throwable error) {
                    logTaskFailure(error);
                }
            }, delay, unit);
            return true;
        } catch (RejectedExecutionException ex) {
            return false;
        }
    }

    private static boolean awaitTermination(ScheduledThreadPoolExecutor current, long timeout, TimeUnit unit) {
        try {
            return current.awaitTermination(Math.max(0L, timeout), unit);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void logTaskFailure(Throwable error) {
        Main plugin = Main.getInstance();
        if (plugin != null) {
            plugin.getLogger().log(Level.SEVERE, "LiteSignIn asynchronous task failed", error);
        }
    }

    private static final class TaskThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "LiteSignIn-Async-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, error) -> logTaskFailure(error));
            return thread;
        }
    }
}
