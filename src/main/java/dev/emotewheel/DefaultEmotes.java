package dev.emotewheel;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Generates a few starter emotes (pure shapes, no fonts needed) on first run. */
final class DefaultEmotes {
    private DefaultEmotes() {}

    static void generate(EmoteWheelPlugin pl, File dir) {
        try {
            ImageIO.write(heart(), "png", new File(dir, "heart.png"));
            ImageIO.write(smile(false), "png", new File(dir, "smile.png"));
            ImageIO.write(smile(true), "png", new File(dir, "cool.png"));
            ImageIO.write(star(), "png", new File(dir, "star.png"));
            Files.writeString(new File(dir, "starter.yml").toPath(), STARTER, StandardCharsets.UTF_8);
            Files.writeString(new File(dir, "OKU-BENI.txt").toPath(), README, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            pl.getLogger().warning("Örnek emoteler oluşturulamadı: " + ex.getMessage());
        }
    }

    private static Graphics2D g(BufferedImage i) {
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    private static BufferedImage heart() {
        BufferedImage i = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(i);
        Path2D.Double h = new Path2D.Double();
        h.moveTo(64, 114); h.curveTo(6, 70, 16, 14, 64, 44); h.curveTo(112, 14, 122, 70, 64, 114); h.closePath();
        g.setColor(new Color(0xE0245E)); g.fill(h);
        g.setColor(new Color(0x8B1538)); g.setStroke(new BasicStroke(5)); g.draw(h);
        g.dispose();
        return i;
    }

    private static BufferedImage smile(boolean cool) {
        BufferedImage i = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(i);
        g.setColor(new Color(0xFFD23F)); g.fillOval(8, 8, 112, 112);
        g.setColor(new Color(0x7A5A00)); g.setStroke(new BasicStroke(5)); g.drawOval(8, 8, 112, 112);
        g.setColor(Color.BLACK);
        if (cool) {
            g.fillRoundRect(24, 40, 36, 24, 8, 8); g.fillRoundRect(68, 40, 36, 24, 8, 8); g.fillRect(58, 46, 12, 6);
        } else {
            g.fillOval(38, 40, 14, 22); g.fillOval(76, 40, 14, 22);
        }
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawArc(34, 44, 60, 50, 200, 140);
        g.dispose();
        return i;
    }

    private static BufferedImage star() {
        BufferedImage i = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = g(i);
        Path2D.Double p = new Path2D.Double();
        for (int k = 0; k < 10; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5, r = k % 2 == 0 ? 58 : 25;
            double x = 64 + Math.cos(a) * r, y = 68 + Math.sin(a) * r;
            if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        g.setColor(new Color(0xFFC400)); g.fill(p);
        g.setColor(new Color(0x9A6B00)); g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(p);
        g.dispose();
        return i;
    }

    private static final String STARTER = """
emotes:
  heart:
    name: "<red>Kalp"
    description: "Sevgi göster"
    image: heart.png
    free: true
    sound: entity.experience_orb.pickup
    particle: HEART
    particle-count: 4
  smile:
    name: "<yellow>Gülümse"
    description: "Mutlu ol"
    image: smile.png
    free: true
    actions: [swing]
  cool:
    name: "<gold>Havalı"
    description: "Tarzını göster"
    image: cool.png
    free: true
    sound: block.note_block.bell
  star:
    name: "<yellow>Yıldız"
    description: "Parla! (kasa ödülü örneği: free yok)"
    image: star.png
    free: false
    sound: entity.player.levelup
    particle: END_ROD
    particle-count: 8
    actions: [spin, jump]
""";

    private static final String README = """
EmoteWheel emote klasörü
========================
Buraya attığın dosyalar otomatik algılanır (auto-reload), /emote reload da yapabilirsin.

1) Sadece resim at: png, jpg, jpeg, bmp, gif (animasyonlu GIF desteklenir)
   -> dosya adı emote id'si olur (kalp.png => "kalp"). free-by-default kapalıysa
      kasadan / komuttan açılana kadar kilitli olur.
2) Aynı isimde yml/json yanına koy: kalp.png + kalp.yml  (ayarlar için)
3) Tek dosyada çok emote: starter.yml'e bak ("emotes:" altında)
4) .zip paket: içine resim + yml koyup tüm klasörü zipleyip at

Tanım alanları: name, description, image, free, permission, sound, volume, pitch,
particle, particle-count, duration, actions (swing, jump, spin), text, icon
""";
}
