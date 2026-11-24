package dev.emotewheel;

import com.sun.net.httpserver.HttpServer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import javax.imageio.ImageIO;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Builds the resource pack (one item model per emote) and sends it to players. */
public final class PackService {
    public static final String NS = "s_emotes";
    private static final UUID PACK_ID = UUID.fromString("5e307e55-1b2c-4d3e-8f40-0123456789ab");
    private static final long FIXED_TIME = 315532800000L; // 1980-01-01, keeps the zip (and its hash) stable

    private final EmoteWheelPlugin pl;
    private volatile byte[] zipBytes, sha1;
    private HttpServer http;
    private boolean warned;

    public PackService(EmoteWheelPlugin pl) { this.pl = pl; }

    public static ItemStack emptyIcon() {
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(NamespacedKey.fromString(NS + ":empty")));
        return it;
    }

    public void rebuild(Collection<Emote> emotes) {
        try {
            List<Emote> list = new ArrayList<>(emotes);
            list.sort(Comparator.comparing(e -> e.id));
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bo)) {
                z.setLevel(9);
                put(z, "pack.mcmeta", "{\"pack\":{\"pack_format\":46,\"description\":\"S-Emotes\"}}".getBytes(StandardCharsets.UTF_8));
                for (Emote e : list) if (e.hasImage()) icon(z, e.id, e.texture, e.frameTime);
                icon(z, "empty", emptyPng(), 0);
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
            if (url() == null && !warned) {
                warned = true;
                pl.getLogger().warning("Resim paketi hazir: plugins/S-Emotes/pack/S-Emotes-pack.zip -> bir yere yukleyip config.yml'de pack.url'ye yaz (README'ye bak).");
            }
            if (changed) for (Player p : Bukkit.getOnlinePlayers()) send(p);
        } catch (Exception ex) {
            pl.getLogger().warning("Resource pack olusturulamadi: " + ex.getMessage());
        }
    }

    public void send(Player p) {
        String u = url();
        byte[] h = sha1;
        if (u == null || h == null || !pl.getConfig().getBoolean("pack.enabled", true)) return;
        try {
            String prompt = pl.getConfig().getString("pack.prompt", "S-Emotes gorselleri");
            p.addResourcePack(PACK_ID, u, h, prompt, pl.getConfig().getBoolean("pack.force", false));
        } catch (Exception ex) {
            pl.getLogger().warning("Pack gonderilemedi: " + ex.getMessage());
        }
    }

    private String url() {
        String u = pl.getConfig().getString("pack.url", "");
        if (u != null && !u.isBlank()) return u.trim();
        if (pl.getConfig().getBoolean("pack.self-host.enabled", false)) {
            String host = pl.getConfig().getString("pack.self-host.public-host", "");
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

    private static byte[] emptyPng() throws Exception {
        BufferedImage i = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(40, 40, 40, 90));
        g.fillOval(14, 14, 100, 100);
        g.setColor(new Color(170, 170, 170, 210));
        g.setStroke(new BasicStroke(8));
        g.drawOval(14, 14, 100, 100);
        g.dispose();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(i, "png", bo);
        return bo.toByteArray();
    }
}
