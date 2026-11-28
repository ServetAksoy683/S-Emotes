package dev.emotewheel;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

public final class Emote {
    /** One scripted action of a motion emote (all fields optional except "at"). */
    public static final class Step {
        public int at, over = 1;
        public String pose, swing, sound, particle;
        public Double yaw, pitch, jump;
    }

    public final String id;
    public String name, description = "", sound, text, source = "", permission;
    public float volume = 1f, pitch = 1f;
    public Particle particle;
    public int particleCount = 5;
    public boolean free;
    /** Ticks. 0 or less = until the player moves or sneaks (sit, lay ...). */
    public int duration = 60, frameDelay = 4;
    public Material fallbackIcon = Material.NAME_TAG;
    public List<String> actions = new ArrayList<>();
    public final List<Step> timeline = new ArrayList<>();
    /** Has body motion (pose/timeline): cancelled when the player moves or sneaks. */
    public boolean motion;
    /** Show the floating image/text above the head while playing. */
    public boolean bubble = true;
    /** PNG strip (frames stacked vertically) that goes into the generated resource pack. */
    public byte[] texture;
    public int frameTime;

    public Emote(String id) { this.id = id; this.name = id; }

    public boolean hasImage() { return texture != null; }

    /** Item that the resource pack renders as the emote image (item_model component). */
    public ItemStack icon(int ignored) {
        if (!hasImage()) return new ItemStack(fallbackIcon);
        ItemStack it = new ItemStack(Material.PAPER);
        it.editMeta(m -> m.setItemModel(NamespacedKey.fromString(PackService.NS + ":" + id)));
        return it;
    }
}
