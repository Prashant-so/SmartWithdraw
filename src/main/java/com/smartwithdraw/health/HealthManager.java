package com.smartwithdraw.health;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyBackend;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.health.HealthIssue.Severity;
import com.smartwithdraw.update.UpdateManager;
import com.smartwithdraw.util.Chat;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Works out what is missing or broken and tells the right people.
 * Everything here is a few map lookups - no polling, no I/O, no lag.
 * It re-runs only when something relevant changes (economy / dependency
 * plugins enabling or disabling) and on /sw reload.
 */
public final class HealthManager {

    public static final String PERMISSION = "smartwithdraw.admin.health";

    // Edit here if a download page ever moves.
    private static final String URL_VAULT = "https://www.spigotmc.org/resources/vault.34315/";
    private static final String URL_ESSENTIALSX = "https://essentialsx.net/downloads.html";
    private static final String URL_PLAYERPOINTS = "https://www.spigotmc.org/resources/playerpoints.80745/";
    private static final String URL_PLACEHOLDERAPI = "https://www.spigotmc.org/resources/placeholderapi.6245/";

    private static volatile List<HealthIssue> issues = List.of();
    private static String lastSignature = null;
    private static boolean queued = false;

    private HealthManager() {
    }

    // ── lifecycle ────────────────────────────────────────────────────

    /** Runs on the first server tick, i.e. after every plugin has enabled. */
    public static void init() {
        Bukkit.getScheduler().runTask(SmartWithdraw.getInstance(), () -> refresh(true));
    }

    /** Debounced: many events in one tick cause a single refresh. */
    public static void scheduleRefresh() {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        if (queued || !plugin.isEnabled()) return;
        queued = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            queued = false;
            refresh(false);
        });
    }

    private static void refresh(boolean startup) {
        EconomyManager.setupEconomy(); // pick up an economy that registered late
        List<HealthIssue> found = compute();
        issues = found;

        String sig = found.stream()
                .map(i -> i.severity() + ":" + i.id())
                .sorted()
                .collect(Collectors.joining("|"));

        if (startup) {
            logReport(found);
        } else if (!sig.equals(lastSignature)) {
            boolean anySerious = found.stream().anyMatch(i -> i.severity() != Severity.INFO);
            if (anySerious) {
                logReport(found);
            } else {
                log().info("All required dependencies are available now.");
            }
        }
        lastSignature = sig;
    }

    // ── the actual checks ────────────────────────────────────────────

    private static List<HealthIssue> compute() {
        List<HealthIssue> list = new ArrayList<>();
        PluginManager pm = Bukkit.getPluginManager();

        Map<String, Currency> enabled = CurrencyManager.getAllEnabled();
        if (enabled.isEmpty()) {
            list.add(new HealthIssue(Severity.CRITICAL, "no-currency",
                    "No currency is enabled",
                    "Every currency in config.yml has enabled: false, so nobody can withdraw or deposit.",
                    "Set enabled: true on at least one currency, then run /sw reload.",
                    List.of()));
        }

        Currency def = CurrencyManager.getDefault();
        Map<CurrencyBackend, List<String>> broken = new EnumMap<>(CurrencyBackend.class);
        int brokenCount = 0;
        for (Currency c : enabled.values()) {
            BalanceProvider p = BalanceProviderRegistry.get(c.backend());
            if (p == null || !p.isAvailable()) {
                broken.computeIfAbsent(c.backend(), k -> new ArrayList<>()).add(c.id());
                brokenCount++;
            }
        }

        // CRITICAL = the default currency can't work, or nothing can work.
        boolean nothingUsable = !enabled.isEmpty() && brokenCount == enabled.size();
        CurrencyBackend defaultBackend = (def != null && def.enabled()) ? def.backend() : null;
        boolean defaultBroken = defaultBackend != null
                && broken.getOrDefault(defaultBackend, List.of()).contains(def.id());

        List<String> vault = broken.get(CurrencyBackend.VAULT);
        if (vault != null) {
            Severity sev = sev(CurrencyBackend.VAULT, nothingUsable, defaultBroken, defaultBackend);
            String affected = String.join(", ", vault);
            if (!pm.isPluginEnabled("Vault")) {
                list.add(new HealthIssue(sev, "vault-missing",
                        "Vault is not installed",
                        "Currency '" + affected + "' cannot work without Vault and an economy plugin.",
                        "Install Vault and an economy plugin such as EssentialsX, then restart the server.",
                        List.of(new HealthIssue.Link("Vault", URL_VAULT),
                                new HealthIssue.Link("EssentialsX", URL_ESSENTIALSX))));
            } else {
                list.add(new HealthIssue(sev, "economy-missing",
                        "No economy plugin found",
                        "Vault is installed but nothing provides money for it (currency '" + affected + "').",
                        "Install an economy plugin such as EssentialsX, then restart the server.",
                        List.of(new HealthIssue.Link("EssentialsX", URL_ESSENTIALSX))));
            }
        }

        List<String> points = broken.get(CurrencyBackend.PLAYERPOINTS);
        if (points != null) {
            Severity sev = sev(CurrencyBackend.PLAYERPOINTS, nothingUsable, defaultBroken, defaultBackend);
            list.add(new HealthIssue(sev, "playerpoints-missing",
                    "PlayerPoints is not installed",
                    "Currency '" + String.join(", ", points) + "' is disabled until PlayerPoints is installed.",
                    "Install PlayerPoints and restart - it starts working automatically.",
                    List.of(new HealthIssue.Link("PlayerPoints", URL_PLAYERPOINTS))));
        }

        // Future-proof: any backend you add later that reports unavailable
        for (Map.Entry<CurrencyBackend, List<String>> e : broken.entrySet()) {
            if (e.getKey() == CurrencyBackend.VAULT || e.getKey() == CurrencyBackend.PLAYERPOINTS) continue;
            list.add(new HealthIssue(sev(e.getKey(), nothingUsable, defaultBroken, defaultBackend),
                    "backend-" + e.getKey().name().toLowerCase(),
                    e.getKey().name() + " backend is unavailable",
                    "Currency '" + String.join(", ", e.getValue()) + "' is disabled until it is available.",
                    "Check that the required plugin is installed and enabled.",
                    List.of()));
        }

        if (!pm.isPluginEnabled("PlaceholderAPI")) {
            list.add(new HealthIssue(Severity.INFO, "papi-missing",
                    "PlaceholderAPI is not installed",
                    "Optional: SmartWithdraw placeholders will not be available.",
                    "Install PlaceholderAPI if you want to use them in scoreboards or menus.",
                    List.of(new HealthIssue.Link("PlaceholderAPI", URL_PLACEHOLDERAPI))));
        }

        list.sort((a, b) -> b.severity().compareTo(a.severity())); // critical first
        return list;
    }

    private static Severity sev(CurrencyBackend backend, boolean nothingUsable,
                                boolean defaultBroken, CurrencyBackend defaultBackend) {
        return (nothingUsable || (defaultBroken && backend == defaultBackend))
                ? Severity.CRITICAL : Severity.WARNING;
    }

    // ── console ──────────────────────────────────────────────────────

    private static void logReport(List<HealthIssue> found) {
        Logger log = log();
        List<HealthIssue> serious = found.stream()
                .filter(i -> i.severity() != Severity.INFO).toList();

        if (serious.isEmpty()) {
            log.info("Health check: all dependencies OK.");
        } else {
            boolean critical = serious.stream().anyMatch(i -> i.severity() == Severity.CRITICAL);
            Level lvl = critical ? Level.SEVERE : Level.WARNING;
            log.log(lvl, "================= Health check =================");
            log.log(lvl, critical
                    ? "SmartWithdraw is NOT working properly. Fix the problem(s) below:"
                    : "Some SmartWithdraw features are disabled:");
            for (HealthIssue i : serious) {
                Level l = i.severity() == Severity.CRITICAL ? Level.SEVERE : Level.WARNING;
                log.log(l, "- " + i.title());
                log.log(l, "    " + i.detail());
                log.log(l, "    Fix: " + i.fix());
                for (HealthIssue.Link link : i.links()) {
                    log.log(l, "    " + link.label() + ": " + link.url());
                }
            }
            log.log(lvl, "Run /sw status in-game or here for this report any time.");
            log.log(lvl, "================================================");
        }

        for (HealthIssue i : found) {
            if (i.severity() == Severity.INFO) log.info("Note: " + i.title() + " - " + i.detail());
        }
    }

    // ── players / admins ─────────────────────────────────────────────

    /** Shown to admins on join (after a short delay). Only WARNING and CRITICAL. */
    public static void notifyAdmin(Player p) {
        if (!SmartWithdraw.getInstance().getConfig().getBoolean("health.notify-admins-on-join", true)) return;
        if (!p.hasPermission(PERMISSION)) return;

        List<HealthIssue> serious = issues.stream()
                .filter(i -> i.severity() != Severity.INFO).toList();
        if (serious.isEmpty()) return;

        boolean critical = serious.stream().anyMatch(i -> i.severity() == Severity.CRITICAL);

        p.sendMessage(Chat.LINE);
        p.sendMessage(critical
                ? "§c§l⚠ SmartWithdraw is NOT working"
                : "§e§l⚠ SmartWithdraw needs attention");
        for (HealthIssue i : serious) {
            p.sendMessage("");
            p.sendMessage((i.severity() == Severity.CRITICAL ? "§c✖ " : "§e⚠ ") + "§f" + i.title());
            p.sendMessage("  §7" + i.detail());
            p.sendMessage("  §7Fix: §f" + i.fix());
            sendLinks(p, i);
        }
        p.sendMessage("");
        p.sendMessage("§8Run §7/sw status §8for the full report.");
        p.sendMessage(Chat.LINE);
    }

    /** /sw status */
    public static void sendStatus(CommandSender s) {
        EconomyManager.setupEconomy();
        List<HealthIssue> list = compute();
        issues = list;

        s.sendMessage(Chat.LINE);
        s.sendMessage("§6§l💰 SmartWithdraw §7v" + UpdateManager.currentVersion());
        s.sendMessage("§7Updates: " + UpdateManager.statusLine());
        s.sendMessage("");
        s.sendMessage("§7Currencies:");
        for (Currency c : CurrencyManager.getAll().values()) {
            if (!c.enabled()) {
                s.sendMessage("  §8○ §7" + c.id() + " §8(disabled in config)");
                continue;
            }
            BalanceProvider p = BalanceProviderRegistry.get(c.backend());
            boolean ok = p != null && p.isAvailable();
            s.sendMessage((ok ? "  §a✔ §f" : "  §c✖ §f") + c.id()
                    + " §8[" + c.backend().name() + "]"
                    + (ok ? "" : " §c- backend unavailable"));
        }
        s.sendMessage("");
        if (list.isEmpty()) {
            s.sendMessage("§a✔ No problems found.");
        } else {
            for (HealthIssue i : list) {
                String icon = switch (i.severity()) {
                    case CRITICAL -> "§c✖ ";
                    case WARNING -> "§e⚠ ";
                    case INFO -> "§b• ";
                };
                s.sendMessage(icon + "§f" + i.title());
                s.sendMessage("  §7" + i.detail());
                s.sendMessage("  §7Fix: §f" + i.fix());
                if (s instanceof Player p) {
                    sendLinks(p, i);
                } else {
                    for (HealthIssue.Link l : i.links()) s.sendMessage("  §7" + l.label() + ": §f" + l.url());
                }
            }
        }
        s.sendMessage(Chat.LINE);
    }

    private static void sendLinks(Player p, HealthIssue i) {
        if (i.links().isEmpty()) return;
        List<BaseComponent> row = new ArrayList<>();
        row.add(new TextComponent("  "));
        for (HealthIssue.Link l : i.links()) {
            row.add(Chat.button("§a§l[➜ Get " + l.label() + "]",
                    "§7Opens the " + l.label() + " download page",
                    ClickEvent.Action.OPEN_URL, l.url()));
            row.add(new TextComponent(" "));
        }
        Chat.sendRow(p, row.toArray(new BaseComponent[0]));
    }

    private static Logger log() {
        return SmartWithdraw.getInstance().getLogger();
    }
}
