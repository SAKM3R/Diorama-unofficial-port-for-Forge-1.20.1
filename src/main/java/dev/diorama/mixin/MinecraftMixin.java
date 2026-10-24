package dev.diorama.mixin;

import dev.diorama.MapSession;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaving a world saves the map on the next tick, but closing the window stops ticking. This runs
 * as the game is asked to stop, before the last frame, so from then on nothing may start the map
 * again (Minecraft.isRunning() is false).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "stop", at = @At("HEAD"))
	private void diorama$onStop(CallbackInfo ci) {
		MapSession.close();
	}
}
