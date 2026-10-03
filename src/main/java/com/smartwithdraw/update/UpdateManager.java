package com.smartwithdraw.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.util.Chat;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    private static final String API_URL = "https://api.github.com/repos/%s/releases/latest";
    private static final Pattern REPO_PATTERN = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final long MAX_JAR_BYTES = 25L * 1024 * 1024;
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
        cleanupLeftovers();
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
                found = fetchLatest(repo);
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

    private static ReleaseInfo fetchLatest(String repo) throws IOException, InterruptedException {
        if (repo == null) {
            throw new IOException("Set update.github-repo (OWNER/REPO) in config.yml");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(API_URL, repo)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SmartWithdraw-UpdateChecker")
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        int code = res.statusCode();
        if (code == 404) {
            throw new IOException("No published release found for " + repo
                    + " (repo must be public and have a release)");
        }
        if (code == 403 || code == 429) {
            throw new IOException("GitHub rate limit reached - will retry later");
        }
        if (code != 200) {
            throw new IOException("GitHub answered HTTP " + code);
        }

        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        String tag = str(o, "tag_name");
        if (tag == null) throw new IOException("Latest release has no tag");

        String version = Version.clean(tag);
        String page = str(o, "html_url");

        String url = null;
        String name = null;
        String digest = null;
        long size = 0;

        if (o.has("assets") && o.get("assets").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("assets")) {
                if (!el.isJsonObject()) continue;
                JsonObject a = el.getAsJsonObject();
                String assetName = str(a, "name");
                if (assetName == null) continue;
                String low = assetName.toLowerCase(Locale.ROOT);
                if (!low.endsWith(".jar") || low.contains("sources") || low.contains("javadoc")) continue;
                String dl = str(a, "browser_download_url");
                if (!trusted(dl)) continue;

                url = dl;
                name = assetName;
                if (a.has("size") && a.get("size").isJsonPrimitive()) {
                    size = a.get("size").getAsLong();
                }
                String d = str(a, "digest");
                if (d != null && d.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
                    digest = d.substring(7);
                }
                break;
            }
        }
        return new ReleaseInfo(version, page, url, name, size, digest);
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
                backup = stage(r, keep);
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

    /**
     * Runs on an async thread. Returns the backup folder path for display.
     * Any failure throws BEFORE the update folder is touched.
     */
    private static String stage(ReleaseInfo r, int keepBackups) throws Exception {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        Path data = plugin.getDataFolder().toPath();
        Path cache = data.resolve("update-cache");
        Files.createDirectories(cache);

        String safe = r.version().replaceAll("[^A-Za-z0-9._-]", "_");
        Path part = cache.resolve("download-" + safe + ".part");
        Files.deleteIfExists(part);

        try {
            // 1. download into our own cache folder (never into plugins/update)
            if (r.size() > MAX_JAR_BYTES) {
                throw new IOException("Release file is larger than the 25 MB safety limit");
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(r.downloadUrl()))
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "SmartWithdraw-UpdateChecker")
                    .header("Accept", "application/octet-stream")
                    .GET()
                    .build();

            CompletableFuture<HttpResponse<Path>> future =
                    http.sendAsync(req, HttpResponse.BodyHandlers.ofFile(part));
            HttpResponse<Path> res;
            try {
                res = future.get(120, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IOException("Download timed out");
            } catch (ExecutionException e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                throw new IOException("Download failed: " + c.getMessage());
            }
            if (res.statusCode() != 200) {
                throw new IOException("Download failed (HTTP " + res.statusCode() + ")");
            }

            // 2. verify before anything else happens
            long len = Files.size(part);
            if (len <= 0 || len > MAX_JAR_BYTES) throw new IOException("Downloaded file has an invalid size");
            if (r.size() > 0 && len != r.size()) throw new IOException("Download is incomplete (size mismatch)");
            if (r.sha256() != null && !r.sha256().equalsIgnoreCase(sha256(part))) {
                throw new IOException("Checksum mismatch - the file was corrupted or altered");
            }
            verifyJar(part, r);

            // 3. back up current jar + config + data files (abort on failure)
            String backup = backup(data, r.version(), keepBackups);

            // 4. stage atomically: copy to a non-.jar temp name, then rename
            Path updateDir = Bukkit.getUpdateFolderFile().toPath();
            Files.createDirectories(updateDir);
            String jarName = plugin.getJarFile().getName();
            Path tmp = updateDir.resolve(jarName + ".swtmp");
 
