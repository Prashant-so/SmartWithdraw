package com.smartwithdraw.gui;

import com.smartwithdraw.currency.Currency;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class BankMenuHolder implements InventoryHolder {

    private Inventory inventory;
    private Currency currency;
    private boolean onCurrencySelectionPage;

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public boolean isOnCurrencySelectionPage() {
        return onCurrencySelectionPage;
    }

    public void setOnCurrencySelectionPage(boolean onCurrencySelectionPage) {
        this.onCurrencySelectionPage = onCurrencySelectionPage;
    }
}
