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

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ItemUseHelperTest {
    @Test
    public void itemUseReadsAppliedRotationAndPrecedesTheNormalMovementPacket() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        float[] rotation = {15, -5};
        List<ServerboundUseItemPacket> packets = new ArrayList<>();
        pending.beginTick();
        assertTrue(pending.request(() -> true, () -> packets.add(
                new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 1, rotation[0], rotation[1]))));
        assertTrue(packets.isEmpty());

        // LookBehavior applies the processed rotation before this listener's PRE.
        rotation[0] = 90.15F;
        rotation[1] = -20.1F;
        pending.flush();
        ServerboundMovePlayerPacket move = new ServerboundMovePlayerPacket.Rot(rotation[0], rotation[1], false, false);

        assertEquals(1, packets.size());
        assertEquals(move.getYRot(0), packets.getFirst().getYRot(), 0);
        assertEquals(move.getXRot(0), packets.getFirst().getXRot(), 0);
    }

    @Test
    public void onlyTheFirstIntentIsAcceptedDuringATick() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        List<String> actions = new ArrayList<>();
        pending.beginTick();
        assertTrue(pending.request(() -> true, () -> actions.add("firework")));
        assertFalse(pending.request(() -> true, () -> actions.add("fallback")));
        pending.flush();
        assertEquals(List.of("firework"), actions);
    }

    @Test
    public void validationRunsAtExecutionTime() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        boolean[] stillValid = {true};
        List<String> actions = new ArrayList<>();
        pending.beginTick();
        pending.request(() -> stillValid[0], () -> actions.add("stale"));
        stillValid[0] = false;
        pending.flush();
        assertTrue(actions.isEmpty());
    }

    @Test
    public void lifecycleResetDropsPendingWorkAndClosesThePhase() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        List<String> actions = new ArrayList<>();
        pending.beginTick();
        pending.request(() -> true, () -> actions.add("old player"));
        pending.clear();
        pending.flush();
        assertFalse(pending.request(() -> true, () -> actions.add("late")));
        assertTrue(actions.isEmpty());
    }

    @Test
    public void aTickWithoutPlayerUpdateDoesNotCarryWorkIntoTheNextTick() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        List<String> actions = new ArrayList<>();
        pending.beginTick();
        pending.request(() -> true, () -> actions.add("missed phase"));
        pending.beginTick();
        pending.flush();
        assertTrue(actions.isEmpty());
    }

    @Test
    public void repeatedPlayerUpdateCannotReplayAnActionOrAcceptALateRequest() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        List<String> actions = new ArrayList<>();
        pending.beginTick();
        pending.request(() -> true, () -> actions.add("once"));
        pending.flush();
        pending.flush();
        assertFalse(pending.request(() -> true, () -> actions.add("late")));
        assertEquals(List.of("once"), actions);
    }

    @Test
    public void anEmptyPlayerUpdateStillClosesTheRequestPhase() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        pending.beginTick();
        pending.flush();
        assertFalse(pending.request(() -> true, () -> fail("late action")));
    }

    @Test
    public void controllerFailureCannotReplayTheUse() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        int[] calls = {0};
        pending.beginTick();
        pending.request(() -> true, () -> {
            calls[0]++;
            throw new IllegalStateException("controller failure");
        });
        assertThrows(IllegalStateException.class, pending::flush);
        pending.flush();
        assertEquals(1, calls[0]);
    }

    @Test
    public void executionCannotScheduleAnUnboundedChainOfUses() {
        ItemUseHelper.PendingUse pending = new ItemUseHelper.PendingUse();
        pending.beginTick();
        pending.request(() -> true, () ->
                assertFalse(pending.request(() -> true, () -> fail("recursive use"))));
        pending.flush();
    }
}
