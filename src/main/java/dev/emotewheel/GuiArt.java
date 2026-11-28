package dev.emotewheel;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Draws the custom screens (wheel + equip). Each image covers the top part of a 6-row chest screen (176x125 px,
 * drawn at 2x). Slot cell for slot s = (7 + 18*col, 17 + 18*row, 18, 18); slot centre = (16 + 18*col, 26 + 18*row).
 */
final class GuiArt {
    private GuiArt() {}

    static final int K = 2, W = 176, H = 125;

    static BufferedImage wheel(int n, Color accent) {
        BufferedImage img = new BufferedImage(W * K, H * K, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.scale(K, K);
        base(g, accent);
        glow(g, 88, 62, accent);
        donut(g, 88, 62, 54, 19, accent);
        for (int s : Layout.ring(n, 4, 2, 0, 8)) cell(g, s);
        hub(g, 88, 62, accent);
        g.dispose();
        return img;
    }

    static BufferedImage equip(int n, Color accent) {
        BufferedImage img = new BufferedImage(W * K, H * K, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.scale(K, K);
        base(g, accent);
        glow(g, 52, 62, accent);
        donut(g, 52, 62, 50, 19, accent);
        g.setColor(new Color(8, 9, 14));
        g.fill(new RoundRectangle2D.Double(95, 15, 76, 91, 6, 6));
        g.setStroke(new BasicStroke(1f));
        g.setColor(alpha(accent, 70));
        g.draw(new RoundRectangle2D.Double(95, 15, 76, 91, 6, 6));
        for (int s : Layout.ring(n, 2, 2, 0, 4)) cell(g, s);
        for (int k = 0; k < 20; k++) cell(g, Layout.pool(k));
        hub(g, 52, 62, accent);
        for (int s = 50; s <= 52; s++) cell(g, s);
        g.dispose();
        return img;
    }

    static BufferedImage sprite(String name, Color accent) {
        BufferedImage i = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(i);
        switch (name) {
            case "ui_empty" -> {
                g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 255, 255, 90));
                g.draw(new Ellipse2D.Double(8, 8, 48, 48));
                g.drawLine(32, 20, 32, 44);
                g.drawLine(20, 32, 44, 32);
            }
            case "ui_hub" -> {
                g.setColor(alpha(accent, 235));
                g.fill(new RoundRectangle2D.Double(10, 10, 20, 20, 6, 6));
                g.fill(new RoundRectangle2D.Double(34, 10, 20, 20, 6, 6));
                g.fill(new RoundRectangle2D.Double(10, 34, 20, 20, 6, 6));
                g.fill(new RoundRectangle2D.Double(34, 34, 20, 20, 6, 6));
            }
            case "ui_prev", "ui_next" -> {
                boolean left = name.equals("ui_prev");
                Path2D.Double t = new Path2D.Double();
                t.moveTo(left ? 42 : 22, 14);
                t.lineTo(left ? 42 : 22, 50);
                t.lineTo(left ? 16 : 48, 32);
                t.closePath();
                g.setColor(new Color(235, 235, 235));
                g.fill(t);
            }
            case "ui_close" -> {
                g.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(230, 70, 70));
                g.drawLine(16, 16, 48, 48);
                g.drawLine(48, 16, 16, 48);
            }
            default -> { }
        }
        g.dispose();
        return i;
    }

    // ---- parts ----
    private static void base(Graphics2D g, Color accent) {
        g.setPaint(new GradientPaint(0, 0, new Color(30, 32, 46), 0, H, new Color(13, 14, 21)));
        g.fillRect(0, 0, W, H);
        g.setStroke(new BasicStroke(1f));
        g.setColor(alpha(accent, 170));
        g.draw(new Rectangle2D.Double(0.5, 0.5, W - 1, H - 1));
        g.setColor(new Color(255, 255, 255, 22));
        g.draw(new Rectangle2D.Double(2.5, 2.5, W - 5, H - 5));
        g.setColor(alpha(accent, 210));
        g.fill(new RoundRectangle2D.Double(70, 6, 36, 2.5, 2, 2));
        g.setColor(new Color(8, 9, 13));
        g.fillRect(1, 106, W - 2, H - 107);
        g.setColor(alpha(accent, 90));
        g.drawLine(1, 106, W - 2, 106);
    }

    private static void glow(Graphics2D g, int cx, int cy, Color accent) {
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), 64f, new float[]{0f, 1f},
                new Color[]{alpha(accent, 60), alpha(accent, 0)}));
        g.fill(new Ellipse2D.Double(cx - 64, cy - 64, 128, 128));
    }

    private static void donut(Graphics2D g, int cx, int cy, double ro, double ri, Color accent) {
        Area ring = new Area(new Ellipse2D.Double(cx - ro, cy - ro, 2 * ro, 2 * ro));
        ring.subtract(new Area(new Ellipse2D.Double(cx - ri, cy - ri, 2 * ri, 2 * ri)));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), (float) ro,
                new float[]{0f, (float) (ri / ro), 1f},
                new Color[]{new Color(42, 44, 62), new Color(42, 44, 62), new Color(22, 23, 33)}));
        g.fill(ring);
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(alpha(accent, 110));
        g.draw(new Ellipse2D.Double(cx - ro, cy - ro, 2 * ro, 2 * ro));
        g.setColor(alpha(accent, 160));
        g.draw(new Ellipse2D.Double(cx - ri, cy - ri, 2 * ri, 2 * ri));
    }

    private static void hub(Graphics2D g, int cx, int cy, Color accent) {
        g.setColor(new Color(10, 11, 16));
        g.fill(new Ellipse2D.Double(cx - 12.5, cy - 12.5, 25, 25));
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(alpha(accent, 210));
        g.draw(new Ellipse2D.Double(cx - 12.5, cy - 12.5, 25, 25));
    }

    private static void cell(Graphics2D g, int slot) {
        double x = 7 + 18 * (slot % 9), y = 17 + 18 * (slot / 9);
        RoundRectangle2D.Double r = new RoundRectangle2D.Double(x, y, 18, 18, 4, 4);
        g.setColor(new Color(10, 11, 16));
        g.fill(r);
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(255, 255, 255, 40));
        g.draw(r);
    }

    private static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    private static Graphics2D aa(BufferedImage i) {
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static byte[] png(BufferedImage i) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(i, "png", bo);
        return bo.toByteArray();
    }
}
