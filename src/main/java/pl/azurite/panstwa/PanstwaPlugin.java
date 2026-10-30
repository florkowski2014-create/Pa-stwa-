package pl.azurite.panstwa;

import org.bukkit.plugin.java.JavaPlugin;

public class PanstwaPlugin extends JavaPlugin {

    public Store store;
    public WarManager war;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new Store(getDataFolder());
        store.load();
        war = new WarManager(this);

        getServer().getPluginManager().registerEvents(new Listeners(this), this);

        PCommand cmd = new PCommand(this);
        getCommand("p").setExecutor(cmd);
        getCommand("p").setTabCompleter(cmd);
    }

    @Override
    public void onDisable() {
        if (war != null) war.shutdown();
        if (store != null) store.save();
    }
}
