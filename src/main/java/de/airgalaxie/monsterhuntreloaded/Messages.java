package de.airgalaxie.monsterhuntreloaded;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;

public final class Messages {
    private final FileConfiguration config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(FileConfiguration config) {
        this.config = config;
    }

    public void send(Audience audience, String key, TagResolver... placeholders) {
        String prefix = config.getString("messages.prefix", "");
        String message = config.getString("messages." + key, "<red>Missing message: " + key + "</red>");
        audience.sendMessage(miniMessage.deserialize(prefix + message, placeholders));
    }

    public static TagResolver text(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
