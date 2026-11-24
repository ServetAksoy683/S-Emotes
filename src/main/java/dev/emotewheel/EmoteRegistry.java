package dev.emotewheel;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Scans the emotes folder (images, yml/json definitions, zip packs) and builds the emote list. */
public final class EmoteRegistry {
    private static final Set<String> IMG = Set.of("png", "jpg", "jpeg", "gif", "bmp", "wbmp");
    private static final Set<String> DEF = Set.of("yml", "yaml", "json");

    private record Res(String path, byte[] data) {}

    private final EmoteWheelPlugin pl;
    private final Map<String, Emote> emotes = new LinkedHashMap<>();
    private volatile long signature;

    public EmoteRegistry(EmoteWheelPlugin pl) { this.pl = pl; }

    private File dir() { return new File(pl.getDataFolder(), pl.getConfig().getString("emotes.folder", "emotes")); }

    public Collection<Emote> all() { return emotes.values(); }
    public Emote get(String id) { return id == null ? null : emotes.get(id.toLowerCase(Locale.ROOT)); }
    public boolean changed() { return sig(dir()) != signature; }

    public void load() {
        emotes.clear();
        File dir = dir();
        if (!dir.exists()) { dir.mkdirs(); DefaultEmotes.generate(pl, dir); }
        List<Res> res = new ArrayList<>();
        collect(dir, res);
        Map<String, Res> idx = new HashMap<>();
        for (Res r : res) idx.put(r.path().toLowerCase(Locale.ROOT), r);
        Set<String> used = new HashSet<>();
        for (Res r : res) if (DEF.contains(ext(r.path()))) parseDef(r, idx, used);
        for (Res r : res) {
            if (!IMG.contains(ext(r.path())) || used.contains(r.path().toLowerCase(Locale.ROOT))) continue;
            String id = norm(baseName(r.path()));
            if (emotes.containsKey(id)) continue;
            Emote e = fresh(id, null, r.path());
            attach(e, r);
            if (e.hasImage()) emotes.put(id, e);
        }
        signature = sig(dir);
        pl.getLogger().info(emotes.size() + " emote yüklendi.");
    }

    private void parseDef(Res r, Map<String, Res> idx, Set<String> used) {
        YamlConfiguration y = new YamlConfiguration();
        try { y.loadFromString(new String(r.data(), StandardCharsets.UTF_8)); }
        catch (Exception ex) { pl.getLogger().warning(r.path() + " okunamadı: " + ex.getMessage()); return; }
        String dir = parent(r.path());
        if (y.isConfigurationSection("emotes")) {
            ConfigurationSection root = y.getConfigurationSection("emotes");
            for (String k : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(k);
                if (s != null) build(norm(k), s, dir, null, idx, used, r.path());
            }
        } else {
            String noExt = r.path().substring(0, r.path().lastIndexOf('.'));
            build(norm(y.getString("id", baseName(r.path()))), y, dir, noExt, idx, used, r.path());
        }
    }

    private void build(String id, ConfigurationSection s, String dir, String sidecar, Map<String, Res> idx, Set<String> used, String src) {
        Emote e = fresh(id, s, src);
        Res ir = null;
        String img = s.getString("image");
        if (img != null) ir = find(idx, dir, img);
        if (ir == null && sidecar != null) ir = withExt(idx, sidecar);
        if (ir == null) ir = withExt(idx, dir + id);
        if (ir != null) { used.add(ir.path().toLowerCase(Locale.ROOT)); attach(e, ir); }
        if (!e.hasImage() && e.text == null) { pl.getLogger().warning(src + " -> '" + id + "' için resim veya text bulunamadı, atlandı."); return; }
        emotes.put(id, e);
    }

    private Emote fresh(String id, ConfigurationSection s, String src) {
        var c = pl.getConfig();
        Emote e = new Emote(id);
        e.source = src;
        e.name = pretty(id);
        e.free = c.getBoolean("emotes.free-by-default", false);
        e.duration = c.getInt("emotes.default-duration-ticks", 60);
        if (s == null) return e;
        e.name = s.getString("name", e.name);
        e.description = s.isList("description") ? String.join("\n", s.getStringList("description")) : s.getString("description", "");
        e.free = s.getBoolean("free", e.free);
        e.permission = s.getString("permission");
        e.text = s.getString("text");
        e.sound = s.getString("sound");
        e.volume = (float) s.getDouble("volume", 1);
        e.pitch = (float) s.getDouble("pitch", 1);
        e.duration = s.getInt("duration", e.duration);
        e.particleCount = s.getInt("particle-count", 5);
        for (String a : s.getStringList("actions")) e.actions.add(a.toLowerCase(Locale.ROOT));
        String p = s.getString("particle");
        if (p != null) {
            try { e.particle = Particle.valueOf(p.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { pl.getLogger().warning(src + ": bilinmeyen particle " + p); }
        }
        Material m = Material.matchMaterial(s.getString("icon", ""));
        if (m != null) e.fallbackIcon = m;
        return e;
    }

    private void attach(Emote e, Res r) {
        try {
            List<BufferedImage> frames;
            if (ext(r.path()).equals("gif")) {
                ImageUtil.Gif g = ImageUtil.readGif(r.data(), pl.getConfig().getInt("emotes.max-frames", 24));
                frames = g.frames();
                e.frameDelay = g.delayTicks();
            } else {
                BufferedImage bi = ImageIO.read(new ByteArrayInputStream(r.data()));
                if (bi == null) throw new IOException("desteklenmeyen/bozuk resim");
                frames = List.of(bi);
            }
            e.frameTime = frames.size() > 1 ? Math.max(1, e.frameDelay) : 0;
            e.texture = ImageUtil.strip(frames);
        } catch (Exception ex) {
            pl.getLogger().warning(r.path() + " yüklenemedi: " + ex.getMessage());
        }
    }

    private void collect(File dir, List<Res> out) {
        Path root = dir.toPath();
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String rel = root.relativize(p).toString().replace('\\', '/');
                String ext = ext(rel);
                if (ext.equals("zip")) readZip(p.toFile(), rel, out);
                else if (IMG.contains(ext) || DEF.contains(ext)) out.add(new Res(rel, Files.readAllBytes(p)));
            }
        } catch (IOException ex) {
            pl.getLogger().warning("Emote klasörü okunamadı: " + ex.getMessage());
        }
    }

    private void readZip(File f, String rel, List<Res> out) {
        try (ZipInputStream z = new ZipInputStream(new FileInputStream(f))) {
            ZipEntry en;
            while ((en = z.getNextEntry()) != null) {
                if (en.isDirectory()) continue;
                String name = en.getName().replace('\\', '/');
                String ext = ext(name);
                if (name.contains("..") || (!IMG.contains(ext) && !DEF.contains(ext))) continue;
                out.add(new Res(rel + "/" + name, z.readAllBytes()));
            }
        } catch (IOException ex) {
            pl.getLogger().warning(rel + " açılamadı: " + ex.getMessage());
        }
    }

    private Res find(Map<String, Res> idx, String dir, String name) {
        String n = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        Res r = idx.get(dir + n);
        if (r == null) r = idx.get(n);
        if (r != null) return r;
        String file = n.substring(n.lastIndexOf('/') + 1);
        for (Map.Entry<String, Res> en : idx.entrySet())
            if (en.getKey().equals(file) || en.getKey().endsWith("/" + file)) return en.getValue();
        return null;
    }

    private Res withExt(Map<String, Res> idx, String noExt) {
        for (String e : IMG) {
            Res r = idx.get((noExt + "." + e).toLowerCase(Locale.ROOT));
            if (r != null) return r;
        }
        return null;
    }

    private long sig(File dir) {
        long h = 17;
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                h = h * 31 + p.toString().hashCode();
                h = h * 31 + p.toFile().length();
                h = h * 31 + p.toFile().lastModified();
            }
        } catch (IOException ignored) {}
        return h;
    }

    private static String ext(String p) { int i = p.lastIndexOf('.'); return i < 0 ? "" : p.substring(i + 1).toLowerCase(Locale.ROOT); }
    private static String parent(String p) { int i = p.lastIndexOf('/'); return i < 0 ? "" : p.substring(0, i + 1).toLowerCase(Locale.ROOT); }
    private static String baseName(String p) {
        String f = p.substring(p.lastIndexOf('/') + 1);
        int i = f.lastIndexOf('.');
        return i < 0 ? f : f.substring(0, i);
    }
    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]+", "_"); }
    private static String pretty(String id) {
        String s = id.replace('_', ' ').replace('-', ' ');
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
