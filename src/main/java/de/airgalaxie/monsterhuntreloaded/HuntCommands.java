package de.airgalaxie.monsterhuntreloaded;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

import static de.airgalaxie.monsterhuntreloaded.Messages.text;

public final class HuntCommands implements CommandExecutor {
    private final MonsterHuntPlugin plugin;
    private final ZoneSelector zoneSelector;

    public HuntCommands(MonsterHuntPlugin plugin, ZoneSelector zoneSelector) {
        this.plugin = plugin;
        this.zoneSelector = zoneSelector;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName()) {
            case "hunt" -> signup(sender);
            case "huntstatus" -> status(sender);
            case "huntscore" -> scores(sender, args);
            case "huntstart" -> start(sender, args);
            case "huntstop" -> stop(sender, args);
            case "huntzone" -> zone(sender);
            case "hunttele" -> teleport(sender);
            default -> false;
        };
    }

    private boolean signup(CommandSender sender) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!allowed(player, "monsterhunt.usercmd.hunt")) return true;
        plugin.hunts().join(player);
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!allowed(player, "monsterhunt.usercmd.huntstatus")) return true;
        HuntSession session = plugin.hunts().session(player.getWorld());
        if (session == null) {
            plugin.messages().send(player, "no-hunt");
            return true;
        }
        int score = session.scores().getOrDefault(player.getUniqueId(), 0);
        player.sendMessage(Component.text("Status: " + session.state() + ", Punkte: " + score, NamedTextColor.GOLD));
        if (session.state() == HuntState.RUNNING && !session.manual()) {
            int end = plugin.getConfig().getInt("schedule.end-time", 23600);
            int start = plugin.getConfig().getInt("schedule.start-time", 13000);
            long elapsed = Math.floorMod(player.getWorld().getTime() - start, 24000);
            long duration = Math.floorMod(end - start, 24000);
            int remaining = duration == 0 ? 0 : (int) Math.max(0, Math.round(100.0 * (duration - elapsed) / duration));
            player.sendMessage(Component.text("Verbleibende Jagdzeit: " + remaining + "%", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean scores(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !allowed(player, "monsterhunt.usercmd.huntscore")) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            int limit = 5;
            if (args.length > 1) {
                try { limit = Integer.parseInt(args[1]); }
                catch (NumberFormatException ignored) { sender.sendMessage("Ungültige Anzahl."); return true; }
            }
            sender.sendMessage(Component.text("MonsterHunt Highscores", NamedTextColor.GOLD));
            int rank = 0;
            for (Map.Entry<String, Integer> entry : plugin.highScores().top(limit).entrySet()) {
                sender.sendMessage(Component.text(++rank + ". " + entry.getKey() + " – " + entry.getValue(), NamedTextColor.YELLOW));
            }
            return true;
        }
        Player target = args.length == 0 && sender instanceof Player player ? player :
                args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        if (target == null) {
            sender.sendMessage("Spieler nicht online. Verwende /huntscore top.");
            return true;
        }
        sender.sendMessage(Component.text(target.getName() + ": " + plugin.highScores().get(target.getUniqueId()), NamedTextColor.GOLD));
        return true;
    }

    private boolean start(CommandSender sender, String[] args) {
        if (!sender.hasPermission("monsterhunt.admincmd.huntstart")) return denied(sender);
        World world = resolveWorld(sender, args);
        if (world == null) return true;
        HuntSession session = plugin.hunts().session(world);
        if (session == null) { sender.sendMessage("Diese Welt ist nicht aktiviert."); return true; }
        plugin.hunts().start(session, world, true);
        return true;
    }

    private boolean stop(CommandSender sender, String[] args) {
        if (!sender.hasPermission("monsterhunt.admincmd.huntstop")) return denied(sender);
        World world = resolveWorld(sender, args);
        if (world == null) return true;
        HuntSession session = plugin.hunts().session(world);
        if (session == null) { sender.sendMessage("Diese Welt ist nicht aktiviert."); return true; }
        plugin.hunts().stop(session, world, true);
        return true;
    }

    private boolean zone(CommandSender sender) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!allowed(player, "monsterhunt.admincmd.huntzone")) return true;
        zoneSelector.begin(player);
        return true;
    }

    private boolean teleport(CommandSender sender) {
        if (!(sender instanceof Player player)) return playerOnly(sender);
        if (!allowed(player, "monsterhunt.usercmd.hunttele")) return true;
        HuntZone zone = plugin.hunts().zone();
        HuntSession session = plugin.hunts().session(player.getWorld());
        if (zone == null || session == null) { plugin.messages().send(player, "no-hunt"); return true; }
        boolean bypass = player.hasPermission("monsterhunt.noteleportrestrictions");
        if (!bypass && (session.state() != HuntState.RUNNING || !session.scores().containsKey(player.getUniqueId()))) {
            plugin.messages().send(player, "no-hunt");
            return true;
        }
        plugin.hunts().rememberTeleport(player, player.getLocation());
        player.teleportAsync(zone.teleport());
        return true;
    }

    private World resolveWorld(CommandSender sender, String[] args) {
        if (args.length > 0) {
            World world = Bukkit.getWorld(args[0]);
            if (world == null) sender.sendMessage("Unbekannte Welt: " + args[0]);
            return world;
        }
        if (sender instanceof Player player) return player.getWorld();
        sender.sendMessage("Bitte eine Welt angeben.");
        return null;
    }

    private boolean allowed(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        plugin.messages().send(player, "no-permission");
        return false;
    }

    private boolean denied(CommandSender sender) {
        plugin.messages().send(sender, "no-permission");
        return true;
    }

    private boolean playerOnly(CommandSender sender) {
        sender.sendMessage("Dieser Befehl kann nur von Spielern verwendet werden.");
        return true;
    }
}
