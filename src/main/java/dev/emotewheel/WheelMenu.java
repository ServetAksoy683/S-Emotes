package dev.emotewheel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The wheel screen: equipped emotes on a ring, click one to play it. Centre button opens the equip screen. */
public final class WheelMenu implements InventoryHolder {
    static final int HUB = 22; // column 4, row 2
    private final EmoteWheelPlugin pl;
    private final Player p;
    private final int n;
    private final int[] ring;
    private final Emote[] slots;
    private final Inventory inv;

    public WheelMenu(EmoteWheelPlugin pl, Player p) {
        this.pl = pl; this.p = p;
        n = pl.slots(p);
        ring = Layout.ring(n, 4, 2, 0, 8);
        slots = new Emote[n];
        inv = Bukkit.createInventory(this, 54, pl.wheels().art(p) ? PackService.artTitle(true, n) : pl.text("menu-title"));
    }

    @Override public Inventory getInventory() { return inv; }

    public void open() {
        PlayerDataManager.Data d = pl.data().get(p);
        for (int i = 0; i < n; i++) {
            Emote e = pl.registry().get(d.wheel[i]);
            if (e != null && !pl.has(p, e)) e = null;
            slots[i] = e;
            inv.setItem(ring[i], e != null
                    ? MenuItems.emote(pl, e, pl.lines("lore-wheel"), false)
                    : MenuItems.button("ui_empty", pl.item("wheel-empty"), pl.lines("lore-slot-empty")));
        }
        inv.setItem(HUB, MenuItems.button("ui_hub", pl.item("hub-equip"), pl.lines("hub-equip-lore")));
        p.openInventory(inv);
    }

    public void click(int raw) {
        if (raw == HUB) { new EmoteMenu(pl, p).open(); return; }
        for (int i = 0; i < n; i++) {
            if (ring[i] != raw) continue;
            final Emote e = slots[i];
            if (e == null) { new EmoteMenu(pl, p).open(); return; }
            p.closeInventory();
            Bukkit.getScheduler().runTask(pl, () -> pl.emotePlayer().play(p, e, false));
            return;
        }
    }
}
