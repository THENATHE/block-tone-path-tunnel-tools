package com.thenathe.blocktools.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InventoryMaterials {
	private static final int SHULKER_SLOTS = 27;

	private InventoryMaterials() {
	}

	public static Map<Item, Shortage> shortages(final ServerPlayer player, final Map<Item, Integer> required) {
		return snapshot(player).shortages(required);
	}

	public static Map<Item, Integer> count(final ServerPlayer player) {
		return snapshot(player).counts();
	}

	public static Map<Item, Shortage> consume(final ServerPlayer player, final Map<Item, Integer> requested) {
		return snapshot(player).consume(requested);
	}

	public static InventorySnapshot snapshot(final ServerPlayer player) {
		return new InventorySnapshot(player, accessibleStacks(player));
	}

	static InventorySnapshot snapshot(final List<ItemStack> accessible) {
		return new InventorySnapshot(null, accessible);
	}

	public static void give(final ServerPlayer player, final Map<Item, Integer> returned) {
		for (Map.Entry<Item, Integer> entry : returned.entrySet()) {
			int remaining = entry.getValue();
			ItemStack template = new ItemStack(entry.getKey());
			int maximum = template.getOrDefault(DataComponents.MAX_STACK_SIZE, 64);
			while (remaining > 0) {
				ItemStack stack = new ItemStack(entry.getKey(), Math.min(maximum, remaining));
				remaining -= stack.getCount();
				player.getInventory().add(stack);
				if (!stack.isEmpty()) addToShulkers(player, stack);
				if (!stack.isEmpty()) {
					player.drop(stack.copy(), false, net.minecraft.util.Prediction.SERVER_ONLY);
					stack.setCount(0);
				}
			}
		}
		player.getInventory().setChanged();
		player.containerMenu.broadcastChanges();
	}

	private static void addToShulkers(final ServerPlayer player, final ItemStack incoming) {
		if (Block.byItem(incoming.getItem()) instanceof ShulkerBoxBlock) return;
		for (ItemStack shulker : accessibleStacks(player)) {
			if (!isShulker(shulker) || incoming.isEmpty()) continue;
			ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
			NonNullList<ItemStack> slots = copyContainerSlots(contents);
			boolean changed = false;
			for (int slot = 0; isUsableShulkerSlot(slot); slot++) {
				ItemStack stack = slots.get(slot);
				if (incoming.isEmpty()) break;
				if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, incoming)) continue;
				int maximum = stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 64);
				int moved = Math.min(maximum - stack.getCount(), incoming.getCount());
				if (moved <= 0) continue;
				stack.grow(moved);
				incoming.shrink(moved);
				changed = true;
			}
			for (int slot = 0; isUsableShulkerSlot(slot) && !incoming.isEmpty(); slot++) {
				if (!slots.get(slot).isEmpty()) continue;
				int maximum = incoming.getOrDefault(DataComponents.MAX_STACK_SIZE, 64);
				int moved = Math.min(maximum, incoming.getCount());
				slots.set(slot, incoming.copyWithCount(moved));
				incoming.shrink(moved);
				changed = true;
			}
			if (changed) shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
		}
	}

	private static List<ItemStack> accessibleStacks(final ServerPlayer player) {
		List<ItemStack> stacks = new ArrayList<>(player.getInventory().getNonEquipmentItems());
		ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
		if (!stacks.contains(offhand)) stacks.add(offhand);
		return stacks;
	}

	/** Bulk placement cannot carry names, container data, or other custom components into the world. */
	static boolean isPlainMaterial(final ItemStack stack) {
		return !stack.isEmpty() && stack.getComponentsPatch().isEmpty();
	}

	private static boolean isShulker(final ItemStack stack) {
		return Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
	}

	private static boolean isNonEmptyShulker(final ItemStack stack) {
		if (!isShulker(stack)) return false;
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		return contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent();
	}

	static Map<Item, Integer> countShulkerContents(final ItemContainerContents contents) {
		Map<Item, Integer> result = new LinkedHashMap<>();
		NonNullList<ItemStack> slots = copyContainerSlots(contents);
		for (int slot = 0; isUsableShulkerSlot(slot); slot++) {
			ItemStack stack = slots.get(slot);
			if (isPlainMaterial(stack)) result.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		return Map.copyOf(result);
	}

	static NonNullList<ItemStack> copyContainerSlots(final ItemContainerContents contents) {
		int storedSlots = contents == null ? 0 : (int) contents.itemCopies().count();
		NonNullList<ItemStack> slots = NonNullList.withSize(Math.max(SHULKER_SLOTS, storedSlots), ItemStack.EMPTY);
		if (contents != null) contents.copyInto(slots);
		return slots;
	}

	static boolean isUsableShulkerSlot(final int slot) {
		return slot >= 0 && slot < SHULKER_SLOTS;
	}

	public static final class InventorySnapshot {
		private final ServerPlayer player;
		private final List<ItemStack> looseStacks = new ArrayList<>();
		private final List<ShulkerInventory> shulkers = new ArrayList<>();
		private final Map<Item, Integer> counts;
		private boolean used;

		private InventorySnapshot(final ServerPlayer player, final List<ItemStack> accessible) {
			this.player = player;
			Map<Item, Integer> counted = new HashMap<>();
			for (ItemStack outer : accessible) {
				if (outer.isEmpty()) continue;
				if (!isNonEmptyShulker(outer)) {
					if (!isPlainMaterial(outer)) continue;
					looseStacks.add(outer);
					counted.merge(outer.getItem(), outer.getCount(), Integer::sum);
					continue;
				}
				NonNullList<ItemStack> slots = copyContainerSlots(outer.get(DataComponents.CONTAINER));
				shulkers.add(new ShulkerInventory(outer, slots));
				for (int slot = 0; isUsableShulkerSlot(slot); slot++) {
					ItemStack inner = slots.get(slot);
					if (isPlainMaterial(inner)) counted.merge(inner.getItem(), inner.getCount(), Integer::sum);
				}
			}
			counts = Map.copyOf(counted);
		}

		public Map<Item, Integer> counts() {
			return counts;
		}

		public Map<Item, Shortage> shortages(final Map<Item, Integer> required) {
			Map<Item, Shortage> shortages = new LinkedHashMap<>();
			for (Map.Entry<Item, Integer> entry : required.entrySet()) {
				int have = counts.getOrDefault(entry.getKey(), 0);
				if (have < entry.getValue()) shortages.put(entry.getKey(), new Shortage(entry.getValue(), have));
			}
			return Map.copyOf(shortages);
		}

		public Map<Item, Shortage> consume(final Map<Item, Integer> requested) {
			if (requested.values().stream().anyMatch(value -> value == null || value < 0)) {
				throw new IllegalArgumentException("Requested material counts cannot be negative or null.");
			}
			if (used) throw new IllegalStateException("This inventory snapshot has already been consumed.");
			used = true;
			Map<Item, Integer> remaining = new LinkedHashMap<>(requested);
			List<LooseRemoval> looseRemovals = new ArrayList<>();
			List<ShulkerChange> shulkerChanges = new ArrayList<>();

			for (ItemStack stack : looseStacks) {
				int wanted = remaining.getOrDefault(stack.getItem(), 0);
				if (wanted <= 0) continue;
				int taken = Math.min(wanted, stack.getCount());
				looseRemovals.add(new LooseRemoval(stack, taken));
				remaining.put(stack.getItem(), wanted - taken);
			}

			for (ShulkerInventory shulker : shulkers) {
				if (remaining.values().stream().noneMatch(value -> value > 0)) break;
				boolean changed = false;
				for (int slot = 0; isUsableShulkerSlot(slot); slot++) {
					ItemStack stack = shulker.slots.get(slot);
					if (!isPlainMaterial(stack)) continue;
					Item item = stack.getItem();
					int wanted = remaining.getOrDefault(item, 0);
					if (wanted <= 0) continue;
					int taken = Math.min(wanted, stack.getCount());
					stack.shrink(taken);
					remaining.put(item, wanted - taken);
					changed = true;
				}
				if (changed) {
					shulkerChanges.add(new ShulkerChange(shulker.outer, ItemContainerContents.fromItems(shulker.slots)));
				}
			}

			if (remaining.values().stream().anyMatch(value -> value > 0)) {
				Map<Item, Shortage> shortages = new LinkedHashMap<>();
				for (Map.Entry<Item, Integer> entry : requested.entrySet()) {
					int missing = remaining.getOrDefault(entry.getKey(), 0);
					if (missing > 0) {
						shortages.put(entry.getKey(), new Shortage(entry.getValue(), entry.getValue() - missing));
					}
				}
				return Map.copyOf(shortages);
			}

			for (LooseRemoval removal : looseRemovals) removal.stack.shrink(removal.count);
			for (ShulkerChange change : shulkerChanges) {
				change.shulker.set(DataComponents.CONTAINER, change.contents);
			}
			if (player != null) {
				player.getInventory().setChanged();
				player.containerMenu.broadcastChanges();
			}
			return Map.of();
		}
	}

	private record LooseRemoval(ItemStack stack, int count) {
	}

	private record ShulkerChange(ItemStack shulker, ItemContainerContents contents) {
	}

	private record ShulkerInventory(ItemStack outer, NonNullList<ItemStack> slots) {
	}

	public record Shortage(int required, int available) {
		public int missing() {
			return required - available;
		}
	}
}
