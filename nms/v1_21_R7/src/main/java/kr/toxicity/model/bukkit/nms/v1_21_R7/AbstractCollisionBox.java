/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.bukkit.nms.v1_21_R7;

import kr.toxicity.model.api.nms.CollisionBox;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AbstractCollisionBox extends Shulker implements CollisionBox {

    AbstractCollisionBox(@NotNull Level level) {
        super(EntityType.SHULKER, level);
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

    @Override //Only for provide compiler hint for Kotlin jvm
    public final boolean equals(@Nullable Object other) {
        return super.equals(other);
    }

    @Override //Only for provide compiler hint for Kotlin jvm
    public final int hashCode() {
        return super.hashCode();
    }
}
