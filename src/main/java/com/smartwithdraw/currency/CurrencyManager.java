package com.smartwithdraw.currency;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CurrencyManager {

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

            Material material;
            try {
                material = Material.valueOf(
                        section.getString("material", "PAPER").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(
                        "Invalid material for '" + id + "', defaulting to PAPER.");
                material = Material.PAPER;
            }

            int expiryDays        = section.getInt("expiry-days", 0);
            long dailyLimit       = section.getLong("daily-limit", 0);
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
                    material,
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
                CurrencyBackend.VAULT, Material.GOLD_NUGGET,
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
