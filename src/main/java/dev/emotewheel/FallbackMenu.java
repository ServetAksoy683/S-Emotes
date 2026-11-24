package dev.emotewheel;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** Chest-GUI "wheel" for Bedrock / pre-1.19.4 clients that can't see display entities. */
public final class FallbackMenu implements InventoryHolder {
    private final EmoteWheelPlugin pl;
    private final Player p;
    private final Inventory inv;
    private final Emote[] slots;

    public FallbackMenu(EmoteWheelPlugin pl, Player p) {
        this.pl = pl; this.p = p;
        int n = pl.slots(p);
        slots = new Emote[n];
        inv = Bukkit.createInventory(this, ((n + 8) / 9) * 9, pl.text("menu-title"));
    }

    @Override public Inventory getInventory() { return inv; }

    public void open() {
        PlayerDataManager.Data d = pl.data().get(p);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            Emote e = pl.registry().get(d.wheel[i]);
            if (e != null && !pl.has(p, e)) e = null;
            slots[i] = e;
            if (e != null && e.hasImage()) ids.add(e.mapIds.get(0));
        }
        pl.maps().ensure(p, ids);
        for (int i = 0; i < slots.length; i++) {
            Emote e = slots[i];
            ItemStack it = e != null ? e.icon(0) : new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            it.editMeta(m -> {
                m.displayName(e != null ? pl.mm("<!italic>" + e.name) : pl.text("wheel-empty"));
                m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            });
            inv.setItem(i, it);
        }
        p.openInventory(inv);
    }

    public void click(int raw) {
        if (raw < 0 || raw >= slots.length) return;
        Emote e = slots[raw];
        p.closeInventory();
        if (e != null) pl.emotePlayer().play(p, e, false);
        else new EmoteMenu(pl, p).open();
    }
}
