package com.thenathe.blocktools.material;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Material accounting for blocks representable by an independent cell edit. */
public final class BlockMaterials {
    private BlockMaterials() { }

    public static boolean isMultipart(final BlockState state) {
        return state.getBlock() instanceof BedBlock || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    public static void requireSingleBlock(final Block block) {
        if (isMultipart(block.defaultBlockState())) {
            throw new IllegalArgumentException("Multipart blocks such as doors, beds, and tall plants are not supported as destinations.");
        }
    }

    public static int itemCount(final BlockState state) {
        if (state.getBlock().asItem() == Items.AIR) return 0;
        if (isMultipart(state)) return 0;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)
            && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        for (IntegerProperty property : new IntegerProperty[] {
            BlockStateProperties.LAYERS, BlockStateProperties.CANDLES, BlockStateProperties.PICKLES,
            BlockStateProperties.EGGS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
        }) {
            if (state.hasProperty(property)) return state.getValue(property);
        }
        return 1;
    }
}
