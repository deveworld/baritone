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
import baritone.api.event.events.PacketEvent;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.behavior.Behavior;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.BooleanSupplier;

/** Executes item uses after look is applied and before the player advances through physics. */
public final class ItemUseHelper extends Behavior {
    private final PendingUse pending = new PendingUse();
    private int tick;

    public ItemUseHelper(Baritone baritone) {
        super(baritone);
    }

    /** A manual interaction owns the rest of this tick; do not replay an automated use. */
    public void cancelForManualUse() {
        pending.clear();
    }

    /**
     * Reserve this tick's item-use attempt. The action must use the normal player
     * controller so its local prediction and packet rotation see the same player.
     * A block fallback retains the block and face that prompted the attempt.
     */
    public boolean request(InteractionHand hand, BlockHitResult hit, BooleanSupplier stillValid, Runnable action) {
        LocalPlayer player = ctx.player();
        Level world = ctx.world();
        if (player == null || world == null || player.getItemInHand(hand).isEmpty()) {
            return false;
        }
        int slot = player.getInventory().selected;
        ItemStack mainHand = player.getMainHandItem().copy();
        ItemStack offHand = player.getOffhandItem().copy();
        int requestTick = tick;
        boolean accepted = pending.request(() -> {
            if (ctx.player() != player || ctx.world() != world || !player.isAlive()
                    || player.isHandsBusy() || player.isUsingItem()
                    || player.getInventory().selected != slot
                    || !ItemStack.matches(mainHand, player.getMainHandItem())
                    || !ItemStack.matches(offHand, player.getOffhandItem())
                    || !stillValid.getAsBoolean()) {
                return false;
            }
            if (hit != null) {
                // playerRotations() can still describe the previous outgoing
                // packet here. Validate against the rotation just applied by LookBehavior.
                HitResult current = RayTraceUtils.rayTraceTowards(player,
                        new Rotation(player.getYRot(), player.getXRot()), ctx.playerController().getBlockReachDistance());
                if (!(current instanceof BlockHitResult block) || current.getType() != HitResult.Type.BLOCK
                        || !block.getBlockPos().equals(hit.getBlockPos()) || block.getDirection() != hit.getDirection()) {
                    return false;
                }
            }
            return true;
        }, () -> {
            InteractionDebug.log(ctx, "item/use:" + hand, hit, "requestTick=" + requestTick + " phase=player-update-pre");
            action.run();
        });
        if (accepted) {
            InteractionDebug.log(ctx, "item/queued:" + hand, hit, "requestTick=" + requestTick);
        }
        return accepted;
    }

    @Override
    public void onTick(TickEvent event) {
        tick = event.getCount();
        if (event.getType() == TickEvent.Type.IN) {
            pending.beginTick();
        } else {
            pending.clear();
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (event.getState() == EventState.PRE) {
            // LookBehavior has applied rotation; both client and server still
            // predict this interaction at the position preceding this tick's movement.
            pending.flush();
        }
    }

    @Override
    public void onPostTick(TickEvent event) {
        pending.clear();
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        pending.clear();
    }

    @Override
    public void onPlayerDeath() {
        pending.clear();
    }

    @Override
    public void onSendPacket(PacketEvent event) {
        LocalPlayer player = ctx.player();
        if (!pending.performed || event.getState() != EventState.POST || player == null) {
            return;
        }
        if (event.getPacket() instanceof ServerboundUseItemPacket use) {
            InteractionDebug.log(ctx, "item/packet:" + use.getHand(), null, "requestTick=" + tick
                    + " rotation=" + use.getYRot() + "," + use.getXRot() + " sequence=" + use.getSequence());
        } else if (event.getPacket() instanceof ServerboundMovePlayerPacket move) {
            InteractionDebug.log(ctx, "item/move", null, "requestTick=" + tick
                    + " rotation=" + move.getYRot(player.getYRot()) + "," + move.getXRot(player.getXRot())
                    + " packet=" + move.getClass().getSimpleName());
        }
    }

    // Small phase gate, independently testable without a running Minecraft client.
    static final class PendingUse {
        private boolean accepting;
        private boolean performed;
        private BooleanSupplier valid;
        private Runnable action;

        void beginTick() {
            clear();
            accepting = true;
        }

        boolean request(BooleanSupplier valid, Runnable action) {
            if (!accepting || this.action != null) {
                return false;
            }
            this.valid = valid;
            this.action = action;
            return true;
        }

        void flush() {
            if (!accepting) {
                return;
            }
            accepting = false;
            BooleanSupplier valid = this.valid;
            Runnable action = this.action;
            this.valid = null;
            this.action = null;
            if (action != null && valid.getAsBoolean()) {
                performed = true;
                action.run();
            }
        }

        void clear() {
            accepting = false;
            performed = false;
            valid = null;
            action = null;
        }
    }
}
