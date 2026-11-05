package pl.galaxy.serca;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

public final class GalaxySerca extends JavaPlugin {
    private HeartManager hearts;
    private ShopGui shop;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        hearts = new HeartManager(this);
        shop = new ShopGui(this);

        getServer().getPluginManager().registerEvents(new HeartListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        Commands cmds = new Commands(this);
        for (String name : new String[]{"wyplacserca", "galaxyserca-gui", "galaxyserca-reload", "galaxyserca-give"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(cmds);
                c.setTabCompleter(cmds);
            }
        }
        getLogger().info("GalaxySerca wlaczony.");
    }

    public HeartManager hearts() { return hearts; }
    public ShopGui shop() { return shop; }

    public String fmt(String path, String... reps) {
        String s = getConfig().getString("messages." + path, "&cBrak wiadomosci: " + path);
        for (int i = 0; i + 1 < reps.length; i += 2) s = s.replace(reps[i], reps[i + 1]);
        return s;
    }

    public void send(CommandSender to, String path, String... reps) {
        to.sendMessage(Text.parse(fmt("prefix") + fmt(path, reps)));
    }

    public void subtitle(Player p, String path, String... reps) {
        p.showTitle(Title.title(Component.empty(), Text.parse(fmt(path, reps)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(300))));
    }

    public void sound(Player p, String key) {
        String s = getConfig().getString("sounds." + key);
        if (s != null && !s.isBlank()) p.playSound(p.getLocation(), s, 1.0f, 1.0f);
    }
}
