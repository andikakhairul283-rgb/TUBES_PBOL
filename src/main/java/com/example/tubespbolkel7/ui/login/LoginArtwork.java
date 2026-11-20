package com.example.tubespbolkel7.ui.login;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.example.tubespbolkel7.ui.login.LoginLayout.Layer;
import static com.example.tubespbolkel7.ui.login.LoginLayout.number;

final class LoginArtwork {
    record Anchor(Layer layer, double x, double y) {
        Anchor find(Predicate<Layer> matches) {
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
                Anchor result = new Anchor(child, x + offsetX(child), y + offsetY(child)).findDescendant(matches);
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
        Layer card = right.children().getFirst();

        double half = width / 2;
        double bodyHeight = height - header.height() - footer.height();
        double bodyHeightDelta = bodyHeight - body.height();
        double leftWidthDelta = half - left.width();
        double cardX = (half - card.width()) / 2;
        double cardY = (bodyHeight - card.height()) / 2;

        List<Layer> leftContents = left.children().stream().map(layer -> {
            if (layer.name().startsWith("Subtle luxury gold line")) {
                return position(size(layer, layer.width(), bodyHeight), half - layer.width(), 0);
            }
            if (layer.name().equals("Overlay+Blur")) {
                return position(layer, offsetX(layer) + leftWidthDelta, offsetY(layer) + bodyHeightDelta);
            }
            if (layer.name().startsWith("Left Bottom Legal") || offsetY(layer) > left.height() * 0.8) {
                return position(layer, offsetX(layer), offsetY(layer) + bodyHeightDelta);
            }
            return layer;
        }).toList();
        Layer leftPanel = children(size(left, half, bodyHeight), leftContents);
        Layer rightPanel = position(children(size(right, half, bodyHeight),
                List.of(position(card, cardX, cardY))), half, 0);
        Layer split = position(children(size(body, width, bodyHeight), List.of(leftPanel, rightPanel)), 0, header.height());
        double widthDelta = width - screen.width();
        Layer bottomBar = position(wideBar(footer, width, widthDelta), 0, height - footer.height());
        Layer viewport = children(size(screen, width, height), List.of(wideBar(header, width, widthDelta), split, bottomBar));
        return new Fit(viewport, half + cardX - offsetX(right) - offsetX(card),
                header.height() + cardY - offsetY(body) - offsetY(card));
    }

    private static Layer wideBar(Layer bar, double width, double delta) {
        return children(size(bar, width, bar.height()), bar.children().stream()
                .map(layer -> offsetX(layer) > bar.width() / 2
                        ? position(layer, offsetX(layer) + delta, offsetY(layer)) : layer)
                .toList());
    }

    static Layer withoutFooterResolution(Layer screen) {
        return children(screen, screen.children().stream().map(layer -> {
            if (!layer.name().startsWith("Window Footer")) {
                return layer;
            }
            return children(layer, layer.children().stream().map(group -> {
                if (group.children().stream().noneMatch(item -> item.characters().startsWith("Tampilan Resolusi Desktop"))) {
                    return group;
                }
                return children(group, group.children().stream()
                        .filter(item -> !item.characters().startsWith("Tampilan Resolusi Desktop") && !item.characters().equals("•"))
                        .toList());
            }).toList());
        }).toList());
    }

    static Layer withLiveForm(Layer layer, Set<String> hidden, Set<String> roleCards, String submitId) {
        if (layer.id().equals(submitId)) {
            return children(layer, List.of());
        }
        Layer result = children(layer, layer.children().stream().filter(child -> !hidden.contains(child.id()))
                .map(child -> withLiveForm(child, hidden, roleCards, submitId)).toList());
        if (roleCards.contains(layer.id())) {
            Map<Object, Object> paints = new HashMap<>(result.properties());
            paints.remove("fills");
            paints.remove("strokes");
            return new Layer(Map.copyOf(paints), result.children());
        }
        return result;
    }

    private static Layer section(Layer parent, String prefix) {
        return parent.children().stream().filter(child -> child.name().startsWith(prefix)).findFirst().orElseThrow();
    }

    private static Layer children(Layer layer, List<Layer> children) {
        return new Layer(layer.properties(), children);
    }

    private static Layer size(Layer layer, double width, double height) {
        if (width == layer.width() && height == layer.height()) {
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

    private static double offsetX(Layer layer) { return number(layer.matrix(), 2, 0); }
    private static double offsetY(Layer layer) { return number(layer.matrix(), 5, 0); }
}
