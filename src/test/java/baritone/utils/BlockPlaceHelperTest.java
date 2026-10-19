/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class BlockPlaceHelperTest {
    private static BlockHitResult hit() {
        return new BlockHitResult(new Vec3(0.5, 1, 0.5), Direction.UP, new BlockPos(0, 0, 0), false);
    }

    @Test
    public void mainHandSuccessClientSwings() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Minecraft.startUseItem swings only when swingSource == CLIENT; SUCCESS is CLIENT.
        assertEquals(List.of(InteractionHand.MAIN_HAND), actions.swings);
        assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        // SUCCESS consumes the action — no useItem fallback and no offhand attempt.
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void successServerDoesNotSwingLocally() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS_SERVER);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // SUCCESS_SERVER has swingSource == SERVER; the server will send its own swing packet.
        assertTrue(actions.swings.isEmpty());
        // Still consumes the action.
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void consumeDoesNotSwing() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.CONSUME);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // CONSUME has swingSource == NONE; neither client nor server swings.
        assertTrue(actions.swings.isEmpty());
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void newlyConstructedSuccessIsRecognized() {
        // Regression: previous code used == InteractionResult.SUCCESS, which fails for any
        // Success built via the Success constructor (even one equal in value to the singleton).
        // withoutItem() returns `new Success(swingSource, ItemContext.NONE)` — a brand-new
        // instance that is NOT reference-equal to the SUCCESS singleton.
        InteractionResult.Success fresh = InteractionResult.SUCCESS.withoutItem();
        assertNotSame("withoutItem must produce a new Success instance", InteractionResult.SUCCESS, fresh);
        assertEquals(InteractionResult.SwingSource.CLIENT, fresh.swingSource());

        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, fresh);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(List.of(InteractionHand.MAIN_HAND), actions.swings);
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void constructedConsumeIsRecognized() {
        // A Success with SwingSource.NONE constructed independently of CONSUME.
        InteractionResult.Success fresh = InteractionResult.CONSUME.withoutItem();
        assertNotSame("withoutItem must produce a new Success instance", InteractionResult.CONSUME, fresh);
        assertEquals(InteractionResult.SwingSource.NONE, fresh.swingSource());

        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, fresh);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Still a Success -> action consumed. SwingSource NONE -> no local swing.
        assertTrue(actions.swings.isEmpty());
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void constructedSuccessServerIsRecognized() {
        // SwingSource.SERVER constructed independently of SUCCESS_SERVER.
        InteractionResult.Success fresh = InteractionResult.SUCCESS_SERVER.withoutItem();
        assertNotSame("withoutItem must produce a new Success instance", InteractionResult.SUCCESS_SERVER, fresh);
        assertEquals(InteractionResult.SwingSource.SERVER, fresh.swingSource());

        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, fresh);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Still a Success -> action consumed. SwingSource SERVER -> no local swing.
        assertTrue(actions.swings.isEmpty());
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void failOnBlockUseStopsImmediately() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.FAIL);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Vanilla returns from startUseItem on Fail: no useItem fallback, no offhand attempt.
        assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertTrue(actions.swings.isEmpty());
    }

    @Test
    public void passFallsThroughToUseItem() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, false);
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(List.of(InteractionHand.MAIN_HAND), actions.swings);
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void tryEmptyHandFallsThroughToUseItem() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.TRY_WITH_EMPTY_HAND);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, false);
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // TRY_WITH_EMPTY_HAND is not a Success and not a Fail -> same fallthrough path as PASS.
        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(List.of(InteractionHand.MAIN_HAND), actions.swings);
    }

    @Test
    public void emptyMainHandWithPassSkipsUseItemAndTriesOffhand() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, true);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Empty hand -> skip useItem and continue to the next hand.
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertEquals(List.of(InteractionHand.OFF_HAND), actions.swings);
    }

    @Test
    public void passOnBothUseItemOnAndUseItemFallsToOffhand() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, false);
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertEquals(List.of(InteractionHand.OFF_HAND), actions.swings);
    }

    @Test
    public void bothHandsPassResultsInNoSwing() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.PASS);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, true);
        actions.emptyHand.put(InteractionHand.OFF_HAND, true);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Both useItemOn attempts occur, neither Success. No swings.
        assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertTrue(actions.swings.isEmpty());
    }

    @Test
    public void successCooldownRunsBeforeNextAttempt() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.placeSpeed = 4;
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);
        assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));

        // Three waiting ticks: placeSpeed(4) - BASE_PLACE_DELAY(1) = 3.
        for (int i = 0; i < 3; i++) {
            helper.tick(true);
            assertEquals("tick " + i + " must not reattempt", 1,
                    actions.useItemOnCount(InteractionHand.MAIN_HAND));
        }
        helper.tick(true);
        assertEquals(2, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void placeSpeedOfOneAttemptsEveryTick() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.placeSpeed = 1;
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);
        helper.tick(true);
        helper.tick(true);

        // speed=1 means no waiting ticks; a Success on every tick.
        assertEquals(3, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void invalidPlaceSpeedsClampWithoutUnderflow() {
        for (int speed : new int[]{Integer.MIN_VALUE, -1, 0}) {
            FakeActions actions = FakeActions.withBlockHit();
            actions.placeSpeed = speed;
            actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
            BlockPlaceHelper helper = new BlockPlaceHelper(actions);

            // With Math.max(1, speed) the cooldown clamps to 0 and the next tick fires immediately.
            helper.tick(true);
            helper.tick(true);

            assertEquals("speed=" + speed, 2, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        }
    }

    @Test
    public void failArmsTheSameCooldownAsSuccess() {
        // Vanilla Minecraft.startUseItem sets rightClickDelay before the interaction call,
        // so even a FAIL from useItemOn still enforces the place-speed window. Mirror that.
        FakeActions actions = FakeActions.withBlockHit();
        actions.placeSpeed = 4;
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.FAIL);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);
        for (int i = 0; i < 3; i++) {
            helper.tick(true);
            assertEquals("tick " + i + " waits during cooldown", 1,
                    actions.useItemOnCount(InteractionHand.MAIN_HAND));
        }
        helper.tick(true);
        assertEquals(2, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void resetDiscardsThePreviousWorldsCooldown() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.placeSpeed = 4;
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);
        helper.reset();
        helper.tick(true);

        assertEquals(2, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void itemUseSuccessConsumesTheActionAndHonorsItsSwingSource() {
        for (InteractionResult.Success result : new InteractionResult.Success[]{
                InteractionResult.SUCCESS.withoutItem(),
                InteractionResult.SUCCESS_SERVER.withoutItem(),
                InteractionResult.CONSUME.withoutItem()}) {
            FakeActions actions = FakeActions.withBlockHit();
            actions.useItemResults.put(InteractionHand.MAIN_HAND, result);
            BlockPlaceHelper helper = new BlockPlaceHelper(actions);

            helper.tick(true);

            assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
            assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
            assertEquals(result.swingSource() == InteractionResult.SwingSource.CLIENT
                    ? List.of(InteractionHand.MAIN_HAND) : List.of(), actions.swings);
        }
    }

    @Test
    public void failedItemUseStillAllowsAnOffhandAttempt() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.FAIL);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertEquals(List.of(InteractionHand.OFF_HAND), actions.swings);
    }

    @Test
    public void handsBusyPreventsAnyInteraction() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.handsBusy = true;
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(0, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertTrue(actions.swings.isEmpty());
    }

    @Test
    public void missHitResultPreventsInteraction() {
        FakeActions actions = new FakeActions();
        actions.hit = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // A miss hit result has type MISS, not BLOCK.
        assertEquals(0, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void nullMouseOverPreventsInteraction() {
        FakeActions actions = new FakeActions();
        actions.hit = null;
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(0, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void unrequestedClickPreventsInteraction() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(false);

        assertEquals(0, actions.useItemOnCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void bothHandsAttemptedInOrderWhenPassing() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.PASS);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, true);
        actions.emptyHand.put(InteractionHand.OFF_HAND, true);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // MAIN_HAND must be attempted before OFF_HAND, matching InteractionHand.values() order.
        assertArrayEquals(
                new InteractionHand[]{InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND},
                actions.useItemOnOrder.toArray(new InteractionHand[0]));
    }

    @Test
    public void traceReceivesEventsPerHand() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.useItemOnResults.put(InteractionHand.MAIN_HAND, InteractionResult.PASS);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.FAIL);
        actions.emptyHand.put(InteractionHand.MAIN_HAND, true);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        // Pass on main (empty hand -> no useItem), Fail on offhand -> stops.
        assertFalse("trace should have captured events", actions.traceEvents.isEmpty());
        assertTrue(
                "expected a pass-trace for MAIN_HAND, got " + actions.traceEvents,
                actions.traceEvents.stream().anyMatch(e -> e.contains("useItemOn:pass:MAIN_HAND")));
        assertTrue(
                "expected a fail-trace for OFF_HAND, got " + actions.traceEvents,
                actions.traceEvents.stream().anyMatch(e -> e.contains("useItemOn:fail:OFF_HAND")));
    }

    @Test
    public void onlyItemUseFallbackWaitsForTheAppliedRotationPhase() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.defer = true;
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);

        assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertTrue(actions.swings.isEmpty());
        actions.pending.run();
        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertEquals(List.of(InteractionHand.MAIN_HAND), actions.swings);
    }

    @Test
    public void aDeferredFailureContinuesTheSameHandLoopWithoutRepeatingTheFirstBlockUse() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.defer = true;
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.FAIL);
        actions.useItemOnResults.put(InteractionHand.OFF_HAND, InteractionResult.PASS);
        actions.useItemResults.put(InteractionHand.OFF_HAND, InteractionResult.SUCCESS);
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);

        helper.tick(true);
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
        actions.pending.run();

        assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.useItemOnCount(InteractionHand.OFF_HAND));
        assertEquals(1, actions.useItemCount(InteractionHand.OFF_HAND));
        assertEquals(List.of(InteractionHand.OFF_HAND), actions.swings);
    }

    @Test
    public void aSuccessfulOrFailedBlockUseDoesNotScheduleItemUse() {
        for (InteractionResult result : new InteractionResult[]{InteractionResult.SUCCESS, InteractionResult.FAIL}) {
            FakeActions actions = FakeActions.withBlockHit();
            actions.defer = true;
            actions.useItemOnResults.put(InteractionHand.MAIN_HAND, result);
            new BlockPlaceHelper(actions).tick(true);
            assertEquals(1, actions.useItemOnCount(InteractionHand.MAIN_HAND));
            assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
            assertEquals(null, actions.pending);
        }
    }

    @Test
    public void resetInvalidatesAnAlreadyScheduledFallback() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.defer = true;
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);
        helper.tick(true);
        helper.reset();
        actions.pending.run();
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(0, actions.useItemOnCount(InteractionHand.OFF_HAND));
    }

    @Test
    public void aMissedExecutionPhaseCannotRunAfterTheNextTick() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.defer = true;
        BlockPlaceHelper helper = new BlockPlaceHelper(actions);
        helper.tick(true);
        helper.tick(false);
        actions.pending.run();
        assertEquals(0, actions.useItemCount(InteractionHand.MAIN_HAND));
    }

    @Test
    public void aScheduledFallbackCannotRunTwice() {
        FakeActions actions = FakeActions.withBlockHit();
        actions.defer = true;
        actions.useItemResults.put(InteractionHand.MAIN_HAND, InteractionResult.SUCCESS);
        new BlockPlaceHelper(actions).tick(true);
        actions.pending.run();
        actions.pending.run();
        assertEquals(1, actions.useItemCount(InteractionHand.MAIN_HAND));
        assertEquals(1, actions.swings.size());
    }

    private static final class FakeActions implements BlockPlaceHelper.Actions {
        private boolean defer;
        private Runnable pending;
        private HitResult hit;
        private boolean handsBusy;
        private int placeSpeed = 1;
        private final Map<InteractionHand, Boolean> emptyHand = new EnumMap<>(InteractionHand.class);
        private final Map<InteractionHand, InteractionResult> useItemOnResults = new EnumMap<>(InteractionHand.class);
        private final Map<InteractionHand, InteractionResult> useItemResults = new EnumMap<>(InteractionHand.class);
        private final Map<InteractionHand, Integer> useItemOnCalls = new EnumMap<>(InteractionHand.class);
        private final Map<InteractionHand, Integer> useItemCalls = new EnumMap<>(InteractionHand.class);
        private final List<InteractionHand> useItemOnOrder = new ArrayList<>();
        private final List<InteractionHand> swings = new ArrayList<>();
        private final List<String> traceEvents = new ArrayList<>();

        static FakeActions withBlockHit() {
            FakeActions a = new FakeActions();
            a.hit = hit();
            return a;
        }

        int useItemOnCount(InteractionHand hand) {
            return useItemOnCalls.getOrDefault(hand, 0);
        }

        int useItemCount(InteractionHand hand) {
            return useItemCalls.getOrDefault(hand, 0);
        }

        @Override
        public HitResult mouseOver() {
            return hit;
        }

        @Override
        public boolean isHandsBusy() {
            return handsBusy;
        }

        @Override
        public boolean isItemInHandEmpty(InteractionHand hand) {
            // Default to non-empty: tests that care about empty behavior set it explicitly.
            return emptyHand.getOrDefault(hand, Boolean.FALSE);
        }

        @Override
        public InteractionResult useItemOn(InteractionHand hand, BlockHitResult h) {
            useItemOnCalls.merge(hand, 1, Integer::sum);
            useItemOnOrder.add(hand);
            // Default to PASS so unconfigured hands fall through without side effects.
            return useItemOnResults.getOrDefault(hand, InteractionResult.PASS);
        }

        @Override
        public InteractionResult useItem(InteractionHand hand) {
            useItemCalls.merge(hand, 1, Integer::sum);
            return useItemResults.getOrDefault(hand, InteractionResult.PASS);
        }

        @Override
        public void deferItemUse(InteractionHand hand, BlockHitResult hit, Runnable action) {
            if (defer) {
                pending = action;
            } else {
                action.run();
            }
        }

        @Override
        public void swing(InteractionHand hand) {
            swings.add(hand);
        }

        @Override
        public int placeSpeed() {
            return placeSpeed;
        }

        @Override
        public void trace(String event, BlockHitResult hit, int cooldown) {
            traceEvents.add(event + "|cooldown=" + cooldown);
        }
    }
}
