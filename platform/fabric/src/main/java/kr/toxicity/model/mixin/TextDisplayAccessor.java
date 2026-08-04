/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private synched entity data keys of {@link Display.TextDisplay}
 * that are no longer reachable through public setters on modern Minecraft
 * versions.
 */
@Mixin(value = Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Accessor("DATA_TEXT_ID")
    static @NotNull EntityDataAccessor<Component> bettermodel$getDataTextId() {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Accessor("DATA_LINE_WIDTH_ID")
    static @NotNull EntityDataAccessor<Integer> bettermodel$getDataLineWidthId() {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Accessor("DATA_BACKGROUND_COLOR_ID")
    static @NotNull EntityDataAccessor<Integer> bettermodel$getDataBackgroundColorId() {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Accessor("DATA_TEXT_OPACITY_ID")
    static @NotNull EntityDataAccessor<Byte> bettermodel$getDataTextOpacityId() {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Accessor("DATA_STYLE_FLAGS_ID")
    static @NotNull EntityDataAccessor<Byte> bettermodel$getDataStyleFlagsId() {
        throw new UnsupportedOperationException("Implemented via mixin");
    }
}
