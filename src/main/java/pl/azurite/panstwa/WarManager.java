package pl.azurite.panstwa;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class WarManager {

    public static class Capture {
        final Location banner;
        final String chunk;
        final String attackerTown;   // klucz miasta
        final String defenderTown;   // klucz miasta
        final String attNation;
        final String defNation;
        final String attName;
        final String defName;
        final int total;
        int left;
        final BossBar bar;

        Capture(Location banner, String chunk, Store.Town att, Store.Town def, int total, BossBar bar) {
            this.banner = banner;
            this.chunk = chunk;
            this.attackerTown = Store.k(att.name);
            this.defenderTown = Store.k(def.name);
            this.attNation = att.nation;
            this.defNation = def.nation;
            this.attName = att.name;
            this.defName = def.name;
            this.total = total;
            this.left = total;
            this.bar = bar;
        }
    }

    private final PanstwaPlugin plugin;
    private final Map<String, Capture> captures = new HashMap<>();
    private final BukkitTask task;

    public WarManager(PanstwaPlugin plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ---------- zapytania ----------

    public Capture captureIn(Chunk c) {
        return captures.get(Store.key(c));
    }

    public Capture captureAt(Block b) {
        Capture c = captures.get(Store.key(b.getChunk()));
        if (c == null) return null;
        Location l = c.banner;
        return (l.getBlockX() == b.getX() && l.getBlockY() == b.getY() && l.getBlockZ() == b.getZ()) ? c : null;
    }

    // ---------- start / stop ----------

    public void start(Block b, Store.Town att, Store.Town def) {
        String ck = Store.key(b.getChunk());
        int total = Math.max(1, plugin.getConfig().getInt("capture-minutes", 5)) * 60;
        BossBar bar = Bukkit.createBossBar("§cPrzejmowanie: " + def.name + " §7<- §c" + att.name,
                BarColor.RED, BarStyle.SOLID);
        bar.setProgress(0.0);
        Capture c = new Capture(b.getLocation(), ck, att, def, total, bar);
        captures.put(ck, c);
        sides(c, "§6[Państwa] §cMiasto " + att.name + " rozpoczęło przejmowanie chunka miasta " + def.name
                + "! Zniszczcie czerwony baner w ciągu " + (total / 60) + " min!");
    }

    public void stop(Capture c, String msg) {
        captures.remove(c.chunk);
        c.bar.removeAll();
        if (msg != null) sides(c, msg);
    }

    public void cancelBetween(String nationA, String nationB) {
        for (Capture c : new ArrayList<>(captures.values())) {
            boolean ab = nationA.equalsIgnoreCase(c.attNation) && nationB.equalsIgnoreCase(c.defNation);
            boolean ba = nationB.equalsIgnoreCase(c.attNation) && nationA.equalsIgnoreCase(c.defNation);
            if (ab || ba) {
                c.banner.getBlock().setType(Material.AIR);
                stop(c, "§6[Państwa] §aWojna zakończona - przejmowanie przerwane.");
            }
        }
    }

    public void cancelFor(String nation) {
        for (Capture c : new ArrayList<>(captures.values())) {
            if (nation.equalsIgnoreCase(c.attNation) || nation.equalsIgnoreCase(c.defNation)) {
                c.banner.getBlock().setType(Material.AIR);
                stop(c, "§6[Państwa] §aPrzejmowanie przerwane.");
            }
        }
    }

    public void cancelTown(String townKey) {
        for (Capture c : new ArrayList<>(captures.values())) {
            if (townKey.equals(c.attackerTown) || townKey.equals(c.defenderTown)) {
                c.banner.getBlock().setType(Material.AIR);
                stop(c, "§6[Państwa] §aPrzejmowanie przerwane.");
            }
        }
    }

    public void shutdown() {
        task.cancel();
        for (Capture c : new ArrayList<>(captures.values())) c.bar.removeAll();
        captures.clear();
    }

    // ---------- tick co sekundę ----------

    private void tick() {
        for (Capture c : new ArrayList<>(captures.values())) {
            Material m = c.banner.getBlock().getType();
            if (m != Material.RED_BANNER && m != Material.RED_WALL_BANNER) {
                stop(c, "§6[Państwa] §aCzerwony baner został zniszczony! Przejmowanie chunka przerwane.");
                continue;
            }
            Store.Town att = plugin.store.towns.get(c.attackerTown);
            Store.Town def = plugin.store.towns.get(c.defenderTown);
            if (att == null || def == null
                    || !c.defenderTown.equals(plugin.store.claims.get(c.chunk))
                    || !plugin.store.atWar(c.attNation, c.defNation)) {
                c.banner.getBlock().setType(Material.AIR);
                stop(c, "§6[Państwa] §aPrzejmowanie przerwane.");
                continue;
            }

            c.left--;
            c.bar.setProgress(Math.max(0.0, Math.min(1.0, 1.0 - (double) c.left / c.total)));
            c.bar.setTitle("§cPrzejmowanie: " + c.defName + " §7<- §c" + c.attName
                    + " §7(" + (c.left / 60) + ":" + String.format("%02d", c.left % 60) + ")");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isSide(p, c)) c.bar.addPlayer(p);
            }

            if (c.left <= 0) finish(c);
        }
    }

    private void finish(Capture c) {
        plugin.store.claims.put(c.chunk, c.attackerTown);
        c.banner.getBlock().setType(Material.AIR);
        captures.remove(c.chunk);
        c.bar.removeAll();
        plugin.store.save();
        sides(c, "§6[Państwa] §cMiasto " + c.attName + " przejęło chunk miasta " + c.defName + "!");
    }

    // ---------- wiadomości ----------

    private boolean isSide(Player p, Capture c) {
        Store.Town t = plugin.store.townOf(p.getUniqueId());
        if (t == null || t.nation == null) return false;
        return t.nation.equalsIgnoreCase(c.attNation) || t.nation.equalsIgnoreCase(c.defNation);
    }

    private void sides(Capture c, String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isSide(p, c)) p.sendMessage(msg);
        }
    }
}
