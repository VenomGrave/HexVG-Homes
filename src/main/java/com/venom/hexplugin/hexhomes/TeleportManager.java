package com.venom.hexplugin.hexhomes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {

    private final HexHomes plugin;

    private final Map<UUID, BukkitTask> warmups = new HashMap<UUID, BukkitTask>();
    private final Map<UUID, Location> warmupStartLoc = new HashMap<UUID, Location>();
    private final Map<UUID, Long> lastCombat = new HashMap<UUID, Long>();

    public TeleportManager(HexHomes plugin) {
        this.plugin = plugin;
    }

    /* ===== combat / warmup ===== */

    public void tagCombat(UUID id) {
        lastCombat.put(id, System.currentTimeMillis());
    }

    public boolean isInWarmup(UUID id) {
        return warmups.containsKey(id);
    }

    public void cancelWarmup(Player p, String reasonMsgKey) {
        BukkitTask t = warmups.remove(p.getUniqueId());
        warmupStartLoc.remove(p.getUniqueId());
        if (t != null) t.cancel();
        if (reasonMsgKey != null) p.sendMessage(plugin.msg(reasonMsgKey));
    }

    public boolean movedPastThreshold(Player p) {
        Location start = warmupStartLoc.get(p.getUniqueId());
        if (start == null) return false;
        double thr = plugin.getConfig().getDouble("teleport.move_threshold", 0.12d);
        Vector d = p.getLocation().toVector().subtract(start.toVector());
        return d.length() > thr;
    }

    /* ===== główna ścieżka TP ===== */

    public void requestTeleport(final Player p, final Location target) {
        // blokada rozpoczęcia teleportu po obrażeniach
        boolean blockAfterDmg = plugin.getConfig().getBoolean(
                "teleport.block_after_damage.enabled",
                plugin.getConfig().getBoolean("teleport.cancel_on_combat_tag", true)
        );
        int combatSec = plugin.getConfig().getInt(
                "teleport.block_after_damage.seconds",
                plugin.getConfig().getInt("teleport.combat_tag_seconds", 10)
        );

        if (blockAfterDmg && !p.hasPermission("homesgui.bypass.combat")) {
            long lastHit = lastCombat.getOrDefault(p.getUniqueId(), 0L);
            if (System.currentTimeMillis() - lastHit < combatSec * 1000L) {
                p.sendMessage(plugin.msg("cancelled_combat"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                return;
            }
        }

        if (isInWarmup(p.getUniqueId())) cancelWarmup(p, null);

        int warm = Math.max(0, plugin.getConfig().getInt("teleport.warmup_seconds", 3));
        if (warm == 0) { performTeleport(p, target); return; }

        warmupStartLoc.put(p.getUniqueId(), p.getLocation().clone());
        final int[] left = new int[]{ warm };

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancelWarmup(p, null); return; }

                String sub = plugin.getConfig()
                        .getString("messages.countdown_subtitle", "&fTeleportowanie do &7domu &fza {s}s")
                        .replace("{s}", String.valueOf(left[0]));
                p.sendTitle(" ", plugin.cc(sub), 0, 20, 0);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);

                left[0]--;
                if (left[0] < 0) {
                    cancelWarmup(p, null);
                    performTeleport(p, target);
                }
            }
        }, 0L, 20L);

        warmups.put(p.getUniqueId(), task);
    }

    private void performTeleport(Player p, Location targetRaw) {
        if (targetRaw == null || targetRaw.getWorld() == null) {
            p.sendMessage(plugin.msg("no_target"));
            return;
        }

        // restrykcje świat/Y
        if (!p.hasPermission("homesgui.bypass.restrictions")) {
            if (!plugin.worldAllowed(targetRaw.getWorld().getName())) {
                p.sendMessage(plugin.msg("world_blocked"));
                return;
            }
            World.Environment env = targetRaw.getWorld().getEnvironment();
            if (!plugin.yAllowed(targetRaw.getBlockY(), env == World.Environment.NETHER)) {
                p.sendMessage(plugin.msg("y_blocked"));
                return;
            }
        }

        // teleportujemy dokładnie w zapisane koordy
        p.teleport(targetRaw, PlayerTeleportEvent.TeleportCause.PLUGIN);

        String sub = plugin.getConfig().getString("messages.teleported_subtitle", "&aTeleportowano do &7domu");
        p.sendTitle(" ", plugin.cc(sub), 0, 30, 10);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }
}
