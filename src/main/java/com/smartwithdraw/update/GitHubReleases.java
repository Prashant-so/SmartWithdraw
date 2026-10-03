package com.smartwithdraw.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** Talks to the GitHub Releases API. Pure network + parsing, no Bukkit state. */
final class GitHubReleases {

    private static final String API_URL = "https://api.github.com/repos/%s/releases/latest";

    private GitHubReleases() {
    }

    static ReleaseInfo fetchLatest(HttpClient http, String repo) throws IOException, InterruptedException {
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

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    /** Only ever download from GitHub over HTTPS. */
    private static boolean trusted(String url) {
        if (url == null) return false;
        try {
            URI u = URI.create(url);
            String host = u.getHost();
            if (!"https".equalsIgnoreCase(u.getScheme()) || host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("github.com")
                    || host.endsWith(".github.com")
                    || host.endsWith(".githubusercontent.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
