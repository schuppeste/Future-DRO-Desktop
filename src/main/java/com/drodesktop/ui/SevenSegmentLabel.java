package com.drodesktop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Objects;

public class SevenSegmentLabel extends JLabel {
    private static final Font SEGMENT_FONT = loadSegmentFont();
    private static final int DISPLAY_PADDING = 24;
    private static final int GLYPH_VERTICAL_PADDING = 2;
    private static final double DISPLAY_HEIGHT_SCALE = 0.88;
    private static final double DISPLAY_WIDTH_SCALE = 0.82;
    private static final Color PLACEHOLDER_COLOR = new Color(82, 88, 94);
    private static final char[] GLYPH_CHARACTERS = "0123456789.-".toCharArray();
    private int fittedForWidth = -1;
    private int fittedForHeight = -1;
    private int fittedCharacterCapacity;
    private BufferedImage[] foregroundGlyphs;
    private BufferedImage[] placeholderGlyphs;
    private int[] glyphAdvances;
    private int glyphHeight;
    private GraphicsConfiguration glyphConfiguration;

    public SevenSegmentLabel() {
        super();
        setOpaque(false);
        setForeground(new Color(236, 247, 255));
        setHorizontalAlignment(SwingConstants.RIGHT);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                resizeFontToFit();
            }
        });
    }

    public void setDisplayScale(int scale) {
        resizeFontToFit();
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        invalidateGlyphCache();
    }

    @Override
    public void setForeground(Color foreground) {
        Color previousForeground = getForeground();
        super.setForeground(foreground);
        if (!Objects.equals(previousForeground, foreground)) {
            invalidateGlyphCache();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (isOpaque()) {
            graphics.setColor(getBackground());
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }

        String text = getText();
        Font font = getFont();
        if (text == null || text.isEmpty() || font == null) {
            return;
        }

        Graphics2D graphics2d = (Graphics2D) graphics.create();
        try {
            ensureGlyphCache(graphics2d, font);
            boolean negative = text.charAt(0) == '-';
            int valueStart = negative ? 1 : 0;
            int placeholderEnd = leadingPlaceholderEnd(text, valueStart);
            int textWidth = 0;
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                textWidth += glyphAdvance(character);
            }
            if (!negative) {
                textWidth += glyphAdvance('-');
            }
            int x = getWidth() - getInsets().right - 6 - textWidth;
            if (!negative) {
                x += glyphAdvance('-');
            }
            int y = (getHeight() - glyphHeight) / 2;

            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                int valueIndex = index - valueStart;
                boolean sign = negative && index == 0;
                boolean placeholder = index >= valueStart && valueIndex < placeholderEnd;
                BufferedImage glyph = sign || !placeholder
                    ? foregroundGlyph(character)
                    : placeholderGlyph(character);
                graphics2d.drawImage(glyph, x - 1, y, null);
                x += glyphAdvance(character);
            }
        } finally {
            graphics2d.dispose();
        }
    }

    @Override
    public void setText(String text) {
        String previousText = getText();
        if (Objects.equals(previousText, text)) {
            return;
        }
        super.setText(text);
        if (getWidth() != fittedForWidth || getHeight() != fittedForHeight
                || reservedCharacterCount(text) > fittedCharacterCapacity) {
            resizeFontToFit();
        }
        repaint();
    }

    private int reservedCharacterCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() + (text.startsWith("-") ? 0 : 1);
    }

    private int leadingPlaceholderEnd(String text, int valueStart) {
        int decimalSeparator = text.indexOf('.', valueStart);
        int integerEnd = decimalSeparator >= 0 ? decimalSeparator : text.length();
        int firstSignificantDigit = integerEnd - 1;

        for (int index = valueStart; index < integerEnd; index++) {
            if (text.charAt(index) != '0') {
                firstSignificantDigit = index;
                break;
            }
        }
        return firstSignificantDigit - valueStart;
    }

    private void ensureGlyphCache(Graphics2D graphics, Font font) {
        GraphicsConfiguration configuration = graphics.getDeviceConfiguration();
        if (foregroundGlyphs != null && configuration == glyphConfiguration) {
            return;
        }

        FontMetrics metrics = getFontMetrics(font);
        glyphHeight = metrics.getHeight() + 2 * GLYPH_VERTICAL_PADDING;
        glyphAdvances = new int[GLYPH_CHARACTERS.length];
        foregroundGlyphs = new BufferedImage[GLYPH_CHARACTERS.length];
        placeholderGlyphs = new BufferedImage[GLYPH_CHARACTERS.length];
        glyphConfiguration = configuration;

        for (int index = 0; index < GLYPH_CHARACTERS.length; index++) {
            char character = GLYPH_CHARACTERS[index];
            int advance = glyphAdvance(graphics, font, metrics, character);
            glyphAdvances[index] = advance;
            foregroundGlyphs[index] = createGlyphImage(graphics, configuration, character, advance, metrics, getForeground());
            placeholderGlyphs[index] = createGlyphImage(graphics, configuration, character, advance, metrics, PLACEHOLDER_COLOR);
        }
    }

    private int glyphAdvance(Graphics2D graphics, Font font, FontMetrics metrics, char character) {
        if (character != '.') {
            return Math.max(1, metrics.charWidth(character));
        }
        java.awt.geom.Rectangle2D bounds = font.createGlyphVector(graphics.getFontRenderContext(), ".").getVisualBounds();
        return Math.max(1, (int) Math.ceil(bounds.getWidth()) + 2);
    }

    private BufferedImage createGlyphImage(Graphics2D graphics, GraphicsConfiguration configuration, char character, int advance,
            FontMetrics metrics, Color color) {
        int drawX = 1;
        int glyphWidth = advance + 2;
        if (character == '.') {
            java.awt.geom.Rectangle2D bounds = getFont().createGlyphVector(graphics.getFontRenderContext(), ".")
                .getVisualBounds();
            drawX = (int) Math.ceil(Math.max(0, -bounds.getX())) + 1;
            glyphWidth = Math.max(glyphWidth, drawX + (int) Math.ceil(Math.max(0, bounds.getMaxX())) + 1);
        }
        BufferedImage image = configuration == null
            ? new BufferedImage(glyphWidth, glyphHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            : configuration.createCompatibleImage(glyphWidth, glyphHeight, Transparency.TRANSLUCENT);
        Graphics2D glyphGraphics = image.createGraphics();
        try {
            glyphGraphics.setFont(getFont());
            glyphGraphics.setColor(color);
            glyphGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            glyphGraphics.drawString(String.valueOf(character), drawX, GLYPH_VERTICAL_PADDING + metrics.getAscent());
        } finally {
            glyphGraphics.dispose();
        }
        return image;
    }

    private BufferedImage foregroundGlyph(char character) {
        return foregroundGlyphs[glyphIndex(character)];
    }

    private BufferedImage placeholderGlyph(char character) {
        return placeholderGlyphs[glyphIndex(character)];
    }

    private int glyphAdvance(char character) {
        return glyphAdvances[glyphIndex(character)];
    }

    private int glyphIndex(char character) {
        if (character >= '0' && character <= '9') {
            return character - '0';
        }
        return character == '.' ? 10 : 11;
    }

    private void invalidateGlyphCache() {
        foregroundGlyphs = null;
        placeholderGlyphs = null;
        glyphAdvances = null;
        glyphHeight = 0;
        glyphConfiguration = null;
    }

    private void resizeFontToFit() {
        String text = getText();
        int availableWidth = getWidth() - getInsets().left - getInsets().right - 12;
        int availableHeight = getHeight() - getInsets().top - getInsets().bottom - DISPLAY_PADDING;

        if (text == null || text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        fittedForWidth = getWidth();
        fittedForHeight = getHeight();
        fittedCharacterCapacity = reservedCharacterCount(text);

        Font baseFont = SEGMENT_FONT != null ? SEGMENT_FONT : new Font(Font.SANS_SERIF, Font.BOLD, 1);
        int minimumSize = 1;
        int maximumSize = Math.max(1, (int) (availableHeight * DISPLAY_HEIGHT_SCALE));
        int fittingSize = 1;
        while (minimumSize <= maximumSize) {
            int fontSize = (minimumSize + maximumSize) >>> 1;
            Font candidate = baseFont.deriveFont(Font.PLAIN, (float) fontSize)
                .deriveFont(AffineTransform.getScaleInstance(DISPLAY_WIDTH_SCALE, 1.0));
            FontMetrics metrics = getFontMetrics(candidate);
            if (reservedDisplayWidth(metrics, text) <= availableWidth
                    && metrics.getHeight() + 2 * GLYPH_VERTICAL_PADDING <= availableHeight) {
                fittingSize = fontSize;
                minimumSize = fontSize + 1;
            } else {
                maximumSize = fontSize - 1;
            }
        }

        Font fittedFont = baseFont.deriveFont(Font.PLAIN, (float) fittingSize)
            .deriveFont(AffineTransform.getScaleInstance(DISPLAY_WIDTH_SCALE, 1.0));
        if (!fittedFont.equals(getFont())) {
            setFont(fittedFont);
        }
        repaint();
    }

    private int reservedDisplayWidth(FontMetrics metrics, String text) {
        return metrics.stringWidth(text) + (text.startsWith("-") ? 0 : metrics.stringWidth("-"));
    }

    private static Font loadSegmentFont() {
        try (var fontStream = SevenSegmentLabel.class.getResourceAsStream("/fonts/DSEG7Classic-Regular.ttf")) {
            if (fontStream != null) {
                return Font.createFont(Font.TRUETYPE_FONT, fontStream);
            }
        } catch (IOException | FontFormatException ex) {
            System.err.println("SevenSegment font not loaded: " + ex.getMessage());
        }
        return null;
    }
}
