package studio.trc.bukkit.litesignin.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Getter;
import lombok.Setter;

import studio.trc.bukkit.litesignin.configuration.ConfigurationType;
import studio.trc.bukkit.litesignin.configuration.ConfigurationUtil;
import studio.trc.bukkit.litesignin.message.MessageUtil;

public class LiteSignInThread
    extends Thread
{
    @Getter
    private static LiteSignInThread taskThread = null;
    
    @Getter
    @Setter
    private boolean running = false;
    @Getter
    private final List<LiteSignInTask> tasks = new CopyOnWriteArrayList<>();
    
    @Getter
    private final double delay;
    
    public LiteSignInThread(String name, double delay) {
        super(name);
        this.delay = delay;
    }

    @Override
    public void run() {
        running = true;
        List<LiteSignInTask> waitToExecute = new ArrayList<>();
        List<LiteSignInTask> waitToRemove = new ArrayList<>();
        while (running) {
            try {
                long usedTime = System.currentTimeMillis();
                if (!tasks.isEmpty()) {
                    waitToExecute.addAll(tasks);
                    waitToExecute.stream().filter(task -> {
                        if (task.getTotalExecuteTimes() != -1 && task.getExecuteTimes() >= task.getTotalExecuteTimes()) {
                            waitToRemove.add(task);
                            return false;
                        }
                        return true;
                    }).forEach(task -> {
                        try {
                            task.run();
                        } catch (Throwable t) {
                            t.printStackTrace();
                            waitToRemove.add(task);
                        }
                    });
                    if (!waitToRemove.isEmpty()) {
                        waitToRemove.stream().forEach(tasks::remove);
                        waitToRemove.clear();
                    }
                    if (!waitToExecute.isEmpty()) waitToExecute.clear();
                }
                long speed = ((long) (delay * 1000)) - (System.currentTimeMillis() - usedTime);
                if (speed >= 0) sleep(speed);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    public static void initialize() {
        if (taskThread != null && taskThread.running) {
            taskThread.running = false;
        }
        taskThread = new LiteSignInThread("LiteSignIn-TaskThread", ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getDouble("Async-Thread-Settings.Task-Thread-Delay"));

        MessageUtil.sendConsoleMessage("Console-Messages.Async-Thread-Started", ConfigurationType.MESSAGES, MessageUtil.getDefaultPlaceholders());
        taskThread.start();
    }
    
    public static void runTask(Runnable task) {
        taskThread.tasks.add(new LiteSignInTask(task, 1, 0));
    }
    
    public static void runTask(Runnable task, double second) {
        taskThread.tasks.add(new LiteSignInTask(task, 1, (long) (1D / ConfigurationUtil.getConfig(ConfigurationType.CONFIG).getDouble("Async-Thread-Settings.Task-Thread-Delay") * second)));
    }
}
