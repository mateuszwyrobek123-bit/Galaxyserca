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

import java.util.ArrayList;
import java.util.List;

public final class HeartManager extends PlaceholderExpansion {

    private final GalaxySerca plugin;
    private final NamespacedKey heartKey;

    public HeartManager(@NotNull GalaxySerca plugin) {
        this.plugin = plugin;
        this.heartKey = new NamespacedKey(plugin, "heart_type");
    }

    /**
     * Tworzy przedmiot serca na podstawie typu i ilości.
     */
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
            
            // Zapis typu serca w PDC przedmiotu
            meta.getPersistentDataContainer().set(heartKey, PersistentDataType.STRING, type.id());
            item.setItemMeta(meta);
        }
        
        return item;
    }

    /**
     * Zwraca typ serca reprezentowany przez dany przedmiot.
     */
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

    /**
     * Sprawdza czy dany przedmiot jest sercem z pluginu.
     */
    public boolean isHeart(@Nullable ItemStack item) {
        return typeOf(item) != null;
    }

    /**
     * Zwraca limit serc dla danego typu.
     */
    public int limit(@NotNull HeartType type) {
        if (type == HeartType.ANARCHIC) {
            return plugin.getConfig().getInt("settings.anarchic-limit", 40);
        }
        return plugin.getConfig().getInt("settings.normal-limit", 30);
    }

    /**
     * Zlicza ile serc danego typu gracz posiada w ekwipunku.
     */
    public int count(@NotNull Player player, @NotNull HeartType type) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (typeOf(stack) == type) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /**
     * Usuwa określoną liczbę serc danego typu z ekwipunku gracza.
     */
    public boolean remove(@NotNull Player player, @NotNull HeartType type, int amount) {
        if (count(player, type) < amount) {
            return false;
        }

        ItemStack[] contents = player.getInventory().getStorageContents();
        int remainingToRemove = amount;

        for (int i = 0; i < contents.length && remainingToRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (typeOf(stack) == type) {
                int stackAmount = stack.getAmount();
                if (stackAmount <= remainingToRemove) {
                    remainingToRemove -= stackAmount;
                    contents[i] = null;
                } else {
                    stack.setAmount(stackAmount - remainingToRemove);
                    remainingToRemove = 0;
                }
            }
        }

        player.getInventory().setStorageContents(contents);
        return true;
    }

    /**
     * Pobiera maksymalne zdrowie gracza przeliczone na punkty (HP).
     */
    public double getMaxHealth(@NotNull Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getBaseValue();
    }

    /**
     * Ustawia bazowe maksymalne zdrowie gracza w punktach (HP).
     */
    public void setMaxHealth(@NotNull Player player, double value) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) return;
        
        double clamped = Math.max(2.0, value);
        attribute.setBaseValue(clamped);
        
        if (player.getHealth() > clamped) {
            player.getHealth();
            player.setHealth(clamped);
        }
    }

    /**
     * Użycie przedmiotu serca przez gracza (1 serce = +2 HP = +1 pełne serduszko).
     */
    public boolean useHeart(@NotNull Player player, @NotNull ItemStack item) {
        HeartType type = typeOf(item);
        if (type == null) {
            return false;
        }

        double currentHp = getMaxHealth(player);
        int currentHeartCount = (int) (currentHp / 2.0);
        int maxLimit = limit(type);

        if (currentHeartCount >= maxLimit) {
            String limitMsg = plugin.getConfig().getString("messages.limit-reached", "&7Nie możesz tego używać! Limit serc wynosi &f{limit}");
            player.sendMessage(Text.parse(limitMsg.replace("{limit}", String.valueOf(maxLimit))));
            return false;
        }

        int newHeartCount = Math.min(currentHeartCount + 1, maxLimit);
        setMaxHealth(player, newHeartCount * 2.0);

        item.setAmount(item.getAmount() - 1);

        String successMsg = plugin.getConfig().getString("messages.heart-used", "&7Otrzymales &f+ 1 serce&7!");
        player.sendMessage(Text.parse(successMsg));
        return true;
    }

    /**
     * Wypłacanie serca z p
