package com.thenathe.blocktools;

import com.thenathe.blocktools.material.BlockMaterials;
import com.thenathe.blocktools.material.BlockFilter;
import com.thenathe.blocktools.material.Palette;
import com.thenathe.blocktools.material.OverlayPalette;
import com.thenathe.blocktools.tone.ToneService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.List;

public final class MaterialSafetyTest {
    private MaterialSafetyTest() { }
    public static void run() {
        require(BlockMaterials.itemCount(Blocks.STONE_SLAB.defaultBlockState()) == 1, "single slab quantity changed");
        require(BlockMaterials.itemCount(Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE)) == 2, "double slab lost material");
        require(BlockMaterials.itemCount(Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 7)) == 7, "snow layers lost material");
        require(BlockMaterials.itemCount(Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 4)) == 4, "clustered candles lost material");
        require(BlockMaterials.itemCount(Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 3)) == 3, "sea pickles lost material");
        require(BlockMaterials.itemCount(Blocks.TURTLE_EGG.defaultBlockState().setValue(BlockStateProperties.EGGS, 4)) == 4, "turtle eggs lost material");
        require(BlockMaterials.itemCount(Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, 3)) == 3, "flower quantity changed");
        require(BlockMaterials.itemCount(Blocks.AIR.defaultBlockState()) == 0, "air returned an item");
        require(BlockMaterials.isMultipart(Blocks.OAK_DOOR.defaultBlockState()), "door was allowed as an independent source");
        require(BlockMaterials.isMultipart(Blocks.BED.red().defaultBlockState()), "bed was allowed as an independent source");
        require(BlockMaterials.isMultipart(Blocks.TALL_GRASS.defaultBlockState()), "tall plant was allowed as an independent source");
        require(!BlockMaterials.isMultipart(Blocks.OAK_STAIRS.defaultBlockState()), "stair half was mistaken for a multipart block");
        fail(() -> Palette.parse("oak_door:100", false, false));
        fail(() -> Palette.parseBracketed("red_bed[100]", false, false));
        fail(() -> OverlayPalette.parse("sunflower[]"));
        fail(() -> Palette.parse("stone:100,", false, false));
        fail(() -> BlockFilter.parse("stone,"));
        require(BlockFilter.parse("cave_air").includesAir(), "explicit cave air could not enter the selection");
        require(!BlockFilter.all().matches(Blocks.CAVE_AIR), "default any unexpectedly matched cave air");
        fail(() -> ToneService.cuboid(List.of(new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0)), 65_536));
        fail(() -> ToneService.cuboid(List.of(new BlockPos(-30_000_000, -64, -30_000_000), new BlockPos(30_000_000, 319, 30_000_000)), 65_536));
        require(ToneService.cuboid(List.of(new BlockPos(Integer.MAX_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0)), 1).size() == 1, "maximum coordinate did not terminate");
    }
    private static void fail(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsafe input was accepted");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
