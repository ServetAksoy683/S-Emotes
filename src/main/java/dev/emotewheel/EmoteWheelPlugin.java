package dev.emotewheel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class EmoteWheelPlugin extends JavaPlugin {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private PackService pack;
    private EmoteRegistry registry;
    private PlayerDataManager data;
    private EmotePlayer emotePlayer;
    private WheelManager wheels;
    private NamespacedKey voucherKey;
    private BukkitTask watcher;

    @Override public void onLoad() { System.setProperty("java.awt.headless", "true"); }

    @Override public void onEnable() {
        saveDefaultConfig();
        voucherKey = new NamespacedKey(this, "voucher");
        pack = new PackService(this);
        registry = new EmoteRegistry(this);
        data = new PlayerDataManager(this);
        emotePlayer = new EmotePlayer(this);
        wheels = new WheelManager(this);
        loadEmotes();
        for (Player p : Bukkit.getOnlinePlayers()) data.get(p);
        getServer().getPluginManager().registerEvents(new Listeners(this), this);
        EmoteCommand cmd = new EmoteCommand(this);
        PluginCommand pc = getCommand("emotewheel");
        pc.setExecutor(cmd);
        pc.setTabCompleter(cmd);
        // Optional: a client mod can open the wheel (e.g. on the B key) by sending any payload on this channel.
        getServer().getMessenger().registerIncomingPluginChannel(this, "emotewheel:open",
                (ch, p, msg) -> Bukkit.getScheduler().runTask(this, () -> { if (canUse(p)) wheels.open(p); }));
        startWatcher();
    }

    @Override public void onDisable() {
        if (emotePlayer != null) emotePlayer.stopAll();
        if (data != null) data.saveAllNow();
        if (pack != null) pack.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        loadEmotes();
        startWatcher();
    }

    private void loadEmotes() {
        registry.load();
        pack.rebuild(registry.all());
    }

    private void startWatcher() {
        if (watcher != null) watcher.cancel();
        watcher = null;
        if (!getConfig().getBoolean("emotes.auto-reload", true)) return;
        watcher = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (registry.changed()) Bukkit.getScheduler().runTask(this, this::loadEmotes);
        }, 100L, 100L);
    }

    // ---- access rules ----
    public boolean has(Player p, Emote e) {
        return e.free || data.get(p).owned.contains(e.id)
                || p.hasPermission("emotewheel.emote.*") || p.hasPermission("emotewheel.emote." + e.id)
                || (e.permission != null && p.hasPermission(e.permission));
    }

    public int slots(Player p) {
        int def = getConfig().getInt("wheel.default-slots", 6);
        int max = Math.min(PlayerDataManager.MAX, getConfig().getInt("wheel.max-slots", 12));
        for (int n = max; n > def; n--) if (p.hasPermission("emotewheel.slots." + n)) return n;
        return Math.max(2, Math.min(def, max));
    }

    public boolean canUse(Player p) {
        return p.hasPermission("emotewheel.use") && !getConfig().getStringList("trigger.disabled-worlds").contains(p.getWorld().getName());
    }

    // ---- items ----
    public ItemStack voucher(Emote e, int amount) {
        ItemStack it = e.icon(0);
        it.setAmount(Math.max(1, amount));
        it.editMeta(m -> {
            m.displayName(item("voucher-name", Placeholder.parsed("emote", e.name)));
            m.lore(lines("voucher-lore"));
            m.getPersistentDataContainer().set(voucherKey, PersistentDataType.STRING, e.id);
        });
        return it;
    }

    public NamespacedKey voucherKey() { return voucherKey; }

    // ---- messages ----
    public String raw(String key) { return getConfig().getString("messages." + key, key); }
    public Component mm(String s) { return MM.deserialize(s); }
    public Component msg(String key, TagResolver... r) { return MM.deserialize(raw("prefix") + raw(key), r); }
    public Component text(String key, TagResolver... r) { return MM.deserialize(raw(key), r); }
    public Component item(String key, TagResolver... r) { return MM.deserialize("<!italic>" + raw(key), r); }
    public void send(CommandSender s, String key, TagResolver... r) { s.sendMessage(msg(key, r)); }

    public List<Component> lines(String key) {
        List<Component> out = new ArrayList<>();
        for (String s : getConfig().getStringList("messages." + key)) out.add(MM.deserialize("<!italic>" + s));
        return out;
    }

    public PackService pack() { return pack; }
    public EmoteRegistry registry() { return registry; }
    public PlayerDataManager data() { return data; }
    public EmotePlayer emotePlayer() { return emotePlayer; }
    public WheelManager wheels() { return wheels; }
}
