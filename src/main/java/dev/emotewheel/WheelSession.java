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
import org.bukkit.util.Vector;

/** One open wheel: display entities visible only to the player; selection by crosshair ray vs. wheel plane. */
final class WheelSession {
    private final EmoteWheelPlugin pl;
    private final WheelManager mgr;
    private final Player p;
    private final int n;
    private final Emote[] slots;
    private final Vector f, r, u;
    private final Location center;
    private final List<ItemDisplay> items = new ArrayList<>();
    private final TextDisplay label;
    private final float rad, slotScale, hoverScale, dead, oldWalk;
    private final int timeout;
    private final BukkitTask task;
    private int hovered = -1, age, lastClick = -100;
    private boolean closing;

    WheelSession(EmoteWheelPlugin pl, WheelManager mgr, Player p) {
        this.pl = pl; this.mgr = mgr; this.p = p;
        FileConfiguration c = pl.getConfig();
        n = pl.slots(p);
        slots = new Emote[n];
        PlayerDataManager.Data data = pl.data().get(p);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Emote e = pl.registry().get(data.wheel[i]);
            if (e != null && !pl.has(p, e)) e = null;
            slots[i] = e;
            if (e != null && e.hasImage()) ids.add(e.mapIds.get(0));
        }
        pl.maps().ensure(p, ids);

        rad = (float) c.getDouble("wheel.radius", 0.95);
        slotScale = (float) c.getDouble("wheel.slot-scale", 0.6);
        hoverScale = (float) c.getDouble("wheel.hover-scale", 0.85);
        dead = (float) c.getDouble("wheel.deadzone", 0.3);
        timeout = c.getInt("wheel.timeout-seconds", 20) * 20;

        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        f = dir.normalize();
        r = f.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        u = r.clone().crossProduct(f).normalize();
        center = p.getEyeLocation().add(f.clone().multiply(c.getDouble("wheel.distance", 2.4)));

        Material em = Material.matchMaterial(c.getString("wheel.empty-material", "LIGHT_GRAY_STAINED_GLASS_PANE"));
        if (em == null) em = Material.GRAY_STAINED_GLASS_PANE;
        World w = center.getWorld();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            Location loc = center.clone().add(r.clone().multiply(Math.sin(a) * rad)).add(u.clone().multiply(Math.cos(a) * rad));
            ItemStack stack = slots[i] != null ? slots[i].icon(0) : new ItemStack(em);
            ItemDisplay d = w.spawn(loc, ItemDisplay.class, x -> {
                x.setVisibleByDefault(false);
                x.setPersistent(false);
                x.setItemStack(stack);
                x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GUI);
                x.setBillboard(Display.Billboard.CENTER);
                x.setBrightness(new Display.Brightness(15, 15));
                x.setTransformation(Util.tr(0, 0f));
            });
            p.showEntity(pl, d);
            items.add(d);
        }
        label = w.spawn(center, TextDisplay.class, x -> {
            x.setVisibleByDefault(false);
            x.setPersistent(false);
            x.setBillboard(Display.Billboard.CENTER);
            x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            x.setShadowed(true);
            x.setAlignment(TextDisplay.TextAlignment.CENTER);
            x.setBrightness(new Display.Brightness(15, 15));
            x.text(pl.text("wheel-hint"));
            x.setTransformation(Util.tr(0, 0f));
        });
        p.showEntity(pl, label);

        float ow = p.getWalkSpeed();
        oldWalk = ow <= 0 ? 0.2f : ow;
        p.setWalkSpeed(0f); // "freeze" without cancelling movement packets (no camera jitter)
        sound("open", 1.2f);
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(pl, this::tick, 1L, 1L);
    }

    private void tick() {
        if (closing) return;
        if (!p.isOnline() || p.isDead() || !p.getWorld().equals(center.getWorld())) { destroy(); return; }
        age++;
        if (age > timeout) { close(); return; }
        for (int i = 0; i < n; i++) if (age == 1 + i) Util.anim(items.get(i), Util.tr(0, i == hovered ? hoverScale : slotScale), 5);
        if (age == 1) Util.anim(label, Util.tr(0, 0.8f), 5);
        int idx = pick();
        if (idx != hovered && age > 1) setHover(idx);
        if (hovered >= 0 && slots[hovered] != null && slots[hovered].animated() && age % slots[hovered].frameDelay == 0)
            items.get(hovered).setItemStack(slots[hovered].icon(age / slots[hovered].frameDelay));
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
        if (dist < dead || dist > rad * 2.0) return -1;
        double ang = Math.atan2(x, y);
        if (ang < 0) ang += 2 * Math.PI;
        double step = 2 * Math.PI / n;
        return (int) Math.floor((ang + step / 2) / step) % n;
    }

    private void setHover(int idx) {
        if (hovered >= 0) {
            Util.anim(items.get(hovered), Util.tr(0, slotScale), 3);
            if (slots[hovered] != null) items.get(hovered).setItemStack(slots[hovered].icon(0));
        }
        hovered = idx;
        if (idx >= 0) {
            Util.anim(items.get(idx), Util.tr(0, hoverScale), 3);
            sound("hover", 1.6f);
            Emote e = slots[idx];
            if (e != null && e.animated()) pl.maps().ensure(p, e.mapIds);
            label.text(e == null ? pl.text("wheel-empty") : labelFor(e));
        } else {
            label.text(pl.text("wheel-hint"));
        }
    }

    private Component labelFor(Emote e) {
        Component c = pl.mm(e.name);
        if (!e.description.isEmpty()) c = c.append(Component.newline()).append(pl.mm("<gray>" + e.description.replace("\n", " ")));
        return c;
    }

    /** Called on left/right click or F while the wheel is open. */
    void click() {
        if (closing || age < 4 || age - lastClick < 4) return;
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
        p.setWalkSpeed(oldWalk);
        for (ItemDisplay d : items) Util.anim(d, Util.tr(0, 0f), 4);
        Util.anim(label, Util.tr(0, 0f), 4);
        mgr.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(pl, this::removeEntities, 6L);
    }

    void destroy() {
        if (!closing) {
            closing = true;
            task.cancel();
            p.setWalkSpeed(oldWalk);
            mgr.remove(p.getUniqueId());
        }
        removeEntities();
    }

    private void removeEntities() {
        for (ItemDisplay d : items) d.remove();
        label.remove();
    }

    private void sound(String key, float pitch) {
        String s = pl.getConfig().getString("wheel.sounds." + key);
        if (s != null && !s.isEmpty()) p.playSound(p.getLocation(), s, SoundCategory.MASTER, 0.6f, pitch);
    }
}
