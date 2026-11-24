package dev.emotewheel;

import java.util.*;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** "Emotelarım": top = wheel slots (0..11), bottom = owned emotes not on the wheel. Select, then click a slot to equip. */
public final class EmoteMenu implements InventoryHolder {
    private static final int POOL_START = 27, PER_PAGE = 18;
    private final EmoteWheelPlugin pl;
    private final Player p;
    private final Inventory inv;
    private int page;
    private String selected;

    public EmoteMenu(EmoteWheelPlugin pl, Player p) {
        this.pl = pl; this.p = p;
        this.inv = Bukkit.createInventory(this, 54, pl.text("menu-title"));
    }

    @Override public Inventory getInventory() { return inv; }

    public void open() {
        List<Integer> ids = new ArrayList<>();
        for (Emote e : pl.registry().all()) if (pl.has(p, e) && e.hasImage()) ids.add(e.mapIds.get(0));
        pl.maps().ensure(p, ids);
        render();
        p.openInventory(inv);
    }

    private Emote equipped(PlayerDataManager.Data d, int i) {
        Emote e = pl.registry().get(d.wheel[i]);
        return e != null && pl.has(p, e) ? e : null;
    }

    private List<Emote> pool(int n) {
        PlayerDataManager.Data d = pl.data().get(p);
        Set<String> eq = new HashSet<>();
        for (int i = 0; i < n; i++) { Emote e = equipped(d, i); if (e != null) eq.add(e.id); }
        List<Emote> out = new ArrayList<>();
        for (Emote e : pl.registry().all()) if (pl.has(p, e) && !eq.contains(e.id)) out.add(e);
        return out;
    }

    private ItemStack emoteItem(Emote e, List<net.kyori.adventure.text.Component> lore, boolean glint) {
        ItemStack it = e.icon(0);
        it.editMeta(m -> {
            m.displayName(pl.mm("<!italic>" + e.name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            if (!e.description.isEmpty()) l.add(pl.mm("<!italic><gray>" + e.description.replace("\n", " ")));
            l.addAll(lore);
            m.lore(l);
            if (glint) m.setEnchantmentGlintOverride(true);
            m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    private ItemStack pane(Material mat, net.kyori.adventure.text.Component name, List<net.kyori.adventure.text.Component> lore) {
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> { m.displayName(name); if (lore != null) m.lore(lore); });
        return it;
    }

    private void render() {
        inv.clear();
        int n = pl.slots(p);
        PlayerDataManager.Data d = pl.data().get(p);
        for (int i = 0; i < PlayerDataManager.MAX; i++) {
            if (i >= n) { inv.setItem(i, pane(Material.RED_STAINED_GLASS_PANE, pl.item("menu-slot-locked"), null)); continue; }
            Emote e = equipped(d, i);
            if (e != null) inv.setItem(i, emoteItem(e, pl.lines(selected != null ? "lore-slot-place" : "lore-equipped"), false));
            else inv.setItem(i, pane(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    pl.item("menu-slot-empty", Placeholder.unparsed("number", String.valueOf(i + 1))),
                    pl.lines(selected != null ? "lore-slot-place" : "lore-slot-empty")));
        }
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, pl.mm("<!italic> "), null);
        for (int i = PlayerDataManager.MAX; i < 27; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        List<Emote> pool = pool(n);
        int pages = Math.max(1, (pool.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int k = 0; k < PER_PAGE; k++) {
            int idx = page * PER_PAGE + k;
            if (idx >= pool.size()) break;
            Emote e = pool.get(idx);
            inv.setItem(POOL_START + k, emoteItem(e, pl.lines("lore-pool"), e.id.equals(selected)));
        }
        if (page > 0) inv.setItem(45, pane(Material.ARROW, pl.item("menu-prev"), null));
        if (page < pages - 1) inv.setItem(53, pane(Material.ARROW, pl.item("menu-next"), null));
        inv.setItem(49, pane(Material.BARRIER, pl.item("menu-close"), List.of(
                pl.item("menu-page", Placeholder.unparsed("page", String.valueOf(page + 1)), Placeholder.unparsed("pages", String.valueOf(pages))))));
    }

    public void click(int raw, ClickType ct) {
        if (raw < 0 || raw >= 54) return;
        int n = pl.slots(p);
        PlayerDataManager.Data d = pl.data().get(p);
        if (raw < PlayerDataManager.MAX) {
            if (raw >= n) return;
            Emote cur = equipped(d, raw);
            if (ct.isRightClick()) { if (cur != null) preview(cur); return; }
            if (selected != null) { d.wheel[raw] = selected; selected = null; pl.data().save(p); }
            else if (cur != null) { d.wheel[raw] = null; pl.data().save(p); }
            render();
        } else if (raw >= POOL_START && raw < POOL_START + PER_PAGE) {
            List<Emote> pool = pool(n);
            int idx = page * PER_PAGE + (raw - POOL_START);
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
        } else if (raw == 45 && page > 0) { page--; render(); }
        else if (raw == 53) { page++; render(); }
        else if (raw == 49) p.closeInventory();
    }

    private void preview(Emote e) {
        p.closeInventory();
        pl.emotePlayer().play(p, e, true);
    }
}
