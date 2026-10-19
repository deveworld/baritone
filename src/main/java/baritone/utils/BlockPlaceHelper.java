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

import baritone.Baritone;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.input.Input;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BlockPlaceHelper {
    // base ticks between places caused by tick logic
    private static final int BASE_PLACE_DELAY = 1;

    private final Actions actions;
    private int rightClickTimer;
    private long generation;

    BlockPlaceHelper(IPlayerContext ctx, ItemUseHelper itemUses) {
        this(new Actions() {
            @Override
            public HitResult mouseOver() {
                return ctx.objectMouseOver();
            }

            @Override
            public boolean isHandsBusy() {
                return ctx.player().isHandsBusy();
            }

            @Override
            public boolean isItemInHandEmpty(InteractionHand hand) {
                return ctx.player().getItemInHand(hand).isEmpty();
            }

            @Override
            public InteractionResult useItemOn(InteractionHand hand, BlockHitResult hit) {
                return ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), hand, hit);
            }

            @Override
            public InteractionResult useItem(InteractionHand hand) {
                return ctx.playerController().processRightClick(ctx.player(), ctx.world(), hand);
            }

            @Override
            public void deferItemUse(InteractionHand hand, BlockHitResult hit, Runnable action) {
                itemUses.request(hand, hit,
                        () -> itemUses.baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_RIGHT)
                                && !itemUses.baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT), action);
            }

            @Override
            public void swing(InteractionHand hand) {
                ctx.player().swing(hand);
            }

            @Override
            public int placeSpeed() {
                return Baritone.settings().rightClickSpeed.value;
            }

            @Override
            public void trace(String event, BlockHitResult hit, int cooldown) {
                InteractionDebug.log(ctx, "place/" + event, hit, "cooldown=" + cooldown);
            }
        });
    }

    BlockPlaceHelper(Actions actions) {
        this.actions = actions;
    }

    /** Discard the cooldown when its player/world is no longer active. */
    void reset() {
        rightClickTimer = 0;
        generation++;
    }

    public void tick(boolean rightClickRequested) {
        generation++;
        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        HitResult mouseOver = actions.mouseOver();
        if (!rightClickRequested || actions.isHandsBusy() || mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockHitResult hit = (BlockHitResult) mouseOver;
        // Clamp cooldown so a 0/negative setting does not underflow the counter.
        rightClickTimer = Math.max(1, actions.placeSpeed()) - BASE_PLACE_DELAY;
        tryHands(hit, 0, true);
    }

    private void tryHands(BlockHitResult hit, int firstHand, boolean deferItemUse) {
        InteractionHand[] hands = InteractionHand.values();
        for (int index = firstHand; index < hands.length; index++) {
            InteractionHand hand = hands[index];
            // Match Minecraft.startUseItem for a block hit: success consumes the
            // action, failure ends the attempt, and other results allow item use.
            InteractionResult blockResult = actions.useItemOn(hand, hit);
            if (blockResult instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                    actions.swing(hand);
                }
                actions.trace("useItemOn:success:" + hand + ":" + success.swingSource(), hit, rightClickTimer);
                return;
            }
            if (blockResult instanceof InteractionResult.Fail) {
                // A failed block interaction ends the attempt for both hands.
                actions.trace("useItemOn:fail:" + hand, hit, rightClickTimer);
                return;
            }
            // PASS or TRY_WITH_EMPTY_HAND -> fall through to useItem for this hand.
            actions.trace("useItemOn:pass:" + hand, hit, rightClickTimer);
            if (actions.isItemInHandEmpty(hand)) {
                continue;
            }
            if (deferItemUse) {
                long requestedGeneration = generation;
                int nextHand = index + 1;
                actions.deferItemUse(hand, hit, () -> {
                    if (generation != requestedGeneration) {
                        return;
                    }
                    generation++;
                    if (!useItem(hand, hit)) {
                        // Continue the same vanilla hand loop in the applied-rotation phase.
                        tryHands(hit, nextHand, false);
                    }
                });
                return;
            }
            if (useItem(hand, hit)) {
                return;
            }
        }
    }

    private boolean useItem(InteractionHand hand, BlockHitResult hit) {
        InteractionResult itemResult = actions.useItem(hand);
        if (itemResult instanceof InteractionResult.Success success) {
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                actions.swing(hand);
            }
            actions.trace("useItem:success:" + hand + ":" + success.swingSource(), hit, rightClickTimer);
            return true;
        }
        actions.trace("useItem:continue:" + hand, hit, rightClickTimer);
        return false;
    }

    // Narrow controller seam so right-click result semantics can be tested without a client.
    interface Actions {
        HitResult mouseOver();
        boolean isHandsBusy();
        boolean isItemInHandEmpty(InteractionHand hand);
        InteractionResult useItemOn(InteractionHand hand, BlockHitResult hit);
        InteractionResult useItem(InteractionHand hand);
        void swing(InteractionHand hand);
        int placeSpeed();

        default void deferItemUse(InteractionHand hand, BlockHitResult hit, Runnable action) {
            action.run();
        }

        default void trace(String event, BlockHitResult hit, int cooldown) {}
    }
}
