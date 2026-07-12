package com.smartwithdraw.currency;

import com.smartwithdraw.SmartWithdraw;
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
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("currencies");

        if (root == null) {
            fallbackToDefault(plugin);
            return;
        }

        boolean foundDefault = false;

        for (String id : root.getKeys(false)) {

            ConfigurationSection section = root.getConfigurationSection(id);

            if (section == null) {
                continue;
            }

            boolean isDefault = section.getBoolean("default", false);
            boolean enabled = section.getBoolean("enabled", true);

            List<Integer> denominations = section.getIntegerList("denominations");

            if (denominations.isEmpty()) {
                denominations = List.of(1, 10, 50, 100, 500, 2000);
            }

            CurrencyBackend backend;

            try {
                backend = CurrencyBackend.valueOf(
                        section.getString("backend", "VAULT").toUpperCase()
                );
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(
                        "Invalid backend for currency '" + id + "', defaulting to VAULT."
                );
                backend = CurrencyBackend.VAULT;
            }

            ConfigurationSection loreSection = section.getConfigurationSection("lore");

            LoreStyle lore = new LoreStyle(
                    loreSection != null ? loreSection.getString("title-color", "&f") : "&f",
                    loreSection != null && loreSection.getBoolean("show-tier", false),
                    loreSection != null ? loreSection.getString("tier", "Common Tier") : "Common Tier",
                    loreSection != null ? loreSection.getString("gain-text", section.getString("name", id)) : section.getString("name", id),
                    loreSection != null ? loreSection.getDouble("tax-percent", 0.0) : 0.0
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
                "No usable 'currencies' section found in config.yml — falling back to a single default currency."
        );

        CURRENCIES.put("coins", new Currency(
                "coins", "$", "Money", "Money", "%symbol%%amount%",
                List.of(1, 10, 50, 100, 500, 2000), 1000, true, true, CurrencyBackend.VAULT,
                new LoreStyle("&e", true, "Common Tier", "money", 0.0)
        ));

        defaultCurrencyId = "coins";
    }

    public static Optional<Currency> get(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(CURRENCIES.get(id.toLowerCase()));
    }

    public static Currency getDefault() {
        return CURRENCIES.get(defaultCurrencyId);
    }

    public static Map<String, Currency> getAll() {
        return CURRENCIES;
    }

    /**
     * Only currencies with enabled: true - used everywhere a player
     * picks/sees a currency (help text, GUI, /sw currencies).
     */
    public static Map<String, Currency> getAllEnabled() {

        Map<String, Currency> enabled = new LinkedHashMap<>();

        for (Map.Entry<String, Currency> entry : CURRENCIES.entrySet()) {
            if (entry.getValue().enabled()) {
                enabled.put(entry.getKey(), entry.getValue());
            }
        }

        return enabled;
    }

    public static boolean exists(String id) {
        return id != null && CURRENCIES.containsKey(id.toLowerCase());
    }
}
