package com.thenathe.blocktools.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Copy only properties with the same name and a value supported by the destination. */
public final class ReplacementStates {
    private static final Map<TorchForm, Block> TORCH_FORMS = new ConcurrentHashMap<>();
    private ReplacementStates() { }

    /** Wall and standing torch states share one inventory material and palette quota. */
    public static Block materialBlock(Block block) {
        if (block instanceof BaseTorchBlock && block.asItem() instanceof BlockItem item
            && item.getBlock() instanceof BaseTorchBlock) return item.getBlock();
        return block;
    }

    private static Block mountedDestination(BlockState source, Block destination) {
        boolean sourceTorch = source.getBlock() instanceof BaseTorchBlock;
        boolean sourceWallTorch = sourceTorch && source.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
        boolean sourceLantern = source.getBlock() instanceof LanternBlock;
        if (destination instanceof LanternBlock && sourceWallTorch) {
            throw new IllegalArgumentException("A lantern cannot retain a wall torch's attachment. Choose a torch destination for wall-mounted torches; nothing was changed.");
        }
        if (destination instanceof BaseTorchBlock && sourceLantern
            && source.getValue(BlockStateProperties.HANGING)) {
            throw new IllegalArgumentException("A torch cannot retain a hanging lantern's ceiling attachment. Choose a lantern destination; nothing was changed.");
        }
        if (!(destination instanceof BaseTorchBlock) || (!sourceTorch && !sourceLantern)) return destination;
        boolean wall = sourceWallTorch;
        if (destination.defaultBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING) == wall) return destination;
        TorchForm key = new TorchForm(destination.asItem(), wall);
        Block resolved = TORCH_FORMS.computeIfAbsent(key, form -> {
            for (Block candidate : BuiltInRegistries.BLOCK) {
                if (candidate instanceof BaseTorchBlock && candidate.asItem() == form.item()
                    && candidate.defaultBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING) == form.wall()) return candidate;
            }
            return null;
        });
        if (resolved == null) throw new IllegalArgumentException("The destination torch has no compatible "
            + (wall ? "wall" : "standing") + " variant; choose a compatible torch material. Nothing was changed.");
        return resolved;
    }

    private record TorchForm(Item item, boolean wall) { }

    public static BlockState preserve(BlockState source, Block destination) {
        BlockState result = mountedDestination(source, destination).defaultBlockState();
        for (Property<?> target : result.getProperties()) {
            Property<?> original = source.getBlock().getStateDefinition().getProperty(target.getName());
            if (original != null) result = copyValue(source, original, result, target);
        }
        return result;
    }

    private static <S extends Comparable<S>, T extends Comparable<T>> BlockState copyValue(
        BlockState source, Property<S> original, BlockState result, Property<T> target
    ) {
        return target.getValue(original.getName(source.getValue(original)))
            .map(value -> result.setValue(target, value)).orElse(result);
    }
}
