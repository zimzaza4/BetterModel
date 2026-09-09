let format, defaultShadeCopy, defaultEmissionCopy

const id = "bettermodel_format"
const name = "BetterModel Format"
const description = "Adds a BetterModel model format based on the generic model, with light emission, shading, and animated texture support for cubes."

Plugin.register(id, {
	title: name,
	author: "zimzaza4",
	icon: "icon-format_free",
	description,
	tags: ["Minecraft: Java Edition", "Format"],
	version: "1.1.0",
	min_version: "4.10.0",
	variant: "both",
	await_loading: true,
	onload() {
		Language.addTranslations("en", {
			"format.bettermodel": "BetterModel"
		})
		Language.addTranslations("zh", {
			"format.bettermodel": "BetterModel"
		})

		format = new ModelFormat("bettermodel", {
			icon: "icon-format_free",
			category: "other",
			target: ["BetterModel"],
			meshes: true,
			billboards: true,
			armature_rig: true,
			splines: true,
			rotate_cubes: true,
			bone_rig: true,
			centered_grid: true,
			per_texture_uv_size: true,
			per_texture_wrap_mode: true,
			uv_rotation: true,
			animation_mode: true,
			per_animator_rotation_interpolation: true,
			animated_textures: true,
			texture_mcmeta: true,
			render_sides: "front",
			locators: true,
			pbr: true,
			java_cube_shading_properties: true,
		})

		// Only save non-default values into the .bbmodel file.
		let shadeProperty = Cube.properties.shade
		if (shadeProperty) {
			defaultShadeCopy = shadeProperty.copy
			shadeProperty.copy = function(instance, target) {
				if (instance.shade === false) target.shade = false
			}
		}
		let emissionProperty = Cube.properties.light_emission
		if (emissionProperty) {
			defaultEmissionCopy = emissionProperty.copy
			emissionProperty.copy = function(instance, target) {
				if (instance.light_emission) target.light_emission = instance.light_emission
			}
		}
	},
	onunload() {
		if (format) format.delete()
		if (defaultShadeCopy) Cube.properties.shade.copy = defaultShadeCopy
		if (defaultEmissionCopy) Cube.properties.light_emission.copy = defaultEmissionCopy
	}
})
