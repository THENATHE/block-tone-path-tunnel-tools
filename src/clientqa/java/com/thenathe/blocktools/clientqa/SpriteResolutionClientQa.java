package com.thenathe.blocktools.clientqa;

import com.thenathe.blocktools.BlockToolsMod;
import com.thenathe.blocktools.client.BlockToolsClient;
import com.thenathe.blocktools.sprite.SpritePreferences;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.RandomSource;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Real resource reload, packets, commands, native item rendering, and world-save persistence. */
public final class SpriteResolutionClientQa implements FabricClientGameTest {
    private final List<String> evidence = new ArrayList<>();
    @Override public void runTest(ClientGameTestContext context) {
        List<String> originalPacks = context.computeOnClient(client -> client.getResourcePackRepository().getSelectedIds().stream().filter(id -> !id.startsWith("file/blocktools-qa")).toList());
        net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext world = null;
        try {
            context.runOnClient(client -> client.options.pauseOnLostFocus = false);
            world = context.worldBuilder().create();
            world.getConnection().waitForChunksDownload();
            context.waitTicks(25);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                int slot = 0;
                for (String name : new String[] {"mortar_trowel", "garden_trowel", "prospecting_pick"})
                    player.getInventory().setItem(slot++, new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, name)).orElseThrow()));
                player.inventoryMenu.broadcastChanges();
            });
            world.getConnection().waitForClientboundPackets();
            choose(context, "auto", false);
            render(context, false);
            context.takeScreenshot("sprites-auto16");
            check(context.computeOnClient(client -> !BlockToolsClient.detectHigh(client.getResourceManager())), "default effective vanilla textures detect16");
            makePack(context, "blocktools-qa32", 3, 32);
            makePack(context, "blocktools-qa-mixed", 1, 32);
            makePack(context, "blocktools-qa64", 3, 64);
            reload(context, originalPacks, "file/blocktools-qa32");
            context.waitFor(client -> BlockToolsClient.high(), 200);
            render(context, true);
            check(true, "auto reacts to actual32 resource-pack reload");
            context.takeScreenshot("sprites-auto32");
            choose(context, "16x16", false);
            render(context, false);
            reload(context, originalPacks, "file/blocktools-qa64");
            context.waitTicks(25);
            check(!context.computeOnClient(client -> BlockToolsClient.high()), "manual16 wins over64 client pack report");
            choose(context, "auto", true);
            render(context, true);
            check(true, "64pack selects highest available32 in auto");
            reload(context, originalPacks, "file/blocktools-qa-mixed");
            context.waitFor(client -> !BlockToolsClient.high(), 200);
            render(context, false);
            check(true, "mixed pack uses conservative16 fallback");
            choose(context, "32x32", true);
            reload(context, originalPacks, null);
            context.waitTicks(25);
            check(context.computeOnClient(client -> BlockToolsClient.high()), "manual32 remains selected after return to16 pack");
            render(context, true);
            var saved = world.getWorldSave();
            world.close();
            world = null;
            check(!context.computeOnClient(client -> BlockToolsClient.high()), "client effective mode resets on disconnect");
            world = saved.open();
            world.getConnection().waitForChunksDownload();
            context.waitTicks(25);
            context.waitFor(client -> BlockToolsClient.high(), 200);
            check(true, "manual32 survives actual integrated-server stop/reopen and reconnect");
            render(context, true);
            choose(context, "auto", false);
            render(context, false);
            evidence.add("RESULT: PASS; " + evidence.size() + " sprite assertions");
            Files.write(Path.of("sprite-clientqa-result.txt"), evidence);
        } catch (Throwable failure) {
            evidence.add("RESULT: FAIL; " + failure);
            try { Files.write(Path.of("sprite-clientqa-result.txt"), evidence); } catch (Exception ignored) { }
            throw new AssertionError("Sprite resolution client QA failed", failure);
        } finally {
            if (world != null) world.close();
        }
    }
    private void choose(ClientGameTestContext context, String option, boolean high) {
        context.runOnClient(client -> client.player.connection.sendCommand("blocktools " + option));
        context.waitTicks(4);
        context.waitFor(client -> BlockToolsClient.high() == high, 200);
        check(true, "actual personal command selects " + option + " -> " + (high ? 32 : 16));
    }
    private void render(ClientGameTestContext context, boolean high) {
        context.runOnClient(client -> {
            for (String name : new String[] {"mortar_trowel", "garden_trowel", "prospecting_pick"}) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, name)).orElseThrow());
                var state = new ItemStackRenderState();
                client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, client.level, client.player, 0);
                var sprite = state.pickParticleMaterial(RandomSource.create(1)).sprite().contents();
                check(sprite.name().equals(Identifier.fromNamespaceAndPath(BlockToolsMod.MOD_ID, "item/" + name + (high ? "_32" : "")))
                    && sprite.width() == (high ? 32 : 16), "native renderer chose " + sprite.name() + " at" + sprite.width());
            }
        });
    }
    private static void makePack(ClientGameTestContext context, String name, int count, int size) throws Exception {
        Path pack = context.computeOnClient(client -> client.getResourcePackDirectory()).resolve(name);
        Path textures = pack.resolve("assets/minecraft/textures/item");
        Files.createDirectories(textures);
        var format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        String range = "[" + format.major() + "," + format.minor() + "]";
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"Disposable Blocktools QA texture size fixture\",\"min_format\":" + range + ",\"max_format\":" + range + "}}");
        String[] names = {"iron_ingot", "copper_ingot", "flint"};
        for (int i = 0; i < count; i++) {
            var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            for (int x=0;x<size;x++) for(int y=0;y<size;y++) image.setRGB(x,y, ((x+y)%2==0) ? 0xffc0c0c0 : 0xff808080);
            ImageIO.write(image, "png", textures.resolve(names[i] + ".png").toFile());
        }
    }
    private static void reload(ClientGameTestContext context, List<String> original, String additional) {
        var future = context.computeOnClient(client -> {
            var selected = new ArrayList<>(original);
            if (additional != null) selected.add(additional);
            client.getResourcePackRepository().reload();
            client.getResourcePackRepository().setSelected(selected);
            return client.reloadResourcePacks();
        });
        context.waitFor(client -> future.isDone(), 1200);
        future.join();
        context.setScreen(() -> null);
        context.waitFor(client -> client.gui.overlay() == null && !client.isPaused(), 1200);
        context.waitTicks(40);
    }
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        evidence.add("PASS: " + message);
    }
}
