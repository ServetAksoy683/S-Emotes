package dev.emotewheel;

import java.util.ArrayList;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

/** Holds the player still while the wheel is open (no walking, no jump-hopping). Transient: never saved to disk. */
final class Freeze {
    private Freeze() {}

    static void on(Player p, EmoteWheelPlugin pl) {
        NamespacedKey k = new NamespacedKey(pl, "wheel_freeze");
        apply(p.getAttribute(Attribute.MOVEMENT_SPEED), k);
        apply(p.getAttribute(Attribute.JUMP_STRENGTH), k);
    }

    static void off(Player p, EmoteWheelPlugin pl) {
        NamespacedKey k = new NamespacedKey(pl, "wheel_freeze");
        remove(p.getAttribute(Attribute.MOVEMENT_SPEED), k);
        remove(p.getAttribute(Attribute.JUMP_STRENGTH), k);
    }

    private static void apply(AttributeInstance ai, NamespacedKey k) {
        if (ai == null) return;
        remove(ai, k);
        ai.addTransientModifier(new AttributeModifier(k, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private static void remove(AttributeInstance ai, NamespacedKey k) {
        if (ai == null) return;
        for (AttributeModifier m : new ArrayList<>(ai.getModifiers())) if (k.equals(m.getKey())) ai.removeModifier(m);
    }
}
