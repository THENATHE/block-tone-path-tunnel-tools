package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real command and inventory checks for the expanded selectors and mounted light replacements. */
public final class LiveExpandedTypesQa {
    private static final BlockPos CENTER = new BlockPos(352, 150, 352);
    private static final int EXTENT = 42;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private final ServerPlayer player;
    private final ServerLevel level;
    private int assertions;

    private LiveExpandedTypesQa(ServerPlayer player, ServerLevel level) {
        this.player = player;
        this.level = level;
        for (int x = CENTER.getX() >> 4; x <= (CENTER.getX() + EXTENT) >> 4; x++) {
            for (int z = CENTER.getZ() >> 4; z <= (CENTER.getZ() + EXTENT) >> 4; z++) level.getChunk(x, z);
        }
    }

    public static int run(ServerPlayer player, ServerLevel level) throws Exception {
        LiveExpandedTypesQa qa = new LiveExpandedTypesQa(player, level);
        try {
            qa.selectorBoundaries();
            qa.copperBars();
            qa.lightSources();
            qa.torches();
            qa.lanterns();
            qa.mountedAndPositionalFamilies();
            qa.atomicFailures();
        } finally {
            player.setGameMode(GameType.SURVIVAL);
            player.getInventory().clearContent();
            BlockToolsMod.STATES.remove(player.getUUID());
            BlockToolsMod.TUNNELS.remove(player.getUUID());
        }
        return qa.assertions;
    }

    private void selectorBoundaries() throws Exception {
        String[] materials = {"oak_fence", "cobblestone_wall", "iron_bars", "copper_bars", "glass_pane", "blue_stained_glass_pane", "oak_fence_gate", "stone"};
        for (String type : List.of("bars", "bar", "railings", "railing", "fences_walls_bars", "fences,walls,bars", "panes")) {
            reset(GameType.CREATIVE);
            List<BlockPos> targets = new ArrayList<>();
            for (int i = 0; i < materials.length; i++) {
                BlockPos pos = CENTER.offset(3 + i * 5, 0, 3);
                put(pos, block(materials[i]).defaultBlockState());
                targets.add(pos);
            }
            normalize(targets);
            select(CENTER, CENTER.offset(41, 0, 5));
            Map<BlockPos, BlockState> before = snapshot();
            check(command("blocktone type:" + type + " to:bricks[100]") > 0, "selector rejected: " + type);
            for (int i = 0; i < targets.size(); i++) {
                boolean selected = type.equals("panes") ? i == 4 || i == 5
                    : type.equals("bars") || type.equals("bar") ? i == 2 || i == 3 : i < 4;
                check(level.getBlockState(targets.get(i)).equals(selected ? Blocks.BRICKS.defaultBlockState() : before.get(targets.get(i))),
                    type + " selected incorrect family " + materials[i]);
            }
        }
    }

    private void copperBars() throws Exception {
        List<Block> destinations = BuiltInRegistries.BLOCK.stream().filter(candidate -> {
            String name = name(candidate);
            return name.contains("copper") && name.endsWith("_bars");
        }).toList();
        check(destinations.size() >= 8, "expected normal/weathered/waxed copper bar variants in registry");
        for (Block destination : destinations) {
            reset(GameType.SURVIVAL);
            BlockPos pos = CENTER.offset(3, 0, 3);
            for (Direction side : HORIZONTAL) put(pos.relative(side), Blocks.STONE.defaultBlockState());
            put(pos, state("iron_bars", "waterlogged", "true"));
            normalize(List.of(pos));
            BlockState before = level.getBlockState(pos);
            select(pos, pos);
            player.getInventory().setItem(0, new ItemStack(destination.asItem(), 1));
            check(command("blocktone type:bars to:" + name(destination) + "[100]") > 0, "copper bars replacement rejected: " + name(destination));
            sameState(pos, destination, before, "copper bar connections/waterlogging");
            check(count(destination.asItem()) == 0 && count(block("iron_bars").asItem()) == 1,
                "copper bar material settlement incorrect");
            check(command("blocktone type:bars to:" + name(destination) + "[100]") > 0, "copper bars must remain in bars selector");
            check(count(block("iron_bars").asItem()) == 1, "repeated copper bar command refunded twice");
        }
    }

    private void lightSources() throws Exception {
        String[] names = {"torch", "lantern", "redstone_lamp", "glowstone", "shroomlight", "sea_lantern", "redstone_torch", "campfire", "lava", "stone"};
        for (String alias : List.of("light_sources", "light_source", "lights", "light")) {
            reset(GameType.CREATIVE);
            List<BlockPos> targets = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                BlockPos pos = CENTER.offset(3 + i % 8 * 5, 0, 3 + i / 8 * 5);
                put(pos.below(), Blocks.STONE.defaultBlockState());
                put(pos, block(names[i]).defaultBlockState());
                targets.add(pos);
            }
            // Selection includes unlit variants even though their current state emits zero light.
            put(targets.get(2), state("redstone_lamp", "lit", "false"));
            put(targets.get(6).below(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            put(targets.get(6), state("redstone_torch", "lit", "false"));
            select(CENTER, CENTER.offset(41, 0, 10));
            Map<BlockPos, BlockState> before = snapshot();
            check(command("blocktone type:" + alias + " to:glowstone[100]") > 0, "light alias rejected: " + alias);
            for (int i = 0; i < targets.size(); i++) {
                check(level.getBlockState(targets.get(i)).equals(i < 7 ? Blocks.GLOWSTONE.defaultBlockState() : before.get(targets.get(i))),
                    "light selector missed or changed excluded source: " + names[i]);
            }
            select(targets.get(8), targets.get(8));
            check(command("blocktone type:lights,liquids to:glowstone[100]") > 0
                && level.getBlockState(targets.get(8)).is(Blocks.GLOWSTONE), "explicit liquids must enable luminous lava selection");
        }
    }

    private void torches() throws Exception {
        String[][] changes = {{"torch", "wall_torch", "soul_torch", "soul_wall_torch"},
            {"soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch"},
            {"redstone_torch", "redstone_wall_torch", "torch", "wall_torch"}};
        for (String[] change : changes) {
            reset(GameType.SURVIVAL);
            List<BlockPos> targets = new ArrayList<>();
            BlockPos floor = CENTER.offset(3, 0, 3);
            put(floor.below(), Blocks.STONE.defaultBlockState());
            put(floor, block(change[0]).defaultBlockState());
            targets.add(floor);
            for (int i = 0; i < HORIZONTAL.length; i++) {
                BlockPos pos = CENTER.offset(9 + i * 6, 0, 3);
                Direction facing = HORIZONTAL[i];
                put(pos.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
                put(pos, state(change[1], "facing", facing.getSerializedName()));
                targets.add(pos);
            }
            for (BlockPos pos : targets) check(level.getBlockState(pos).canSurvive(level, pos), "torch fixture lacks real support");
            select(CENTER, CENTER.offset(35, 0, 6));
            player.getInventory().setItem(0, new ItemStack(block(change[2]).asItem(), targets.size()));
            Map<BlockPos, BlockState> before = snapshot();
            check(command("blocktone type:torches to:" + change[2] + "[100]") > 0, "torch replacement rejected");
            for (BlockPos pos : targets) {
                BlockState after = level.getBlockState(pos);
                check(after.is(block(pos.equals(floor) ? change[2] : change[3])), "torch standing/wall variant lost at " + pos);
                preserveCommon(before.get(pos), after, "torch facing");
                check(after.canSurvive(level, pos), "replacement torch cannot survive on original support");
            }
            check(count(block(change[2]).asItem()) == 0 && count(block(change[0]).asItem()) == targets.size(),
                "wall torch replacement must use corresponding inventory item");
            Map<Item, Integer> inventory = InventoryMaterials.count(player);
            check(command("blocktone type:torch to:" + change[2] + "[100]") > 0, "repeated torch command rejected");
            check(InventoryMaterials.count(player).equals(inventory), "replacing same mounted torch material charged or refunded inventory");
            check(command("blocktone calc type:torches to:" + change[2]) > 0, "torch calculate preview rejected with existing wall materials");
            check(command("blocktone calc apply type:torches to:" + change[2]) > 0, "torch calculate apply must recognize existing wall-torch inventory material");
            check(InventoryMaterials.count(player).equals(inventory), "torch calculate no-op charged/refunded inventory");
            for (BlockPos pos : targets) check(level.getBlockState(pos).is(block(pos.equals(floor) ? change[2] : change[3])),
                "calculate no-op lost mounted torch variant");
        }
    }

    private void lanterns() throws Exception {
        reset(GameType.SURVIVAL);
        List<BlockPos> targets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BlockPos pos = CENTER.offset(3 + i * 6, 0, 3);
            boolean hanging = i >= 2;
            put(hanging ? pos.above() : pos.below(), Blocks.STONE.defaultBlockState());
            put(pos, state("lantern", "hanging", Boolean.toString(hanging), "waterlogged", Boolean.toString(i % 2 == 1)));
            check(level.getBlockState(pos).canSurvive(level, pos), "lantern fixture must have actual floor/ceiling support");
            targets.add(pos);
        }
        select(CENTER, CENTER.offset(25, 0, 6));
        Map<BlockPos, BlockState> before = snapshot();
        player.getInventory().setItem(0, new ItemStack(block("soul_lantern").asItem(), targets.size()));
        check(command("blocktone type:lantern to:soul_lantern[100]") > 0, "lantern replacement rejected");
        for (BlockPos pos : targets) {
            sameState(pos, block("soul_lantern"), before.get(pos), "lantern floor/ceiling/waterlogging");
            check(level.getBlockState(pos).canSurvive(level, pos), "lantern support lost");
        }
        check(count(block("soul_lantern").asItem()) == 0 && count(block("lantern").asItem()) == 4, "lantern settlement incorrect");
    }

    private void mountedAndPositionalFamilies() throws Exception {
        reset(GameType.CREATIVE);
        List<BlockPos> buttons = new ArrayList<>();
        int i = 0;
        for (String face : List.of("floor", "wall", "ceiling")) for (Direction facing : HORIZONTAL) {
            BlockPos pos = CENTER.offset(3 + i % 6 * 6, 0, 3 + i / 6 * 6);
            BlockPos support = face.equals("floor") ? pos.below() : face.equals("ceiling") ? pos.above() : pos.relative(facing.getOpposite());
            put(support, Blocks.STONE.defaultBlockState());
            put(pos, state("oak_button", "face", face, "facing", facing.getSerializedName()));
            check(level.getBlockState(pos).canSurvive(level, pos), "button fixture support invalid");
            buttons.add(pos);
            i++;
        }
        select(CENTER, CENTER.offset(40, 0, 12));
        Map<BlockPos, BlockState> before = snapshot();
        check(command("blocktone type:button to:spruce_button[100]") > 0, "button category rejected");
        for (BlockPos pos : buttons) sameState(pos, block("spruce_button"), before.get(pos), "button mounting/facing");

        for (Direction.Axis axis : Direction.Axis.values()) {
            family("chains", "iron_chain", "copper_chain", "axis", axis.getSerializedName(), "waterlogged", "true");
            family("pillar", "quartz_pillar", "purpur_pillar", "axis", axis.getSerializedName());
        }
        for (Direction facing : HORIZONTAL) {
            reset(GameType.CREATIVE);
            BlockPos pos = CENTER.offset(3, 0, 3);
            put(pos.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
            put(pos, state("ladder", "facing", facing.getSerializedName(), "waterlogged", "true"));
            select(pos, pos);
            BlockState old = level.getBlockState(pos);
            check(old.canSurvive(level, pos), "ladder fixture support invalid");
            check(command("blocktone type:ladders to:ladder[100]") > 0, "ladder selector rejected");
            sameState(pos, block("ladder"), old, "ladder wall facing/waterlogging");
        }
        family("pressure_plate", "oak_pressure_plate", "spruce_pressure_plate");
        family("leaf", "oak_leaves", "birch_leaves", "distance", "7", "persistent", "true", "waterlogged", "true");
        family("carpet", "white_carpet", "red_carpet");
        family("carpets", "pale_moss_carpet", "pale_moss_carpet");
        for (String shape : List.of("north_south", "east_west", "north_east", "north_west", "south_east", "south_west")) {
            reset(GameType.CREATIVE);
            BlockPos pos = CENTER.offset(3, 0, 3);
            put(pos.below(), Blocks.STONE.defaultBlockState());
            put(pos, state("rail", "shape", shape, "waterlogged", "true"));
            select(pos, pos);
            BlockState old = level.getBlockState(pos);
            check(command("blocktone type:rail to:rail[100]") > 0, "rail selector rejected");
            sameState(pos, block("rail"), old, "ordinary rail straight/curved shape/waterlogging");
        }
    }

    private void family(String type, String source, String destination, String... properties) throws Exception {
        reset(GameType.CREATIVE);
        BlockPos pos = CENTER.offset(3, 0, 3);
        put(pos.below(), Blocks.STONE.defaultBlockState());
        put(pos, state(source, properties));
        check(level.getBlockState(pos).canSurvive(level, pos), "positional family fixture cannot survive: " + source);
        select(pos, pos);
        BlockState before = level.getBlockState(pos);
        check(command("blocktone type:" + type + " to:" + destination + "[100]") > 0, "positional family command rejected: " + type);
        sameState(pos, block(destination), before, type + " positional state");
    }

    private void atomicFailures() throws Exception {
        // A valid floor torch is staged before the incompatible wall torch, so a later rejection must roll back everything.
        reset(GameType.SURVIVAL);
        BlockPos floor = CENTER.offset(3, 0, 3);
        BlockPos wall = floor.east(6);
        put(floor.below(), Blocks.STONE.defaultBlockState());
        put(floor, block("torch").defaultBlockState());
        put(wall.north(), Blocks.STONE.defaultBlockState());
        put(wall, state("wall_torch", "facing", "south"));
        select(floor, wall);
        player.getInventory().setItem(0, new ItemStack(block("lantern").asItem(), 2));
        rejected("blocktone type:torches to:lantern[100]", "wall torch cannot become a floor/ceiling lantern");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(block("soul_torch").asItem(), 1));
        rejected("blocktone type:torches to:soul_torch[100]", "mounted torch inventory shortage");

        reset(GameType.SURVIVAL);
        BlockPos hanging = CENTER.offset(3, 0, 3);
        put(hanging.above(), Blocks.STONE.defaultBlockState());
        put(hanging, state("lantern", "hanging", "true"));
        select(hanging, hanging);
        player.getInventory().setItem(0, new ItemStack(block("torch").asItem(), 1));
        rejected("blocktone type:lanterns to:torch[100]", "hanging lantern cannot become floor torch");

        reset(GameType.SURVIVAL);
        BlockPos unsupported = CENTER.offset(3, 0, 3);
        put(unsupported, Blocks.STONE.defaultBlockState());
        select(unsupported, unsupported);
        player.getInventory().setItem(0, new ItemStack(block("torch").asItem(), 1));
        rejected("blocktone from:stone to:torch[100]", "staged unsupported destination must fail before consuming materials");

        reset(GameType.SURVIVAL);
        BlockPos luminousSupport = CENTER.offset(3, 0, 3);
        BlockPos unchangedTorch = luminousSupport.above();
        put(luminousSupport.below(), Blocks.STONE.defaultBlockState());
        put(luminousSupport, Blocks.GLOWSTONE.defaultBlockState());
        put(unchangedTorch, block("torch").defaultBlockState());
        check(level.getBlockState(unchangedTorch).canSurvive(level, unchangedTorch), "existing torch must survive on original glowstone support");
        select(luminousSupport, unchangedTorch);
        player.getInventory().setItem(0, new ItemStack(block("torch").asItem(), 1));
        rejected("blocktone type:lights to:torch[100]", "staged support removal beneath an unchanged selected torch");

        // Both standing variants use floor support, so this conversion is valid in either direction.
        reset(GameType.SURVIVAL);
        BlockPos standing = CENTER.offset(3, 0, 3);
        put(standing.below(), Blocks.STONE.defaultBlockState());
        put(standing, block("torch").defaultBlockState());
        select(standing, standing);
        player.getInventory().setItem(0, new ItemStack(block("lantern").asItem(), 1));
        check(command("blocktone type:torches to:lantern[100]") > 0, "floor torch to standing lantern conversion rejected");
        check(level.getBlockState(standing).equals(state("lantern", "hanging", "false"))
            && level.getBlockState(standing).canSurvive(level, standing), "converted standing lantern lost floor mounting/support");
        check(count(block("lantern").asItem()) == 0 && count(block("torch").asItem()) == 1,
            "torch to standing lantern conversion settled incorrect items");
        check(command("blocktone type:lanterns to:torch[100]") > 0, "standing lantern to floor torch conversion rejected");
        check(level.getBlockState(standing).is(block("torch")) && level.getBlockState(standing).canSurvive(level, standing),
            "converted floor torch lost floor support");
        check(count(block("lantern").asItem()) == 1 && count(block("torch").asItem()) == 0,
            "standing lantern to torch conversion settled incorrect items");
    }

    private void rejected(String specification, String context) throws Exception {
        Map<BlockPos, BlockState> before = snapshot();
        Map<Item, Integer> inventory = InventoryMaterials.count(player);
        check(command(specification) == 0, context + " must reject");
        check(snapshot().equals(before), context + " changed world before failure");
        check(InventoryMaterials.count(player).equals(inventory), context + " consumed/refunded inventory before failure");
    }

    private void sameState(BlockPos pos, Block expected, BlockState before, String context) {
        BlockState after = level.getBlockState(pos);
        check(after.is(expected), context + " did not replace expected material");
        check(after.getValues().toList().equals(before.getValues().toList()), context + " changed properties at " + pos);
    }

    private void preserveCommon(BlockState before, BlockState after, String context) {
        for (Property<?> property : before.getProperties()) {
            Property<?> target = after.getBlock().getStateDefinition().getProperty(property.getName());
            if (target != null) check(propertyValue(before, property).equals(propertyValue(after, target)), context + " changed " + property.getName());
        }
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private BlockState state(String name, String... properties) {
        BlockState result = block(name).defaultBlockState();
        for (int i = 0; i < properties.length; i += 2) {
            Property<?> property = result.getBlock().getStateDefinition().getProperty(properties[i]);
            if (property == null) throw new AssertionError("fixture has no property " + name + "." + properties[i]);
            result = with(result, property, properties[i + 1]);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return state.setValue(property, property.getValue(value).orElseThrow());
    }

    private Block block(String name) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + name));
        if (block == null || block == Blocks.AIR) throw new AssertionError("missing fixture block: " + name);
        return block;
    }

    private void reset(GameType mode) {
        player.setGameMode(mode);
        player.getInventory().clearContent();
        BlockToolsMod.STATES.remove(player.getUUID());
        BlockToolsMod.TUNNELS.remove(player.getUUID());
        for (int x = 0; x <= EXTENT; x++) for (int z = 0; z <= EXTENT; z++) for (int y = -2; y <= 2; y++) {
            put(CENTER.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    private void select(BlockPos first, BlockPos second) {
        BlockToolsMod.STATES.selection(player.getUUID()).set(level, 1, first);
        BlockToolsMod.STATES.selection(player.getUUID()).set(level, 2, second);
    }

    private void normalize(List<BlockPos> positions) {
        for (int pass = 0; pass < 2; pass++) for (BlockPos pos : positions) {
            put(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos));
        }
    }

    private Map<BlockPos, BlockState> snapshot() {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (int x = 0; x <= EXTENT; x++) for (int z = 0; z <= EXTENT; z++) for (int y = -2; y <= 2; y++) {
            BlockPos pos = CENTER.offset(x, y, z);
            states.put(pos, level.getBlockState(pos));
        }
        return states;
    }

    private void put(BlockPos pos, BlockState state) { level.setBlock(pos, state, Block.UPDATE_SKIP_ALL_SIDEEFFECTS); }
    private int command(String specification) throws Exception {
        return level.getServer().getCommands().getDispatcher().execute(specification, player.createCommandSourceStack());
    }
    private int count(Item item) { return InventoryMaterials.count(player).getOrDefault(item, 0); }
    private static String name(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toShortString(); }
    private void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); assertions++; }
}
