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

import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {
    // base ticks between block breaks caused by tick logic
    private static final int BASE_BREAK_DELAY = 1;

    private final Actions actions;
    private boolean wasHitting;
    private int breakDelayTimer = 0;

    BlockBreakHelper(IPlayerContext ctx) {
        this(new Actions() {
            @Override
            public boolean hasPlayer() {
                return ctx.player() != null;
            }

            @Override
            public HitResult mouseOver() {
                return ctx.objectMouseOver();
            }

            @Override
            public void setHittingBlock(boolean hitting) {
                ctx.playerController().setHittingBlock(hitting);
            }

            @Override
            public boolean hasBrokenBlock() {
                return ctx.playerController().hasBrokenBlock();
            }

            @Override
            public void syncHeldItem() {
                ctx.playerController().syncHeldItem();
            }

            @Override
            public boolean startBreaking(BlockHitResult hit) {
                return ctx.playerController().clickBlock(hit.getBlockPos(), hit.getDirection());
            }

            @Override
            public boolean continueBreaking(BlockHitResult hit) {
                return ctx.playerController().onPlayerDamageBlock(hit.getBlockPos(), hit.getDirection());
            }

            @Override
            public void stopBreaking() {
                ctx.playerController().resetBlockRemoving();
            }

            @Override
            public void swing() {
                ctx.player().swing(InteractionHand.MAIN_HAND);
            }

            @Override
            public int breakSpeed() {
                return BaritoneAPI.getSettings().blockBreakSpeed.value;
            }

            @Override
            public void clearDestroyDelay() {
                ((IPlayerControllerMP) ctx.minecraft().gameMode).setDestroyDelay(0);
            }

            @Override
            public void trace(String event, BlockHitResult hit, int cooldown) {
                InteractionDebug.log(ctx, "break/" + event, hit, "cooldown=" + cooldown);
            }
        });
    }

    BlockBreakHelper(Actions actions) {
        this.actions = actions;
    }

    public void stopBreakingBlock() {
        if (!wasHitting) {
            return;
        }
        wasHitting = false;
        if (actions.hasPlayer()) {
            // Vanilla only sends the abort while its isDestroying flag is set.
            // We temporarily hide that flag between ticks, so restore it first.
            actions.setHittingBlock(true);
            try {
                actions.stopBreaking();
                actions.trace("stop", null, breakDelayTimer);
            } finally {
                actions.setHittingBlock(false);
            }
        }
    }

    /** Discard state belonging to a player/world which is no longer active. */
    void reset() {
        wasHitting = false;
        breakDelayTimer = 0;
    }

    public void tick(boolean isLeftClick) {
        if (!actions.hasPlayer()) {
            reset();
            return;
        }
        HitResult trace = isLeftClick ? actions.mouseOver() : null;
        boolean isBlockTrace = trace != null && trace.getType() == HitResult.Type.BLOCK;
        if (!isLeftClick || !isBlockTrace) {
            stopBreakingBlock();
        }
        if (breakDelayTimer > 0) {
            breakDelayTimer--;
            return;
        }
        if (!isLeftClick || !isBlockTrace) {
            return;
        }
        BlockHitResult hit = (BlockHitResult) trace;
        actions.setHittingBlock(wasHitting);
        try {
            if (actions.hasBrokenBlock()) {
                actions.syncHeldItem();
                boolean started = actions.startBreaking(hit);
                if (started) {
                    actions.swing();
                }
                actions.trace(started ? "start" : "rejected", hit, breakDelayTimer);
            } else {
                if (actions.continueBreaking(hit)) {
                    actions.swing();
                }
                if (actions.hasBrokenBlock()) {
                    // Keep one cooldown owner: vanilla's counter only decreases
                    // when continueDestroyBlock runs, which we skip while waiting.
                    // Leaving both counters set would apply the delay twice.
                    wasHitting = false;
                    int cooldown = Math.max(1, actions.breakSpeed()) - BASE_BREAK_DELAY;
                    actions.clearDestroyDelay();
                    breakDelayTimer = cooldown;
                    actions.trace("complete", hit, breakDelayTimer);
                }
            }
            wasHitting = !actions.hasBrokenBlock();
        } finally {
            // Don't let vanilla's mouse-key handling stop our break between ticks.
            // The saved state is restored before the next controller operation.
            actions.setHittingBlock(false);
        }
    }

    // Narrow controller seam so lifecycle and timing can be tested without a client.
    interface Actions {
        boolean hasPlayer();
        HitResult mouseOver();
        void setHittingBlock(boolean hitting);
        boolean hasBrokenBlock();
        void syncHeldItem();
        boolean startBreaking(BlockHitResult hit);
        boolean continueBreaking(BlockHitResult hit);
        void stopBreaking();
        void swing();
        int breakSpeed();
        void clearDestroyDelay();

        default void trace(String event, BlockHitResult hit, int cooldown) {}
    }
}
