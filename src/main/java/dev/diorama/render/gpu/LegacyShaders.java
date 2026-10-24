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
		// The shader binds its textures as it's applied; make sure those binds really happen.
		GlBindings.reset(GlBindings.UNITS);
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

	private static int fogDiagnosed;
	private static boolean lastFast;

	/**
	 * The fog, logging what the GPU sees for the first draws at each quality: the program's
	 * attribute and sampler locations, the bound textures, the mask under the middle of the view,
	 * and the screen pixel there before and after the fog.
	 */
	static void drawFog(BufferBuilder.RenderedBuffer mesh, MapTexture mask, MapTexture noise, float guiX, float guiY, float maskU, float maskV, int params) {
		boolean fast = (params >>> 24 & 15) >= 2;
		if (fast != lastFast) {
			lastFast = fast;
			fogDiagnosed = 0;
		}
		if (fogDiagnosed >= 2 || fogShader == null || !(mask instanceof GlIdTexture probe)) {
			draw(FOG, mesh);
			return;
		}
		// Only over known ground, where the fog should let the map through.
		int[] middle = probe.readBack(0, 256);
		int mx = Math.max(0, Math.min(255, (int) (maskU * 256)));
		int my = Math.max(0, Math.min(255, (int) (maskV * 256)));
		if ((middle[my * 256 + mx] & 0xFF) != 0xFF) {
			draw(FOG, mesh);
			return;
		}
		fogDiagnosed++;
		int program = fogShader.getId();
		String attributes = "Position " + org.lwjgl.opengl.GL20.glGetAttribLocation(program, "Position")
			+ ", UV0 " + org.lwjgl.opengl.GL20.glGetAttribLocation(program, "UV0")
			+ ", Color " + org.lwjgl.opengl.GL20.glGetAttribLocation(program, "Color");
		String uniforms = "Sampler0 " + org.lwjgl.opengl.GL20.glGetUniformLocation(program, "Sampler0")
			+ ", Sampler1 " + org.lwjgl.opengl.GL20.glGetUniformLocation(program, "Sampler1")
			+ ", GameTime " + org.lwjgl.opengl.GL20.glGetUniformLocation(program, "GameTime")
			+ ", ColorModulator " + org.lwjgl.opengl.GL20.glGetUniformLocation(program, "ColorModulator");
		String maskInfo = "n/a";
		if (mask instanceof GlIdTexture gl) {
			int[] texels = gl.readBack(0, 256);
			int full = 0;
			int clear = 0;
			int deep = 0;
			for (int t : texels) {
				full += (t & 0xFF) == 0xFF ? 1 : 0;
				clear += (t >> 8 & 0xFF) == 0xFF ? 1 : 0;
				deep += (t >> 16 & 0xFF) == 0xFF ? 1 : 0;
			}
			int tx = Math.max(0, Math.min(255, (int) (maskU * 256)));
			int ty = Math.max(0, Math.min(255, (int) (maskV * 256)));
			maskInfo = "known " + full + ", clear " + clear + ", deep " + deep + " of 65536, middle texel " + Integer.toHexString(texels[ty * 256 + tx]);
		}
		com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
		double scale = window.getGuiScale();
		int px = (int) (guiX * scale);
		int py = window.getHeight() - 1 - (int) (guiY * scale);
		int before = readPixel(px, py);
		draw(FOG, mesh);
		int after = readPixel(px, py);
		LOGGER.info("Diorama fog diagnostics ({}): program {}, attributes [{}], uniforms [{}], textures bound {} {} (mask {}, noise {}), mask {}, params {}, pixel at {},{} before {} after {}, GL error {}",
			fast ? "fast" : "fancy", program, attributes, uniforms, RenderSystem.getShaderTexture(0), RenderSystem.getShaderTexture(1),
			((GlIdTexture) mask).id(), ((GlIdTexture) noise).id(), maskInfo, Integer.toHexString(params), px, py,
			Integer.toHexString(before), Integer.toHexString(after), GL11.glGetError());
	}

	private static int readPixel(int x, int y) {
		java.nio.ByteBuffer buffer = org.lwjgl.system.MemoryUtil.memAlloc(4);
		try {
			GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
			return buffer.getInt(0);
		} finally {
			org.lwjgl.system.MemoryUtil.memFree(buffer);
		}
	}

	private LegacyShaders() {
	}
}
