package com.thenathe.blocktools.selection;

import com.mojang.brigadier.context.CommandContext;
import com.thenathe.blocktools.BlockToolsMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Temporary, player-private vanilla particle previews; never edits blocks or reserves items. */
public final class SelectionVisualization {
    public enum Kind {
        SELECTION("blocktone", "Block Toning Tool", ChatFormatting.LIGHT_PURPLE),
        PATH("pathtool", "Path Tool", ChatFormatting.AQUA),
        TUNNEL("tunneltool", "Tunnel Tool", ChatFormatting.DARK_AQUA);
        final String command;
        final String label;
        final ChatFormatting color;
        Kind(String command, String label, ChatFormatting color) {
            this.command = command; this.label = label; this.color = color;
        }
    }
    private record Active(int started, String dimension) { }
    public record Point(double x, double y, double z) { }
    private static final Map<UUID, EnumMap<Kind, Active>> ACTIVE = new HashMap<>();
    private static final int DURATION = 400;
    private static final DustParticleOptions FIRST = new DustParticleOptions(0x55FF55, 1.5F);
    private static final DustParticleOptions SECOND = new DustParticleOptions(0xFF55FF, 1.5F);

    private SelectionVisualization() { }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (UUID id : List.copyOf(ACTIVE.keySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) remove(id);
                else refresh(player, server.getTickCount());
            }
        });
    }

    public static boolean show(ServerPlayer player) { return show(player, Kind.SELECTION); }
    public static boolean showPath(ServerPlayer player) { return show(player, Kind.PATH); }
    public static boolean showTunnel(ServerPlayer player) { return show(player, Kind.TUNNEL); }
    public static void hide(UUID id) { hide(id, Kind.SELECTION); }
    public static void hidePath(UUID id) { hide(id, Kind.PATH); }
    public static void hideTunnel(UUID id) { hide(id, Kind.TUNNEL); }
    public static void remove(UUID id) { ACTIVE.remove(id); }
    public static void clear() { ACTIVE.clear(); }

    private static boolean show(ServerPlayer player, Kind kind) {
        if (!available(player, kind)) return false;
        ACTIVE.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Kind.class))
            .put(kind, new Active(player.level().getServer().getTickCount(), dimension(player)));
        refresh(player, player.level().getServer().getTickCount());
        MutableComponent buttons = Component.literal("[" + kind.label + "] Preview for 20 seconds. ").withStyle(kind.color);
        buttons.append(button("[Refresh]", "/" + kind.command + " visualize", ChatFormatting.GREEN));
        buttons.append(Component.literal(" "));
        buttons.append(button("[Hide]", "/" + kind.command + " visualize off", ChatFormatting.GRAY));
        if (kind == Kind.SELECTION) {
            buttons.append(Component.literal(" "));
            buttons.append(button("[Clear selection]", "/blocktone clear", ChatFormatting.RED));
        }
        player.sendSystemMessage(buttons);
        return true;
    }

    public static void renewSelection(ServerPlayer player) {
        if (!available(player, Kind.SELECTION)) return;
        int tick = player.level().getServer().getTickCount();
        var entries = ACTIVE.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Kind.class));
        Active previous = entries.put(Kind.SELECTION, new Active(tick, dimension(player)));
        if (previous == null || tick - previous.started() >= DURATION) refresh(player, tick);
    }

    private static Component button(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withColor(color)
            .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static void hide(UUID id, Kind kind) {
        var active = ACTIVE.get(id);
        if (active == null) return;
        active.remove(kind);
        if (active.isEmpty()) ACTIVE.remove(id);
    }

    public static int command(CommandContext<CommandSourceStack> context, Kind kind, boolean visible) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(BlockToolsMod.error("This command must be run by a player."));
            return 0;
        }
        if (visible) {
            if (show(player, kind)) return 1;
            context.getSource().sendFailure(BlockToolsMod.error(kind == Kind.SELECTION
                ? "Select at least one corner in this dimension first."
                : "Configure the " + kind.label + " first; it may be paused while previewing."));
            return 0;
        }
        hide(player.getUUID(), kind);
        player.sendSystemMessage(Component.empty(), true);
        context.getSource().sendSuccess(() -> BlockToolsMod.message(kind.label + " preview hidden; settings retained."), false);
        return 1;
    }

    private static boolean available(ServerPlayer player, Kind kind) {
        if (!player.isAlive()) return false;
        return switch (kind) {
            case SELECTION -> {
                var selection = BlockToolsMod.STATES.selection(player.getUUID());
                yield selection.belongsTo(player.level()) && !selection.points().isEmpty();
            }
            case PATH -> BlockToolsMod.STATES.path(player.getUUID()).palette != null;
            case TUNNEL -> BlockToolsMod.TUNNELS.state(player.getUUID()).configured();
        };
    }

    public static boolean refresh(ServerPlayer player, int tick) {
        var active = ACTIVE.get(player.getUUID());
        if (active == null) return false;
        active.entrySet().removeIf(entry -> tick - entry.getValue().started() >= DURATION
            || !dimension(player).equals(entry.getValue().dimension()) || !available(player, entry.getKey()));
        if (active.isEmpty()) {
            ACTIVE.remove(player.getUUID());
            player.sendSystemMessage(Component.empty(), true);
            return false;
        }
        List<String> labels = new ArrayList<>();
        for (Kind kind : active.keySet()) labels.add(switch (kind) {
            case SELECTION -> selection(player);
            case PATH -> ToolPreviews.path(player);
            case TUNNEL -> ToolPreviews.tunnel(player);
        });
        player.sendSystemMessage(Component.literal(String.join(" | ", labels)).withStyle(ChatFormatting.YELLOW), true);
        return true;
    }

    private static String dimension(ServerPlayer player) { return player.level().dimension().identifier().toString(); }

    private static String selection(ServerPlayer player) {
        var selection = BlockToolsMod.STATES.selection(player.getUUID());
        BlockPos a = selection.pointOne(), b = selection.pointTwo();
        for (Point p : outline(a, b)) point(player, ParticleTypes.END_ROD, p.x, p.y, p.z);
        marker(player, a, FIRST);
        marker(player, b, SECOND);
        if (a == null || b == null) return "Block Toning Tool: Point " + (a == null ? "2" : "1") + " set; select the other corner";
        long x = Math.abs((long) a.getX() - b.getX()) + 1;
        long y = Math.abs((long) a.getY() - b.getY()) + 1;
        long z = Math.abs((long) a.getZ() - b.getZ()) + 1;
        long count = volume(a, b);
        return "Block Toning Tool: " + x + " × " + y + " × " + z + " · " + String.format(java.util.Locale.ROOT, "%,d", count)
            + " blocks" + (count > BlockToolsMod.CONFIG.config().maxToneBlocks ? " · OVER LIMIT" : "");
    }

    private static void marker(ServerPlayer player, BlockPos pos, ParticleOptions color) {
        if (pos == null) return;
        // Offset just outside each selected block so solid corners do not conceal every marker.
        for (Point p : outline(pos, pos)) point(player, color,
            p.x + (p.x == pos.getX() ? -.04 : .04),
            p.y + (p.y == pos.getY() ? -.04 : .04),
            p.z + (p.z == pos.getZ() ? -.04 : .04));
    }

    public static void point(ServerPlayer player, ParticleOptions type, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        double dx = x - player.getX(), dy = y - player.getY(), dz = z - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 128D * 128D) return;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!player.level().hasChunkAt(pos)) return;
        player.level().sendParticles(player, type, false, true, x, y, z, 1, 0, 0, 0, 0);
    }

    public static long volume(BlockPos a, BlockPos b) {
        if (a == null || b == null) return 0;
        try {
            return Math.multiplyExact(Math.multiplyExact(Math.abs((long) a.getX() - b.getX()) + 1,
                Math.abs((long) a.getY() - b.getY()) + 1), Math.abs((long) a.getZ() - b.getZ()) + 1);
        } catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    public static List<Point> outline(BlockPos a, BlockPos b) {
        if (a == null && b == null) return List.of();
        if (a == null) a = b;
        if (b == null) b = a;
        double[] lo = {Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())};
        double[] hi = {(double) Math.max(a.getX(), b.getX()) + 1, (double) Math.max(a.getY(), b.getY()) + 1, (double) Math.max(a.getZ(), b.getZ()) + 1};
        var points = new LinkedHashSet<Point>();
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            int segments = (int) Math.min(32, Math.ceil(hi[axis] - lo[axis]));
            for (int side = 0; side < 4; side++) for (int i = 0; i <= segments; i++) {
                double[] p = lo.clone();
                p[axis] = lo[axis] + (hi[axis] - lo[axis]) * i / segments;
                p[u] = (side & 1) == 0 ? lo[u] : hi[u];
                p[v] = (side & 2) == 0 ? lo[v] : hi[v];
                points.add(new Point(p[0], p[1], p[2]));
            }
        }
        return List.copyOf(points);
    }
}
