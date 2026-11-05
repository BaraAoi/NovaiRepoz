package com.novaimod.registry;

import com.novaimod.NovaiMod;
import com.novaimod.entity.ModNpcEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
	public static final EntityType<ModNpcEntity> MOD_NPC = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "mod_npc"),
			EntityType.Builder.create(ModNpcEntity::new, SpawnGroup.MISC)
					.dimensions(0.6F, 1.95F)
					.makeFireImmune()
					.maxTrackingRange(10)
					.build("mod_npc")
	);

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MOD_NPC, ModNpcEntity.createNpcAttributes());
	}
}
