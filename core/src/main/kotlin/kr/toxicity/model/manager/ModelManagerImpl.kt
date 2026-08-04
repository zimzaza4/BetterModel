/*
 * This source file is part of BetterModel.
 * Copyright (c) 2024 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.manager

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kr.toxicity.model.api.bone.BoneItemMapper
import kr.toxicity.model.api.bone.BoneTags
import kr.toxicity.model.api.data.ModelAsset
import kr.toxicity.model.api.data.blueprint.BlueprintElement
import kr.toxicity.model.api.data.blueprint.BlueprintJson
import kr.toxicity.model.api.data.blueprint.ModelBlueprint
import kr.toxicity.model.api.data.renderer.ModelRenderer
import kr.toxicity.model.api.data.renderer.RendererGroup
import kr.toxicity.model.api.event.ModelAssetsEvent
import kr.toxicity.model.api.event.ModelImportedEvent
import kr.toxicity.model.api.manager.ModelManager
import kr.toxicity.model.api.pack.PackBuilder
import kr.toxicity.model.api.pack.PackZipper
import kr.toxicity.model.api.platform.PlatformNamespace
import kr.toxicity.model.api.util.MathUtil
import kr.toxicity.model.util.*
import net.kyori.adventure.text.format.NamedTextColor.*
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.extension
import kotlin.math.abs

object ModelManagerImpl : ModelManager, GlobalManager {

    private val generalModelMap = addressingMapOf<String, ModelRenderer>()
    private val generalModelView = generalModelMap.toImmutableView()
    private val playerModelMap = addressingMapOf<String, ModelRenderer>()
    private val playerModelView = playerModelMap.toImmutableView()
    private val modelExtensions = setOf("bbmodel", "ajmodel")

    private fun importModels(
        type: ModelRenderer.Type,
        pipeline: ReloadPipeline,
        dir: File
    ): Sequence<ImportedModel> {
        val targetAssets = ModelAssetsEvent(type, dir.fileTrees().use { stream ->
            stream.filter { it.extension.lowercase() in modelExtensions }
                .map(ModelAsset::of)
                .toMutableSet()
        }).apply { call() }
            .assets
            .ifEmpty { return emptySequence() }
            .toList()
        val modelFileMap = ConcurrentHashMap<String, Pair<ModelAsset, ModelBlueprint>>(targetAssets.size)
        val typeName = type.name.lowercase()
        pipeline.apply {
            status = "Importing $typeName models..."
            goal = targetAssets.size
        }.forEachParallel(targetAssets, ModelAsset::sizeAssume) {
            val index = pipeline.progress(it.name)
            val load = it.toTexturedModel() ?: return@forEachParallel
            modelFileMap.compute(load.name) { _, v ->
                if (v != null) {
                    // A model with the same name already exists from a different file
                    warn(
                        "Duplicate $typeName model name '${load.name}'.".toComponent(),
                        "Duplicated file: $it".toComponent(RED),
                        "And: ${v.first}".toComponent(RED)
                    )
                    if (v.first < it) return@compute v
                }
                debugPack {
                    componentOf(
                        "$typeName model file successfully loaded: ".toComponent(),
                        it.toString().toComponent(GREEN),
                        " ($index/${pipeline.goal})".toComponent(DARK_GRAY)
                    )
                }
                it to load
            }
        }
        return modelFileMap.values
            .asSequence()
            .sortedBy { it.first }
            .map {
                ImportedModel(
                    it.first.sizeAssume - it.second.textures.sumOf { tex -> tex.image.size },
                    type,
                    it.second
                )
            }
    }

    private fun loadModels(pipeline: ReloadPipeline, zipper: PackZipper) {
        ModelPipeline(zipper).use {
            if (CONFIG.module().model) it.addModelTo(
                generalModelMap,
                importModels(ModelRenderer.Type.GENERAL, pipeline, DATA_FOLDER.getOrCreateDirectory("models") { folder ->
                    File(DATA_FOLDER.parent, "ModelEngine/blueprints")
                        .takeIf(File::isDirectory)
                        ?.run {
                            copyRecursively(folder, overwrite = true)
                            info("ModelEngine's models are successfully migrated.".toComponent(GREEN))
                        } ?: run {
                        folder.addResource("demon_knight.bbmodel")
                        folder.addResource("blue_wizard.bbmodel")
                    }
                })
            )
            if (CONFIG.module().playerAnimation) it.addModelTo(
                playerModelMap,
                importModels(ModelRenderer.Type.PLAYER, pipeline, DATA_FOLDER.getOrCreateDirectory("players") { folder ->
                    folder.addResource("steve.bbmodel")
                })
            )
        }
    }

    private data class ImportedModel(
        val jsonSize: Long,
        val type: ModelRenderer.Type,
        val blueprint: ModelBlueprint
    )

    private class ModelPipeline(
        private val zipper: PackZipper
    ) : AutoCloseable {

        private data class GeneratedGroup(val itemNamespace: String, val scale: Float)
        private val textures = zipper.assets().bettermodel().textures()

        private val modernModel = ModelBuilder(
            namespace = zipper.assets().obfuscate("model"),
            builder = { zipper.assets().bettermodel().models().resolve(namespace) },
            available = true,
            onBuild = { name, blueprints, json, size ->
                items.add(name, size) {
                    jsonObjectOf("model" to blueprints.toModernJson(namespace, json)).toByteArray()
                }
                blueprints.forEach { json ->
                    models.add(json.jsonName(), size / blueprints.size) {
                        json.buildJson().toByteArray()
                    }
                }
            }
        )

        override fun close() {
        }

        fun addModelTo(
            targetMap: MutableMap<String, ModelRenderer>,
            model: Sequence<ImportedModel>
        ) {
            model.forEach { addModelTo(targetMap, it) }
        }

        private fun addModelTo(
            targetMap: MutableMap<String, ModelRenderer>,
            importedModel: ImportedModel
        ) {
            val (size, type, blueprint) = importedModel
            val context = blueprint.context()
            data class MergedStatic(
                val group: BlueprintElement.Group,
                val position: Vector3f,
                val rotation: Quaternionf
            )

            val generatedGroup = hashMapOf<UUID, GeneratedGroup?>()
            fun isExplicitlyStatic(group: BlueprintElement.Group) = group.name().tagged(
                BoneTags.STATIC,
                BoneTags.STATIC_WITH_CHILDREN
            )
            fun isStaticAnchor(group: BlueprintElement.Group) = group.name().tagged(BoneTags.STATIC_CHILDREN)
            fun isRecursivelyStatic(group: BlueprintElement.Group) = group.name().tagged(
                BoneTags.STATIC_WITH_CHILDREN,
                BoneTags.STATIC_CHILDREN
            )
            val mergedStaticGroups = hashSetOf<UUID>()
            fun collectMergedStatic(group: BlueprintElement.Group, root: Boolean, inherited: Boolean) {
                val cubeOnly = group.isCubeOnly()
                if (!root && cubeOnly && !isStaticAnchor(group) && (inherited || isExplicitlyStatic(group))) {
                    mergedStaticGroups += group.uuid()
                }
                val recursive = cubeOnly && (inherited || isRecursivelyStatic(group))
                group.children().filterIsInstance<BlueprintElement.Group>()
                    .forEach { child -> collectMergedStatic(child, root = false, inherited = recursive) }
            }
            blueprint.elements.filterIsInstance<BlueprintElement.Group>()
                .forEach { group -> collectMergedStatic(group, root = true, inherited = false) }
            fun mergedStatic(group: BlueprintElement.Group) = buildList {
                fun visit(parent: BlueprintElement.Group, position: Vector3f, rotation: Quaternionf) {
                    parent.children().filterIsInstance<BlueprintElement.Group>()
                        .filter { it.uuid() in mergedStaticGroups }
                        .forEach { child ->
                            val localPosition = child.origin().invertXZ()
                                .minus(parent.origin().invertXZ())
                                .toBlockScale()
                                .toVector()
                            val childRotation = rotation.mul(
                                MathUtil.toQuaternion(child.rotation().invertXZ().toVector()),
                                Quaternionf()
                            )
                            val childPosition = localPosition.rotate(rotation).add(position)
                            add(MergedStatic(child, childPosition, childRotation))
                            visit(child, childPosition, childRotation)
                        }
                }
                visit(group, Vector3f(), Quaternionf())
            }
            fun buildGroup(group: BlueprintElement.Group): GeneratedGroup? = generatedGroup.getOrPut(group.uuid()) {
                if (!context.canBeRendered()) return@getOrPut null
                val mergedStatic = mergedStatic(group)
                val renderScale = mergedStatic.fold(group.scale()) { scale, child ->
                    maxOf(
                        scale,
                        child.group.scale(),
                        abs(child.position.x) / 5F,
                        abs(child.position.y) / 5F,
                        abs(child.position.z) / 5F
                    )
                }
                modernModel.ifAvailable {
                    val json = buildList {
                        group.buildModernJson(obfuscator, context, null, renderScale)?.let(::addAll)
                        mergedStatic.forEach { child ->
                            val childTransform = BlueprintElement.Group.FixedTransform(
                                kr.toxicity.model.api.data.Float3(
                                    child.position.x * MathUtil.MODEL_TO_BLOCK_MULTIPLIER / renderScale,
                                    child.position.y * MathUtil.MODEL_TO_BLOCK_MULTIPLIER / renderScale,
                                    child.position.z * MathUtil.MODEL_TO_BLOCK_MULTIPLIER / renderScale
                                ),
                                child.rotation,
                                1F
                            )
                            child.group.buildModernJson(obfuscator, context, childTransform, renderScale)?.let(::addAll)
                        }
                    }
                    val itemModel = group.buildMeshItemModel(context, renderScale)
                    if (json.isNotEmpty() || itemModel != null) {
                        group.jsonName(context)
                            .also { name -> build("$name.json", json, itemModel, if (json.isNotEmpty()) size / json.size else 0) }
                            .let { GeneratedGroup("$namespace/$it", renderScale) }
                    } else null
                }
            }
            targetMap[blueprint.name] = blueprint.toRenderer(type) { group, root ->
                if (!root && group.uuid() in mergedStaticGroups) null else buildGroup(group)
            }.apply {
                debugPack {
                    componentOf(
                        "This model was successfully imported: ".toComponent(),
                        blueprint.name.toComponent(GREEN)
                    )
                }
                callEvent { ModelImportedEvent(blueprint, this) }
            }
            context.buildImage(textures.obfuscator()).forEach { image ->
                textures.add(image.pngName(), image.estimatedSize()) {
                    image.toByteArray()
                }
                image.mcmeta()?.let { meta ->
                    textures.add(image.mcmetaName(), -1) {
                        meta.toByteArray()
                    }
                }
            }
        }

        inner class ModelBuilder(
            val namespace: String,
            val builder: ModelBuilder.() -> PackBuilder,
            private val available: Boolean,
            private val onBuild: ModelBuilder.(String, List<BlueprintJson>, JsonObject?, Long) -> Unit,
        ) {
            val items = zipper.assets().bettermodel().items().resolve(namespace)
            val models = builder()
            val obfuscator = textures.obfuscator().withModels(models.obfuscator())

            inline fun <T> ifAvailable(block: ModelBuilder.() -> T): T? {
                return if (available) block() else null
            }

            fun build(name: String, list: List<BlueprintJson>, json: JsonObject?, size: Long) {
                onBuild(name, list, json, size)
            }
        }

        private fun List<BlueprintJson>.toModernJson(namespace: String, plus: JsonObject?) = if (size == 1) first().toModernJson(namespace) else jsonObjectOf(
            "type" to "composite",
            "models" to fold(JsonArray(size + if (plus != null) 1 else 0).apply {
                plus?.run(::add)
            }) { array, element -> array.apply { add(element.toModernJson(namespace)) } }
        )

        private fun BlueprintJson.toModernJson(namespace: String) = jsonObjectOf(
            "type" to "model",
            "model" to "${CONFIG.namespace()}:$namespace/$name",
            "tints" to jsonArrayOf(
                jsonObjectOf(
                    "type" to "custom_model_data",
                    "default" to 0xFFFFFF
                )
            )
        )

        private fun ModelBlueprint.toRenderer(type: ModelRenderer.Type, builder: (BlueprintElement.Group, Boolean) -> GeneratedGroup?): ModelRenderer {
            fun <T> Collection<BlueprintElement>.toBoneMap(mapper: (BlueprintElement.Bone, Boolean) -> T, root: Boolean) = filterIsInstance<BlueprintElement.Bone>().let { bone ->
                bone.associateTo(sequencedAddressingMapOf(bone.size)) { it.name() to mapper(it, root) }
            }.toImmutableView()
            fun BlueprintElement.Bone.parse(root: Boolean): RendererGroup {
                if (this !is BlueprintElement.Group) return RendererGroup(1.0F, null, this, emptySequencedMap(), null)
                val generated = if (name.toItemMapper() !== BoneItemMapper.EMPTY) null else builder(this, root)
                return RendererGroup(
                    generated?.scale ?: scale(),
                    generated?.let { value ->
                        CONFIG.item().get().namespace(PlatformNamespace(CONFIG.namespace(), value.itemNamespace))

                    },
                    this,
                    children.toBoneMap({ it, _ -> it.parse(root = false) }, root = false),
                    hitBox(),
                )
            }
            return ModelRenderer(
                name,
                type,
                elements.toBoneMap({ it, root -> it.parse(root) }, root = true),
                animations
            )
        }
    }

    override fun start() {
    }

    override fun reload(pipeline: ReloadPipeline, zipper: PackZipper) {
        generalModelMap.clear()
        playerModelMap.clear()
        loadModels(pipeline, zipper)
    }

    override fun model(name: String): ModelRenderer? = generalModelView[name]
    override fun models(): Collection<ModelRenderer> = generalModelView.values
    override fun modelKeys(): Set<String> = generalModelView.keys
    override fun limb(name: String): ModelRenderer? = playerModelView[name]
    override fun limbs(): Collection<ModelRenderer> = playerModelView.values
    override fun limbKeys(): Set<String> = playerModelView.keys
}
