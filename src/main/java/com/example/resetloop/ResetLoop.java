package com.example.resetloop;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class ResetLoop extends JavaPlugin implements Listener {

    // 15 minutes in seconds. Change if you want a different interval.
    private int resetIntervalSeconds = 15 * 60;
    private int timerSeconds = resetIntervalSeconds;

    // We capture the current world's seed on enable and reuse it forever.
    private long fixedSeed;
    private String baseName;

    private BossBar bossBar;

    @Override
    public void onEnable() {
        // Detect the actual base world and seed
        World baseWorld = Bukkit.getWorlds().get(0);
        baseName = baseWorld.getName();           // e.g., "world"
        fixedSeed = baseWorld.getSeed();          // keep the same seed every reset

        // BossBar setup
        bossBar = Bukkit.createBossBar(formatBossTitle(timerSeconds), BarColor.RED, BarStyle.SOLID);
        bossBar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        Bukkit.getPluginManager().registerEvents(this, this);

        // Countdown task (updates once per second)
        new BukkitRunnable() {
            @Override public void run() {
                timerSeconds--;

                // broadcast each minute & last 10 seconds
                if (timerSeconds % 60 == 0 || timerSeconds <= 10) {
                    Bukkit.broadcastMessage("§cWorld resets in " + timerSeconds + "s!");
                }

                // update bossbar
                bossBar.setTitle(formatBossTitle(timerSeconds));
                bossBar.setProgress(Math.max(0, Math.min(1, (double) timerSeconds / resetIntervalSeconds)));

                if (timerSeconds <= 0) {
                    resetWorld();
                    timerSeconds = resetIntervalSeconds;
                }
            }
        }.runTaskTimer(this, 20L, 20L); // start after 1s, run every 1s

        getLogger().info("[ResetLoop] Enabled. Base world=" + baseName + ", seed=" + fixedSeed);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Attach BossBar and (optionally) ensure clean advancements on join
        bossBar.addPlayer(e.getPlayer());
        // If you also want to clear advancements on every join, uncomment:
        // clearAdvancements(e.getPlayer());
    }

    @Override
    public void onDisable() {
        if (bossBar != null) bossBar.removeAll();
    }

    // -------- Commands --------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("resetnow")) {
            resetWorld();
            timerSeconds = resetIntervalSeconds;
            return true;
        }
        return false;
        // (You can add /resetloop setseed <seed> etc. later if you want.)
    }

    // -------- Core reset logic (robust, resets OW + Nether + End) --------
    private void resetWorld() {
        Bukkit.broadcastMessage("§4§lResetting world...");

        // Kick all players to avoid corruption during unload/delete
        for (World w : Bukkit.getWorlds()) {
            for (Player p : w.getPlayers()) {
                p.kickPlayer("§cWorld is resetting...");
            }
        }

        // Unload order: End -> Nether -> Overworld
        World end = Bukkit.getWorld(baseName + "_the_end");
        World nether = Bukkit.getWorld(baseName + "_nether");
        World base = Bukkit.getWorld(baseName);

        if (end != null)    Bukkit.unloadWorld(end, false);
        if (nether != null) Bukkit.unloadWorld(nether, false);
        if (base != null)   Bukkit.unloadWorld(base, false);

        // Build absolute paths from the world container (server root by default)
        File container = Bukkit.getWorldContainer();
        Path owPath = new File(container, baseName).toPath();
        Path nePath = new File(container, baseName + "_nether").toPath();
        Path enPath = new File(container, baseName + "_the_end").toPath();

        // Delay to ensure file locks (session.lock) are released on Windows
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean owDeleted = deleteTree(owPath);
            boolean neDeleted = deleteTree(nePath);
            boolean enDeleted = deleteTree(enPath);

            getLogger().info("[ResetLoop] Delete results: overworld=" + owDeleted + " nether=" + neDeleted + " end=" + enDeleted);

            // If overworld failed to delete, fall back to a clean server restart
            if (!owDeleted) {
                getLogger().severe("[ResetLoop] Overworld folder could not be deleted. Restarting server for a clean reset.");
                Bukkit.shutdown();
                return;
            }

            // Recreate all three worlds with the same seed
            Bukkit.createWorld(new WorldCreator(baseName)
                    .environment(Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .seed(fixedSeed));

            Bukkit.createWorld(new WorldCreator(baseName + "_nether")
                    .environment(Environment.NETHER)
                    .seed(fixedSeed));

            Bukkit.createWorld(new WorldCreator(baseName + "_the_end")
                    .environment(Environment.THE_END)
                    .seed(fixedSeed));

            // Clear advancements for any online players (after reconnects)
            Bukkit.getOnlinePlayers().forEach(this::clearAdvancements);

            Bukkit.broadcastMessage("§aWorld has been reset!");
            getLogger().info("[ResetLoop] World reset complete. Base=" + baseName + ", seed=" + fixedSeed);
        }, 60L); // 3 seconds
    }

    // Robust recursive delete using NIO; returns true if path no longer exists.
    private boolean deleteTree(Path root) {
        try {
            if (!Files.exists(root)) return true;
            Files.walk(root)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (Exception e) { getLogger().warning("[ResetLoop] Delete failed: " + p + " -> " + e.getMessage()); }
                    });
            return !Files.exists(root);
        } catch (Exception ex) {
            getLogger().warning("[ResetLoop] NIO delete error for " + root + " -> " + ex.getMessage());
            return false;
        }
    }

    private void clearAdvancements(Player p) {
        Bukkit.getServer().advancementIterator().forEachRemaining(adv -> {
            var prog = p.getAdvancementProgress(adv);
            for (String c : prog.getAwardedCriteria()) {
                try { prog.revokeCriteria(c); } catch (Exception ignored) {}
            }
        });
    }

    private String formatBossTitle(int secs) {
        int m = Math.max(0, secs) / 60;
        int s = Math.max(0, secs) % 60;
        return "§cReset in §f" + String.format("%02d:%02d", m, s);
    }
}
