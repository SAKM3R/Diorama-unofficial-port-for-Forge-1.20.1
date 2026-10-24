package dev.diorama;

import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import org.jspecify.annotations.Nullable;

/**
 * The plugin channels Xaero's Minimap and World Map share with servers. Servers built around
 * Xaero's mods send on them: Xaero's mods installed on the server, and plugins such as
 * MapModCompanion. Both channels carry the same messages, each one byte for its kind and then
 * its body. Diorama reads:
 * <ul>
 *   <li>the level ID (kind 0, a big-endian int): the server's name for the world the player is
 *       in, which tells apart worlds behind one address that share a seed;</li>
 *   <li>the handshake (kind 1, the server's protocol version as an int), which Diorama answers
 *       like Xaero's mods do, since servers only track players for clients that answered;</li>
 *   <li>tracked players (kind 2, one player in network NBT, see {@link TrackedPlayers}) and the
 *       tracker reset (kind 3, empty), sent before the server sends everyone again.</li>
 * </ul>
 *
 * <p>Servers track players on one channel per client, the minimap's when the client answered
 * there. Diorama takes them from that channel only, so a server moving a player from one channel
 * to the other can't remove them. It doesn't answer at all while Xaero's World Map is installed:
 * an answer moves the server's tracking from the World Map's channel to Diorama.
 *
 * <p>Forge allows one handler per channel, and registering a channel twice throws at startup. So a channel is only Diorama's when the Xaero mod that owns it isn't installed. With
 * both of Xaero's mods installed, they keep their channels and Diorama names worlds by their seed.
 */
public final class XaeroChannels {
	/** Message kinds, the first byte of every message. */
	static final int LEVEL_ID = 0;
	static final int HANDSHAKE = 1;
	static final int TRACKED_PLAYER = 2;
	static final int TRACKER_RESET = 3;
	/** The protocol version Xaero's mods answer with, which the player tracker needs. */
	private static final int PROTOCOL = 3;
	/** The channel version Xaero's mods register with. */
	private static final String VERSION = "1.0";

	/** The channels, public for tests that send on them. */
	public static final ResourceLocation MINIMAP = new ResourceLocation("xaerominimap", "main");
	public static final ResourceLocation WORLD_MAP = new ResourceLocation("xaeroworldmap", "main");

	/** Whether Diorama listens on at least one of the channels. */
	private static boolean listening;
	/** The channel tracked players come on: the first Diorama answered on, the minimap's over the World Map's. */
	private static @Nullable ResourceLocation trackerChannel;

	private XaeroChannels() {
	}

	/** One message: its channel and kind, then its body as sent. */
	public record Message(ResourceLocation channel, int kind, byte[] body) {
		/** The level ID this message carries, or null if it's another kind. */
		public @Nullable Integer levelId() {
			return this.kind == LEVEL_ID && this.body.length >= 4 ? ByteBuffer.wrap(this.body).getInt() : null;
		}
	}

	/**
	 * Listens on the channels no Xaero mod has. Called once, while the mod is constructed. The
	 * channels accept any version on either side, so a server without them, or with another
	 * version of Xaero's on it, still lets the player in.
	 */
	public static void register() {
		for (ResourceLocation channel : unclaimed()) {
			var events = NetworkRegistry.ChannelBuilder.named(channel)
				.networkProtocolVersion(() -> VERSION)
				.clientAcceptedVersions(version -> true)
				.serverAcceptedVersions(version -> true)
				.eventNetworkChannel();
			// One listener per channel, each a method of its own, so Forge can tell the event type.
			if (channel.equals(MINIMAP)) {
				events.addListener(XaeroChannels::onMinimap);
			} else {
				events.addListener(XaeroChannels::onWorldMap);
			}
		}
	}

	private static void onMinimap(NetworkEvent event) {
		onPayload(MINIMAP, event);
	}

	private static void onWorldMap(NetworkEvent event) {
		onPayload(WORLD_MAP, event);
	}

	/**
	 * A message from the server, on the network thread. The whole payload is read here, before
	 * Forge frees it, and handled on the client thread.
	 */
	private static void onPayload(ResourceLocation channel, NetworkEvent event) {
		NetworkEvent.Context context = event.getSource().get();
		FriendlyByteBuf payload = event.getPayload();
		// Channel registrations and anything the client sent (a LAN guest's messages, on the
		// integrated server) come here too.
		if (payload == null || context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
			return;
		}
		int kind = payload.isReadable() ? payload.readByte() : -1;
		byte[] body = new byte[payload.readableBytes()];
		payload.readBytes(body);
		Message message = new Message(channel, kind, body);
		context.enqueueWork(() -> receive(message));
		context.setPacketHandled(true);
	}

	/** The channels whose Xaero mod isn't installed. */
	private static List<ResourceLocation> unclaimed() {
		List<ResourceLocation> unclaimed = new ArrayList<>(2);
		// Fair-Play Minimap used the minimap's channel name in some versions.
		if (unclaimed(MINIMAP, "xaerominimap", "xaerominimapfair")) {
			unclaimed.add(MINIMAP);
		}
		if (unclaimed(WORLD_MAP, "xaeroworldmap")) {
			unclaimed.add(WORLD_MAP);
		}
		listening = !unclaimed.isEmpty();
		return unclaimed;
	}

	private static boolean unclaimed(ResourceLocation channel, String... xaeroMods) {
		for (String mod : xaeroMods) {
			if (modLoaded(mod)) {
				Diorama.LOGGER.info("{} is installed, so it keeps {} to itself", mod, channel);
				return false;
			}
		}
		return true;
	}

	private static boolean modLoaded(String mod) {
		return ModList.get().isLoaded(mod);
	}

	private static void receive(Message message) {
		switch (message.kind()) {
			case LEVEL_ID -> {
				Integer id = message.levelId();
				if (id != null) {
					WorldKey.serverLevelId(id);
				}
			}
			case HANDSHAKE -> answer(message.channel());
			case TRACKED_PLAYER -> {
				if (message.channel().equals(trackerChannel)) {
					CompoundTag tag = readNbt(message.body());
					if (tag != null) {
						TrackedPlayers.update(tag);
					}
				}
			}
			case TRACKER_RESET -> {
				if (message.channel().equals(trackerChannel)) {
					TrackedPlayers.clear();
				}
			}
			default -> {
			}
		}
	}

	/** Answers a server's handshake the way Xaero's mods do. It sent one, so it listens there. */
	private static void answer(ResourceLocation channel) {
		if (modLoaded("xaeroworldmap")) {
			return;
		}
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		if (connection == null) {
			return;
		}
		if (trackerChannel == null || channel.equals(MINIMAP) && !MINIMAP.equals(trackerChannel)) {
			trackerChannel = channel;
			TrackedPlayers.clear();
		}
		FriendlyByteBuf answer = new FriendlyByteBuf(Unpooled.buffer());
		answer.writeByte(HANDSHAKE);
		answer.writeInt(PROTOCOL);
		connection.send(new ServerboundCustomPayloadPacket(channel, answer));
	}

	private static @Nullable CompoundTag readNbt(byte[] body) {
		try {
			return new FriendlyByteBuf(Unpooled.wrappedBuffer(body)).readNbt();
		} catch (RuntimeException e) {
			Diorama.LOGGER.warn("Couldn't read a tracked player from the server", e);
			return null;
		}
	}

	/** Left the server, or a network moved the player to another of its servers. */
	public static void onJoinOrLeave() {
		trackerChannel = null;
	}

	static boolean listening() {
		return listening;
	}

	/**
	 * Whether the server said it listens on one of the channels that come with level IDs. Forge
	 * 1.20.1 doesn't tell the client which channels a server declared, so this is never known up
	 * front: a server that names its worlds is waited on from the visit after it first sent an ID
	 * (see {@code WorldKey.namedBefore}), or once it sent one this visit.
	 */
	static boolean serverMayNameWorlds() {
		return false;
	}
}
