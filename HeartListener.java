package pl.galaxy.serca;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public final class HeartListener implements Listener {
    /** Jedyne typy inwentarzy, do których wolno włożyć serce. Reszta (kowadło, stół, piec itd.) jest blokowana. */
    private static final Set<InventoryType> SAFE = EnumSet.of(
            InventoryType.CHEST, InventoryType.ENDER_CHEST, InventoryType.SHULKER_BOX, InventoryType.BARREL,
            InventoryType.HOPPER, InventoryType.CRAFTING, InventoryType.PLAYER, InventoryType.CREATIVE);

    private final GalaxySerca plugin;
    private final HeartManager hearts;

    public HeartListener(GalaxySerca plugin) {
        this.plugin = plugin;
        this.hearts = plugin.hearts();
    }

    // ---------- używanie serca ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        HeartType type = hearts.typeOf(e.getItem());
        if (type == null) return;

        // serce nigdy nie może zadziałać jak zwykły barwnik (tablice, itp.)
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        EquipmentSlot hand = e.getHand();
        if (hand == null || (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)) return;
        Player p = e.getPlayer();

        ItemStack stack = p.getInventory().getItem(hand);
        if (hearts.typeOf(stack) != type) return;

        double max = hearts.getMax(p);
        int limit = hearts.limit(type);
        if (max >= limit) {
            plugin.subtitle(p, "limit-reached", "{limit}", String.valueOf(limit));
            return;
        }

        // najpierw zabieramy przedmiot, potem dajemy zdrowie
        ItemStack rest = stack.clone();
        rest.setAmount(stack.getAmount() - 1);
        p.getInventory().setItem(hand, rest.getAmount() <= 0 ? null : rest);

        hearts.setMax(p, max + 1);
        plugin.subtitle(p, "heart-used");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseOnEntity(PlayerInteractEntityEvent e) {
        ItemStack inHand = e.getPlayer().getInventory().getItem(e.getHand());
        if (hearts.isHeart(inHand)) e.setCancelled(true); // owce, psy, koty - barwienie
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (hearts.isHeart(e.getItem())) e.setCancelled(true);
    }

    // ---------- śmierć ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("death.enabled", true)) return;
        Player p = e.getEntity();

        if (cfg.getBoolean("death.lightning", true)) p.getWorld().strikeLightningEffect(p.getLocation());

        double loss = cfg.getDouble("death.lose-health", 1);
        if (loss <= 0) return;
        double max = hearts.getMax(p);

        // tryb ochrony: do progu włącznie (domyślnie 30) gracz nie traci serc
        if (cfg.getBoolean("death.no-loss-below.enabled", true)
                && max <= cfg.getDouble("death.no-loss-below.threshold", 30)) {
            return;
        }
        double newMax = max - loss;

        if (cfg.getBoolean("death.drop-heart", true)) {
            HeartType drop = max > hearts.limit(HeartType.NORMAL) ? HeartType.ANARCHIC : HeartType.NORMAL;
            p.getWorld().dropItemNaturally(p.getLocation(), hearts.create(drop, 1));
        }

        if (newMax < 1) {
            hearts.setMax(p, cfg.getDouble("death.reset-health-after-ban", 10));
            String cmd = cfg.getString("death.ban-command", "").replace("%player%", p.getName());
            if (!cmd.isBlank()) Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        } else {
            hearts.setMax(p, newMax);
        }
    }

    // ---------- ochrona przed duplikacją ----------

    /** Kowadło, szlifierka, kuźnia - serce nigdy nie może być składnikiem. */
    private boolean hasHeart(ItemStack[] items) {
        for (ItemStack it : items) {
            if (hearts.isHeart(it)) return true;
        }
        return false;
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (hasHeart(e.getInventory().getContents())) e.setResult(null);
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent e) {
        if (hasHeart(e.getInventory().getContents())) e.setResult(null);
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        if (hasHeart(e.getInventory().getContents())) e.setResult(null);
    }

    /** Stół rzemieślniczy i craftowanie 2x2. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) {
            if (hearts.isHeart(it)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Blokada wkładania serc do niebezpiecznych inwentarzy (kowadło, piec, tkacz, kamieniarz, crafter itd.). */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopGui.Holder) return;
        if (SAFE.contains(e.getView().getTopInventory().getType())) return;

        boolean topClicked = e.getClickedInventory() == e.getView().getTopInventory();
        boolean involved = false;

        if (topClicked) {
            involved = hearts.isHeart(e.getCursor()) || hearts.isHeart(e.getCurrentItem());
            if (!involved && e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
                involved = hearts.isHeart(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()));
            }
            if (!involved && e.getClick() == ClickType.SWAP_OFFHAND) {
                involved = hearts.isHeart(e.getWhoClicked().getInventory().getItemInOffHand());
            }
        } else if (e.isShiftClick()) {
            involved = hearts.isHeart(e.getCurrentItem()); // shift-klik z własnego ekwipunku do góry
        }

        if (involved) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) plugin.subtitle(p, "heart-blocked");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ShopGui.Holder) return;
        if (!hearts.isHeart(e.getOldCursor())) return;
        if (SAFE.contains(e.getView().getTopInventory().getType())) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    /** Lejki nie mogą wsadzić serc do crafterów, dropperów itp. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (hearts.isHeart(e.getItem()) && !SAFE.contains(e.getDestination().getType())) e.setCancelled(true);
    }
}
