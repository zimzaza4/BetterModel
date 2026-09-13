/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.bukkit.nms.v26_R2;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.config.DebugConfig;
import kr.toxicity.model.api.nms.CollisionBox;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AbstractCollisionBox extends Shulker implements CollisionBox {

    AbstractCollisionBox(@NotNull Level level) {
        super(EntityTypes.SHULKER, level);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    public void checkDespawn() {
    }

    protected final void attachDown() {
        entityData.set(DATA_ATTACH_FACE_ID, Direction.DOWN);
    }

    // Spectators see through invisibility by design, so an invisible box would still show up to
    // them as a faint ghost. Withholding the entity from the tracker instead means their client
    // never hears about it at all. Spectators do not collide with anything, so nothing is lost.
    // The debug outline is the one case that wants them included, so they keep getting the box.
    @Override
    public boolean broadcastToPlayer(@NotNull ServerPlayer player) {
        if (player.isSpectator() && !BetterModel.config().debug().has(DebugConfig.DebugOption.COLLISION)) return false;
        return super.broadcastToPlayer(player);
    }

    @Override //Only for provide compiler hint for Kotlin jvm
    public final boolean equals(@Nullable Object other) {
        return super.equals(other);
    }

    @Override //Only for provide compiler hint for Kotlin jvm
    public final int hashCode() {
        return super.hashCode();
    }
}
