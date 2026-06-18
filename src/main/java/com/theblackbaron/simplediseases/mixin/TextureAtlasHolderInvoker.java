package com.theblackbaron.simplediseases.mixin;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TextureAtlasHolder.class)
public interface TextureAtlasHolderInvoker {

    @Invoker("getSprite")
    TextureAtlasSprite simplediseases$invokeGetSprite(ResourceLocation location);
}
