package com.smartwithdraw.listener;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.economy.EconomyManager;
import com.smartwithdraw.security.NoteValidator;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class NoteRedeemListener implements Listener {

```
@EventHandler
public void onRedeem(PlayerInteractEvent event) {

    if (event.getHand() != EquipmentSlot.HAND) {
        return;
    }

    if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
        return;
    }

    ItemStack item = event.getItem();

    if (!NoteValidator.isValid(item)) {
        return;
    }

    ItemMeta meta = item.getItemMeta();

    if (meta == null) {
        return;
    }

    NamespacedKey valueKey =
            new NamespacedKey(SmartWithdraw.getInstance(), "note_value");

    Integer value = meta.getPersistentDataContainer().get(
            valueKey,
            PersistentDataType.INTEGER
    );

    if (value == null || value <= 0) {
        return;
    }

    Player player = event.getPlayer();

    EconomyManager.deposit(player, value);

    if (item.getAmount() > 1) {
        item.setAmount(item.getAmount() - 1);
    } else {
        player.getInventory().setItemInMainHand(null);
    }

    player.sendMessage(
            "§6§lSmartWithdraw §8» §aDeposited ₹" + value
    );

    event.setCancelled(true);
}
```

}
