package dev.emotewheel;

import java.util.*;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EmoteCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("menu", "wheel", "play", "list", "give", "take", "item", "reload");
    private final EmoteWheelPlugin pl;

    public EmoteCommand(EmoteWheelPlugin pl) { this.pl = pl; }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        pl.send(s, "player-only");
        return null;
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("emotewheel.admin")) return true;
        pl.send(s, "no-permission");
        return false;
    }

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> { Player p = player(s); if (p != null && pl.canUse(p)) new EmoteMenu(pl, p).open(); }
            case "wheel" -> { Player p = player(s); if (p != null && pl.canUse(p)) pl.wheels().open(p); }
            case "list" -> {
                Player p = player(s);
                if (p == null) break;
                List<String> ids = pl.registry().all().stream().filter(e -> pl.has(p, e)).map(e -> e.id).toList();
                pl.send(p, "list-header", Placeholder.unparsed("count", String.valueOf(ids.size())), Placeholder.unparsed("list", String.join(", ", ids)));
            }
            case "play" -> {
                if (a.length < 2) { pl.send(s, "usage"); break; }
                Emote e = pl.registry().get(a[1]);
                if (e == null) { pl.send(s, "unknown-emote", Placeholder.unparsed("id", a[1])); break; }
                if (a.length >= 3) { // /emote play <id> <player> (admin)
                    if (!admin(s)) break;
                    Player t = Bukkit.getPlayerExact(a[2]);
                    if (t == null) { pl.send(s, "player-not-found", Placeholder.unparsed("player", a[2])); break; }
                    pl.emotePlayer().play(t, e, true);
                } else playSelf(s, e);
            }
            case "give", "take" -> {
                if (!admin(s)) break;
                if (a.length < 3) { pl.send(s, "usage"); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { pl.send(s, "player-not-found", Placeholder.unparsed("player", a[1])); break; }
                List<Emote> targets = new ArrayList<>();
                if (a[2].equals("*")) targets.addAll(pl.registry().all());
                else {
                    Emote e = pl.registry().get(a[2]);
                    if (e == null) { pl.send(s, "unknown-emote", Placeholder.unparsed("id", a[2])); break; }
                    targets.add(e);
                }
                for (Emote e : targets) {
                    var ph = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                            Placeholder.unparsed("player", t.getName()), Placeholder.parsed("emote", e.name)};
                    if (sub.equals("give") ? pl.data().grant(t, e) : pl.data().revoke(t, e.id)) {
                        pl.send(s, sub.equals("give") ? "granted-sender" : "taken-sender", ph);
                        pl.send(t, sub.equals("give") ? "granted-target" : "taken-target", ph);
                    }
                }
            }
            case "item" -> {
                if (!admin(s)) break;
                if (a.length < 3) { pl.send(s, "usage"); break; }
                Player t = Bukkit.getPlayerExact(a[1]);
                Emote e = pl.registry().get(a[2]);
                if (t == null) { pl.send(s, "player-not-found", Placeholder.unparsed("player", a[1])); break; }
                if (e == null) { pl.send(s, "unknown-emote", Placeholder.unparsed("id", a[2])); break; }
                int amt = 1;
                try { if (a.length > 3) amt = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException ignored) {}
                ItemStack it = pl.voucher(e, amt);
                t.getInventory().addItem(it).values().forEach(left -> t.getWorld().dropItemNaturally(t.getLocation(), left));
                pl.send(s, "voucher-given", Placeholder.unparsed("player", t.getName()), Placeholder.parsed("emote", e.name));
            }
            case "reload" -> {
                if (!admin(s)) break;
                pl.reloadAll();
                pl.send(s, "reloaded", Placeholder.unparsed("count", String.valueOf(pl.registry().all().size())));
            }
            default -> {
                Emote e = pl.registry().get(sub); // /emote heart
                if (e != null) playSelf(s, e); else pl.send(s, "usage");
            }
        }
        return true;
    }

    private void playSelf(CommandSender s, Emote e) {
        Player p = player(s);
        if (p == null || !pl.canUse(p)) return;
        if (!pl.has(p, e)) { pl.send(p, "not-owned"); return; }
        pl.emotePlayer().play(p, e, false);
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        boolean adm = s.hasPermission("emotewheel.admin");
        if (a.length == 1) {
            out.addAll(SUBS);
            for (Emote e : pl.registry().all()) if (!(s instanceof Player p) || pl.has(p, e)) out.add(e.id);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("play")) {
            for (Emote e : pl.registry().all()) out.add(e.id);
        } else if (adm && a.length == 2 && List.of("give", "take", "item").contains(a[0].toLowerCase(Locale.ROOT))) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (adm && a.length == 3 && List.of("give", "take", "item").contains(a[0].toLowerCase(Locale.ROOT))) {
            pl.registry().all().forEach(e -> out.add(e.id));
            if (!a[0].equalsIgnoreCase("item")) out.add("*");
        } else if (adm && a.length == 3 && a[0].equalsIgnoreCase("play")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return out.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).sorted().collect(Collectors.toList());
    }
}
