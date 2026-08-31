package com.example.discovery;

import java.util.Locale;

public enum SearchScope {
    CATALOG,
    OMDB;

    public static SearchScope from(String value) {
        if (value == null || value.isBlank()) return OMDB;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return OMDB;
        }
    }

    public String requestValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean includesCatalog() {
        return this == CATALOG;
    }

    public boolean includesOmdb() {
        return this == OMDB;
    }
}
