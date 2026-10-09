package com.thenathe.blocktools.material;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Source categories use vanilla classes/tags, including compatible modded blocks. */
public enum BlockType {
    STAIRS, SLABS, TRAPDOORS, FENCES, WALLS, BARS, PANES, FENCE_GATES, RAILINGS,
    LOGS, PILLARS, BUTTONS, PRESSURE_PLATES, RAILS, CHAINS, LADDERS, LEAVES, CARPETS,
    TORCHES, LANTERNS, LIGHT_SOURCES, BLOCKS, LIQUIDS, AIR;

    private static final Map<Block, Boolean> LIGHT_EMITTERS = new ConcurrentHashMap<>();

    public boolean matches(Block block) {
        return switch (this) {
            case STAIRS -> block instanceof StairBlock;
            case SLABS -> block instanceof SlabBlock;
            case TRAPDOORS -> block instanceof TrapDoorBlock;
            case FENCES -> block instanceof FenceBlock;
            case WALLS -> block instanceof WallBlock;
            case BARS -> isBars(block);
            case PANES -> block instanceof IronBarsBlock && !isBars(block);
            case FENCE_GATES -> block instanceof FenceGateBlock;
            case RAILINGS -> FENCES.matches(block) || WALLS.matches(block) || BARS.matches(block);
            case LOGS -> block.defaultBlockState().is(BlockTags.LOGS);
            case PILLARS -> block instanceof RotatedPillarBlock;
            case BUTTONS -> block instanceof ButtonBlock;
            case PRESSURE_PLATES -> block instanceof BasePressurePlateBlock;
            case RAILS -> block instanceof BaseRailBlock;
            case CHAINS -> block instanceof ChainBlock;
            case LADDERS -> block instanceof LadderBlock;
            case LEAVES -> block instanceof LeavesBlock;
            case CARPETS -> block instanceof CarpetBlock || block instanceof MossyCarpetBlock;
            case TORCHES -> block instanceof BaseTorchBlock;
            case LANTERNS -> block instanceof LanternBlock;
            // Include temporarily unlit lamps, candles, redstone torches and copper bulbs.
            // Cache by registered block so bulk selections do not repeatedly scan state spaces.
            case LIGHT_SOURCES -> LIGHT_EMITTERS.computeIfAbsent(block,
                candidate -> candidate.getStateDefinition().getPossibleStates().stream()
                    .anyMatch(state -> state.getLightEmission() > 0));
            case LIQUIDS -> block instanceof LiquidBlock;
            case AIR -> block.defaultBlockState().isAir();
            case BLOCKS -> !(block instanceof StairBlock || block instanceof SlabBlock
                || block instanceof TrapDoorBlock || block instanceof FenceBlock || block instanceof WallBlock
                || block instanceof IronBarsBlock || block instanceof FenceGateBlock || block instanceof LiquidBlock)
                && Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        };
    }

    private static boolean isBars(Block block) {
        // Native identity fallback also works during bootstrap before world tags are loaded.
        return block == Blocks.IRON_BARS || Blocks.COPPER_BARS.asList().contains(block)
            || block.defaultBlockState().is(BlockTags.BARS);
    }

    public String key() { return name().toLowerCase(Locale.ROOT); }

    public static List<String> keys() { return Arrays.stream(values()).map(BlockType::key).toList(); }

    public static BlockType parse(String input) {
        String key = input.trim().toLowerCase(Locale.ROOT);
        key = switch (key) {
            case "stair" -> "stairs";
            case "slab" -> "slabs";
            case "trapdoor" -> "trapdoors";
            case "fence" -> "fences";
            case "wall" -> "walls";
            case "bar" -> "bars";
            case "pane" -> "panes";
            case "fence_gate", "gate", "gates" -> "fence_gates";
            case "railing", "fences_walls_bars" -> "railings";
            case "log" -> "logs";
            case "pillar" -> "pillars";
            case "button" -> "buttons";
            case "pressure_plate" -> "pressure_plates";
            case "rail" -> "rails";
            case "chain" -> "chains";
            case "ladder" -> "ladders";
            case "leaf" -> "leaves";
            case "carpet" -> "carpets";
            case "torch" -> "torches";
            case "lantern" -> "lanterns";
            case "light_source", "light", "lights" -> "light_sources";
            case "block" -> "blocks";
            case "liquid", "fluid", "fluids" -> "liquids";
            default -> key;
        };
        for (BlockType type : values()) if (type.key().equals(key)) return type;
        throw new IllegalArgumentException("Unknown source type '" + input + "'. Use " + String.join(",", keys()) + ".");
    }
}
