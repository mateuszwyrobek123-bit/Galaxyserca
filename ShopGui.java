package pl.galaxy.serca;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class ShopGui {
    private final GalaxySerca plugin;

    public ShopGui(GalaxySerca plugin) { this.plugin = plugin; }

    public record Entry(String id, int cost, String type, ConfigurationSection section) {}

    public static final class Holder implements InventoryHolder {
        private final Map<Integer, Entry> entries = new HashMap<>();
        private Inventory inventory;
        private int backSlot = -1;
        private String backCommand = "";

        public Map<Integer, Entry> entries() { return entries; }
        public int backSlot() { return backSlot; }
        public String backCommand() { return backCommand; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public void open(Player p) {
        ConfigurationSection shop = plugin.getConfig().getConfigurationSection("shop");
        if (shop == null) return;
        int rows = Math.max(1, Math.min(6, shop.getInt("rows", 5)));
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.parse(shop.getString("title", "&8Sklep za serca")));
        holder.inventory = inv;

        ConfigurationSection deco = shop.getConfigurationSection("decorations");
        if (deco != null) {
            for (String id : deco.getKeys(false)) {
                ConfigurationSection s = deco.getConfigurationSection(id);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, ItemBuilder.build(s));
            }
        }

        ConfigurationSection back = shop.getConfigurationSection("back-button");
        if (back != null && back.getBoolean("enabled", false)) {
            int slot = back.getInt("slot", -1);
            ConfigurationSection item = back.getConfigurationSection("item");
            if (item != null && slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, ItemBuilder.build(item));
                holder.backSlot = slot;
                holder.backCommand = back.getString("command", "");
            }
        }

        ConfigurationSection items = shop.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null) continue;
                int slot = s.getInt("slot", -1);
                if (slot < 0 || slot >= inv.getSize()) continue;
                String type = s.getString("type", "ITEM").toUpperCase();
                int cost = Math.max(0, s.getInt("cost", 0));

                ItemStack display = displayOf(s, type);
                if (display == null) continue;
                List<Component> lore = display.lore() == null ? new ArrayList<>() : new ArrayList<>(display.lore());
                List<String> footer = s.contains("footer") ? s.getStringList("footer") : shop.getStringList("footer");
                for (String line : footer) {
                    lore.add(Text.parse(line.replace("{cost}", String.valueOf(cost))));
                }
                display.lore(lore);
                inv.setItem(slot, display);
                holder.entries.put(slot, new Entry(id, cost, type, s));
            }
        }
        p.openInventory(inv);
    }

    private ItemStack displayOf(ConfigurationSection s, String type) {
        ConfigurationSection display = s.getConfigurationSection("display");
        if (display != null) return ItemBuilder.build(display);
        return switch (type) {
            case "HEART" -> plugin.hearts().create(HeartType.NORMAL, 1);
            case "ANARCHIC_HEART" -> plugin.hearts().create(HeartType.ANARCHIC, 1);
            default -> {
                ConfigurationSection item = s.getConfigurationSection("item");
                yield item == null ? null : ItemBuilder.build(item);
            }
        };
    }

    private ItemStack rewardOf(Entry e) {
        return switch (e.type()) {
            case "HEART" -> plugin.hearts().create(HeartType.NORMAL, 1);
            case "ANARCHIC_HEART" -> plugin.hearts().create(HeartType.ANARCHIC, 1);
            case "RANDOM" -> {
                ConfigurationSection r = e.section().getConfigurationSection("random");
                if (r == null || r.getKeys(false).isEmpty()) yield null;
                List<String> keys = new ArrayList<>(r.getKeys(false));
                String pick = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
                ConfigurationSection s = r.getConfigurationSection(pick);
                yield s == null ? null : ItemBuilder.build(s);
            }
            default -> {
                ConfigurationSection item = e.section().getConfigurationSection("item");
                yield item == null ? null : ItemBuilder.build(item);
            }
        };
    }

    public void purchase(Player p, Entry e) {
        HeartManager hm = plugin.hearts();
        closeNextTick(p);

        String perm = e.section().getString("permission", "");
        if (!perm.isBlank() && !p.hasPermission(perm)) {
            plugin.send(p, "no-permission");
            plugin.sound(p, "fail");
            return;
        }
        if (e.cost() > 0 && hm.count(p, HeartType.NORMAL) < e.cost()) {
            plugin.subtitle(p, "purchase-fail");
            plugin.sound(p, "fail");
            return;
        }
        ItemStack reward = rewardOf(e);
        if (reward == null) {
            plugin.subtitle(p, "purchase-error");
            plugin.sound(p, "fail");
            return;
        }
        if (e.cost() > 0) hm.remove(p, HeartType.NORMAL, e.cost());
        Map<Integer, ItemStack> left = p.getInventory().addItem(reward);
        for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);

        plugin.subtitle(p, "purchase-success");
        plugin.sound(p, "success");
    }

    private void closeNextTick(Player p) {
        Bukkit.getScheduler().runTask(plugin, (Runnable) p::closeInventory);
    }
}
