package pl.azurite.panstwa;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Store {

    public static class Town {
        public String name;
        public UUID mayor;
        public final Set<UUID> members = new HashSet<>();
        public String nation;   // nazwa państwa albo null
        public String spawn;    // world,x,y,z,yaw,pitch
        public boolean pvp = false;        // PvP w mieście
        public boolean mobs = false;       // spawn wrogich mobów
        public boolean explosions = false; // wybuchy
        public boolean fire = false;       // ogień / podpalenia
    }

    public static class Nation {
        public String name;
        public String capital;  // nazwa miasta-stolicy
    }

    public final Map<String, Town> towns = new LinkedHashMap<>();
    public final Map<String, Nation> nations = new LinkedHashMap<>();
    public final Map<String, String> claims = new HashMap<>(); // klucz chunka -> klucz miasta
    public final Set<String> wars = new HashSet<>();
    private final File file;

    public Store(File folder) {
        this.file = new File(folder, "data.yml");
    }

    // ---------- helpery ----------

    public static String k(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    public static String key(Chunk c) {
        return c.getWorld().getName() + ";" + c.getX() + ";" + c.getZ();
    }

    public static String fmt(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ()
                + "," + l.getYaw() + "," + l.getPitch();
    }

    public static Location parse(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length < 6) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }

    public static String warKey(String a, String b) {
        a = k(a);
        b = k(b);
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    public boolean atWar(String a, String b) {
        return a != null && b != null && wars.contains(warKey(a, b));
    }

    public Town townOf(UUID id) {
        for (Town t : towns.values()) if (t.members.contains(id)) return t;
        return null;
    }

    public Town townAt(Chunk c) {
        String n = claims.get(key(c));
        return n == null ? null : towns.get(n);
    }

    public Nation nationOf(Town t) {
        return t == null || t.nation == null ? null : nations.get(k(t.nation));
    }

    public int chunkCount(Town t) {
        String tk = k(t.name);
        int n = 0;
        for (String v : claims.values()) if (v.equals(tk)) n++;
        return n;
    }

    public void deleteNation(Nation n) {
        for (Town t : towns.values()) {
            if (t.nation != null && t.nation.equalsIgnoreCase(n.name)) t.nation = null;
        }
        nations.remove(k(n.name));
        String nk = k(n.name);
        wars.removeIf(w -> Arrays.asList(w.split("\\|")).contains(nk));
    }

    // ---------- zapis / odczyt ----------

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection ts = y.getConfigurationSection("towns");
        if (ts != null) {
            for (String id : ts.getKeys(false)) {
                ConfigurationSection s = ts.getConfigurationSection(id);
                if (s == null) continue;
                Town t = new Town();
                t.name = s.getString("name");
                t.mayor = UUID.fromString(Objects.requireNonNull(s.getString("mayor")));
                for (String m : s.getStringList("members")) t.members.add(UUID.fromString(m));
                t.nation = s.getString("nation");
                t.spawn = s.getString("spawn");
                t.pvp = s.getBoolean("pvp", false);
                t.mobs = s.getBoolean("mobs", false);
                t.explosions = s.getBoolean("explosions", false);
                t.fire = s.getBoolean("fire", false);
                towns.put(id, t);
                for (String c : s.getStringList("chunks")) claims.put(c, id);
            }
        }

        ConfigurationSection ns = y.getConfigurationSection("nations");
        if (ns != null) {
            for (String id : ns.getKeys(false)) {
                ConfigurationSection s = ns.getConfigurationSection(id);
                if (s == null) continue;
                Nation n = new Nation();
                n.name = s.getString("name");
                n.capital = s.getString("capital");
                nations.put(id, n);
            }
        }

        wars.addAll(y.getStringList("wars"));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();

        for (Map.Entry<String, Town> e : towns.entrySet()) {
            Town t = e.getValue();
            String p = "towns." + e.getKey() + ".";
            y.set(p + "name", t.name);
            y.set(p + "mayor", t.mayor.toString());
            List<String> members = new ArrayList<>();
            for (UUID u : t.members) members.add(u.toString());
            y.set(p + "members", members);
            y.set(p + "nation", t.nation);
            y.set(p + "spawn", t.spawn);
            y.set(p + "pvp", t.pvp);
            y.set(p + "mobs", t.mobs);
            y.set(p + "explosions", t.explosions);
            y.set(p + "fire", t.fire);
            List<String> chunks = new ArrayList<>();
            for (Map.Entry<String, String> c : claims.entrySet()) {
                if (c.getValue().equals(e.getKey())) chunks.add(c.getKey());
            }
            y.set(p + "chunks", chunks);
        }

        for (Map.Entry<String, Nation> e : nations.entrySet()) {
            String p = "nations." + e.getKey() + ".";
            y.set(p + "name", e.getValue().name);
            y.set(p + "capital", e.getValue().capital);
        }

        y.set("wars", new ArrayList<>(wars));

        try {
            y.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
