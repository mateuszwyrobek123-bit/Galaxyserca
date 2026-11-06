package pl.galaxy.serca;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class ShopListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof ShopGui.Holder guiHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            
            HeartType type = guiHolder.slots().get(slot);
            if (type == null) return;

            // Tworzymy serce wybranego typu i dodajemy do ekwipunku gracza
            ItemStack heartItem = GalaxySerca.getInstance().hearts().create(type, 1);
            Map<Integer, ItemStack> left = p.getInventory().addItem(heartItem);
            
            // Jeśli ekwipunek jest pełny, upuść na ziemię
            for (ItemStack it : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), it);
            }

            GalaxySerca.getInstance().sound(p, "success");
        }
    }
}
