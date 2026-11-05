package pl.galaxy.serca;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.List;
import java.util.Locale;

public final class ItemBuilder {
    private ItemBuilder() {}

    public static ItemStack build(ConfigurationSection s) {
        Material mat = Material.matchMaterial(s.getString("material", "STONE"));
        if (mat == null || !mat.isItem()) mat = Material.STONE;
        ItemStack item = new ItemStack(mat, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = item.getItemMeta();

        if (s.contains("name")) meta.displayName(Text.parse(s.getString("name")));
        if (s.contains("lore")) meta.lore(Text.parseAll(s.getStringList("lore")));

        ConfigurationSection ench = s.getConfigurationSection("enchants");
        if (ench != null) {
            for (String key : ench.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (e != null) meta.addEnchant(e, ench.getInt(key), true);
            }
        }
        for (String f : s.getStringList("flags")) {
            try { meta.addItemFlags(ItemFlag.valueOf(f.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { }
        }
        int cmd = s.getInt("custom-model-data", 0);
        if (cmd > 0) setCustomModelData(meta, cmd);
        if (s.getBoolean("unbreakable", false)) meta.setUnbreakable(true);

        item.setItemMeta(meta);
        return item;
    }

    public static void setCustomModelData(ItemMeta meta, int value) {
        CustomModelDataComponent c = meta.getCustomModelDataComponent();
        c.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(c);
    }
}
