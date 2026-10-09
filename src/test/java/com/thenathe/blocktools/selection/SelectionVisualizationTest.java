package com.thenathe.blocktools.selection;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Geometry properties protect accurate inclusive selections and bounded packet work. */
public final class SelectionVisualizationTest {
    private SelectionVisualizationTest() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        run();
        System.out.println("Selection visualization geometry checks passed.");
    }

    public static void run() {
        require(SelectionVisualization.outline(null, null).isEmpty(), "empty selection has no outline");
        require(SelectionVisualization.volume(null, null) == 0, "empty selection has no editable volume");
        BlockPos point = new BlockPos(-3, -64, 7);
        require(SelectionVisualization.volume(point, null) == 0, "first corner alone has no editable volume");
        require(SelectionVisualization.volume(null, point) == 0, "second corner alone has no editable volume");
        require(new HashSet<>(SelectionVisualization.outline(point, null)).equals(
            new HashSet<>(SelectionVisualization.outline(point, point))), "first corner outlines one full block");
        require(new HashSet<>(SelectionVisualization.outline(null, point)).equals(
            new HashSet<>(SelectionVisualization.outline(point, point))), "second corner outlines one full block");
        verify(point, point);
        require(SelectionVisualization.volume(point, point) == 1, "same corners select one block");
        verify(new BlockPos(-5, -64, -8), new BlockPos(12, -64, 21));
        require(SelectionVisualization.volume(new BlockPos(-5, -64, -8), new BlockPos(12, -64, 21)) == 540,
            "flat selection includes both boundary blocks");
        verify(new BlockPos(0, 0, 0), new BlockPos(0, 200, 0));
        verify(new BlockPos(-30_000_000, -64, -30_000_000), new BlockPos(30_000_000, 319, 30_000_000));
        BlockPos minimum = new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        BlockPos maximum = new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        verify(minimum, maximum);
        verify(maximum, maximum);
        require(SelectionVisualization.volume(minimum, maximum) == Long.MAX_VALUE, "huge volume saturates instead of overflowing");
        require(SelectionVisualization.volume(new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0))
            == 4_294_967_296L, "extreme one-dimensional span uses long arithmetic");
        Random random = new Random(20260909L);
        for (int i = 0; i < 100; i++) {
            BlockPos a = new BlockPos(random.nextInt(2001) - 1000, random.nextInt(384) - 64, random.nextInt(2001) - 1000);
            BlockPos b = new BlockPos(random.nextInt(2001) - 1000, random.nextInt(384) - 64, random.nextInt(2001) - 1000);
            verify(a, b);
            long expected = (Math.abs((long) a.getX() - b.getX()) + 1)
                * (Math.abs((long) a.getY() - b.getY()) + 1) * (Math.abs((long) a.getZ() - b.getZ()) + 1);
            require(SelectionVisualization.volume(a, b) == expected, "random cuboid volume matches inclusive bounds");
        }
    }

    private static void verify(BlockPos a, BlockPos b) {
        List<SelectionVisualization.Point> points = SelectionVisualization.outline(a, b);
        require(!points.isEmpty() && points.size() <= 384, "outline has a fixed packet budget at every size");
        require(new HashSet<>(points).equals(new HashSet<>(SelectionVisualization.outline(b, a))), "corner order does not change geometry");
        double minX = Math.min(a.getX(), b.getX()), maxX = (double) Math.max(a.getX(), b.getX()) + 1;
        double minY = Math.min(a.getY(), b.getY()), maxY = (double) Math.max(a.getY(), b.getY()) + 1;
        double minZ = Math.min(a.getZ(), b.getZ()), maxZ = (double) Math.max(a.getZ(), b.getZ()) + 1;
        for (var p : points) {
            require(Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z()), "finite particle position");
            require(p.x() >= minX && p.x() <= maxX && p.y() >= minY && p.y() <= maxY && p.z() >= minZ && p.z() <= maxZ,
                "particle stays inside inclusive outer bounds");
            int boundaryAxes = (p.x() == minX || p.x() == maxX ? 1 : 0)
                + (p.y() == minY || p.y() == maxY ? 1 : 0) + (p.z() == minZ || p.z() == maxZ ? 1 : 0);
            require(boundaryAxes >= 2, "particle traces an edge, never face interior or volume interior");
        }
        for (double x : new double[]{minX, maxX}) for (double y : new double[]{minY, maxY}) for (double z : new double[]{minZ, maxZ}) {
            require(points.stream().anyMatch(p -> p.x() == x && p.y() == y && p.z() == z), "all eight outer corners are represented");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
