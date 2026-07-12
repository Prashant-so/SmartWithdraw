package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.gui.BankMenu;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.util.InventoryUtils;
import com.smartwithdraw.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class SmartWithdrawCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "gui", "bank" -> openGui(sender);
            case "give" -> handleGive(sender, args);
            case "reload" -> handleReload(sender);
            case "currencies" -> listCurrencies(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void openGui(CommandSender sender) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can open the bank menu.");
            return;
        }

        if (!player.hasPermission("smartwithdraw.gui")) {
            Lang.send(player, "no-permission");
            return;
        }

        BankMenu.open(player);
    }

    private void listCurrencies(CommandSender sender) {

        sender.sendMessage("§6§lAvailable Currencies:");

        for (Currency currency : CurrencyManager.getAllEnabled().values()) {
            sender.sendMessage("§e- " + currency.id()
                    + " §7(" + currency.format(1) + (currency.isDefault() ? " §a[default]" : "") + ")");
        }
    }

    /**
     * Usage: /sw give <player> <amount> [currency]
     * This is your requested "give satchels by command" admin tool.
     */
    private void handleGive(CommandSender sender, String[] args) {

        if (!sender.hasPermission("smartwithdraw.admin.give")) {
            Lang.send(sender, "no-permission");
            return;
        }

        if (args.length < 3 || args.length > 4) {
            sender.sendMessage("§cUsage: /sw give <player> <amount> [currency]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online.");
            return;
        }

        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            Lang.send(sender, "invalid-amount");
            return;
        }

        if (amount <= 0) {
            Lang.send(sender, "invalid-amount");
            return;
        }

        Currency currency = CurrencyManager.getDefault();

        if (args.length == 4) {

            currency = CurrencyManager.get(args[3]).orElse(null);

            if (currency == null) {
                Lang.send(sender, "unknown-currency", Map.of("currency", args[3]));
                return;
            }
        }

        if (!currency.enabled()) {
            Lang.send(sender, "currency-disabled");
            return;
        }

        boolean autoSplit = SmartWithdraw.getInstance().getConfig()
                .getBoolean("notes.auto-split-notes", false);

        if (autoSplit) {

            Map<Integer, Integer> notes = DenominationCalculator.calculate(amount, currency);

            for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    InventoryUtils.give(target, NoteFactory.createNote(currency, entry.getKey()));
                }
            }

        } else {
            InventoryUtils.give(target, NoteFactory.createNote(currency, amount));
        }

        TransactionLogger.log("ADMIN_GIVE", target, currency.id(), amount);

        Lang.send(sender, "give-success", Map.of(
                "player", target.getName(),
                "amount", currency.format(amount)
        ));

        Lang.send(target, "give-received", Map.of(
                "amount", currency.format(amount)
        ));
    }

    private void handleReload(CommandSender sender) {

        if (!sender.hasPermission("smartwithdraw.admin.reload")) {
            Lang.send(sender, "no-permission");
            return;
        }

        SmartWithdraw plugin = SmartWithdraw.getInstance();
        plugin.reloadConfig();
        CurrencyManager.load();

        Lang.send(sender, "reload-success");
    }

    private void sendHelp(CommandSender sender) {

        Currency def = CurrencyManager.getDefault();

        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§l💰 SmartWithdraw Help");
        sender.sendMessage("");
        sender.sendMessage("§e/withdraw <amount> [currency] §7➜ Convert money into notes");
        sender.sendMessage("§aRight-Click Note §7➜ Deposit note");
        sender.sendMessage("§aSneak + Right-Click Stack §7➜ Deposit whole stack");
        sender.sendMessage("§aSneak + Left-Click Note §7➜ Split into smaller notes");
        sender.sendMessage("§e/deposit §7➜ Deposit all notes in inventory (any currency)");
        sender.sendMessage("§e/sw gui §7➜ Open the bank menu");
        sender.sendMessage("§e/sw currencies §7➜ List available currencies");
        sender.sendMessage("§e/sw give <player> <amount> [currency] §7➜ (admin) give notes");
        sender.sendMessage("§e/sw reload §7➜ (admin) reload config");
        sender.sendMessage("");
        sender.sendMessage("§6Default currency (" + def.id() + ") notes:");

        StringBuilder line = new StringBuilder();
        int i = 0;
        String[] colors = {"§a", "§6", "§b", "§d", "§e", "§5"};

        for (int value : def.denominations().stream().sorted().toList()) {
            line.append(colors[i % colors.length]).append(def.format(value)).append("  ");
            i++;
        }

        sender.sendMessage(line.toString());
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }
}
