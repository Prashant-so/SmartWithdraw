package com.smartwithdraw.update;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Downloads, verifies, backs up and stages a new jar.
 * The running jar is never touched - Bukkit swaps the staged one in on restart.
 * Every method here runs on an async thread.
 */
final class UpdateInstaller {

    private static final long MAX_JAR_BYTES = 25L * 1024 * 1024;

    private UpdateInstaller() {
    }

    /**
     * Runs on an async thread. Returns the backup folder path for display.
     * Any failure throws BEFORE the update folder is touched.
     */
    static String stage(HttpClient http, ReleaseInfo r, int keepBackups) throws Exception {
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
            Path target = updateDir.resolve(jarName);
            try {
                Files.copy(part, tmp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
            return backup;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private static void verifyJar(Path file, ReleaseInfo r) throws IOException {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        try (JarFile jar = new JarFile(file.toFile())) {
            var entry = jar.getJarEntry("plugin.yml");
            if (entry == null) throw new IOException("The file is not a Bukkit plugin (no plugin.yml)");

            YamlConfiguration yml;
            try (InputStreamReader reader =
                         new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
                yml = YamlConfiguration.loadConfiguration(reader);
            }
            String name = yml.getString("name", "");
            String main = yml.getString("main", "");
            String ver = Version.clean(yml.getString("version", ""));

            if (!name.equalsIgnoreCase(plugin.getName())) {
                throw new IOException("The file is a different plugin (" + name + ")");
            }
            if (!main.equals(plugin.getDescription().getMain())) {
                throw new IOException("The file has a different main class");
            }
            if (jar.getJarEntry(main.replace('.', '/') + ".class") == null) {
                throw new IOException("The jar is missing its main class");
            }
            if (!ver.equals(r.version())) {
                throw new IOException("The jar says version " + ver + " but the release is "
                        + r.version() + " (plugin.yml version must match the release tag)");
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("The downloaded file is not a valid jar");
        }
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    // ── backups ──────────────────────────────────────────────────────

    private static String backup(Path data, String newVersion, int keep) throws IOException {
        Path root = data.resolve("backups");
        Files.createDirectories(root);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String folder = "before-" + newVersion.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + stamp;
        Path dir = root.resolve(folder);
        Files.createDirectories(dir);

        Path jar = SmartWithdraw.getInstance().getJarFile().toPath();
        Files.copy(jar, dir.resolve(jar.getFileName().toString()));

        try (Stream<Path> files = Files.list(data)) {
            for (Path p : files.toList()) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(p)
                        && (n.endsWith(".yml") || n.endsWith(".yaml")
                        || n.endsWith(".json") || n.endsWith(".dat"))) {
                    Files.copy(p, dir.resolve(p.getFileName().toString()));
                }
            }
        }
        prune(root, keep);
        return "plugins/" + data.getFileName() + "/backups/" + folder;
    }

    private static void prune(Path root, int keep) {
        try (Stream<Path> dirs = Files.list(root)) {
            List<Path> list = dirs
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("before-"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .toList();
            for (int i = keep; i < list.size(); i++) deleteTree(list.get(i));
        } catch (IOException ignored) {
            // pruning is best-effort
        }
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort
                }
            });
        } catch (IOException ignored) {
            // best-effort
        }
    }

    /** After a crash/stop mid-download: remove half-written files on next start. */
    static void cleanupLeftovers() {
        SmartWithdraw plugin = SmartWithdraw.getInstance();
        try {
            Path cache = plugin.getDataFolder().toPath().resolve("update-cache");
            if (Files.isDirectory(cache)) {
                try (Stream<Path> s = Files.list(cache)) {
                    for (Path p : s.toList()) {
                        if (p.getFileName().toString().endsWith(".part")) Files.deleteIfExists(p);
                    }
                }
            }
            Files.deleteIfExists(Bukkit.getUpdateFolderFile().toPath()
                    .resolve(plugin.getJarFile().getName() + ".swtmp"));
        } catch (IOException | RuntimeException ignored) {
            // cleanup is best-effort
        }
    }
                            }
