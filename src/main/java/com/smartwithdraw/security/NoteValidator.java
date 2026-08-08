package com.smartwithdraw.security;

import com.smartwithdraw.SmartWithdraw;
import com.smartwithdraw.currency.Currency;
import com.smartwithdraw.currency.CurrencyManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

public final class NoteValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private NoteValidator() {
    }

    public static String sign(String currencyId, int value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(SecretKeyManager.getKey(), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(
                    (currencyId + ":" + value)
                            .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign note", e);
        }
    }

    public static boolean isValid(ItemStack item) {
        return getInfo(item).isPresent();
    }

    /**
     * Returns true if the note is valid AND not expired.
     */
    public static boolean isValidAndNotExpired(ItemStack item) {
        Optional<NoteInfo> info = getInfo(item);
        if (info.isEmpty()) return false;
        return !isExpired(info.get());
    }

    public static boolean isExpired(NoteInfo info) {
        int expiryDays = info.currency().expiryDays();
        if (expiryDays <= 0) return false;
        long expiryMs = info.createdAt() + (long) expiryDays * 86_400_000L;
        return System.currentTimeMillis() > expiryMs;
    }

    /**
     * Returns days remaining until expiry, or -1 if no expiry set.
     */
    public static long daysRemaining(NoteInfo info) {
        int expiryDays = info.currency().expiryDays();
        if (expiryDays <= 0) return -1;
        long expiryMs  = info.createdAt() + (long) expiryDays * 86_400_000L;
        long remaining = expiryMs - System.currentTimeMillis();
        if (remaining <= 0) return 0;
        return (long) Math.ceil(remaining / 86_400_000.0);
    }

    public static Optional<NoteInfo> getInfo(ItemStack item) {

        if (item == null || !item.hasItemMeta()) return Optional.empty();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        NamespacedKey markerKey    = new NamespacedKey(plugin, NoteKeys.NOTE_MARKER);
        NamespacedKey valueKey     = new NamespacedKey(plugin, NoteKeys.NOTE_VALUE);
        NamespacedKey currencyKey  = new NamespacedKey(plugin, NoteKeys.NOTE_CURRENCY);
        NamespacedKey signatureKey = new NamespacedKey(plugin, NoteKeys.NOTE_SIGNATURE);
        NamespacedKey createdKey   = new NamespacedKey(plugin, NoteKeys.NOTE_CREATED);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(markerKey,    PersistentDataType.BYTE)
         || !pdc.has(valueKey,     PersistentDataType.INTEGER)
         || !pdc.has(currencyKey,  PersistentDataType.STRING)
         || !pdc.has(signatureKey, PersistentDataType.STRING)) {
            return Optional.empty();
        }

        Integer value     = pdc.get(valueKey,     PersistentDataType.INTEGER);
        String  currencyId = pdc.get(currencyKey,  PersistentDataType.STRING);
        String  signature = pdc.get(signatureKey, PersistentDataType.STRING);
        Long    createdAt = pdc.has(createdKey, PersistentDataType.LONG)
                ? pdc.get(createdKey, PersistentDataType.LONG)
                : 0L;

        if (value == null || value <= 0 || currencyId == null || signature == null) {
            return Optional.empty();
        }

        String expected = sign(currencyId, value);

        boolean matches = MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));

        if (!matches) return Optional.empty();

        Optional<Currency> currency = CurrencyManager.get(currencyId);

        return currency.map(c -> new NoteInfo(c, value, createdAt));
    }
}
