Language.addTranslations('en', {
	'format.bettermodel': 'BetterModel'
})
Language.addTranslations('zh', {
	'format.bettermodel': 'BetterModel'
})

new ModelFormat('bettermodel', {
	icon: 'icon-format_free',
	category: 'other',
	target: ['BetterModel'],
	meshes: true,
	billboards: true,
	armature_rig: true,
	splines: true,
	rotate_cubes: true,
	bone_rig: true,
	centered_grid: true,
	optional_box_uv: true,
	per_texture_uv_size: true,
	per_texture_wrap_mode: true,
	uv_rotation: true,
	animation_mode: true,
	per_animator_rotation_interpolation: true,
	animated_textures: true,
	locators: true,
	pbr: true,
	java_cube_shading_properties: true,
})

// Only save non-default values into the .bbmodel file.
var shadeProperty = Cube.properties.shade
if (shadeProperty) shadeProperty.copy = function(instance, target) {
	if (instance.shade === false) target.shade = false
}
var emissionProperty = Cube.properties.light_emission
if (emissionProperty) emissionProperty.copy = function(instance, target) {
	if (instance.light_emission) target.light_emission = instance.light_emission
}
