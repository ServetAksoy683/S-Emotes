package dev.emotewheel;

import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Builds the resource pack (emote item models, UI sprites, screen backgrounds as font glyphs) and sends it. */
public final class PackService {
    public static final String NS = "s_emotes";
    public static final UUID ID = UUID.fromString("5e307e55-1b2c-4d3e-8f40-0123456789ab");
    private static final long FIXED_TIME = 315532800000L; // keeps the zip (and its hash) stable
    private static final String[] SPRITES = {"ui_empty", "ui_hub", "ui_prev", "ui_next", "ui_close"};

    private final EmoteWheelPlugin pl;
    private final Set<UUID> loaded = Collections.synchronizedSet(new HashSet<>());
    private volatile byte[] zipBytes, sha1;
    private HttpServer http;
    private boolean warned;

    public PackService(EmoteWheelPlugin pl) { this.pl = pl; }

    public static ItemStack uiIcon(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(NamespacedKey.fromString(NS + ":" + name)));
        return it;
    }

    /** Inventory title that draws the custom screen background (negative space shifts the glyph to the left edge). */
    public static Component artTitle(boolean wheel, int n) {
        char c = (char) ((wheel ? 0xE100 : 0xE200) + n);
        return Component.text("\uF808" + c).font(Key.key(NS, "ui")).color(NamedTextColor.WHITE);
    }

    public void setLoaded(Player p, boolean ok) { if (ok) loaded.add(p.getUniqueId()); else loaded.remove(p.getUniqueId()); }
    public boolean isLoaded(Player p) { return loaded.contains(p.getUniqueId()); }
    public void forget(UUID id) { loaded.remove(id); }

    public void rebuild(Collection<Emote> emotes) {
        try {
            List<Emote> list = new ArrayList<>(emotes);
            list.sort(Comparator.comparing(e -> e.id));
            Color accent;
            try { accent = Color.decode(pl.getConfig().getString("ui.accent", "#FFB300")); }
            catch (NumberFormatException ex) { accent = new Color(0xFFB300); }
            int max = Math.max(2, Math.min(PlayerDataManager.MAX, pl.getConfig().getInt("wheel.max-slots", 12)));

            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bo)) {
                z.setLevel(9);
                put(z, "pack.mcmeta", "{\"pack\":{\"pack_format\":46,\"description\":\"S-Emotes\"}}".getBytes(StandardCharsets.UTF_8));
                for (Emote e : list) if (e.hasImage()) icon(z, e.id, e.texture, e.frameTime);
                for (String s : SPRITES) icon(z, s, GuiArt.png(GuiArt.sprite(s, accent)), 0);

                StringBuilder font = new StringBuilder("{\"providers\":[{\"type\":\"space\",\"advances\":{\"\\uF808\":-8}}");
                for (int n = 2; n <= max; n++) {
                    put(z, "assets/" + NS + "/textures/font/ui_wheel_" + n + ".png", GuiArt.png(GuiArt.wheel(n, accent)));
                    put(z, "assets/" + NS + "/textures/font/ui_equip_" + n + ".png", GuiArt.png(GuiArt.equip(n, accent)));
                    font.append(',').append(glyph("ui_wheel_" + n, 0xE100 + n));
                    font.append(',').append(glyph("ui_equip_" + n, 0xE200 + n));
                }
                font.append("]}");
                put(z, "assets/" + NS + "/font/ui.json", font.toString().getBytes(StandardCharsets.UTF_8));
            }
            byte[] data = bo.toByteArray();
            byte[] h = MessageDigest.getInstance("SHA-1").digest(data);
            boolean changed = !Arrays.equals(h, sha1);
            zipBytes = data;
            sha1 = h;
            File dir = new File(pl.getDataFolder(), "pack");
            dir.mkdirs();
            Files.write(new File(dir, "S-Emotes-pack.zip").toPath(), data);
            startHttp();
            if (url(null) == null && !warned) {
                warned = true;
                pl.getLogger().warning("Resim paketi hazir: plugins/S-Emotes/pack/S-Emotes-pack.zip -> bir yere yukleyip config.yml'de pack.url'ye yaz (README'ye bak).");
            }
            if (changed) for (Player p : Bukkit.getOnlinePlayers()) send(p);
        } catch (Exception ex) {
            pl.getLogger().warning("Resource pack olusturulamadi: " + ex.getMessage());
        }
    }

    private static String glyph(String file, int code) {
        return "{\"type\":\"bitmap\",\"file\":\"" + NS + ":font/" + file + ".png\",\"ascent\":13,\"height\":" + GuiArt.H
                + ",\"chars\":[\"" + String.format("\\u%04X", code) + "\"]}";
    }

    public void send(Player p) {
        String u = url(p);
        byte[] h = sha1;
        if (u == null || h == null || !pl.getConfig().getBoolean("pack.enabled", true)) return;
        try {
            String prompt = pl.getConfig().getString("pack.prompt", "S-Emotes gorselleri");
            p.addResourcePack(ID, u, h, prompt, pl.getConfig().getBoolean("pack.force", false));
        } catch (Exception ex) {
            pl.getLogger().warning("Pack gonderilemedi: " + ex.getMessage());
        }
    }

    public String url(Player p) {
        String u = pl.getConfig().getString("pack.url", "");
        if (u != null && !u.isBlank()) return u.trim();
        if (pl.getConfig().getBoolean("pack.self-host.enabled", false)) {
            String host = pl.getConfig().getString("pack.self-host.public-host", "");
            if ((host == null || host.isBlank()) && p != null && p.getVirtualHost() != null) host = p.getVirtualHost().getHostString();
            if (host != null && !host.isBlank())
                return "http://" + host.trim() + ":" + pl.getConfig().getInt("pack.self-host.port", 8123) + "/S-Emotes-pack.zip";
        }
        return null;
    }

    private void startHttp() {
        if (http != null || !pl.getConfig().getBoolean("pack.self-host.enabled", false)) return;
        try {
            http = HttpServer.create(new InetSocketAddress(pl.getConfig().getInt("pack.self-host.port", 8123)), 0);
            http.createContext("/", ex -> {
                byte[] b = zipBytes;
                if (b == null) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
                ex.getResponseHeaders().add("Content-Type", "application/zip");
                ex.sendResponseHeaders(200, b.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(b); }
            });
            http.start();
            pl.getLogger().info("Pack sunucusu acildi, port " + pl.getConfig().getInt("pack.self-host.port", 8123));
        } catch (Throwable t) {
            http = null;
            pl.getLogger().warning("Pack sunucusu acilamadi: " + t.getMessage());
        }
    }

    public void shutdown() {
        if (http != null) { http.stop(0); http = null; }
    }

    private static void icon(ZipOutputStream z, String n, byte[] png, int frameTime) throws Exception {
        put(z, "assets/" + NS + "/items/" + n + ".json",
                ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + NS + ":item/" + n + "\"}}").getBytes(StandardCharsets.UTF_8));
        put(z, "assets/" + NS + "/models/item/" + n + ".json",
                ("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + NS + ":item/" + n + "\"}}").getBytes(StandardCharsets.UTF_8));
        put(z, "assets/" + NS + "/textures/item/" + n + ".png", png);
        if (frameTime > 0)
            put(z, "assets/" + NS + "/textures/item/" + n + ".png.mcmeta",
                    ("{\"animation\":{\"frametime\":" + frameTime + "}}").getBytes(StandardCharsets.UTF_8));
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws Exception {
        ZipEntry e = new ZipEntry(name);
        e.setTime(FIXED_TIME);
        z.putNextEntry(e);
        z.write(data);
        z.closeEntry();
    }
}
