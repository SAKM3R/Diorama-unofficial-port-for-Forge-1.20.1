package dev.diorama.forge;

import dev.diorama.DioramaClient;
import dev.diorama.MapSession;
import dev.diorama.MinimapHud;
import dev.diorama.ServerRules;
import dev.diorama.Settings;
import dev.diorama.SettingsScreen;
import dev.diorama.TrackedPlayers;
import dev.diorama.WorldKey;
import dev.diorama.XaeroChannels;
import dev.diorama.waypoint.WaypointHud;
import dev.diorama.waypoint.WaypointShare;
import dev.diorama.waypoint.WaypointStore;
import dev.diorama.waypoint.Waystones;
import dev.diorama.waypoint.WorldProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

/**
 * What {@code dev.diorama.fabric.DioramaFabric} does on Fabric, through Forge's events. Every
 * listener names its event class, so Forge never has to work the type out of a lambda.
 */
final class DioramaForgeClient {
	private DioramaForgeClient() {
	}

	static void init(IEventBus modBus) {
		DioramaClient.init();
		// The mod list's Config button opens the map settings.
		ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
			() -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
		// The settings file sits in the game directory; load it on the main thread once the game is set up.
		modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
			Settings.get();
		}));
		modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, event -> {
			DioramaClient.KEY_MAPPINGS.forEach(event::register);
		});
		// Above the whole vanilla HUD, in the same order as on Fabric: waypoint labels, then the
		// minimap over them.
		modBus.addListener(EventPriority.NORMAL, false, RegisterGuiOverlaysEvent.class, event -> {
			WaypointHud waypoints = new WaypointHud();
			MinimapHud minimap = new MinimapHud();
			event.registerAboveAll("waypoints", (gui, graphics, partialTick, width, height) -> waypoints.render(graphics, partialTick));
			event.registerAboveAll("minimap", (gui, graphics, partialTick, width, height) -> minimap.render(graphics, partialTick));
		});
		XaeroChannels.register();

		IEventBus forge = MinecraftForge.EVENT_BUS;
		// Fired for the integrated server's chunks too, on its own thread.
		forge.addListener(EventPriority.NORMAL, false, ChunkEvent.Load.class, event -> {
			if (event.getLevel() instanceof ClientLevel level && event.getChunk() instanceof LevelChunk chunk && Minecraft.getInstance().isRunning()) {
				MapSession.chunkLoaded(level, chunk);
			}
		});
		// Servers set their rules with codes in system messages; read them before any other mod can
		// change or hide the line, cancelled or not.
		forge.addListener(EventPriority.HIGHEST, true, ClientChatReceivedEvent.System.class, event -> ServerRules.onServerMessage(event.getMessage()));
		forge.addListener(EventPriority.NORMAL, false, RegisterClientCommandsEvent.class, event -> WaypointShare.register(event.getDispatcher()));
		forge.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class, event -> leftOrJoined());
		// A network moving the player to another of its servers joins again without leaving.
		forge.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class, event -> leftOrJoined());
		forge.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
			Minecraft mc = Minecraft.getInstance();
			if (event.phase == TickEvent.Phase.END && mc.isRunning()) {
				DioramaClient.tick(mc);
			}
		});
		// The projection the world is drawn with, after view bobbing, the damage tilt and nausea,
		// and the camera's rotation, so waypoint labels stay pinned to their spot in the world.
		forge.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, event -> {
			if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
				WorldProjection.capture(new Matrix4f(event.getProjectionMatrix()), new Matrix4f(event.getPoseStack().last().pose()),
					event.getCamera().getPosition());
			}
		});
	}

	private static void leftOrJoined() {
		ServerRules.onDisconnect();
		WaypointStore.onLeave();
		WorldKey.onJoinOrLeave();
		XaeroChannels.onJoinOrLeave();
		TrackedPlayers.clear();
		Waystones.clear();
	}
}
