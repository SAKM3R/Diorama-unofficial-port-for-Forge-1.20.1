package dev.diorama.forge;

import dev.diorama.Diorama;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Diorama's entrypoint on Forge. The mod is client-side only: on a dedicated server it loads and
 * does nothing, so a modpack can ship it to both sides. Everything that touches client classes is
 * in {@link DioramaForgeClient}, which a server never loads.
 */
@Mod(Diorama.MOD_ID)
public final class DioramaForge {
	public DioramaForge() {
		if (FMLEnvironment.dist == Dist.CLIENT) {
			DioramaForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
		} else {
			Diorama.LOGGER.info("Diorama is a client-side mod; it does nothing on a dedicated server");
		}
	}
}
