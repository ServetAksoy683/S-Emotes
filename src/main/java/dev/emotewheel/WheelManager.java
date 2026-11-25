package dev.emotewheel;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class WheelManager {
    private final EmoteWheelPlugin pl;
    private final Map<UUID, WheelSession> sessions = new HashMap<>();

    public WheelManager(EmoteWheelPlugin pl) { this.pl = pl; }

    public WheelSession get(Player p) { return sessions.get(p.getUniqueId()); }

    public void open(Player p) {
        if (sessions.containsKey(p.getUniqueId())) return;
        if (!useDisplay(p)) { new FallbackMenu(pl, p).open(); return; }
        sessions.put(p.getUniqueId(), new WheelSession(pl, this, p));
    }

    void remove(UUID id) { sessions.remove(id); }

    public void close(Player p, boolean instant) {
        WheelSession s = get(p);
        if (s == null) return;
        if (instant) s.destroy(); else s.close();
    }

    public void closeAll() {
        for (WheelSession s : new ArrayList<>(sessions.values())) s.destroy();
        sessions.clear();
    }

    /** Display-entity wheel needs a 1.19.4+ client and a Java player (Bedrock/old clients get a chest GUI). */
    private boolean useDisplay(Player p) {
        String mode = pl.getConfig().getString("wheel.mode", "AUTO").toUpperCase(Locale.ROOT);
        if (mode.equals("DISPLAY")) return true;
        if (mode.equals("GUI")) return false;
        if (p.getUniqueId().getMostSignificantBits() == 0) return false; // Floodgate (Bedrock) UUID
        return protocol(p) >= 769 && pl.pack().isLoaded(p); // 769 = 1.21.4 (item models), pack must be loaded
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
