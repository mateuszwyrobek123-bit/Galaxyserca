package pl.galaxy.serca;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopListener implements Listener {
    private final GalaxySerca plugin;

    public ShopListener(GalaxySerca plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ShopGui.Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getClick() != ClickType.LEFT) return;

        int slot = e.getSlot();
        if (slot == holder.backSlot() && !holder.backCommand().isBlank()) {
            p.performCommand(holder.backCommand());
            return;
        }
        ShopGui.Entry entry = holder.entries().get(slot);
        if (entry != null) plugin.shop().purchase(p, entry);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopGui.Holder) e.setCancelled(true);
    }
}
