package dev.diorama.waypoint;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Vanilla's pieces of GUI art as 1.20.1 draws them. That version has no GUI sprites, so its text
 * fields, lists and scrollbars are plain fills; these match them.
 */
final class ScreenArt {
	/** Vanilla's scrollbar width and shortest handle. */
	static final int SCROLLBAR_WIDTH = 6;
	static final int SCROLLER_MIN = 32;

	private ScreenArt() {
	}

	/** A text field's frame, as a bordered EditBox draws it: white when focused, grey otherwise. */
	static void textField(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
		graphics.fill(x, y, x + width, y + height, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000);
	}

	/** A small magnifying glass, 12 pixels square with its top left at (x, y). */
	static void searchIcon(GuiGraphics graphics, int x, int y) {
		int c = 0xFFA0A0A0;
		// The lens: a ring 7 pixels across.
		graphics.fill(x + 2, y + 1, x + 6, y + 2, c);
		graphics.fill(x + 2, y + 6, x + 6, y + 7, c);
		graphics.fill(x + 1, y + 2, x + 2, y + 6, c);
		graphics.fill(x + 6, y + 2, x + 7, y + 6, c);
		// The handle, down to the bottom right.
		for (int i = 0; i < 4; i++) {
			graphics.fill(x + 6 + i, y + 6 + i, x + 8 + i, y + 8 + i, c);
		}
	}

	/** A darker in-world list panel from y0 to y1, with separator lines above and below it. */
	static void list(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, 0x80000000);
		separator(graphics, x0, y0 - 2, x1);
		separator(graphics, x0, y1, x1);
	}

	private static void separator(GuiGraphics graphics, int x0, int y, int x1) {
		graphics.fill(x0, y, x1, y + 1, 0x40FFFFFF);
		graphics.fill(x0, y + 1, x1, y + 2, 0x80000000);
	}

	/** Vanilla's selection outline: white when the list has focus, as vanilla's lists draw it. */
	static void selection(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
		graphics.fill(x0, y0, x1, y1, 0xFFFFFFFF);
		graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000);
	}

	/** Vanilla's scrollbar on the right of a list from y0 to y1, its handle {@code top} of the track down. */
	static void scrollbar(GuiGraphics graphics, int x, int y0, int y1, int handle, int top) {
		int x1 = x + SCROLLBAR_WIDTH;
		graphics.fill(x, y0, x1, y1, 0xFF000000);
		int h0 = y0 + top;
		graphics.fill(x, h0, x1, h0 + handle, 0xFF808080);
		graphics.fill(x, h0, x1 - 1, h0 + handle - 1, 0xFFC0C0C0);
	}
}
