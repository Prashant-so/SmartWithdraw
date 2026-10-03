package com.smartwithdraw.update;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.util.Chat;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * GitHub-release based update checker + safe installer.
 *
 * Safety model:
 *  - All network work runs async; the main thread only reads cached fields.
 *  - The running jar is NEVER touched. A verified jar is placed in Bukkit's
 *    update folder and Bukkit swaps it in on the next start.
 *  - config.yml and data files are never modified; they are backed up first.
 *  - A crash at any point leaves either the old setup untouched or a fully
 *    verified jar in the update folder - never a half-written one.
 */
@SuppressWarnings("deprecation")
public final class UpdateManager {

    public static final String PERMISSION = "smartwithdraw.admin.update";

    /** Used when config.yml has no update.github-repo (e.g. configs from older versions). */
    private static final String DEFAULT_REPO = "Prashant-so/SmartWithdraw";
    private static final Pattern REPO_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final long MIN_CHECK_GAP_MS = 30_000L;

    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final AtomicBoolean installing = new AtomicBoolean(false);

    private static volatile ReleaseInfo latest;
    private static volatile boolean updateAvailable;
    private static volatile String stagedVersion;
    private static volatile String lastError;
    private static volatile long lastCheckAt;

    private static String consoleNotified = "";
    private static String lastLoggedError = "";
    private static HttpClient http;
    private static BukkitTask timer;

    private UpdateManager() {
    }

    // ── lifecycle ────────────────────────────────────────────────────

    public static void init() {
        UpdateInstaller.cleanupLeftovers();
        schedule();
    }

    /** Called by /sw reload so a changed interval or toggle takes effect. */
    public static void reload() {
        schedule();
    }

    public static void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private static void schedule() {
        shutdown();
        if (!enabled()) return;

        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }

        long hours = Math.max(1, cfg().getInt("update.check-interval-hours", 6));
        long period = hours * 60L * 60L * 20L;

        // First check 5s after start, then every N hours. The timer itself is
        // trivial - the real work happens on an async thread.
        timer = Bukkit.getScheduler().runTaskTimer(SmartWithdraw.getInstance(),
                () -> checkAsync(false, null), 100L, period);
    }

    // ── checking ─────────────────────────────────────────────────────

    public static void manualCheck(CommandSender sender) {
        if (!enabled()) {
            Chat.say(sender, "§cUpdate checking is turned off §7(update.enabled in config.yml).");
            return;
        }
        boolean fresh = latest != null
                && System.currentTimeMillis() - lastCheckAt < MIN_CHECK_GAP_MS;
        if (fresh) {
            report(sender);
            return;
        }
        Chat.say(sender, "§7Checking for updates…");
        UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        checkAsync(true, () -> {
            CommandSender target = id == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(id);
            if (target != null) report(target);
        });
    }

    private static void checkAsync(boolean manual, Runnable done) {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        if (http == null || !checking.compareAndSet(false, true)) {
            if (done != null) done.run();
            return;
        }
        String repo = repo(); // read config on the main thread

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ReleaseInfo found = null;
            String error = null;
            try {
                found = GitHubReleases.fetchLatest(http, repo);
            } catch (Exception e) {
                error = friendly(e);
            } finally {
                checking.set(false);
            }
            final ReleaseInfo f = found;
            final String err = error;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                applyCheckResult(f, err, manual);
                if (done != null) done.run();
            });
        });
    }

    private static void applyCheckResult(ReleaseInfo found, String error, boolean manual) {
        lastCheckAt = System.currentTimeMillis();

        if (found == null) {
            lastError = error;
            // Log a given failure once, so an offline server doesn't spam its console
            if (!manual && error != null && !error.equals(lastLoggedError)) {
                lastLoggedError = error;
                log().warning("Update check failed: " + error);
            }
            return;
        }

        lastError = null;
        lastLoggedError = "";
        latest = found;
        updateAvailable = Version.isNewer(found.version(), currentVersion());

        if (updateAvailable
                && cfg().getBoolean("update.notify-console", true)
                && !found.version().equals(consoleNotified)) {
            consoleNotified = found.version();
            log().info("============== Update available ==============");
            log().info("A new SmartWithdraw version is out: " + found.version()
                    + " (you have " + currentVersion() + ")");
            if (found.pageUrl() != null) log().info("What's new: " + found.pageUrl());
            if (allowInstall() && found.downloadUrl() != null) {
                log().info("To install it, run:  sw update install   (or click the button when an OP joins)");
            }
            log().info("==============================================");
        }
    }

    // ── messages ─────────────────────────────────────────────────────

    private static void report(CommandSender to) {
        if (latest == null) {
            Chat.say(to, "§c✖ Could not check for updates: §f"
                    + (lastError != null ? lastError : "unknown error"));
            return;
        }
        if (!updateAvailable) {
            Chat.say(to, "§a✔ You are running the latest version §f(" + currentVersion() + ")§a.");
            return;
        }
        sendPanel(to);
    }

    /** Shown to OPs/admins on join (after a short delay). */
    public static void notifyAdmin(Player player) {
        if (!enabled() || !cfg().getBoolean("update.notify-admins-on-join", true)) return;
        if (!player.hasPermission(PERMISSION) || !updateAvailable || latest == null) return;
        sendPanel(player);
    }

    private static void sendPanel(CommandSender to) {
        ReleaseInfo r = latest;
        if (r == null) return;

        to.sendMessage(Chat.LINE);
        to.sendMessage("§6§l⬆ SmartWithdraw update available");
        to.sendMessage("§7Installed: §c" + currentVersion() + " §8➜ §7Latest: §a" + r.version());

        if (r.version().equals(stagedVersion)) {
            to.sendMessage("§a✔ §fDownloaded and verified.");
            to.sendMessage("§7Restart the server to apply it. Your config and data stay untouched.");
        } else {
            boolean canInstall = allowInstall() && r.downloadUrl() != null;
            sendButtons(to, r, canInstall);
        }
        to.sendMessage(Chat.LINE);
    }

    private static void sendButtons(CommandSender to, ReleaseInfo r, boolean install) {
        if (to instanceof Player p) {
            List<BaseComponent> row = new ArrayList<>();
            if (install) {
                row.add(Chat.button("§a§l[➜ Download & Install]",
                        "§7Downloads and verifies §f" + r.version() + "§7.\n"
                                + "§7Applied on the next restart.\n"
                                + "§7Your config and data are not touched.",
                        ClickEvent.Action.RUN_COMMAND, "/sw update install"));
                row.add(new TextComponent("  "));
            }
            if (r.pageUrl() != null) {
                row.add(Chat.button("§b§l[What's new]", "§7Open the release notes",
                        ClickEvent.Action.OPEN_URL, r.pageUrl()));
            }
            if (!row.isEmpty()) Chat.sendRow(p, row.toArray(new BaseComponent[0]));
        } else {
            if (install) to.sendMessage("§7Run §e/sw update install §7to download and install it.");
            if (r.pageUrl() != null) to.sendMessage("§7Release notes: §f" + r.pageUrl());
        }
    }

    /** One line for /sw status. */
    public static String statusLine() {
        if (!enabled()) return "§7disabled";
        if (stagedVersion != null) return "§a" + stagedVersion + " downloaded §7(restart to apply)";
        if (latest == null) {
            return lastError != null ? "§cnot checked §7(" + lastError + ")" : "§7not checked yet";
        }
        return updateAvailable
                ? "§e" + latest.version() + " available §7(/sw update install)"
                : "§aup to date";
    }

    // ── installing ───────────────────────────────────────────────────

    public static void install(CommandSender sender) {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        ReleaseInfo r = latest;

        if (!enabled()) {
            Chat.say(sender, "§cUpdate checking is turned off §7(update.enabled in config.yml).");
            return;
        }
        if (!allowInstall()) {
            Chat.say(sender, "§cOne-click install is turned off §7(update.allow-install).");
            if (r != null && r.pageUrl() != null) sender.sendMessage("§7Download manually: §f" + r.pageUrl());
            return;
        }
        if (r == null || !updateAvailable) {
            Chat.say(sender, "§7No update to install. Run §e/sw update check §7first.");
            return;
        }
        if (r.version().equals(stagedVersion)) {
            Chat.say(sender, "§a✔ §f" + r.version() + " §ais already downloaded. Restart the server to apply it.");
            return;
        }
        if (r.downloadUrl() == null) {
            Chat.say(sender, "§c✖ That release has no .jar file attached.");
            if (r.pageUrl() != null) sender.sendMessage("§7Download manually: §f" + r.pageUrl());
            return;
        }
        if (!installing.compareAndSet(false, true)) {
            Chat.say(sender, "§7A download is already running…");
            return;
        }

        Chat.say(sender, "§7Downloading §f" + r.version() + "§7…");
        final UUID id = sender instanceof Player p ? p.getUniqueId() : null;
        final int keep = Math.max(1, cfg().getInt("update.keep-backups", 5));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String backup = null;
            String error = null;
            try {
                backup = UpdateInstaller.stage(http, r, keep);
            } catch (Exception e) {
                error = friendly(e);
            } finally {
                installing.set(false);
            }
            final String bk = backup;
            final String err = error;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> finishInstall(id, r, err, bk));
        });
    }

    private static void finishInstall(UUID id, ReleaseInfo r, String err, String backup) {
        CommandSender target = id == null ? Bukkit.getConsoleSender() : Bukkit.getPlayer(id);

        if (err != null) {
            log().warning("Update to " + r.version() + " failed: " + err + " (nothing was changed)");
            if (target instanceof Player tp) {
                Chat.say(tp, "§c✖ Update failed: §f" + err);
                Chat.say(tp, "§7Nothing was changed - your current version keeps running normally.");
            }
            return;
        }

        stagedVersion = r.version();
        log().info("Update " + r.version() + " downloaded and verified. It will be applied on the next restart.");
        log().info("Backup of your config/data: " + backup);

        if (target instanceof Player tp) {
            Chat.say(tp, "§a✔ §fSmartWithdraw " + r.version() + " §ais downloaded and verified.");
            Chat.say(tp, "§7Restart the server to apply it. Backup saved in §f" + backup);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(PERMISSION) && !p.equals(target)) {
                Chat.say(p, "§a✔ SmartWithdraw §f" + r.version() + " §ais ready - restart the server to apply it.");
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    public static String currentVersion() {
        return Version.clean(SmartWithdraw.getInstance().getDescription().getVersion());
    }

    private static FileConfiguration cfg() {
        return SmartWithdraw.getInstance().getConfig();
    }

    private static boolean enabled() {
        return cfg().getBoolean("update.enabled", true);
    }

    private static boolean allowInstall() {
        return cfg().getBoolean("update.allow-install", true);
    }

    private static String repo() {
        String r = cfg().getString("update.github-repo", DEFAULT_REPO);
        r = (r == null || r.isBlank()) ? DEFAULT_REPO : r.trim();
        return REPO_PATTERN.matcher(r).matches() ? r : null;
    }

    private static Logger log() {
        return SmartWithdraw.getInstance().getLogger();
    }

    private static String friendly(Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return "Interrupted";
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.http.HttpTimeoutException) {
            return "Could not reach GitHub (offline or timed out)";
        }
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? e.getClass().getSimpleName() : m;
    }
}
