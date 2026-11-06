package pl.galaxy.serca;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class ShopGui implements Listener {
    private final GalaxySerca plugin;

    public ShopGui(GalaxySerca plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        private final Map<Integer, HeartType> slots = new HashMap<>();
        private Inventory inventory;

        public Map<Integer, HeartType> slots() { return slots; }
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof Holder guiHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            
            HeartType type = guiHolder.slots().get(slot);
            if (type == null) return;

            // Tworzymy serce wybranego typu i dodajemy bezpośrednio do ekwipunku gracza
            ItemStack heartItem = plugin.hearts().create(type, 1);
            Map<Integer, ItemStack> left = p.getInventory().addItem(heartItem);
            
            // Jeśli ekwipunek jest pełny, upuść na ziemię
            for (ItemStack it : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
            }

            plugin.sound(p, "success");
        }
    }
}
