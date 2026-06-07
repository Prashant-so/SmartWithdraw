package com.smartwithdraw.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class InventoryUtils {

```
private InventoryUtils() {
}

public static void give(Player player, ItemStack item) {

    player.getInventory().addItem(item);
}
```

}
