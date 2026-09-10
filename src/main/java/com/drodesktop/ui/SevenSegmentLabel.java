package com.drodesktop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.awt.geom.AffineTransform;

public class SevenSegmentLabel extends JLabel {
    private static final Font SEGMENT_FONT = loadSegmentFont();
    private static final int DISPLAY_PADDING = 24;
    private static final double DISPLAY_HEIGHT_SCALE = 0.88;
    private static final double DISPLAY_WIDTH_SCALE = 0.82;
    private static final Color PLACEHOLDER_COLOR = new Color(82, 88, 94);
    private int fittedForWidth = -1;
    private int fittedForHeight = -1;

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
            graphics2d.setFont(font);
            graphics2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            FontMetrics metrics = graphics2d.getFontMetrics();
            boolean negative = text.startsWith("-");
            String valueText = negative ? text.substring(1) : text;
            int placeholderEnd = leadingPlaceholderEnd(valueText);
            int signPosition = negative ? Math.max(0, placeholderEnd - 1) : -1;
            int textWidth = metrics.stringWidth(text)
                - (negative ? metrics.stringWidth(String.valueOf(valueText.charAt(signPosition))) : 0);
            int x = getWidth() - getInsets().right - 6 - textWidth;
            int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();

            for (int index = 0; index < valueText.length(); index++) {
                if (index == signPosition) {
                    graphics2d.setColor(getForeground());
                    graphics2d.drawString("-", x, y);
                    x += metrics.stringWidth("-");
                    continue;
                }
                graphics2d.setColor(index < placeholderEnd ? PLACEHOLDER_COLOR : getForeground());
                String character = String.valueOf(valueText.charAt(index));
                graphics2d.drawString(character, x, y);
                x += metrics.stringWidth(character);
            }
        } finally {
            graphics2d.dispose();
        }
    }

    @Override
    public void setText(String text) {
        super.setText(text);
        if (getWidth() != fittedForWidth || getHeight() != fittedForHeight || !currentFontFits(text)) {
            resizeFontToFit();
        }
    }

    private boolean currentFontFits(String text) {
        Font font = getFont();
        if (font == null || text == null || text.isEmpty()) {
            return true;
        }
        int availableWidth = getWidth() - getInsets().left - getInsets().right - 12;
        FontMetrics metrics = getFontMetrics(font);
        return reservedDisplayWidth(metrics, text) <= availableWidth;
    }

    private int leadingPlaceholderEnd(String text) {
        int firstDigit = text.startsWith("-") || text.startsWith("+") ? 1 : 0;
        int decimalSeparator = text.indexOf('.');
        int integerEnd = decimalSeparator >= 0 ? decimalSeparator : text.length();
        int firstSignificantDigit = integerEnd - 1;

        for (int index = firstDigit; index < integerEnd; index++) {
            if (text.charAt(index) != '0') {
                firstSignificantDigit = index;
                break;
            }
        }
        return firstSignificantDigit;
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

        Font baseFont = SEGMENT_FONT != null ? SEGMENT_FONT : new Font(Font.SANS_SERIF, Font.BOLD, 1);
        int fontSize = Math.max(1, (int) (availableHeight * DISPLAY_HEIGHT_SCALE));
        while (fontSize > 1) {
            Font candidate = baseFont.deriveFont(Font.PLAIN, (float) fontSize)
                .deriveFont(AffineTransform.getScaleInstance(DISPLAY_WIDTH_SCALE, 1.0));
            FontMetrics metrics = getFontMetrics(candidate);
            if (reservedDisplayWidth(metrics, text) <= availableWidth && metrics.getHeight() <= availableHeight) {
                setFont(candidate);
                revalidate();
                repaint();
                return;
            }
            fontSize--;
        }

        setFont(baseFont.deriveFont(Font.PLAIN, 1f)
            .deriveFont(AffineTransform.getScaleInstance(DISPLAY_WIDTH_SCALE, 1.0)));
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
