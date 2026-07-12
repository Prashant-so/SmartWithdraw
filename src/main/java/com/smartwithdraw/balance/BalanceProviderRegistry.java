package com.smartwithdraw.balance;

import com.smartwithdraw.currency.CurrencyBackend;

import java.util.EnumMap;
import java.util.Map;

public final class BalanceProviderRegistry {

    private static final Map<CurrencyBackend, BalanceProvider> PROVIDERS = new EnumMap<>(CurrencyBackend.class);

    static {
        PROVIDERS.put(CurrencyBackend.VAULT, new VaultBalanceProvider());
        PROVIDERS.put(CurrencyBackend.PLAYERPOINTS, new PlayerPointsBalanceProvider());
        PROVIDERS.put(CurrencyBackend.XP, new XpBalanceProvider());
    }

    private BalanceProviderRegistry() {
    }

    public static BalanceProvider get(CurrencyBackend backend) {
        return PROVIDERS.get(backend);
    }
}
