package com.example.tubespbolkel7.ui.login;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LayoutJsonParser {
    private final String source;
    private int position;

    LayoutJsonParser(String source) {
        this.source = source;
    }

    Object parse() throws IOException {
        Object result = value();
        whitespace();
        if (position != source.length()) {
            throw invalid();
        }
        return result;
    }

    private Object value() throws IOException {
        whitespace();
        if (position == source.length()) {
            throw invalid();
        }
        return switch (source.charAt(position)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> quoted();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> numeric();
        };
    }

    private Map<String, Object> object() throws IOException {
        position++;
        Map<String, Object> result = new LinkedHashMap<>();
        if (!take('}')) {
            do {
                whitespace();
                String key = quoted();
                require(':');
                result.put(key, value());
            } while (take(','));
            require('}');
        }
        return result;
    }

    private List<Object> array() throws IOException {
        position++;
        List<Object> result = new ArrayList<>();
        if (!take(']')) {
            do {
                result.add(value());
            } while (take(','));
            require(']');
        }
        return result;
    }

    private String quoted() throws IOException {
        require('"');
        StringBuilder result = new StringBuilder();
        while (position < source.length()) {
            char next = source.charAt(position++);
            if (next == '"') {
                return result.toString();
            }
            if (next == '\\') {
                next = escaped();
            }
            result.append(next);
        }
        throw invalid();
    }

    private char escaped() throws IOException {
        if (position == source.length()) {
            throw invalid();
        }
        return switch (source.charAt(position++)) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> unicode();
            default -> throw invalid();
        };
    }

    private char unicode() throws IOException {
        if (position + 4 > source.length()) {
            throw invalid();
        }
        try {
            char value = (char) Integer.parseInt(source.substring(position, position + 4), 16);
            position += 4;
            return value;
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private Object literal(String text, Object value) throws IOException {
        if (!source.startsWith(text, position)) {
            throw invalid();
        }
        position += text.length();
        return value;
    }

    private Number numeric() throws IOException {
        int start = position;
        while (position < source.length() && "0123456789.-+eE".indexOf(source.charAt(position)) >= 0) {
            position++;
        }
        try {
            return Double.parseDouble(source.substring(start, position));
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private void whitespace() {
        while (position < source.length() && Character.isWhitespace(source.charAt(position))) {
            position++;
        }
    }

    private boolean take(char token) {
        whitespace();
        if (position < source.length() && source.charAt(position) == token) {
            position++;
            return true;
        }
        return false;
    }

    private void require(char token) throws IOException {
        if (!take(token)) {
            throw invalid();
        }
    }

    private IOException invalid() {
        return new IOException("Data desain rusak di posisi " + position + '.');
    }
}