package com.smartwithdraw.security;

import com.smartwithdraw.SmartWithdraw;
import org.bukkit.NamespacedKey;

public class NoteKeys {

    public static final NamespacedKey VALUE =
            new NamespacedKey(SmartWithdraw.getInstance(), "value");

    public static final NamespacedKey SIGNATURE =
            new NamespacedKey(SmartWithdraw.getInstance(), "signature");
}
