package com.thenathe.blocktools.client.mixin;

import com.thenathe.blocktools.client.BlockToolsClient;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyVariable(method = "appendItemLayers", at = @At("STORE"), ordinal = 0)
    private Identifier blocktools$chooseSprite(Identifier model) { return BlockToolsClient.model(model); }
}
