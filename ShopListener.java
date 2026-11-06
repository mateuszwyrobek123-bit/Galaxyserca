package pl.galaxy.serca;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class HeartListener implements Listener {

    private final GalaxySerca plugin;

    public HeartListener(GalaxySerca plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && 
            event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        HeartManager hm = plugin.hearts();
        HeartType type = hm.typeOf(item);

        if (type == null) {
            return;
        }

        event.setCancelled(true);

        double currentMaxHP = hm.getMax(player);
        int currentHearts = (int) (currentMaxHP / 2.0);
        
        int normalLimit = hm.limit(HeartType.NORMAL);     // 30
        int anarchicLimit = hm.limit(HeartType.ANARCHIC); // 40

        // Zwykłe serce (id: 1)
        if (type == HeartType.NORMAL) {
            if (currentHearts >= normalLimit) {
                plugin.subtitle(player, "limit-reached");
                plugin.sound(player, "fail");
                return;
            }
        }
        // Anarchiczne serce (id: 2)
        else if (type == HeartType.ANARCHIC) {
            if (currentHearts < normalLimit) {
                plugin.subtitle(player, "heart-blocked");
                plugin.sound(player, "fail");
                return;
            }
            if (currentHearts >= anarchicLimit) {
                plugin.subtitle(player, "limit-reached");
                plugin.sound(player, "fail");
                return;
            }
        }

        // Zabieramy 1 sztukę przedmiotu z ekwipunku
        item.setAmount(item.getAmount() - 1);

        // Dodajemy równe 1 całe serce = 2 punkty zdrowia (HP)
        hm.setMax(player, currentMaxHP + 2.0);

        plugin.subtitle(player, "heart-used");
        plugin.sound(player, "success");
    }
}
