package com.smartwithdraw.security;

import com.smartwithdraw.SmartWithdraw;

import java.security.SecureRandom;
import java.util.Base64;

public final class SecretKeyManager {

    private static byte[] key;

    private SecretKeyManager() {
    }

    public static void load() {

        SmartWithdraw plugin = SmartWithdraw.getInstance();
        String stored = plugin.getConfig().getString("security.secret", "");

        if (stored == null || stored.isBlank()) {

            byte[] generated = new byte[32];
            new SecureRandom().nextBytes(generated);

            String encoded = Base64.getEncoder().encodeToString(generated);

            plugin.getConfig().set("security.secret", encoded);
            plugin.saveConfig();

            key = generated;

            plugin.getLogger().info("Generated a new note-signing secret key.");

            return;
        }

        key = Base64.getDecoder().decode(stored);
    }

    public static byte[] getKey() {

        if (key == null) {
            load();
        }

        return key;
    }
}
