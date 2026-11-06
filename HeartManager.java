package pl.galaxy.serca;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class HeartManager extends PlaceholderExpansion {

    private final GalaxySerca plugin;
    private final NamespacedKey heartKey;

    public HeartManager(@NotNull GalaxySerca plugin) {
        this.plugin = plugin;
        this.heartKey = new NamespacedKey(plugin, "heart_type");
    }

    @NotNull
    public ItemStack create(@NotNull HeartType type, int amount) {
        int finalAmount = Math.max(1, amount);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("hearts." + type.id());
        
        ItemStack item = new ItemStack(Material.RED_DYE, finalAmount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            if (section != null) {
                String name = section.getString("name", "&4&lSerce");
                meta.displayName(Text.parse(name));
                
                List<String> rawLore = section.getStringList("lore");
                if (!rawLore.isEmpty()) {
                    meta.lore(Text.parseAll(rawLore));
                }
                
                int customModelData = section.getInt("custom-model-data", 0);
                if (customModelData > 0) {
                    ItemBuilder.setCustomModelData(meta, customModelData);
                }
            } else {
                meta.displayName(Text.parse(type == HeartType.ANARCHIC ? "&4&lAnarchiczne serce" : "&4&lDodatkowe serce"));
            }
            
            meta.getPersistentDataContainer().set(heartKey, PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        
        return item;
    }

    @Nullable
    public HeartType typeOf(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(heartKey, PersistentDataType.STRING)) {
            return null;
        }
        
        String id = pdc.get(heartKey, PersistentDataType.STRING);
        return id != null ? HeartType.fromId(id) : null;
    }

    public boolean isHeart(@Nullable ItemStack item) {
        return typeOf(item) != null;
    }

    public int limit(@NotNull HeartType type) {
        if (type == HeartType.ANARCHIC) {
            return plugin.getConfig().getInt("settings.anarchic-limit", 40);
        }
        return plugin.getConfig().getInt("settings.normal-limit", 30);
    }

    public double getMax(@NotNull Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getBaseValue();
    }

    public void setMax(@NotNull Player player, double value) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        
        double clamped = Math.max(2.0, value);
        attr.setBaseValue(clamped);
        
        if (player.getHealth() > clamped) {
            player.setHealth(clamped);
        }
    }

    // --- REJESTRACJA PLACEHOLDERAPI ---

    @Override
    public @NotNull String getIdentifier() {
        return "galaxyserca";
    }

    @Override
    public @NotNull String getAuthor() {
        return "youngmateo";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (params.equalsIgnoreCase("hearts") || params.isEmpty()) {
            int hearts = (int) (getMax(player) / 2.0);
            return String.valueOf(hearts);
        }

        if (params.equalsIgnoreCase("hp")) {
            return String.valueOf((int) getMax(player));
        }

        if (params.equalsIgnoreCase("limit_normal")) {
            return String.valueOf(limit(HeartType.NORMAL));
        }

        if (params.equalsIgnoreCase("limit_anarchic")) {
            return String.valueOf(limit(HeartType.ANARCHIC));
        }

        return null;
    }
}
