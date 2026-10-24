package dev.diorama.compat;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Something Diorama draws over the HUD. On Forge 1.20.1 each one is a GUI overlay registered
 * above the whole vanilla HUD (see {@code DioramaForgeClient}), which hands over the partial tick.
 */
public interface HudElement {
	void render(GuiGraphics graphics, float partialTick);
}
