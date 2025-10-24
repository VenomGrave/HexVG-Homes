package com.venom.hexplugin.hexhomes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class HexHomes extends JavaPlugin {

    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private GuiListener guiListener;
    private TeleportCancelListener teleportCancelListener;

    // GUI główne
    private String guiTitle;
    private String itemName;
    private List<String> itemLore;
    private List<Integer> homeSlots;

    // GUI potwierdzenia
    private String confirmTitle;
    private String confirmYesName, confirmNoName;
    private List<String> confirmYesLore, confirmNoLore;

    // Ograniczenia lokacji
    private Set<String> allowedWorlds;
    private boolean blockNetherRoof;
    private int yMin, yMax;

    // Oczekujące usunięcie
    private final Map<UUID, Integer> pendingDelete = new HashMap<UUID, Integer>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();

        this.homeManager = new HomeManager(this);
        this.teleportManager = new TeleportManager(this);

        this.guiListener = new GuiListener(this, homeManager, teleportManager);
        this.teleportCancelListener = new TeleportCancelListener(this, teleportManager);

        Bukkit.getPluginManager().registerEvents(guiListener, this);
        Bukkit.getPluginManager().registerEvents(teleportCancelListener, this);

        getLogger().info("HexVG-Homes wlaczony.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(guiListener);
        HandlerList.unregisterAll(teleportCancelListener);
        homeManager.save();
        getLogger().info("HexVG-Homes wylaczony.");
    }

    /* ===================== CONFIG / MSG ===================== */

    public void reloadLocalConfig() {
        reloadConfig();
        FileConfiguration c = getConfig();

        // GUI
        this.guiTitle = cc(c.getString("gui.title", "&8Twoje &cDomy"));
        this.itemName = cc(c.getString("gui.item_name", "&cDom"));
        this.itemLore = toCCList(c.getStringList("gui.item_lore"));
        this.homeSlots = c.getIntegerList("homes.slots");
        if (homeSlots == null || homeSlots.isEmpty()) homeSlots = Arrays.asList(10, 11, 12, 13, 14, 15);

        // potwierdzenie
        this.confirmTitle = cc(c.getString("confirm_gui.title", "&8Usunac dom?"));
        this.confirmYesName = cc(c.getString("confirm_gui.green_name", "&aPotwierdz"));
        this.confirmNoName  = cc(c.getString("confirm_gui.red_name", "&cAnuluj"));
        this.confirmYesLore = toCCList(c.getStringList("confirm_gui.green_lore"));
        this.confirmNoLore  = toCCList(c.getStringList("confirm_gui.red_lore"));

        // restrykcje
        this.allowedWorlds = new HashSet<String>(c.getStringList("restrictions.allow_worlds"));
        this.blockNetherRoof = c.getBoolean("restrictions.block_nether_roof", true);
        this.yMin = c.getInt("restrictions.y_min", 0);
        this.yMax = c.getInt("restrictions.y_max", 320);
    }

    private List<String> toCCList(List<String> in) {
        if (in == null) return Collections.emptyList();
        return in.stream().map(this::cc).collect(Collectors.<String>toList());
    }

    public String cc(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public String msg(String key) {
        String p = getConfig().getString("messages.prefix", "&8>> ");
        String m = getConfig().getString("messages." + key, "&7" + key);
        return cc(p + m);
    }

    public String msgFmt(String key, Map<String, String> vars) {
        String p = getConfig().getString("messages.prefix", "&8>> ");
        String m = getConfig().getString("messages." + key, "&7" + key);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            m = m.replace("{" + e.getKey() + "}", e.getValue());
        }
        return cc(p + m);
    }

    public boolean worldAllowed(String worldName) {
        return allowedWorlds == null || allowedWorlds.isEmpty() || allowedWorlds.contains(worldName);
    }

    public boolean yAllowed(int y, boolean isNether) {
        if (isNether && blockNetherRoof && y > 127) return false;
        return y >= yMin && y <= yMax;
    }

    /* ===================== GUI ===================== */

    public void openHomesGui(Player p) {
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(p, 27, guiTitle);

        // tło
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // łóżka
        for (int slot : homeSlots) {
            ItemStack bed = new ItemStack(Material.RED_BED);
            ItemMeta meta = bed.getItemMeta();
            meta.setDisplayName(itemName);
            meta.setLore(itemLore);
            bed.setItemMeta(meta);
            inv.setItem(slot, bed);
        }
        p.openInventory(inv);
    }

    public void openConfirmDeleteGui(Player p, int homeIndex) {
        pendingDelete.put(p.getUniqueId(), homeIndex);

        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(p, 27, confirmTitle);

        // tło
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.setDisplayName(" ");
        filler.setItemMeta(fm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);

        // zielona wełna (TAK)
        ItemStack yes = new ItemStack(Material.GREEN_WOOL);
        ItemMeta ym = yes.getItemMeta();
        ym.setDisplayName(confirmYesName);
        List<String> yl = new ArrayList<String>();
        int guiSlot = homeSlots.get(homeIndex);
        List<String> baseYesLore = confirmYesLore.isEmpty()
                ? Collections.singletonList(cc("&7Usun slot " + guiSlot))
                : confirmYesLore;
        for (String s : baseYesLore) yl.add(s.replace("{slot}", String.valueOf(guiSlot)));
        ym.setLore(yl);
        yes.setItemMeta(ym);
        inv.setItem(12, yes);

        // czerwona wełna (NIE)
        ItemStack no = new ItemStack(Material.RED_WOOL);
        ItemMeta nm = no.getItemMeta();
        nm.setDisplayName(confirmNoName);
        nm.setLore(confirmNoLore);
        no.setItemMeta(nm);
        inv.setItem(14, no);

        p.openInventory(inv);
    }

    /* ===================== GETTERY ===================== */

    public HomeManager getHomeManager() { return homeManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public String getGuiTitle() { return guiTitle; }
    public String getItemName() { return itemName; }
    public List<Integer> getHomeSlots() { return homeSlots; }
    public String getConfirmTitle() { return confirmTitle; }
    public String getConfirmYesName() { return confirmYesName; }
    public String getConfirmNoName() { return confirmNoName; }
    public Map<UUID, Integer> getPendingDelete() { return pendingDelete; }

    /* ===================== KOMENDA /dom ===================== */

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dom")) return false;

        // /dom reload
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("homesgui.admin.reload")) {
                s.sendMessage(msg("no_perm"));
                return true;
            }
            try {
                reloadLocalConfig();
                s.sendMessage(msg("reload_ok"));
            } catch (Exception ex) {
                getLogger().warning("Blad przy reloadzie configu: " + ex.getMessage());
                s.sendMessage(msg("reload_err"));
            }
            return true;
        }

        // /dom (otwarcie GUI)
        if (!(s instanceof Player)) {
            s.sendMessage(msg("player_only"));
            return true;
        }
        if (!s.hasPermission("homesgui.command.dom")) {
            s.sendMessage(msg("no_perm"));
            return true;
        }
        Player p = (Player) s;
        openHomesGui(p);
        return true;
    }
}
