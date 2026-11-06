package com.novaimod.registry;

import com.novaimod.NovaiMod;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.entity.SeatEntity;

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

	public static final EntityType<SeatEntity> SEAT_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "seat"),
			EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC)
					.dimensions(0.05F, 0.05F)
					.maxTrackingRange(6)
					.trackingTickInterval(10)
					.build("seat")
	);

	public static final EntityType<CushionEntity> CUSHION_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "cushion"),
			EntityType.Builder.<CushionEntity>create(CushionEntity::new, SpawnGroup.MISC)
					.dimensions(0.75F, 0.24F)
					.maxTrackingRange(8)
					.trackingTickInterval(10)
					.build("cushion")
	);

	public static final EntityType<PartyDollEntity> PARTY_DOLL_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "party_doll"),
			EntityType.Builder.<PartyDollEntity>create(PartyDollEntity::new, SpawnGroup.MISC)
					.dimensions(0.55F, 1.00F)
					.maxTrackingRange(10)
					.trackingTickInterval(3)
					.build("party_doll")
	);

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MOD_NPC, ModNpcEntity.createNpcAttributes());
	}
}
