package pl.galaxy.serca;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
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
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GalaxySerca extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // ------------------------------------------------------------------
    // Domyslna konfiguracja (zapisuje sie sama do plugins/GalaxySerca/config.yml)
    // ------------------------------------------------------------------
    private static final String DEFAULT_CONFIG = """
# GalaxySerca - konfiguracja
# 1 serce = 1 PELNE serce na pasku (2 HP). Zwykly gracz ma 10, zakres 1-40.

settings:
  min-withdraw-health: 10     # nie mozna wyplacic, jesli gracz ma <= tyle serc
  normal-limit: 30            # limit zwyklych serc
  anarchic-limit: 40          # limit anarchicznych serc

hearts:
  normal:
    name: "&4&lDodatkowe serce"
    lore:
      - " &8» &7Kliknij PRAWYM, aby je wykorzystać!"
    custom-model-data: 1
  anarchic:
    name: "&4&lAnarchiczne serce"
    lore:
      - " &8» &7To &cserce &7możesz użyć poza limitem &c30 serc&7."
      - " &8» &7Maksymalnie możesz uzyskać &f40 serduszek&7!"
    custom-model-data: 4

death:
  enabled: true
  lightning: true
  lose-health: 1              # ile serc traci gracz po smierci
  # Tryb ochrony: true = gracz majacy "threshold" serc lub mniej (do 30 wlacznie)
  # NIE traci serc po smierci. Traca tylko ci, ktorzy maja wiecej (anarchiczne).
  # false = serca traca wszyscy.
  no-loss-below:
    enabled: true
    threshold: 30
  drop-heart: true
  ban-command: "tempban %player% 12h brak serc!"
  reset-health-after-ban: 10  # ile serc ma gracz po odbanowaniu

# Panel admina /galaxyserca-gui (kliknij serce LEWYM, a trafi do ekwipunku)
gui:
  title: "&8GalaxySerca &7- panel admina"
  rows: 3
  normal-slot: 11
  anarchic-slot: 15
  footer:
    - ""
    - " &8» &aKliknij &2LEWYM&a, aby odebrać!"

sounds:
  success: "entity.villager.celebrate"
  fail: "entity.villager.no"

messages:
  prefix: "&8[&bGalaxySerca&8] "
  only-player: "&cTa komenda jest tylko dla graczy!"
  no-permission: "&cNie masz permisji!"
  reloaded: "&aPrzeładowano konfigurację!"
  usage-give: "&cUżycie: /galaxyserca-give <normal|anarchic> [ilosc] [gracz]"
  player-not-found: "&cNie znaleziono gracza!"
  given: "&aDano &f{amount}x {type} &adla &f{player}&a."
  withdraw-not-enough: "&cBrak wystarczającej ilości serc do wypłaty! &f(Minimum {min})"
  withdraw-no-space: "&cNie masz miejsca w ekwipunku!"
  withdraw-success: "&aWypłacono serca: &f{amount}&a!"
  usage-withdraw: "&cUżycie: /wyplacserca [ilosc]"
  heart-used: "&7Otrzymales &f+ 1 serce&7!"
  limit-reached: "&7Nie możesz tego używać! Limit serc wynosi &f{limit}"
  gui-received: "&aOtrzymano serce!"
  heart-blocked: "&cSerc nie można tu używać!"
""";

    // ------------------------------------------------------------------

    private enum HeartType {
        NORMAL("normal"), ANARCHIC("anarchic");

        private final String id;
        HeartType(String id) { this.id = id; }

        static HeartType fromId(String id) {
            for (HeartType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
            return null;
        }
    }

    private static final class GuiHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    /** Jedyne inwentarze, do ktorych wolno wlozyc serce (reszta: kowadlo, piec, stol itd. - blokada). */
    private static final Set<InventoryType> SAFE = EnumSet.of(
            InventoryType.CHEST, InventoryType.ENDER_CHEST, InventoryType.SHULKER_BOX, InventoryType.BARREL,
            InventoryType.HOPPER, InventoryType.CRAFTING, InventoryType.PLAYER, InventoryType.CREATIVE);

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private NamespacedKey heartKey;

    // ------------------------------------------------------------------
    // Start / konfiguracja
    // ------------------------------------------------------------------

    @Override
    public void onEnable() {
        heartKey = new NamespacedKey(this, "heart_type");
        writeDefaultConfig();
        reloadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        for (String name : new String[]{"wyplacserca", "galaxyserca-gui", "galaxyserca-reload", "galaxyserca-give"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SercaExpansion(this).register();
            getLogger().info("Podlaczono PlaceholderAPI (%galaxyserca_...%).");
        }
        getLogger().info("GalaxySerca wlaczony.");
    }

    private void writeDefaultConfig() {
        File file = new File(getDataFolder(), "config.yml");
        if (file.exists()) return;
        getDataFolder().mkdirs();
        try {
            Files.writeString(file.toPath(), DEFAULT_CONFIG, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            getLogger().warning("Nie mozna zapisac config.yml: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Pomocnicze: teksty, wiadomosci
    // ------------------------------------------------------------------

    private static Component txt(String s) {
        return LEGACY.deserialize(s == null ? "" : s)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    private String fmt(String path, String... reps) {
        String s = getConfig().getString("messages." + path, "&cBrak wiadomosci: " + path);
        for (int i = 0; i + 1 < reps.length; i += 2) s = s.replace(reps[i], reps[i + 1]);
        return s;
    }

    private void send(CommandSender to, String path, String... reps) {
        to.sendMessage(txt(fmt("prefix") + fmt(path, reps)));
    }

    private void subtitle(Player p, String path, String... reps) {
        p.showTitle(Title.title(Component.empty(), txt(fmt(path, reps)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(1), Duration.ofMillis(300))));
    }

    private void sound(Player p, String key) {
        String s = getConfig().getString("sounds." + key);
        if (s != null && !s.isBlank()) p.playSound(p.getLocation(), s, 1.0f, 1.0f);
    }

    // ------------------------------------------------------------------
    // Serca: przedmioty i zdrowie
    // ------------------------------------------------------------------

    private ItemStack createHeart(HeartType type, int amount) {
        ConfigurationSection s = getConfig().getConfigurationSection("hearts." + type.id);
        ItemStack item = new ItemStack(Material.RED_DYE, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(txt(s != null ? s.getString("name", "&4Serce") : "&4Serce"));
        List<Component> lore = new ArrayList<>();
        if (s != null) for (String l : s.getStringList("lore")) lore.add(txt(l));
        meta.lore(lore);

        int cmd = s != null ? s.getInt("custom-model-data", 0) : 0;
        if (cmd > 0) {
            CustomModelDataComponent c = meta.getCustomModelDataComponent();
            c.setFloats(List.of((float) cmd));
            meta.setCustomModelDataComponent(c);
        }
        // znacznik w PDC - po nim rozpoznajemy prawdziwe serce
        meta.getPersistentDataContainer().set(heartKey, PersistentDataType.STRING, type.id);
        item.setItemMeta(meta);
        return item;
    }

    private HeartType typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.RED_DYE || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(heartKey, PersistentDataType.STRING);
        return id == null ? null : HeartType.fromId(id);
    }

    private boolean isHeart(ItemStack item) {
        return typeOf(item) != null;
    }

    private boolean hasHeart(ItemStack[] items) {
        for (ItemStack it : items) if (isHeart(it)) return true;
        return false;
    }

    private int limit(HeartType type) {
        return getConfig().getInt(type == HeartType.NORMAL ? "settings.normal-limit" : "settings.anarchic-limit",
                type == HeartType.NORMAL ? 30 : 40);
    }

    private double getMax(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getBaseValue();
    }

    /** Liczba serc gracza (1 serce = 2 HP), np. 10 dla zwyklego gracza. */
    private double getHearts(Player p) {
        return getMax(p) / 2.0;
    }

    private void setHearts(Player p, double hearts) {
        setMax(p, hearts * 2.0);
    }

    private void setMax(Player p, double value) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) return;
        a.setBaseValue(Math.max(2, value));
        if (p.getHealth() > a.getValue()) p.setHealth(a.getValue());
    }

    // ------------------------------------------------------------------
    // Uzywanie serca (prawy klik)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        HeartType type = typeOf(e.getItem());
        if (type == null) return;

        // serce nigdy nie dziala jak zwykly barwnik (tablice itp.)
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);

        EquipmentSlot hand = e.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;
        Player p = e.getPlayer();

        ItemStack stack = p.getInventory().getItem(hand);
        if (typeOf(stack) != type) return;

        double max = getHearts(p);
        int lim = limit(type);
        if (max >= lim) {
            subtitle(p, "limit-reached", "{limit}", String.valueOf(lim));
            return;
        }

        // najpierw zabieramy przedmiot, potem dajemy zdrowie
        ItemStack rest = stack.clone();
        rest.setAmount(stack.getAmount() - 1);
        p.getInventory().setItem(hand, rest.getAmount() <= 0 ? null : rest);

        setHearts(p, max + 1);
        subtitle(p, "heart-used");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseOnEntity(PlayerInteractEntityEvent e) {
        ItemStack inHand = e.getPlayer().getInventory().getItem(e.getHand());
        if (isHeart(inHand)) e.setCancelled(true); // barwienie owiec, psow, kotow
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (isHeart(e.getItem())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Smierc
    // ------------------------------------------------------------------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        FileConfiguration cfg = getConfig();
        if (!cfg.getBoolean("death.enabled", true)) return;
        Player p = e.getEntity();

        if (cfg.getBoolean("death.lightning", true)) p.getWorld().strikeLightningEffect(p.getLocation());

        double loss = cfg.getDouble("death.lose-health", 1);
        if (loss <= 0) return;
        double max = getHearts(p);

        // tryb ochrony: do progu wlacznie (domyslnie 30) gracz nie traci serc
        if (cfg.getBoolean("death.no-loss-below.enabled", true)
                && max <= cfg.getDouble("death.no-loss-below.threshold", 30)) {
            return;
        }
        double newMax = max - loss;

        if (cfg.getBoolean("death.drop-heart", true)) {
            HeartType drop = max > limit(HeartType.NORMAL) ? HeartType.ANARCHIC : HeartType.NORMAL;
            p.getWorld().dropItemNaturally(p.getLocation(), createHeart(drop, 1));
        }

        if (newMax < 1) {
            setHearts(p, cfg.getDouble("death.reset-health-after-ban", 10));
            String cmd = cfg.getString("death.ban-command", "").replace("%player%", p.getName());
            if (!cmd.isBlank()) {
                Bukkit.getScheduler().runTask(this, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
            }
        } else {
            setHearts(p, newMax);
        }
    }

    // ------------------------------------------------------------------
    // Ochrona przed duplikacja
    // ------------------------------------------------------------------

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

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (hasHeart(e.getInventory().getMatrix())) e.getInventory().setResult(null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        if (isHeart(e.getItem()) && !SAFE.contains(e.getDestination().getType())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Klikanie w inwentarzach (GUI admina + blokada wkladania serc gdzie nie wolno)
    // ------------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();

        // --- panel admina ---
        if (top.getHolder() instanceof GuiHolder) {
            e.setCancelled(true);
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() != top || e.getClick() != ClickType.LEFT) return;
            if (!p.hasPermission("galaxyserca.admin")) {
                send(p, "no-permission");
                return;
            }
            int slot = e.getSlot();
            if (slot == getConfig().getInt("gui.normal-slot", 11)) giveHeart(p, HeartType.NORMAL);
            else if (slot == getConfig().getInt("gui.anarchic-slot", 15)) giveHeart(p, HeartType.ANARCHIC);
            return;
        }

        // --- blokada serc w kowadle, piecu, stole itd. ---
        if (SAFE.contains(top.getType())) return;

        boolean topClicked = e.getClickedInventory() == top;
        boolean involved = false;
        if (topClicked) {
            involved = isHeart(e.getCursor()) || isHeart(e.getCurrentItem());
            if (!involved && e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0) {
                involved = isHeart(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()));
            }
            if (!involved && e.getClick() == ClickType.SWAP_OFFHAND) {
                involved = isHeart(e.getWhoClicked().getInventory().getItemInOffHand());
            }
        } else if (e.isShiftClick()) {
            involved = isHeart(e.getCurrentItem());
        }

        if (involved) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) subtitle(p, "heart-blocked");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder() instanceof GuiHolder) {
            e.setCancelled(true);
            return;
        }
        if (!isHeart(e.getOldCursor()) || SAFE.contains(top.getType())) return;
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void giveHeart(Player p, HeartType type) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(createHeart(type, 1));
        for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), it);
        subtitle(p, "gui-received");
        sound(p, "success");
    }

    private void openGui(Player p) {
        FileConfiguration cfg = getConfig();
        int rows = Math.max(1, Math.min(6, cfg.getInt("gui.rows", 3)));
        GuiHolder holder = new GuiHolder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, txt(cfg.getString("gui.title", "&8GalaxySerca")));
        holder.inventory = inv;

        putGuiHeart(inv, HeartType.NORMAL, cfg.getInt("gui.normal-slot", 11));
        putGuiHeart(inv, HeartType.ANARCHIC, cfg.getInt("gui.anarchic-slot", 15));
        p.openInventory(inv);
    }

    private void putGuiHeart(Inventory inv, HeartType type, int slot) {
        if (slot < 0 || slot >= inv.getSize()) return;
        ItemStack item = createHeart(type, 1);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        for (String line : getConfig().getStringList("gui.footer")) lore.add(txt(line));
        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    // ------------------------------------------------------------------
    // Komendy
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);

        if (name.equals("wyplacserca")) {
            withdraw(sender, args);
        } else if (name.equals("galaxyserca-gui")) {
            if (!(sender instanceof Player p)) {
                send(sender, "only-player");
            } else {
                openGui(p);
            }
        } else if (name.equals("galaxyserca-reload")) {
            reloadConfig();
            send(sender, "reloaded");
        } else if (name.equals("galaxyserca-give")) {
            giveCommand(sender, args);
        } else {
            return false;
        }
        return true;
    }

    private void withdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            send(sender, "only-player");
            return;
        }
        int wanted = 1;
        if (args.length >= 1) {
            try {
                wanted = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ex) {
                send(p, "usage-withdraw");
                return;
            }
        }
        int min = getConfig().getInt("settings.min-withdraw-health", 10);

        int done = 0;
        boolean noSpace = false;
        for (int i = 0; i < wanted; i++) {
            double max = getHearts(p);
            if (max <= min) break;
            HeartType type = max > limit(HeartType.NORMAL) ? HeartType.ANARCHIC : HeartType.NORMAL;

            // najpierw dodajemy przedmiot, dopiero potem zabieramy zdrowie - nic nie przepada
            Map<Integer, ItemStack> left = p.getInventory().addItem(createHeart(type, 1));
            if (!left.isEmpty()) {
                noSpace = true;
                break;
            }
            setHearts(p, max - 1);
            done++;
        }

        if (done > 0) {
            send(p, "withdraw-success", "{amount}", String.valueOf(done));
            if (noSpace) send(p, "withdraw-no-space");
        } else if (noSpace) {
            send(p, "withdraw-no-space");
        } else {
            send(p, "withdraw-not-enough", "{min}", String.valueOf(min));
        }
    }

    private void giveCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            send(sender, "usage-give");
            return;
        }
        HeartType type = HeartType.fromId(args[0]);
        if (type == null) {
            send(sender, "usage-give");
            return;
        }
        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException ex) {
                send(sender, "usage-give");
                return;
            }
        }
        Player target = null;
        if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
        else if (sender instanceof Player self) target = self;
        if (target == null) {
            send(sender, "player-not-found");
            return;
        }
        Map<Integer, ItemStack> left = target.getInventory().addItem(createHeart(type, amount));
        for (ItemStack it : left.values()) target.getWorld().dropItemNaturally(target.getLocation(), it);
        send(sender, "given", "{amount}", String.valueOf(amount), "{type}", type.id, "{player}", target.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!cmd.getName().equalsIgnoreCase("galaxyserca-give")) return out;
        if (args.length == 1) {
            for (HeartType t : HeartType.values()) if (t.id.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(t.id);
        } else if (args.length == 3) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // PlaceholderAPI: %galaxyserca_<nazwa>%
    // ------------------------------------------------------------------

    private static final class SercaExpansion extends PlaceholderExpansion {
        private final GalaxySerca plugin;

        SercaExpansion(GalaxySerca plugin) { this.plugin = plugin; }

        @Override public String getIdentifier() { return "galaxyserca"; }
        @Override public String getAuthor() { return "Galaxy"; }
        @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
        @Override public boolean persist() { return true; }

        @Override
        public String onRequest(OfflinePlayer offline, String params) {
            if (offline == null) return "";
            Player p = offline.getPlayer();
            if (p == null) return "";

            // jedyny placeholder: %galaxyserca_hearts% - liczba serc (1-40), tylko serca z pluginu
            if (params.equalsIgnoreCase("hearts")) {
                return String.valueOf((int) Math.floor(plugin.getHearts(p)));
            }
            return null;
        }
    }
}
