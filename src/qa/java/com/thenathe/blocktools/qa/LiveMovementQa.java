package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.tunnel.TunnelService;
import com.thenathe.blocktools.tunnel.TunnelSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LiveMovementQa {
    private static int assertions;
    public static int run(ServerPlayer player, ServerLevel level) throws Exception {
        assertions = 0;
        var tick = TunnelService.class.getDeclaredMethod("tickPlayer", ServerPlayer.class);
        tick.setAccessible(true);
        BlockPos start = new BlockPos(400, 100, 400);
        for (int x = 24; x <= 26; x++) for (int z = 24; z <= 27; z++) level.getChunk(x, z);
        for (int z = 0; z <= 31; z++) prepare(level, start.south(z), Direction.SOUTH);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockToolsMod.TUNNEL_CONFIG.toolItem()));
        player.setYRot(0);
        move(player, start);
        var state = BlockToolsMod.TUNNELS.state(player.getUUID());
        state.configure(TunnelSpec.parse("width:2 height:3 to:bricks[100]", 3, 3));
        tick.invoke(null, player);
        check(start.equals(state.lastCenter) && start.equals(state.runOrigin), "initial held-tool tick locks original block line");
        check(state.runDirection == Direction.SOUTH && state.runDimension.equals(level.dimension().identifier().toString()),
            "initial lock captures facing and dimension");
        check(level.getBlockState(start.below()).is(Blocks.BRICKS), "initial tick lines shell");
        move(player, start.south(3));
        tick.invoke(null, player);
        for (int z = 1; z <= 3; z++) check(level.getBlockState(start.south(z).below()).is(Blocks.BRICKS), "catch-up fills slice " + z);

        Map<BlockPos, BlockState> before = snapshot(level, start);
        move(player, start.south(3).east());
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(start.equals(state.runOrigin) && start.south(3).equals(state.lastCenter), "repeated sideways ticks preserve original line and progress");
        move(player, start.south(5).east());
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(start.equals(state.runOrigin) && start.south(3).equals(state.lastCenter), "diagonal movement cannot re-anchor or catch up off line");
        move(player, start.south(4).above());
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(start.equals(state.runOrigin) && start.south(3).equals(state.lastCenter), "elevation changes preserve the lock across ticks");
        check(before.equals(snapshot(level, start)), "off-line diagonal and elevation errors make no world edits");

        move(player, start.south(4));
        player.setYRot(-90);
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(state.runDirection == Direction.SOUTH && start.south(3).equals(state.lastCenter), "turning east pauses edits without changing the locked heading");
        check(level.getBlockState(start.south(4).below()).is(Blocks.STONE), "rotated player cannot place next slice on original line");
        player.setYRot(180);
        tick.invoke(null, player);
        check(start.south(3).equals(state.lastCenter), "opposite cardinal heading also remains blocked");
        player.setYRot(10);
        tick.invoke(null, player);
        check(start.south(4).equals(state.lastCenter), "returning to original cardinal heading resumes despite small yaw");
        check(level.getBlockState(start.south(4).below()).is(Blocks.BRICKS), "returning to locked line and heading resumes placement");
        move(player, start.south());
        tick.invoke(null, player);
        check(start.south().equals(state.lastCenter), "backtracking along original line advances trail");
        check(state.lastDirection == Direction.SOUTH && state.runDirection == Direction.SOUTH, "backtracking retains even-width orientation");

        move(player, start.south(20));
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(start.south().equals(state.lastCenter) && start.equals(state.runOrigin), "excessive catch-up retains original lock on repeated ticks");
        check(level.getBlockState(start.south(20).below()).is(Blocks.STONE), "excessive jump never edits destination on later ticks");
        move(player, start.south(2));
        tick.invoke(null, player);
        check(start.south(2).equals(state.lastCenter), "returning within catch-up range resumes original trail");
        String dimension = state.runDimension;
        state.runDimension = "minecraft:the_nether";
        move(player, start.south(5));
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(state.runDimension.equals("minecraft:the_nether") && start.south(2).equals(state.lastCenter), "dimension mismatch preserves original lock without editing");
        check(level.getBlockState(start.south(5).below()).is(Blocks.STONE), "dimension mismatch leaves next slice unchanged");
        state.runDimension = dimension;
        player.setGameMode(GameType.ADVENTURE);
        tick.invoke(null, player);
        check(start.equals(state.runOrigin) && start.south(2).equals(state.lastCenter), "adventure ticks preserve lock and cannot edit");
        player.setGameMode(GameType.CREATIVE);

        move(player, start.south(2));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BlockToolsMod.TUNNEL_CONFIG.toolItem()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        tick.invoke(null, player);
        check(start.equals(state.runOrigin), "moving held tool to offhand does not release lock");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        tick.invoke(null, player);
        check(state.runOrigin == null && state.runDirection == null && state.runDimension == null
            && state.lastCenter == null && state.processedCenters.isEmpty(), "putting tool away in both hands clears full line lock");
        BlockPos newLine = start.east(8).south(4);
        prepare(level, newLine, Direction.EAST);
        move(player, newLine);
        player.setYRot(-90);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BlockToolsMod.TUNNEL_CONFIG.toolItem()));
        tick.invoke(null, player);
        check(newLine.equals(state.runOrigin) && state.runDirection == Direction.EAST && newLine.equals(state.lastCenter),
            "put away rotate and pull out starts a new line from offhand");

        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        tick.invoke(null, player);
        BlockPos blockedStart = start.south(30);
        level.setBlock(blockedStart.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        move(player, blockedStart);
        player.setYRot(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BlockToolsMod.TUNNEL_CONFIG.toolItem()));
        tick.invoke(null, player);
        check(blockedStart.equals(state.runOrigin) && state.lastCenter == null, "failed first slice still captures original line");
        move(player, blockedStart.east());
        for (int i = 0; i < 3; i++) tick.invoke(null, player);
        check(blockedStart.equals(state.runOrigin) && state.lastCenter == null, "sideways movement after first failure cannot establish a different origin");
        move(player, blockedStart.south());
        tick.invoke(null, player);
        check(blockedStart.equals(state.runOrigin) && state.lastCenter == null, "moving forward retries failed original slice instead of silently re-anchoring");
        check(level.getBlockState(blockedStart.south().below()).is(Blocks.STONE), "failed first slice prevents later slices from bypassing it");
        level.setBlock(blockedStart.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        tick.invoke(null, player);
        check(blockedStart.equals(state.runOrigin) && blockedStart.south().equals(state.lastCenter), "repairing first slice resumes original run and catch-up");
        check(level.getBlockState(blockedStart.below()).is(Blocks.BRICKS)
            && level.getBlockState(blockedStart.south().below()).is(Blocks.BRICKS), "resumed initial failure lines original and next slice");
        state.disarm();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setGameMode(GameType.SURVIVAL);
        return assertions;
    }

    private static void move(ServerPlayer player, BlockPos pos) {
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }

    private static void prepare(ServerLevel level, BlockPos center, Direction direction) {
        for (int lateral = -2; lateral <= 2; lateral++) for (int y = -1; y <= 3; y++) {
            level.setBlock(TunnelService.lateral(center, direction, lateral).above(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
        for (BlockPos pos : TunnelService.shell(center, direction, 2, 3).keySet()) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        }
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos start) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int x = -3; x <= 3; x++) for (int z = 0; z <= 6; z++) for (int y = -1; y <= 4; y++) {
            BlockPos pos = start.offset(x, y, z);
            result.put(pos, level.getBlockState(pos));
        }
        return result;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        assertions++;
    }
}
