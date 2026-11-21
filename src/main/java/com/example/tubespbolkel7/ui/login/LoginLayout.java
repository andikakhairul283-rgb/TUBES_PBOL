package com.example.tubespbolkel7.ui.login;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

final class LoginLayout {
    private static final String LAYOUT_RESOURCE = "layout.json.gz";

    private LoginLayout() { }

    record Model(Layer screen, Map<?, ?> paths) {
        String path(int id) {
            return paths.get(Integer.toString(id)) instanceof String str ? str : null;
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
        try (InputStream input = LoginLayout.class.getResourceAsStream(LAYOUT_RESOURCE)) {
            if (input == null) {
                throw new IOException("Resource desain CheckIn.id tidak ditemukan: " + LAYOUT_RESOURCE);
            }
            try (Reader reader = new InputStreamReader(new GZIPInputStream(input), StandardCharsets.UTF_8)) {
                String jsonContent = readAll(reader);
                Map<?, ?> root = map(new LayoutJsonParser(jsonContent).parse());
                return new Model(readLayer(root.get("screen")), map(root.get("paths")));
            }
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder source = new StringBuilder(16384);
        char[] buffer = new char[16384];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            source.append(buffer, 0, count);
        }
        return source.toString();
    }

    private static Layer readLayer(Object entry) {
        Map<?, ?> properties = map(entry);
        List<Layer> children = list(properties, "children").stream()
                .map(LoginLayout::readLayer)
                .toList();
        return new Layer(properties, children);
    }

    static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    static List<?> list(Map<?, ?> map, String key) {
        return list(map.get(key));
    }

    static String string(Map<?, ?> map, String key) {
        return map.get(key) instanceof String value ? value : "";
    }

    static double number(Map<?, ?> map, String key, double fallback) {
        return number(map.get(key), fallback);
    }

    static double number(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    static double number(List<?> list, int index, double fallback) {
        return (index >= 0 && index < list.size()) ? number(list.get(index), fallback) : fallback;
    }
}