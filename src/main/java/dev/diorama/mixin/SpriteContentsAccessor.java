package dev.diorama.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A sprite's own pixels, which the map averages for each block's colour. Through an accessor rather
 * than reflection, because a Forge 1.20.1 install names the field differently from the source.
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("originalImage")
	NativeImage diorama$originalImage();
}
