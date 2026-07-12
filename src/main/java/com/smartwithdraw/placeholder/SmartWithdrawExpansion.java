package com.smartwithdraw.placeholder;

import com.smartwithdraw.balance.BalanceProvider;
import com.smartwithdraw.balance.BalanceProviderRegistry;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import com.smartwithdraw.util.InventoryUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * %smartwithdraw_balance_formatted%          - default currency balance, formatted
 * %smartwithdraw_balance_formatted_<id>%      - specific currency balance, formatted
 * %smartwithdraw_held_value_formatted%        - default currency notes held
 * %smartwithdraw_held_value_formatted_<id>%   - specific currency notes held
 */
public class SmartWithdrawExpansion extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "smartwithdraw";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SmartWithdraw";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {

        if (player == null) {
            return "";
        }

        Currency defaultCurrency = CurrencyManager.getDefault();
        String lower = params.toLowerCase();

        if (lower.equals("balance_formatted")) {
            return formattedBalance(player, defaultCurrency);
        }

        if (lower.equals("held_value_formatted")) {
            return defaultCurrency.format(InventoryUtils.sumHeldNoteValue(player, defaultCurrency));
        }

        if (lower.startsWith("balance_formatted_")) {

            String currencyId = lower.substring("balance_formatted_".length());

            return CurrencyManager.get(currencyId)
                    .map(c -> formattedBalance(player, c))
                    .orElse("");
        }

        if (lower.startsWith("held_value_formatted_")) {

            String currencyId = lower.substring("held_value_formatted_".length());

            return CurrencyManager.get(currencyId)
                    .map(c -> c.format(InventoryUtils.sumHeldNoteValue(player, c)))
                    .orElse("");
        }

        return null;
    }

    private String formattedBalance(Player player, Currency currency) {

        BalanceProvider provider = BalanceProviderRegistry.get(currency.backend());

        if (provider == null || !provider.isAvailable()) {
            return "";
        }

        return currency.format(provider.getBalance(player));
    }
}
