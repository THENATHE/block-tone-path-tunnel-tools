package com.thenathe.blocktools.qa;

import com.thenathe.blocktools.compat.ToolRecipes;
import net.minecraft.advancements.triggers.PlayerTrigger;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import com.thenathe.blocktools.compat.PolymerTools;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.ArrayList;
import java.util.List;

/** Real loaded recipes/advancements, crafted stacks and asynchronous configuration reloads. */
public final class LiveToolRecipesQa {
    private static int checks;
    private static final String[] IDS = {"mortar_trowel", "garden_trowel", "prospecting_pick"};
    private static final String[] NAMES = {"Block Toning Tool", "Path Tool", "Tunnel Tool"};

    public static int availability(ServerPlayer player, boolean enabled) {
        checks = 0;
        var server = player.level().getServer();
        for (String name : IDS) {
            var id = Identifier.fromNamespaceAndPath("blocktonepathtools", name);
            var key = ResourceKey.create(Registries.RECIPE, id);
            check(server.getRecipeManager().byKey(key).isPresent() == enabled, name + " recipe load matches integration mode");
            check((server.getAdvancements().get(Identifier.fromNamespaceAndPath("blocktonepathtools", "recipes/tools/" + name)) != null) == enabled,
                name + " advancement load matches integration mode");
            if (enabled) {
                CriteriaTriggers.TICK.trigger(player);
                check(player.getRecipeBook().contains(key), name + " unlocks for existing players after integration reload");
            }
        }
        if (PolymerTools.available()) checks += LivePolymerToolsQa.creativeAvailability(player, enabled);
        return checks;
    }

    public static int crafting(ServerPlayer player) {
        checks = 0;
        var server = player.level().getServer();
        var manager = server.getRecipeManager();
        List<List<Item>> grids = List.of(
            List.of(Items.AIR, Items.IRON_INGOT, Items.AIR, Items.IRON_INGOT, Items.IRON_INGOT, Items.AIR, Items.AIR, Items.STICK, Items.AIR),
            List.of(Items.AIR, Items.IRON_INGOT, Items.AIR, Items.AIR, Items.COPPER_INGOT, Items.AIR, Items.AIR, Items.STICK, Items.AIR),
            List.of(Items.IRON_INGOT, Items.IRON_INGOT, Items.FLINT, Items.AIR, Items.STICK, Items.AIR, Items.AIR, Items.STICK, Items.AIR));
        for (int index = 0; index < IDS.length; index++) {
            String name = IDS[index];
            var id = Identifier.fromNamespaceAndPath("blocktonepathtools", name);
            var key = ResourceKey.create(Registries.RECIPE, id);
            var holder = manager.byKey(key).orElseThrow();
            check(holder.value() instanceof CraftingRecipe, name + " uses crafting table");
            var recipe = (CraftingRecipe) holder.value();
            List<ItemStack> stacks = grids.get(index).stream().map(item -> item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item)).toList();
            var input = CraftingInput.of(3, 3, stacks);
            check(recipe.matches(input, player.level()), name + " exact ingredient layout matches");
            var match = manager.getRecipeFor(RecipeType.CRAFTING, input, player.level());
            check(match.isPresent() && match.get().id().equals(key), name + " selected by live recipe manager");
            long matches = manager.getRecipes().stream().filter(entry -> entry.value() instanceof CraftingRecipe crafting
                && crafting.matches(input, player.level())).count();
            check(matches == 1, name + " does not collide with any loaded vanilla recipe");
            var output = recipe.assemble(input);
            check(output.is(BuiltInRegistries.ITEM.getOptional(id).orElseThrow()) && output.getCount() == 1, name + " produces one real custom tool");
            check(output.getHoverName().getString().equals(NAMES[index]), name + " uses requested display name");
            for (int slot = 0; slot < stacks.size(); slot++) if (!stacks.get(slot).isEmpty()) {
                var wrong = new ArrayList<>(stacks);
                wrong.set(slot, new ItemStack(Items.DIAMOND));
                check(!recipe.matches(CraftingInput.of(3, 3, wrong), player.level()), name + " rejects wrong ingredient at " + slot);
                wrong.set(slot, ItemStack.EMPTY);
                check(!recipe.matches(CraftingInput.of(3, 3, wrong), player.level()), name + " rejects missing ingredient at " + slot);
            }
            var advancement = server.getAdvancements().get(Identifier.fromNamespaceAndPath("blocktonepathtools", "recipes/tools/" + name));
            check(advancement != null && advancement.value().rewards().recipes().equals(List.of(key)), name + " advancement unlocks its recipe");
            var criterion = advancement.value().criteria().get("automatic");
            check(criterion != null && criterion.triggerInstance() instanceof PlayerTrigger.TriggerInstance,
                name + " uses unconditional player tick unlock");
            // Exercise the real advancement listener, including a player with no ingredients.
            player.getInventory().clearContent();
            for (String criterionName : advancement.value().criteria().keySet()) {
                player.getAdvancements().revoke(advancement, criterionName);
            }
            player.getRecipeBook().remove(key);
            CriteriaTriggers.TICK.trigger(player);
            check(player.getRecipeBook().contains(key), name + " is in the crafting book after a tick with empty inventory");
            check(player.getAdvancements().getOrStartProgress(advancement).isDone(), name + " automatic advancement completes");
        }
        return checks;
    }

    /** Only the development harness pumps queued server work while awaiting asynchronous reloads. */
    public static void awaitReload(ServerPlayer player) {
        var future = ToolRecipes.pendingReload();
        player.level().getServer().managedBlock(future::isDone);
        future.join();
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
