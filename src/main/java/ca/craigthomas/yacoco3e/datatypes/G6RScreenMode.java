/*
 * Copyright (C) 2018 Craig Thomas
 * This project uses an MIT style license - see LICENSE for details.
 */
package ca.craigthomas.yacoco3e.datatypes;

import java.awt.*;

public class G6RScreenMode extends ScreenMode
{
    /* Screen size for the mode */
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 240;

    /* Block definitions */
    private static final int BLOCK_WIDTH = 2;
    private static final int BLOCK_HEIGHT = 1;

    /* Color definitions for graphics G3R mode */
    private final Color colors[][] = {
        {
            // Color Mode 0
            new Color(0, 0, 0, 255),        /* Black */
            new Color(40, 224, 40, 255),    /* Green */
            new Color(240, 136, 40, 255),   /* Orange Artifact */
            new Color(32, 32, 216, 255),    /* Blue Artifact */
        }, {
            // Color Mode 1
            new Color(0, 0, 0, 255),        /* Black */
            new Color(180, 60, 30, 255),    /* Black to Orange */
            new Color(255, 128, 0, 255),    /* Orange */
            new Color(255, 240, 200, 255),  /* Orange to White */
            new Color(0, 50, 120, 255),     /* Black to Blue */
            new Color(0, 128, 255, 255),    /* Blue Artifact */
            new Color(70, 200, 255, 255),   /* Blue to White */
            new Color(240, 240, 240, 255),  /* White */
        }, {
            // Color Mode 0 - no artifacts
            new Color(0, 0, 0, 255),        /* Black */
            new Color(40, 224, 40, 255),    /* Green */
        }, {
            // Color Mode 1 - no artifacts
            new Color(0, 0, 0, 255),        /* Black */
            new Color(240, 240, 240, 255),  /* White */
        }
    };

    private final Color background = new Color(240, 240, 240, 255);

    // The color mode to apply
    private int colorMode;
    // Whether to produce artifact colors
    private boolean artifacts;
    // Records the last color phase shift
    private int lastPhase;

    public G6RScreenMode(int scale, int colorMode, boolean artifacts) {
        this.scale = scale;
        this.width = SCREEN_WIDTH;
        this.height = SCREEN_HEIGHT;
        this.colorMode = artifacts ? colorMode : colorMode + 2;
        this.artifacts = artifacts;
        lastPhase = 7;
        createBackBuffer();
    }

    @Override
    public void refreshScreen() {
        Graphics2D graphics = backBuffer.createGraphics();
        graphics.setColor(background);
        graphics.fillRect(0, 0, width * scale, height * scale);

        int memoryPointer = memoryOffset;

        for (int y = 0; y < 192; y++) {
            lastPhase = 3;
            for (int x = 0; x < 32; x++) {
                UnsignedByte value = io.readPhysicalByte(memoryPointer);
                drawCharacter(value, x, y);
                memoryPointer++;
            }
        }

        graphics.dispose();
    }

    private void drawBlock(int col, int row, int color, int blockWidth) {
        for (int x = col; x < col + blockWidth; x++) {
            for (int y = row; y < row + BLOCK_HEIGHT; y++) {
                drawPixel(x, y, colors[colorMode][color]);
            }
        }
    }

    private void drawPixelTransition(int x, int y, int nextColor) {
            switch (nextColor) {
                case 0:
                    switch (lastPhase) {
                        case 0:
                            drawBlock(x, y, 0, 2);
                            break;

                        case 1:
                            drawBlock(x, y, 1, 1);
                            drawBlock(x + 1, y, 0, 1);
                            break;

                        case 2:
                            drawBlock(x, y, 4, 1);
                            drawBlock(x + 1, y, 0, 1);
                            break;

                        case 3:
                            drawBlock(x, y, 4, 1);
                            drawBlock(x + 1, y, 0, 1);
                            break;
                    }
                    lastPhase = 0;
                    break;

                case 1:
                    switch (lastPhase) {
                        case 0:
                            drawBlock(x, y, 1, 1);
                            drawBlock(x + 1, y, 2, 1);
                            break;

                        case 1:
                            drawBlock(x, y, 2, 2);
                            break;

                        case 2:
                            drawBlock(x, y, 4, 1);
                            drawBlock(x + 1, y, 1, 1);
                            break;

                        case 3:
                            drawBlock(x, y, 7, 1);
                            drawBlock(x + 1, y, 3, 1);
                            break;
                    }
                    lastPhase = 1;
                    break;

                case 2:
                    switch (lastPhase) {
                        case 0:
                            drawBlock(x, y, 0, 1);
                            drawBlock(x + 1, y, 4, 1);
                            break;

                        case 1:
                            drawBlock(x, y, 1, 1);
                            drawBlock(x + 1, y, 4, 1);
                            break;

                        case 2:
                            drawBlock(x, y, 5, 1);
                            break;

                        case 3:
                            drawBlock(x, y, 6, 1);
                            drawBlock(x + 1, y, 5, 1);
                            break;
                    }
                    lastPhase = 2;
                    break;

                case 3:
                    switch (lastPhase) {
                        case 0:
                            drawBlock(x, y, 1, 1);
                            drawBlock(x + 1, y, 7, 1);
                            break;

                        case 1:
                            drawBlock(x, y, 2, 1);
                            drawBlock(x + 1, y, 3, 1);
                            break;

                        case 2:
                            drawBlock(x, y, 5, 1);
                            drawBlock(x + 1, y, 6, 1);
                            break;

                        case 3:
                            drawBlock(x, y, 7, 2);
                            break;
                    }
                    lastPhase = 3;
                    break;
            }

    }

    private void drawCharacter(UnsignedByte value, int col, int row) {
        /* Translated position in pixels */
        int x = 32 + (col * 8);
        int y = 24 + (row * BLOCK_HEIGHT);

        if (artifacts) {
            /* Pixel 1 */
            drawPixelTransition(x, y, (value.getShort() & 0xC0) >> 6);

            /* Pixel 2 */
            drawPixelTransition(x + 2, y, (value.getShort() & 0x30) >> 4);

            /* Pixel 3 */
            drawPixelTransition(x + 4, y, (value.getShort() & 0x0C) >> 2);

            /* Pixel 4 */
            drawPixelTransition(x + 6, y, (value.getShort() & 0x03));
        } else {
            /* Pixel 1 */
            int color = (value.getShort() & 0x80) >> 7;
            drawBlock(x, y, color, 1);

            /* Pixel 2 */
            color = (value.getShort() & 0x40) >> 6;
            drawBlock(x + 1, y, color, 1);

            /* Pixel 3 */
            color = (value.getShort() & 0x20) >> 5;
            drawBlock(x + 2, y, color, 1);

            /* Pixel 4 */
            color = (value.getShort() & 0x10) >> 4;
            drawBlock(x + 3, y, color, 1);

            /* Pixel 5 */
            color = (value.getShort() & 0x08) >> 3;
            drawBlock(x + 4, y, color, 1);

            /* Pixel 6 */
            color = (value.getShort() & 0x04) >> 2;
            drawBlock(x + 5, y, color, 1);

            /* Pixel 7 */
            color = (value.getShort() & 0x02) >> 1;
            drawBlock(x + 6, y, color, 1);

            /* Pixel 8 */
            color = (value.getShort() & 0x01);
            drawBlock(x + 7, y, color, 1);
        }
    }
}
