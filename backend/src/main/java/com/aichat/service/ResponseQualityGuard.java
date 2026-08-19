package com.aichat.service;

import java.util.HashMap;
import java.util.Map;

/** Detects likely generation loops without changing the model response. */
public final class ResponseQualityGuard {

    private static final int MIN_REPEATED_LINE_LENGTH = 16;
    private static final int REPEATED_LINE_THRESHOLD = 3;

    private ResponseQualityGuard() {
    }

    public static boolean isLikelyRepetitive(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        Map<String, Integer> lineCounts = new HashMap<>();
        for (String line : text.split("\\R")) {
            String normalized = line.trim().replaceAll("\\s+", " ");
            if (normalized.length() >= MIN_REPEATED_LINE_LENGTH
                    && lineCounts.merge(normalized, 1, Integer::sum) >= REPEATED_LINE_THRESHOLD) {
                return true;
            }
        }

        String normalized = text.replaceAll("\\s+", " ").trim();
        for (int length = 24; length <= 96 && length * REPEATED_LINE_THRESHOLD <= normalized.length(); length += 8) {
            for (int start = 0; start + length * REPEATED_LINE_THRESHOLD <= normalized.length(); start++) {
                String segment = normalized.substring(start, start + length);
                int second = start + length;
                int third = second + length;
                if (normalized.startsWith(segment, second) && normalized.startsWith(segment, third)) {
                    return true;
                }
            }
        }
        return false;
    }
}
