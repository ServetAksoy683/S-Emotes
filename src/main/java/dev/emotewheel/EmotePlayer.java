package dev.emotewheel;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

/** Plays an emote: floating image/text bubble above the head + optional sound, particles, actions. */
public final class EmotePlayer {
    private final EmoteWheelPlugin pl;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();

    private static final class Active { Display display; BukkitTask task; }

    public EmotePlayer(EmoteWheelPlugin pl) { this.pl = pl; }

    public boolean play(Player p, Emote e, boolean ignoreCooldown) {
        FileConfiguration c = pl.getConfig();
        long now = System.currentTimeMillis();
        if (!ignoreCooldown && now - last.getOrDefault(p.getUniqueId(), 0L) < c.getLong("playback.cooldown-ms", 1500)) {
            pl.send(p, "cooldown");
            return false;
        }
        last.put(p.getUniqueId(), now);
        stop(p);

        double radius = c.getDouble("playback.view-distance", 32);
        List<Player> viewers = new ArrayList<>(p.getWorld().getNearbyPlayers(p.getLocation(), radius));
        if (!viewers.contains(p)) viewers.add(p);
        float scale = (float) c.getDouble("playback.bubble-scale", 0.9);
        float height = (float) c.getDouble("playback.bubble-height", 0.6);

        Display d = null;
        if (e.hasImage()) {
            for (Player v : viewers) pl.maps().ensure(v, e.mapIds);
            ItemDisplay.ItemDisplayTransform tf;
            try { tf = ItemDisplay.ItemDisplayTransform.valueOf(c.getString("playback.display-transform", "GUI").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { tf = ItemDisplay.ItemDisplayTransform.GUI; }
            final ItemDisplay.ItemDisplayTransform ftf = tf;
            d = p.getWorld().spawn(p.getLocation(), ItemDisplay.class, x -> {
                x.setPersistent(false);
                x.setItemStack(e.icon(0));
                x.setItemDisplayTransform(ftf);
                x.setBillboard(Display.Billboard.CENTER);
                x.setBrightness(new Display.Brightness(15, 15));
                x.setTransformation(Util.tr(height, 0f));
            });
        } else if (e.text != null) {
            d = p.getWorld().spawn(p.getLocation(), TextDisplay.class, x -> {
                x.setPersistent(false);
                x.text(pl.mm(e.text));
                x.setBillboard(Display.Billboard.CENTER);
                x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                x.setShadowed(true);
                x.setBrightness(new Display.Brightness(15, 15));
                x.setTransformation(Util.tr(height, 0f));
            });
        }
        final Display disp = d;
        if (disp != null) {
            p.addPassenger(disp);
            if (!c.getBoolean("playback.show-own-bubble", true)) p.hideEntity(pl, disp);
        }
        if (e.sound != null) p.getWorld().playSound(p.getLocation(), e.sound, SoundCategory.PLAYERS, e.volume, e.pitch);

        Active a = new Active();
        a.display = disp;
        final int dur = Math.max(20, e.duration);
        a.task = Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || disp != null && !disp.isValid()) { stop(p); return; }
                t++;
                if (t == 1) {
                    if (disp != null) Util.anim(disp, Util.tr(height, scale), 6);
                    if (e.actions.contains("jump")) p.setVelocity(p.getVelocity().setY(0.42));
                    if (e.actions.contains("swing")) p.swingMainHand();
                }
                if (disp instanceof ItemDisplay id && e.animated() && t % e.frameDelay == 0) id.setItemStack(e.icon(t / e.frameDelay));
                if (e.particle != null && t % 10 == 1) {
                    try { p.getWorld().spawnParticle(e.particle, p.getLocation().add(0, p.getHeight() + 0.3, 0), e.particleCount, 0.3, 0.2, 0.3, 0.02); }
                    catch (Exception ignored) {}
                }
                if (e.actions.contains("spin") && t <= 20) p.setRotation(p.getLocation().getYaw() + 18f, p.getLocation().getPitch());
                if (disp != null && t == dur - 6) Util.anim(disp, Util.tr(height, 0f), 6);
                if (t >= dur) stop(p);
            }
        }, 0L, 1L);
        active.put(p.getUniqueId(), a);
        return true;
    }

    public void stop(Player p) {
        Active a = active.remove(p.getUniqueId());
        if (a == null) return;
        a.task.cancel();
        if (a.display != null) a.display.remove();
    }

    public void stopAll() {
        for (Active a : active.values()) { a.task.cancel(); if (a.display != null) a.display.remove(); }
        active.clear();
    }
}
