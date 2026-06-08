package com.smartwithdraw.listener;

import com.smartwithdraw.economy.EconomyManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NoteRedeemListener implements Listener {

```
@EventHandler
public void onRedeem(PlayerInteractEvent event) {

    ItemStack item = event.getItem();

    if (item == null) {
        return;
    }

    if (item.getType() != Material.PAPER) {
        return;
    }

    if (!item.hasItemMeta()) {
        return;
    }

    if (!item.getItemMeta().hasLore()) {
        return;
    }

    List<String> lore = item.getItemMeta().getLore();

    if (lore == null) {
        return;
    }

    int value = -1;

    for (String line : lore) {

        if (line.contains("Value:")) {

            String cleaned = line
                    .replace("§fValue: §a₹", "")
                    .replace(",", "")
                    .trim();

            try {
                value = Integer.parseInt(cleaned);
            } catch (Exception ignored) {
            }

            break;
        }
    }

    if (value <= 0) {
        return;
    }

    EconomyManager.deposit(event.getPlayer(), value);

    if (item.getAmount() > 1) {
        item.setAmount(item.getAmount() - 1);
    } else {
        event.getPlayer().getInventory().remove(item);
    }

    event.getPlayer().sendMessage(
            "§6§lSmartWithdraw §8» §aDeposited ₹" + value
    );
}
```

}
