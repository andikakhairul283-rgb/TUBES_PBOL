package com.example.tubespbolkel7.ui.login;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

final class LoginLayout {
    record Model(Layer screen, Map<?, ?> paths) {
        String path(int id) {
            return (String) paths.get(Integer.toString(id));
        }
    }

    record Layer(Map<?, ?> properties, List<Layer> children) {
        String id() { return string(properties, "id"); }
        String name() { return string(properties, "name"); }
        String type() { return string(properties, "type"); }
        List<?> size() { return list(properties, "size"); }
        double width() { return number(size(), 0, 0); }
        double height() { return number(size(), 1, 0); }
        List<?> matrix() { return list(properties, "matrix"); }
        Map<?, ?> text() { return map(properties.get("text")); }
        String characters() { return string(text(), "characters"); }
    }

    static Model load() throws IOException {
        try (InputStream input = LoginLayout.class.getResourceAsStream("layout.json.gz")) {
            if (input == null) {
                throw new IOException("Resource desain CheckIn.id tidak ditemukan.");
            }
            try (Reader reader = new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8)) {
                StringBuilder source = new StringBuilder();
                char[] buffer = new char[16384];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    source.append(buffer, 0, count);
                }
                Map<?, ?> root = map(new Json(source.toString()).value());
                return new Model(readLayer(root.get("screen")), map(root.get("paths")));
            }
        }
    }

    private static Layer readLayer(Object entry) {
        Map<?, ?> properties = map(entry);
        List<Layer> children = list(properties, "children").stream().map(LoginLayout::readLayer).toList();
        return new Layer(properties, children);
    }

    static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    static List<?> list(Object value) { return value instanceof List<?> list ? list : List.of(); }
    static List<?> list(Map<?, ?> map, String key) { return list(map.get(key)); }
    static String string(Map<?, ?> map, String key) { return map.get(key) instanceof String value ? value : ""; }
    static double number(Map<?, ?> map, String key, double fallback) { return number(map.get(key), fallback); }
    static double number(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }

    static double number(List<?> list, int index, double fallback) {
        return index < list.size() ? number(list.get(index), fallback) : fallback;
    }

    private static final class Json {
        private final String source;
        private int position;

        Json(String source) { this.source = source; }

        Object value() throws IOException {
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
}
