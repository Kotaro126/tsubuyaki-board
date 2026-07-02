package com.example.tsubuyaki.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TagParser {

    private TagParser() {
    }

    public static List<String> extractNames(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        // 入力順を保ちながら重複タグを除くため LinkedHashSet に一度集約する。
        Set<String> tags = new LinkedHashSet<>();
        int index = 0;
        while (index < body.length()) {
            // 仕様上、全角の「＃」はタグ開始として扱わず半角 # だけを検出する。
            int hashIndex = body.indexOf('#', index);
            if (hashIndex < 0) {
                break;
            }

            int start = hashIndex + 1;
            int end = start;
            while (end < body.length() && !isTagSeparator(body.charAt(end))) {
                end++;
            }

            if (end > start) {
                tags.add(body.substring(start, end));
            }
            index = end + 1;
        }
        return new ArrayList<>(tags);
    }

    private static boolean isTagSeparator(char character) {
        // Java の空白判定に加え、全角スペースもタグ区切りとして明示的に扱う。
        return Character.isWhitespace(character) || character == '　';
    }
}
