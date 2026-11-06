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
        if (event.getAction() != Action.RIGHT_CLICK_GIANT && 
            event.getAction() != Action.RIGHT_CLICK_BLOCK && 
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
        
        int normalLimit = hm.limit(HeartType.NORMAL); // domyślnie 30
        int anarchicLimit = hm.limit(HeartType.ANARCHIC); // domyślnie 40

        // --- LOGIKA DLA ZWYKŁEGO SERCA (ID 1) ---
        if (type == HeartType.NORMAL) {
            if (currentHearts >= normalLimit) {
                plugin.subtitle(player, "limit-reached");
                plugin.sound(player, "fail");
                return;
            }
        }
        
        // --- LOGIKA DLA ANARCHICZNEGO SERCA (ID 2) ---
        else if (type == HeartType.ANARCHIC) {
            // Jeśli gracz ma mniej niż limit zwykłego serca (np. poniżej 30), blokujemy użycie ana!
            if (currentHearts < normalLimit) {
                // Możesz dodać klucz w configu np. "anarchic-too-early" lub użyć istniejącego komunikatu
                plugin.subtitle(player, "heart-blocked"); 
                plugin.sound(player, "fail");
                return;
            }
            // Jeśli osiągnął już maksymalny limit anarchiczny (40)
            if (currentHearts >= anarchicLimit) {
                plugin.subtitle(player, "limit-reached");
                plugin.sound(player, "fail");
                return;
            }
        }

        // Zabieramy dokładnie 1 sztukę przedmiotu z ekwipunku
        item.setAmount(item.getAmount() - 1);

        // Dodajemy równe 1 całe serce = 2 punkty zdrowia (HP)
        hm.setMax(player, currentMaxHP + 2.0);

        plugin.subtitle(player, "heart-used");
        plugin.sound(player, "success");
    }
}
