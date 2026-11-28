package dev.emotewheel;

import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class Util {
    private Util() {}

    static Transformation tr(float y, float s) {
        return new Transformation(new Vector3f(0, y, 0), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    /** Smooth transition of a display entity to a new transformation. */
    static void anim(Display d, Transformation t, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(t);
    }
}
