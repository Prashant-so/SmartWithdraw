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

    /**
     * Computes the signature for a given currency+value pair using the
     * plugin's secret key. Notes of the same currency and value always
     * get the same signature, so identical notes still stack normally.
     */
    public static String sign(String currencyId, int value) {

        try {

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);

            mac.init(new SecretKeySpec(
                    SecretKeyManager.getKey(),
                    HMAC_ALGORITHM
            ));

            String payload = currencyId + ":" + value;

            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(raw);

        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign note", e);
        }
    }

    public static boolean isValid(ItemStack item) {
        return getInfo(item).isPresent();
    }

    public static Integer getValue(ItemStack item) {
        return getInfo(item).map(NoteInfo::value).orElse(null);
    }

    /**
     * Returns the verified currency + value of a note, or empty if the
     * item is not a note, references an unknown/removed currency, or
     * fails signature verification (i.e. was forged or edited).
     */
    public static Optional<NoteInfo> getInfo(ItemStack item) {

        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return Optional.empty();
        }

        SmartWithdraw plugin = SmartWithdraw.getInstance();

        NamespacedKey markerKey = new NamespacedKey(plugin, NoteKeys.NOTE_MARKER);
        NamespacedKey valueKey = new NamespacedKey(plugin, NoteKeys.NOTE_VALUE);
        NamespacedKey currencyKey = new NamespacedKey(plugin, NoteKeys.NOTE_CURRENCY);
        NamespacedKey signatureKey = new NamespacedKey(plugin, NoteKeys.NOTE_SIGNATURE);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(markerKey, PersistentDataType.BYTE)
                || !pdc.has(valueKey, PersistentDataType.INTEGER)
                || !pdc.has(currencyKey, PersistentDataType.STRING)
                || !pdc.has(signatureKey, PersistentDataType.STRING)) {
            return Optional.empty();
        }

        Integer value = pdc.get(valueKey, PersistentDataType.INTEGER);
        String currencyId = pdc.get(currencyKey, PersistentDataType.STRING);
        String signature = pdc.get(signatureKey, PersistentDataType.STRING);

        if (value == null || value <= 0 || currencyId == null || signature == null) {
            return Optional.empty();
        }

        String expected = sign(currencyId, value);

        boolean matches = MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            return Optional.empty();
        }

        Optional<Currency> currency = CurrencyManager.get(currencyId);

        return currency.map(c -> new NoteInfo(c, value));
    }
}
