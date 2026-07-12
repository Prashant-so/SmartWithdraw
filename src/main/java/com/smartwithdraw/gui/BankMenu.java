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
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Bank GUI. Shows withdraw buttons for the current currency's
 * denominations, a deposit-all button, and (if more than one enabled
 * currency is configured) a button to switch currencies.
 */
public final class BankMenu {

    public static final String ACTION_KEY = "bank_action";
    public static final String DEPOSIT_ALL_ACTION = "DEPOSIT_ALL";
    public static final String WITHDRAW_ACTION_PREFIX = "WITHDRAW_";
    public static final String SWITCH_CURRENCY_ACTION_PREFIX = "SWITCH_CURRENCY_";

    private static final int[] DENOMINATION_SLOTS = {10, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23};

    private BankMenu() {
    }

    public static void open(Player player) {
        open(player, CurrencyManager.getDefault());
    }

    public static void open(Player player, Currency currency) {

        BankMenuHolder holder = new BankMenuHolder();
        holder.setCurrency(currency);

        Inventory inventory = Bukkit.createInventory(
                holder,
                36,
                ChatColor.translateAlternateColorCodes('&', "&6&lSmart Bank &8- &7" + currency.name())
        );

        holder.setInventory(inventory);

        BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());
        long balance = (provider != null && provider.isAvailable())
                ? provider.getBalance(player)
                : 0;

        inventory.setItem(4, buildItem(
                Material.GOLD_INGOT,
                "§6§lYour Balance",
                List.of(
                        "§7Current balance:",
                        "§a" + currency.format(balance)
                ),
                null
        ));

        List<Integer> denominations = currency.denominations().stream().sorted().toList();

        for (int i = 0; i < denominations.size() && i < DENOMINATION_SLOTS.length; i++) {

            int value = denominations.get(i);

            inventory.setItem(DENOMINATION_SLOTS[i], buildItem(
                    Material.PAPER,
                    "§a✦ Withdraw " + currency.format(value) + " ✦",
                    List.of(
                            "§7Click to withdraw a single",
                            "§7" + currency.format(value) + " note."
                    ),
                    WITHDRAW_ACTION_PREFIX + value
            ));
        }

        inventory.setItem(31, buildItem(
                Material.EMERALD,
                "§a§lDeposit All Notes",
                List.of("§7Click to deposit every note", "§7currently in your inventory."),
                DEPOSIT_ALL_ACTION
        ));

        if (CurrencyManager.getAllEnabled().size() > 1) {

            inventory.setItem(8, buildItem(
                    Material.NETHER_STAR,
                    "§d§lSwitch Currency",
                    List.of(
                            "§7Currently viewing: §f" + currency.name(),
                            "§7Click to see all currencies."
                    ),
                    "OPEN_CURRENCY_SWITCHER"
            ));
        }

        player.openInventory(inventory);
    }

    public static void openCurrencySwitcher(Player player) {

        BankMenuHolder holder = new BankMenuHolder();

        Inventory inventory = Bukkit.createInventory(
                holder,
                27,
                ChatColor.translateAlternateColorCodes('&', "&d&lChoose a Currency")
        );

        holder.setInventory(inventory);

        int slot = 10;

        for (Currency currency : CurrencyManager.getAllEnabled().values()) {

            if (slot > 16) {
                break;
            }

            inventory.setItem(slot, buildItem(
                    Material.PAPER,
                    "§e" + currency.name() + (currency.isDefault() ? " §a(default)" : ""),
                    List.of("§7Click to open the bank", "§7for this currency."),
                    SWITCH_CURRENCY_ACTION_PREFIX + currency.id()
            ));

            slot++;
        }

        player.openInventory(inventory);
    }

    private static ItemStack buildItem(Material material,
                                        String name,
                                        List<String> lore,
                                        String action) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));

        if (action != null) {

            NamespacedKey actionKey = new NamespacedKey(
                    SmartWithdraw.getInstance(),
                    ACTION_KEY
            );

            meta.getPersistentDataContainer().set(
                    actionKey,
                    PersistentDataType.STRING,
                    action
            );
        }

        item.setItemMeta(meta);

        return item;
    }
}
