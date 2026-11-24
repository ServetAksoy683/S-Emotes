package dev.emotewheel;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

final class ImageUtil {
    private ImageUtil() {}
    static final int S = 128;

    record Gif(List<BufferedImage> frames, int delayTicks) {}

    /** Scales an image into a 128x128 ARGB map-sized canvas, keeping aspect ratio. */
    static BufferedImage fit(BufferedImage src) {
        BufferedImage out = new BufferedImage(S, S, BufferedImage.TYPE_INT_ARGB);
        double sc = Math.min((double) S / src.getWidth(), (double) S / src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * sc));
        int h = Math.max(1, (int) Math.round(src.getHeight() * sc));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, (S - w) / 2, (S - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    static String hash(BufferedImage img) {
        try {
            int[] px = img.getRGB(0, 0, S, S, null, 0, S);
            ByteBuffer bb = ByteBuffer.allocate(px.length * 4);
            bb.asIntBuffer().put(px);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bb.array()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static BufferedImage copy(BufferedImage b) {
        BufferedImage c = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(b, 0, 0, null);
        g.dispose();
        return c;
    }

    /** Stacks frames vertically into one PNG (Minecraft animated-texture format). */
    static byte[] strip(List<BufferedImage> frames) throws IOException {
        BufferedImage out = new BufferedImage(S, S * frames.size(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        for (int i = 0; i < frames.size(); i++) g.drawImage(fit(frames.get(i)), 0, i * S, null);
        g.dispose();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        ImageIO.write(out, "png", bo);
        return bo.toByteArray();
    }

    static Gif readGif(byte[] data, int maxFrames) throws IOException {
        ImageReader r = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            r.setInput(in, false, false);
            int n = r.getNumImages(true);
            int w = 0, h = 0;
            IIOMetadata sm = r.getStreamMetadata();
            if (sm != null) {
                Node lsd = child(sm.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                w = attr(lsd, "logicalScreenWidth", 0);
                h = attr(lsd, "logicalScreenHeight", 0);
            }
            if (w <= 0 || h <= 0) {
                BufferedImage f0 = r.read(0);
                w = f0.getWidth();
                h = f0.getHeight();
            }
            BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), prev = null;
            List<BufferedImage> all = new ArrayList<>();
            long totalDelay = 0;
            for (int i = 0; i < n; i++) {
                BufferedImage fr = r.read(i);
                Node root = r.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node desc = child(root, "ImageDescriptor");
                int x = attr(desc, "imageLeftPosition", 0), y = attr(desc, "imageTopPosition", 0);
                Node gce = child(root, "GraphicControlExtension");
                String disp = gce == null ? "none" : str(gce, "disposalMethod");
                totalDelay += gce == null ? 10 : attr(gce, "delayTime", 10);
                if ("restoreToPrevious".equals(disp)) prev = copy(canvas);
                Graphics2D g = canvas.createGraphics();
                g.drawImage(fr, x, y, null);
                g.dispose();
                all.add(copy(canvas));
                if ("restoreToBackgroundColor".equals(disp)) {
                    Graphics2D c = canvas.createGraphics();
                    c.setComposite(AlphaComposite.Clear);
                    c.fillRect(x, y, fr.getWidth(), fr.getHeight());
                    c.dispose();
                } else if ("restoreToPrevious".equals(disp) && prev != null) {
                    canvas = copy(prev);
                }
            }
            int step = Math.max(1, (int) Math.ceil(all.size() / (double) Math.max(1, maxFrames)));
            List<BufferedImage> out = new ArrayList<>();
            for (int i = 0; i < all.size(); i += step) out.add(all.get(i));
            double avg = Math.max(2, totalDelay / (double) Math.max(1, n));
            return new Gif(out, Math.max(1, (int) Math.round(avg * step / 5.0)));
        } finally {
            r.dispose();
        }
    }

    private static Node child(Node n, String name) {
        if (n == null) return null;
        for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling())
            if (name.equals(c.getNodeName())) return c;
        return null;
    }

    private static String str(Node n, String name) {
        if (n == null || n.getAttributes() == null) return "";
        Node a = n.getAttributes().getNamedItem(name);
        return a == null ? "" : a.getNodeValue();
    }

    private static int attr(Node n, String name, int def) {
        try { return Integer.parseInt(str(n, name)); } catch (NumberFormatException e) { return def; }
    }
}
