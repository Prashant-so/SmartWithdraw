package com.smartwithdraw.command;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.currency.DenominationCalculator;
import com.smartwithdraw.currency.NoteFactory;
import com.smartwithdraw.gui.BankMenu;
import com.smartwithdraw.gui.InspectMenu;
import com.smartwithdraw.logging.TransactionLogger;
import com.smartwithdraw.storage.PendingNoteStorage;
import com.smartwithdraw.util.DailyLimitManager;
import com.smartwithdraw.util.InventoryUtils;
import com.smartwithdraw.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SmartWithdrawCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                              String label, String[] args) {

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "gui", "bank"   -> openGui(sender);
            case "give"          -> handleGive(sender, args);
            case "reload"        -> handleReload(sender);
            case "currencies"    -> listCurrencies(sender);
            case "inspect"       -> handleInspect(sender);
            case "limit"         -> handleLimit(sender, args);
            default              -> sendHelp(sender);
        }

        return true;
    }

    // ── GUI ──────────────────────────────────────────────────────────

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

    // ── CURRENCIES ───────────────────────────────────────────────────

    private void listCurrencies(CommandSender sender) {

        sender.sendMessage("§6§lAvailable Currencies:");

        for (Currency currency : CurrencyManager.getAllEnabled().values()) {
            sender.sendMessage("§e- " + currency.id()
                    + " §7(" + currency.format(1)
                    + (currency.isDefault() ? " §a[default]" : "") + ")"
                    + " §8[" + currency.backend().name() + "]");
        }
    }

    // ── INSPECT ──────────────────────────────────────────────────────

    private void handleInspect(CommandSender sender) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /sw inspect.");
            return;
        }

        if (!player.hasPermission("smartwithdraw.inspect")) {
            Lang.send(player, "no-permission");
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();

        if (held == null || held.getType().isAir()) {
            player.sendMessage(
                    "§c§l✖ §cHold a note in your main hand to inspect it.");
            return;
        }

        InspectMenu.open(player, held);
    }

    // ── DAILY LIMIT INFO ─────────────────────────────────────────────

    /**
     * /sw limit [currency] — shows the player their current daily
     * usage and time until the window resets.
     */
    private void handleLimit(CommandSender sender, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /sw limit.");
            return;
        }

        Currency currency = CurrencyManager.getDefault();

        if (args.length >= 2) {
            currency = CurrencyManager.get(args[1]).orElse(null);
            if (currency == null) {
                Lang.send(player, "unknown-currency",
                        Map.of("currency", args[1]));
                return;
            }
        }

        if (!currency.hasDailyLimit()) {
            player.sendMessage("§7Daily limit for §f" + currency.name()
                    + "§7: §aUnlimited");
            return;
        }

        long used = DailyLimitManager.getWithdrawn(
                player.getUniqueId(), currency.id());
        String reset = DailyLimitManager.resetString(
                player.getUniqueId(), currency.id());

        Lang.send(player, "daily-limit-info", Map.of(
                "used",     currency.format(used),
                "limit",    currency.format(currency.dailyLimit()),
                "currency", currency.name(),
                "reset",    reset
        ));
    }

    // ── GIVE (online + offline) ───────────────────────────────────────

    @SuppressWarnings("deprecation")
    private void handleGive(CommandSender sender, String[] args) {

        if (!sender.hasPermission("smartwithdraw.admin.give")) {
            Lang.send(sender, "no-permission");
            return;
        }

        if (args.length < 3 || args.length > 4) {
            sender.sendMessage(
                    "§cUsage: /sw give <player> <amount> [currency]");
            return;
        }

        String targetName = args[1];
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
                Lang.send(sender, "unknown-currency",
                        Map.of("currency", args[3]));
                return;
            }
        }

        if (!currency.enabled()) {
            Lang.send(sender, "currency-disabled");
            return;
        }

        // Try online first
        Player target = Bukkit.getPlayerExact(targetName);

        if (target != null) {

            // Online — give directly
            boolean autoSplit = SmartWithdraw.getInstance().getConfig()
                    .getBoolean("notes.auto-split-notes", false);

            if (autoSplit) {
                Map<Integer, Integer> notes =
                        DenominationCalculator.calculate(amount, currency);
                for (Map.Entry<Integer, Integer> entry : notes.entrySet()) {
                    for (int i = 0; i < entry.getValue(); i++) {
                        InventoryUtils.give(target,
                                NoteFactory.createNote(currency, entry.getKey()));
                    }
                }
            } else {
                InventoryUtils.give(target,
                        NoteFactory.createNote(currency, amount));
            }

            TransactionLogger.log("ADMIN_GIVE", target, currency.id(), amount);

            Lang.send(sender, "give-success", Map.of(
                    "player", target.getName(),
                    "amount", currency.format(amount)
            ));

            Lang.send(target, "give-received",
                    Map.of("amount", currency.format(amount)));

        } else {

            // Offline — store in pending-notes.yml
            OfflinePlayer offlineTarget =
                    Bukkit.getOfflinePlayer(targetName);

            if (!offlineTarget.hasPlayedBefore()) {
                sender.sendMessage(
                        "§c§l✖ §cPlayer '§f" + targetName
                                + "§c' has never played on this server.");
                return;
            }

            PendingNoteStorage.addPendingNote(
                    offlineTarget.getUniqueId(), currency.id(), amount);

            Lang.send(sender, "give-pending",
                    Map.of("player", targetName));
        }
    }

    // ── RELOAD ───────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {

        if (!sender.hasPermission("smartwithdraw.admin.reload")) {
            Lang.send(sender, "no-permission");
            return;
        }

        SmartWithdraw.getInstance().reloadConfig();
        CurrencyManager.load();

        Lang.send(sender, "reload-success");
    }

    // ── HELP ─────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {

        Currency def = CurrencyManager.getDefault();

        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§l💰 SmartWithdraw Help");
        sender.sendMessage("");
        sender.sendMessage("§e/withdraw <amount> [currency]  §7➜ Withdraw as notes");
        sender.sendMessage("§e/deposit                       §7➜ Deposit all notes");
        sender.sendMessage("§e/sw gui                        §7➜ Open bank menu");
        sender.sendMessage("§e/sw currencies                 §7➜ List currencies");
        sender.sendMessage("§e/sw inspect                    §7➜ Inspect held note");
        sender.sendMessage("§e/sw limit [currency]           §7➜ View daily limit");
        sender.sendMessage("§e/sw give <player> <amount> [currency] §7➜ §8(admin)");
        sender.sendMessage("§e/sw reload                     §7➜ §8(admin) reload config");
        sender.sendMessage("");
        sender.sendMessage("§6Default (" + def.id() + ") denominations:");

        StringBuilder line = new StringBuilder();
        String[] colors = {"§a", "§6", "§b", "§d", "§e", "§5"};
        int i = 0;

        for (int value : def.denominations().stream().sorted().toList()) {
            line.append(colors[i % colors.length])
                    .append(def.format(value)).append("  ");
            i++;
        }

        sender.sendMessage(line.toString());
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    // ── TAB COMPLETION ───────────────────────────────────────────────

    @Override
    @SuppressWarnings("deprecation")
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String label, String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subcommands = Arrays.asList(
                    "gui", "bank", "give", "reload",
                    "currencies", "inspect", "limit");
            String partial = args[0].toLowerCase();
            subcommands.stream()
                    .filter(s -> s.startsWith(partial))
                    .forEach(completions::add);
            return completions;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("give")) {

            if (args.length == 2) {
                // Player names (online + offline)
                String partial = args[1].toLowerCase();
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .forEach(completions::add);
                return completions;
            }

            if (args.length == 4) {
                // Currency IDs
                String partial = args[3].toLowerCase();
                CurrencyManager.getAllEnabled().keySet().stream()
                        .filter(id -> id.startsWith(partial))
                        .forEach(completions::add);
                return completions;
            }
        }

        if (sub.equals("limit") && args.length == 2) {
            String partial = args[1].toLowerCase();
            CurrencyManager.getAllEnabled().keySet().stream()
                    .filter(id -> id.startsWith(partial))
                    .forEach(completions::add);
            return completions;
        }

        return completions;
    }
}
