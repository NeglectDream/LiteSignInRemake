package studio.trc.bukkit.litesignin.util;

import org.bukkit.Bukkit;

import studio.trc.bukkit.litesignin.Main;

/**
 * Thin wrapper over the Bukkit scheduler.
 */
public class BukkitSchedulerManager
{
    /**
     * Run a task on the main server thread, either immediately or after the
     * given delay (in ticks).
     *
     * @param task the task to run
     * @param delay delay in ticks; {@code 0} runs on the next tick
     */
    public static void runBukkitTask(Runnable task, long delay) {
        if (delay <= 0) {
            Bukkit.getScheduler().runTask(Main.getInstance(), task);
        } else {
            Bukkit.getScheduler().runTaskLater(Main.getInstance(), task, delay);
        }
    }
}
