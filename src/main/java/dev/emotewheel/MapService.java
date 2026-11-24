package dev.emotewheel;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.map.*;

/** Turns images into persistent map views (reused across restarts) and sends them to clients. */
public final class MapService {
    private final EmoteWheelPlugin pl;
    private final File file;
    private final YamlConfiguration cache;
    private final Map<UUID, Set<Integer>> sent = new HashMap<>();
    private boolean dirty;

    public MapService(EmoteWheelPlugin pl) {
        this.pl = pl;
        pl.getDataFolder().mkdirs();
        this.file = new File(pl.getDataFolder(), "maps.yml");
        this.cache = YamlConfiguration.loadConfiguration(file);
    }

    /** @param fitted 128x128 image (see ImageUtil.fit). Returns the map id. */
    public int mapFor(BufferedImage fitted) {
        String key = "m" + ImageUtil.hash(fitted);
        int id = cache.getInt(key, -1);
        MapView view = id >= 0 ? Bukkit.getMap(id) : null;
        if (view == null) {
            view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            cache.set(key, view.getId());
            dirty = true;
        }
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.setScale(MapView.Scale.CLOSEST);
        view.addRenderer(new ImageRenderer(fitted));
        return view.getId();
    }

    /** Item displays don't trigger vanilla map syncing, so we push the data ourselves (once per player). */
    public void ensure(Player p, Collection<Integer> ids) {
        Set<Integer> s = sent.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        for (int id : ids) {
            if (!s.add(id)) continue;
            MapView v = Bukkit.getMap(id);
            if (v != null) p.sendMap(v);
        }
    }

    public void forget(UUID id) { sent.remove(id); }

    public void save() {
        if (!dirty) return;
        try { cache.save(file); dirty = false; } catch (IOException e) { pl.getLogger().warning("maps.yml kaydedilemedi: " + e.getMessage()); }
    }

    private static final class ImageRenderer extends MapRenderer {
        private final BufferedImage img;
        private boolean drawn;
        ImageRenderer(BufferedImage img) { super(false); this.img = img; }
        @Override public void render(MapView map, MapCanvas canvas, Player player) {
            if (drawn) return;
            canvas.drawImage(0, 0, img);
            drawn = true;
        }
    }
}
