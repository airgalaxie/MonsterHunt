package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

public record HuntZone(UUID worldId, Location corner1, Location corner2, Location teleport) {
    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getUID().equals(worldId)) return false;
        return between(corner1.getX(), corner2.getX(), location.getX())
                && between(corner1.getY(), corner2.getY(), location.getY())
                && between(corner1.getZ(), corner2.getZ(), location.getZ());
    }

    public static HuntZone load(FileConfiguration config) {
        if (!config.getBoolean("zone.enabled")) return null;
        World world = Bukkit.getWorld(config.getString("zone.world", ""));
        if (world == null) return null;
        Location first = parse(world, config.getString("zone.corner-1", ""));
        Location second = parse(world, config.getString("zone.corner-2", ""));
        Location teleport = parse(world, config.getString("zone.teleport", ""));
        return first == null || second == null || teleport == null
                ? null : new HuntZone(world.getUID(), first, second, teleport);
    }

    public static String serialize(Location location) {
        return location.getX() + "," + location.getY() + "," + location.getZ()
                + "," + location.getYaw() + "," + location.getPitch();
    }

    private static Location parse(World world, String value) {
        String[] parts = value.split(",");
        if (parts.length < 3) return null;
        try {
            float yaw = parts.length > 3 ? Float.parseFloat(parts[3]) : 0;
            float pitch = parts.length > 4 ? Float.parseFloat(parts[4]) : 0;
            return new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), yaw, pitch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean between(double first, double second, double value) {
        return value >= Math.min(first, second) && value <= Math.max(first, second);
    }
}
