/*
 * This source file is part of BetterModel.
 * Copyright (c) 2025 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.api.nms;

import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.platform.PlatformBillboard;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a nametag associated with a model part.
 * <p>
 * Nametags are typically implemented as invisible armor stands or text displays
 * that float above a specific bone.
 * </p>
 *
 * @since 1.15.2
 */
public interface ModelNametag {

    /**
     * Sets whether the nametag should always be visible (even through blocks).
     *
     * @param alwaysVisible true for always visible, false otherwise
     * @since 1.15.2
     */
    void alwaysVisible(boolean alwaysVisible);

    /**
     * Sets the text component of the nametag.
     *
     * @param component the text component, or null to clear
     * @since 1.15.2
     */
    void component(@Nullable Component component);

    /**
     * Sets the maximum width of a text line before it wraps.
     *
     * @param width the width in pixels
     * @since 3.3.0
     */
    default void lineWidth(int width) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets the ARGB background color of this text display.
     *
     * @param color the ARGB color
     * @since 3.3.0
     */
    default void backgroundColor(int color) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets the opacity of the text.
     *
     * @param opacity an opacity from 0 (transparent) through 255 (opaque)
     * @throws IllegalArgumentException if the opacity is outside the supported range
     * @since 3.3.0
     */
    default void textOpacity(int opacity) {
        if (opacity < 0 || opacity > 255) throw new IllegalArgumentException("opacity must be between 0 and 255");
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets whether the text has a shadow.
     *
     * @param shadowed true to enable a shadow
     * @since 3.3.0
     */
    default void shadowed(boolean shadowed) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets whether the text is visible through blocks.
     *
     * @param seeThrough true to render through blocks
     * @since 3.3.0
     */
    default void seeThrough(boolean seeThrough) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets whether the vanilla default text background is used.
     *
     * @param defaultBackground true to use the vanilla background
     * @since 3.3.0
     */
    default void defaultBackground(boolean defaultBackground) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets the text alignment.
     *
     * @param alignment the alignment to apply
     * @since 3.3.0
     */
    default void alignment(@NotNull ModelTextAlignment alignment) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Sets the billboard constraint for this text display.
     *
     * @param billboard the billboard constraint
     * @since 3.3.0
     */
    default void billboard(@NotNull PlatformBillboard billboard) {
        throw new UnsupportedOperationException("Text display options are not supported by this platform version.");
    }

    /**
     * Teleports the nametag to a new location.
     *
     * @param location the target location
     * @since 1.15.2
     */
    void teleport(@NotNull PlatformLocation location);

    /**
     * Sends the nametag packet to a specific player.
     *
     * @param player the target player
     * @since 1.15.2
     */
    void send(@NotNull PlatformPlayer player);

    /**
     * Removes the nametag.
     *
     * @param bundler the packet bundler to use
     * @since 1.15.2
     */
    void remove(@NotNull PacketBundler bundler);
}
