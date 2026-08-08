package com.smartwithdraw.security;

import com.smartwithdraw.currency.Currency;

public record NoteInfo(
        Currency currency,
        int value,
        long createdAt  // epoch ms when the note was created
) {
}
