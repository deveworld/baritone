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

import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.*;

public class PlayerMovementInputTest {
    @Test
    public void diagonalDirectionsHaveTheSameLengthAsStraightMovement() {
        Keys keys = new Keys();
        PlayerMovementInput input = new PlayerMovementInput(keys);
        for (Input longitudinal : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK}) {
            for (Input lateral : new Input[]{Input.MOVE_LEFT, Input.MOVE_RIGHT}) {
                keys.clearAllKeys();
                keys.setInputForceState(longitudinal, true);
                keys.setInputForceState(lateral, true);
                input.tick();
                assertEquals(1, input.getMoveVector().length(), 1E-6);
                assertEquals(longitudinal == Input.MOVE_FORWARD, input.keyPresses.forward());
                assertEquals(lateral == Input.MOVE_LEFT, input.keyPresses.left());
            }
        }
    }

    @Test
    public void oppositeDirectionsCancelWithoutLosingTheActualKeys() {
        Keys keys = new Keys();
        for (Input key : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT}) {
            keys.setInputForceState(key, true);
        }
        PlayerMovementInput input = new PlayerMovementInput(keys);
        input.tick();
        assertEquals(0, input.getMoveVector().lengthSquared(), 0);
        assertTrue(input.keyPresses.forward());
        assertTrue(input.keyPresses.backward());
        assertTrue(input.keyPresses.left());
        assertTrue(input.keyPresses.right());
    }

    @Test
    public void sneakIsAnInputAndDoesNotApplyTheVanillaSlowdownTwice() {
        Keys keys = new Keys();
        for (Input key : new Input[]{Input.MOVE_FORWARD, Input.SNEAK, Input.JUMP, Input.SPRINT}) {
            keys.setInputForceState(key, true);
        }
        PlayerMovementInput input = new PlayerMovementInput(keys);
        input.tick();
        assertEquals(1, input.getMoveVector().y, 0);
        assertTrue(input.keyPresses.shift());
        assertTrue(input.keyPresses.jump());
        assertTrue(input.keyPresses.sprint());
    }

    @Test
    public void releasingControlsClearsTheVectorAndKeyStateOnTheNextTick() {
        Keys keys = new Keys();
        keys.setInputForceState(Input.MOVE_FORWARD, true);
        keys.setInputForceState(Input.JUMP, true);
        PlayerMovementInput input = new PlayerMovementInput(keys);
        input.tick();
        keys.clearAllKeys();
        input.tick();
        assertEquals(0, input.getMoveVector().lengthSquared(), 0);
        assertEquals(net.minecraft.world.entity.player.Input.EMPTY, input.keyPresses);
    }

    private static final class Keys implements IInputOverrideHandler {
        private final EnumSet<Input> held = EnumSet.noneOf(Input.class);

        @Override
        public boolean isInputForcedDown(Input input) {
            return held.contains(input);
        }

        @Override
        public void setInputForceState(Input input, boolean forced) {
            if (forced) held.add(input);
            else held.remove(input);
        }

        @Override
        public void clearAllKeys() {
            held.clear();
        }
    }
}
