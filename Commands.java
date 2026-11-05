package pl.galaxy.serca;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Commands implements CommandExecutor, TabCompleter {
    private final GalaxySerca plugin;

    public Commands(GalaxySerca plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "wyplacserca" -> withdraw(sender);
            case "galaxyserca-gui" -> {
                if (!(sender instanceof Player p)) { plugin.send(sender, "only-player"); return true; }
                plugin.shop().open(p);
            }
            case "galaxyserca-reload" -> {
                plugin.reloadConfig();
                plugin.send(sender, "reloaded");
            }
            case "galaxyserca-give" -> give(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void withdraw(CommandSender sender) {
        if (!(sender instanceof Player p)) { plugin.send(sender, "only-player"); return; }
        HeartManager hm = plugin.hearts();
        double max = hm.getMax(p);
        int min = plugin.getConfig().getInt("settings.min-withdraw-health", 10);
        if (max <= min) {
            plugin.send(p, "withdraw-not-enough", "{min}", String.valueOf(min));
            return;
        }
        HeartType type = max > hm.limit(HeartType.NORMAL) ? HeartType.ANARCHIC : HeartType.NORMAL;

        // najpierw dodajemy przedmiot, dopiero potem zabieramy zdrowie - nic nie przepada
        Map<Integer, ItemStack> left = p.getInventory().addItem(hm.create(type, 1));
        if (!left.isEmpty()) {
            plugin.send(p, "withdraw-no-space");
            return;
        }
        hm.setMax(p, max - 1);
        plugin.send(p, "withdraw-success");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 1) { plugin.send(sender, "usage-give"); return; }
        HeartType type = HeartType.fromId(args[0]);
        if (type == null) { plugin.send(sender, "usage-give"); return; }
        int amount = 1;
        if (args.length >= 2) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[1]))); }
            catch (NumberFormatException e) { plugin.send(sender, "usage-give"); return; }
        }
        Player target;
        if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
        else target = sender instanceof Player p ? p : null;
        if (target == null) { plugin.send(sender, "player-not-found"); return; }

        Map<Integer, ItemStack> left = target.getInventory().addItem(plugin.hearts().create(type, amount));
        for (ItemStack it : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), it);
        plugin.send(sender, "given", "{amount}", String.valueOf(amount), "{type}", type.id(), "{player}", target.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("galaxyserca-give")) return out;
        if (args.length == 1) {
            for (HeartType t : HeartType.values()) if (t.id().startsWith(args[0].toLowerCase())) out.add(t.id());
        } else if (args.length == 3) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) out.add(p.getName());
        }
        return out;
    }
}
