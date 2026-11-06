package pl.galaxy.serca;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class ShopGui {
    private final GalaxySerca plugin;

    public ShopGui(GalaxySerca plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        public final Map<Integer, HeartType> slots = new HashMap<>();
        private Inventory inventory;

        public Map<Integer, HeartType> getSlots() { return slots; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public void open(Player p) {
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Text.parse("&8Panel Zarządzania Sercami"));
        holder.inventory = inv;

        // Slot 11: Zwykłe serce (id: 1)
        ItemStack normalHeart = plugin.hearts().create(HeartType.NORMAL, 1);
        inv.setItem(11, normalHeart);
        holder.slots.put(11, HeartType.NORMAL);

        // Slot 15: Anarchiczne serce (id: 2)
        ItemStack anarchicHeart = plugin.hearts().create(HeartType.ANARCHIC, 1);
        inv.setItem(15, anarchicHeart);
        holder.slots.put(15, HeartType.ANARCHIC);

        p.openInventory(inv);
    }
}
