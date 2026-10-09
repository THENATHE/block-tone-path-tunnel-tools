package com.thenathe.blocktools.sprite;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.compat.PolymerTools;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** World-owned explicit preferences persist; client detection is discarded at disconnect. */
public final class SpritePreferences {
    public enum Mode { AUTO, LOW, HIGH }
    private static final Map<UUID, Mode> MODES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> DETECTED = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static Path file;
    private static boolean writable;
    private SpritePreferences() {}
    public static void initialize() {
        SpriteNetworking.initialize();
        ServerLifecycleEvents.SERVER_STARTING.register(SpritePreferences::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forgetSession(handler.getPlayer().getUUID()));
        // Coalesce even a malicious stream of cosmetic reports to at most one refresh per second.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (UUID id : Set.copyOf(PENDING)) {
                PENDING.remove(id);
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) refresh(player);
            }
        });
    }
    public static Mode mode(UUID player) { return MODES.getOrDefault(player, Mode.AUTO); }
    public static boolean hasClient(UUID player) { return DETECTED.containsKey(player); }
    public static boolean high(UUID player) {
        return switch (mode(player)) {
            case HIGH -> true;
            case LOW -> false;
            case AUTO -> DETECTED.getOrDefault(player, false);
        };
    }
    public static void report(ServerPlayer player, boolean high) {
        UUID id = player.getUUID();
        Boolean previous = DETECTED.put(id, high);
        if (previous == null || previous != high) PENDING.add(id);
    }
    public static void forgetSession(UUID id) { DETECTED.remove(id); PENDING.remove(id); }
    public static void select(ServerPlayer player, Mode mode) throws IOException {
        UUID id = player.getUUID();
        Mode previous = MODES.get(id);
        if (mode == Mode.AUTO) MODES.remove(id); else MODES.put(id, mode);
        try { save(); } catch (IOException failure) {
            if (previous == null) MODES.remove(id); else MODES.put(id, previous);
            throw failure;
        }
        refresh(player);
    }
    public static String description(UUID id) {
        return "Tool sprites: " + switch (mode(id)) { case AUTO -> "auto"; case LOW -> "16x16"; case HIGH -> "32x32"; }
            + " (currently " + (high(id) ? "32x32" : "16x16") + ")."
            + (mode(id) == Mode.AUTO && !hasClient(id) ? " Auto defaults to 16x16 without the client mod." : "");
    }
    private static void sync(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SpriteNetworking.Choice.TYPE))
            ServerPlayNetworking.send(player, new SpriteNetworking.Choice(high(player.getUUID())));
    }
    private static void refresh(ServerPlayer player) {
        sync(player);
        PolymerTools.refreshSprites(player);
    }
    public static void load(MinecraftServer server) {
        clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve("data/blocktools-sprite-preferences.json");
        writable = true;
        if (!Files.exists(file)) return;
        try {
            JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var entry : object.entrySet()) {
                UUID id = UUID.fromString(entry.getKey());
                Mode mode = Mode.valueOf(entry.getValue().getAsString());
                if (mode != Mode.AUTO) MODES.put(id, mode);
            }
        } catch (Exception failure) {
            MODES.clear();
            writable = false;
            BlockToolsMod.LOGGER.error("Cannot read sprite preferences; preserving existing file", failure);
        }
    }
    private static void save() throws IOException {
        if (!writable || file == null) throw new IOException("Sprite preference storage is unavailable.");
        Files.createDirectories(file.getParent());
        JsonObject object = new JsonObject();
        MODES.forEach((id, mode) -> object.addProperty(id.toString(), mode.name()));
        Path temporary = Files.createTempFile(file.getParent(), "blocktools-sprites-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(object) + "\n");
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
    private static void clear() {
        MODES.clear(); DETECTED.clear(); PENDING.clear(); file = null; writable = false;
    }
}
