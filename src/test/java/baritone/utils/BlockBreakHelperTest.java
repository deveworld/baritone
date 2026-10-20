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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

import static org.junit.Assert.*;

public class BlockBreakHelperTest {
    private static BlockHitResult hit(int x) {
        return new BlockHitResult(new Vec3(x + 0.5, 1, 0.5), Direction.UP, new BlockPos(x, 0, 0), false);
    }

    @Test
    public void explicitStopRestoresTheFlagVanillaNeedsToAbort() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        assertFalse(actions.destroying); // Hidden between ticks, just like the real controller.

        helper.stopBreakingBlock();
        helper.stopBreakingBlock();

        assertEquals(1, actions.aborts);
        assertFalse(actions.destroying);
        helper.tick(true);
        assertEquals(2, actions.starts);
        assertEquals(0, actions.continues);
    }

    @Test
    public void releasingClickStopsThePreviousBreakOnce() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        helper.tick(false);
        helper.tick(false);
        assertEquals(1, actions.aborts);
        assertFalse(actions.destroying);
    }

    @Test
    public void losingTheBlockHitStopsThePreviousBreak() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.hit = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
        helper.tick(true);
        helper.tick(true);
        assertEquals(1, actions.aborts);
        assertEquals(0, actions.continues);
        assertFalse(actions.destroying);
    }

    @Test
    public void continuingRestoresTheControllerStateAndTargetChangesUseItsNormalPath() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.hit = hit(2);
        helper.tick(true);
        assertEquals(1, actions.starts);
        assertEquals(1, actions.continues);
        assertEquals(new BlockPos(2, 0, 0), actions.continuedAt);
        assertFalse(actions.destroying);
    }

    @Test
    public void aRejectedStartDoesNotSwingOrRememberAnActiveBreak() {
        FakeActions actions = new FakeActions();
        actions.acceptStart = false;
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        helper.stopBreakingBlock();
        assertEquals(1, actions.starts);
        assertEquals(1, actions.syncs);
        assertEquals(0, actions.swings);
        assertEquals(0, actions.aborts);
    }

    @Test
    public void defaultCooldownWaitsFiveTicksAndClearsVanillasDuplicateDelayOnce() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.completeOnContinue = true;
        helper.tick(true);
        assertEquals(1, actions.clearedDelays);
        for (int tick = 0; tick < 5; tick++) {
            helper.tick(true);
            assertEquals(1, actions.starts);
            assertEquals(1, actions.continues);
        }
        helper.tick(true);
        assertEquals(2, actions.starts);
        assertEquals(1, actions.clearedDelays);
    }

    @Test
    public void stoppingDoesNotEraseAnAlreadyEarnedCooldown() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.completeOnContinue = true;
        helper.tick(true);
        helper.stopBreakingBlock();
        helper.tick(false);
        for (int tick = 0; tick < 4; tick++) {
            helper.tick(true);
        }
        assertEquals(1, actions.starts);
        helper.tick(true);
        assertEquals(2, actions.starts);
        assertEquals(0, actions.aborts);
    }

    @Test
    public void invalidBreakSpeedsClampWithoutIntegerUnderflow() {
        for (int speed : new int[]{Integer.MIN_VALUE, -1, 0, 1}) {
            FakeActions actions = new FakeActions();
            actions.speed = speed;
            BlockBreakHelper helper = new BlockBreakHelper(actions);
            helper.tick(true);
            actions.completeOnContinue = true;
            helper.tick(true);
            helper.tick(true);
            assertEquals("speed=" + speed, 2, actions.starts);
        }
    }

    @Test
    public void instantBreaksDoNotAcquireTheMultiTickCooldown() {
        FakeActions actions = new FakeActions();
        actions.instant = true;
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        helper.tick(true);
        helper.tick(true);
        assertEquals(3, actions.starts);
        assertEquals(0, actions.continues);
        assertEquals(0, actions.clearedDelays);
    }

    @Test
    public void resetDiscardsCooldownWithoutOperatingOnTheOldController() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.completeOnContinue = true;
        helper.tick(true);
        helper.reset();
        helper.tick(true);
        assertEquals(2, actions.starts);
        assertEquals(0, actions.aborts);
    }

    @Test
    public void missingPlayerDiscardsTheOldBreakWithoutControllerAccess() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.playerPresent = false;
        helper.tick(true);
        helper.stopBreakingBlock();
        assertEquals(0, actions.aborts);
        actions.playerPresent = true;
        helper.tick(true);
        assertEquals(2, actions.starts);
        assertEquals(0, actions.continues);
    }

    @Test
    public void controllerExceptionDoesNotLeaveVanillasFlagSet() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.throwOnContinue = true;
        assertThrows(IllegalStateException.class, () -> helper.tick(true));
        assertFalse(actions.destroying);
        helper.stopBreakingBlock();
        assertEquals(1, actions.aborts);
        assertFalse(actions.destroying);
    }

    @Test
    public void stopExceptionIsNotRetriedAndStillClearsTheFlag() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.throwOnStop = true;
        assertThrows(IllegalStateException.class, helper::stopBreakingBlock);
        assertFalse(actions.destroying);
        helper.stopBreakingBlock();
        assertEquals(1, actions.aborts);
    }

    @Test
    public void failureToClearVanillasDelayDoesNotAlsoArmACustomDelay() {
        FakeActions actions = new FakeActions();
        BlockBreakHelper helper = new BlockBreakHelper(actions);
        helper.tick(true);
        actions.completeOnContinue = true;
        actions.throwOnClearDelay = true;
        assertThrows(IllegalStateException.class, () -> helper.tick(true));
        assertFalse(actions.destroying);
        helper.stopBreakingBlock();
        assertEquals(0, actions.aborts);
        actions.throwOnClearDelay = false;
        helper.tick(true);
        assertEquals(2, actions.starts);
    }

    private static final class FakeActions implements BlockBreakHelper.Actions {
        private boolean playerPresent = true;
        private HitResult hit = hit(0);
        private boolean destroying;
        private boolean acceptStart = true;
        private boolean completeOnContinue;
        private boolean instant;
        private boolean throwOnContinue;
        private boolean throwOnStop;
        private boolean throwOnClearDelay;
        private int speed = 6;
        private int starts;
        private int continues;
        private int aborts;
        private int swings;
        private int syncs;
        private int clearedDelays;
        private BlockPos continuedAt;

        @Override
        public boolean hasPlayer() {
            return playerPresent;
        }

        @Override
        public HitResult mouseOver() {
            return hit;
        }

        @Override
        public void setHittingBlock(boolean hitting) {
            assertTrue(playerPresent);
            destroying = hitting;
        }

        @Override
        public boolean hasBrokenBlock() {
            return !destroying;
        }

        @Override
        public void syncHeldItem() {
            syncs++;
        }

        @Override
        public boolean startBreaking(BlockHitResult hit) {
            starts++;
            destroying = acceptStart && !instant;
            return acceptStart;
        }

        @Override
        public boolean continueBreaking(BlockHitResult hit) {
            assertTrue("continue must restore isDestroying", destroying);
            continues++;
            continuedAt = hit.getBlockPos();
            if (throwOnContinue) {
                throw new IllegalStateException("controller failure");
            }
            destroying = !completeOnContinue;
            return true;
        }

        @Override
        public void stopBreaking() {
            assertTrue("vanilla stop requires isDestroying", destroying);
            aborts++;
            if (throwOnStop) {
                throw new IllegalStateException("stop failure");
            }
            destroying = false;
        }

        @Override
        public void swing() {
            swings++;
        }

        @Override
        public int breakSpeed() {
            return speed;
        }

        @Override
        public void clearDestroyDelay() {
            if (throwOnClearDelay) {
                throw new IllegalStateException("delay failure");
            }
            clearedDelays++;
        }
    }
}
