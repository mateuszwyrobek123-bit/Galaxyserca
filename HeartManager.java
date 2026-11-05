package pl.galaxy.serca;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class HeartManager {
    private final GalaxySerca plugin;
    private final NamespacedKey key;

    public HeartManager(GalaxySerca plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "heart_type");
    }

    public ItemStack create(HeartType type, int amount) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("hearts." + type.id());
        ItemStack item = new ItemStack(Material.RED_DYE, amount);
        ItemMeta meta = item.getItemMeta();
        if (s != null) {
            meta.displayName(Text.parse(s.getString("name", "&4Serce")));
            meta.lore(Text.parseAll(s.getStringList("lore")));
            int cmd = s.getInt("custom-model-data", 0);
            if (cmd > 0) ItemBuilder.setCustomModelData(meta, cmd);
        }
        // znacznik w PDC - po nim rozpoznajemy prawdziwe serce
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    public HeartType typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.RED_DYE || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : HeartType.fromId(id);
    }

    public boolean isHeart(ItemStack item) {
        return typeOf(item) != null;
    }

    public int limit(HeartType type) {
        return plugin.getConfig().getInt(type == HeartType.NORMAL ? "settings.normal-limit" : "settings.anarchic-limit",
                type == HeartType.NORMAL ? 30 : 40);
    }

    public int count(Player p, HeartType type) {
        int total = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (typeOf(it) == type) total += it.getAmount();
        }
        return total;
    }

    public void remove(Player p, HeartType type, int amount) {
        ItemStack[] contents = p.getInventory().getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (typeOf(it) != type) continue;
            if (it.getAmount() <= left) {
                left -= it.getAmount();
                contents[i] = null;
            } else {
                it.setAmount(it.getAmount() - left);
                left = 0;
            }
        }
        p.getInventory().setStorageContents(contents);
    }

    public double getMax(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getBaseValue();
    }

    public void setMax(Player p, double value) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        a.setBaseValue(Math.max(1, value));
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }
}
