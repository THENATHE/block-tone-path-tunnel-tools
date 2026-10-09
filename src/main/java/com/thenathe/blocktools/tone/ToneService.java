package com.thenathe.blocktools.tone;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.compat.BuildPermissions;
import com.thenathe.blocktools.material.BlockMaterials;
import com.thenathe.blocktools.material.ReplacementStates;
import com.thenathe.blocktools.world.WorldEdits;
import com.thenathe.blocktools.inventory.InventoryMaterials;
import com.thenathe.blocktools.inventory.MaterialLedger;
import com.thenathe.blocktools.material.BlockFilter;
import com.thenathe.blocktools.material.OverlayPalette;
import com.thenathe.blocktools.material.Palette;
import com.thenathe.blocktools.state.PlayerStateManager.SelectionState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class ToneService {
	private ToneService() {
	}

	public static Result apply(final ServerPlayer player, final Palette palette, final BlockFilter filter) {
		TargetSearch search = findTargets(player, filter);
		if (search.error != null) return Result.error(search.error);
		long seed = selectionSeed(player);
		List<Palette.Entry> quotaEntries = palette.exactAssignments(search.targets.size(), seed);
		Map<Block, Integer> desiredCounts = new LinkedHashMap<>();
		for (Palette.Entry entry : quotaEntries) desiredCounts.merge(entry.block(), 1, Integer::sum);
		ReplacementPlan plan = createPlan(player.level(), search.targets, desiredCounts, seed);
		InventoryMaterials.InventorySnapshot inventorySnapshot = InventoryMaterials.snapshot(player);
		Map<Item, InventoryMaterials.Shortage> shortages = player.isCreative() || plan.required.isEmpty()
			? Map.of()
			: inventorySnapshot.shortages(plan.required);
		if (!shortages.isEmpty()) return Result.error(shortageMessage(shortages));

		int changed = execute(player, plan, inventorySnapshot);
		if (changed < 0) return Result.error(editFailure(changed));
		return Result.success("Toning complete"
			+ "\n  Eligible: " + formatNumber(search.targets.size()) + " blocks from " + filter.canonical()
			+ "\n  Changed: " + formatNumber(changed)
			+ "\n  Applied:" + formatDistributionLines(desiredCounts, search.targets.size(), 0)
			+ "\n  Returned: " + formatItems(plan.returned));
	}

	public static Result applyOverlay(
		final ServerPlayer player,
		final OverlayPalette palette,
		final BlockFilter filter
	) {
		TargetSearch search = findTargets(player, filter);
		if (search.error != null) return Result.error(search.error);
		long seed = selectionSeed(player);
		OverlayPalette.Distribution distribution = palette.exactDistribution(search.targets.size());
		ReplacementPlan plan = createOverlayPlan(
			player.level(), search.targets, distribution.materialCounts(), seed
		);
		InventoryMaterials.InventorySnapshot inventorySnapshot = InventoryMaterials.snapshot(player);
		Map<Item, InventoryMaterials.Shortage> shortages = player.isCreative() || plan.required.isEmpty()
			? Map.of()
			: inventorySnapshot.shortages(plan.required);
		if (!shortages.isEmpty()) return Result.error(shortageMessage(shortages));

		int changed = execute(player, plan, inventorySnapshot);
		if (changed < 0) return Result.error(editFailure(changed));
		return Result.success("Toning complete"
			+ "\n  Eligible: " + formatNumber(search.targets.size()) + " blocks from " + filter.canonical()
			+ "\n  Changed: " + formatNumber(changed)
			+ "\n  Applied:" + formatDistributionLines(
				distribution.materialCounts(), search.targets.size(), distribution.unchangedCount()
			)
			+ "\n  Returned: " + formatItems(plan.returned));
	}

	public static Result shuffle(final ServerPlayer player, final BlockFilter filter) {
		TargetSearch search = findTargets(player, filter);
		if (search.error != null) return Result.error(search.error);
		if (search.targets.size() < 2) {
			return Result.error("Shuffle needs at least two safe matching blocks in the selection.");
		}

		ServerLevel level = player.level();
		List<BlockState> existing = search.targets.stream().map(level::getBlockState).toList();
		if (existing.stream().distinct().limit(2).count() < 2) {
			return Result.error("Shuffle needs at least two different matching block states to rerandomize.");
		}

		List<BlockState> shuffled = shuffledStates(existing, player.getRandom().nextLong());
		Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
		for (int index = 0; index < search.targets.size(); index++) {
			if (!existing.get(index).equals(shuffled.get(index))) edits.put(search.targets.get(index), shuffled.get(index));
		}
		if (!WorldEdits.apply(level, edits)) return Result.error("The shuffle could not be committed; the original blocks were restored.");
		int changed = edits.size();
		return Result.success("Shuffle complete"
			+ "\n  Eligible: " + formatNumber(search.targets.size()) + " blocks from " + filter.canonical()
			+ "\n  Changed positions: " + formatNumber(changed)
			+ "\n  Preserved:" + formatCounts(existingCounts(existing))
			+ "\n  Inventory: not used");
	}

	public static Result calculate(
		final ServerPlayer player,
		final OverlayPalette materials,
		final BlockFilter filter,
		final boolean apply
	) {
		TargetSearch search = findTargets(player, filter);
		if (search.error != null) return Result.error(search.error);
		InventoryMaterials.InventorySnapshot inventorySnapshot = InventoryMaterials.snapshot(player);
		Map<Item, Integer> inventory = inventorySnapshot.counts();
		Map<Block, Integer> existing = existingCounts(player.level(), search.targets);
		Map<Block, Integer> desiredCounts = calculatedCounts(
			search.targets.size(), materials, inventory, existing, player.isCreative()
		);
		int unchanged = search.targets.size() - desiredCounts.values().stream().mapToInt(Integer::intValue).sum();
		ReplacementPlan plan = createOverlayPlan(player.level(), search.targets, desiredCounts, selectionSeed(player));

		if (!apply) {
			Map<Item, InventoryMaterials.Shortage> shortages = player.isCreative()
				? Map.of()
				: inventorySnapshot.shortages(plan.required);
			String availability = shortages.isEmpty() ? "Materials available." : shortageMessage(shortages);
			return Result.success("Calculate preview"
				+ "\n  Eligible: " + formatNumber(search.targets.size()) + " blocks from " + filter.canonical()
				+ "\n  Existing:" + formatExisting(player.level(), search.targets)
				+ "\n  Proposed:" + formatDistributionLines(desiredCounts, search.targets.size(), unchanged)
				+ "\n  Would change: " + formatNumber(plan.changed)
				+ "\n  Net inventory needed: " + formatRequirements(plan.required, inventory)
				+ "\n  Returned: " + formatItems(plan.returned)
				+ "\n  Status: " + availability
				+ "\n  Apply: `/blocktone calc apply to:" + materials.canonical()
				+ (filter.isDefault() ? "`" : " from:" + filter.canonical() + "`"));
		}

		Map<Item, InventoryMaterials.Shortage> shortages = player.isCreative() || plan.required.isEmpty()
			? Map.of()
			: inventorySnapshot.shortages(plan.required);
		if (!shortages.isEmpty()) return Result.error(shortageMessage(shortages));
		int changed = execute(player, plan, inventorySnapshot);
		if (changed < 0) return Result.error(editFailure(changed));
		return Result.success("Calculate apply complete"
			+ "\n  Eligible: " + formatNumber(search.targets.size()) + " blocks from " + filter.canonical()
			+ "\n  Changed: " + formatNumber(changed)
			+ "\n  Applied:" + formatDistributionLines(desiredCounts, search.targets.size(), unchanged)
			+ "\n  Returned: " + formatItems(plan.returned));
	}

	public static Map<Block, Integer> calculatedCounts(
		final int targetCount,
		final OverlayPalette materials,
		final Map<Item, Integer> inventory,
		final Map<Block, Integer> existing
	) {
		return calculatedCounts(targetCount, materials, inventory, existing, false);
	}

	private static Map<Block, Integer> calculatedCounts(
		final int targetCount,
		final OverlayPalette materials,
		final Map<Item, Integer> inventory,
		final Map<Block, Integer> existing,
		final boolean infiniteMaterials
	) {
		OverlayPalette.Allocation allocation = materials.allocation(targetCount);
		Map<Block, Integer> result = new LinkedHashMap<>(allocation.explicitCounts());
		List<Block> automatic = materials.automaticBlocks();
		for (Block block : automatic) result.putIfAbsent(block, 0);
		int capacity = allocation.automaticSlots();
		if (capacity <= 0 || automatic.isEmpty()) return result;

		if (infiniteMaterials) {
			int base = capacity / automatic.size();
			int extra = capacity % automatic.size();
			for (int index = 0; index < automatic.size(); index++) {
				result.merge(automatic.get(index), base + (index < extra ? 1 : 0), Integer::sum);
			}
			return result;
		}

        // Automatic air is a free fallback, after ordinary carried/placed materials fit.
        List<Block> itemMaterials = automatic.stream().filter(block -> block != net.minecraft.world.level.block.Blocks.AIR).toList();
		Map<Block, Integer> carried = new LinkedHashMap<>();
		for (Block block : itemMaterials) carried.put(block, Math.max(0, inventory.getOrDefault(block.asItem(), 0)));
		Map<Block, Integer> fittedInventory = fitProportionally(carried, capacity);
		mergeCounts(result, fittedInventory);
		int inventoryUsed = fittedInventory.values().stream().mapToInt(Integer::intValue).sum();

		int remaining = capacity - inventoryUsed;
		if (remaining > 0) {
			Map<Block, Integer> placed = new LinkedHashMap<>();
			for (Block block : itemMaterials) placed.put(block, Math.max(0, existing.getOrDefault(block, 0)));
            Map<Block, Integer> retained = preserveRareExisting(placed, remaining);
			mergeCounts(result, retained);
            remaining -= retained.values().stream().mapToInt(Integer::intValue).sum();
		}
        if (automatic.contains(net.minecraft.world.level.block.Blocks.AIR) && remaining > 0) {
            result.merge(net.minecraft.world.level.block.Blocks.AIR, remaining, Integer::sum);
        }
		return result;
	}

	private static Map<Block, Integer> fitProportionally(
		final Map<Block, Integer> available,
		final int capacity
	) {
		Map<Block, Integer> result = new LinkedHashMap<>();
		long total = available.values().stream().mapToLong(Integer::longValue).sum();
		if (total <= capacity) {
			result.putAll(available);
			return result;
		}
		if (capacity <= 0 || total == 0) return result;

		List<ScaledQuota> quotas = new ArrayList<>();
		int allocated = 0;
		int index = 0;
		for (Map.Entry<Block, Integer> entry : available.entrySet()) {
			long product = (long) capacity * entry.getValue();
			int count = (int) (product / total);
			allocated += count;
			quotas.add(new ScaledQuota(index++, entry.getKey(), count, product % total));
		}
		quotas.stream()
			.sorted(Comparator.comparingLong(ScaledQuota::remainder).reversed().thenComparingInt(ScaledQuota::index))
			.limit(capacity - allocated)
			.forEach(ScaledQuota::addOne);
		quotas.stream().sorted(Comparator.comparingInt(ScaledQuota::index)).forEach(quota ->
			result.put(quota.block, quota.count));
		return result;
	}

	/** Keeps scarce placed accents intact and trims the dominant existing filler first. */
	private static Map<Block, Integer> preserveRareExisting(
		final Map<Block, Integer> existing,
		final int capacity
	) {
		Map<Block, Integer> result = new LinkedHashMap<>();
		long total = existing.values().stream().mapToLong(Integer::longValue).sum();
		if (total <= capacity) {
			result.putAll(existing);
			return result;
		}
		if (capacity <= 0) return result;

		int low = 0;
		int high = existing.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		while (low < high) {
			int middle = low + (high - low + 1) / 2;
			long capped = existing.values().stream().mapToLong(value -> Math.min(value, middle)).sum();
			if (capped <= capacity) low = middle;
			else high = middle - 1;
		}
		int used = 0;
		for (Map.Entry<Block, Integer> entry : existing.entrySet()) {
			int count = Math.min(entry.getValue(), low);
			result.put(entry.getKey(), count);
			used += count;
		}
		int extra = capacity - used;
		for (Map.Entry<Block, Integer> entry : existing.entrySet()) {
			if (extra == 0) break;
			if (result.get(entry.getKey()) < entry.getValue()) {
				result.merge(entry.getKey(), 1, Integer::sum);
				extra--;
			}
		}
		return result;
	}

	private static void mergeCounts(final Map<Block, Integer> target, final Map<Block, Integer> source) {
		for (Map.Entry<Block, Integer> entry : source.entrySet()) {
			target.merge(entry.getKey(), entry.getValue(), Integer::sum);
		}
	}

	private static TargetSearch findTargets(final ServerPlayer player, final BlockFilter filter) {
		ServerLevel level = player.level();
		SelectionState selection = BlockToolsMod.STATES.selection(player.getUUID());
		if (selection.points().size() != 2 || !selection.belongsTo(level)) {
			return TargetSearch.error("Select two opposite corners in this dimension with the selection item first.");
		}

		List<BlockPos> volume;
		try {
			volume = cuboid(selection.points(), BlockToolsMod.CONFIG.config().maxToneBlocks);
		} catch (IllegalArgumentException exception) {
			return TargetSearch.error(exception.getMessage());
		}

		List<BlockPos> targets = new ArrayList<>();
		for (BlockPos pos : volume) {
			if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) {
				return TargetSearch.error("The selection includes an unloaded or out-of-bounds block at " + coordinates(pos) + ".");
			}
			if (!BuildPermissions.canChange(player, pos)) {
				return TargetSearch.error("You cannot modify the selection at " + coordinates(pos) + ".");
			}
			BlockState state = level.getBlockState(pos);
			if (BlockMaterials.isMultipart(state)) continue;
            if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && !filter.includesLiquids()) continue;
			if (state.isAir() && !filter.includesAir()) continue;
			if (!filter.matches(state.getBlock())) continue;
			if (level.getBlockEntity(pos) != null) continue;
			if (state.getDestroySpeed(level, pos) < 0) continue;
			targets.add(pos.immutable());
		}
		if (targets.isEmpty()) return TargetSearch.error("No safe blocks in the selected cuboid match source filter '" + filter.canonical() + "'.");
		return new TargetSearch(List.copyOf(targets), null);
	}

	private static ReplacementPlan createPlan(
		final ServerLevel level,
		final List<BlockPos> targets,
		final Map<Block, Integer> desiredCounts,
		final long seed
	) {
		List<Block> existingBlocks = targets.stream().map(pos -> ReplacementStates.materialBlock(level.getBlockState(pos).getBlock())).toList();
		List<Block> assignments = assignments(existingBlocks, desiredCounts, seed);
		return createReplacementPlan(level, targets, assignments);
	}

	private static ReplacementPlan createOverlayPlan(
		final ServerLevel level,
		final List<BlockPos> targets,
		final Map<Block, Integer> materialCounts,
		final long seed
	) {
		List<Block> existingBlocks = targets.stream().map(pos -> ReplacementStates.materialBlock(level.getBlockState(pos).getBlock())).toList();
		List<Block> assignments = overlayAssignments(existingBlocks, materialCounts, seed);
		return createReplacementPlan(level, targets, assignments);
	}

	private static ReplacementPlan createReplacementPlan(
		final ServerLevel level,
		final List<BlockPos> targets,
		final List<Block> assignments
	) {

		Map<Item, Integer> grossRequired = new LinkedHashMap<>();
		Map<Item, Integer> grossReturned = new LinkedHashMap<>();
		int changed = 0;
        List<BlockState> states = new ArrayList<>(targets.size());
		for (int i = 0; i < targets.size(); i++) {
            BlockState source = level.getBlockState(targets.get(i));
            BlockState destination = ReplacementStates.preserve(source, assignments.get(i));
            states.add(destination);
			Block existing = source.getBlock();
			if (source.equals(destination)) continue;
			changed++;
			int neededItems = BlockMaterials.itemCount(destination);
            if (neededItems > 0) grossRequired.merge(destination.getBlock().asItem(), neededItems, Integer::sum);
			if (existing.asItem() != net.minecraft.world.item.Items.AIR) {
				grossReturned.merge(existing.asItem(), BlockMaterials.itemCount(level.getBlockState(targets.get(i))), Integer::sum);
			}
		}
		MaterialLedger.Delta net = MaterialLedger.net(grossRequired, grossReturned);
		return new ReplacementPlan(List.copyOf(targets), List.copyOf(states), net.required(), net.returned(), changed);
	}

	public static List<Block> overlayAssignments(
		final List<Block> existingBlocks,
		final Map<Block, Integer> materialCounts,
		final long seed
	) {
		int assignedTotal = materialCounts.values().stream().mapToInt(Integer::intValue).sum();
		if (assignedTotal > existingBlocks.size()) {
			throw new IllegalArgumentException("Overlay material count cannot exceed the number of target blocks.");
		}
		if (materialCounts.values().stream().anyMatch(count -> count < 0)) {
			throw new IllegalArgumentException("Overlay material counts cannot be negative.");
		}

		List<Block> assignments = new ArrayList<>(existingBlocks);
		Map<Block, Integer> remaining = new LinkedHashMap<>(materialCounts);
		List<Integer> targetOrder = new ArrayList<>(existingBlocks.size());
		for (int i = 0; i < existingBlocks.size(); i++) targetOrder.add(i);
		Random random = new Random(seed);
		Collections.shuffle(targetOrder, random);
		boolean[] selected = new boolean[existingBlocks.size()];
		for (int index : targetOrder) {
			Block existing = existingBlocks.get(index);
			int quota = remaining.getOrDefault(existing, 0);
			if (quota <= 0) continue;
			selected[index] = true;
			remaining.put(existing, quota - 1);
		}

		List<Block> destinations = new ArrayList<>();
		for (Map.Entry<Block, Integer> entry : remaining.entrySet()) {
			for (int i = 0; i < entry.getValue(); i++) destinations.add(entry.getKey());
		}
		Collections.shuffle(destinations, random);
		List<Integer> openPositions = targetOrder.stream().filter(index -> !selected[index]).toList();
		for (int i = 0; i < destinations.size(); i++) assignments.set(openPositions.get(i), destinations.get(i));
		return List.copyOf(assignments);
	}

	public static List<Block> assignments(
		final List<Block> existingBlocks,
		final Map<Block, Integer> desiredCounts,
		final long seed
	) {
		int desiredTotal = desiredCounts.values().stream().mapToInt(Integer::intValue).sum();
		if (desiredTotal != existingBlocks.size()) {
			throw new IllegalArgumentException("Desired material count must equal the number of target blocks.");
		}
		if (desiredCounts.values().stream().anyMatch(count -> count < 0)) {
			throw new IllegalArgumentException("Desired material counts cannot be negative.");
		}

		List<Block> assignments = new ArrayList<>(Collections.nCopies(existingBlocks.size(), null));
		Map<Block, Integer> remaining = new LinkedHashMap<>(desiredCounts);
		List<Integer> targetOrder = new ArrayList<>(existingBlocks.size());
		for (int i = 0; i < existingBlocks.size(); i++) targetOrder.add(i);
		Random random = new Random(seed);
		Collections.shuffle(targetOrder, random);
		for (int i : targetOrder) {
			Block existing = existingBlocks.get(i);
			int available = remaining.getOrDefault(existing, 0);
			if (available <= 0) continue;
			assignments.set(i, existing);
			remaining.put(existing, available - 1);
		}

		List<Block> unassigned = new ArrayList<>();
		for (Map.Entry<Block, Integer> entry : remaining.entrySet()) {
			for (int i = 0; i < entry.getValue(); i++) unassigned.add(entry.getKey());
		}
		Collections.shuffle(unassigned, random);
		int next = 0;
		for (int i = 0; i < assignments.size(); i++) {
			if (assignments.get(i) == null) assignments.set(i, unassigned.get(next++));
		}
		return List.copyOf(assignments);
	}

	public static List<BlockState> shuffledStates(final List<BlockState> existing, final long seed) {
		List<BlockState> shuffled = new ArrayList<>(existing);
		Collections.shuffle(shuffled, new Random(seed));
		if (!shuffled.equals(existing)) return List.copyOf(shuffled);

		for (int left = 0; left < shuffled.size(); left++) {
			for (int right = left + 1; right < shuffled.size(); right++) {
				if (shuffled.get(left).equals(shuffled.get(right))) continue;
				Collections.swap(shuffled, left, right);
				return List.copyOf(shuffled);
			}
		}
		return List.copyOf(shuffled);
	}

	private static int execute(final ServerPlayer player, final ReplacementPlan plan,
		final InventoryMaterials.InventorySnapshot inventorySnapshot) {
		ServerLevel level = player.level();
		Map<BlockPos, BlockState> edits = new LinkedHashMap<>();
		for (int i = 0; i < plan.targets.size(); i++) {
			BlockPos pos = plan.targets.get(i);
			BlockState destination = plan.states.get(i);
			if (!level.getBlockState(pos).equals(destination)) edits.put(pos, destination);
		}
        boolean[] unsupported = {false};
        if (!WorldEdits.apply(level, edits, () -> {
            // Validate against the whole staged edit, including simultaneously replaced supports.
            for (BlockPos pos : plan.targets) {
                if (!level.getBlockState(pos).canSurvive(level, pos)) {
                    unsupported[0] = true;
                    return false;
                }
            }
            return player.isCreative() || plan.required.isEmpty()
                || inventorySnapshot.consume(plan.required).isEmpty();
        })) return unsupported[0] ? -2 : -1;
		if (!plan.returned.isEmpty()) InventoryMaterials.give(player, plan.returned);
		return edits.size();
	}

    private static String editFailure(int code) {
        return code == -2
            ? "A destination has no valid support at its preserved position; nothing was changed. Choose materials with compatible mounting."
            : "The world edit could not be committed; nothing was changed.";
    }

	private static long selectionSeed(final ServerPlayer player) {
		long seed = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits();
		for (BlockPos point : BlockToolsMod.STATES.selection(player.getUUID()).points()) seed = seed * 31L + point.asLong();
		return seed;
	}

	private static String formatExisting(final ServerLevel level, final List<BlockPos> targets) {
		return formatCounts(existingCounts(level, targets));
	}

	private static Map<Block, Integer> existingCounts(final ServerLevel level, final List<BlockPos> targets) {
		Map<Block, Integer> counts = new LinkedHashMap<>();
		for (BlockPos pos : targets) counts.merge(ReplacementStates.materialBlock(level.getBlockState(pos).getBlock()), 1, Integer::sum);
		return counts;
	}

	private static Map<Block, Integer> existingCounts(final List<BlockState> states) {
		Map<Block, Integer> counts = new LinkedHashMap<>();
		for (BlockState state : states) counts.merge(state.getBlock(), 1, Integer::sum);
		return counts;
	}

	private static String formatDistributionLines(
		final Map<Block, Integer> counts,
		final int total,
		final int unchanged
	) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
			double percent = total == 0 ? 0.0 : entry.getValue() * 100.0 / total;
			parts.add("\n    - " + blockName(entry.getKey()) + ": " + formatNumber(entry.getValue()) + " ("
				+ String.format(Locale.ROOT, "%.1f", percent) + "%)");
		}
		if (unchanged > 0) {
			double percent = total == 0 ? 0.0 : unchanged * 100.0 / total;
			parts.add("\n    - unchanged: " + formatNumber(unchanged) + " ("
				+ String.format(Locale.ROOT, "%.1f", percent) + "%)");
		}
		return String.join("", parts);
	}

	private static String formatCounts(final Map<Block, Integer> counts) {
		return counts.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparing(ToneService::blockName)))
			.map(entry -> "\n    - " + blockName(entry.getKey()) + ": " + formatNumber(entry.getValue()))
			.reduce((left, right) -> left + right)
			.orElse("\n    - none");
	}

	private static String formatRequirements(final Map<Item, Integer> required, final Map<Item, Integer> inventory) {
		if (required.isEmpty()) return "none";
		return required.entrySet().stream()
			.map(entry -> new ItemStack(entry.getKey()).getHoverName().getString() + "=" + formatNumber(entry.getValue())
				+ "/" + formatNumber(inventory.getOrDefault(entry.getKey(), 0)) + " available")
			.reduce((left, right) -> left + ", " + right)
			.orElse("none");
	}

	private static String formatItems(final Map<Item, Integer> items) {
		if (items.isEmpty()) return "none";
		return items.entrySet().stream()
			.map(entry -> new ItemStack(entry.getKey()).getHoverName().getString() + "=" + formatNumber(entry.getValue()))
			.reduce((left, right) -> left + ", " + right)
			.orElse("none");
	}

	private static String blockName(final Block block) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toShortString();
	}

	private static String formatNumber(final int value) {
		return String.format(Locale.ROOT, "%,d", value);
	}

	public static List<BlockPos> cuboid(final List<BlockPos> points, final int maximum) {
		if (points.size() != 2) throw new IllegalArgumentException("Exactly two opposite corners are required.");
		int minX = points.stream().mapToInt(BlockPos::getX).min().orElseThrow();
		int maxX = points.stream().mapToInt(BlockPos::getX).max().orElseThrow();
		int minY = points.stream().mapToInt(BlockPos::getY).min().orElseThrow();
		int maxY = points.stream().mapToInt(BlockPos::getY).max().orElseThrow();
		int minZ = points.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
		int maxZ = points.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
		long width = (long) maxX - minX + 1;
		long height = (long) maxY - minY + 1;
		long depth = (long) maxZ - minZ + 1;
		if (maximum < 1 || width > maximum || height > maximum / width || depth > maximum / (width * height)) {
			throw new IllegalArgumentException("The cuboid exceeds the configured maximum of " + maximum + " blocks.");
		}
		long size = width * height * depth;
		if (size > maximum) throw new IllegalArgumentException("The cuboid contains " + size + " blocks; the configured maximum is " + maximum + ".");

		List<BlockPos> result = new ArrayList<>((int) size);
		for (long x = minX; x <= maxX; x++) {
			for (long y = minY; y <= maxY; y++) {
				for (long z = minZ; z <= maxZ; z++) result.add(new BlockPos((int) x, (int) y, (int) z));
			}
		}
		return result;
	}

	public static String shortageMessage(final Map<Item, InventoryMaterials.Shortage> shortages) {
		List<String> details = new ArrayList<>();
		for (Map.Entry<Item, InventoryMaterials.Shortage> entry : shortages.entrySet()) {
			InventoryMaterials.Shortage shortage = entry.getValue();
			String name = new ItemStack(entry.getKey()).getHoverName().getString();
			details.add(name + ": need " + shortage.required() + ", have " + shortage.available() + " (missing " + shortage.missing() + ")");
		}
		return "Not enough materials; nothing was changed.\n  - " + String.join("\n  - ", details);
	}

	private static String coordinates(final BlockPos pos) {
		return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
	}

	private record TargetSearch(List<BlockPos> targets, String error) {
		private static TargetSearch error(final String error) {
			return new TargetSearch(List.of(), error);
		}
	}

	private record ReplacementPlan(
		List<BlockPos> targets,
		List<BlockState> states,
		Map<Item, Integer> required,
		Map<Item, Integer> returned,
		int changed
	) {
	}

	private static final class ScaledQuota {
		private final int index;
		private final Block block;
		private int count;
		private final long remainder;

		private ScaledQuota(final int index, final Block block, final int count, final long remainder) {
			this.index = index;
			this.block = block;
			this.count = count;
			this.remainder = remainder;
		}

		private int index() {
			return index;
		}

		private long remainder() {
			return remainder;
		}

		private void addOne() {
			count++;
		}
	}

	public record Result(boolean success, Component message) {
		public static Result success(final String message) {
			return new Result(true, BlockToolsMod.toneMessage(message));
		}

		public static Result error(final String message) {
			return new Result(false, BlockToolsMod.toneError(message));
		}
	}
}
