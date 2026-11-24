package dev.emotewheel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class Listeners implements Listener {
    private final EmoteWheelPlugin pl;
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    public Listeners(EmoteWheelPlugin pl) { this.pl = pl; }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        pl.data().get(p);
        org.bukkit.Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) pl.pack().send(p); }, 40L);
        // crash safety: a wheel freezes the player with walk speed 0
        if (p.getWalkSpeed() == 0f && pl.wheels().get(p) == null) p.setWalkSpeed(0.2f);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.wheels().close(p, true);
        pl.emotePlayer().stop(p);
        pl.data().unload(p.getUniqueId());
        lastSneak.remove(p.getUniqueId());
    }

    // ---- open / confirm triggers ----
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        WheelSession s = pl.wheels().get(p);
        if (s != null) { e.setCancelled(true); s.click(); return; }
        if (!"SWAP_HAND".equalsIgnoreCase(pl.getConfig().getString("trigger.mode", "SWAP_HAND")) || !pl.canUse(p)) return;
        e.setCancelled(true);
        pl.wheels().open(p);
    }

    @EventHandler public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        if (pl.wheels().get(p) != null) { pl.wheels().close(p, false); return; } // shift = cancel
        if (!"SNEAK_DOUBLE".equalsIgnoreCase(pl.getConfig().getString("trigger.mode", "")) || !pl.canUse(p)) return;
        long now = System.currentTimeMillis();
        if (now - lastSneak.getOrDefault(p.getUniqueId(), 0L) < 350) { lastSneak.remove(p.getUniqueId()); pl.wheels().open(p); }
        else lastSneak.put(p.getUniqueId(), now);
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        WheelSession s = pl.wheels().get(p);
        if (s != null) {
            e.setCancelled(true);
            if (e.getHand() == null || e.getHand() == EquipmentSlot.HAND) s.click();
            return;
        }
        // emote voucher (what you put into your crate plugin)
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(pl.voucherKey(), PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Emote em = pl.registry().get(id);
        if (em == null) { pl.send(p, "unknown-emote", Placeholder.unparsed("id", id)); return; }
        if (pl.data().grant(p, em)) {
            it.setAmount(it.getAmount() - 1);
            pl.send(p, "voucher-redeemed", Placeholder.parsed("emote", em.name));
        } else {
            pl.send(p, "voucher-duplicate");
        }
    }

    @EventHandler public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && pl.wheels().get(p) != null) e.setCancelled(true);
    }

    // ---- cleanup ----
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) { reset(e.getPlayer()); }
    @EventHandler public void onTeleport(PlayerTeleportEvent e) { reset(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { reset(e.getEntity()); }

    private void reset(Player p) {
        pl.wheels().close(p, true);
        pl.emotePlayer().stop(p);
    }

    // ---- menus ----
    @EventHandler public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && pl.wheels().get(p) != null) pl.wheels().close(p, true);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof EmoteMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) m.click(e.getRawSlot(), e.getClick());
        } else if (h instanceof FallbackMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) m.click(e.getRawSlot());
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof EmoteMenu || h instanceof FallbackMenu) e.setCancelled(true);
    }
}
