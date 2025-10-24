package com.venom.hexplugin.hexhomes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class HomeManager {

    private final Plugin plugin;
    private final File file;
    private final FileConfiguration cfg;

    public HomeManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    private String path(UUID uuid, int index) {
        return "players." + uuid + ".homes." + index;
    }

    public boolean hasHome(UUID uuid, int index) {
        return cfg.contains(path(uuid, index) + ".world");
    }

    public void setHome(UUID uuid, int index, Location loc) {
        String p = path(uuid, index);
        cfg.set(p + ".world", loc.getWorld().getName());
        cfg.set(p + ".x", loc.getX());
        cfg.set(p + ".y", loc.getY());
        cfg.set(p + ".z", loc.getZ());
        cfg.set(p + ".yaw", loc.getYaw());
        cfg.set(p + ".pitch", loc.getPitch());
        save();
    }

    public Location getHome(UUID uuid, int index) {
        String p = path(uuid, index);
        if (!cfg.contains(p + ".world")) return null;

        String worldName = cfg.getString(p + ".world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;

        double x = cfg.getDouble(p + ".x");
        double y = cfg.getDouble(p + ".y");
        double z = cfg.getDouble(p + ".z");
        float yaw = (float) cfg.getDouble(p + ".yaw");
        float pitch = (float) cfg.getDouble(p + ".pitch");

        return new Location(w, x, y, z, yaw, pitch);
    }

    public void removeHome(UUID uuid, int index) {
        cfg.set(path(uuid, index), null);
        save();
    }
}
