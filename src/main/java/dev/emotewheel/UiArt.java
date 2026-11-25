package dev.emotewheel;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Draws the wheel UI textures (ring, highlight wedge, backdrop, empty slot) that go into the resource pack. */
final class UiArt {
    private UiArt() {}

    static final int S = 512, C = 256;
    static final double R_OUT = 246, R_IN = 92;

    /** Ring with n slices (slice 0 centered at 12 o'clock) and an empty hub. */
    static byte[] disc(int n, Color accent) throws IOException {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        Area ring = new Area(circle(R_OUT));
        ring.subtract(new Area(circle(R_IN)));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(C, C), (float) R_OUT,
                new float[]{0f, (float) (R_IN / R_OUT), 1f},
                new Color[]{new Color(38, 40, 56, 238), new Color(38, 40, 56, 238), new Color(15, 16, 23, 238)}));
        g.fill(ring);
        g.setStroke(new BasicStroke(5f));
        g.setColor(new Color(255, 255, 255, 70));
        g.draw(circle(R_OUT - 3));
        g.setColor(alpha(accent, 170));
        g.draw(circle(R_IN));
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 255, 255, 40));
        double step = 2 * Math.PI / n;
        for (int i = 0; i < n; i++) {
            double a = (i + 0.5) * step;
            g.draw(new Line2D.Double(C + Math.sin(a) * (R_IN + 4), C - Math.cos(a) * (R_IN + 4),
                    C + Math.sin(a) * (R_OUT - 4), C - Math.cos(a) * (R_OUT - 4)));
        }
        g.setColor(new Color(10, 11, 16, 235));
        g.fill(circle(R_IN - 3));
        g.dispose();
        return png(img);
    }

    /** Highlight for slice 0 (top); the plugin rotates it to the hovered slice. */
    static byte[] wedge(int n, Color accent) throws IOException {
        BufferedImage img = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        double half = 180.0 / n - 1.0;
        Area a = new Area(new Arc2D.Double(C - R_OUT + 8, C - R_OUT + 8, 2 * (R_OUT - 8), 2 * (R_OUT - 8), 90 - half, 2 * half, Arc2D.PIE));
        a.subtract(new Area(circle(R_IN + 8)));
        g.setPaint(new RadialGradientPaint(new Point2D.Float(C, C), (float) R_OUT,
                new float[]{0f, (float) (R_IN / R_OUT), 1f},
                new Color[]{alpha(accent, 205), alpha(accent, 205), alpha(accent, 110)}));
        g.fill(a);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(alpha(accent.brighter(), 255));
        g.draw(a);
        g.dispose();
        return png(img);
    }

    /** Soft dark vignette behind the wheel so the world doesn't distract. */
    static byte[] backdrop() throws IOException {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(128, 128), 128f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 150), new Color(0, 0, 0, 115), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        return png(img);
    }

    static byte[] empty() throws IOException {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(img);
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 60));
        g.draw(new Ellipse2D.Double(16, 16, 96, 96));
        g.drawLine(64, 42, 64, 86);
        g.drawLine(42, 64, 86, 64);
        g.dispose();
        return png(img);
    }

    private static Ellipse2D circle(double r) { return new Ellipse2D.Double(C - r, C - r, 2 * r, 2 * r); }

    private static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    private static Graphics2D aa(BufferedImage i) {
        Graphics2D g = i.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static byte[] png(BufferedImage i) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(i, "png", bo);
        return bo.toByteArray();
    }
}
