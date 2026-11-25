package dev.emotewheel;

import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class WheelManager {
    private final EmoteWheelPlugin pl;

    public WheelManager(EmoteWheelPlugin pl) { this.pl = pl; }

    public void open(Player p) {
        if (!art(p) && p.hasPermission("emotewheel.admin") && pl.pack().url(p) == null) pl.send(p, "wheel-no-pack-admin");
        new WheelMenu(pl, p).open();
    }

    /** True when the custom-art screens can be shown: Java 1.21.4+ client with the resource pack loaded. */
    public boolean art(Player p) {
        String mode = pl.getConfig().getString("wheel.mode", "AUTO").toUpperCase(Locale.ROOT);
        if (mode.equals("GUI") || mode.equals("PLAIN")) return false;
        if (p.getUniqueId().getMostSignificantBits() == 0) return false; // Floodgate (Bedrock) UUID
        if (protocol(p) < 769) return false;                              // 769 = 1.21.4
        return pl.pack().isLoaded(p);
    }

    private int protocol(Player p) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) return Integer.MAX_VALUE;
            Object api = Class.forName("com.viaversion.viaversion.api.Via").getMethod("getAPI").invoke(null);
            Object v = Class.forName("com.viaversion.viaversion.api.ViaAPI")
                    .getMethod("getPlayerVersion", UUID.class).invoke(api, p.getUniqueId());
            return (Integer) v;
        } catch (Throwable t) {
            return Integer.MAX_VALUE;
        }
    }
}
