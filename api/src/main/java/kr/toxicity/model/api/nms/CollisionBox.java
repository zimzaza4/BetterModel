/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.api.nms;

import kr.toxicity.model.api.data.blueprint.ModelBoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link HitBox} that clients also receive as a solid entity.
 * <p>
 * Unlike a plain hitbox, which is hidden from clients and only forwards interaction, a collision
 * box is sent to clients as a real solid entity. Clients therefore collide with it on their own:
 * players can stand on its top face, and it blocks movement like a block does.
 * </p>
 * <p>
 * Because it is a real entity, every {@link HitBox} capability applies as well. It can hold
 * passengers, be driven by a {@link kr.toxicity.model.api.mount.MountController}, and report the
 * usual hitbox events, so a plugin can treat it exactly like a seat.
 * </p>
 * <p>
 * Its box is always a cube: the footprint is square and the height equals the footprint. A model
 * part that is not a cube cannot be expressed, and {@link #resolve(ModelBoundingBox, float)}
 * reports that so the caller can fall back to a plain hitbox.
 * </p>
 * <p>
 * A collision box is solid for every player who can see the entity, even one who cannot see the
 * model. {@link #hide(PlatformPlayer)} therefore keeps the entity but makes it invisible, rather
 * than removing it from that player, because removing it would drop the collision on their client
 * while the server still collides. To stop colliding, remove the collision box instead.
 * </p>
 *
 * @since 3.4.1
 */
public interface CollisionBox extends HitBox {

    /**
     * The maximum width a collision box can have.
     * <p>
     * Wider parts are clamped, because the underlying entity cannot grow any further.
     * </p>
     * @since 3.4.1
     */
    float MAX_WIDTH = 3.0F;

    /**
     * Resolves the solid geometry of a model part.
     * <p>
     * Only a cube can be expressed, so this returns null when the part is wider than it is tall or
     * taller than it is wide. The caller should then fall back to a plain hitbox.
     * </p>
     *
     * <pre>{@code
     * var resolved = CollisionBox.resolve(box, scale);
     * if (resolved == null) {
     *     // the part is not a cube: fall back to a hitbox
     * }
     * }</pre>
     *
     * @param box the model bounding box, before scaling
     * @param scale the scale of the bone
     * @return the resolved geometry, or null if the part is not a cube
     * @since 3.4.1
     */
    static @Nullable Resolved resolve(@NotNull ModelBoundingBox box, float scale) {
        var width = (float) ((box.x() + box.z()) / 2 * scale);
        if (width <= 0) return null;
        var tolerance = 1.0E-4F;
        if (Math.abs((float) (box.y() * scale) - width) > tolerance) return null;
        return new Resolved(Math.min(width, MAX_WIDTH), width > MAX_WIDTH);
    }

    /**
     * A resolved collision box geometry.
     *
     * @param width the side of the cube, used as both its width and its height
     * @param clamped whether the requested width was clamped to {@link #MAX_WIDTH}
     * @since 3.4.1
     */
    record Resolved(float width, boolean clamped) {
    }
}
