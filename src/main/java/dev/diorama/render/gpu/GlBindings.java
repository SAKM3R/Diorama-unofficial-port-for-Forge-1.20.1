package dev.diorama.render.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Makes Minecraft's copy of the texture bindings true again. GlStateManager skips a bind its copy
 * says is already done, but other mods (shader and renderer mods such as Oculus and Embeddium)
 * bind textures and switch texture units straight through OpenGL. Uploading a tile then writes
 * into whatever texture they left bound, and a draw samples it. This binds nothing on the first
 * few units, both for real and in Minecraft's copy, so the next bind of any texture really happens.
 */
final class GlBindings {
	/** The texture units the map's shaders use. */
	static final int UNITS = 3;

	private GlBindings() {
	}

	/** Clears the bindings on the first {@code units} units and leaves unit 0 active, in OpenGL and in Minecraft's copy. */
	static void reset(int units) {
		for (int unit = units - 1; unit >= 0; unit--) {
			GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
			GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
			GlStateManager._bindTexture(0);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}
	}

	/** Binds a texture on unit 0 for certain, for uploading to it or setting how it's sampled. */
	static void bindForUpdate(int texture) {
		reset(1);
		GlStateManager._bindTexture(texture);
	}
}
