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

package baritone.behavior;

import baritone.api.utils.Rotation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.junit.Test;

import static org.junit.Assert.*;

public class LookBehaviorTest {
    @Test
    public void manualUseRetainsItsExactDirectionAcrossAutomaticUpdatesAndMovement() {
        LookBehavior.RotationFrame frame = new LookBehavior.RotationFrame();
        Rotation camera = new Rotation(17.125F, -8.625F);
        frame.useManual(camera);
        ServerboundUseItemPacket use = new ServerboundUseItemPacket(
                InteractionHand.MAIN_HAND, 1, camera.getYaw(), camera.getPitch());

        // PRE and physics must not run manual input through noise or mouse rounding.
        Rotation pre = frame.resolve(() -> new Rotation(90, 30));
        Rotation physics = frame.resolve(() -> new Rotation(180, -45));
        ServerboundMovePlayerPacket move = new ServerboundMovePlayerPacket.Rot(
                physics.getYaw(), physics.getPitch(), false, false);

        assertTrue(frame.isManual());
        assertSame(camera, pre);
        assertEquals(use.getYRot(), move.getYRot(0), 0);
        assertEquals(use.getXRot(), move.getXRot(0), 0);
    }

    @Test
    public void automaticLookIsEvaluatedOnceForInteractionPhysicsAndMovement() {
        LookBehavior.RotationFrame frame = new LookBehavior.RotationFrame();
        int[] evaluations = {0};
        Rotation pre = frame.resolve(() -> {
            evaluations[0]++;
            return new Rotation(90.15F, 20.1F);
        });
        Rotation physics = frame.resolve(() -> {
            evaluations[0]++;
            return new Rotation(90.3F, 20.2F);
        });
        assertSame(pre, physics);
        assertEquals(1, evaluations[0]);
        assertFalse(frame.isManual());
    }

    @Test
    public void manualUseCanTakeOwnershipAfterAutomaticLookWasResolved() {
        LookBehavior.RotationFrame frame = new LookBehavior.RotationFrame();
        frame.resolve(() -> new Rotation(90, 0));
        Rotation manual = new Rotation(-20, 35);
        frame.useManual(manual);
        assertSame(manual, frame.resolve(() -> {
            throw new AssertionError("manual direction was replaced");
        }));
    }

    @Test
    public void aNewTickOrLifecycleResetReleasesManualAndAutomaticRotations() {
        LookBehavior.RotationFrame frame = new LookBehavior.RotationFrame();
        frame.useManual(new Rotation(-20, 35));
        frame.clear();
        Rotation next = new Rotation(70, 10);
        assertFalse(frame.isManual());
        assertSame(next, frame.resolve(() -> next));
        frame.clear();
        Rotation following = new Rotation(80, 15);
        assertSame(following, frame.resolve(() -> following));
    }
}
