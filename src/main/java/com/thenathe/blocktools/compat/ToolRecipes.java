package com.thenathe.blocktools.compat;

import com.mojang.serialization.MapCodec;
import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Filters optional tool data before recipe/advancement decoding, with no Polymer linkage. */
public final class ToolRecipes implements ResourceCondition {
    private static final ResourceConditionType<ToolRecipes> TYPE = ResourceConditionType.create(
        Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, "polymer_tools_enabled"), MapCodec.unit(ToolRecipes::new));
    private static volatile ReloadQueue queue = new ReloadQueue(null);

    public static void register() {
        ResourceConditions.register(TYPE);
        ServerLifecycleEvents.SERVER_STOPPED.register(ToolRecipes::clear);
    }
    @Override public ResourceConditionType<?> getType() { return TYPE; }
    @Override public boolean test(RegistryOps.RegistryInfoLookup registryInfo) { return PolymerTools.active(); }

    /** Serialize rapid config toggles; never wait or join on the server thread. */
    public static synchronized CompletableFuture<Void> refresh(MinecraftServer server) {
        if (queue.server != server) queue = new ReloadQueue(server);
        ReloadQueue current = queue;
        List<String> packs = List.copyOf(server.getPackRepository().getSelectedIds());
        // Vanilla reloadResources performs managedBlock when invoked on the server thread.
        // Start it on the worker pool; vanilla schedules its apply phase back on the server.
        current.pending = current.pending.handle((ignored, failure) -> null).thenComposeAsync(ignored ->
            server.reloadResources(packs)).whenComplete((ignored, failure) -> current.failed = failure != null);
        return current.pending;
    }

    public static boolean needsRetry(MinecraftServer server) {
        ReloadQueue current = queue;
        return current.server == server && current.failed;
    }

    public static CompletableFuture<Void> pendingReload() { return queue.pending; }

    private static synchronized void clear(MinecraftServer server) {
        // An integrated server may stop with queued work unfinished; the next world must not wait on it.
        if (queue.server == server) queue = new ReloadQueue(null);
    }

    private static final class ReloadQueue {
        final MinecraftServer server;
        volatile CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
        volatile boolean failed;
        ReloadQueue(MinecraftServer server) { this.server = server; }
    }
}
