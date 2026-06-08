package com.smartwithdraw.security;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class NoteValidator {

```
private NoteValidator() {
}

public static boolean isValid(ItemStack item) {

    if (item == null || !item.hasItemMeta()) {
        return false;
    }

    ItemMeta meta = item.getItemMeta();

    if (meta == null) {
        return false;
    }

    NamespacedKey valueKey =
            new NamespacedKey(SmartWithdraw.getInstance(), "note_value");

    NamespacedKey noteKey =
            new NamespacedKey(SmartWithdraw.getInstance(), "smartwithdraw_note");

    return meta.getPersistentDataContainer().has(
            valueKey,
            PersistentDataType.INTEGER
    ) && meta.getPersistentDataContainer().has(
            noteKey,
            PersistentDataType.BYTE
    );
}
```

}
