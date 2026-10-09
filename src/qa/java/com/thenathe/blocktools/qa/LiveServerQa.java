package com.thenathe.blocktools.qa;

import com.mojang.authlib.GameProfile;
import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.compat.BuildPermissions;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import com.thenathe.blocktools.path.PathService;
import com.thenathe.blocktools.state.PlayerStateManager.PathState;
import com.thenathe.blocktools.tunnel.TunnelService;
import com.thenathe.blocktools.tunnel.TunnelSpec;
import com.thenathe.blocktools.tunnel.TunnelStateManager.TunnelState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Loaded only by qaJar in a disposable server; never included in production artifacts. */
public final class LiveServerQa implements ModInitializer {
	private final StringBuilder report = new StringBuilder();
	private int assertions;
	private int failures;
	private ServerPlayer player;
	private ServerLevel level;
	private MinecraftServer server;
	private Method placeSlice;
	private static final BlockPos CENTER = new BlockPos(256, 100, 256);

	@Override
	public void onInitialize() {
		if (!Boolean.getBoolean("blocktools.qa")) throw new IllegalStateException("QA mod requires -Dblocktools.qa=true and a disposable world");
		ServerLifecycleEvents.SERVER_STARTED.register(running -> running.execute(() -> run(running)));
	}

	private void run(final MinecraftServer running) {
		server = running;
		try {
			level = server.overworld();
			GameProfile profile = new GameProfile(UUID.fromString("22454998-84ca-4e4e-bbdd-a3b4d7fbab18"), "BlockToolsQA");
			player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
			player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
				CommonListenerCookie.createInitial(profile, false));
            // This synthetic connection skips Fabric's real login listener, which normally sets the profile.
            ((net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider) player.connection).getPacketContext()
                .set(net.fabricmc.fabric.impl.networking.context.PacketContextImpl.GAME_PROFILE, profile);
			player.setPos(CENTER.getX() + .5, CENTER.getY(), CENTER.getZ() + .5);
			placeSlice = TunnelService.class.getDeclaredMethod("placeSlice", ServerPlayer.class, TunnelState.class, BlockPos.class, Direction.class);
			placeSlice.setAccessible(true);
			for (int x = 15; x <= 17; x++) for (int z = 15; z <= 18; z++) level.getChunk(x, z);
			caseOf("survival shortage leaves world and inventory unchanged", this::shortage);
			caseOf("survival consumes exact shell materials from shulker", this::shulker);
			caseOf("obstructions and unsafe shells cause no partial edits", this::unsafeSurfaces);
			caseOf("creative lining requires no inventory", this::creative);
			caseOf("open-air shells fill around a supported walking lane", this::openAirShells);
			caseOf("open-air shortages missing footing and unsafe shells remain atomic", this::openAirSafety);
			caseOf("open-air rail lining builds support and rail-only rejects missing support", this::openAirRails);
			caseOf("air wall and ceiling palettes clear safe shells without material cost", this::airPalettes);
			caseOf("native adventure spectator unloaded bounds and border checks", this::permissions);
			caseOf("straight survival rail runs preserve shell and power boosters", this::rails);
			caseOf("special rails are preserved", this::specialRails);
			caseOf("commands aliases pause disarm and mutually exclusive modes", this::commands);
			caseOf("blocktone modifies selection and charges survival inventory", this::tone);
			caseOf("blocktone type selectors preserve natural roof shapes and connected states", () -> assertions += LiveToneShapeQa.run(player, level));
			caseOf("blocktone expanded families and mounted lights preserve states and atomic inventory", () -> assertions += LiveExpandedTypesQa.run(player, level));
			caseOf("blocktone air destinations preserve quotas refunds guards and atomic failures", () -> assertions += LiveAirDestinationQa.run(player, level));
			caseOf("path tool applies a real survival stripe with exact inventory charge", this::path);
			caseOf("path dirt conversion and shortage regressions", () -> assertions += LivePathQa.run(player, level));
			caseOf("held-tool movement catch-up backtracking and reset safety", () -> assertions += LiveMovementQa.run(player, level));
			caseOf("selection path and tunnel visual interfaces", () -> assertions += LiveSelectionQa.run(player, level));
			caseOf("malformed and future settings preserved byte for byte", this::configPreservation);
			caseOf("optional Polymer roles migrations and reload", () -> assertions += LiveOptionalToolsQa.run(player));
			caseOf("standalone copy has no Precious Stones claim engine", () -> {
				if (!FabricLoader.getInstance().isModLoaded("preciousstones")) {
					check(server.getCommands().getDispatcher().getRoot().getChild("ps") == null, "no ps command without optional Precious Stones mod");
				} else check(BuildPermissions.canChange(player, CENTER), "optional Precious Stones permits native edits outside claims");
			});
			if (FabricLoader.getInstance().isModLoaded("preciousstones")) {
				caseOf("optional Precious Stones real claims and ownership restrictions", () -> assertions += LiveClaimsQa.run(player, level));
			}
		} catch (Throwable failure) {
			failures++;
			appendFailure("harness setup", failure);
		} finally {
			report.append("RESULT: ").append(failures == 0 ? "PASS" : "FAIL").append("; assertions=").append(assertions)
				.append("; failed_cases=").append(failures).append('\n');
			try {
				Files.writeString(Path.of(System.getProperty("blocktools.qa.result", "qa-result.txt")), report.toString());
			} catch (Exception failure) {
				BlockToolsMod.LOGGER.error("Could not write live QA report", failure);
			}
			BlockToolsMod.LOGGER.info("Live server QA:\n{}", report);
			server.halt(false);
		}
	}

	private void shortage() throws Exception {
		reset(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 9));
		Map<BlockPos, BlockState> before = snapshot(CENTER);
		check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "short slice is rejected");
		check(before.equals(snapshot(CENTER)), "shortage leaves all surrounding blocks unchanged");
		check(count(Items.BRICKS) == 9, "shortage charges no materials");
	}

	private void shulker() throws Exception {
		reset(GameType.SURVIVAL);
		NonNullList<ItemStack> slots = NonNullList.withSize(27, ItemStack.EMPTY);
		slots.set(0, new ItemStack(Items.BRICKS, 64));
		ItemStack box = new ItemStack(Items.SHULKER_BOX);
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
		player.getInventory().setItem(0, box);
		check(slice(CENTER, "width:2 height:3 to:bricks[100]"), "shulker-funded slice succeeds");
		check(count(Items.BRICKS) == 54, "ten shell blocks deducted from shulker");
		check(snapshot(CENTER).values().stream().filter(state -> state.is(Blocks.BRICKS)).count() == 10, "all ten shell blocks lined");
		check(slice(CENTER, "width:2 height:3 to:bricks[100]"), "repeated slice succeeds");
		check(count(Items.BRICKS) == 54, "unchanged shell is not charged again");
	}

	private void unsafeSurfaces() throws Exception {
		for (Block obstacle : new Block[]{Blocks.STONE, Blocks.WATER}) {
			reset(GameType.CREATIVE);
			put(CENTER.above(), obstacle);
			Map<BlockPos, BlockState> before = snapshot(CENTER);
			check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "obstructed/wet interior refused");
			check(before.equals(snapshot(CENTER)), "obstructed/wet interior leaves world unchanged");
		}
		for (Block unsafe : new Block[]{Blocks.CHEST, Blocks.BEDROCK, Blocks.AIR, Blocks.WATER}) {
			reset(GameType.CREATIVE);
			put(CENTER.below(), unsafe);
			Map<BlockPos, BlockState> before = snapshot(CENTER);
			check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "unsafe/missing shell refused: " + unsafe);
			check(before.equals(snapshot(CENTER)), "unsafe shell causes no partial replacement");
		}
	}

	private void creative() throws Exception {
		reset(GameType.CREATIVE);
		check(slice(CENTER, "width:2 height:3 to:bricks[100]"), "creative slice succeeds with empty inventory");
		check(count(Items.BRICKS) == 0, "creative does not add or remove materials");
	}

	private void openAirShells() throws Exception {
		for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SURVIVAL}) {
			reset(mode);
			openShell(CENTER);
			if (mode == GameType.SURVIVAL) player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 12));
			check(slice(CENTER, "width:2 height:3 to:bricks[100]"), "open-air slice succeeds in " + mode);
			for (BlockPos pos : TunnelService.shell(CENTER, Direction.SOUTH, 2, 3).keySet()) {
				check(level.getBlockState(pos).is(Blocks.BRICKS), "missing shell or existing footing lined at " + pos);
			}
			check(count(Items.BRICKS) == (mode == GameType.SURVIVAL ? 2 : 0), "open-air shell has exact material cost in " + mode);
			check(slice(CENTER, "width:2 height:3 to:bricks[100]"), "completed open-air shell may be revisited");
			check(count(Items.BRICKS) == (mode == GameType.SURVIVAL ? 2 : 0), "revisiting open-air shell costs nothing");
		}
	}

	private void openAirSafety() throws Exception {
		reset(GameType.SURVIVAL);
		openShell(CENTER);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 9));
		Map<BlockPos, BlockState> before = snapshot(CENTER);
		check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "open-air shell shortage refused");
		check(before.equals(snapshot(CENTER)), "open-air shortage places no partial shell");
		check(count(Items.BRICKS) == 9, "open-air shortage charges no materials");
		for (Block missingFooting : new Block[]{Blocks.AIR, Blocks.WATER, Blocks.STONE_SLAB}) {
			reset(GameType.SURVIVAL);
			openShell(CENTER);
			put(CENTER.below(), missingFooting);
			player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 12));
			before = snapshot(CENTER);
			check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "missing or incomplete walking support refused: " + missingFooting);
			check(before.equals(snapshot(CENTER)), "unsupported walking lane places no partial shell");
			check(count(Items.BRICKS) == 12, "unsupported walking lane charges no materials");
		}
		for (Block unsafe : new Block[]{Blocks.WATER, Blocks.CHEST, Blocks.BEDROCK}) {
			reset(GameType.CREATIVE);
			openShell(CENTER);
			put(CENTER.east().above(), unsafe);
			before = snapshot(CENTER);
			check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "unsafe shell remains protected beside air: " + unsafe);
			check(before.equals(snapshot(CENTER)), "unsafe shell leaves open-air slice unchanged");
		}
	}

	private void openAirRails() throws Exception {
		reset(GameType.SURVIVAL);
		openShell(CENTER);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 10));
		player.getInventory().setItem(1, new ItemStack(Items.POWERED_RAIL, 1));
		player.getInventory().setItem(2, new ItemStack(Items.REDSTONE_TORCH, 1));
		check(slice(CENTER, "width:2 height:3 to:bricks[100] rail:on"), "lining builds missing floor before powered rail");
		check(level.getBlockState(CENTER.west().below()).is(Blocks.BRICKS), "rail support built into air");
		BlockState rail = level.getBlockState(CENTER.west());
		check(rail.is(Blocks.POWERED_RAIL) && rail.getValue(PoweredRailBlock.POWERED), "open-air booster remains supported and powered");
		check(count(Items.BRICKS) == 0 && count(Items.POWERED_RAIL) == 0 && count(Items.REDSTONE_TORCH) == 0,
			"open-air rail slice charges exact shell rail and torch materials");
		reset(GameType.CREATIVE);
		openShell(CENTER);
		Map<BlockPos, BlockState> before = snapshot(CENTER);
		check(!slice(CENTER, "width:2 height:3 rail:only"), "rail-only refuses missing rail-lane floor");
		check(before.equals(snapshot(CENTER)), "unsupported rail-only leaves slice unchanged");
		put(CENTER.west().below(), Blocks.STONE);
		check(slice(CENTER, "width:2 height:3 rail:only"), "rail-only accepts supported floor with air walls and ceiling");
		check(level.getBlockState(CENTER.above(3)).isAir() && level.getBlockState(CENTER.east()).isAir(),
			"rail-only leaves missing walls and ceiling as air");
	}

	private void airPalettes() throws Exception {
		String openSides = "width:2 height:3 walls:air[100] floor:bricks[100] ceiling:air[100]";
		reset(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 2));
		check(slice(CENTER, openSides), "air palettes clear safe existing walls and ceiling");
		for (Map.Entry<BlockPos, TunnelService.Surface> entry : TunnelService.shell(CENTER, Direction.SOUTH, 2, 3).entrySet()) {
			check(level.getBlockState(entry.getKey()).is(entry.getValue() == TunnelService.Surface.FLOOR ? Blocks.BRICKS : Blocks.AIR),
				"air palette preserves solid floor and clears selected surface at " + entry.getKey());
		}
		check(count(Items.BRICKS) == 0 && count(Items.STONE) == 0 && count(Items.COBBLESTONE) == 0,
			"only two floor materials charged and cleared blocks are not refunded");
		check(slice(CENTER, openSides), "air cells are idempotent with empty inventory");
		check(InventoryMaterials.count(player).isEmpty(), "air cells require no inventory items");
		reset(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 1));
		Map<BlockPos, BlockState> before = snapshot(CENTER);
		check(!slice(CENTER, openSides), "floor shortage refuses free air clearing too");
		check(before.equals(snapshot(CENTER)) && count(Items.BRICKS) == 1, "air clearing and floor lining remain atomic on shortage");
		for (Block unsafe : new Block[]{Blocks.CHEST, Blocks.BEDROCK, Blocks.WATER}) {
			reset(GameType.CREATIVE);
			put(CENTER.east().above(), unsafe);
			before = snapshot(CENTER);
			check(!slice(CENTER, openSides), "air palette cannot clear unsafe shell: " + unsafe);
			check(before.equals(snapshot(CENTER)), "unsafe air clearing leaves entire slice unchanged");
		}
		reset(GameType.ADVENTURE);
		before = snapshot(CENTER);
		check(!slice(CENTER, "width:2 height:3 walls:air[100] floor:stone[100] ceiling:air[100]"),
			"air-only changes still require build permission");
		check(before.equals(snapshot(CENTER)), "denied air clearing leaves shell unchanged");
	}

	private void permissions() throws Exception {
		reset(GameType.ADVENTURE);
		Map<BlockPos, BlockState> before = snapshot(CENTER);
		check(!BuildPermissions.canChange(player, CENTER), "adventure edits denied");
		check(!slice(CENTER, "width:2 height:3 to:bricks[100]"), "adventure tunnel slice denied");
		check(before.equals(snapshot(CENTER)), "adventure slice leaves world unchanged");
		player.setGameMode(GameType.SPECTATOR);
		check(!BuildPermissions.canChange(player, CENTER), "spectator edits denied");
		player.setGameMode(GameType.SURVIVAL);
		check(BuildPermissions.canChange(player, CENTER), "loaded native survival position allowed");
		check(!BuildPermissions.canChange(player, new BlockPos(20_000_000, 100, 20_000_000)), "unloaded chunk denied");
		check(!BuildPermissions.canChange(player, new BlockPos(256, 10_000, 256)), "outside build height denied");
		double original = level.getWorldBorder().getSize();
		try {
			level.getWorldBorder().setSize(64);
			check(!BuildPermissions.canChange(player, CENTER), "outside world border denied");
		} finally {
			level.getWorldBorder().setSize(original);
		}
	}

	private void rails() throws Exception {
		reset(GameType.SURVIVAL);
		for (int z = 0; z <= 9; z++) prepare(CENTER.south(z));
		player.getInventory().setItem(0, new ItemStack(Items.RAIL, 64));
		player.getInventory().setItem(1, new ItemStack(Items.POWERED_RAIL, 64));
		player.getInventory().setItem(2, new ItemStack(Items.REDSTONE_TORCH, 64));
		for (int z = 0; z <= 9; z++) check(slice(CENTER.south(z), "width:2 height:3 rail:only"), "rail slice " + z + " succeeds");
		check(count(Items.RAIL) == 56, "eight ordinary rails charged");
		check(count(Items.POWERED_RAIL) == 62 && count(Items.REDSTONE_TORCH) == 62, "two real boosters and torches charged");
		for (int z = 0; z <= 9; z++) {
			BlockPos center = CENTER.south(z);
			BlockState rail = level.getBlockState(center.west());
			if (z % 8 == 0) {
				check(rail.is(Blocks.POWERED_RAIL) && rail.getValue(PoweredRailBlock.POWERED), "booster " + z + " powered by real torch");
				check(level.getBlockState(center).is(Blocks.REDSTONE_TORCH), "booster torch survives at " + z);
			} else check(rail.is(Blocks.RAIL), "ordinary rail survives at " + z);
			check(level.getBlockState(center.below()).is(Blocks.STONE), "rail-only shell remains stone");
		}
		check(slice(CENTER, "width:2 height:3 rail:only"), "existing powered slice can be revisited");
		check(count(Items.POWERED_RAIL) == 62 && count(Items.REDSTONE_TORCH) == 62, "revisited booster charges nothing");
	}

	private void specialRails() throws Exception {
		reset(GameType.CREATIVE);
		put(CENTER.west(), Blocks.DETECTOR_RAIL);
		check(slice(CENTER, "width:2 height:3 rail:only"), "detector rail slice accepted");
		check(level.getBlockState(CENTER.west()).is(Blocks.DETECTOR_RAIL), "detector rail preserved at booster coordinate");
	}

	private void commands() throws Exception {
		reset(GameType.SURVIVAL);
		check(command("pathtool to:bricks[100]") > 0, "path configuration command succeeds");
		check(BlockToolsMod.STATES.path(player.getUUID()).enabled, "path activated");
		check(command("tunnel width:2 height:3 to:bricks[100]") > 0, "short tunnel alias configures");
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		check(state.enabled && !BlockToolsMod.STATES.path(player.getUUID()).enabled, "tunnel activation pauses path");
		check(command("tunnel") > 0 && command("tunneltool") > 0, "bare status works for both aliases");
		check(command("tunnel off") > 0 && !state.enabled && state.configured(), "pause preserves tunnel configuration");
		check(command("tunnel on") > 0 && state.enabled, "paused configuration resumes");
		check(command("pathtool on") > 0 && !state.enabled, "path activation pauses tunnel");
		check(command("tunnel disarm") > 0 && !state.configured(), "disarm clears tunnel configuration");
		check(command("tunnel on") == 0 && !state.enabled, "unconfigured tunnel cannot be enabled");
	}

	private void tone() throws Exception {
		reset(GameType.SURVIVAL);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 5));
		BlockToolsMod.STATES.selection(player.getUUID()).set(level, 1, CENTER.below());
		BlockToolsMod.STATES.selection(player.getUUID()).set(level, 2, CENTER.below().west());
		check(command("blocktone to:bricks[100]") > 0, "tone command succeeds");
		check(level.getBlockState(CENTER.below()).is(Blocks.BRICKS) && level.getBlockState(CENTER.below().west()).is(Blocks.BRICKS), "selected floor blocks toned");
		check(count(Items.BRICKS) == 3, "tone charges two materials");
	}

	private void configPreservation() throws Exception {
		Path general = BlockToolsMod.CONFIG.path();
		Path tunnel = BlockToolsMod.TUNNEL_CONFIG.path();
		byte[] originalGeneral = Files.readAllBytes(general);
		byte[] originalTunnel = Files.readAllBytes(tunnel);
		try {
			for (String input : new String[]{"{ definitely malformed", "{\"configVersion\":999,\"unknown_future_setting\":\"preserve me\"}"}) {
				Files.writeString(general, input);
				Files.writeString(tunnel, input);
				BlockToolsMod.CONFIG.load();
				BlockToolsMod.TUNNEL_CONFIG.load();
				check(Files.readString(general).equals(input), "general invalid/future configuration retained exactly");
				check(Files.readString(tunnel).equals(input), "tunnel invalid/future configuration retained exactly");
				check(BlockToolsMod.TUNNEL_CONFIG.config().poweredRailInterval == 8, "invalid/future tunnel config uses safe defaults");
			}
		} finally {
			Files.write(general, originalGeneral);
			Files.write(tunnel, originalTunnel);
			BlockToolsMod.CONFIG.load();
			BlockToolsMod.TUNNEL_CONFIG.load();
		}
	}

	private void path() throws Exception {
		reset(GameType.SURVIVAL);
		// Open sky avoids selecting the tunnel ceiling as the terrain surface.
		put(CENTER.above(3), Blocks.AIR);
		put(CENTER.above(3).west(), Blocks.AIR);
		player.getInventory().setItem(0, new ItemStack(Items.BRICKS, 5));
		check(command("pathtool to:bricks[100]") > 0 && command("pathtool width 2") > 0, "two-wide path configured");
		Method stripe = PathService.class.getDeclaredMethod("placeStripe", ServerPlayer.class, PathState.class, BlockPos.class, Direction.class);
		stripe.setAccessible(true);
		check((boolean) stripe.invoke(null, player, BlockToolsMod.STATES.path(player.getUUID()), CENTER, Direction.SOUTH), "survival path stripe succeeds");
		check(level.getBlockState(CENTER.below()).is(Blocks.BRICKS) && level.getBlockState(CENTER.below().west()).is(Blocks.BRICKS), "both path floor blocks replaced");
		check(count(Items.BRICKS) == 3, "path charges two bricks");
	}

	private void reset(final GameType gameType) {
		player.setGameMode(gameType);
		player.getInventory().clearContent();
		BlockToolsMod.STATES.remove(player.getUUID());
		BlockToolsMod.TUNNELS.remove(player.getUUID());
		prepare(CENTER);
	}

	private void prepare(final BlockPos center) {
		for (int x = -3; x <= 3; x++) for (int y = -1; y <= 3; y++) put(center.offset(x, y, 0), Blocks.AIR);
		for (int x = -1; x <= 0; x++) {
			put(center.offset(x, -1, 0), Blocks.STONE);
			put(center.offset(x, 3, 0), Blocks.STONE);
		}
		for (int y = 0; y < 3; y++) {
			put(center.offset(1, y, 0), Blocks.STONE);
			put(center.offset(-2, y, 0), Blocks.STONE);
		}
	}

	private void openShell(final BlockPos center) {
		for (BlockPos pos : TunnelService.shell(center, Direction.SOUTH, 2, 3).keySet()) {
			if (!pos.equals(center.below())) put(pos, Blocks.AIR);
		}
	}

	private boolean slice(final BlockPos center, final String spec) throws Exception {
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		state.configure(TunnelSpec.parse(spec, 2, 3));
		return (boolean) placeSlice.invoke(null, player, state, center, Direction.SOUTH);
	}

	private void put(final BlockPos pos, final Block block) {
		level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
	}

	private Map<BlockPos, BlockState> snapshot(final BlockPos center) {
		Map<BlockPos, BlockState> result = new LinkedHashMap<>();
		for (int x = -3; x <= 3; x++) for (int y = -1; y <= 3; y++) {
			BlockPos pos = center.offset(x, y, 0);
			result.put(pos, level.getBlockState(pos));
		}
		return result;
	}

	private int count(final net.minecraft.world.item.Item item) {
		return InventoryMaterials.count(player).getOrDefault(item, 0);
	}

	private int command(final String input) throws Exception {
		return server.getCommands().getDispatcher().execute(input, player.createCommandSourceStack());
	}

	private void check(final boolean condition, final String description) {
		if (!condition) throw new AssertionError(description);
		assertions++;
	}

	private void caseOf(final String name, final CheckedRunnable action) {
		try {
			action.run();
			report.append("PASS: ").append(name).append('\n');
		} catch (Throwable failure) {
			failures++;
			appendFailure(name, failure);
		}
	}

	private void appendFailure(final String name, final Throwable failure) {
		StringWriter stack = new StringWriter();
		failure.printStackTrace(new PrintWriter(stack));
		report.append("FAIL: ").append(name).append('\n').append(stack);
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
