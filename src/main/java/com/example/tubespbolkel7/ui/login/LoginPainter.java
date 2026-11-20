package com.example.tubespbolkel7.ui.login;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.InnerShadow;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.FillRule;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Affine;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.tubespbolkel7.ui.login.LoginLayout.*;

final class LoginPainter {
    private final Model model;
    private final Map<String, Image> images = new HashMap<>();
    private final Map<String, Color> colors = new HashMap<>();

    LoginPainter(Model model) {
        this.model = model;
    }

    void draw(GraphicsContext graphics, Layer screen) {
        graphics.clearRect(0, 0, screen.width(), screen.height());
        drawLayer(graphics, screen);
    }

    private void drawLayer(GraphicsContext graphics, Layer layer) {
        Map<?, ?> properties = layer.properties();
        double opacity = number(properties, "opacity", 1);
        if (opacity <= 0) {
            return;
        }
        List<?> matrix = layer.matrix();
        graphics.save();
        graphics.transform(number(matrix, 0, 1), number(matrix, 3, 0), number(matrix, 1, 0),
                number(matrix, 4, 1), number(matrix, 2, 0), number(matrix, 5, 0));
        graphics.setGlobalAlpha(graphics.getGlobalAlpha() * opacity);

        if (layer.type().equals("TEXT")) {
            drawText(graphics, layer);
        } else {
            drawBackground(graphics, layer);
        }

        if (Boolean.TRUE.equals(properties.get("clip")) && !layer.children().isEmpty()) {
            clipOutline(graphics, layer, list(properties, "fillPaths"));
            graphics.clip();
        }
        for (Layer child : layer.children()) {
            drawLayer(graphics, child);
        }
        graphics.restore();
    }

    private void drawBackground(GraphicsContext graphics, Layer layer) {
        Map<?, ?> properties = layer.properties();
        List<?> fillPaths = list(properties, "fillPaths");
        List<?> fills = list(properties, "fills");
        List<?> effects = list(properties, "effects");
        drawBackdrop(graphics, layer, effects);
        boolean first = true;
        for (Object item : fills) {
            Map<?, ?> fill = map(item);
            graphics.save();
            if (first) {
                applyEffect(graphics, effects);
                first = false;
            }
            if (string(fill, "type").equals("IMAGE")) {
                drawImage(graphics, layer, fillPaths, fill);
            } else {
                Paint paint = resolvePaint(fill, layer.width(), layer.height());
                if (paint != null) {
                    graphics.setFill(paint);
                    fillShape(graphics, layer, fillPaths);
                }
            }
            graphics.restore();
        }

        List<?> strokePaths = list(properties, "strokePaths");
        for (Object item : list(properties, "strokes")) {
            Paint paint = resolvePaint(map(item), layer.width(), layer.height());
            if (paint == null) {
                continue;
            }
            if (strokePaths.isEmpty()) {
                outline(graphics, layer);
                graphics.setStroke(paint);
                graphics.setLineWidth(number(properties, "strokeWidth", 1));
                graphics.stroke();
            } else {
                graphics.setFill(paint);
                fillShape(graphics, layer, strokePaths);
            }
        }
    }

    private void fillShape(GraphicsContext graphics, Layer layer, List<?> paths) {
        if (paths.isEmpty()) {
            if (!layer.type().equals("VECTOR") && !layer.type().equals("BRUSH")) {
                outline(graphics, layer);
                graphics.fill();
            }
            return;
        }
        for (Object item : paths) {
            List<?> path = list(item);
            graphics.beginPath();
            graphics.setFillRule(Boolean.TRUE.equals(path.get(1)) ? FillRule.EVEN_ODD : FillRule.NON_ZERO);
            appendPath(graphics, path);
            graphics.fill();
        }
    }

    private void appendPath(GraphicsContext graphics, List<?> reference) {
        String path = model.path((int) number(reference, 0, -1));
        if (path != null && !path.isEmpty()) {
            graphics.appendSVGPath(path);
        }
    }

    private void clipOutline(GraphicsContext graphics, Layer layer, List<?> paths) {
        if (paths.isEmpty()) {
            outline(graphics, layer);
            return;
        }
        graphics.beginPath();
        graphics.setFillRule(Boolean.TRUE.equals(list(paths.getFirst()).get(1)) ? FillRule.EVEN_ODD : FillRule.NON_ZERO);
        for (Object item : paths) {
            appendPath(graphics, list(item));
        }
    }

    private void outline(GraphicsContext graphics, Layer layer) {
        double width = layer.width();
        double height = layer.height();
        List<?> corners = list(layer.properties(), "radius");
        graphics.setFillRule(FillRule.NON_ZERO);
        graphics.beginPath();
        if (corners.isEmpty()) {
            graphics.rect(0, 0, width, height);
            return;
        }
        double max = Math.min(width, height) / 2;
        double tl = Math.min(max, number(corners, 0, 0));
        double tr = Math.min(max, number(corners, 1, 0));
        double br = Math.min(max, number(corners, 2, 0));
        double bl = Math.min(max, number(corners, 3, 0));
        graphics.moveTo(tl, 0);
        graphics.lineTo(width - tr, 0);
        graphics.quadraticCurveTo(width, 0, width, tr);
        graphics.lineTo(width, height - br);
        graphics.quadraticCurveTo(width, height, width - br, height);
        graphics.lineTo(bl, height);
        graphics.quadraticCurveTo(0, height, 0, height - bl);
        graphics.lineTo(0, tl);
        graphics.quadraticCurveTo(0, 0, tl, 0);
        graphics.closePath();
    }

    private void drawText(GraphicsContext graphics, Layer layer) {
        Map<?, ?> text = layer.text();
        List<?> fills = list(layer.properties(), "fills");
        if (fills.isEmpty()) {
            return;
        }
        Paint defaultPaint = resolvePaint(map(fills.getLast()), layer.width(), layer.height());
        if (defaultPaint == null) {
            return;
        }
        List<?> glyphs = list(text, "glyphs");
        if (glyphs.isEmpty()) {
            drawTextFallback(graphics, layer, defaultPaint);
            return;
        }
        Paint currentPaint = defaultPaint;
        graphics.setFillRule(FillRule.NON_ZERO);
        graphics.beginPath();
        for (Object item : glyphs) {
            List<?> glyph = list(item);
            Paint glyphPaint = glyph.size() > 6 && glyph.get(6) instanceof String encoded
                    ? color(encoded, 1) : defaultPaint;
            if (!glyphPaint.equals(currentPaint)) {
                graphics.setFill(currentPaint);
                graphics.fill();
                graphics.beginPath();
                currentPaint = glyphPaint;
            }
            double size = number(glyph, 3, 0);
            graphics.save();
            graphics.translate(number(glyph, 1, 0), number(glyph, 2, 0));
            double rotation = number(glyph, 4, 0);
            if (rotation != 0) {
                graphics.rotate(rotation);
            }
            graphics.scale(size, -size);
            appendPath(graphics, glyph);
            graphics.restore();
        }
        graphics.setFill(currentPaint);
        graphics.fill();
    }

    private void drawTextFallback(GraphicsContext graphics, Layer layer, Paint paint) {
        Map<?, ?> text = layer.text();
        double size = number(text, "size", 14);
        String family = string(text, "family");
        graphics.setFont(Font.font(family, fontWeight(string(text, "style")), size));
        graphics.setFill(paint);
        graphics.setTextBaseline(VPos.BASELINE);
        for (Object item : list(text, "baselines")) {
            List<?> line = list(item);
            int start = (int) number(line, 2, 0);
            int end = Math.min(layer.characters().length(), (int) number(line, 3, 0));
            if (start < end) {
                graphics.fillText(layer.characters().substring(start, end), number(line, 0, 0), number(line, 1, size));
            }
        }
    }

    static FontWeight fontWeight(String style) {
        return switch (style.replace(" ", "")) {
            case "Black" -> FontWeight.BLACK;
            case "Bold" -> FontWeight.BOLD;
            case "SemiBold" -> FontWeight.SEMI_BOLD;
            case "Medium" -> FontWeight.MEDIUM;
            default -> FontWeight.NORMAL;
        };
    }

    private Paint resolvePaint(Map<?, ?> paint, double width, double height) {
        double opacity = number(paint, "opacity", 1);
        if (string(paint, "type").equals("SOLID")) {
            return color(string(paint, "color"), opacity);
        }
        List<?> axis = list(paint, "line");
        if (axis.size() == 4 && width > 0 && height > 0) {
            List<Stop> stops = new ArrayList<>();
            for (Object item : list(paint, "stops")) {
                List<?> stop = list(item);
                stops.add(new Stop(number(stop, 0, 0), color((String) stop.get(1), opacity)));
            }
            List<?> transform = list(paint, "matrix");
            double gx = number(transform, 0, 0) / width;
            double gy = number(transform, 1, 0) / height;
            double offset = number(transform, 2, 0);
            double lengthSquared = gx * gx + gy * gy;
            if (lengthSquared > 0) {
                return new LinearGradient(-offset * gx / lengthSquared, -offset * gy / lengthSquared,
                        (1 - offset) * gx / lengthSquared, (1 - offset) * gy / lengthSquared,
                        false, CycleMethod.NO_CYCLE, stops);
            }
            return new LinearGradient(number(axis, 0, 0) * width, number(axis, 1, 0) * height,
                    number(axis, 2, 1) * width, number(axis, 3, 0) * height, false, CycleMethod.NO_CYCLE, stops);
        }
        return null;
    }

    private Color color(String value, double opacity) {
        if (value.isEmpty()) {
            return Color.TRANSPARENT;
        }
        Color base = colors.computeIfAbsent(value, Color::web);
        return opacity == 1 ? base : new Color(base.getRed(), base.getGreen(), base.getBlue(), base.getOpacity() * opacity);
    }

    private void applyEffect(GraphicsContext graphics, List<?> effects) {
        for (Object item : effects) {
            Map<?, ?> effect = map(item);
            double radius = Math.min(127, number(effect, "radius", 0) / 2);
            double spread = Math.clamp(number(effect, "spread", 0), 0, 1);
            double x = number(effect, "x", 0);
            double y = number(effect, "y", 0);
            Color color = color(string(effect, "color"), 1);
            switch (string(effect, "type")) {
                case "DROP_SHADOW" -> graphics.setEffect(new DropShadow(BlurType.GAUSSIAN, color, radius, spread, x, y));
                case "INNER_SHADOW" -> graphics.setEffect(new InnerShadow(BlurType.GAUSSIAN, color, radius, spread, x, y));
                case "FOREGROUND_BLUR" -> graphics.setEffect(new GaussianBlur(Math.min(63, radius)));
                default -> {
                    continue;
                }
            }
            return;
        }
    }

    private void drawBackdrop(GraphicsContext graphics, Layer layer, List<?> effects) {
        for (Object item : effects) {
            Map<?, ?> effect = map(item);
            if (!string(effect, "type").equals("BACKGROUND_BLUR")) {
                continue;
            }
            double radius = Math.min(63, number(effect, "radius", 0) / 2);
            var canvas = graphics.getCanvas();
            Bounds bounds = graphics.getTransform().transform(new BoundingBox(0, 0, layer.width(), layer.height()));
            double x = Math.max(0, Math.floor(bounds.getMinX() - radius));
            double y = Math.max(0, Math.floor(bounds.getMinY() - radius));
            double right = Math.min(canvas.getWidth(), Math.ceil(bounds.getMaxX() + radius));
            double bottom = Math.min(canvas.getHeight(), Math.ceil(bounds.getMaxY() + radius));
            if (right <= x || bottom <= y) {
                return;
            }
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            parameters.setViewport(new Rectangle2D(x, y, right - x, bottom - y));
            WritableImage backdrop = canvas.snapshot(parameters, null);
            graphics.save();
            clipOutline(graphics, layer, list(layer.properties(), "fillPaths"));
            graphics.clip();
            graphics.setTransform(new Affine());
            graphics.setGlobalAlpha(1);
            graphics.setEffect(new GaussianBlur(radius));
            graphics.drawImage(backdrop, x, y);
            graphics.restore();
            return;
        }
    }

    private void drawImage(GraphicsContext graphics, Layer layer, List<?> paths, Map<?, ?> paint) {
        String hash = string(paint, "image");
        Image image = images.computeIfAbsent(hash, this::loadImage);
        if (image == null || image.isError() || layer.width() <= 0 || layer.height() <= 0) {
            return;
        }
        clipOutline(graphics, layer, paths);
        graphics.clip();
        graphics.setGlobalAlpha(graphics.getGlobalAlpha() * number(paint, "opacity", 1));
        double width = layer.width();
        double height = layer.height();
        List<?> matrix = list(paint, "matrix");
        String mode = string(paint, "mode");
        if (!matrix.isEmpty() && (mode.equals("STRETCH") || mode.equals("CROP"))) {
            double a = number(matrix, 0, 1);
            double b = number(matrix, 1, 0);
            double tx = number(matrix, 2, 0);
            double c = number(matrix, 3, 0);
            double d = number(matrix, 4, 1);
            double ty = number(matrix, 5, 0);
            double determinant = a * d - b * c;
            if (Math.abs(determinant) > 0.00001) {
                graphics.transform(width * d / determinant, -height * c / determinant,
                        -width * b / determinant, height * a / determinant,
                        width * (b * ty - d * tx) / determinant, height * (c * tx - a * ty) / determinant);
                graphics.drawImage(image, 0, 0, 1, 1);
                return;
            }
        }
        double ratio = mode.equals("FIT") ? Math.min(width / image.getWidth(), height / image.getHeight())
                : Math.max(width / image.getWidth(), height / image.getHeight());
        double imageWidth = image.getWidth() * ratio;
        double imageHeight = image.getHeight() * ratio;
        graphics.drawImage(image, (width - imageWidth) / 2, (height - imageHeight) / 2, imageWidth, imageHeight);
    }

    private Image loadImage(String filename) {
        URL resource = LoginPainter.class.getResource(filename);
        return resource == null ? null : new Image(resource.toExternalForm());
    }
}
