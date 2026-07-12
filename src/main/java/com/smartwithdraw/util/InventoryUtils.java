package com.smartwithdraw.util;

import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.security.NoteInfo;
import com.smartwithdraw.security.NoteValidator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class InventoryUtils {

    private InventoryUtils() {
    }

    public static void give(Player player, ItemStack item) {

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

        if (!leftover.isEmpty()) {

            leftover.values().forEach(stack ->
                    player.getWorld().dropItemNaturally(
                            player.getLocation(),
                            stack
                    )
            );
        }
    }

    /**
     * Sums the total value of all verified notes of the given currency
     * currently held by the player. Used to enforce max-held-value.
     */
    public static long sumHeldNoteValue(Player player, Currency currency) {

        long total = 0;

        for (ItemStack item : player.getInventory().getContents()) {

            Optional<NoteInfo> info = NoteValidator.getInfo(item);

            if (info.isEmpty() || !info.get().currency().id().equals(currency.id())) {
                continue;
            }

            total += (long) info.get().value() * item.getAmount();
        }

        return total;
    }

    /**
     * Sums the total value of all verified notes of ANY currency
     * currently held by the player, grouped by currency id.
     */
    public static Map<String, Long> sumHeldNoteValueByCurrency(Player player) {

        Map<String, Long> totals = new HashMap<>();

        for (ItemStack item : player.getInventory().getContents()) {

            Optional<NoteInfo> info = NoteValidator.getInfo(item);

            if (info.isEmpty()) {
                continue;
            }

            totals.merge(
                    info.get().currency().id(),
                    (long) info.get().value() * item.getAmount(),
                    Long::sum
            );
        }

        return totals;
    }
}
