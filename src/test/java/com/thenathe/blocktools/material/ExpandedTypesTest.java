package com.thenathe.blocktools.material;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.List;
import java.util.Map;

/** Category overlap and exhaustive orientation regressions for structural and lighting palettes. */
public final class ExpandedTypesTest {
    private ExpandedTypesTest() { }

    public static void run() {
        testAliasesAndSelectors();
        testStructuralCategories();
        testLightSources();
        testExpandedCategories();
        testMountedTorches();
        testAllPositionalStates();
    }

    private static void testAliasesAndSelectors() {
        Map<BlockType, List<String>> aliases = Map.ofEntries(
            Map.entry(BlockType.BARS, List.of("bar", "bars")),
            Map.entry(BlockType.RAILINGS, List.of("railing", "railings", "fences_walls_bars")),
            Map.entry(BlockType.LIGHT_SOURCES, List.of("light", "lights", "light_source", "light_sources")),
            Map.entry(BlockType.TORCHES, List.of("torch", "torches")),
            Map.entry(BlockType.LANTERNS, List.of("lantern", "lanterns")),
            Map.entry(BlockType.BUTTONS, List.of("button", "buttons")),
            Map.entry(BlockType.PRESSURE_PLATES, List.of("pressure_plate", "pressure_plates")),
            Map.entry(BlockType.RAILS, List.of("rail", "rails")),
            Map.entry(BlockType.CHAINS, List.of("chain", "chains")),
            Map.entry(BlockType.LADDERS, List.of("ladder", "ladders")),
            Map.entry(BlockType.LEAVES, List.of("leaf", "leaves")),
            Map.entry(BlockType.CARPETS, List.of("carpet", "carpets")),
            Map.entry(BlockType.PILLARS, List.of("pillar", "pillars"))
        );
        aliases.forEach((expected, spellings) -> spellings.forEach(spelling -> {
            require(BlockType.parse(spelling) == expected, "wrong expanded alias: " + spelling);
            require(BlockType.parse(" " + spelling.toUpperCase(java.util.Locale.ROOT) + " ") == expected,
                "expanded alias lost case/space normalization: " + spelling);
            require(BlockFilter.parse("type:" + spelling).canonical().equals("type:" + expected.key()),
                "expanded alias lost canonical form: " + spelling);
        }));
        BlockFilter union = BlockFilter.parse("type:fence,wall,bar");
        BlockFilter railings = BlockFilter.parse("type:railings");
        require(union.canonical().equals("type:bars,fences,walls"), "structural type union was not normalized");
        for (Block block : BuiltInRegistries.BLOCK) {
            require(union.matches(block) == railings.matches(block), "railings differs from fences,walls,bars: " + block);
        }
        NamedSelectors first = NamedSelectors.parse("type:railings,light_sources to:iron_bars");
        NamedSelectors last = NamedSelectors.parse("to:iron_bars from:type:railings,light_sources");
        require(first.equals(last), "expanded selectors changed with nesting/order");
        require(ShuffleSelector.parse("from:type:railings").from().matches(Blocks.OAK_FENCE),
            "shuffle rejected expanded nested type selector");
        expectFailure(() -> BlockFilter.parse("type:lights,light_source"));
        expectFailure(() -> BlockFilter.parse("type:railing,railings"));
    }

    private static void testStructuralCategories() {
        int metalBars = 0;
        int glassPanes = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            boolean metal = id.equals("iron_bars") || id.endsWith("copper_bars");
            if (metal) {
                metalBars++;
                require(BlockType.BARS.matches(block), "bars missed metal/weather/wax variant: " + id);
                require(BlockType.RAILINGS.matches(block), "railings missed metal bars: " + id);
                require(!BlockType.PANES.matches(block), "metal bars leaked into glass panes: " + id);
            } else if (block instanceof IronBarsBlock) {
                glassPanes++;
                require(BlockType.PANES.matches(block), "panes missed glass pane: " + id);
                require(!BlockType.BARS.matches(block) && !BlockType.RAILINGS.matches(block),
                    "glass pane leaked into bars/railings: " + id);
            }
        }
        require(metalBars >= 9, "iron and all copper-bar wax/weather variants were not exercised: " + metalBars);
        require(glassPanes >= 17, "clear and all stained glass panes were not exercised: " + glassPanes);
        for (String id : List.of("oak_fence", "nether_brick_fence", "cobblestone_wall", "brick_wall", "iron_bars")) {
            require(BlockType.RAILINGS.matches(block(id)), "railings missed expected structure: " + id);
        }
        for (String id : List.of("oak_fence_gate", "glass_pane", "stone", "rail", "ladder")) {
            require(!BlockType.RAILINGS.matches(block(id)), "railings selected unrelated structure: " + id);
        }
    }

    private static void testLightSources() {
        BlockFilter lights = BlockFilter.parse("type:light_sources");
        for (String id : List.of("torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch",
                "redstone_wall_torch", "lantern", "soul_lantern", "redstone_lamp", "copper_bulb",
                "waxed_oxidized_copper_bulb", "campfire", "soul_campfire", "candle", "sea_pickle",
                "respawn_anchor", "light", "end_rod", "glowstone", "lava")) {
            require(lights.matches(block(id)), "light sources missed emitter or currently unlit source: " + id);
        }
        require(block("redstone_lamp").defaultBlockState().getLightEmission() == 0,
            "lamp regression no longer exercises an unlit default");
        require(block("copper_bulb").defaultBlockState().getLightEmission() == 0,
            "bulb regression no longer exercises an unlit default");
        for (String id : List.of("stone", "glass", "iron_bars", "oak_button", "oak_fence_gate", "ladder")) {
            require(!lights.matches(block(id)), "light sources selected a nonemitter: " + id);
        }
        int emitters = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean emitsInAnyState = block.getStateDefinition().getPossibleStates().stream()
                .anyMatch(state -> state.getLightEmission() > 0);
            require(lights.matches(block) == emitsInAnyState, "light category disagreed with possible emission: " + block);
            // Repeat after classification to exercise cached positive and negative results.
            require(lights.matches(block) == emitsInAnyState, "cached light classification changed: " + block);
            if (emitsInAnyState) emitters++;
        }
        require(emitters > 30, "light-emitter registry coverage unexpectedly shrank: " + emitters);
    }

    private static void testExpandedCategories() {
        Map<BlockType, List<String>> representatives = Map.ofEntries(
            Map.entry(BlockType.TORCHES, List.of("torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch")),
            Map.entry(BlockType.LANTERNS, List.of("lantern", "soul_lantern")),
            Map.entry(BlockType.BUTTONS, List.of("oak_button", "stone_button", "polished_blackstone_button")),
            Map.entry(BlockType.PRESSURE_PLATES, List.of("oak_pressure_plate", "stone_pressure_plate", "heavy_weighted_pressure_plate", "light_weighted_pressure_plate")),
            Map.entry(BlockType.RAILS, List.of("rail", "powered_rail", "detector_rail", "activator_rail")),
            Map.entry(BlockType.LADDERS, List.of("ladder")),
            Map.entry(BlockType.LEAVES, List.of("oak_leaves", "spruce_leaves", "azalea_leaves")),
            Map.entry(BlockType.CARPETS, List.of("white_carpet", "red_carpet", "moss_carpet", "pale_moss_carpet")),
            Map.entry(BlockType.PILLARS, List.of("oak_log", "stripped_oak_wood", "quartz_pillar", "bone_block", "hay_block", "basalt"))
        );
        representatives.forEach((type, ids) -> {
            BlockFilter filter = BlockFilter.parse("type:" + type.key());
            ids.forEach(id -> require(filter.matches(block(id)), type + " missed representative: " + id));
            require(!filter.matches(Blocks.STONE) && !filter.matches(Blocks.AIR), type + " selected unrelated full block/air");
        });
        List<Block> chains = BuiltInRegistries.BLOCK.stream().filter(block -> {
            String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
            return id.equals("chain") || id.endsWith("_chain");
        }).toList();
        require(!chains.isEmpty(), "chain registry coverage was empty");
        for (Block chain : chains) {
            require(BlockType.CHAINS.matches(chain), "chains missed material variant: " + chain);
            require(!BlockType.LIGHT_SOURCES.matches(chain), "chain was incorrectly classified as a light source");
        }
    }

    private static void testMountedTorches() {
        List<String> families = List.of("", "soul_", "redstone_");
        int states = 0;
        for (String sourceFamily : families) {
            for (boolean wall : List.of(false, true)) {
                Block source = block(sourceFamily + (wall ? "wall_torch" : "torch"));
                require(ReplacementStates.materialBlock(source) == block(sourceFamily + "torch"),
                    "wall torch did not normalize to its inventory material: " + source);
                for (BlockState original : source.getStateDefinition().getPossibleStates()) {
                    for (String destinationFamily : families) {
                        for (boolean destinationWall : List.of(false, true)) {
                            Block destination = block(destinationFamily + (destinationWall ? "wall_torch" : "torch"));
                            BlockState result = ReplacementStates.preserve(original, destination);
                            require(result.is(block(destinationFamily + (wall ? "wall_torch" : "torch"))),
                                "torch replacement changed floor/wall mounting: " + original + " -> " + result);
                            require(result.getBlock().asItem() == destination.asItem(), "torch replacement changed palette inventory item");
                            assertSharedProperties(original, result);
                            states++;
                        }
                    }
                }
            }
        }
        for (BlockState wallTorch : block("redstone_wall_torch").getStateDefinition().getPossibleStates()) {
            expectFailure(() -> ReplacementStates.preserve(wallTorch, Blocks.LANTERN));
        }
        for (BlockState lantern : Blocks.LANTERN.getStateDefinition().getPossibleStates()) {
            if (lantern.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING)) {
                expectFailure(() -> ReplacementStates.preserve(lantern, Blocks.TORCH));
            }
        }
        require(ReplacementStates.materialBlock(Blocks.LANTERN) == Blocks.LANTERN,
            "material normalization changed an ordinary block");
        System.out.println("Expanded categories and " + states + " torch mounting replacements passed.");
    }

    private static void testAllPositionalStates() {
        int states = 0;
        for (String[] pair : List.of(
                new String[]{"lantern", "soul_lantern"},
                new String[]{"end_rod", "end_rod"},
                new String[]{"small_amethyst_bud", "amethyst_cluster"},
                new String[]{"oak_button", "stone_button"},
                new String[]{"stone_pressure_plate", "polished_blackstone_pressure_plate"},
                new String[]{"light_weighted_pressure_plate", "heavy_weighted_pressure_plate"},
                new String[]{"powered_rail", "activator_rail"},
                new String[]{"rail", "rail"},
                new String[]{"ladder", "ladder"},
                new String[]{"oak_leaves", "spruce_leaves"},
                new String[]{"pale_moss_carpet", "pale_moss_carpet"},
                new String[]{"quartz_pillar", "bone_block"},
                new String[]{"candle", "red_candle"},
                new String[]{"redstone_lamp", "redstone_lamp"},
                new String[]{"sea_pickle", "sea_pickle"},
                new String[]{"glow_lichen", "glow_lichen"},
                new String[]{"light", "light"},
                new String[]{"campfire", "soul_campfire"},
                new String[]{"copper_bulb", "waxed_oxidized_copper_bulb"})) {
            states += assertAllProperties(block(pair[0]), block(pair[1]));
        }
        for (Block destination : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(destination).getPath();
            if (id.equals("iron_bars") || id.endsWith("copper_bars")) {
                states += assertAllProperties(Blocks.IRON_BARS, destination);
                states += assertAllProperties(destination, Blocks.IRON_BARS);
            }
            if (id.equals("chain") || id.endsWith("_chain")) states += assertAllProperties(destination, destination);
            if (destination instanceof net.minecraft.world.level.block.LanternBlock) {
                require(BlockType.LANTERNS.matches(destination) && BlockType.LIGHT_SOURCES.matches(destination),
                    "lantern material/wax/weather variant was not classified: " + id);
                states += assertAllProperties(destination, Blocks.SOUL_LANTERN);
                states += assertAllProperties(Blocks.SOUL_LANTERN, destination);
            }
        }
        require(states >= 800, "expanded positional coverage unexpectedly shrank: " + states);
        System.out.println("Expanded lighting/attachment/connection properties passed for " + states + " states.");
    }

    private static int assertAllProperties(Block source, Block destination) {
        List<BlockState> states = source.getStateDefinition().getPossibleStates();
        for (BlockState original : states) {
            BlockState replacement = ReplacementStates.preserve(original, destination);
            require(replacement.is(destination), "replacement retained wrong material: " + original);
            assertSharedProperties(original, replacement);
            require(ReplacementStates.preserve(original, source).equals(original),
                "same-material replacement lost positional state: " + original);
        }
        return states.size();
    }

    private static void assertSharedProperties(BlockState original, BlockState replacement) {
        for (Property<?> property : original.getProperties()) {
            Property<?> target = replacement.getBlock().getStateDefinition().getProperty(property.getName());
            if (target == null) continue;
            String value = propertyValue(original, property);
            if (target.getValue(value).isPresent()) {
                require(propertyValue(replacement, target).equals(value),
                    "replacement lost " + property.getName() + "=" + value + ": " + original + " -> " + replacement);
            }
        }
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static Block block(String id) {
        Block result = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + id));
        require(result != null && result != Blocks.AIR, "missing test fixture block: " + id);
        return result;
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected unsupported mounting or duplicate type to be rejected");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage() != null && !expected.getMessage().isBlank(), "rejection omitted explanation");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
