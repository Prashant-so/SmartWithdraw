package com.smartwithdraw.security;

import com.smartwithdraw.currency.Currency;

/**
 * The verified contents of a note item: which currency it belongs to
 * and its value. Only ever constructed after signature verification
 * succeeds, so holding one of these means the note is authentic.
 */
public record NoteInfo(Currency currency, int value) {
}
