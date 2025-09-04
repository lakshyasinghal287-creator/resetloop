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

    // Detect actual base world (index 0)
    World baseWorld = Bukkit.getWorlds().get(0);
    String baseName = baseWorld.getName(); // should be "world"

    // Kick all players out safely
    for (World w : Bukkit.getWorlds()) {
        for (Player p : w.getPlayers()) {
            p.kickPlayer("§cWorld is resetting...");
        }
    }

    // Unload worlds in safe order
    World end = Bukkit.getWorld(baseName + "_the_end");
    World nether = Bukkit.getWorld(baseName + "_nether");
    if (end != null) Bukkit.unloadWorld(end, false);
    if (nether != null) Bukkit.unloadWorld(nether, false);
    Bukkit.unloadWorld(baseWorld, false);

    // Resolve absolute folders from the server's world container
    File container = Bukkit.getWorldContainer(); // usually the server root
    File overworldDir = new File(container, baseName);
    File netherDir = new File(container, baseName + "_nether");
    File endDir    = new File(container, baseName + "_the_end");

    // Delete & recreate after a short delay to release file locks
    Bukkit.getScheduler().runTaskLater(this, () -> {
        boolean owOk = deleteFolderNio(overworldDir.toPath());
        boolean neOk = deleteFolderNio(netherDir.toPath());
        boolean enOk = deleteFolderNio(endDir.toPath());

        getLogger().info("[ResetLoop] Deleted: OW=" + owOk + " NE=" + neOk + " EN=" + enOk);

        // If deletion failed, fallback to a safe full restart (optional – comment out if you don't want it)
        if (!owOk) {
            getLogger().severe("[ResetLoop] Overworld delete failed; falling back to server restart for a clean reset.");
            // Optionally write a marker file so your start script can see it
            // try { Files.writeString(new File(container, "RESET_MARKER.txt").toPath(), "reset"); } catch (Exception ignored) {}
            Bukkit.shutdown();
            return;
        }

        // Recreate worlds with fixed seed
        Bukkit.createWorld(new WorldCreator(baseName)
                .environment(World.Environment.NORMAL)
                .type(WorldType.NORMAL)
                .seed(fixedSeed));

        Bukkit.createWorld(new WorldCreator(baseName + "_nether")
                .environment(World.Environment.NETHER)
                .seed(fixedSeed));

        Bukkit.createWorld(new WorldCreator(baseName + "_the_end")
                .environment(World.Environment.THE_END)
                .seed(fixedSeed));

        // (Advancements clear if anyone online after reconnect)
        for (Player p : Bukkit.getOnlinePlayers()) {
            Bukkit.advancementIterator().forEachRemaining(adv ->
                    p.getAdvancementProgress(adv).getAwardedCriteria()
                            .forEach(c -> p.getAdvancementProgress(adv).revokeCriteria(c)));
        }

        Bukkit.broadcastMessage("§aWorld has been reset!");
        getLogger().info("[ResetLoop] World reset complete.");
    }, 60L); // 3 seconds
}



// Robust recursive delete using NIO; returns true if the directory no longer exists
private boolean deleteFolderNio(java.nio.file.Path root) {
    try {
        if (!java.nio.file.Files.exists(root)) return true;
        // walk file tree and delete children before parents
        java.nio.file.Files.walk(root)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        java.nio.file.Files.deleteIfExists(p);
                    } catch (Exception e) {
                        getLogger().warning("[ResetLoop] Failed to delete: " + p + " -> " + e.getMessage());
                    }
                });
        return !java.nio.file.Files.exists(root);
    } catch (Exception ex) {
        getLogger().warning("[ResetLoop] NIO delete error for " + root + " -> " + ex.getMessage());
        return false;
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

