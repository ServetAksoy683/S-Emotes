package dev.emotewheel;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

final class MenuItems {
    private MenuItems() {}

    static ItemStack emote(EmoteWheelPlugin pl, Emote e, List<Component> lore, boolean glint) {
        ItemStack it = e.icon(0);
        it.editMeta(m -> {
            m.displayName(pl.mm("<!italic>" + e.name));
            List<Component> l = new ArrayList<>();
            if (!e.description.isEmpty()) l.add(pl.mm("<!italic><gray>" + e.description.replace("\n", " ")));
            if (lore != null) l.addAll(lore);
            m.lore(l);
            if (glint) m.setEnchantmentGlintOverride(true);
            m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }

    static ItemStack button(String sprite, Component name, List<Component> lore) {
        ItemStack it = PackService.uiIcon(sprite);
        it.editMeta(m -> {
            m.displayName(name);
            if (lore != null) m.lore(lore);
            m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
        return it;
    }
}
