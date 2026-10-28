package com.dormitory.common;

public final class PassCodeUtil {
    private PassCodeUtil() {}

    public static String fromVisitorId(Long id) {
        long seed = id != null ? id : 1L;
        return String.format("VP-%06d", Math.abs(seed * 13579L + 802100L) % 1000000L);
    }

    public static String normalize(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toUpperCase();
        if (s.matches("\\d{1,6}")) {
            s = String.format("VP-%06d", Integer.parseInt(s));
        }
        return s;
    }
}
