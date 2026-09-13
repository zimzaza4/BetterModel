/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.bukkit.nms.v1_21_R7

import io.papermc.paper.event.entity.EntityKnockbackEvent
import kr.toxicity.model.api.bone.BoneMovement
import kr.toxicity.model.api.BetterModel
import kr.toxicity.model.api.bone.RenderedBone
import kr.toxicity.model.api.config.DebugConfig
import kr.toxicity.model.api.data.blueprint.ModelBoundingBox
import kr.toxicity.model.api.event.hitbox.*
import kr.toxicity.model.api.mount.MountController
import kr.toxicity.model.api.nms.CollisionBox
import kr.toxicity.model.api.nms.HitBox
import kr.toxicity.model.api.nms.HitBoxListener
import kr.toxicity.model.api.nms.ModelInteractionHand
import kr.toxicity.model.api.platform.PlatformEntity
import kr.toxicity.model.api.platform.PlatformPlayer
import kr.toxicity.model.api.util.MathUtil
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundInteractPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionHand.MAIN_HAND
import net.minecraft.world.InteractionHand.OFF_HAND
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.*
import net.minecraft.world.entity.Display.ItemDisplay
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileDeflection
import net.minecraft.world.entity.monster.Shulker
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.craftbukkit.entity.CraftShulker
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityPotionEffectEvent
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.plugin.Plugin
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal class CollisionBoxImpl(
    private val source: ModelBoundingBox,
    private val bone: RenderedBone,
    private var listener: HitBoxListener,
    private val delegate: Entity,
    private var mountController: MountController
) : AbstractCollisionBox(delegate.level()) {
    private val posCache = BoneMovement()
    private var initialized = false
    private var jumpDelay = 0
    private var mounted = false
    private var collision = ifLivingEntity { collides } == true
    private var noGravity = if (delegate is Mob) delegate.isNoAi else delegate.isNoGravity
    private var forceDismount = false
    private var onFly = false
    private var width = 0F
    private var lastScale = Float.NaN
    private val hiddenPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    private val carrier = ItemDisplay(EntityType.ITEM_DISPLAY, delegate.level()).apply {
        isInvisible = true
        isSilent = true
        isNoGravity = true
        persist = false
        entityData[Display.DATA_POS_ROT_INTERPOLATION_DURATION_ID] = INTERPOLATION_DURATION
    }

    val craftEntity: CollisionBox by lazy {
        object : CraftShulker(Bukkit.getServer() as CraftServer, this), CollisionBox by this {}
    }

    init {
        moveTo(delegate.position())
        isInvisible = true
        isSilent = true
        isNoAi = true
        isInvulnerable = true
        isNoGravity = true
        persist = false
        setPersistenceRequired()
        attachDown()
        initialized = true
        updateGeometry()
        reapplyPosition()
        val target = targetPosition()
        carrier.moveTo(target.x.toDouble(), target.y.toDouble(), target.z.toDouble(), 0F, 0F)
        level().addFreshEntity(carrier, CreatureSpawnEvent.SpawnReason.CUSTOM)
        level().addFreshEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM)
        startRiding(carrier)
        attachDown()
        listener.handle(HitBoxCreateEvent(this))
        (delegate as? ServerPlayer)?.let { owner ->
            hiddenPlayers.add(owner.uuid)
            owner.bukkitEntity.hideEntity(BetterModel.platform() as Plugin, bukkitEntity)
        }
    }

    private fun initialSetup() {
        if (mounted) {
            mounted = false
            if (delegate is Mob) delegate.isNoAi = noGravity
            else delegate.isNoGravity = noGravity
            ifLivingEntity { collides = collision }
        }
    }

    override fun id(): Int = id
    override fun uuid(): UUID = uuid
    override fun source(): PlatformEntity = delegate.bukkitEntity.wrap()
    override fun positionSource(): RenderedBone = bone
    override fun forceDismount(): Boolean = forceDismount
    override fun mountController(): MountController = mountController
    override fun hasMountDriver(): Boolean = controllingPassenger != null
    override fun mountController(controller: MountController) {
        this.mountController = controller
    }
    override fun relativePosition(): Vector3f = delegate.position().run {
        bone.hitBoxPosition(posCache).add(x.toFloat(), y.toFloat(), z.toFloat())
    }
    override fun listener(): HitBoxListener = listener
    override fun listener(listener: HitBoxListener) {
        this.listener = listener
    }
    override fun getItemBySlot(slot: EquipmentSlot): ItemStack = ItemStack.EMPTY
    override fun setItemSlot(slot: EquipmentSlot, stack: ItemStack) {
    }
    override fun getMainArm(): HumanoidArm = HumanoidArm.RIGHT

    override fun mount(entity: PlatformEntity) {
        if (controllingPassenger != null) return
        if (bukkitEntity.addPassenger(entity.unwarp())) {
            if (mountController.canControl()) {
                mounted = true
                noGravity = delegate.isNoGravity
                ifLivingEntity {
                    collision = collides
                    collides = false
                }
            }
            listener.handle(HitBoxMountEvent(this, entity))
        }
    }

    override fun dismount(entity: PlatformEntity) {
        forceDismount = true
        if (bukkitEntity.removePassenger(entity.unwarp())) listener.handle(HitBoxDismountEvent(this, entity))
        forceDismount = false
    }

    override fun dismountAll() {
        forceDismount = true
        passengers.forEach {
            if (it !== carrier) {
                it.stopRiding(true)
                listener.handle(HitBoxDismountEvent(this, it.bukkitEntity.wrap()))
            }
        }
        forceDismount = false
    }

    override fun setRemainingFireTicks(remainingFireTicks: Int) {
        delegate.remainingFireTicks = remainingFireTicks
    }

    override fun getRemainingFireTicks(): Int {
        return delegate.remainingFireTicks
    }

    override fun knockback(
        d0: Double,
        d1: Double,
        d2: Double,
        attacker: Entity?,
        cause: EntityKnockbackEvent.Cause
    ) {
        if (attacker === delegate) return
        ifLivingEntity { knockback(d0, d1, d2, attacker, cause) }
    }

    override fun push(pushingEntity: Entity) {
        if (pushingEntity === delegate) return
        delegate.push(pushingEntity)
    }

    override fun push(x: Double, y: Double, z: Double, pushingEntity: Entity?) {
        if (pushingEntity === delegate) return
        delegate.push(x, y, z, pushingEntity)
    }

    override fun isCollidable(ignoreClimbing: Boolean): Boolean {
        return delegate.isCollidable(ignoreClimbing)
    }

    override fun canCollideWith(entity: Entity): Boolean {
        return checkCollide(entity) && delegate.canCollideWith(entity)
    }

    override fun canCollideWithBukkit(entity: Entity): Boolean {
        return checkCollide(entity) && delegate.canCollideWithBukkit(entity)
    }

    // The argument is the entity being moved. A player is client-authoritative and the client
    // already holds a vanilla shulker that stops them, so claiming a collision here would only
    // make the server reject and rewind their movement. Any other entity is server-authoritative,
    // so it has to be told the truth or it walks straight through the box.
    override fun canBeCollidedWith(entity: Entity?): Boolean {
        if (entity == null) return true
        if (entity is Player) return false
        return checkCollide(entity)
    }

    private fun checkCollide(entity: Entity): Boolean {
        return entity !== delegate
                && entity !== carrier
                && !isHiddenFrom(entity)
                && passengers.none { it === entity }
                && delegate.passengers.none { it === entity }
                && (entity !is CollisionBoxImpl || entity.delegate !== delegate)
    }

    override fun getActiveEffects(): Collection<MobEffectInstance> {
        return ifLivingEntity { getActiveEffects() } ?: emptyList()
    }

    override fun getControllingPassenger(): LivingEntity? {
        return if (mounted) firstPassenger as? LivingEntity ?: super.getControllingPassenger() else null
    }

    override fun onWalk(): Boolean {
        return isWalking()
    }

    private fun mountControl(player: ServerPlayer) {
        if (delegate !is LivingEntity) return
        val travelVector = Vec3(delegate.xxa.toDouble(), delegate.yya.toDouble(), delegate.zza.toDouble())
        if (!mountController.canFly() && delegate.isFallFlying) return

        updateFlyStatus(player)
        val riddenInput = rideInput(player, travelVector)
        if (riddenInput.length() > 0.01) {
            delegate.yRot = player.yRot
            if (onFly) delegate.yHeadRot = player.yRot
            delegate.move(MoverType.SELF, Vec3(riddenInput.x.toDouble(), riddenInput.y.toDouble(), riddenInput.z.toDouble()))
        }
        val dy = delegate.deltaMovement.y + delegate.gravity
        if (!onFly && mountController.canJump() && (delegate.horizontalCollision || player.isJump()) && dy in 0.0..0.01 && jumpDelay == 0) {
            jumpDelay = 10
            delegate.jumpFromGround()
        }
    }

    private fun movementSpeed() = ifLivingEntity {
        getAttribute(Attributes.MOVEMENT_SPEED)?.value?.toFloat()?.let {
            if (!onFly && !shouldDiscardFriction()) level()
                .getBlockState(blockPosBelowThatAffectsMyMovement)
                .block
                .getFriction() * it else it
        } ?: 0.0F
    } ?: 0.0F

    private fun updateFlyStatus(player: ServerPlayer) {
        val fly = (player.isJump() && mountController.canFly()) || noGravity || onFly
        if (delegate is Mob) delegate.isNoAi = fly
        else delegate.isNoGravity = fly
        onFly = fly && !delegate.onGround()
        if (onFly) delegate.resetFallDistance()
    }

    private fun rideInput(player: ServerPlayer, travelVector: Vec3) = mountController.move(
        if (onFly) MountController.MoveType.FLY else MountController.MoveType.DEFAULT,
        player.bukkitEntity.wrap(),
        (delegate.bukkitEntity as org.bukkit.entity.LivingEntity).wrap(),
        Vector3f(
            player.xMovement(),
            player.yMovement(),
            player.zMovement()
        ),
        Vector3f(
            travelVector.x.toFloat(),
            travelVector.y.toFloat(),
            travelVector.z.toFloat()
        )
    ).mul(movementSpeed()).rotateY(-Math.toRadians(player.yRot.toDouble()).toFloat())

    private fun updateGeometry() {
        val scale = bone.hitBoxScale()
        if (!lastScale.isNaN() && abs(scale - lastScale) < MathUtil.FLOAT_COMPARISON_EPSILON) return
        lastScale = scale
        val resolved = CollisionBox.resolve(source, scale) ?: return
        width = resolved.width()
        getAttribute(Attributes.SCALE)?.baseValue = width.toDouble()
        refreshDimensions()
    }

    private fun targetPosition(): Vector3f {
        val pos = relativePosition()
        return pos.set(
            pos.x,
            pos.y - width / 2,
            pos.z
        )
    }

    override fun tick() {
        delegate.removalReason?.let {
            if (!isRemoved) remove(it)
            return
        }
        val controller = controllingPassenger
        if (jumpDelay > 0) jumpDelay--
        if (controller is ServerPlayer && !isDeadOrDying && mountController.canControl()) {
            if (delegate is Mob) delegate.navigation.stop()
            mountControl(controller)
        } else initialSetup()
        updateGeometry()
        yRot = bone.rotation().y
        yHeadRot = yRot
        yBodyRot = yRot
        val target = targetPosition()
        val x = target.x.toDouble()
        val y = target.y.toDouble()
        val z = target.z.toDouble()
        if (isPassenger) {
            if (x != carrier.x || y != carrier.y || z != carrier.z) carrier.setPos(x, y, z)
        } else if (x != this.x || y != this.y || z != this.z) {
            setPos(x, y, z)
        }
        firstTick = false
        listener.sync(craftEntity)
    }

    override fun getDefaultDimensions(pose: Pose): EntityDimensions {
        return if (initialized) EntityDimensions.scalable(1F, 1F) else super.getDefaultDimensions(pose)
    }

    override fun makeBoundingBox(vec3: Vec3): AABB {
        return if (!initialized) {
            super.makeBoundingBox(vec3)
        } else {
            Shulker.getProgressAabb(getScale(), Direction.UP, 0F, vec3).apply {
                if (CONFIG.debug().has(DebugConfig.DebugOption.COLLISION)) {
                    bukkitEntity.world.spawnParticle(Particle.DUST, minX, minY, minZ, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.LIME, 1F))
                    bukkitEntity.world.spawnParticle(Particle.DUST, maxX, maxY, maxZ, 1, 0.0, 0.0, 0.0, 0.0, Particle.DustOptions(Color.LIME, 1F))
                }
            }
        }
    }

    override fun remove(reason: RemovalReason, cause: EntityRemoveEvent.Cause?) {
        initialSetup()
        listener.handle(HitBoxRemoveEvent(craftEntity))
        passengers.forEach { if (it !== carrier) it.stopRiding(true) }
        carrier.remove(reason)
        super.remove(reason, cause)
    }

    override fun removeHitBox() {
        source().task {
            dismountAll()
            remove(ifLivingEntity { removalReason } ?: RemovalReason.KILLED)
        }
    }

    override fun getBukkitEntity(): CraftShulker = craftEntity as CraftShulker
    override fun getBukkitEntityRaw(): CraftShulker = bukkitEntity
    override fun hasExactlyOnePlayerPassenger(): Boolean = false

    override fun isDeadOrDying(): Boolean {
        return ifLivingEntity { isDeadOrDying } == true
    }

    override fun hide(player: PlatformPlayer) {
        if (!hiddenPlayers.add(player.uuid())) return
        val plugin = BetterModel.platform() as Plugin
        player.unwarp().hideEntity(plugin, bukkitEntity)
    }

    override fun show(player: PlatformPlayer) {
        if (!hiddenPlayers.remove(player.uuid())) return
        val plugin = BetterModel.platform() as Plugin
        player.unwarp().showEntity(plugin, bukkitEntity)
    }

    private fun isHiddenFrom(entity: Entity?): Boolean {
        return entity is ServerPlayer && hiddenPlayers.contains(entity.uuid)
    }

    override fun isPickable(): Boolean = false
    override fun isPushable(): Boolean = false
    override fun getPistonPushReaction(): PushReaction = PushReaction.IGNORE
    override fun hurtServer(world: ServerLevel, source: DamageSource, amount: Float): Boolean = false

    override fun skipAttackInteraction(entity: Entity): Boolean {
        if (entity !is Player || entity === delegate) return false
        entity.attack(delegate)
        return true
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult = forwardInteract(player, hand)

    override fun interactAt(player: Player, vec: Vec3, hand: InteractionHand): InteractionResult {
        val interact = HitBoxInteractAtEvent(
            (player.bukkitEntity as org.bukkit.entity.Player).wrap(), craftEntity, when (hand) {
                MAIN_HAND -> ModelInteractionHand.RIGHT
                OFF_HAND -> ModelInteractionHand.LEFT
            }, vec.toBukkit()
        )
        if (!listener.handle(interact)) return InteractionResult.FAIL
        return forwardInteract(player, hand)
    }

    private fun forwardInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (player === delegate) return InteractionResult.FAIL
        (player as ServerPlayer).connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(delegate, player.isShiftKeyDown, hand))
        return InteractionResult.SUCCESS
    }

    override fun addEffect(effectInstance: MobEffectInstance, cause: EntityPotionEffectEvent.Cause): Boolean {
        return ifLivingEntity { addEffect(effectInstance, cause) } == true
    }

    override fun addEffect(effectInstance: MobEffectInstance, entity: Entity?): Boolean {
        if (entity === delegate) return false
        return ifLivingEntity { addEffect(effectInstance, entity) } == true
    }

    override fun addEffect(
        effectInstance: MobEffectInstance,
        entity: Entity?,
        cause: EntityPotionEffectEvent.Cause
    ): Boolean {
        if (entity === delegate) return false
        return ifLivingEntity { addEffect(effectInstance, entity, cause) } == true
    }

    override fun addEffect(
        effectInstance: MobEffectInstance,
        entity: Entity?,
        cause: EntityPotionEffectEvent.Cause,
        fireEvent: Boolean
    ): Boolean {
        if (entity === delegate) return false
        return ifLivingEntity { addEffect(effectInstance, entity, cause, fireEvent) } == true
    }

    override fun deflection(projectile: Projectile): ProjectileDeflection {
        if (projectile.owner?.uuid == delegate.uuid) return ProjectileDeflection.NONE
        return ifLivingEntity { deflection(projectile) } ?: ProjectileDeflection.NONE
    }

    override fun getHealth(): Float {
        return ifLivingEntity { health } ?: super.getHealth()
    }

    private inline fun <T> ifLivingEntity(block: LivingEntity.() -> T): T? {
        return if (delegate.valid) (delegate as? LivingEntity)?.block() else null
    }

    private companion object {
        private const val INTERPOLATION_DURATION = 0
    }
}
