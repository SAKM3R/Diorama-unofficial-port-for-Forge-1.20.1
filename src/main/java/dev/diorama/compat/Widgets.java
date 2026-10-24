package dev.diorama.compat;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;

/** Vanilla widgets the way 1.20.1 makes them, for the screens written against newer builders. */
public final class Widgets {
	/** The box and the gap before its label, as vanilla's checkbox draws them. */
	private static final int BOX = 24;

	private Widgets() {
	}

	/**
	 * A checkbox with its label, as wide as the label needs but at most {@code maxWidth}, calling
	 * {@code changed} with its new value on every click.
	 */
	public static Checkbox checkbox(Component label, Font font, int x, int y, int maxWidth, boolean selected, Consumer<Boolean> changed) {
		int width = Math.min(maxWidth, BOX + font.width(label));
		return new Checkbox(x, y, width, 20, label, selected) {
			@Override
			public void onPress() {
				super.onPress();
				changed.accept(this.selected());
			}
		};
	}
}
