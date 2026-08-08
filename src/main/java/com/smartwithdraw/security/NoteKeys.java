package com.smartwithdraw.security;

public final class NoteKeys {

    private NoteKeys() {
    }

    public static final String NOTE_VALUE     = "note_value";
    public static final String NOTE_MARKER    = "smartwithdraw_note";
    public static final String NOTE_SIGNATURE = "note_signature";
    public static final String NOTE_CURRENCY  = "note_currency";
    public static final String NOTE_CREATED   = "note_created";   // epoch ms, for expiry
}
