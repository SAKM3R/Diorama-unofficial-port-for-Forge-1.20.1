package dev.diorama.render.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// How ImmediateCanvas draws on 1.20.1: each shape is drawn at once with one of three shaders,
// without depth testing, so the map draws in painter's order. Textures are bound by hand
// (RenderSystem.setShaderTexture) before each draw.
//
// A mod shader that fails to compile through the game's normal path takes the whole resource
// reload down with it, so the fog's program only ever comes through a side door; fogReady() says
// whether it may be drawn. A resource reload can change the shader's sources, so the fog is
// checked again after each one.
final class LegacyShaders {
	private static final Logger LOGGER = LoggerFactory.getLogger("diorama");

	private static ShaderInstance fogShader;
	// Each resource reload makes new instances of vanilla's own programs; this is the one seen at the
	// last check, so a different one means the fog has to be checked again.
	private static Object fogReload;
	private static boolean fogFailed;

	static final Supplier<ShaderInstance> TEXTURED = GameRenderer::getPositionTexColorShader;
	static final Supplier<ShaderInstance> SOLID = GameRenderer::getPositionColorShader;
	static final Supplier<ShaderInstance> FOG = () -> fogShader;

	static boolean fogReady() {
		ShaderInstance reload = GameRenderer.getPositionTexColorShader();
		if (reload != fogReload) {
			fogReload = reload;
			fogFailed = false;
			if (fogShader != null) {
				fogShader.close();
				fogShader = null;
			}
		}
		// Built by hand rather than through Forge's shader registration event, whose failure would
		// fail the whole resource reload. The json and both stages live in the minecraft namespace,
		// where the vanilla constructor looks. The game doesn't know about it, so it's closed and
		// built again here after a reload.
		if (fogShader == null && !fogFailed) {
			try {
				fogShader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "diorama_fog", DefaultVertexFormat.POSITION_TEX_COLOR);
			} catch (Exception e) {
				fogFailed = true;
				LOGGER.error("Diorama's fog shader didn't compile; the map draws without fog", e);
			}
		}
		return fogShader != null;
	}

	/**
	 * Draws a finished mesh with a shader: blended like vanilla's translucent shapes, no depth test,
	 * no depth writes, no culling. The GUI's flush turns depth testing on, so earlier GUI shapes
	 * could hide parts of the map: off for the draw, then back as it was.
	 */
	static void draw(Supplier<ShaderInstance> shader, BufferBuilder.RenderedBuffer mesh) {
		boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		RenderSystem.setShader(shader);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(
			GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
			GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);
		BufferUploader.drawWithShader(mesh);
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
		if (depth) {
			RenderSystem.enableDepthTest();
		}
	}

	static void drawFog(BufferBuilder.RenderedBuffer mesh) {
		draw(FOG, mesh);
	}

	private LegacyShaders() {
	}
}
