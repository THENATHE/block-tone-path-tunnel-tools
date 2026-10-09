package com.thenathe.blocktools.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Stages a complete edit and its inventory reservation before publishing ordinary world callbacks. */
public final class WorldEdits {
    static final int STAGING_FLAGS = Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private WorldEdits() { }

    public static boolean apply(final ServerLevel level, final Map<BlockPos, BlockState> desired) {
        return apply(level, desired, () -> true);
    }

    public static boolean apply(final ServerLevel level, final Map<BlockPos, BlockState> desired, final BooleanSupplier commit) {
        return apply(new Access() {
            public BlockState get(BlockPos pos) { return level.getBlockState(pos); }
            public boolean set(BlockPos pos, BlockState state) { return level.setBlock(pos, state, STAGING_FLAGS); }
            public void placed(BlockPos pos, BlockState oldState) {
                BlockState current = level.getBlockState(pos);
                if (oldState.getBlock() != current.getBlock() || current.getBlock() instanceof BaseRailBlock) {
                    oldState.affectNeighborsAfterRemoval(level, pos, false);
                }
                if (level.getBlockState(pos).getBlock() == desired.get(pos).getBlock()) {
                    level.getBlockState(pos).onPlace(level, pos, oldState, false);
                }
            }
            public void notify(BlockPos pos, BlockState oldState) {
                BlockState current = level.getBlockState(pos);
                level.sendBlockUpdated(pos, oldState, current, Block.UPDATE_ALL);
                level.updateNeighborsAt(pos, oldState.getBlock(), null);
                if (current.hasAnalogOutputSignal()) level.updateNeighbourForOutputSignal(pos, current.getBlock());
                oldState.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
                current.updateNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
                current.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_CLIENTS);
            }
        }, desired, commit);
    }

    static boolean apply(final Access world, final Map<BlockPos, BlockState> desired) {
        return apply(world, desired, () -> true);
    }

    static boolean apply(final Access world, final Map<BlockPos, BlockState> desired, final BooleanSupplier commit) {
        Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : desired.entrySet()) {
            BlockState existing = world.get(entry.getKey());
            if (!existing.equals(entry.getValue())) original.put(entry.getKey(), existing);
        }
        try {
            for (BlockPos pos : original.keySet()) {
                if (world.set(pos, desired.get(pos)) && world.get(pos).equals(desired.get(pos))) continue;
                restore(world, original);
                return false;
            }
            for (Map.Entry<BlockPos, BlockState> entry : desired.entrySet()) {
                if (!world.get(entry.getKey()).equals(entry.getValue())) {
                    restore(world, original);
                    return false;
                }
            }
            if (!commit.getAsBoolean()) {
                restore(world, original);
                return false;
            }
        } catch (RuntimeException exception) {
            try { restore(world, original); }
            catch (RuntimeException rollbackFailure) { exception.addSuppressed(rollbackFailure); }
            throw exception;
        }
        for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) world.placed(entry.getKey(), entry.getValue());
        for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) world.notify(entry.getKey(), entry.getValue());
        return true;
    }

    private static void restore(final Access world, final Map<BlockPos, BlockState> original) {
        for (Map.Entry<BlockPos, BlockState> entry : original.entrySet()) {
            if (world.get(entry.getKey()).equals(entry.getValue())) continue;
            if (!world.set(entry.getKey(), entry.getValue()) || !world.get(entry.getKey()).equals(entry.getValue())) {
                throw new IllegalStateException("Could not restore failed world edit at " + entry.getKey());
            }
        }
    }

    interface Access {
        BlockState get(BlockPos pos);
        boolean set(BlockPos pos, BlockState state);
        default void placed(BlockPos pos, BlockState oldState) { }
        void notify(BlockPos pos, BlockState oldState);
    }
}
