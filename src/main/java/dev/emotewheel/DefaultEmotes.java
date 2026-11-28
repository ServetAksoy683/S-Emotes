package dev.emotewheel;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Starter motion emotes with generated stick-figure icons (shapes only, no fonts needed). */
final class DefaultEmotes {
    private DefaultEmotes() {}

    static void generate(EmoteWheelPlugin pl, File dir) {
        try { Files.writeString(new File(dir, "OKU-BENI.txt").toPath(), README, StandardCharsets.UTF_8); }
        catch (Exception ex) { pl.getLogger().warning("OKU-BENI.txt yazilamadi: " + ex.getMessage()); }
        ensureMotion(pl, dir);
    }

    /** Adds the motion starter pack once (also for servers that upgrade from an older version). */
    static void ensureMotion(EmoteWheelPlugin pl, File dir) {
        File marker = new File(dir, ".motion-starter-done");
        if (marker.exists()) return;
        try {
            File yml = new File(dir, "motion-starter.yml");
            if (!yml.exists()) {
                ImageIO.write(icon(new Color(0xE0A800), "sit"), "png", new File(dir, "sit.png"));
                ImageIO.write(icon(new Color(0x2196F3), "lay"), "png", new File(dir, "lay.png"));
                ImageIO.write(icon(new Color(0x43A047), "crawl"), "png", new File(dir, "crawl.png"));
                ImageIO.write(icon(new Color(0xFB8C00), "wave"), "png", new File(dir, "wave.png"));
                ImageIO.write(icon(new Color(0x8E24AA), "bow"), "png", new File(dir, "bow.png"));
                ImageIO.write(icon(new Color(0xD32F2F), "superman"), "png", new File(dir, "superman.png"));
                ImageIO.write(icon(new Color(0x3949AB), "spin"), "png", new File(dir, "spin.png"));
                Files.writeString(yml.toPath(), MOTION, StandardCharsets.UTF_8);
            }
            marker.createNewFile();
        } catch (Exception ex) {
            pl.getLogger().warning("Hareket emoteleri olusturulamadi: " + ex.getMessage());
        }
    }

    private static BufferedImage icon(Color bg, String kind) {
        BufferedImage i = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(bg);
        g.fillOval(4, 4, 120, 120);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case "sit" -> { head(g, 50, 36); l(g, 50, 48, 50, 80); l(g, 50, 80, 84, 80); l(g, 84, 80, 84, 102); l(g, 50, 58, 72, 72); }
            case "lay" -> { head(g, 28, 86); l(g, 40, 86, 86, 86); l(g, 86, 86, 108, 80); l(g, 50, 86, 70, 98); }
            case "crawl" -> { head(g, 34, 76); l(g, 46, 80, 88, 86); l(g, 88, 86, 108, 92); l(g, 52, 82, 52, 102); }
            case "wave" -> { head(g, 64, 34); l(g, 64, 46, 64, 84); l(g, 64, 84, 50, 106); l(g, 64, 84, 78, 106); l(g, 64, 58, 46, 76); l(g, 64, 58, 92, 40); }
            case "bow" -> { head(g, 82, 50); l(g, 70, 58, 56, 86); l(g, 56, 86, 44, 108); l(g, 56, 86, 70, 108); l(g, 70, 64, 90, 80); }
            case "superman" -> { head(g, 34, 56); l(g, 46, 60, 90, 64); l(g, 90, 64, 112, 70); l(g, 46, 60, 22, 56); }
            case "spin" -> { head(g, 64, 38); l(g, 64, 50, 64, 86); l(g, 64, 86, 52, 106); l(g, 64, 86, 76, 106); l(g, 64, 62, 40, 62); l(g, 64, 62, 88, 62); g.drawArc(20, 20, 88, 88, 20, 110); }
            default -> { }
        }
        g.dispose();
        return i;
    }

    private static void head(Graphics2D g, int x, int y) { g.drawOval(x - 11, y - 11, 22, 22); }
    private static void l(Graphics2D g, int a, int b, int c, int d) { g.drawLine(a, b, c, d); }

    private static final String MOTION = """
# Hareket emoteleri. pose: sitting | sleeping | swimming | fall_flying | spin_attack | sneaking | standing
# timeline adimlari: at (tick), pose, swing (main/off), yaw (+derece, over: kac tickte), pitch, jump, sound, particle
#                    every + times ile tekrar. duration yazmazsan: pose-only = Shift/yuruyunce biter.
emotes:
  sit:
    name: "<yellow>Otur"
    description: "Yere otur (Shift ile kalk)"
    image: sit.png
    free: true
    pose: sitting
  lay:
    name: "<aqua>Uzan"
    description: "Yere uzan (Shift ile kalk)"
    image: lay.png
    free: true
    pose: sleeping
  crawl:
    name: "<green>Sürün"
    description: "Yerde sürün"
    image: crawl.png
    free: true
    pose: swimming
  wave:
    name: "<gold>El Salla"
    description: "Selam ver"
    image: wave.png
    free: true
    timeline:
      - {at: 0, swing: main, every: 8, times: 5}
  bow:
    name: "<light_purple>Selam Ver"
    description: "Eğilerek selamla"
    image: bow.png
    free: true
    timeline:
      - {at: 0, pitch: 50}
      - {at: 25, pitch: 0}
  superman:
    name: "<red>Süpermen"
    description: "Uçuş pozu"
    image: superman.png
    free: false
    duration: 80
    sound: entity.firework_rocket.launch
    timeline:
      - {at: 0, pose: fall_flying}
      - {at: 0, jump: 0.5}
  spin:
    name: "<blue>Fırıl"
    description: "Zıpla ve dön"
    image: spin.png
    free: false
    duration: 40
    sound: entity.player.attack.sweep
    timeline:
      - {at: 0, pose: spin_attack}
      - {at: 0, jump: 0.42}
      - {at: 0, yaw: 720, over: 30}
""";

    private static final String README = """
S-Emotes emote klasoru
======================
Buraya attigin dosyalar otomatik algilanir (auto-reload), /emote reload da yapabilirsin.

Resim (simge): png, jpg, jpeg, bmp, gif (animasyonlu GIF desteklenir)
Tanim: .yml / .json (tek dosyada cok emote: motion-starter.yml'e bak) veya .zip paket

Hareket: pose + timeline (motion-starter.yml'deki aciklamalara bak).
Not: Emotecraft (.emotecraft) / Blockbench (.bbmodel) dans animasyonlari mod olmayan
client'ta oynatilamaz; onun yerine timeline ile pozlar ve hareketler yazilir.
""";
}
