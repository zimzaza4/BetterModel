/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.bukkit.nms.v26_R1

import com.mojang.math.Transformation
import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.bone.BoneMovement
import kr.toxicity.model.api.bone.BonePosition
import kr.toxicity.model.api.bone.RenderedBone
import kr.toxicity.model.api.bone.BoneTags
import kr.toxicity.model.api.nms.ModelNametag
import kr.toxicity.model.api.nms.ModelTextAlignment
import kr.toxicity.model.api.nms.PacketBundler
import kr.toxicity.model.api.platform.PlatformBillboard
import kr.toxicity.model.api.platform.PlatformLocation
import kr.toxicity.model.api.platform.PlatformPlayer
import kr.toxicity.model.api.util.EntityUtil
import net.kyori.adventure.text.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.joml.Quaternionf
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class ModelNametagImpl(
    private val bone: RenderedBone
) : ModelNametag {
    private companion object {
        private const val FLAG_SHADOW = 1
        private const val FLAG_SEE_THROUGH = 2
        private const val FLAG_DEFAULT_BACKGROUND = 4
        private const val FLAG_ALIGN_LEFT = 8
        private const val FLAG_ALIGN_RIGHT = 16

        private val textDisplayData = Display.TextDisplay::class.java.accessors()
        @Suppress("UNCHECKED_CAST")
        private val lineWidthData = textDisplayData[1] as EntityDataAccessor<Int>
        @Suppress("UNCHECKED_CAST")
        private val backgroundColorData = textDisplayData[2] as EntityDataAccessor<Int>
        @Suppress("UNCHECKED_CAST")
        private val textOpacityData = textDisplayData[3] as EntityDataAccessor<Byte>
        @Suppress("UNCHECKED_CAST")
        private val styleFlagsData = textDisplayData[4] as EntityDataAccessor<Byte>

        private val emptyVector = Vector3f()
        private val emptyTransformation = Transformation(
            Vector3f(-1F / 40F, -0.2F - 1F / 40F, 0F),
            null,
            null,
            null
        )
    }

    private val textDisplay = bone.name().tagged(BoneTags.TEXT_DISPLAY)
    private val viewedPlayer = ConcurrentHashMap.newKeySet<UUID>()
    private val display = Display.TextDisplay(
        EntityType.TEXT_DISPLAY,
        MinecraftServer.getServer().overworld()
    ).apply {
        entityData[Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID] = 3
        setTransformation(emptyTransformation)
        billboardConstraints = if (textDisplay) Display.BillboardConstraints.FIXED else Display.BillboardConstraints.CENTER
    }
    private val posCache = BoneMovement()
    private val transformCache = BoneMovement()
    private var alwaysVisible = false
    private var location = BetterModel.platform().adapter().zero()

    override fun component(component: Component?) {
        display.text = component?.asVanilla() ?: VanillaComponent.empty()
    }

    override fun lineWidth(width: Int) { display.entityData[lineWidthData] = width }
    override fun backgroundColor(color: Int) { display.entityData[backgroundColorData] = color }
    override fun textOpacity(opacity: Int) {
        require(opacity in 0..255) { "opacity must be between 0 and 255" }
        display.entityData[textOpacityData] = opacity.toByte()
    }
    override fun shadowed(shadowed: Boolean) = setFlag(FLAG_SHADOW, shadowed)
    override fun seeThrough(seeThrough: Boolean) = setFlag(FLAG_SEE_THROUGH, seeThrough)
    override fun defaultBackground(defaultBackground: Boolean) = setFlag(FLAG_DEFAULT_BACKGROUND, defaultBackground)
    override fun alignment(alignment: ModelTextAlignment) {
        styleFlags = styleFlags and (FLAG_ALIGN_LEFT or FLAG_ALIGN_RIGHT).inv()
        styleFlags = styleFlags or when (alignment) {
            ModelTextAlignment.LEFT -> FLAG_ALIGN_LEFT
            ModelTextAlignment.CENTER -> 0
            ModelTextAlignment.RIGHT -> FLAG_ALIGN_RIGHT
        }
        display.entityData[styleFlagsData] = styleFlags.toByte()
    }
    override fun billboard(billboard: PlatformBillboard) {
        display.billboardConstraints = Display.BillboardConstraints.BY_ID.apply(billboard.ordinal)
    }

    override fun teleport(location: PlatformLocation) {
        this.location = location
    }

    override fun alwaysVisible(alwaysVisible: Boolean) {
        this.alwaysVisible = alwaysVisible
    }

    private var styleFlags = 0
    private fun setFlag(flag: Int, enabled: Boolean) {
        styleFlags = if (enabled) styleFlags or flag else styleFlags and flag.inv()
        display.entityData[styleFlagsData] = styleFlags.toByte()
    }

    private fun updateTextDisplayTransformation(uuid: UUID) {
        if (!textDisplay) return
        val movement = bone.worldMovement(uuid, transformCache)
        display.xRot = bone.rotation().x
        display.yRot = bone.rotation().y
        display.setTransformation(Transformation(
            Vector3f(-1F / 40F, -0.02F - 1F / 40F, if (textDisplay) -0.02F else 0F),
            Quaternionf(movement.rotation()),
            Vector3f(movement.scale()).mul(bone.hitBoxScale() * bone.textDisplayScale()),
            null
        ))
    }

    override fun send(player: PlatformPlayer) {
        if (display.text == VanillaComponent.empty()) return
        updateTextDisplayTransformation(player.uuid())
        val hb = bone.group.hitBoxPoint
        val pos = bone.worldPosition(BonePosition(emptyVector, hb, player.uuid()), posCache)
        display.moveTo(Vec3(
            location.x() + pos.x,
            location.y() + pos.y,
            location.z() + pos.z
        ))
        val inPoint = alwaysVisible || EntityUtil.isCustomNameVisible(player.location(), location)
        when {
            inPoint && viewedPlayer.add(player.uuid()) -> bundlerOfNotNull(
                addPacket,
                display.entityData.pack()?.let {
                    ClientboundSetEntityDataPacket(display.id, it)
                }
            )
            inPoint -> bundlerOfNotNull(
                ClientboundEntityPositionSyncPacket(display.id, PositionMoveRotation.of(display), false),
                ClientboundMoveEntityPacket.Rot(display.id, bone.rotation().packedY(), bone.rotation().packedX(), false),
                display.entityData.packDirty()?.let {
                    ClientboundSetEntityDataPacket(display.id, it)
                }
            )
            viewedPlayer.remove(player.uuid()) -> bundlerOf(removePacket)
            else -> null
        }?.send(player)
    }

    override fun remove(bundler: PacketBundler) {
        bundler += removePacket
    }

    private val addPacket get() = ClientboundAddEntityPacket(
        display.id,
        display.uuid,
        display.x,
        display.y,
        display.z,
        display.xRot,
        display.yRot,
        display.type,
        0,
        display.deltaMovement,
        display.yHeadRot.toDouble()
    )

    private val removePacket get() = ClientboundRemoveEntitiesPacket(display.id)
}
