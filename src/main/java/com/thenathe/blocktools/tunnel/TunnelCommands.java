package com.thenathe.blocktools.tunnel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.selection.SelectionVisualization;
import com.thenathe.blocktools.selection.SelectionVisualization.Kind;
import com.thenathe.blocktools.tunnel.TunnelStateManager.TunnelState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TunnelCommands {
	private TunnelCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> node = dispatcher.register(Commands.literal("tunneltool")
			.executes(TunnelCommands::status)
			.then(Commands.literal("help")
				.executes(context -> help(context, false))
				.then(Commands.literal("verbose").executes(context -> help(context, true))))
			.then(Commands.literal("visualize")
				.executes(context -> SelectionVisualization.command(context, Kind.TUNNEL, true))
				.then(Commands.literal("off").executes(context -> SelectionVisualization.command(context, Kind.TUNNEL, false))))
			.then(Commands.literal("status").executes(TunnelCommands::status))
			.then(Commands.literal("on").executes(context -> enabled(context, true)))
			.then(Commands.literal("off").executes(context -> enabled(context, false)))
			.then(Commands.literal("disarm").executes(TunnelCommands::disarm))
			.then(Commands.literal("width")
				.then(Commands.argument("blocks", IntegerArgumentType.integer(1, 5)).executes(TunnelCommands::width)))
			.then(Commands.literal("height")
				.then(Commands.argument("blocks", IntegerArgumentType.integer(2, 5)).executes(TunnelCommands::height)))
			.then(Commands.argument("spec", StringArgumentType.greedyString())
				.suggests((context, builder) -> TunnelSuggestions.suggest(builder))
				.executes(TunnelCommands::configure)));
		dispatcher.register(Commands.literal("tunnel").executes(TunnelCommands::status).redirect(node));
	}

	private static int configure(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return 0;
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		try {
			TunnelSpec spec = TunnelSpec.parse(StringArgumentType.getString(context, "spec"), state.width, state.height);
			state.configure(spec);
			BlockToolsMod.activateTunnel(player.getUUID());
			SelectionVisualization.showTunnel(player);
			success(context, "Armed " + spec.width() + "×" + spec.height() + " tunnel construction mode with rails "
				+ spec.railMode().name().toLowerCase(java.util.Locale.ROOT) + ".");
			success(context, TunnelService.alignmentMessage(spec.width()));
			return success(context, "Path Tool paused; its settings are retained. Hold " + toolName() + " and walk through the clear tunnel interior.");
		} catch (IllegalArgumentException exception) {
			return fail(context, exception.getMessage());
		}
	}

	private static int width(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return 0;
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		int width = IntegerArgumentType.getInteger(context, "blocks");
		if (width < 2 && state.railMode != TunnelSpec.RailMode.OFF) {
			return fail(context, "Disarm or reconfigure with rail:off before setting width 1; rails require width 2–5.");
		}
		state.width = width;
		state.resetTrail();
		SelectionVisualization.showTunnel(player);
		success(context, "Tunnel width set to " + state.width + " clear block(s).");
		return success(context, TunnelService.alignmentMessage(state.width));
	}

	private static int height(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return 0;
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		state.height = IntegerArgumentType.getInteger(context, "blocks");
		state.resetTrail();
		SelectionVisualization.showTunnel(player);
		return success(context, "Tunnel height set to " + state.height + " clear block(s).");
	}

	private static int enabled(final CommandContext<CommandSourceStack> context, final boolean enabled) {
		ServerPlayer player = player(context);
		if (player == null) return 0;
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		if (enabled && !state.configured()) return fail(context, "Configure a lining mix or rail:only first.");
		state.enabled = enabled;
		if (enabled) BlockToolsMod.activateTunnel(player.getUUID());
		state.resetTrail();
		SelectionVisualization.showTunnel(player);
		if (enabled) success(context, TunnelService.alignmentMessage(state.width));
		return success(context, "Tunnel Tool " + (enabled ? "enabled." : "paused; settings retained."));
	}

	private static int disarm(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return 0;
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		state.disarm();
		SelectionVisualization.hideTunnel(player.getUUID());
		return success(context, "Tunnel Tool disabled and its lining/rail settings cleared; dimensions retained.");
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = player(context);
		if (player == null) return 0;
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		String materials = state.railMode == TunnelSpec.RailMode.ONLY ? "rail only"
			: state.walls == null ? "not configured"
			: "walls=" + state.walls.canonical() + ", floor=" + state.floor.canonical()
				+ ", ceiling=" + state.ceiling.canonical();
		success(context, "Status: " + (state.enabled ? "ON" : "OFF") + ", clear interior "
			+ state.width + "×" + state.height + ", rail=" + state.railMode.name().toLowerCase(java.util.Locale.ROOT)
			+ ", " + materials + ".");
		success(context, TunnelService.alignmentMessage(state.width));
		return success(context, "Tool item: " + toolName() + "; powered rail interval: "
			+ BlockToolsMod.TUNNEL_CONFIG.config().poweredRailInterval + " blocks.");
	}

	private static int help(final CommandContext<CommandSourceStack> context, final boolean verbose) {
		success(context, "Uniform lining: `/tunneltool width:2 height:3 to:stone_bricks[70],andesite[30] rail:off`");
		success(context, "Separate mixes: `/tunneltool width:3 height:4 walls:stone_bricks[100] floor:deepslate_tiles[100] ceiling:smooth_stone[100] rail:on`");
		success(context, "Open sides/top: `walls:air[100] floor:stone_bricks[100] ceiling:air[100]`. Walls and ceiling also accept weighted air mixes; floors stay solid.");
		success(context, "Rail only: `/tunneltool width:2 height:3 rail:only`");
		success(context, "`/tunneltool visualize [off]` — private interior, lining and rail-alignment preview; works while paused.");
		success(context, "Clauses may appear in any order. Width is 1–5; height is 2–5; every mix must total 100.");
		success(context, "Rail modes require width 2–5 so powered rails can use a real torch in the adjacent lane.");
		success(context, "`/tunneltool status|on|off|disarm`, `/tunneltool width <1-5>`, and `/tunneltool height <2-5>` are also available.");
		success(context, "Even width: stand LEFT-center and rail uses RIGHT-center. Odd width: stand/rail in MIDDLE; width 1 is lining-only.");
		success(context, "To start a new line: put the tool away from both hands, rotate, then pull it back out.");
		if (verbose) {
			success(context, "Keep the interior clear and stand on a solid floor block. Missing walls, ceiling, and the rest of the floor are built from your palettes. The tool never excavates the walkable interior.");
			success(context, "Face along the intended line before holding " + toolName() + ". That starting block line, elevation, and cardinal facing stay locked. Stepping off the line or facing another direction pauses placement.");
			success(context, "Survival uses loose inventory and carried shulkers. Air in walls/ceiling clears safe shell blocks without material cost. Replaced or cleared shell blocks are not refunded.");
			success(context, "rail:on builds the shell and rails; rail:only leaves the shell unchanged and requires existing rail and torch support. Powered rails receive real redstone torches every "
				+ BlockToolsMod.TUNNEL_CONFIG.config().poweredRailInterval + " blocks.");
			success(context, "Native build permissions apply. When Precious Stones is installed, build access is required and structural field/owned blocks are protected.");
		}
		return 1;
	}

	private static ServerPlayer player(final CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) context.getSource().sendFailure(error("This command must be run by a player."));
		return player;
	}

	private static String toolName() {
		return new net.minecraft.world.item.ItemStack(BlockToolsMod.TUNNEL_CONFIG.toolItem()).getHoverName().getString();
	}

	private static int success(final CommandContext<CommandSourceStack> context, final String text) {
		context.getSource().sendSuccess(() -> message(text), false);
		return 1;
	}

	private static int fail(final CommandContext<CommandSourceStack> context, final String text) {
		context.getSource().sendFailure(error(text));
		return 0;
	}

	public static Component message(final String text) {
		return com.thenathe.blocktools.ChatStyling.tagged("Tunnel Tool", ChatFormatting.DARK_AQUA, text, ChatFormatting.WHITE);
	}

	public static Component error(final String text) {
		return com.thenathe.blocktools.ChatStyling.tagged("Tunnel Tool", ChatFormatting.DARK_AQUA, text, ChatFormatting.RED);
	}
}
