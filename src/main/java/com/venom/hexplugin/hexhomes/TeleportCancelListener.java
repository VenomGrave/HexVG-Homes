package com.venom.hexplugin.hexhomes;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TeleportCancelListener implements Listener {

    private final HexHomes plugin;
    private final TeleportManager tp;

    public TeleportCancelListener(HexHomes plugin, TeleportManager tp) {
        this.plugin = plugin;
        this.tp = tp;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getConfig().getBoolean("teleport.cancel_on_move", true)) return;
        Player p = e.getPlayer();
        if (!tp.isInWarmup(p.getUniqueId())) return;

        if (tp.movedPastThreshold(p)) {
            tp.cancelWarmup(p, "cancelled_move");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        // blokada startu TP
        tp.tagCombat(p.getUniqueId());

        // przerwij trwający warmup
        if (!plugin.getConfig().getBoolean("teleport.cancel_on_damage", true)) return;
        if (tp.isInWarmup(p.getUniqueId())) {
            tp.cancelWarmup(p, "cancelled_damage");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            tp.tagCombat(((Player) e.getDamager()).getUniqueId());
        }
        if (e.getEntity() instanceof Player) {
            tp.tagCombat(((Player) e.getEntity()).getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (tp.isInWarmup(e.getPlayer().getUniqueId())) {
            tp.cancelWarmup(e.getPlayer(), null); // bez wiadomości przy wyjściu
        }
    }
}
