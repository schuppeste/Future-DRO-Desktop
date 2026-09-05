package com.drodesktop.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;

public class SevenSegmentLabel extends JLabel {
    private static final Font SEGMENT_FONT = loadSegmentFont();
    // Caches the size the current font was fitted for, so rapid telemetry updates (many setText calls per
    // second) don't re-run the expensive font-size search unless the component was actually resized or the
    // new text no longer fits (e.g. an extra digit).
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
        return getFontMetrics(font).stringWidth(text) <= availableWidth;
    }

    private void resizeFontToFit() {
        String text = getText();
        int availableWidth = getWidth() - getInsets().left - getInsets().right - 12;
        int availableHeight = getHeight() - getInsets().top - getInsets().bottom - 12;

        if (text == null || text.isEmpty() || availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        fittedForWidth = getWidth();
        fittedForHeight = getHeight();

        Font baseFont = SEGMENT_FONT != null ? SEGMENT_FONT : new Font(Font.SANS_SERIF, Font.BOLD, 1);
        int fontSize = Math.max(1, availableHeight);
        while (fontSize > 1) {
            Font candidate = baseFont.deriveFont(Font.PLAIN, (float) fontSize);
            FontMetrics metrics = getFontMetrics(candidate);
            if (metrics.stringWidth(text) <= availableWidth && metrics.getHeight() <= availableHeight) {
                setFont(candidate);
                revalidate();
                repaint();
                return;
            }
            fontSize--;
        }

        setFont(baseFont.deriveFont(Font.PLAIN, 1f));
    }

    private static Font loadSegmentFont() {
        try {
            File fontFile = new File("src/seven-segment.ttf");
            if (fontFile.exists()) {
                return Font.createFont(Font.TRUETYPE_FONT, fontFile);
            }
        } catch (IOException | FontFormatException ex) {
            System.err.println("SevenSegment font not loaded: " + ex.getMessage());
        }
        return null;
    }
}
