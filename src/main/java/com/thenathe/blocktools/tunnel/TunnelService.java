package com.thenathe.blocktools.tunnel;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.compat.BuildPermissions;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import com.thenathe.blocktools.material.BlockMaterials;
import com.thenathe.blocktools.world.WorldEdits;
import com.thenathe.blocktools.tunnel.TunnelStateManager.TunnelState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TunnelService {
	static final int MAX_REMEMBERED_CENTERS = 4096;
	private TunnelService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TunnelService::tick);
	}

	private static void tick(final MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(player);
	}

	private static void tickPlayer(final ServerPlayer player) {
		TunnelState state = BlockToolsMod.TUNNELS.state(player.getUUID());
		if (!state.enabled || !state.configured() || !holdsTool(player)) {
			state.resetTrail();
			return;
		}
		if (!player.mayBuild() || player.isSpectator()) return;
		ServerLevel level = player.level();
		BlockPos current = player.blockPosition();
		String dimension = level.dimension().identifier().toString();
		if (state.runOrigin == null) {
			state.runOrigin = current.immutable();
			state.runDimension = dimension;
			state.runDirection = horizontal(player.getDirection());
		}
		if (!dimension.equals(state.runDimension)) {
			warnResetPosition(player, state, "Your tunnel line is locked in another dimension.");
			return;
		}
		if (current.getY() != state.runOrigin.getY()) {
			warnResetPosition(player, state, "Your tunnel line is locked at its original elevation.");
			return;
		}
		Direction runDirection = state.runDirection;
		if (horizontal(player.getDirection()) != runDirection) {
			warnReset(player, state, "Your tunnel line is locked to its original direction. Put the tool away, rotate, and pull it back out to start a new line.",
				"Put tool away, turn, then re-equip.");
			return;
		}
		boolean offLine = runDirection.getAxis() == Direction.Axis.X
			? current.getZ() != state.runOrigin.getZ() : current.getX() != state.runOrigin.getX();
		if (offLine) {
			warnResetPosition(player, state, "Stay on the original straight tunnel line.");
			return;
		}
		if (current.equals(state.lastCenter)) return;
		BlockPos previous = state.lastCenter == null ? state.runOrigin : state.lastCenter;
		int deltaX = current.getX() - previous.getX();
		int deltaZ = current.getZ() - previous.getZ();
		int distance = Math.max(Math.abs(deltaX), Math.abs(deltaZ));
		if (distance > BlockToolsMod.TUNNEL_CONFIG.config().maxCatchUp) {
			warnResetPosition(player, state, "Moved too far from the last completed tunnel slice; return closer to continue.");
			return;
		}
		if (state.lastCenter == null) {
			if (!placeSlice(player, state, state.runOrigin, runDirection)) return;
			remember(state, state.runOrigin, dimension, runDirection);
		}
		for (BlockPos next : line(state.lastCenter, current)) {
			if (state.processedCenters.contains(next)) {
				state.lastCenter = next.immutable();
				state.lastDimension = dimension;
				continue;
			}
			if (!placeSlice(player, state, next, runDirection)) return;
			remember(state, next, dimension, runDirection);
		}
	}

	static void remember(final TunnelState state, final BlockPos center, final String dimension, final Direction direction) {
		state.lastCenter = center.immutable();
		state.lastDimension = dimension;
		state.lastDirection = direction;
		state.processedCenters.add(center.immutable());
		while (state.processedCenters.size() > MAX_REMEMBERED_CENTERS) {
			var oldest = state.processedCenters.iterator();
			oldest.next();
			oldest.remove();
		}
	}

	private static boolean placeSlice(
		final ServerPlayer player,
		final TunnelState state,
		final BlockPos center,
		final Direction direction
	) {
		ServerLevel level = player.level();
		int left = leftExtent(state.width);
		int right = rightExtent(state.width);
		int railLane = railLane(state.width);
		Set<BlockPos> interior = new LinkedHashSet<>();
		for (int lateral = -left; lateral <= right; lateral++) {
			for (int y = 0; y < state.height; y++) interior.add(lateral(center, direction, lateral).above(y));
		}
		for (BlockPos pos : interior) {
			if (!validLoadedPosition(level, player, pos)) return fail(player, state, "Tunnel interior reaches an unloaded or protected world boundary.");
			BlockState existing = level.getBlockState(pos);
			if (!existing.getFluidState().isEmpty()) {
				return fail(player, state, "Tunnel interior contains fluid at " + coordinates(pos) + ".");
			}
			boolean plannedRail = state.railMode != TunnelSpec.RailMode.OFF
				&& pos.equals(lateral(center, direction, railLane));
			if (plannedRail) {
				if (!existing.isAir() && !(existing.getBlock() instanceof BaseRailBlock)) {
					return fail(player, state, "The rail lane is obstructed at " + coordinates(pos) + ".");
				}
			} else if (!isOpenInterior(level, pos, existing)) {
				return fail(player, state, "Tunnel is not pre-dug: interior obstruction at " + coordinates(pos)
					+ ". Use a full-height floor so your feet remain in the clear interior.");
			}
		}

		BlockPos footingPos = center.below();
		if (!validLoadedPosition(level, player, footingPos)) {
			return fail(player, state, "Tunnel footing reaches an unloaded or protected world boundary.");
		}
		BlockState footing = level.getBlockState(footingPos);
		if (!footing.getFluidState().isEmpty() || !footing.isFaceSturdy(level, footingPos, Direction.UP)) {
			return fail(player, state, "Tunnel Tool needs a solid, full-height block under your walking lane at "
				+ coordinates(footingPos) + ". Walls, ceiling, and the rest of the floor may start as air.");
		}

		Map<BlockPos, Surface> shell = shell(center, direction, state.width, state.height);
		for (Map.Entry<BlockPos, Surface> entry : shell.entrySet()) {
			BlockPos pos = entry.getKey();
			if (!validLoadedPosition(level, player, pos)) return fail(player, state, "Tunnel shell reaches an unloaded or protected world boundary.");
			BlockState existing = level.getBlockState(pos);
			if (!existing.isAir() && (!existing.getFluidState().isEmpty()
				|| existing.getCollisionShape(level, pos).isEmpty())) {
				return fail(player, state, "Tunnel " + entry.getValue().label
					+ " is unsafe to replace at " + coordinates(pos) + ".");
			}
		}

		Map<BlockPos, Change> changes = new LinkedHashMap<>();
		if (state.railMode != TunnelSpec.RailMode.ONLY) {
			for (Map.Entry<BlockPos, Surface> entry : shell.entrySet()) {
				TunnelPalette palette = switch (entry.getValue()) {
					case WALL -> state.walls;
					case FLOOR -> state.floor;
					case CEILING -> state.ceiling;
				};
				Block desired = palette.pick(materialHash(entry.getKey(), palette.canonical(), entry.getValue().name()));
				String error = planReplacement(player, entry.getKey(), desired.defaultBlockState(), 0, true, changes);
				if (error != null) return fail(player, state, error);
			}
		}

		if (state.railMode != TunnelSpec.RailMode.OFF) {
			if (state.width < 2) {
				return fail(player, state, "Rail mode requires width 2–5 so a real redstone torch can power each booster without breaking the tunnel shell.");
			}
			BlockPos railPos = lateral(center, direction, railLane);
			BlockPos supportPos = railPos.below();
			BlockState support = plannedState(level, supportPos, changes);
			if (!support.isFaceSturdy(level, supportPos, Direction.UP)) {
				return fail(player, state, "Rail needs a sturdy floor at " + coordinates(supportPos)
					+ ". Rail-only mode does not build missing floor blocks.");
			}
			BlockState existingRail = level.getBlockState(railPos);
			if (!isSpecialRail(existingRail.getBlock())) {
				boolean powered = existingRail.getBlock() instanceof PoweredRailBlock
					|| isBoosterCoordinate(center, direction, BlockToolsMod.TUNNEL_CONFIG.config().poweredRailInterval);
				RailShape shape = direction.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
				BlockState rail = powered
					? Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape)
					: Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape);
				String railError = planReplacement(player, railPos, rail, 2, false, changes);
				if (railError != null) return fail(player, state, railError);
				if (powered) {
					TorchPlan torch = torchPlan(center, direction, state.width);
					BlockState torchSupport = plannedState(level, torch.support(), changes);
					if (!torchSupport.isFaceSturdy(level, torch.support(), torch.supportFace())) {
						return fail(player, state, "Powered rail needs sturdy torch support at " + coordinates(torch.support()) + ".");
					}
					BlockState existingTorch = level.getBlockState(torch.pos());
					if (!canUseTorchPosition(existingTorch)) {
						return fail(player, state, "Booster torch lane is occupied at " + coordinates(torch.pos()) + ".");
					}
					String torchError = existingTorch.is(Blocks.REDSTONE_TORCH) ? null : planReplacement(player, torch.pos(), torch.state(), 1, false, changes);
					if (torchError != null) return fail(player, state, torchError);
				}
			}
		}

		if (changes.isEmpty()) return true;
		Map<Item, Integer> required = new LinkedHashMap<>();
		for (Change change : changes.values()) {
			if (change.cost() != null) required.merge(change.cost(), 1, Integer::sum);
		}
		if (!player.isCreative()) {
			Map<Item, InventoryMaterials.Shortage> shortages = InventoryMaterials.snapshot(player).shortages(required);
			if (!shortages.isEmpty()) return fail(player, state, shortageMessage(shortages));
		}
		List<Change> ordered = new ArrayList<>(changes.values());
		ordered.sort(Comparator.comparingInt(Change::phase));
		Map<BlockPos, BlockState> desired = new LinkedHashMap<>();
		for (Change change : ordered) desired.put(change.pos(), change.desired());
		if (!WorldEdits.apply(level, desired, () -> player.isCreative()
			|| InventoryMaterials.consume(player, required).isEmpty())) {
			return fail(player, state, "This tunnel slice could not be committed; its blocks were restored and no materials were charged.");
		}
		return true;
	}

	private static String planReplacement(
		final ServerPlayer player,
		final BlockPos pos,
		final BlockState desired,
		final int phase,
		final boolean shell,
		final Map<BlockPos, Change> changes
	) {
		ServerLevel level = player.level();
		BlockState existing = level.getBlockState(pos);
		if (existing.getBlock() == desired.getBlock() && shell) return null;
		if (existing.equals(desired)) return null;

		if (BlockMaterials.isMultipart(existing) || level.getBlockEntity(pos) != null || existing.getDestroySpeed(level, pos) < 0
			|| !existing.getFluidState().isEmpty()) {
			return "Tunnel surface is unsafe to replace at " + coordinates(pos) + ".";
		}
		if (!BuildPermissions.canChange(player, pos)) {
			return "You do not have build access to change " + coordinates(pos) + ".";
		}
		Item cost = existing.getBlock() == desired.getBlock() ? null : desired.getBlock().asItem();
		changes.put(pos, new Change(pos, desired, cost == Items.AIR ? null : cost, phase));
		return null;
	}

	private static BlockState plannedState(final ServerLevel level, final BlockPos pos, final Map<BlockPos, Change> changes) {
		Change planned = changes.get(pos);
		return planned == null ? level.getBlockState(pos) : planned.desired();
	}

	public static Map<BlockPos, Surface> shell(
		final BlockPos center,
		final Direction direction,
		final int width,
		final int height
	) {
		int left = leftExtent(width);
		int right = rightExtent(width);
		Map<BlockPos, Surface> result = new LinkedHashMap<>();
		for (int offset = -left; offset <= right; offset++) {
			BlockPos column = lateral(center, direction, offset);
			result.put(column.below(), Surface.FLOOR);
			result.put(column.above(height), Surface.CEILING);
		}
		for (int y = 0; y < height; y++) {
			result.put(lateral(center, direction, -left - 1).above(y), Surface.WALL);
			result.put(lateral(center, direction, right + 1).above(y), Surface.WALL);
		}
		return result;
	}

	public static int leftExtent(final int width) {
		return (width - 1) / 2;
	}

	public static int rightExtent(final int width) {
		return width - 1 - leftExtent(width);
	}

	public static int railLane(final int width) {
		return width % 2 == 0 ? 1 : 0;
	}

	public static int torchLane(final int width) {
		if (width < 2) throw new IllegalArgumentException("Rail mode requires tunnel width 2–5.");
		int rail = railLane(width);
		int right = rightExtent(width);
		return rail + 1 <= right ? rail + 1 : rail - 1;
	}

	public static boolean isBoosterCoordinate(final BlockPos center, final Direction direction, final int interval) {
		int coordinate = direction.getAxis() == Direction.Axis.X ? center.getX() : center.getZ();
		return Math.floorMod(coordinate, interval) == 0;
	}

	static boolean isSpecialRail(final Block block) {
		return block instanceof BaseRailBlock && block != Blocks.RAIL && block != Blocks.POWERED_RAIL;
	}

	static boolean sameRunAxis(final Direction runDirection, final Direction movement) {
		return runDirection.getAxis() == movement.getAxis();
	}

	private static TorchPlan torchPlan(
		final BlockPos center,
		final Direction direction,
		final int width
	) {
		if (width >= 2) {
			BlockPos pos = lateral(center, direction, torchLane(width));
			return new TorchPlan(pos, pos.below(), Direction.UP, Blocks.REDSTONE_TORCH.defaultBlockState());
		}
		throw new IllegalArgumentException("Rail mode requires tunnel width 2–5.");
	}

	static boolean canUseTorchPosition(final BlockState existing) {
		return existing.isAir() || existing.is(Blocks.REDSTONE_TORCH);
	}

	private static boolean validLoadedPosition(final ServerLevel level, final ServerPlayer player, final BlockPos pos) {
		return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos)
			&& level.hasChunkAt(pos) && player.mayInteract(level, pos);
	}

	private static boolean isOpenInterior(final ServerLevel level, final BlockPos pos, final BlockState state) {
		return state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null
			&& (state.isAir() || state.getCollisionShape(level, pos).isEmpty());
	}

	private static boolean holdsTool(final ServerPlayer player) {
		Item tool = BlockToolsMod.TUNNEL_CONFIG.toolItem();
		return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == tool
			|| player.getItemInHand(InteractionHand.OFF_HAND).getItem() == tool;
	}

	private static boolean fail(final ServerPlayer player, final TunnelState state, final String message) {
		warn(player, state, message);
		return false;
	}

	private static void warn(final ServerPlayer player, final TunnelState state, final String message) {
		long now = player.level().getGameTime();
		if (state.lastErrorGameTime != Long.MIN_VALUE && now - state.lastErrorGameTime < 40L) return;
		state.lastErrorGameTime = now;
		player.sendOverlayMessage(TunnelCommands.error(message));
	}

	private static void warnResetPosition(final ServerPlayer player, final TunnelState state, final String message) {
		warnReset(player, state, message + " Put the tool away and pull it back out to reset position.",
			"Put tool away, then re-equip to reset.");
	}

	/** Keep action-bar text readable; send the complete reset instructions to wrapping chat once per reason. */
	private static void warnReset(final ServerPlayer player, final TunnelState state, final String message, final String overlay) {
		long now = player.level().getGameTime();
		if (state.lastErrorGameTime != Long.MIN_VALUE && now - state.lastErrorGameTime < 40L) return;
		state.lastErrorGameTime = now;
		player.sendOverlayMessage(TunnelCommands.error(overlay));
		if (!message.equals(state.lastResetNotice)) {
			state.lastResetNotice = message;
			player.sendSystemMessage(TunnelCommands.error(message));
		}
	}

	private static String shortageMessage(final Map<Item, InventoryMaterials.Shortage> shortages) {
		return "Missing " + shortages.entrySet().stream()
			.map(entry -> entry.getValue().missing() + " " + BuiltInRegistries.ITEM.getKey(entry.getKey()).toShortString())
			.reduce((left, right) -> left + ", " + right).orElse("materials") + ".";
	}

	private static String coordinates(final BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static BlockPos lateral(final BlockPos center, final Direction travel, final int amount) {
		return center.relative(travel.getClockWise(), amount);
	}

	private static Direction travelDirection(final BlockPos from, final BlockPos to, final Direction fallback) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
		if (Math.abs(dz) > Math.abs(dx)) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
		return horizontal(fallback);
	}

	private static Direction horizontal(final Direction direction) {
		return direction.getAxis().isHorizontal() ? direction : Direction.NORTH;
	}

	static List<BlockPos> line(final BlockPos from, final BlockPos to) {
		List<BlockPos> result = new ArrayList<>();
		int x = from.getX();
		int z = from.getZ();
		int targetX = to.getX();
		int targetZ = to.getZ();
		int dx = Math.abs(targetX - x);
		int dz = Math.abs(targetZ - z);
		int stepX = Integer.compare(targetX, x);
		int stepZ = Integer.compare(targetZ, z);
		int error = dx - dz;
		while (x != targetX || z != targetZ) {
			int doubled = error * 2;
			if (doubled > -dz) {
				error -= dz;
				x += stepX;
			}
			if (doubled < dx) {
				error += dx;
				z += stepZ;
			}
			result.add(new BlockPos(x, from.getY(), z));
		}
		return result;
	}

	private static long materialHash(final BlockPos pos, final String palette, final String surface) {
		long value = pos.asLong() ^ (long) palette.hashCode() * 0x9E3779B97F4A7C15L ^ surface.hashCode();
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}

	public static String alignmentMessage(final int width) {
		if (width == 1) return "Width 1: stay in the MIDDLE; rail mode requires width 2–5.";
		return width % 2 == 0
			? "Even width: stay on the LEFT center block; rails are placed on the RIGHT center block."
			: "Odd width: stay in the MIDDLE; rails are placed in the MIDDLE.";
	}

	public enum Surface {
		WALL("wall"),
		FLOOR("floor"),
		CEILING("ceiling");

		private final String label;

		Surface(final String label) {
			this.label = label;
		}
	}

	private record Change(BlockPos pos, BlockState desired, Item cost, int phase) {
	}

	private record TorchPlan(BlockPos pos, BlockPos support, Direction supportFace, BlockState state) {
	}
}
