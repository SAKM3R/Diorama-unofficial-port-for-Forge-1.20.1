package dev.diorama;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Diorama {
	public static final String MOD_ID = "diorama";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Extra logging, on with -Ddiorama.debug=true or DIORAMA_DEBUG=1. */
	public static final boolean DEBUG = Boolean.getBoolean("diorama.debug") || "1".equals(System.getenv("DIORAMA_DEBUG"));

	private Diorama() {
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}
}
