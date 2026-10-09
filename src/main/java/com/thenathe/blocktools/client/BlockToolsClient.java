package com.thenathe.blocktools.client;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.sprite.SpriteNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import javax.imageio.ImageIO;

/** Loaded only on clients; no client classes are referenced by common/server code. */
public final class BlockToolsClient implements ClientModInitializer {
    private static volatile boolean detectedHigh;
    private static volatile boolean effectiveHigh;
    @Override public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SpriteNetworking.Choice.TYPE, (payload, context) -> effectiveHigh = payload.high());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            detectedHigh = detectHigh(client.getResourceManager());
            report();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> effectiveHigh = false);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public Identifier getFabricId() { return Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, "sprite_resolution"); }
            @Override public void onResourceManagerReload(ResourceManager manager) {
                detectedHigh = detectHigh(manager);
                Minecraft.getInstance().execute(BlockToolsClient::report);
            }
        });
    }
    private static void report() {
        if (ClientPlayNetworking.canSend(SpriteNetworking.Detection.TYPE))
            ClientPlayNetworking.send(new SpriteNetworking.Detection(detectedHigh));
    }
    /** Conservative mixed-pack policy: all three relevant textures must have square HD frames. */
    public static boolean detectHigh(ResourceManager manager) {
        for (String name : new String[] {"iron_ingot", "copper_ingot", "flint"}) {
            try {
                var resource = manager.getResource(Identifier.withDefaultNamespace("textures/item/" + name + ".png")).orElseThrow();
                try (var stream = resource.open(); var image = ImageIO.createImageInputStream(stream)) {
                    if (image == null) return false;
                    var readers = ImageIO.getImageReaders(image);
                    if (!readers.hasNext()) return false;
                    var reader = readers.next();
                    try {
                        reader.setInput(image, true, true);
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        var animation = resource.metadata().getSection(AnimationMetadataSection.TYPE);
                        if (animation.isPresent()) {
                            var frame = animation.get().calculateFrameSize(width, height);
                            if (frame.width() <= 0 || frame.height() <= 0
                                    || width % frame.width() != 0 || height % frame.height() != 0) return false;
                            width = frame.width(); height = frame.height();
                        }
                        if (width < 32 || width != height) return false;
                    } finally { reader.dispose(); }
                }
            } catch (Exception failure) { return false; }
        }
        return true;
    }
    public static boolean high() { return effectiveHigh; }
    /** Native registered stacks and Polymer stacks both use the local player's effective choice. */
    public static Identifier model(Identifier original) {
        if (original == null || !original.getNamespace().equals(BlockToolsMod.MOD_ID)) return original;
        String base = original.getPath();
        if (base.endsWith("_32")) base = base.substring(0, base.length() - 3);
        if (!base.equals("mortar_trowel") && !base.equals("garden_trowel") && !base.equals("prospecting_pick")) return original;
        return Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, base + (effectiveHigh ? "_32" : ""));
    }
}
