package dev.emotewheel;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

/** Plays an emote: body poses/motions (timeline) and/or a floating bubble, plus sound and particles. */
public final class EmotePlayer {
    private final EmoteWheelPlugin pl;
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Long> last = new HashMap<>();

    private static final class Active {
        Player p;
        Display display;
        BukkitTask task;
        ArmorStand seat;
        Location origin;
        boolean posed, motion;
        float yawStep;
        int yawLeft;
    }

    public EmotePlayer(EmoteWheelPlugin pl) { this.pl = pl; }

    public boolean isMotion(Player p) {
        Active a = active.get(p.getUniqueId());
        return a != null && a.motion;
    }

    public boolean play(Player p, Emote e, boolean ignoreCooldown) {
        FileConfiguration c = pl.getConfig();
        long now = System.currentTimeMillis();
        if (!ignoreCooldown && now - last.getOrDefault(p.getUniqueId(), 0L) < c.getLong("playback.cooldown-ms", 1500)) {
            pl.send(p, "cooldown");
            return false;
        }
        last.put(p.getUniqueId(), now);
        stop(p);

        float scale = (float) c.getDouble("playback.bubble-scale", 0.9);
        float height = (float) c.getDouble("playback.bubble-height", 0.6);
        Active a = new Active();
        a.p = p;
        a.motion = e.motion;

        Display d = null;
        if (e.bubble && e.hasImage()) {
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
        } else if (e.bubble && e.text != null) {
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
        a.display = disp;
        if (disp != null) {
            p.addPassenger(disp);
            if (!c.getBoolean("playback.show-own-bubble", true)) p.hideEntity(pl, disp);
        }
        if (e.sound != null) p.getWorld().playSound(p.getLocation(), e.sound, SoundCategory.PLAYERS, e.volume, e.pitch);

        final int dur = e.duration <= 0 ? Integer.MAX_VALUE : Math.max(20, e.duration);
        final List<Emote.Step> tl = e.timeline;
        a.task = Bukkit.getScheduler().runTaskTimer(pl, new Runnable() {
            int t = 0, ti = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead() || (disp != null && !disp.isValid())) { stop(p); return; }
                int tick = t++;
                if (a.seat != null && tick > 2 && (!a.seat.isValid() || p.getVehicle() != a.seat)) { stop(p); return; }
                if (tick == 0) {
                    if (disp != null) Util.anim(disp, Util.tr(height, scale), 6);
                    if (e.actions.contains("jump")) p.setVelocity(p.getVelocity().setY(0.42));
                    if (e.actions.contains("swing")) p.swingMainHand();
                }
                while (ti < tl.size() && tl.get(ti).at <= tick) apply(p, a, tl.get(ti++));
                if (a.yawLeft > 0) {
                    p.setRotation(p.getLocation().getYaw() + a.yawStep, p.getLocation().getPitch());
                    a.yawLeft--;
                }
                if (e.actions.contains("spin") && tick < 20) p.setRotation(p.getLocation().getYaw() + 18f, p.getLocation().getPitch());
                if (e.particle != null && tick % 10 == 0) {
                    try { p.getWorld().spawnParticle(e.particle, p.getLocation().add(0, p.getHeight() + 0.3, 0), e.particleCount, 0.3, 0.2, 0.3, 0.02); }
                    catch (Exception ignored) {}
                }
                if (disp != null && dur != Integer.MAX_VALUE && tick == dur - 6) Util.anim(disp, Util.tr(height, 0f), 6);
                if (tick >= dur) stop(p);
            }
        }, 0L, 1L);
        active.put(p.getUniqueId(), a);
        return true;
    }

    private void apply(Player p, Active a, Emote.Step s) {
        if (s.pose != null) pose(p, a, s.pose);
        if (s.swing != null) {
            if (s.swing.equalsIgnoreCase("off") || s.swing.equalsIgnoreCase("false")) p.swingOffHand(); // YAML turns "off" into false
            else p.swingMainHand();
        }
        if (s.yaw != null) {
            int over = Math.max(1, s.over);
            a.yawStep = s.yaw.floatValue() / over;
            a.yawLeft = over;
        }
        if (s.pitch != null) p.setRotation(p.getLocation().getYaw(), s.pitch.floatValue());
        if (s.jump != null) p.setVelocity(p.getVelocity().setY(s.jump));
        if (s.sound != null) p.getWorld().playSound(p.getLocation(), s.sound, SoundCategory.PLAYERS, 1f, 1f);
        if (s.particle != null) {
            try { p.getWorld().spawnParticle(Particle.valueOf(s.particle.toUpperCase(Locale.ROOT)), p.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02); }
            catch (Exception ignored) {}
        }
    }

    private void pose(Player p, Active a, String name) {
        String n = name.toUpperCase(Locale.ROOT);
        if (n.equals("SITTING") || n.equals("SIT")) { sit(p, a); return; }
        unsit(a);
        Pose po = switch (n) {
            case "SNEAKING", "CROUCHING" -> Pose.SNEAKING;
            case "SLEEPING", "LAY", "LAYING" -> Pose.SLEEPING;
            case "SWIMMING", "CRAWL", "CRAWLING" -> Pose.SWIMMING;
            case "FALL_FLYING", "FLYING" -> Pose.FALL_FLYING;
            case "SPIN_ATTACK" -> Pose.SPIN_ATTACK;
            default -> null;
        };
        if (po == null) { p.setPose(Pose.STANDING, false); a.posed = false; return; }
        p.setPose(po, true); // "fixed": the server stops overriding the pose
        a.posed = true;
    }

    /** Sitting = riding an invisible marker armor stand (works on every client). */
    private void sit(Player p, Active a) {
        if (a.seat != null) return;
        a.origin = p.getLocation();
        Location l = p.getLocation().add(0, pl.getConfig().getDouble("playback.sit-offset", 0.1), 0);
        ArmorStand st = p.getWorld().spawn(l, ArmorStand.class, x -> {
            x.setInvisible(true);
            x.setMarker(true);
            x.setGravity(false);
            x.setSilent(true);
            x.setInvulnerable(true);
            x.setBasePlate(false);
            x.setPersistent(false);
        });
        st.addPassenger(p);
        a.seat = st;
    }

    private void unsit(Active a) {
        if (a.seat == null) return;
        ArmorStand s = a.seat;
        a.seat = null;
        s.removePassenger(a.p);
        s.remove();
        if (a.p.isOnline() && a.origin != null) a.p.teleport(a.origin);
    }

    private void finish(Active a) {
        a.task.cancel();
        if (a.display != null) a.display.remove();
        unsit(a);
        if (a.posed && a.p.isOnline()) a.p.setPose(Pose.STANDING, false);
    }

    public void stop(Player p) {
        Active a = active.remove(p.getUniqueId());
        if (a != null) finish(a);
    }

    public void stopAll() {
        List<Active> all = new ArrayList<>(active.values());
        active.clear();
        for (Active a : all) finish(a);
    }
}
