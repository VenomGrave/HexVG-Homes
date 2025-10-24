package com.venom.hexplugin.hexhomes;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class GuiListener implements Listener {

    private final HexHomes plugin;
    private final HomeManager homeManager;
    private final TeleportManager tpManager;

    public GuiListener(HexHomes plugin, HomeManager homeManager, TeleportManager tpManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.tpManager = tpManager;
    }


    private boolean isOurGuiTitle(String title) {
        String stripped = ChatColor.stripColor(title == null ? "" : title);
        String expected = ChatColor.stripColor(plugin.getGuiTitle());
        return expected.equalsIgnoreCase(stripped);
    }

    private boolean isConfirmTitle(String title) {
        String stripped = ChatColor.stripColor(title == null ? "" : title);
        String expected = ChatColor.stripColor(plugin.getConfirmTitle());
        return expected.equalsIgnoreCase(stripped);
    }

    private boolean isOurHomeItem(ItemStack is) {
        if (is == null || is.getType() != Material.RED_BED) return false;
        ItemMeta m = is.getItemMeta();
        if (m == null || !m.hasDisplayName()) return false;
        String a = ChatColor.stripColor(m.getDisplayName());
        String b = ChatColor.stripColor(plugin.getItemName());
        return a.equalsIgnoreCase(b);
    }

    private boolean isNamed(ItemStack is, Material mat, String expectedName) {
        if (is == null || is.getType() != mat) return false;
        if (!is.hasItemMeta() || !is.getItemMeta().hasDisplayName()) return false;
        String a = ChatColor.stripColor(is.getItemMeta().getDisplayName());
        String b = ChatColor.stripColor(expectedName);
        return a.equalsIgnoreCase(b);
    }


    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        String title = e.getView().getTitle();
        if (isOurGuiTitle(title) || isConfirmTitle(title)) e.setCancelled(true);
    }


    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        String title = e.getView().getTitle();

        // --- OKNO POTWIERDZENIA ---
        if (isConfirmTitle(title)) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;

            ItemStack clicked = e.getCurrentItem();
            if (clicked == null) return;

            // Potwierdzenie
            if (isNamed(clicked, Material.GREEN_WOOL, plugin.getConfirmYesName())) {
                Integer idx = plugin.getPendingDelete().remove(p.getUniqueId());
                p.closeInventory();
                if (idx == null) {
                    p.sendMessage(plugin.msg("remove_cancelled"));
                    return;
                }
                if (homeManager.getHome(p.getUniqueId(), idx) == null) {
                    p.sendMessage(plugin.msg("slot_empty"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                    plugin.openHomesGui(p);
                    return;
                }
                homeManager.removeHome(p.getUniqueId(), idx);
                p.sendMessage(plugin.msg("removed_success"));
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.0f);
                plugin.openHomesGui(p);
                return;
            }

            // Anulowanie
            if (isNamed(clicked, Material.RED_WOOL, plugin.getConfirmNoName())) {
                plugin.getPendingDelete().remove(p.getUniqueId());
                p.closeInventory();
                p.sendMessage(plugin.msg("remove_cancelled"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                plugin.openHomesGui(p);
            }
            return;
        }

        // --- GŁÓWNE GUI ---
        if (!isOurGuiTitle(title)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getSlot();
        List<Integer> homeSlots = plugin.getHomeSlots();
        if (!homeSlots.contains(slot)) return;

        ItemStack clicked = e.getCurrentItem();
        if (!isOurHomeItem(clicked)) return;

        int idx = homeSlots.indexOf(slot);

        // USUWANIE: Q / Ctrl+Q
        if (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            if (!p.hasPermission("homesgui.remove")) {
                p.sendMessage(plugin.msg("no_perm_remove"));
                return;
            }
            if (homeManager.getHome(p.getUniqueId(), idx) == null) {
                p.sendMessage(plugin.msg("slot_empty"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }
            p.closeInventory();
            plugin.openConfirmDeleteGui(p, idx);
            p.sendMessage(plugin.msg("remove_open_confirm"));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.0f);
            return;
        }

        // USTAWIENIE: PPM
        if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
            if (!p.hasPermission("homesgui.set")) {
                p.sendMessage(plugin.msg("no_perm_set"));
                return;
            }

            if (homeManager.getHome(p.getUniqueId(), idx) != null) {
                p.sendMessage(plugin.msg("already_exists"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.75f);
                return;
            }

            Location loc = p.getLocation();
            World.Environment env = loc.getWorld().getEnvironment();

            if (!p.hasPermission("homesgui.bypass.restrictions")) {
                if (!plugin.worldAllowed(loc.getWorld().getName())) {
                    p.sendMessage(plugin.msg("world_blocked"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                    return;
                }
                if (!plugin.yAllowed(loc.getBlockY(), env == World.Environment.NETHER)) {
                    p.sendMessage(plugin.msg("y_blocked"));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                    return;
                }
            }

            p.closeInventory();
            homeManager.setHome(p.getUniqueId(), idx, loc);
            p.sendMessage(plugin.msg("set_success"));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            return;
        }

        // TELEPORT: LPM
        if (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.SHIFT_LEFT) {
            if (!p.hasPermission("homesgui.tp")) {
                p.sendMessage(plugin.msg("no_perm_tp"));
                return;
            }
            Location home = homeManager.getHome(p.getUniqueId(), idx);
            if (home == null) {
                p.sendMessage(plugin.msg("not_set"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                return;
            }
            p.closeInventory();
            if (tpManager.isInWarmup(p.getUniqueId())) tpManager.cancelWarmup(p, null);
            tpManager.requestTeleport(p, home);
        }
    }

    /* ===================== Zamknięcie potwierdzenia ===================== */

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        if (isConfirmTitle(e.getView().getTitle())) {
            if (plugin.getPendingDelete().remove(p.getUniqueId()) != null) {
                p.sendMessage(plugin.msg("remove_cancelled"));
            }
        }
    }
}
