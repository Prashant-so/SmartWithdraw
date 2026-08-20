package com.smartwithdraw.currency;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CurrencyManager {

    // Skull textures hardcoded per currency id.
    // To change the head for a currency, update the value here
    // and rebuild — it is intentionally not configurable so server
    // owners cannot accidentally break notes by entering wrong values.
    private static final Map<String, String> SKULL_TEXTURES = Map.of(
            "coins",  "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODRiMzI3MTBjZWE1NjA4NTRlM2ZhYzE5MmYyMGE2MzNmNzI5YWZhMDJkZTRiMDc1ZmVmYjY4MGIzMDE2NThlMCJ9fX0=",
            "points", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjhiNDJmODZlZmE1ZjBmMzk2YTQwZDc3MzU0OTQxNDMzM2QxYzgwMjY5ZDA1ODBiYjg0YTY0YWI2Yjk2N2Q2ZSJ9fX0=",
            "xp",     "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjUzZDc3NDI0Y2NiMTFmNzQyOTMyNDg3NzEzZTJlNDFiNDgxZjkxZWQxMTg3NGZkZmM1NzZlNDJkNTU5YTg2NiJ9fX0="
    );

    // Fallback texture used when currency id has no matching skull
    private static final String FALLBACK_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTNlNmMwNGU5NDM0ZjkwNzJhZjU3NDY0ODQ3MTNkZWRjMjdmOWZhNzA5MDc5ZjA5MjYxNzk4M2Y1NGQ0ZGJlZSJ9fX0=";

    private static final Map<String, Currency> CURRENCIES = new LinkedHashMap<>();
    private static String defaultCurrencyId = "coins";

    private CurrencyManager() {
    }

    public static void load() {

        CURRENCIES.clear();

        SmartWithdraw plugin = SmartWithdraw.getInstance();
        ConfigurationSection root = plugin.getConfig()
                .getConfigurationSection("currencies");

        if (root == null) {
            fallbackToDefault(plugin);
            return;
        }

        boolean foundDefault = false;

        for (String id : root.getKeys(false)) {

            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            boolean isDefault = section.getBoolean("default", false);
            boolean enabled   = section.getBoolean("enabled", true);

            List<Integer> denominations = section.getIntegerList("denominations");
            if (denominations.isEmpty()) {
                denominations = List.of(1, 10, 50, 100, 500, 2000);
            }

            CurrencyBackend backend;
            try {
                backend = CurrencyBackend.valueOf(
                        section.getString("backend", "VAULT").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(
                        "Invalid backend for '" + id + "', defaulting to VAULT.");
                backend = CurrencyBackend.VAULT;
            }

            String skullTexture = SKULL_TEXTURES.getOrDefault(
                    id.toLowerCase(), FALLBACK_TEXTURE);

            int expiryDays             = section.getInt("expiry-days", 0);
            long dailyLimit            = section.getLong("daily-limit", 0);
            List<String> allowedWorlds = section.getStringList("allowed-worlds");

            ConfigurationSection taxSection = section.getConfigurationSection("tax");
            TaxConfig tax = taxSection != null
                    ? new TaxConfig(
                            taxSection.getDouble("percent", 0.0),
                            taxSection.getBoolean("apply-on-withdraw", false),
                            taxSection.getBoolean("apply-on-deposit", false))
                    : TaxConfig.NONE;

            ConfigurationSection loreSection = section.getConfigurationSection("lore");
            List<String> template = loreSection != null
                    ? loreSection.getStringList("template")
                    : List.of();

            LoreStyle lore = new LoreStyle(
                    loreSection != null ? loreSection.getString("title-color", "&f") : "&f",
                    loreSection != null && loreSection.getBoolean("show-tier", false),
                    loreSection != null ? loreSection.getString("tier", "Common Tier") : "Common Tier",
                    loreSection != null ? loreSection.getString("gain-text", id) : id,
                    template
            );

            Currency currency = new Currency(
                    id,
                    section.getString("symbol", "$"),
                    section.getString("name", id),
                    section.getString("name-plural", id),
                    section.getString("format", "%symbol%%amount%"),
                    denominations,
                    section.getInt("model-data-base", 1000),
                    isDefault,
                    enabled,
                    backend,
                    skullTexture,
                    expiryDays,
                    dailyLimit,
                    allowedWorlds,
                    tax,
                    lore
            );

            CURRENCIES.put(id.toLowerCase(), currency);

            if (isDefault) {
                defaultCurrencyId = id.toLowerCase();
                foundDefault = true;
            }
        }

        if (CURRENCIES.isEmpty()) {
            fallbackToDefault(plugin);
            return;
        }

        if (!foundDefault) {
            defaultCurrencyId = CURRENCIES.keySet().iterator().next();
        }
    }

    private static void fallbackToDefault(SmartWithdraw plugin) {

        plugin.getLogger().warning(
                "No 'currencies' section found — falling back to default currency.");

        CURRENCIES.put("coins", new Currency(
                "coins", "$", "Money", "Money", "%symbol%%amount%",
                List.of(1, 10, 50, 100, 500, 2000), 1000, true, true,
                CurrencyBackend.VAULT,
                SKULL_TEXTURES.get("coins"),
                0, 0, List.of(), TaxConfig.NONE,
                new LoreStyle("&e", true, "Common Tier", "money", List.of())
        ));

        defaultCurrencyId = "coins";
    }

    public static Optional<Currency> get(String id) {
        return id == null
                ? Optional.empty()
                : Optional.ofNullable(CURRENCIES.get(id.toLowerCase()));
    }

    public static Currency getDefault() {
        return CURRENCIES.get(defaultCurrencyId);
    }

    public static Map<String, Currency> getAll() {
        return CURRENCIES;
    }

    public static Map<String, Currency> getAllEnabled() {
        Map<String, Currency> result = new LinkedHashMap<>();
        CURRENCIES.forEach((k, v) -> { if (v.enabled()) result.put(k, v); });
        return result;
    }

    public static boolean exists(String id) {
        return id != null && CURRENCIES.containsKey(id.toLowerCase());
    }
}
