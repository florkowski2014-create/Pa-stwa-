package pl.azurite.panstwa;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import pl.azurite.panstwa.Store.Nation;
import pl.azurite.panstwa.Store.Town;

import java.util.*;
import java.util.stream.Collectors;

public class PCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6[Państwa] §f";

    private final PanstwaPlugin plugin;
    private final Store st;
    private final Map<UUID, String> townInvites = new HashMap<>();      // gracz -> klucz miasta
    private final Map<String, String> nationInvites = new HashMap<>();  // klucz miasta -> nazwa państwa

    public PCommand(PanstwaPlugin plugin) {
        this.plugin = plugin;
        this.st = plugin.store;
    }

    private void msg(Player p, String s) {
        p.sendMessage(PREFIX + s);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Tylko dla graczy.");
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create", "new" -> create(p, a);
            case "toggle" -> toggle(p, a);
            case "delete" -> delete(p);
            case "claim" -> claim(p);
            case "unclaim" -> unclaim(p);
            case "add", "invite" -> add(p, a);
            case "join" -> join(p, a);
            case "kick" -> kick(p, a);
            case "leave" -> leave(p);
            case "spawn" -> spawn(p);
            case "set" -> {
                if (a.length > 1 && a[1].equalsIgnoreCase("spawn")) setSpawn(p);
                else msg(p, "§cUżycie: /p set spawn");
            }
            case "setspawn" -> setSpawn(p);
            case "info" -> info(p, a);
            case "list" -> list(p);
            case "online" -> online(p);
            case "panstwo", "nation", "n" -> nation(p, a);
            case "wojna", "war" -> war(p, a);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage("§6===== Państwa - pomoc =====");
        p.sendMessage("§e/p create <nazwa> §7- załóż miasto (chunk, na którym stoisz)");
        p.sendMessage("§e/p claim | unclaim §7- dodaj / usuń chunk miasta");
        p.sendMessage("§e/p add <gracz> §7- zaproś, §e/p join <miasto> §7- dołącz");
        p.sendMessage("§e/p kick <gracz> | leave | delete");
        p.sendMessage("§e/p toggle pvp|mobs|explosions|fire §7- flagi miasta");
        p.sendMessage("§e/p spawn | set spawn | info [miasto] | list | online");
        p.sendMessage("§e/p panstwo create|add|join|leave|kick|delete|info|list");
        p.sendMessage("§e/p wojna wypowiedz <państwo> §7- wypowiedz wojnę");
        p.sendMessage("§e/p wojna poddaj <państwo> | lista");
    }

    // ---------- sprawdzanie uprawnień ----------

    private Town mayorTown(Player p) {
        Town t = st.townOf(p.getUniqueId());
        if (t == null) {
            msg(p, "§cNie masz miasta.");
            return null;
        }
        if (!t.mayor.equals(p.getUniqueId())) {
            msg(p, "§cTylko burmistrz może to zrobić.");
            return null;
        }
        return t;
    }

    private Nation leaderNation(Player p) {
        Town t = mayorTown(p);
        if (t == null) return null;
        Nation n = st.nationOf(t);
        if (n == null) {
            msg(p, "§cTwoje miasto nie należy do żadnego państwa.");
            return null;
        }
        if (!n.capital.equalsIgnoreCase(t.name)) {
            msg(p, "§cTylko burmistrz stolicy państwa może to zrobić.");
            return null;
        }
        return n;
    }

    // ---------- miasta ----------

    private void create(Player p, String[] a) {
        if (a.length < 2) {
            msg(p, "§cUżycie: /p create <nazwa>");
            return;
        }
        String name = a[1];
        if (!name.matches("[A-Za-z0-9_]{3,20}")) {
            msg(p, "§cNazwa: 3-20 znaków, litery, cyfry lub _.");
            return;
        }
        if (st.townOf(p.getUniqueId()) != null) {
            msg(p, "§cJuż należysz do miasta.");
            return;
        }
        if (st.towns.containsKey(Store.k(name))) {
            msg(p, "§cTaka nazwa jest zajęta.");
            return;
        }
        Chunk c = p.getLocation().getChunk();
        if (st.claims.containsKey(Store.key(c))) {
            msg(p, "§cTen chunk jest już zajęty.");
            return;
        }
        Town t = new Town();
        t.name = name;
        t.mayor = p.getUniqueId();
        t.members.add(p.getUniqueId());
        t.spawn = Store.fmt(p.getLocation());
        st.towns.put(Store.k(name), t);
        st.claims.put(Store.key(c), Store.k(name));
        st.save();
        Bukkit.broadcastMessage(PREFIX + "§a" + p.getName() + " założył miasto §e" + name + "§a!");
    }

    private void delete(Player p) {
        Town t = mayorTown(p);
        if (t == null) return;
        Nation n = st.nationOf(t);
        if (n != null && n.capital.equalsIgnoreCase(t.name)) removeNation(n);
        String tk = Store.k(t.name);
        plugin.war.cancelTown(tk);
        st.claims.values().removeIf(v -> v.equals(tk));
        st.towns.remove(tk);
        st.save();
        Bukkit.broadcastMessage(PREFIX + "§cMiasto " + t.name + " zostało usunięte.");
    }

    private boolean adjacent(Chunk c, String townKey) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] o : d) {
            String k = c.getWorld().getName() + ";" + (c.getX() + o[0]) + ";" + (c.getZ() + o[1]);
            if (townKey.equals(st.claims.get(k))) return true;
        }
        return false;
    }

    private void claim(Player p) {
        Town t = mayorTown(p);
        if (t == null) return;
        Chunk c = p.getLocation().getChunk();
        String key = Store.key(c);
        if (st.claims.containsKey(key)) {
            msg(p, "§cTen chunk jest już zajęty.");
            return;
        }
        int max = plugin.getConfig().getInt("max-chunks", 0);
        if (max > 0 && st.chunkCount(t) >= max) {
            msg(p, "§cOsiągnięto limit chunków (" + max + ").");
            return;
        }
        if (plugin.getConfig().getBoolean("require-adjacent", true) && !adjacent(c, Store.k(t.name))) {
            msg(p, "§cChunk musi przylegać do terenu twojego miasta.");
            return;
        }
        st.claims.put(key, Store.k(t.name));
        st.save();
        msg(p, "§aChunk dodany do miasta §e" + t.name + "§a.");
    }

    private void unclaim(Player p) {
        Town t = mayorTown(p);
        if (t == null) return;
        Chunk c = p.getLocation().getChunk();
        String key = Store.key(c);
        if (!Store.k(t.name).equals(st.claims.get(key))) {
            msg(p, "§cTo nie jest chunk twojego miasta.");
            return;
        }
        Location sp = Store.parse(t.spawn);
        if (sp != null && Store.key(sp.getChunk()).equals(key)) {
            msg(p, "§cNie możesz usunąć chunka ze spawnem miasta.");
            return;
        }
        WarManager.Capture cap = plugin.war.captureIn(c);
        if (cap != null) {
            cap.banner.getBlock().setType(org.bukkit.Material.AIR);
            plugin.war.stop(cap, null);
        }
        st.claims.remove(key);
        st.save();
        msg(p, "§aChunk usunięty z miasta.");
    }

    private void add(Player p, String[] a) {
        Town t = mayorTown(p);
        if (t == null) return;
        if (a.length < 2) {
            msg(p, "§cUżycie: /p add <gracz>");
            return;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            msg(p, "§cGracz musi być online.");
            return;
        }
        if (st.townOf(target.getUniqueId()) != null) {
            msg(p, "§cTen gracz ma już miasto.");
            return;
        }
        townInvites.put(target.getUniqueId(), Store.k(t.name));
        msg(p, "§aZaproszenie wysłane do " + target.getName() + ".");
        msg(target, "§aZaproszenie do miasta §e" + t.name + "§a. Wpisz §e/p join " + t.name);
    }

    private void join(Player p, String[] a) {
        if (a.length < 2) {
            msg(p, "§cUżycie: /p join <miasto>");
            return;
        }
        if (st.townOf(p.getUniqueId()) != null) {
            msg(p, "§cJuż należysz do miasta.");
            return;
        }
        String tk = Store.k(a[1]);
        Town t = st.towns.get(tk);
        if (t == null || !tk.equals(townInvites.get(p.getUniqueId()))) {
            msg(p, "§cNie masz zaproszenia do tego miasta.");
            return;
        }
        t.members.add(p.getUniqueId());
        townInvites.remove(p.getUniqueId());
        st.save();
        msg(p, "§aDołączyłeś do miasta §e" + t.name + "§a.");
        Player mayor = Bukkit.getPlayer(t.mayor);
        if (mayor != null) msg(mayor, "§a" + p.getName() + " dołączył do miasta.");
    }

    private void kick(Player p, String[] a) {
        Town t = mayorTown(p);
        if (t == null) return;
        if (a.length < 2) {
            msg(p, "§cUżycie: /p kick <gracz>");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(a[1]);
        if (op == null || !t.members.contains(op.getUniqueId())) {
            msg(p, "§cTaki gracz nie jest w twoim mieście.");
            return;
        }
        if (op.getUniqueId().equals(t.mayor)) {
            msg(p, "§cNie możesz wyrzucić burmistrza.");
            return;
        }
        t.members.remove(op.getUniqueId());
        st.save();
        msg(p, "§aGracz wyrzucony z miasta.");
        if (op.isOnline() && op.getPlayer() != null) msg(op.getPlayer(), "§cZostałeś wyrzucony z miasta " + t.name + ".");
    }

    private void leave(Player p) {
        Town t = st.townOf(p.getUniqueId());
        if (t == null) {
            msg(p, "§cNie masz miasta.");
            return;
        }
        if (t.mayor.equals(p.getUniqueId())) {
            msg(p, "§cBurmistrz nie może wyjść. Użyj /p delete.");
            return;
        }
        t.members.remove(p.getUniqueId());
        st.save();
        msg(p, "§aOpuściłeś miasto " + t.name + ".");
    }

    private void spawn(Player p) {
        Town t = st.townOf(p.getUniqueId());
        if (t == null) {
            msg(p, "§cNie masz miasta.");
            return;
        }
        Location l = Store.parse(t.spawn);
        if (l == null) {
            msg(p, "§cSpawn miasta jest niedostępny.");
            return;
        }
        p.teleport(l);
        msg(p, "§aTeleportacja do spawnu miasta.");
    }

    private void setSpawn(Player p) {
        Town t = mayorTown(p);
        if (t == null) return;
        Chunk c = p.getLocation().getChunk();
        if (!Store.k(t.name).equals(st.claims.get(Store.key(c)))) {
            msg(p, "§cSpawn musi być w chunku twojego miasta.");
