package dev.diorama.mixin;

import dev.diorama.MapSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.client.ClientCommandHandler;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the map when server light for a chunk has been applied, which cave slices draw. Hooks the
 * method that applies it; the packet handler only queues that work.
 * It also runs the client's own commands from chat links.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "applyLightData", at = @At("TAIL"))
	// There was no scheduleRebuild parameter.
	private void diorama$onLightApplied(int x, int z, ClientboundLightUpdatePacketData lightData, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			MapSession.lightUpdated(mc.level, x, z);
		}
	}

	// A chat link's command, like a shared waypoint's [Add], goes through here. Whether Forge 1.20.1
	// runs the client's own commands from it isn't certain, so Diorama asks Forge's client command
	// handler first. Optional: if the method isn't there, links fall back to whatever Forge does.
	@Inject(method = "sendUnsignedCommand", at = @At("HEAD"), cancellable = true, require = 0)
	private void diorama$runClientCommand(String command, CallbackInfoReturnable<Boolean> cir) {
		if (ClientCommandHandler.runCommand(command)) {
			cir.setReturnValue(true);
		}
	}
}
