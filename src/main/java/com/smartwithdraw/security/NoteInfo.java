package com.smartwithdraw.security;

import com.smartwithdraw.currency.Currency;

public record NoteInfo(
        Currency currency,
        int value,
        long createdAt
) {
}
