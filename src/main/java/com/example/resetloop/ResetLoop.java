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
        // Kick all players before reset
        for (Player p : oldWorld.getPlayers()) {
            p.kickPlayer("§cWorld is resetting...");
        }

        // Unload the world
        Bukkit.unloadWorld(oldWorld, false);

        // Run deletion & recreation on the next tick
        Bukkit.getScheduler().runTaskLater(this, () -> {
            File worldFolder = oldWorld.getWorldFolder();

            // Fully delete the folder (with subdirectories)
            deleteFolder(worldFolder);

            // Now create new world with the same seed
            WorldCreator wc = new WorldCreator("world");
            wc.seed(fixedSeed);
            wc.environment(Environment.NORMAL);
            wc.type(WorldType.NORMAL);
            Bukkit.createWorld(wc);

            Bukkit.broadcastMessage("§aWorld has been reset!");
        }, 40L); // wait 2 seconds just to be safe
    }
}


private void deleteFolder(File folder) {
    if (folder.exists()) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
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

