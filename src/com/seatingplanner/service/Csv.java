package com.seatingplanner.service;

import java.util.ArrayList;
import java.util.List;

/** RFC 4180 style CSV escaping and parsing. */
public final class Csv {

    private Csv() {
    }

    /**
     * Quotes a field when it contains a comma, quote, line break or surrounding spaces, doubling
     * embedded quotes. Fields starting with = + - @ are prefixed with an apostrophe so spreadsheet
     * programs do not execute them as formulas (CSV injection).
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        boolean needsQuotes = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")
                || (!v.isEmpty() && (Character.isWhitespace(v.charAt(0)) || Character.isWhitespace(v.charAt(v.length() - 1))));
        if (!needsQuotes) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    public static String row(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(fields[i]));
        }
        return sb.append("\r\n").toString();
    }

    /** Parses CSV text into rows of fields; quoted fields may contain commas, quotes and line breaks. */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean any = false;
        int i = 0;
        if (text.startsWith("﻿")) {
            i = 1; // skip UTF-8 byte order mark written by Excel
        }
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            any = true;
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                any = false;
            } else {
                field.append(c);
            }
        }
        if (any || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        rows.removeIf(r -> r.size() == 1 && r.get(0).isBlank());
        return rows;
    }
}
