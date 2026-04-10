package com.codewiki.util;

public final class Texts {

    private Texts() {
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
