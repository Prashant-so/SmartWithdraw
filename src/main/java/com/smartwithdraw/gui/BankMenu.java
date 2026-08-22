package com.smartwithdraw.gui;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public final class BankMenu {

    public static final String ACTION_KEY                  = "bank_action";
    public static final String DEPOSIT_ALL_ACTION          = "DEPOSIT_ALL";
    public static final String DEPOSIT_HAND_ACTION         = "DEPOSIT_HAND";
    public static final String WITHDRAW_ACTION_PREFIX      = "WITHDRAW_";
    public static final String SWITCH_CURRENCY_ACTION_PREFIX = "SWITCH_CURRENCY_";
    public static final String BACK_ACTION                 = "BACK";
    public static final String CLOSE_ACTION                = "CLOSE";

    // Slots for denomination buttons on the detail page (row 3 and 4, centred)
    private static final int[] DENOM_SLOTS = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32};

    private BankMenu() {
    }

    // ── Entry point ───────────────────────────────────────────────────

    public static void open(Player player) {

        if (CurrencyManager.getAllEnabled().size() == 1) {
            openDetail(player, CurrencyManager.getDefault());
        } else {
            openSelection(player);
        }
    }

    public static void open(Player player, Currency currency) {
        openDetail(player, currency);
    }

    // ── Page 1: Currency Selection ────────────────────────────────────

    public static void openSelection(Player player) {

        BankMenuHolder holder = new BankMenuHolder();
        holder.setOnCurrencySelectionPage(true);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.translateAlternateColorCodes('&', "&8&lSmartWithdraw &7» &6Select Currency"));

        holder.setInventory(inv);

        fillBorder(inv);

        List<Currency> currencies = new ArrayList<>(CurrencyManager.getAllEnabled().values());

        // Centre currencies in row 2 and 3 depending on count
        int[] selectionSlots = centreSlots(currencies.size());

        for (int i = 0; i < currencies.size() && i < selectionSlots.length; i++) {

            Currency currency = currencies.get(i);

            BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());
            long balance = (provider != null && provider.isAvailable())
                    ? provider.getBalance(player) : 0;

            ItemStack head = buildSkullItem(
                    currency.skullTexture(),
                    translate(currency.lore().titleColor()) + currency.name(),
                    List.of(
                            "§7Balance: §a" + currency.format(balance),
                            "§7Backend: §f" + currency.backend().name(),
                            "",
                            "§eClick to open"
                    ),
                    SWITCH_CURRENCY_ACTION_PREFIX + currency.id()
            );

            inv.setItem(selectionSlots[i], head);
        }

        inv.setItem(49, buildItem(
                Material.RED_STAINED_GLASS_PANE,
                "§c§lClose",
                List.of("§7Click to close the menu."),
                CLOSE_ACTION
        ));

        player.openInventory(inv);
    }

    private static int[] centreSlots(int count) {
        if (count == 1) return new int[]{22};
        if (count == 2) return new int[]{21, 23};
        if (count == 3) return new int[]{20, 22, 24};
        if (count == 4) return new int[]{20, 21, 23, 24};
        return new int[]{19, 20, 21, 22, 23, 24};
    }

    // ── Page 2: Currency Detail ───────────────────────────────────────

    public static void openDetail(Player player, Currency currency) {

        BankMenuHolder holder = new BankMenuHolder();
        holder.setCurrency(currency);
        holder.setOnCurrencySelectionPage(false);

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.translateAlternateColorCodes('&',
                        "&8&lSmartWithdraw &7» &6" + currency.name() + " &7Bank"));

        holder.setInventory(inv);

        fillBorder(inv);

        BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());
        long balance = (provider != null && provider.isAvailable())
                ? provider.getBalance(player) : 0;

        // Slot 4 — balance display
        inv.setItem(4, buildItem(
                Material.GOLD_INGOT,
                "§6§lYour Balance",
                List.of(
                        "§7Currency: §f" + currency.name(),
                        "§7Balance: §a" + currency.format(balance)
                ),
                null
        ));

        // Denomination buttons
        List<Integer> denominations = currency.denominations().stream().sorted().toList();

        for (int i = 0; i < denominations.size() && i < DENOM_SLOTS.length; i++) {

            int value = denominations.get(i);

            inv.setItem(DENOM_SLOTS[i], buildSkullItem(
                    currency.skullTexture(),
                    "§a§l" + currency.format(value),
                    List.of(
                            "§7Click to withdraw",
                            "§7a single §a" + currency.format(value) + " §7note."
                    ),
                    WITHDRAW_ACTION_PREFIX + value
            ));
        }

        // Slot 38 — deposit all
        inv.setItem(38, buildItem(
                Material.EMERALD,
                "§a§lDeposit All Notes",
                List.of(
                        "§7Deposits every valid note",
                        "§7in your inventory at once."
                ),
                DEPOSIT_ALL_ACTION
        ));

        // Slot 42 — deposit held note
        inv.setItem(42, buildItem(
                Material.CHEST,
                "§e§lDeposit Held Note",
                List.of(
                        "§7Deposits the note you are",
                        "§7currently holding in your hand."
                ),
                DEPOSIT_HAND_ACTION
        ));

        // Slot 45 — back (only if more than one currency)
        if (CurrencyManager.getAllEnabled().size() > 1) {
            inv.setItem(45, buildItem(
                    Material.ARROW,
                    "§7§lBack",
                    List.of("§7Return to currency selection."),
                    BACK_ACTION
            ));
        }

        // Slot 49 — close
        inv.setItem(49, buildItem(
                Material.RED_STAINED_GLASS_PANE,
                "§c§lClose",
                List.of("§7Click to close the menu."),
                CLOSE_ACTION
        ));

        player.openInventory(inv);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static void fillBorder(Inventory inv) {

        ItemStack pane = buildItem(
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                " ",
                List.of(),
                null
        );

        int size = inv.getSize();

        for (int i = 0; i < 9; i++) inv.setItem(i, pane);
        for (int i = size - 9; i < size; i++) inv.setItem(i, pane);

        for (int row = 1; row < size / 9 - 1; row++) {
            inv.setItem(row * 9, pane);
            inv.setItem(row * 9 + 8, pane);
        }
    }

    public static ItemStack buildItem(Material material, String name,
                                       List<String> lore, String action) {

        ItemStack item = new ItemStack(material);
        ItemMeta  meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));

        if (action != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(SmartWithdraw.getInstance(), ACTION_KEY),
                    PersistentDataType.STRING,
                    action
            );
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildSkullItem(String textureValue, String name,
                                            List<String> lore, String action) {

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta == null) return item;

        if (textureValue != null && !textureValue.isBlank()) {
            try {
                String decoded = new String(
                        Base64.getDecoder().decode(textureValue),
                        StandardCharsets.UTF_8);

                int urlStart = decoded.indexOf("\"url\":\"") + 7;
                int urlEnd   = decoded.indexOf("\"", urlStart);

                if (urlStart >= 7 && urlEnd > urlStart) {
                    String skinUrl = decoded.substring(urlStart, urlEnd);
                    UUID uuid = UUID.nameUUIDFromBytes(
                            textureValue.getBytes(StandardCharsets.UTF_8));
                    PlayerProfile profile = SmartWithdraw.getInstance()
                            .getServer().createPlayerProfile(uuid, "SmartWithdraw");
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new URL(skinUrl));
                    profile.setTextures(textures);
                    meta.setOwnerProfile(profile);
                }
            } catch (MalformedURLException | IllegalArgumentException ignored) {
            }
        }

        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));

        if (action != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(SmartWithdraw.getInstance(), ACTION_KEY),
                    PersistentDataType.STRING,
                    action
            );
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String translate(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
