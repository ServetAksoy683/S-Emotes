package dev.emotewheel;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class PlayerDataManager {
    public static final int MAX = 12;

    public static final class Data {
        public final Set<String> owned = new LinkedHashSet<>();
        public final String[] wheel = new String[MAX];
    }

    private final EmoteWheelPlugin pl;
    private final File dir;
    private final Map<UUID, Data> cache = new HashMap<>();

    public PlayerDataManager(EmoteWheelPlugin pl) {
        this.pl = pl;
        this.dir = new File(pl.getDataFolder(), "data");
        dir.mkdirs();
    }

    public Data get(Player p) { return cache.computeIfAbsent(p.getUniqueId(), this::load); }

    private File file(UUID id) { return new File(dir, id + ".yml"); }

    private Data load(UUID id) {
        Data d = new Data();
        File f = file(id);
        if (!f.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        d.owned.addAll(y.getStringList("owned"));
        List<String> w = y.getStringList("wheel");
        for (int i = 0; i < MAX && i < w.size(); i++) d.wheel[i] = (w.get(i) == null || w.get(i).isEmpty()) ? null : w.get(i);
        return d;
    }

    private synchronized void write(UUID id, List<String> owned, List<String> wheel) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("owned", owned);
        y.set("wheel", wheel);
        try { y.save(file(id)); } catch (IOException e) { pl.getLogger().warning("Oyuncu verisi kaydedilemedi: " + e.getMessage()); }
    }

    private List<String> wheelList(Data d) {
        List<String> l = new ArrayList<>();
        for (String s : d.wheel) l.add(s == null ? "" : s);
        return l;
    }

    public void save(Player p) {
        Data d = cache.get(p.getUniqueId());
        if (d == null) return;
        UUID id = p.getUniqueId();
        List<String> owned = new ArrayList<>(d.owned), wheel = wheelList(d);
        Bukkit.getScheduler().runTaskAsynchronously(pl, () -> write(id, owned, wheel));
    }

    public void saveAllNow() {
        for (Map.Entry<UUID, Data> e : cache.entrySet()) write(e.getKey(), new ArrayList<>(e.getValue().owned), wheelList(e.getValue()));
    }

    public void unload(UUID id) {
        Data d = cache.remove(id);
        if (d != null) {
            List<String> owned = new ArrayList<>(d.owned), wheel = wheelList(d);
            Bukkit.getScheduler().runTaskAsynchronously(pl, () -> write(id, owned, wheel));
        }
    }

    /** Adds an emote to the player's collection. Returns false if they already own it. */
    public boolean grant(Player p, Emote e) {
        Data d = get(p);
        if (pl.has(p, e)) return false;
        d.owned.add(e.id);
        if (pl.getConfig().getBoolean("unlock.auto-equip", true)) {
            int n = pl.slots(p);
            for (int i = 0; i < n; i++) if (d.wheel[i] == null) { d.wheel[i] = e.id; break; }
        }
        save(p);
        return true;
    }

    public boolean revoke(Player p, String id) {
        Data d = get(p);
        boolean had = d.owned.remove(id);
        for (int i = 0; i < MAX; i++) if (id.equals(d.wheel[i])) d.wheel[i] = null;
        save(p);
        return had;
    }
}
