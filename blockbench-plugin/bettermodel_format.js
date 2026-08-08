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

new Property(Cube, 'boolean', 'shade', {
	default: true,
	condition: () => Format.id === 'bettermodel',
	inputs: {
		element_panel: {
			input: { type: 'checkbox', label: 'Shaded' }
		}
	}
})
