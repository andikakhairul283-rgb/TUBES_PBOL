package com.example.tubespbolkel7.ui.login;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static com.example.tubespbolkel7.ui.login.LoginLayout.Layer;
import static com.example.tubespbolkel7.ui.login.LoginLayout.number;

final class LoginArtwork {
    private static final double BOTTOM_ANCHOR_THRESHOLD = 0.8;

    private LoginArtwork() { }

    record Anchor(Layer layer, double x, double y) {
        Anchor find(Predicate<Layer> matches) {
            Objects.requireNonNull(matches, "Predicate pencarian layer tidak boleh null");
            Anchor result = findDescendant(matches);
            if (result == null) {
                throw new IllegalArgumentException("Komponen login tidak ditemukan di " + layer.name());
            }
            return result;
        }

        private Anchor findDescendant(Predicate<Layer> matches) {
            if (matches.test(layer)) {
                return this;
            }
            for (Layer child : layer.children()) {
                Anchor candidate = new Anchor(child, x + offsetX(child), y + offsetY(child));
                Anchor result = candidate.findDescendant(matches);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    record Fit(Layer screen, double formX, double formY) { }

    static Fit fit(Layer screen, double width, double height) {
        Layer header = section(screen, "Header - Desktop");
        Layer body = section(screen, "Main Split Body");
        Layer footer = section(screen, "Window Footer");
        Layer left = section(body, "Section - LEFT HALF");
        Layer right = section(body, "Section - RIGHT HALF");

        if (right.children().isEmpty()) {
            throw new IllegalStateException("Layer card pada panel kanan tidak ditemukan.");
        }
        Layer card = right.children().getFirst();

        double halfWidth = width / 2.0;
        double bodyHeight = Math.max(0, height - header.height() - footer.height());
        double bodyHeightDelta = bodyHeight - body.height();
        double leftWidthDelta = halfWidth - left.width();
        double cardX = (halfWidth - card.width()) / 2.0;
        double cardY = (bodyHeight - card.height()) / 2.0;

        List<Layer> leftContents = left.children().stream()
                .map(layer -> adjustLeftLayer(layer, left.height(), halfWidth, leftWidthDelta, bodyHeight, bodyHeightDelta))
                .toList();

        Layer leftPanel = children(size(left, halfWidth, bodyHeight), leftContents);
        Layer rightPanel = position(
                children(size(right, halfWidth, bodyHeight), List.of(position(card, cardX, cardY))),
                halfWidth, 0
        );
        Layer split = position(
                children(size(body, width, bodyHeight), List.of(leftPanel, rightPanel)),
                0, header.height()
        );

        double widthDelta = width - screen.width();
        Layer topBar = wideBar(header, width, widthDelta);
        Layer bottomBar = position(wideBar(footer, width, widthDelta), 0, height - footer.height());
        Layer viewport = children(size(screen, width, height), List.of(topBar, split, bottomBar));

        double formX = halfWidth + cardX - offsetX(right) - offsetX(card);
        double formY = header.height() + cardY - offsetY(body) - offsetY(card);
        return new Fit(viewport, formX, formY);
    }

    private static Layer adjustLeftLayer(Layer layer, double originalLeftHeight, double halfWidth,
                                         double leftWidthDelta, double bodyHeight, double bodyHeightDelta) {
        String name = layer.name();
        if (name.startsWith("Subtle luxury gold line")) {
            return position(size(layer, layer.width(), bodyHeight), halfWidth - layer.width(), 0);
        }
        if (name.equals("Overlay+Blur")) {
            return position(layer, offsetX(layer) + leftWidthDelta, offsetY(layer) + bodyHeightDelta);
        }
        if (name.startsWith("Left Bottom Legal") || offsetY(layer) > originalLeftHeight * BOTTOM_ANCHOR_THRESHOLD) {
            return position(layer, offsetX(layer), offsetY(layer) + bodyHeightDelta);
        }
        return layer;
    }

    private static Layer wideBar(Layer bar, double width, double delta) {
        double midpoint = bar.width() / 2.0;
        List<Layer> adjusted = bar.children().stream()
                .map(layer -> offsetX(layer) > midpoint
                        ? position(layer, offsetX(layer) + delta, offsetY(layer))
                        : layer)
                .toList();
        return children(size(bar, width, bar.height()), adjusted);
    }

    static Layer withoutFooterResolution(Layer screen) {
        return children(screen, screen.children().stream().map(layer -> {
            if (!layer.name().startsWith("Window Footer")) {
                return layer;
            }
            return children(layer, layer.children().stream().map(group -> {
                boolean hasResolutionLabel = group.children().stream()
                        .anyMatch(item -> item.characters().startsWith("Tampilan Resolusi Desktop"));
                if (!hasResolutionLabel) {
                    return group;
                }
                List<Layer> filtered = group.children().stream()
                        .filter(item -> !item.characters().startsWith("Tampilan Resolusi Desktop")
                                && !item.characters().equals("•"))
                        .toList();
                return children(group, filtered);
            }).toList());
        }).toList());
    }

    static Layer withLiveForm(Layer layer, Set<String> hidden, Set<String> roleCards, String submitId) {
        if (layer.id().equals(submitId)) {
            return children(layer, List.of());
        }
        List<Layer> visibleChildren = layer.children().stream()
                .filter(child -> !hidden.contains(child.id()))
                .map(child -> withLiveForm(child, hidden, roleCards, submitId))
                .toList();
        Layer result = children(layer, visibleChildren);

        if (roleCards.contains(layer.id())) {
            Map<Object, Object> paints = new HashMap<>(result.properties());
            paints.remove("fills");
            paints.remove("strokes");
            return new Layer(Map.copyOf(paints), result.children());
        }
        return result;
    }

    private static Layer section(Layer parent, String prefix) {
        return parent.children().stream()
                .filter(child -> child.name().startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Section tidak ditemukan: " + prefix));
    }

    private static Layer children(Layer layer, List<Layer> children) {
        return new Layer(layer.properties(), children);
    }

    private static Layer size(Layer layer, double width, double height) {
        if (Double.compare(width, layer.width()) == 0 && Double.compare(height, layer.height()) == 0) {
            return layer;
        }
        Map<Object, Object> properties = new HashMap<>(layer.properties());
        properties.put("size", List.of(width, height));
        properties.remove("fillPaths");
        properties.remove("strokePaths");
        return new Layer(Map.copyOf(properties), layer.children());
    }

    private static Layer position(Layer layer, double x, double y) {
        Map<Object, Object> properties = new HashMap<>(layer.properties());
        properties.put("matrix", List.of(1, 0, x, 0, 1, y));
        return new Layer(Map.copyOf(properties), layer.children());
    }

    private static double offsetX(Layer layer) {
        return number(layer.matrix(), 2, 0);
    }

    private static double offsetY(Layer layer) {
        return number(layer.matrix(), 5, 0);
    }
}