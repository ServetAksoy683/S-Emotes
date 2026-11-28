package dev.emotewheel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        if (p.getWalkSpeed() == 0f) p.setWalkSpeed(0.2f); // leftover from older versions
        Bukkit.getScheduler().runTaskLater(pl, () -> { if (p.isOnline()) pl.pack().send(p); }, 40L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.emotePlayer().stop(p);
        pl.pack().forget(p.getUniqueId());
        pl.data().unload(p.getUniqueId());
        lastSneak.remove(p.getUniqueId());
    }

    @EventHandler public void onPack(PlayerResourcePackStatusEvent e) {
        if (!PackService.ID.equals(e.getID())) return;
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> pl.pack().setLoaded(e.getPlayer(), true);
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> pl.pack().setLoaded(e.getPlayer(), false);
            default -> { }
        }
    }

    // ---- open the wheel ----
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!"SWAP_HAND".equalsIgnoreCase(pl.getConfig().getString("trigger.mode", "SWAP_HAND")) || !pl.canUse(p)) return;
        e.setCancelled(true);
        if (pl.getConfig().getBoolean("debug", false)) pl.getLogger().info(p.getName() + " F tusuna bastigi icin cark aciliyor");
        pl.wheels().open(p);
    }

    @EventHandler public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        if (pl.emotePlayer().isMotion(p)) { pl.emotePlayer().stop(p); return; } // shift ends sit/lay/etc.
        if (!"SNEAK_DOUBLE".equalsIgnoreCase(pl.getConfig().getString("trigger.mode", "")) || !pl.canUse(p)) return;
        long now = System.currentTimeMillis();
        if (now - lastSneak.getOrDefault(p.getUniqueId(), 0L) < 350) { lastSneak.remove(p.getUniqueId()); pl.wheels().open(p); }
        else lastSneak.put(p.getUniqueId(), now);
    }

    // ---- emote voucher (what you put into your crate plugin) ----
    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(pl.voucherKey(), PersistentDataType.STRING);
        if (id == null) return;
        Player p = e.getPlayer();
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

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) return;
        Player p = e.getPlayer();
        if (!pl.emotePlayer().isMotion(p) || !pl.getConfig().getBoolean("playback.cancel-on-move", true)) return;
        Location f = e.getFrom(), t = e.getTo();
        double dx = t.getX() - f.getX(), dz = t.getZ() - f.getZ();
        if (dx * dx + dz * dz > 0.01) pl.emotePlayer().stop(p);
    }

    // ---- cleanup ----
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) { pl.emotePlayer().stop(e.getPlayer()); }
    @EventHandler public void onTeleport(PlayerTeleportEvent e) { pl.emotePlayer().stop(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { pl.emotePlayer().stop(e.getEntity()); }

    // ---- screens ----
    @EventHandler public void onClick(InventoryClickEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof EmoteMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) m.click(e.getRawSlot(), e.getClick());
        } else if (h instanceof WheelMenu m) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) m.click(e.getRawSlot());
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent e) {
        InventoryHolder h = e.getView().getTopInventory().getHolder();
        if (h instanceof EmoteMenu || h instanceof WheelMenu) e.setCancelled(true);
    }
}
