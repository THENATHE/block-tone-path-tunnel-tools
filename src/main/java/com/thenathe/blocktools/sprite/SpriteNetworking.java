package com.thenathe.blocktools.sprite;

import com.thenathe.blocktools.BlockToolsMod;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Each message is one boolean; a report can only affect its authenticated sender. */
public final class SpriteNetworking {
    private SpriteNetworking() {}
    public record Detection(boolean high) implements CustomPacketPayload {
        public static final Type<Detection> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, "sprite_detection"));
        public static final StreamCodec<ByteBuf, Detection> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Detection::high, Detection::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Choice(boolean high) implements CustomPacketPayload {
        public static final Type<Choice> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, "sprite_choice"));
        public static final StreamCodec<ByteBuf, Choice> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Choice::high, Choice::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void initialize() {
        PayloadTypeRegistry.serverboundPlay().register(Detection.TYPE, Detection.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Choice.TYPE, Choice.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Detection.TYPE, (payload, context) -> SpritePreferences.report(context.player(), payload.high()));
    }
}
