package dev.emotewheel;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Custom-drawn wheel UI built from flat sprites (resource pack): backdrop, ring, sliding highlight wedge,
 * slot icons, big preview in the hub. Fixed in the world in front of the player; selection = crosshair ray vs. wheel plane.
 */
final class WheelSession {
    private final EmoteWheelPlugin pl;
    private final WheelManager mgr;
    private final Player p;
    private final int n, timeout;
    private final Emote[] slots;
    private final Vector f, r, u;
    private final Location center;
    private final double size, ringMid, hubR;
    private final float discScale, iconScale, hubScale, labelScale, sign;
    private final List<ItemDisplay> icons = new ArrayList<>();
    private final ItemDisplay backdrop, disc, wedge, hub;
    private final TextDisplay label;
    private final BukkitTask task;
    private int hovered = -1, age, lastClick = -100;
    private boolean closing;
    private double wedgeDeg;

    WheelSession(EmoteWheelPlugin pl, WheelManager mgr, Player p) {
        this.pl = pl; this.mgr = mgr; this.p = p;
        FileConfiguration c = pl.getConfig();
        n = pl.slots(p);
        slots = new Emote[n];
        PlayerDataManager.Data data = pl.data().get(p);
        for (int i = 0; i < n; i++) {
            Emote e = pl.registry().get(data.wheel[i]);
            if (e != null && !pl.has(p, e)) e = null;
            slots[i] = e;
        }
        timeout = c.getInt("wheel.timeout-seconds", 20) * 20;
        size = c.getDouble("ui.size", 0.8);
        double dist = c.getDouble("ui.distance", 1.8);
        sign = (float) c.getDouble("ui.rotation-sign", -1);
        labelScale = (float) c.getDouble("ui.label-scale", 0.45);
        discScale = (float) (size * 2);
        ringMid = size * 0.66;
        iconScale = (float) (size * 0.42);
        hubScale = (float) (size * 0.62);
        hubR = size * 0.36;

        // wheel plane faces the player, centered on the crosshair at open time
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        if (Math.abs(dir.getY()) > 0.97) {
            double yaw = Math.toRadians(eye.getYaw());
            dir = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        f = dir.normalize();
        r = f.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        u = r.clone().crossProduct(f).normalize();
        center = eye.clone().add(f.clone().multiply(dist));

        backdrop = spawn(at(0, 0, -0.4), PackService.uiIcon("ui_backdrop"), tf(0, 0));
        disc = spawn(at(0, 0, 0), PackService.uiIcon("ui_disc_" + n), tf(0, -25));
        wedge = spawn(at(0, 0, 0.01), PackService.uiIcon("ui_wedge_" + n), tf(0, 0));
        hub = spawn(at(0, 0, 0.02), new ItemStack(Material.AIR), tf(0, 0));
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            ItemStack st = slots[i] != null ? slots[i].icon(0) : PackService.uiIcon("ui_empty");
            icons.add(spawn(at(Math.sin(a) * ringMid, Math.cos(a) * ringMid, 0.02), st, tf(0, 0)));
        }
        Location ll = at(0, -size * 1.18, 0.04);
        label = ll.getWorld().spawn(ll, TextDisplay.class, x -> {
            x.setVisibleByDefault(false);
            x.setPersistent(false);
            x.setBillboard(Display.Billboard.CENTER);
            x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            x.setShadowed(true);
            x.setAlignment(TextDisplay.TextAlignment.CENTER);
            x.setLineWidth(600);
            x.setBrightness(new Display.Brightness(15, 15));
            x.text(pl.text("wheel-hint"));
            x.setTransformation(tf(0, 0));
        });
        p.showEntity(pl, label);

        Freeze.on(p, pl);
        sound("open", 1.2f);
        task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
    }

    // ---- geometry helpers ----
    private Location at(double right, double up, double depth) {
        return center.clone().add(r.clone().multiply(right)).add(u.clone().multiply(up)).subtract(f.clone().multiply(depth));
    }

    private Transformation tf(float scale, double deg) {
        return new Transformation(new Vector3f(), new Quaternionf().rotateZ((float) Math.toRadians(sign * deg)),
                new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private ItemDisplay spawn(Location loc, ItemStack stack, Transformation t) {
        ItemDisplay d = loc.getWorld().spawn(loc, ItemDisplay.class, x -> {
            x.setVisibleByDefault(false);
            x.setPersistent(false);
            x.setItemStack(stack);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
            x.setBillboard(Display.Billboard.CENTER);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setTransformation(t);
        });
        p.showEntity(pl, d);
        return d;
    }

    // ---- loop ----
    private void tick() {
        if (closing) return;
        if (!p.isOnline() || p.isDead() || !p.getWorld().equals(center.getWorld())) { destroy(); return; }
        age++;
        if (age > timeout) { close(); return; }
        if (age == 1) {
            Util.anim(backdrop, tf(8f, 0), 8);
            Util.anim(disc, tf(discScale, 0), 8);
        }
        for (int i = 0; i < n; i++) if (age == 3 + i) Util.anim(icons.get(i), tf(i == hovered ? iconScale * 1.25f : iconScale, 0), 5);
        int idx = pick();
        if (idx != hovered && age > 6) setHover(idx);
    }

    private int pick() {
        Location eye = p.getEyeLocation();
        Vector d = eye.getDirection();
        double df = d.dot(f);
        if (df < 0.05) return -1;
        Vector rel = eye.toVector().subtract(center.toVector());
        double t = -rel.dot(f) / df;
        if (t <= 0) return -1;
        Vector hit = rel.clone().add(d.clone().multiply(t));
        double x = hit.dot(r), y = hit.dot(u);
        double dist = Math.hypot(x, y);
        if (dist < hubR * 0.9 || dist > size * 1.08) return -1;
        double ang = Math.atan2(x, y);
        if (ang < 0) ang += 2 * Math.PI;
        double step = 2 * Math.PI / n;
        return (int) Math.floor((ang + step / 2) / step) % n;
    }

    private void setHover(int idx) {
        int prev = hovered;
        hovered = idx;
        if (prev >= 0) Util.anim(icons.get(prev), tf(iconScale, 0), 3);
        if (idx >= 0) {
            Util.anim(icons.get(idx), tf(iconScale * 1.25f, 0), 3);
            double target = 360.0 * idx / n;
            wedgeDeg += ((target - wedgeDeg) % 360 + 540) % 360 - 180; // shortest way round
            Util.anim(wedge, tf(discScale, wedgeDeg), 4);
            sound("hover", 1.6f);
            Emote e = slots[idx];
            hub.setItemStack(e != null ? e.icon(0) : PackService.uiIcon("ui_empty"));
            Util.anim(hub, tf(hubScale * 0.55f, 0), 0);
            Bukkit.getScheduler().runTaskLater(pl, () -> {
                if (!closing && hovered == idx) Util.anim(hub, tf(hubScale, 0), 4);
            }, 1L);
            label.text(e == null ? pl.text("wheel-empty") : labelFor(e));
        } else {
            Util.anim(wedge, tf(0, wedgeDeg), 3);
            Util.anim(hub, tf(0, 0), 3);
            label.text(pl.text("wheel-hint"));
        }
    }

    private Component labelFor(Emote e) {
        Component c = pl.mm(e.name);
        if (!e.description.isEmpty()) c = c.append(Component.newline()).append(pl.mm("<gray>" + e.description.replace("\n", " ")));
        return c;
    }

    /** Left/right click or F while the wheel is open. */
    void click() {
        if (closing || age < 6 || age - lastClick < 4) return;
        lastClick = age;
        Emote e = hovered >= 0 ? slots[hovered] : null;
        boolean emptySlot = hovered >= 0 && e == null;
        sound("select", 1.0f);
        close();
        if (e != null) pl.emotePlayer().play(p, e, false);
        else if (emptySlot) new EmoteMenu(pl, p).open();
    }

    void close() {
        if (closing) return;
        closing = true;
        task.cancel();
        Freeze.off(p, pl);
        Util.anim(backdrop, tf(0, 0), 5);
        Util.anim(disc, tf(0, 25), 5);
        Util.anim(wedge, tf(0, wedgeDeg), 4);
        Util.anim(hub, tf(0, 0), 4);
        for (ItemDisplay d : icons) Util.anim(d, tf(0, 0), 4);
        Util.anim(label, tf(0, 0), 4);
        mgr.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(pl, this::removeEntities, 7L);
    }

    void destroy() {
        if (!closing) {
            closing = true;
            task.cancel();
            Freeze.off(p, pl);
            mgr.remove(p.getUniqueId());
        }
        removeEntities();
    }

    private void removeEntities() {
        backdrop.remove(); disc.remove(); wedge.remove(); hub.remove(); label.remove();
        for (ItemDisplay d : icons) d.remove();
    }

    private void sound(String key, float pitch) {
        String s = pl.getConfig().getString("wheel.sounds." + key);
        if (s != null && !s.isEmpty()) p.playSound(p.getLocation(), s, SoundCategory.MASTER, 0.6f, pitch);
    }
}
