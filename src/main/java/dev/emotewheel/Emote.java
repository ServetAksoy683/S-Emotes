package dev.emotewheel;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

public final class Emote {
    public final String id;
    public String name, description = "", sound, text, source = "", permission;
    public float volume = 1f, pitch = 1f;
    public Particle particle;
    public int particleCount = 5;
    public boolean free;
    public int duration = 60, frameDelay = 4;
    public Material fallbackIcon = Material.NAME_TAG;
    public List<String> actions = new ArrayList<>();
    public final List<Integer> mapIds = new ArrayList<>();

    public Emote(String id) { this.id = id; this.name = id; }

    public boolean hasImage() { return !mapIds.isEmpty(); }
    public boolean animated() { return mapIds.size() > 1; }

    public ItemStack icon(int frame) {
        if (mapIds.isEmpty()) return new ItemStack(fallbackIcon);
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapView v = Bukkit.getMap(mapIds.get(Math.floorMod(frame, mapIds.size())));
        if (v != null) it.editMeta(MapMeta.class, m -> m.setMapView(v));
        return it;
    }
}
