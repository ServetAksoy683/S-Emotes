package dev.emotewheel;

import java.util.*;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Equip screen: ring on the left = wheel slots, grid on the right = emotes you own that aren't on the wheel.
 * Click an emote to pick it, then click a wheel slot to equip. Clicking an equipped slot returns it to the grid.
 */
public final class EmoteMenu implements InventoryHolder {
    static final int HUB = 20, PREV = 50, CLOSE = 51, NEXT = 52, PER_PAGE = 20;
    private final EmoteWheelPlugin pl;
    private final Player p;
    private final int n;
    private final int[] ring;
    private final Inventory inv;
    private int page;
    private String selected;

    public EmoteMenu(EmoteWheelPlugin pl, Player p) {
        this.pl = pl; this.p = p;
        n = pl.slots(p);
        ring = Layout.ring(n, 2, 2, 0, 4);
        inv = Bukkit.createInventory(this, 54, pl.wheels().art(p) ? PackService.artTitle(false, n) : pl.text("menu-title"));
    }

    @Override public Inventory getInventory() { return inv; }

    public void open() { render(); p.openInventory(inv); }

    private Emote equipped(PlayerDataManager.Data d, int i) {
        Emote e = pl.registry().get(d.wheel[i]);
        return e != null && pl.has(p, e) ? e : null;
    }

    private List<Emote> pool() {
        PlayerDataManager.Data d = pl.data().get(p);
        Set<String> eq = new HashSet<>();
        for (int i = 0; i < n; i++) { Emote e = equipped(d, i); if (e != null) eq.add(e.id); }
        List<Emote> out = new ArrayList<>();
        for (Emote e : pl.registry().all()) if (pl.has(p, e) && !eq.contains(e.id)) out.add(e);
        return out;
    }

    private void render() {
        inv.clear();
        PlayerDataManager.Data d = pl.data().get(p);
        for (int i = 0; i < n; i++) {
            Emote e = equipped(d, i);
            var lore = pl.lines(selected != null ? "lore-slot-place" : (e != null ? "lore-equipped" : "lore-slot-empty"));
            inv.setItem(ring[i], e != null ? MenuItems.emote(pl, e, lore, false)
                    : MenuItems.button("ui_empty", pl.item("menu-slot-empty", Placeholder.unparsed("number", String.valueOf(i + 1))), lore));
        }
        Emote sel = selected == null ? null : pl.registry().get(selected);
        inv.setItem(HUB, sel != null ? MenuItems.emote(pl, sel, pl.lines("hub-selected-lore"), true)
                : MenuItems.button("ui_hub", pl.item("hub-back"), pl.lines("hub-back-lore")));

        List<Emote> pool = pool();
        int pages = Math.max(1, (pool.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int k = 0; k < PER_PAGE; k++) {
            int idx = page * PER_PAGE + k;
            if (idx >= pool.size()) break;
            Emote e = pool.get(idx);
            inv.setItem(Layout.pool(k), MenuItems.emote(pl, e, pl.lines("lore-pool"), e.id.equals(selected)));
        }
        if (page > 0) inv.setItem(PREV, MenuItems.button("ui_prev", pl.item("menu-prev"), null));
        if (page < pages - 1) inv.setItem(NEXT, MenuItems.button("ui_next", pl.item("menu-next"), null));
        inv.setItem(CLOSE, MenuItems.button("ui_close", pl.item("menu-close"), List.of(
                pl.item("menu-page", Placeholder.unparsed("page", String.valueOf(page + 1)), Placeholder.unparsed("pages", String.valueOf(pages))))));
    }

    public void click(int raw, ClickType ct) {
        if (raw < 0 || raw >= 54) return;
        PlayerDataManager.Data d = pl.data().get(p);
        for (int i = 0; i < n; i++) if (ring[i] == raw) { ringClick(d, i, ct); return; }
        if (raw == HUB) {
            if (selected != null) { selected = null; render(); } else new WheelMenu(pl, p).open();
            return;
        }
        int k = -1;
        for (int j = 0; j < PER_PAGE; j++) if (Layout.pool(j) == raw) { k = j; break; }
        if (k >= 0) { poolClick(d, k, ct); return; }
        if (raw == PREV && page > 0) { page--; render(); }
        else if (raw == NEXT) { page++; render(); }
        else if (raw == CLOSE) p.closeInventory();
    }

    private void ringClick(PlayerDataManager.Data d, int i, ClickType ct) {
        Emote cur = equipped(d, i);
        if (ct.isRightClick()) { if (cur != null) preview(cur); return; }
        if (selected != null) { d.wheel[i] = selected; selected = null; pl.data().save(p); }
        else if (cur != null) { d.wheel[i] = null; pl.data().save(p); }
        render();
    }

    private void poolClick(PlayerDataManager.Data d, int k, ClickType ct) {
        List<Emote> pool = pool();
        int idx = page * PER_PAGE + k;
        if (idx >= pool.size()) return;
        Emote e = pool.get(idx);
        if (ct.isRightClick()) { preview(e); return; }
        if (ct.isShiftClick()) {
            boolean placed = false;
            for (int i = 0; i < n && !placed; i++) if (equipped(d, i) == null) { d.wheel[i] = e.id; placed = true; }
            if (placed) pl.data().save(p); else pl.send(p, "menu-full");
        } else {
            selected = e.id.equals(selected) ? null : e.id;
        }
        render();
    }

    private void preview(Emote e) {
        p.closeInventory();
        Bukkit.getScheduler().runTask(pl, () -> pl.emotePlayer().play(p, e, true));
    }
}
