package com.thenathe.blocktools.clientqa;

import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Drives the real local player's input and checks changes on its integrated server. */
public final class TunnelWalkClientQa implements FabricClientGameTest {
    private final List<String> evidence = new ArrayList<>();
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            run(context, world);
            evidence.add("RESULT: PASS; actual client keyboard movement, integrated server, Creative inventory and recipe sync");
            Files.write(Path.of("clientqa-result.txt"), evidence);
        } catch (Throwable failure) {
            evidence.add("RESULT: FAIL; " + failure);
            try { Files.write(Path.of("clientqa-result.txt"), evidence); } catch (Exception ignored) { }
            throw new AssertionError("Real-client Tunnel Tool QA failed", failure);
        }
    }
    private void run(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        var server = world.getServer();
        var connection = world.getConnection();
        connection.waitForChunksDownload();
        server.runOnServer(s -> {
            var player = connection.getServerPlayer();
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            var level = player.level();
            for (int x = -12; x <= 12; x++) for (int z = -8; z <= 64; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState());
            }
            player.connection.teleport(0.5, 100, 0.5, 0, 0);
        });
        connection.waitForClientboundPackets();
        context.waitTicks(10);
        context.runOnClient(client -> client.options.autoJump().set(false));
        context.getInput().lookAt(0, 0);
        server.runOnServer(s -> {
            var player = connection.getServerPlayer();
            for (String id : new String[] {"mortar_trowel", "garden_trowel", "prospecting_pick"}) {
                check(player.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE,
                    Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, id))), "default recipe unlocked: " + id);
            }
        });
        context.runOnClient(client -> {
            CreativeModeTabs.tryRebuildTabContents(client.level.enabledFeatures(), true, client.level.registryAccess());
            var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.withDefaultNamespace("tools_and_utilities"));
            for (String name : new String[] {"Block Toning Tool", "Path Tool", "Tunnel Tool"}) {
                check(tab.getDisplayItems().stream().anyMatch(stack -> stack.getHoverName().getString().equals(name)),
                    "native client Creative tab contains " + name);
                var displayContext = SlotDisplayContext.fromLevel(client.level);
                check(client.player.getRecipeBook().getCollections().stream().flatMap(collection -> collection.getRecipes().stream())
                    .flatMap(recipe -> recipe.resultItems(displayContext).stream())
                    .anyMatch(stack -> stack.getHoverName().getString().equals(name)), "client crafting book received " + name);
            }
        });
        command(context, "tunneltool width:5 height:3 to:stone_bricks[100]");
        server.runOnServer(s -> {
            var player = connection.getServerPlayer();
            var state = BlockToolsMod.TUNNELS.state(player.getUUID());
            check(state.enabled && state.configured(), "client command configured tunnel successfully");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockToolsMod.TUNNEL_CONFIG.toolItem()));
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        });
        connection.waitForClientboundPackets();
        context.waitTicks(3);
        var start = context.computeOnClient(client -> client.player.position());
        context.getInput().holdKeyFor(options -> options.keyUp, 55);
        context.waitTicks(5);
        connection.waitForServerboundPackets();
        var end = context.computeOnClient(client -> client.player.position());
        check(end.z - start.z >= 8, "forward key moved real client at least 8 blocks: " + start + " -> " + end);
        int completed = server.computeOnServer(s -> {
            var player = connection.getServerPlayer();
            var state = BlockToolsMod.TUNNELS.state(player.getUUID());
            check(state.runOrigin != null && state.runOrigin.getX() == 0 && state.runDirection == Direction.SOUTH,
                "held run anchors original south-facing line: origin=" + state.runOrigin + " direction=" + state.runDirection);
            check(state.processedCenters.size() >= 8, "actual walk completed at least eight slices: " + state.processedCenters.size());
            for (BlockPos center : state.processedCenters) {
                check(center.getX() == 0 && center.getY() == 100, "completed center remains on original line " + center);
                check(player.level().getBlockState(center.below()).is(Blocks.STONE_BRICKS), "walk laid floor " + center);
                check(player.level().getBlockState(center.above(3)).is(Blocks.STONE_BRICKS), "walk built ceiling " + center);
                check(player.level().getBlockState(center.offset(3, 0, 0)).is(Blocks.STONE_BRICKS)
                    && player.level().getBlockState(center.offset(-3, 0, 0)).is(Blocks.STONE_BRICKS), "walk built both walls " + center);
            }
            return state.processedCenters.size();
        });
        context.getInput().lookAt(180, 25);
        context.waitTicks(2);
        evidence.add("SCREENSHOT: " + context.takeScreenshot("tunnel-straight-client-walk"));
        context.getInput().holdKeyFor(options -> options.keyUp, 8);
        context.waitTicks(3);
        server.runOnServer(s -> {
            var state = BlockToolsMod.TUNNELS.state(connection.getServerPlayer().getUUID());
            check(state.processedCenters.size() == completed && state.runDirection == Direction.SOUTH,
                "rotated backward walk does not edit or change original direction");
        });
        context.getInput().lookAt(0, 0);
        context.getInput().holdKeyFor(options -> options.keyRight, 12);
        context.waitTicks(3);
        var sidestep = context.computeOnClient(client -> client.player.position());
        check(Math.abs(sidestep.x - start.x) > 1, "real strafe leaves original line: " + sidestep);
        context.getInput().holdKeyFor(options -> options.keyUp, 15);
        context.waitTicks(3);
        server.runOnServer(s -> {
            var state = BlockToolsMod.TUNNELS.state(connection.getServerPlayer().getUUID());
            check(state.processedCenters.size() == completed && state.runOrigin.getX() == 0,
                "off-line forward movement cannot shift held tunnel line");
        });
        context.getInput().lookAt(180, 30);
        evidence.add("SCREENSHOT: " + context.takeScreenshot("tunnel-off-line-no-extension"));
        // Real hotbar input unequips/re-equips; no direct state reset or position simulation.
        context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
        context.waitTicks(3);
        server.runOnServer(s -> check(BlockToolsMod.TUNNELS.state(connection.getServerPlayer().getUUID()).runOrigin == null,
            "unequipping via hotbar clears run anchor"));
        command(context, "tunneltool width:1 height:3 walls:air[100] floor:stone_bricks[100] ceiling:air[100]");
        context.getInput().lookAt(0, 0);
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        context.waitTicks(3);
        var restart = context.computeOnClient(client -> client.player.blockPosition());
        context.getInput().holdKeyFor(options -> options.keyUp, 30);
        context.waitTicks(3);
        server.runOnServer(s -> {
            var player = connection.getServerPlayer();
            var state = BlockToolsMod.TUNNELS.state(player.getUUID());
            check(state.runOrigin != null && state.runOrigin.getX() == restart.getX() && state.runOrigin.getX() != 0,
                "re-equipping accepts new parallel line");
            check(state.processedCenters.size() >= 4, "new held run builds from actual forward movement");
            for (BlockPos center : state.processedCenters) {
                check(player.level().getBlockState(center.below()).is(Blocks.STONE_BRICKS)
                    && player.level().getBlockState(center.above(3)).isAir()
                    && player.level().getBlockState(center.offset(1, 0, 0)).isAir()
                    && player.level().getBlockState(center.offset(-1, 0, 0)).isAir(), "air wall/ceiling palette builds only floor " + center);
            }
        });
        context.getInput().lookAt(180, 30);
        evidence.add("SCREENSHOT: " + context.takeScreenshot("tunnel-reequipped-new-line"));
    }
    private static void command(ClientGameTestContext context, String text) {
        context.runOnClient(client -> client.player.connection.sendCommand(text));
        context.waitTicks(3);
    }
    private void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        evidence.add("PASS: " + message);
    }
}
