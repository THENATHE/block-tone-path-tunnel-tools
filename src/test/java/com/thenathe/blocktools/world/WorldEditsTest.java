package com.thenathe.blocktools.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WorldEditsTest {
    private WorldEditsTest() { }

    public static void run() {
        require(WorldEdits.STAGING_FLAGS == net.minecraft.world.level.block.Block.UPDATE_SKIP_ALL_SIDEEFFECTS,
            "staging permits neighbor, item-drop, or placement side effects");
        BlockPos a = BlockPos.ZERO;
        BlockPos b = a.above();
        Map<BlockPos, BlockState> desired = new LinkedHashMap<>();
        desired.put(a, Blocks.GRANITE.defaultBlockState());
        desired.put(b, Blocks.DIORITE.defaultBlockState());
        FakeWorld successful = new FakeWorld(desired, null);
        require(WorldEdits.apply(successful, desired), "batch edit failed");
        require(successful.notifications == 2, "changed positions were not notified");
        require(successful.states.equals(desired), "committed states differ from plan");
        require(WorldEdits.apply(successful, desired), "unchanged batch failed");
        require(successful.notifications == 2, "unchanged positions emitted callbacks");

        FakeWorld failing = new FakeWorld(desired, b);
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>(failing.states);
        require(!WorldEdits.apply(failing, desired), "rejected write was treated as success");
        require(failing.states.equals(originals), "rejected second write did not restore the first block");
        require(failing.notifications == 0, "partial edit ran neighbor callbacks before rollback");

        FakeWorld changedState = new FakeWorld(desired, null);
        changedState.corrupt = b;
        require(!WorldEdits.apply(changedState, desired), "successful set with wrong actual state was accepted");
        require(changedState.states.equals(originals), "incorrect state did not roll back");
        require(changedState.notifications == 0, "incorrect state ran notifications");

        FakeWorld crossWrite = new FakeWorld(desired, null);
        crossWrite.mutateEarlier = b;
        require(!WorldEdits.apply(crossWrite, desired), "later write changed an earlier state without detection");
        require(crossWrite.states.equals(originals) && crossWrite.notifications == 0, "cross-write mismatch was not restored");

        FakeWorld rejectedReservation = new FakeWorld(desired, null);
        require(!WorldEdits.apply(rejectedReservation, desired, () -> false), "rejected reservation committed");
        require(rejectedReservation.states.equals(originals), "rejected reservation left staged blocks");
        require(rejectedReservation.notifications == 0, "rejected reservation ran notifications");

        FakeWorld reservation = new FakeWorld(desired, null);
        reservation.requireCommit = true;
        require(WorldEdits.apply(reservation, desired, () -> {
            require(reservation.states.equals(desired), "materials charged before all states were staged");
            require(reservation.notifications == 0, "callbacks ran before reservation");
            reservation.committed = true;
            return true;
        }), "staged reservation failed");

        FakeWorld exception = new FakeWorld(desired, null);
        try {
            WorldEdits.apply(exception, desired, () -> { throw new IllegalStateException("reservation failure"); });
            throw new AssertionError("commit exception swallowed");
        } catch (IllegalStateException expected) { }
        require(exception.states.equals(originals) && exception.notifications == 0, "reservation exception left a partial edit");
    }

    private static final class FakeWorld implements WorldEdits.Access {
        final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> expected;
        final BlockPos rejected;
        int notifications;
        BlockPos corrupt;
        BlockPos mutateEarlier;
        boolean requireCommit;
        boolean committed;
        FakeWorld(Map<BlockPos, BlockState> expected, BlockPos rejected) {
            this.expected = expected;
            this.rejected = rejected;
            for (BlockPos pos : expected.keySet()) states.put(pos, Blocks.STONE.defaultBlockState());
        }
        public BlockState get(BlockPos pos) { return states.get(pos); }
        public boolean set(BlockPos pos, BlockState state) {
            if (pos.equals(rejected) && !state.is(Blocks.STONE)) return false;
            states.put(pos, pos.equals(corrupt) && !state.is(Blocks.STONE) ? Blocks.ANDESITE.defaultBlockState() : state);
            if (pos.equals(mutateEarlier) && !state.is(Blocks.STONE)) {
                states.put(expected.keySet().iterator().next(), Blocks.ANDESITE.defaultBlockState());
            }
            return true;
        }
        public void notify(BlockPos pos, BlockState oldState) {
            require(states.equals(expected), "neighbor callback ran while another planned cell retained its original state");
            require(!requireCommit || committed, "notifications ran before material commit");
            notifications++;
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
