package com.novaimod.registry;

import com.novaimod.NovaiMod;
import com.novaimod.entity.AuroraPhenomenonEntity;
import com.novaimod.entity.BabylonTreasuryEntity;
import com.novaimod.entity.CatgirlEntity;
import com.novaimod.entity.CelestialMeteorEntity;
import com.novaimod.entity.CelestialOrbEntity;
import com.novaimod.entity.ChronoAstrolabeEntity;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.HolyOrderEntity;
import com.novaimod.entity.HolyWaterEntity;
import com.novaimod.entity.InfinityEyeEntity;
import com.novaimod.entity.InfinityPearlEntity;
import com.novaimod.entity.LuceEntity;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.OrbitalLaserEntity;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.entity.PlagueFlaskEntity;
import com.novaimod.entity.PokeballProjectileEntity;
import com.novaimod.entity.PrismCathedralEntity;
import com.novaimod.entity.RealitySlashEntity;
import com.novaimod.entity.SeatEntity;
import com.novaimod.entity.SingularityOrbEntity;
import com.novaimod.entity.SupernovaStarEntity;
import com.novaimod.entity.TerraCataclysmEntity;
import com.novaimod.entity.WhiteCapEntity;

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

	public static final EntityType<InfinityEyeEntity> INFINITY_EYE_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "infinity_eye_entity"),
			EntityType.Builder.<InfinityEyeEntity>create(InfinityEyeEntity::new, SpawnGroup.MISC)
					.dimensions(0.45F, 0.45F)
					.makeFireImmune()
					.maxTrackingRange(10)
					.trackingTickInterval(2)
					.build("infinity_eye_entity")
	);

	public static final EntityType<InfinityPearlEntity> INFINITY_PEARL_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "infinity_pearl_entity"),
			EntityType.Builder.<InfinityPearlEntity>create(InfinityPearlEntity::new, SpawnGroup.MISC)
					.dimensions(0.25F, 0.25F)
					.makeFireImmune()
					.maxTrackingRange(8)
					.trackingTickInterval(3)
					.build("infinity_pearl_entity")
	);

	public static final EntityType<SingularityOrbEntity> SINGULARITY_ORB_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "singularity_orb_entity"),
			EntityType.Builder.<SingularityOrbEntity>create(SingularityOrbEntity::new, SpawnGroup.MISC)
					.dimensions(0.85F, 0.85F)
					.makeFireImmune()
					.maxTrackingRange(16)
					.trackingTickInterval(2)
					.build("singularity_orb_entity")
	);

	public static final EntityType<CelestialMeteorEntity> CELESTIAL_METEOR_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "celestial_meteor_entity"),
			EntityType.Builder.<CelestialMeteorEntity>create(CelestialMeteorEntity::new, SpawnGroup.MISC)
					.dimensions(0.65F, 0.65F)
					.makeFireImmune()
					.maxTrackingRange(10)
					.trackingTickInterval(2)
					.build("celestial_meteor_entity")
	);

	public static final EntityType<CelestialOrbEntity> CELESTIAL_ORB_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "celestial_orb_entity"),
			EntityType.Builder.<CelestialOrbEntity>create(CelestialOrbEntity::new, SpawnGroup.MISC)
					.dimensions(1.2F, 1.2F)
					.makeFireImmune()
					.maxTrackingRange(16)
					.trackingTickInterval(2)
					.build("celestial_orb_entity")
	);

	public static final EntityType<OrbitalLaserEntity> ORBITAL_LASER_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "orbital_laser_entity"),
			EntityType.Builder.<OrbitalLaserEntity>create(OrbitalLaserEntity::new, SpawnGroup.MISC)
					.dimensions(1.5F, 3.0F)
					.makeFireImmune()
					.maxTrackingRange(20)
					.trackingTickInterval(1)
					.build("orbital_laser_entity")
	);

	public static final EntityType<RealitySlashEntity> REALITY_SLASH_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "reality_slash_entity"),
			EntityType.Builder.<RealitySlashEntity>create(RealitySlashEntity::new, SpawnGroup.MISC)
					.dimensions(1.8F, 0.8F)
					.makeFireImmune()
					.maxTrackingRange(18)
					.trackingTickInterval(1)
					.build("reality_slash_entity")
	);

	public static final EntityType<TerraCataclysmEntity> TERRA_CATACLYSM_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "terra_cataclysm_entity"),
			EntityType.Builder.<TerraCataclysmEntity>create(TerraCataclysmEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.5F)
					.makeFireImmune()
					.maxTrackingRange(12)
					.trackingTickInterval(4)
					.build("terra_cataclysm_entity")
	);

	public static final EntityType<WhiteCapEntity> WHITE_CAP_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "white_cap"),
			EntityType.Builder.<WhiteCapEntity>create(WhiteCapEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.6F, 1.95F)
					.makeFireImmune()
					.maxTrackingRange(12)
					.build("white_cap")
	);

	public static final EntityType<PlagueFlaskEntity> PLAGUE_FLASK_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "plague_flask"),
			EntityType.Builder.<PlagueFlaskEntity>create(PlagueFlaskEntity::new, SpawnGroup.MISC)
					.dimensions(0.28F, 0.28F)
					.maxTrackingRange(8)
					.trackingTickInterval(3)
					.build("plague_flask")
	);

	public static final EntityType<CatgirlEntity> CATGIRL_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "catgirl"),
			EntityType.Builder.<CatgirlEntity>create(CatgirlEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.55F, 1.82F)
					.maxTrackingRange(10)
					.build("catgirl")
	);

	public static final EntityType<PokeballProjectileEntity> POKEBALL_PROJECTILE_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "pokeball_entity"),
			EntityType.Builder.<PokeballProjectileEntity>create(PokeballProjectileEntity::new, SpawnGroup.MISC)
					.dimensions(0.28F, 0.28F)
					.maxTrackingRange(8)
					.trackingTickInterval(2)
					.build("pokeball_entity")
	);

	public static final EntityType<LuceEntity> LUCE_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "luce"),
			EntityType.Builder.<LuceEntity>create(LuceEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.58F, 1.85F)
					.makeFireImmune()
					.maxTrackingRange(12)
					.build("luce")
	);

	public static final EntityType<HolyOrderEntity> HOLY_ORDER_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "holy_order"),
			EntityType.Builder.<HolyOrderEntity>create(HolyOrderEntity::new, SpawnGroup.CREATURE)
					.dimensions(0.6F, 1.95F)
					.makeFireImmune()
					.maxTrackingRange(12)
					.build("holy_order")
	);

	public static final EntityType<HolyWaterEntity> HOLY_WATER_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "holy_water_entity"),
			EntityType.Builder.<HolyWaterEntity>create(HolyWaterEntity::new, SpawnGroup.MISC)
					.dimensions(0.28F, 0.28F)
					.maxTrackingRange(8)
					.trackingTickInterval(3)
					.build("holy_water_entity")
	);

	// === v4.3.0: 3D-ФЕНОМЕНЫ НОВЫХ КРЕАТИВНЫХ АРТЕФАКТОВ ===
	public static final EntityType<ChronoAstrolabeEntity> CHRONO_ASTROLABE_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "chrono_astrolabe"),
			EntityType.Builder.<ChronoAstrolabeEntity>create(ChronoAstrolabeEntity::new, SpawnGroup.MISC)
					.dimensions(1.0F, 1.0F)
					.maxTrackingRange(24)
					.trackingTickInterval(2)
					.build("chrono_astrolabe")
	);

	public static final EntityType<SupernovaStarEntity> SUPERNOVA_STAR_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "supernova_star"),
			EntityType.Builder.<SupernovaStarEntity>create(SupernovaStarEntity::new, SpawnGroup.MISC)
					.dimensions(1.0F, 1.0F)
					.maxTrackingRange(24)
					.trackingTickInterval(2)
					.build("supernova_star")
	);

	public static final EntityType<BabylonTreasuryEntity> BABYLON_TREASURY_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "babylon_treasury"),
			EntityType.Builder.<BabylonTreasuryEntity>create(BabylonTreasuryEntity::new, SpawnGroup.MISC)
					.dimensions(1.0F, 1.0F)
					.maxTrackingRange(20)
					.trackingTickInterval(1)
					.build("babylon_treasury")
	);

	public static final EntityType<AuroraPhenomenonEntity> AURORA_PHENOMENON_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "aurora_phenomenon"),
			EntityType.Builder.<AuroraPhenomenonEntity>create(AuroraPhenomenonEntity::new, SpawnGroup.MISC)
					.dimensions(1.0F, 1.0F)
					.maxTrackingRange(24)
					.trackingTickInterval(4)
					.build("aurora_phenomenon")
	);

	public static final EntityType<PrismCathedralEntity> PRISM_CATHEDRAL_ENTITY = Registry.register(
			Registries.ENTITY_TYPE,
			Identifier.of(NovaiMod.MOD_ID, "prism_cathedral"),
			EntityType.Builder.<PrismCathedralEntity>create(PrismCathedralEntity::new, SpawnGroup.MISC)
					.dimensions(1.0F, 1.0F)
					.maxTrackingRange(20)
					.trackingTickInterval(2)
					.build("prism_cathedral")
	);

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(MOD_NPC, ModNpcEntity.createNpcAttributes());
		FabricDefaultAttributeRegistry.register(WHITE_CAP_ENTITY, WhiteCapEntity.createWhiteCapAttributes());
		FabricDefaultAttributeRegistry.register(CATGIRL_ENTITY, CatgirlEntity.createCatgirlAttributes());
		FabricDefaultAttributeRegistry.register(LUCE_ENTITY, LuceEntity.createLuceAttributes());
		FabricDefaultAttributeRegistry.register(HOLY_ORDER_ENTITY, HolyOrderEntity.createHolyOrderAttributes());
	}
}
