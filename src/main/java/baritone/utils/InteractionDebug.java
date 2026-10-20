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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional controller-operation diagnostics, written to the game log. */
final class InteractionDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger("baritone.interactions");

    private InteractionDebug() {}

    static void log(IPlayerContext ctx, String action, BlockHitResult hit, String detail) {
        if (!Baritone.settings().debugInteractions.value) {
            return;
        }
        LocalPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        LOGGER.info("tick={} action={} effectiveRotation={} playerRotation={},{} slot={} block={} face={} {}",
                player.tickCount, action, ctx.playerRotations(), player.getYRot(), player.getXRot(),
                player.getInventory().getSelectedSlot(), hit == null ? "-" : hit.getBlockPos(),
                hit == null ? "-" : hit.getDirection(), detail);
    }
}
