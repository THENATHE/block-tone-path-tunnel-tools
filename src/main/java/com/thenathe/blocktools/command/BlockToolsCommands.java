package com.thenathe.blocktools.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.selection.SelectionVisualization;
import com.thenathe.blocktools.selection.SelectionVisualization.Kind;
import com.thenathe.blocktools.material.BlockFilter;
import com.thenathe.blocktools.material.NamedSelectors;
import com.thenathe.blocktools.material.OverlayPalette;
import com.thenathe.blocktools.material.Palette;
import com.thenathe.blocktools.material.PathSelectors;
import com.thenathe.blocktools.material.ShuffleSelector;
import com.thenathe.blocktools.state.PlayerStateManager.PathState;
import com.thenathe.blocktools.state.PlayerStateManager.SelectionState;
import com.thenathe.blocktools.tone.ToneService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;

import java.util.regex.Pattern;

public final class BlockToolsCommands {
	private static final Pattern LEGACY_PALETTE_ENTRY = Pattern.compile("^.+:\\d+$");

	private BlockToolsCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("blocktone")
			.executes(BlockToolsCommands::toneStatus)
			.then(Commands.literal("help")
				.executes(context -> toneHelp(context, false))
				.then(Commands.literal("verbose").executes(context -> toneHelp(context, true))))
			.then(Commands.literal("visualize")
				.executes(context -> SelectionVisualization.command(context, Kind.SELECTION, true))
				.then(Commands.literal("off").executes(context -> SelectionVisualization.command(context, Kind.SELECTION, false))))
			.then(Commands.literal("clear").executes(BlockToolsCommands::clearSelection))
			.then(Commands.literal("shuffle")
				.then(Commands.argument("shuffle_spec", StringArgumentType.greedyString())
					.suggests((context, builder) -> BlockSuggestions.shuffle(builder))
					.executes(BlockToolsCommands::shuffleTone)))
			.then(Commands.literal("calc")
				.then(Commands.literal("apply")
					.then(Commands.argument("calc_spec", StringArgumentType.greedyString())
						.suggests((context, builder) -> BlockSuggestions.calc(builder))
						.executes(context -> calculateTone(context, true))))
				.then(Commands.argument("calc_spec", StringArgumentType.greedyString())
					.suggests((context, builder) -> BlockSuggestions.calc(builder))
					.executes(context -> calculateTone(context, false))))
			.then(Commands.argument("spec", StringArgumentType.greedyString())
				.suggests((context, builder) -> BlockSuggestions.tone(builder))
				.executes(BlockToolsCommands::tone)));

		dispatcher.register(Commands.literal("pathtool")
			.executes(BlockToolsCommands::pathStatus)
			.then(Commands.literal("help")
				.executes(context -> pathHelp(context, false))
				.then(Commands.literal("verbose").executes(context -> pathHelp(context, true))))
			.then(Commands.literal("visualize")
				.executes(context -> SelectionVisualization.command(context, Kind.PATH, true))
				.then(Commands.literal("off").executes(context -> SelectionVisualization.command(context, Kind.PATH, false))))
			.then(Commands.literal("status").executes(BlockToolsCommands::pathStatus))
			.then(Commands.literal("on").executes(context -> pathEnabled(context, true)))
			.then(Commands.literal("off").executes(context -> pathEnabled(context, false)))
			.then(Commands.literal("disarm").executes(BlockToolsCommands::pathDisarm))
			.then(Commands.literal("width")
				.then(Commands.argument("blocks", IntegerArgumentType.integer(1, 5)).executes(BlockToolsCommands::pathWidth)))
			.then(Commands.argument("spec", StringArgumentType.greedyString())
				.suggests((context, builder) -> BlockSuggestions.path(builder))
				.executes(BlockToolsCommands::configurePath)));

        dispatcher.register(Commands.literal("blocktools")
            .executes(context -> {
                if (!isAdmin(context.getSource())) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    net.minecraft.network.chat.Component.literal("Administrator permission is required for Block Tools help.")).create();
                return generalHelp(context, false);
            })
            .then(Commands.literal("auto").executes(context -> spriteMode(context, com.thenathe.blocktools.sprite.SpritePreferences.Mode.AUTO)))
            .then(Commands.literal("16x16").executes(context -> spriteMode(context, com.thenathe.blocktools.sprite.SpritePreferences.Mode.LOW)))
            .then(Commands.literal("32x32").executes(context -> spriteMode(context, com.thenathe.blocktools.sprite.SpritePreferences.Mode.HIGH)))
            .then(Commands.literal("help").requires(BlockToolsCommands::isAdmin)
                .executes(context -> generalHelp(context, false))
                .then(Commands.literal("verbose").executes(context -> generalHelp(context, true))))
            .then(Commands.literal("tools").requires(BlockToolsCommands::isAdmin).executes(BlockToolsCommands::tools))
            .then(Commands.literal("reload").requires(BlockToolsCommands::isAdmin).executes(BlockToolsCommands::reload)));
	}

    private static boolean isAdmin(CommandSourceStack source) {
        return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN);
    }
    private static int spriteMode(CommandContext<CommandSourceStack> context, com.thenathe.blocktools.sprite.SpritePreferences.Mode mode) {
        var player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(BlockToolsMod.error("Only a player can choose personal tool sprites."));
            return 0;
        }
        try {
            com.thenathe.blocktools.sprite.SpritePreferences.select(player, mode);
            return generalSuccess(context, com.thenathe.blocktools.sprite.SpritePreferences.description(player.getUUID())
                + (com.thenathe.blocktools.compat.PolymerTools.active() ? "" : " Custom Polymer tools are not active."));
        } catch (java.io.IOException failure) {
            BlockToolsMod.LOGGER.error("Could not save sprite preference", failure);
            context.getSource().sendFailure(BlockToolsMod.error("Could not save your sprite preference. Check the server log."));
            return 0;
        }
    }

	private static int shuffleTone(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return toneFail(context, "Only a player can shuffle a selection.");
		String specification = StringArgumentType.getString(context, "shuffle_spec").trim();
		try {
			ShuffleSelector selector = ShuffleSelector.parse(specification);
			return sendResult(context, ToneService.shuffle(player, selector.from()));
		} catch (IllegalArgumentException exception) {
			return toneFail(context, exception.getMessage());
		}
	}

	private static int calculateTone(final CommandContext<CommandSourceStack> context, final boolean apply) {
		ServerPlayer player = player(context);
		if (player == null) return toneFail(context, "Only a player can calculate a selection replacement.");
		String specification = StringArgumentType.getString(context, "calc_spec").trim();
		try {
			NamedSelectors selectors = NamedSelectors.parse(specification);
			OverlayPalette materials = OverlayPalette.parse(selectors.to());
			BlockFilter filter = selectors.from() == null ? BlockFilter.all() : BlockFilter.parse(selectors.from());
			ToneService.Result result = ToneService.calculate(player, materials, filter, apply);
			return sendResult(context, result);
		} catch (IllegalArgumentException exception) {
			return toneFail(context, exception.getMessage());
		}
	}

	private static int tone(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return toneFail(context, "Only a player can tone a selection.");
		String specification = StringArgumentType.getString(context, "spec").trim();
		try {
			NamedSelectors selectors = NamedSelectors.parse(specification);
			BlockFilter filter = selectors.from() == null ? BlockFilter.all() : BlockFilter.parse(selectors.from());
			return isLegacyPalette(selectors.to())
				? sendResult(context, ToneService.apply(player, Palette.parseTone(selectors.to()), filter))
				: sendResult(context, ToneService.applyOverlay(player, OverlayPalette.parse(selectors.to()), filter));
		} catch (IllegalArgumentException exception) {
			return toneFail(context, exception.getMessage());
		}
	}

	private static boolean isLegacyPalette(final String input) {
		for (String entry : input.split(",", -1)) {
			if (!LEGACY_PALETTE_ENTRY.matcher(entry.trim()).matches()) return false;
		}
		return true;
	}

	private static int configurePath(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return pathFail(context, "Only a player can configure the path tool.");
		String specification = StringArgumentType.getString(context, "spec").trim();

		try {
			PathSelectors selectors = PathSelectors.parse(specification);
			Palette palette = Palette.parseBracketed(selectors.to(), true, false);
			Palette stairs = selectors.stairs() == null ? null : Palette.parseBracketed(selectors.stairs(), false, true);
			BlockFilter sourceFilter = selectors.from() == null ? BlockFilter.all() : BlockFilter.parse(selectors.from());
			PathState state = BlockToolsMod.STATES.path(player.getUUID());
			state.palette = palette;
			state.stairPalette = stairs;
			state.sourceFilter = sourceFilter;
			state.stairsEnabled = stairs != null;
			state.enabled = true;
			BlockToolsMod.activatePath(player.getUUID());
			state.resetTrail();
			SelectionVisualization.showPath(player);
			return pathSuccess(context, "Path Tool armed; Tunnel Tool paused with its settings retained."
				+ "\n  Materials: " + palette.canonical()
				+ "\n  From: " + sourceFilter.canonical()
				+ "\n  Stairs: " + (stairs == null ? "off" : stairs.canonical())
				+ "\n  Width: " + state.width
				+ "\n  Hold " + itemName(BlockToolsMod.CONFIG.pathItem()) + " and walk.");
		} catch (IllegalArgumentException exception) {
			return pathFail(context, exception.getMessage());
		}
	}

	private static int pathWidth(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return pathFail(context, "Only a player can set path width.");
		PathState state = BlockToolsMod.STATES.path(player.getUUID());
		state.width = IntegerArgumentType.getInteger(context, "blocks");
		state.resetTrail();
		SelectionVisualization.showPath(player);
		return pathSuccess(context, "Path width set to " + state.width + " block(s), including the block under you.");
	}

	private static int pathEnabled(final CommandContext<CommandSourceStack> context, final boolean enabled) {
		ServerPlayer player = player(context);
		if (player == null) return pathFail(context, "Only a player can use the path tool.");
		PathState state = BlockToolsMod.STATES.path(player.getUUID());
		if (enabled && state.palette == null) return pathFail(context, "Configure materials with `/pathtool to:<palette>` first.");
		state.enabled = enabled;
		if (enabled) BlockToolsMod.activatePath(player.getUUID());
		state.resetTrail();
		SelectionVisualization.showPath(player);
		return pathSuccess(context, "Path Tool " + (enabled ? "enabled" : "disabled") + ".");
	}

	private static int pathDisarm(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return pathFail(context, "Only a player can disarm the path tool.");
		PathState state = BlockToolsMod.STATES.path(player.getUUID());
		state.enabled = false;
		SelectionVisualization.hidePath(player.getUUID());
		state.palette = null;
		state.stairPalette = null;
		state.sourceFilter = BlockFilter.all();
		state.stairsEnabled = false;
		state.resetTrail();
		return pathSuccess(context, "Path Tool disarmed and its material palettes cleared. Width remains " + state.width + ".");
	}

	private static int pathStatus(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return pathFail(context, "Only a player has path-tool settings.");
		PathState state = BlockToolsMod.STATES.path(player.getUUID());
		return pathSuccess(context, "Path Tool status"
			+ "\n  Tool: " + itemName(BlockToolsMod.CONFIG.pathItem())
			+ "\n  State: " + (state.enabled ? "ON" : "OFF")
			+ "\n  Width: " + state.width
			+ "\n  Materials: " + (state.palette == null ? "not configured" : state.palette.canonical())
			+ "\n  From: " + state.sourceFilter.canonical()
			+ "\n  Stairs: " + (state.stairsEnabled && state.stairPalette != null ? state.stairPalette.canonical() : "off"));
	}

	private static int toneStatus(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return toneFail(context, "Only a player has a block selection.");
		SelectionState selection = BlockToolsMod.STATES.selection(player.getUUID());
		String pointOne = selection.pointOne() == null ? "unset" : coordinates(selection.pointOne());
		String pointTwo = selection.pointTwo() == null ? "unset" : coordinates(selection.pointTwo());
		return toneSuccess(context, "Selection status"
			+ "\n  Point 1: " + pointOne
			+ "\n  Point 2: " + pointTwo
			+ "\n  Tool: " + itemName(BlockToolsMod.CONFIG.selectionItem())
			+ "\n  Use `/blocktone help` for commands.");
	}

	private static int clearSelection(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return toneFail(context, "Only a player has a block selection.");
		BlockToolsMod.STATES.selection(player.getUUID()).clear();
		SelectionVisualization.hide(player.getUUID());
		player.sendSystemMessage(net.minecraft.network.chat.Component.empty(), true);
		return toneSuccess(context, "Selection cleared.");
	}

	private static int toneHelp(final CommandContext<CommandSourceStack> context, final boolean verbose) {
		toneHelpLine(context, "Block toning: left-click Point 1 and right-click Point 2 with "
			+ itemName(BlockToolsMod.CONFIG.selectionItem()) + ", then run `/blocktone to:<materials> [from:<blocks>]`.");
		toneHelpLine(context, "`/blocktone visualize [off]` — private selection outline; green Point 1, purple Point 2, dimensions above the hotbar.");
		toneHelpLine(context, "Even automatic mix: `/blocktone to:bricks,granite,polished_granite`");
		toneHelpLine(context, "Additive remainder: `/blocktone to:bricks[20],granite[],polished_granite[]`");
		toneHelpLine(context, "Filtered: `/blocktone from:stone,diorite to:bricks[20],granite[]`");
		toneHelpLine(context, "Clear matching blocks: `/blocktone from:stone to:air` — no air items are needed.");
		toneHelpLine(context, "Keep Grass Blocks and Dirt: `/blocktone from:not(grass_block,dirt) to:air` clears other eligible non-air blocks.");
		toneHelpLine(context, "Exclude shapes: `/blocktone type:not(stairs,slabs) to:air` keeps stairs and slabs.");
		toneHelpLine(context, "Roof palette: `/blocktone type:stairs to:stone_brick_stairs[70],mossy_stone_brick_stairs[30]` preserves stair shape.");
		toneHelpLine(context, "Structural types: stairs, slabs, trapdoors, fences, walls, bars, panes, fence_gates, railings, logs, pillars.");
		toneHelpLine(context, "Other types: buttons, pressure_plates, rails, chains, ladders, leaves, carpets, torches, lanterns, light_sources, blocks, liquids, air.");
		toneHelpLine(context, "Use `type:<name>` or a comma union; singular names work. `type:railings` selects fences, walls, and bars together.");
		toneHelpLine(context, "Torch recolor: `/blocktone type:torches to:soul_torch[100]` keeps floor/wall mounting and wall facing.");
		toneHelpLine(context, "Lantern recolor: `/blocktone type:lanterns to:soul_lantern[100]` keeps hanging/standing and waterlogging.");
		toneHelpLine(context, "`type:light_sources` selects light-emitting families, including currently unlit states; wall torches need torch destinations, hanging lanterns need lanterns.");
		toneHelpLine(context, "Calc preview: `/blocktone calc to:bricks,granite,polished_granite`");
		toneHelpLine(context, "Calc filtered: `/blocktone calc from:grass_block to:bricks,granite,polished_granite`");
		toneHelpLine(context, "Apply a preview: `/blocktone calc apply to:bricks,granite,polished_granite`");
		toneHelpLine(context, "Rerandomize existing blocks: `/blocktone shuffle from:bricks,granite,polished_granite`");
		if (verbose) {
			toneHelpLine(context, "The corners define an inclusive cuboid. Sharing an axis naturally creates a flat wall or floor selection.");
			toneHelpLine(context, "Left-click always updates Point 1; right-click always updates Point 2. Reselecting one point preserves the other.");
			toneHelpLine(context, "Selector clicks cannot damage or use the clicked block.");
			toneHelpLine(context, "to: is always required. from: is optional and defaults to from:any, which targets every safe non-air block.");
			toneHelpLine(context, "Use from:any+air to include air, from:air for only air, or a comma-separated block list that may contain air.");
			toneHelpLine(context, "A bare material and material[] are automatic. Automatic materials split the percentage left after explicit material[percent] entries.");
			toneHelpLine(context, "If no automatic material is present and explicit percentages total under 100, every unallocated position retains its existing block.");
			toneHelpLine(context, "from: (or type:) and to: are position-independent, for example `/blocktone to:bricks[20],granite[] from:stone`.");
			toneHelpLine(context, "Legacy material:percent palettes totaling exactly 100 remain supported inside regular to:, including air:100; calc uses bare/bracket entries.");
			toneHelpLine(context, "Air destinations: to:air, to:minecraft:air, to:air[100], and to:air[] clear blocks; type:air instead selects empty sources.");
			toneHelpLine(context, "Mixed clearing: to:bricks[70],air[30] places 70% Bricks and clears 30%; to:air[20] retains the remaining positions.");
			toneHelpLine(context, "from:not(grass_block,dirt) excludes those exact IDs. grass is invalid; add coarse_dirt/rooted_dirt separately to preserve them too.");
			toneHelpLine(context, "Category exclusion: type:not(stairs,slabs) or from:not(type:stairs,slabs). Negation skips air and does not implicitly opt into pure liquids.");
			toneHelpLine(context, "Use one non-empty not(...) block or type list; nested not, extra parentheses, and mixed block/type lists are rejected.");
			toneHelpLine(context, "Source air is skipped unless explicitly included. Pure liquids require type:liquids or an explicit water/lava source ID. Block entities, multipart blocks, and unbreakable blocks are skipped.");
			toneHelpLine(context, "type: replaces from:; comma-separated types match any listed category. blocks means full collision cubes, excluding slabs and other shaped types.");
			toneHelpLine(context, "Use type: in Blocktone toning and calc preview/apply. Nested from:type:stairs also works; shuffle and Path Tool use the nested from:type: form.");
			toneHelpLine(context, "bars includes iron and all weathered/waxed copper bars. panes now means glass panes only; type:panes,bars retains the older combined match.");
			toneHelpLine(context, "railings is exactly fences,walls,bars, excluding panes and gates. Aliases: railing and fences_walls_bars. lights, light, and light_source mean light_sources.");
			toneHelpLine(context, "Logs use the logs tag; pillars include axis blocks such as logs, hay, and quartz. Singular aliases include pressure_plate and leaf.");
			toneHelpLine(context, "light_sources includes unlit redstone torches, lamps, candles, and copper bulbs. Block entities such as campfires/beacons and pure lava still follow safety exclusions.");
			toneHelpLine(context, "Replacements copy supported states: stair shape/facing/half, slab top/bottom/double, trapdoor facing/half/open/powered, connections, axis, attachment, and waterlogging.");
			toneHelpLine(context, "Double slabs cost/refund two slab items. Use matching destination families to retain geometry; converting a fence to bars adopts bar geometry.");
			toneHelpLine(context, "Use torch, soul_torch, or redstone_torch palette items; wall counterparts are chosen automatically. Lanterns retain hanging/standing.");
			toneHelpLine(context, "Wall torch to lantern and hanging lantern to torch reject the whole operation. Floor torch/standing lantern conversions need valid support.");
			toneHelpLine(context, "Planned replacements and selected survivors are checked for support before spending items. Unsupported placement rejects all edits, including clearing.");
			toneHelpLine(context, "Clearing waterlogged blocks removes their water too; nearby water may flow back. Removing supports can affect attached neighbors through vanilla physics.");
			toneHelpLine(context, "Exact material totals are planned first. Loose inventory and top-level shulker contents are counted; shortage means nothing changes.");
			toneHelpLine(context, "Calc fits as many carried automatic materials as possible, proportionally if oversupplied, then fills capacity with requested blocks already in the selection.");
			toneHelpLine(context, "Calc preserves scarce placed accents and trims the dominant existing material first. Unfilled capacity retains existing blocks unless automatic air is requested.");
			toneHelpLine(context, "Calc to:bricks[],air[] fits requested carried/existing Bricks first, then clears the remaining capacity with free air. Explicit air percentages stay fixed.");
			toneHelpLine(context, "Explicit calc percentages reserve exact quotas; [] entries use the remaining capacity. Preview changes nothing; only calc apply executes.");
			toneHelpLine(context, "Replaced/cleared recoverable blocks fund required materials first; surplus returns to inventory/shulkers and overflow drops at the player. Double slabs return two items.");
			toneHelpLine(context, "Air destinations require no items. Missing materials reject the whole mixed palette without edits/refunds. Path Tool rejects air; Tunnel Tool keeps separate rules.");
			toneHelpLine(context, "Shuffle permutes whole states, including orientations, between positions; it does not keep roof shape at each position. It uses no inventory and needs two different states.");
			toneHelpLine(context, "Successful Blocktone operations retain both selected corners; clear them explicitly when you are finished.");
			toneHelpLine(context, "Block names autocomplete from the live registry. Use canonical IDs such as bricks and granite; invalid spellings are rejected.");
			toneHelpLine(context, "Sneak-left/right-click with the selector, or use `/blocktone clear`, to discard the current selection.");
		}
		return 1;
	}

	private static int pathHelp(final CommandContext<CommandSourceStack> context, final boolean verbose) {
		pathHelpLine(context, "Path Tool: `/pathtool to:material[percent],... [stairs:stairmaterial[percent],...]`");
		pathHelpLine(context, "Example: `/pathtool to:dirt_path[60],cobblestone[40] stairs:cobblestone_stairs[100]`");
		pathHelpLine(context, "Filtered terrain: `/pathtool from:grass_block,dirt to:dirt_path[60],cobblestone[40] stairs:cobblestone_stairs[100]`");
		pathHelpLine(context, "to: is required. from:, to:, and stairs: may appear in any order; omitted from: defaults to from:any.");
		pathHelpLine(context, "Then hold " + itemName(BlockToolsMod.CONFIG.pathItem()) + " and walk. Select Blocktone corners with " + itemName(BlockToolsMod.CONFIG.selectionItem()) + ".");
		pathHelpLine(context, "`/pathtool visualize [off]` — private three-stripe terrain footprint; works while paused and does not place blocks.");
		pathHelpLine(context, "Set width with `/pathtool width <1-5>`.");
		if (verbose) {
			pathHelpLine(context, "Category sources use nested syntax, for example from:type:blocks. Standalone type: is supported only by Blocktone toning/calc.");
			pathHelpLine(context, "Source exclusion is supported, for example from:not(grass_block,dirt) or from:not(type:stairs,slabs). Path terrain rules still apply; air destinations are rejected.");
			pathHelpLine(context, "Each width lane independently follows the solid terrain beneath you; replaceable short/tall grass and similar vegetation do not count as terrain.");
			pathHelpLine(context, "Vegetation directly over a changed path cell is cleared. Leaves, water, unsupported lanes, block entities, and unbreakable blocks are not used as path ground.");
			pathHelpLine(context, "dirt_path converts vanilla shovel-flattenable dirt or grass for free; otherwise its share falls back to other palette blocks.");
			pathHelpLine(context, "When stairs are configured, one-block rises and drops use weighted stairs. The stair occupies the upper terrain block and its high side always faces uphill.");
			pathHelpLine(context, "Every stripe atomically consumes from loose inventory and shulkers before placement. Missing materials pause placement safely.");
			pathHelpLine(context, "Use `/pathtool off` to pause while preserving its palette, `/pathtool on` to resume, or `/pathtool disarm` to clear its palettes.");
			pathHelpLine(context, "Use `/pathtool status` to inspect it. Releasing the trigger item prevents paths from connecting across gaps.");
		}
		return 1;
	}

	private static int generalHelp(final CommandContext<CommandSourceStack> context, final boolean verbose) {
		generalHelpLine(context, "/blocktools help, tools, and reload require administrator permission (operator level 3 or higher).");
		generalHelpLine(context, "`/blocktone help" + (verbose ? " verbose" : "") + "` — two-corner survival-safe replacement and toning.");
		generalHelpLine(context, "`/pathtool help" + (verbose ? " verbose" : "") + "` — terrain-following survival paths.");
		generalHelpLine(context, "`/tunneltool help` — tunnel construction and rails. Path and Tunnel Tools pause each other when armed.");
        generalHelpLine(context, "Personal sprites: `/blocktools auto`, `/blocktools 16x16`, or `/blocktools 32x32`. Available to every player.");
		generalHelpLine(context, "`/blocktools tools` — get the three custom tools when Polymer integration is enabled.");
		generalHelpLine(context, com.thenathe.blocktools.compat.PolymerTools.status());
		generalHelpLine(context, "`/blocktools reload` — reload the server JSON configuration.");
		if (verbose) {
			generalHelpLine(context, "This mod is server-authoritative with optional Polymer tool models. Dedicated-server players need no client mod; accept the Polymer pack for custom tool sprites.");
			generalHelpLine(context, "Single-player runs the same logic in its integrated server and therefore needs Fabric API plus this JAR installed locally.");
			toneHelp(context, true);
			pathHelp(context, true);
		}
		return 1;
	}

	private static int tools(final CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) return generalSuccess(context, "Run /blocktools tools as a player.");
        if (!com.thenathe.blocktools.compat.PolymerTools.active()) {
            return generalSuccess(context, com.thenathe.blocktools.compat.PolymerTools.status()
                + " Hold " + itemName(BlockToolsMod.CONFIG.legacySelectionItem()) + " for selection, paths, and tunnels.");
        }
        for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[] {
                BlockToolsMod.CONFIG.selectionItem(), BlockToolsMod.CONFIG.pathItem(), BlockToolsMod.TUNNEL_CONFIG.toolItem() }) {
            var stack = new net.minecraft.world.item.ItemStack(item);
            if (!player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return generalSuccess(context, "Received Block Toning Tool, Path Tool, and Tunnel Tool.");
    }

    private static int reload(final CommandContext<CommandSourceStack> context) {
        boolean previousMode = com.thenathe.blocktools.compat.PolymerTools.active();
		BlockToolsMod.CONFIG.load();
		BlockToolsMod.TUNNEL_CONFIG.load();
		SelectionVisualization.clear();
		BlockToolsMod.STATES.resetTrails();
		BlockToolsMod.TUNNELS.resetTrails();
        if (previousMode != com.thenathe.blocktools.compat.PolymerTools.active()
                || com.thenathe.blocktools.compat.ToolRecipes.needsRetry(context.getSource().getServer())) {
            var source = context.getSource();
            com.thenathe.blocktools.compat.ToolRecipes.refresh(source.getServer()).whenCompleteAsync((ignored, failure) -> {
                if (failure == null) source.sendSuccess(() -> BlockToolsMod.message("Tool crafting recipes refreshed. "
                    + com.thenathe.blocktools.compat.PolymerTools.status()), false);
                else {
                    BlockToolsMod.LOGGER.error("Could not refresh tool crafting recipes after config reload", failure);
                    source.sendFailure(BlockToolsMod.error("Configuration loaded, but refreshing tool recipes failed. Check the server log and run /blocktools reload to retry."));
                }
            }, source.getServer());
            return generalSuccess(context, "Configuration loaded. Refreshing tool crafting recipes...");
        }
		return generalSuccess(context, "Configuration reloaded from " + BlockToolsMod.CONFIG.path() + " and " + BlockToolsMod.TUNNEL_CONFIG.path() + ". " + com.thenathe.blocktools.compat.PolymerTools.status());
	}

	private static void generalHelpLine(final CommandContext<CommandSourceStack> context, final String line) {
		context.getSource().sendSuccess(() -> BlockToolsMod.message(line), false);
	}

	private static void toneHelpLine(final CommandContext<CommandSourceStack> context, final String line) {
		context.getSource().sendSuccess(() -> BlockToolsMod.toneMessage(line), false);
	}

	private static void pathHelpLine(final CommandContext<CommandSourceStack> context, final String line) {
		context.getSource().sendSuccess(() -> BlockToolsMod.pathMessage(line), false);
	}

	private static ServerPlayer player(final CommandContext<CommandSourceStack> context) {
		return context.getSource().getPlayer();
	}

	private static int generalSuccess(final CommandContext<CommandSourceStack> context, final String message) {
		context.getSource().sendSuccess(() -> BlockToolsMod.message(message), false);
		return 1;
	}

	private static int toneSuccess(final CommandContext<CommandSourceStack> context, final String message) {
		context.getSource().sendSuccess(() -> BlockToolsMod.toneMessage(message), false);
		return 1;
	}

	private static int toneFail(final CommandContext<CommandSourceStack> context, final String message) {
		context.getSource().sendFailure(BlockToolsMod.toneError(message));
		return 0;
	}

	private static int pathSuccess(final CommandContext<CommandSourceStack> context, final String message) {
		context.getSource().sendSuccess(() -> BlockToolsMod.pathMessage(message), false);
		return 1;
	}

	private static int pathFail(final CommandContext<CommandSourceStack> context, final String message) {
		context.getSource().sendFailure(BlockToolsMod.pathError(message));
		return 0;
	}

	private static int sendResult(final CommandContext<CommandSourceStack> context, final ToneService.Result result) {
		if (result.success()) {
			context.getSource().sendSuccess(result::message, false);
			return 1;
		}
		context.getSource().sendFailure(result.message());
		return 0;
	}

	private static String itemName(final net.minecraft.world.item.Item item) {
		return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
	}

	private static String coordinates(final BlockPos pos) {
		return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
	}
}
