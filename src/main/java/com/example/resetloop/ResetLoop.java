package com.example.resetloop;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;

public class ResetLoop extends JavaPlugin {

    private int resetInterval = 15 * 60; // 15 min in seconds
    private int timer = resetInterval;
    private long fixedSeed = 123456789L; // your world seed

    @Override
    public void onEnable() {
        getLogger().info("ResetLoop plugin enabled for 1.21.1!");

        // Start countdown timer
        new BukkitRunnable() {
            @Override
            public void run() {
                timer--;

                // Notify every minute + last 10 seconds
                if (timer % 60 == 0 || timer <= 10) {
                    Bukkit.broadcastMessage("§cWorld resets in " + timer + "s!");
                }

                if (timer <= 0) {
                    resetWorld();
                    timer = resetInterval;
                }
            }
        }.runTaskTimer(this, 20, 20); // runs every second
    }

    private void resetWorld() {
        Bukkit.broadcastMessage("§4§lResetting world...");

        World oldWorld = Bukkit.getWorld("world");
        if (oldWorld != null) {
            // Kick players to avoid corruption
            for (Player p : oldWorld.getPlayers()) {
                p.kickPlayer("§cWorld is resetting...");
            }

            // Unload the world first
Bukkit.unloadWorld(oldWorld, false);

// Run deletion & recreation on the next tick (to avoid session.lock errors)
Bukkit.getScheduler().runTaskLater(this, () -> {
    deleteFolder(oldWorld.getWorldFolder());

    WorldCreator wc = new WorldCreator("world");
    wc.seed(fixedSeed);
    wc.environment(Environment.NORMAL);
    wc.type(WorldType.NORMAL);
    World newWorld = Bukkit.createWorld(wc);

    Bukkit.broadcastMessage("§aWorld has been reset!");
}, 20L); // wait 1 second


        // Reset advancements
    for (Player p : Bukkit.getOnlinePlayers()) {
    Bukkit.advancementIterator().forEachRemaining(adv ->
            p.getAdvancementProgress(adv).getAwardedCriteria()
                    .forEach(c -> p.getAdvancementProgress(adv).revokeCriteria(c))
    );
}

        Bukkit.broadcastMessage("§aWorld has been reset!");
    }

    private void deleteFolder(File folder) {
        if (folder.exists()) {
            for (File file : folder.listFiles()) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
            folder.delete();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("resetnow")) {
            resetWorld();
            return true;
        }
        return false;
    }
}

