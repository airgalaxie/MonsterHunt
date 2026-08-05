package de.airgalaxie.monsterhuntreloaded;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ZoneSelector implements Listener {
    private final MonsterHuntPlugin plugin;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public ZoneSelector(MonsterHuntPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player player) {
        selections.put(player.getUniqueId(), new Selection());
        Material tool = Material.matchMaterial(plugin.getConfig().getString("zone.selection-tool", "minecraft:wooden_sword"));
        if (tool == null) tool = Material.WOODEN_SWORD;
        if (!player.getInventory().contains(tool)) player.getInventory().addItem(new ItemStack(tool));
        player.sendRichMessage("<gold>[MonsterHunt]</gold> Rechtsklick: Ecke 1, Ecke 2, danach Teleportpunkt.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Selection selection = selections.get(event.getPlayer().getUniqueId());
        if (selection == null) return;
        Material configured = Material.matchMaterial(plugin.getConfig().getString("zone.selection-tool", "minecraft:wooden_sword"));
        if (configured == null || event.getPlayer().getInventory().getItemInMainHand().getType() != configured) return;
        event.setCancelled(true);
        if (selection.first == null) {
            selection.first = event.getClickedBlock().getLocation();
            event.getPlayer().sendRichMessage("<green>Erste Ecke gesetzt. Jetzt zweite Ecke wählen.</green>");
            return;
        }
        if (selection.second == null) {
            selection.second = event.getClickedBlock().getLocation();
            event.getPlayer().sendRichMessage("<green>Zweite Ecke gesetzt. Noch einmal klicken, um den aktuellen Standort als Teleportpunkt zu speichern.</green>");
            return;
        }
        Location teleport = event.getPlayer().getLocation();
        if (!selection.first.getWorld().equals(selection.second.getWorld()) || !selection.first.getWorld().equals(teleport.getWorld())) {
            event.getPlayer().sendRichMessage("<red>Alle Zonenpunkte müssen in derselben Welt liegen.</red>");
            selections.remove(event.getPlayer().getUniqueId());
            return;
        }
        plugin.getConfig().set("zone.enabled", true);
        plugin.getConfig().set("zone.world", teleport.getWorld().getName());
        plugin.getConfig().set("zone.corner-1", HuntZone.serialize(selection.first));
        plugin.getConfig().set("zone.corner-2", HuntZone.serialize(selection.second));
        plugin.getConfig().set("zone.teleport", HuntZone.serialize(teleport));
        plugin.saveConfig();
        plugin.hunts().reload();
        selections.remove(event.getPlayer().getUniqueId());
        event.getPlayer().sendRichMessage("<green>Hunt-Zone gespeichert.</green>");
    }

    private static final class Selection {
        private Location first;
        private Location second;
    }
}
