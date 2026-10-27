package com.seatingplanner.model;

import java.util.Comparator;

/** Helpers for identifiers: normalisation and "natural" ordering (A2 before A10). */
public final class Ids {

    public static final Comparator<String> NATURAL = Ids::compareNatural;

    private Ids() {
    }

    /** Trims and upper-cases an identifier; null becomes an empty string. */
    public static String normalize(String id) {
        return id == null ? "" : id.trim().toUpperCase();
    }

    /** Case-insensitive comparison that orders embedded numbers numerically. */
    public static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = stripZeros(a.substring(si, i));
                String nb = stripZeros(b.substring(sj, j));
                if (na.length() != nb.length()) {
                    return na.length() - nb.length();
                }
                int c = na.compareTo(nb);
                if (c != 0) {
                    return c;
                }
            } else {
                int c = Character.compare(Character.toUpperCase(ca), Character.toUpperCase(cb));
                if (c != 0) {
                    return c;
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String stripZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') {
            k++;
        }
        return digits.substring(k);
    }
}
