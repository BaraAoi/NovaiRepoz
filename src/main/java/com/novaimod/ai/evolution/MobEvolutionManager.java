package com.novaimod.ai.evolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.novaimod.NovaiMod;
import com.novaimod.ai.evolution.EvolvedMobAccess.MobTrait;
import com.novaimod.ai.evolution.EvolvedMobAccess.RareMobVariant;
import com.novaimod.data.ModPersistentState;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEffects;
import com.novaimod.world.ApocalypseEventManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractPiglinEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Центральная система плавной эволюции поведения, восприятия, среды и адаптации мобов (v2.0.0).
 * Все изменения появляются постепенно с прожитыми днями и развитием снаряжения игроков,
 * с соблюдением баланса: не более 1 индивидуальной черты на моба и снижение натиска после серии смертей.
 */
public final class MobEvolutionManager {
	private static final Identifier MOD_DAY_HP = Identifier.of(NovaiMod.MOD_ID, "evo_day_hp");
	private static final Identifier MOD_DAY_DMG = Identifier.of(NovaiMod.MOD_ID, "evo_day_dmg");
	private static final Identifier MOD_BIOME_SPD = Identifier.of(NovaiMod.MOD_ID, "evo_biome_spd");
	private static final Identifier MOD_BIOME_HP = Identifier.of(NovaiMod.MOD_ID, "evo_biome_hp");
	private static final Identifier MOD_BIOME_ARMOR = Identifier.of(NovaiMod.MOD_ID, "evo_biome_armor");
	private static final Identifier MOD_TRAIT_SPD = Identifier.of(NovaiMod.MOD_ID, "evo_trait_spd");
	private static final Identifier MOD_TRAIT_HP = Identifier.of(NovaiMod.MOD_ID, "evo_trait_hp");
	private static final Identifier MOD_TRAIT_KB = Identifier.of(NovaiMod.MOD_ID, "evo_trait_kb");
	private static final Identifier MOD_LEADER_HP = Identifier.of(NovaiMod.MOD_ID, "evo_leader_hp");
	private static final Identifier MOD_LEADER_DMG = Identifier.of(NovaiMod.MOD_ID, "evo_leader_dmg");
	private static final Identifier MOD_ENV_LIGHT_DMG = Identifier.of(NovaiMod.MOD_ID, "evo_env_light_dmg");
	private static final Identifier MOD_ENV_LIGHT_SPD = Identifier.of(NovaiMod.MOD_ID, "evo_env_light_spd");

	private static final Map<UUID, Long> LAST_APPROACH_SOUND_TICK = new HashMap<>();
	private static final Map<UUID, Long> LAST_SHADOW_CUE_TICK = new HashMap<>();
	private static int globalTickCounter = 0;

	private MobEvolutionManager() {
	}

	public static boolean isEvolvableMob(Entity entity) {
		return entity instanceof ZombieEntity
				|| entity instanceof AbstractSkeletonEntity
				|| entity instanceof SpiderEntity;
	}

	public static String categorizeMob(Entity entity) {
		if (entity instanceof ZombieEntity) {
			return "zombie";
		}
		if (entity instanceof AbstractSkeletonEntity) {
			return "skeleton";
		}
		if (entity instanceof SpiderEntity) {
			return "spider";
		}
		if (entity instanceof CreeperEntity) {
			return "creeper";
		}
		if (entity instanceof PillagerEntity || entity instanceof VindicatorEntity) {
			return "raider";
		}
		return "other";
	}

	/**
	 * Главный серверный тик системы эволюции (вызывается из NovaiMod).
	 */
	public static void tickServer(MinecraftServer server) {
		globalTickCounter++;
		ModPersistentState state = ModPersistentState.get(server);
		long overworldTime = server.getOverworld().getTime();

		// 1. Обновляем прогресс прожитого времени, экипировки и распад штрафа смертей (раз в секунду)
		if (globalTickCounter % 20 == 0) {
			updatePlayersProgression(server, state, overworldTime);
			updatePacksAndEnvironment(server, state);
		}

		// 2. Слух мобов: шаги на бегу и звуки боя (каждые 10 тиков)
		if (globalTickCounter % 10 == 5) {
			processPlayerNoiseAndHearing(server);
		}

		// 3. Звуки приближения вне поля зрения + мелькающие тени в углу экрана (каждые 15 тиков)
		if (globalTickCounter % 15 == 0) {
			processApproachSoundsAndShadows(server, overworldTime);
		}
	}

	private static void updatePlayersProgression(MinecraftServer server, ModPersistentState state, long overworldTime) {
		boolean dirty = false;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
			acc.ticksLivedInWorld += 20L;

			int curGear = evaluatePlayerGearScore(player);
			if (curGear > acc.highestGearScore) {
				acc.highestGearScore = curGear;
			} else if (curGear < acc.highestGearScore && (globalTickCounter % 1200 == 0)) {
				// Если игрок потерял броню (например, сгорело Проклятие утраты при смерти) — пиковый рейтинг плавно снижается к текущему
				acc.highestGearScore = Math.max(curGear, acc.highestGearScore - 3);
			}

			// Раз в игровой день (24000 тиков) без новых смертей постепенно снижаем счётчик смертей (временное послабление)
			if (acc.lastMercyDecayTick == 0L) {
				acc.lastMercyDecayTick = overworldTime;
			} else if (overworldTime - acc.lastMercyDecayTick >= 24000L) {
				acc.lastMercyDecayTick = overworldTime;
				for (String key : new ArrayList<>(acc.recentMobDeaths.keySet())) {
					int v = acc.recentMobDeaths.getOrDefault(key, 0);
					if (v <= 1) {
						acc.recentMobDeaths.remove(key);
					} else {
						acc.recentMobDeaths.put(key, v - 1);
					}
				}
			}
			dirty = true;
		}
		if (dirty) {
			state.markDirty();
		}
	}

	private static int evaluatePlayerGearScore(PlayerEntity player) {
		int score = player.getArmor() * 2; // 0..40
		ItemStack main = player.getMainHandStack();
		String id = Registries.ITEM.getId(main.getItem()).getPath();
		if (id.contains("netherite")) {
			score += 14;
		} else if (id.contains("diamond")) {
			score += 10;
		} else if (id.contains("iron")) {
			score += 5;
		}
		if (main.hasEnchantments()) {
			score += 4;
		}
		return score;
	}

	/**
	 * Растянутый и почти незаметный расчёт уровня развития (0.0 .. 1.0) на основе дней в мире и развития ближайшего игрока,
	 * с учётом серии смертей от данного типа мобов.
	 */
	public static float computeLocalEvolution(ServerWorld world, BlockPos pos, String mobCategory) {
		MinecraftServer server = world.getServer();
		// Во время 10-минутного Проклятия из первого данж-сундука эволюция и усложнение всех мобов максимальные (1.0F)!
		if (ApocalypseEventManager.isActive(server)) {
			return 1.0F;
		}
		ModPersistentState state = ModPersistentState.get(server);

		double worldDays = server.getOverworld().getTime() / 24000.0D;
		// Медленная растянутая кривая по прожитым дням: день 0 -> 0.0, день 10 -> ~0.24, день 30 -> ~0.57, день 60 -> ~0.82
		float dayFactor = (float) (1.0D - Math.exp(-worldDays / 36.0D));

		PlayerEntity nearest = world.getClosestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 96.0D, false);
		float playerGearFactor = 0.0F;
		float mercy = 1.0F;

		if (nearest != null) {
			ModPersistentState.PlayerFinanceData acc = state.getAccount(nearest.getUuid());
			double playerDays = acc.ticksLivedInWorld / 24000.0D;
			dayFactor = Math.max(dayFactor * 0.65F, (float) (1.0D - Math.exp(-playerDays / 30.0D)));
			int liveGear = evaluatePlayerGearScore(nearest);
			float effectiveGear = liveGear * 0.70F + acc.highestGearScore * 0.30F;
			playerGearFactor = MathHelper.clamp(effectiveGear / 54.0F, 0.0F, 1.0F);
			mercy = getMercyMultiplier(acc, mobCategory);
		}

		// На ранних днях мира (например, сразу после завершения 5-минутного испытания) одна только награда/броня
		// не задирает уровень развития обычных мобов до эндгейма — вес снаряжения раскрывается плавно с прожитыми днями!
		float gearWeight = 0.20F + 0.28F * dayFactor;
		float raw = MathHelper.clamp(dayFactor * 0.62F + playerGearFactor * gearWeight, 0.0F, 1.0F);
		return raw * mercy;
	}

	/**
	 * Учёт смертей: если игрок несколько раз подряд погиб от одного типа мобов,
	 * их усиление временно снижается, чтобы игра оставалась честной!
	 */
	public static float getMercyMultiplier(ModPersistentState.PlayerFinanceData acc, String mobCategory) {
		int deaths = acc.recentMobDeaths.getOrDefault(mobCategory, 0);
		if (deaths <= 1) {
			return 1.0F;
		}
		// 2 смерти -> 0.69x, 3 смерти -> 0.52x, 4+ смертей -> до 0.35x
		return Math.max(0.35F, 1.0F / (1.0F + 0.45F * (deaths - 1)));
	}

	public static void recordPlayerDeath(ServerPlayerEntity victim, DamageSource source) {
		Entity attacker = source.getAttacker();
		if (attacker == null) {
			return;
		}
		String cat = categorizeMob(attacker);
		if ("other".equals(cat)) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(victim.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(victim.getUuid());
		int cur = acc.recentMobDeaths.getOrDefault(cat, 0);
		acc.recentMobDeaths.put(cat, Math.min(8, cur + 1));
		acc.lastMercyDecayTick = victim.getServer().getOverworld().getTime();
		state.markDirty();
	}

	public static void onMobDeath(LivingEntity dead) {
		if (!(dead.getWorld() instanceof ServerWorld sw) || !(dead instanceof EvolvedMobAccess access)) {
			return;
		}
		// Если погиб Лидер группы — остальные члены стаи поблизости временно теряют координацию!
		if (access.novai$isPackLeader()) {
			Box box = dead.getBoundingBox().expand(16.0D);
			for (MobEntity ally : sw.getEntitiesByClass(MobEntity.class, box, MobEvolutionManager::isEvolvableMob)) {
				EvolvedMobAccess allyAcc = (EvolvedMobAccess) ally;
				if (dead.getUuid().equals(allyAcc.novai$getLeaderUuid())) {
					allyAcc.novai$setLeaderUuid(null);
					allyAcc.novai$setDisorganizedTicks(90); // 4.5 секунды замешательства
				}
			}
		}
	}

	/**
	 * Однократная плавная инициализация моба при появлении в мире.
	 * Строго соблюдает правило: НЕ делать всё сразу (максимум 1 индивидуальная черта на моба!).
	 */
	public static void initializeMobIfNeeded(MobEntity mob, ServerWorld world) {
		if (!isEvolvableMob(mob)) {
			return;
		}
		EvolvedMobAccess access = (EvolvedMobAccess) mob;
		if (access.novai$isInitialized()) {
			return;
		}
		access.novai$setInitialized(true);

		String category = categorizeMob(mob);
		float evol = computeLocalEvolution(world, mob.getBlockPos(), category);
		access.novai$setEvolutionLevel(evol);

		Random rng = mob.getRandom();

		// 1. Почти незаметный растянутый рост по прожитым дням мира
		double worldDays = world.getServer().getOverworld().getTime() / 24000.0D;
		double stretchedDay = Math.min(0.16D, (1.0D - Math.exp(-worldDays / 45.0D)) * 0.16D) * evol;
		if (stretchedDay > 0.01D) {
			applyPersistentMod(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_DAY_HP, stretchedDay, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			applyPersistentMod(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_DAY_DMG, stretchedDay * 0.75D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}

		// 2. Адаптация к биомам (при развитии >= 0.18)
		if (evol >= 0.18F) {
			applyBiomeSpecialization(mob, access, world, evol);
		}

		// 3. Индивидуальная черта — НЕ БОЛЕЕ ОДНОЙ на моба, чтобы игрок запоминал их характер, а не видел перекачанных монстров!
		if (evol >= 0.15F && rng.nextFloat() < (0.18F + evol * 0.42F)) {
			assignSingleTrait(mob, access, rng, evol);
		}

		// 4. Редкие вариации (на развитых этапах игры >= 0.48, шанс ~5.5%)
		if (evol >= 0.48F && access.novai$getTrait() == MobTrait.NONE && rng.nextFloat() < 0.055F) {
			assignRareVariant(mob, access, evol);
		}

	// 5. Лидер группы (в стае один моб чуть сильнее и координирует остальных)
		if (evol >= 0.42F && access.novai$getRareVariant() == RareMobVariant.NONE && rng.nextFloat() < 0.12F) {
			tryPromotePackLeader(mob, access, world, evol);
		}

		// 6. В обычной игре — очень редко на высоких уровнях и с шансом ровно 10% на максимальном уровне развития (ТОЛЬКО милишники!):
		// выдаём эффект процентного урона сквозь любую броню (1..4 ХП)
		if (!ApocalypseEventManager.isActive(world.getServer()) && isMeleeHostile(mob)) {
			float pctChance = 0.0F;
			if (evol >= 0.95F) {
				pctChance = 0.10F; // 10% при максимальном уровне в обычной игре!
			} else if (evol >= 0.60F) {
				pctChance = 0.01F + ((evol - 0.60F) / 0.35F) * 0.09F; // Очень редко (1% .. 9.9%) на высоких этапах
			}
			if (pctChance > 0.0F && rng.nextFloat() < pctChance) {
				mob.addCommandTag(TAG_PERCENT_MELEE);
			}
		}

		mob.setHealth(mob.getMaxHealth());
	}

	public static final String TAG_PERCENT_MELEE = "novai_percent_melee";

	/**
	 * Полный сброс усилений моба после окончания 5-минутного Испытания и чистый пересчёт под обычный уровень развития мира.
	 */
	public static void resetAndReinitializeAfterTrial(MobEntity mob, ServerWorld world) {
		if (!isEvolvableMob(mob)) {
			return;
		}
		removeModIfPresent(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_DAY_HP);
		removeModIfPresent(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_DAY_DMG);
		removeModIfPresent(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_BIOME_SPD);
		removeModIfPresent(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_BIOME_HP);
		removeModIfPresent(mob, EntityAttributes.GENERIC_ARMOR, MOD_BIOME_ARMOR);
		removeModIfPresent(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_TRAIT_SPD);
		removeModIfPresent(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_TRAIT_HP);
		removeModIfPresent(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, MOD_TRAIT_KB);
		removeModIfPresent(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_LEADER_HP);
		removeModIfPresent(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_LEADER_DMG);
		removeModIfPresent(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_ENV_LIGHT_DMG);
		removeModIfPresent(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_ENV_LIGHT_SPD);

		EvolvedMobAccess access = (EvolvedMobAccess) mob;
		access.novai$setTrait(MobTrait.NONE);
		access.novai$setRareVariant(RareMobVariant.NONE);
		access.novai$setBiomeTag("");
		access.novai$setPackLeader(false);
		access.novai$setLeaderUuid(null);
		access.novai$setEvolutionLevel(0.0F);
		access.novai$setInitialized(false);

		initializeMobIfNeeded(mob, world);
		if (mob.getHealth() > mob.getMaxHealth()) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	private static void removeModIfPresent(MobEntity mob, RegistryEntry<EntityAttribute> attribute, Identifier id) {
		EntityAttributeInstance inst = mob.getAttributeInstance(attribute);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}

	/**
	 * Проверяет, что враждебный моб является строго бойцом ближнего боя («милишником»):
	 * исключает криперов, разбойников с арбалетами и любых стрелков с луком/арбалетом.
	 */
	public static boolean isMeleeHostile(MobEntity mob) {
		if (mob instanceof CreeperEntity || mob instanceof PillagerEntity) {
			return false;
		}
		if (mob.getMainHandStack().getItem() instanceof RangedWeaponItem
				|| mob.getOffHandStack().getItem() instanceof RangedWeaponItem) {
			return false;
		}
		if (mob instanceof AbstractSkeletonEntity) {
			// Скелет считается милишником только если вооружён не луком (например, скелет-мечник или визер-скелет)
			return !mob.getMainHandStack().isEmpty() && !(mob.getMainHandStack().getItem() instanceof RangedWeaponItem);
		}
		return mob instanceof ZombieEntity
				|| mob instanceof VindicatorEntity
				|| mob instanceof SpiderEntity
				|| mob instanceof AbstractPiglinEntity;
	}

	/**
	 * Применяет эффект «Процентный урон» (от 1 до 4 ХП = 5%..20%, не важно какая броня и зачарования!)
	 * при прямом ударе ближнего боя от моба с тегом TAG_PERCENT_MELEE.
	 */
	public static float applyPercentageMeleeDamageIfApplicable(
			ServerPlayerEntity player,
			DamageSource source,
			float appliedDamage
	) {
		if (!(source.getAttacker() instanceof MobEntity attackerMob)) {
			return appliedDamage;
		}
		// Строго прямой удар в ближнем бою (без стрел, снарядов и взрывов!)
		if (source.getSource() != attackerMob || source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			return appliedDamage;
		}
		if (!attackerMob.getCommandTags().contains(TAG_PERCENT_MELEE) || !isMeleeHostile(attackerMob)) {
			return appliedDamage;
		}

		int step = 1 + player.getRandom().nextInt(4); // 1..4 (соответствует 5%..20% здоровья = 1..4 ХП!)
		float pct = step * 0.05F;
		float bonusPercentHp = MathHelper.clamp((float) Math.round(player.getMaxHealth() * pct), 1.0F, 4.0F);

		player.addStatusEffect(new StatusEffectInstance(ModEffects.PERCENT_EROSION, 50, step - 1, false, true, true), attackerMob);
		player.getServerWorld().spawnParticles(
				ParticleTypes.DAMAGE_INDICATOR,
				player.getX(),
				player.getBodyY(0.6D),
				player.getZ(),
				step + 1,
				0.25D,
				0.25D,
				0.25D,
				0.08D
		);
		return appliedDamage + bonusPercentHp;
	}

	private static void applyBiomeSpecialization(MobEntity mob, EvolvedMobAccess access, ServerWorld world, float evol) {
		RegistryEntry<Biome> biomeEntry = world.getBiome(mob.getBlockPos());
		String biomePath = biomeEntry.getKey().map(k -> k.getValue().getPath()).orElse("");

		if (mob instanceof SpiderEntity && (biomePath.contains("swamp") || biomePath.contains("mangrove"))) {
			// В болотах пауки ловчее
			access.novai$setBiomeTag("swamp_spider");
			applyPersistentMod(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_BIOME_SPD, 0.14D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else if (mob instanceof AbstractSkeletonEntity && (biomePath.contains("desert") || biomePath.contains("badlands"))) {
			// В пустыне скелеты стреляют точнее (учитывается в ProjectileEntityMixin)
			access.novai$setBiomeTag("desert_skeleton");
		} else if (mob instanceof ZombieEntity && (biomePath.contains("forest") || biomePath.contains("taiga") || biomePath.contains("jungle"))) {
			// В лесу зомби медленнее, но заметно живучее
			access.novai$setBiomeTag("forest_zombie");
			applyPersistentMod(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_BIOME_SPD, -0.10D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			applyPersistentMod(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_BIOME_HP, 0.28D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			applyPersistentMod(mob, EntityAttributes.GENERIC_ARMOR, MOD_BIOME_ARMOR, 3.0D * evol, EntityAttributeModifier.Operation.ADD_VALUE);
		}
	}

	private static void assignSingleTrait(MobEntity mob, EvolvedMobAccess access, Random rng, float evol) {
		if (mob instanceof ZombieEntity) {
			int pick = rng.nextInt(3);
			if (pick == 0) {
				access.novai$setTrait(MobTrait.SPRINTER);
				applyPersistentMod(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_TRAIT_SPD, 0.15D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			} else if (pick == 1) {
				access.novai$setTrait(MobTrait.TOUGH);
				applyPersistentMod(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_TRAIT_HP, 0.30D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				applyPersistentMod(mob, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, MOD_TRAIT_KB, 0.30D * evol, EntityAttributeModifier.Operation.ADD_VALUE);
			} else {
				access.novai$setTrait(MobTrait.KEEN_EAR);
			}
		} else if (mob instanceof AbstractSkeletonEntity) {
			int pick = rng.nextInt(3);
			if (pick == 0) {
				access.novai$setTrait(MobTrait.SNIPER);
			} else if (pick == 1) {
				access.novai$setTrait(MobTrait.SKIRMISHER);
				applyPersistentMod(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_TRAIT_SPD, 0.09D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			} else {
				access.novai$setTrait(MobTrait.FLANKER);
			}
		} else if (mob instanceof SpiderEntity) {
			if (rng.nextBoolean()) {
				access.novai$setTrait(MobTrait.STALKER);
			} else {
				access.novai$setTrait(MobTrait.SKITTERER);
				applyPersistentMod(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_TRAIT_SPD, 0.10D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
		}
	}

	private static void assignRareVariant(MobEntity mob, EvolvedMobAccess access, float evol) {
		if (mob instanceof AbstractSkeletonEntity skeleton) {
			// Редкая вариация: скелет, который идёт в ближний бой с мечом!
			access.novai$setRareVariant(RareMobVariant.MELEE_SKELETON);
			ItemStack sword = new ItemStack(evol >= 0.72F ? Items.IRON_SWORD : Items.STONE_SWORD);
			skeleton.setStackInHand(Hand.MAIN_HAND, sword);
			skeleton.updateAttackType();
			applyPersistentMod(skeleton, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_TRAIT_SPD, 0.14D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else if (mob instanceof SpiderEntity) {
			// Редкая вариация: паук, стремительно лазающий по вертикальным стенам!
			access.novai$setRareVariant(RareMobVariant.FAST_CLIMBER_SPIDER);
		}
	}

	private static void tryPromotePackLeader(MobEntity mob, EvolvedMobAccess access, ServerWorld world, float evol) {
		if (!(mob instanceof ZombieEntity) && !(mob instanceof AbstractSkeletonEntity)) {
			return;
		}
		Box area = mob.getBoundingBox().expand(14.0D);
		List<MobEntity> nearbySame = world.getEntitiesByClass(
				MobEntity.class,
				area,
				other -> other != mob && other.getClass() == mob.getClass() && other.isAlive()
		);
		if (nearbySame.isEmpty()) {
			return;
		}
		for (MobEntity other : nearbySame) {
			if (((EvolvedMobAccess) other).novai$isPackLeader()) {
				return; // В этой стае уже есть лидер
			}
		}

		access.novai$setPackLeader(true);
		applyPersistentMod(mob, EntityAttributes.GENERIC_MAX_HEALTH, MOD_LEADER_HP, 0.22D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		applyPersistentMod(mob, EntityAttributes.GENERIC_ATTACK_DAMAGE, MOD_LEADER_DMG, 0.14D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		for (MobEntity follower : nearbySame) {
			((EvolvedMobAccess) follower).novai$setLeaderUuid(mob.getUuid());
		}
	}

	/**
	 * Обновляет:
	 * - Окружение стаей (слоты обхода вокруг игрока) и координацию от Лидера группы.
	 * - Усиление мобов в темноте/пещерах и ослабление рядом с факелами.
	 */
	private static void updatePacksAndEnvironment(MinecraftServer server, ModPersistentState state) {
		for (ServerWorld world : server.getWorlds()) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isAlive() || player.isSpectator()) {
					continue;
				}
				Box scanBox = player.getBoundingBox().expand(28.0D, 16.0D, 28.0D);
				List<MobEntity> mobs = world.getEntitiesByClass(MobEntity.class, scanBox, MobEvolutionManager::isEvolvableMob);

				List<MobEntity> huntingThisPlayer = new ArrayList<>();
				MobEntity activeLeader = null;

				for (MobEntity mob : mobs) {
					EvolvedMobAccess access = (EvolvedMobAccess) mob;
					updateEnvironmentalLightModifiers(world, mob, access);

					if (access.novai$isPackLeader() && mob.isAlive()) {
						activeLeader = mob;
						// Ненавязчивая визуальная подсказка раз в 2 сек, чтобы игрок мог заметить лидера группы
						if (mob.getTarget() != null && world.getRandom().nextBoolean()) {
							world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getBodyY(0.85D), mob.getZ(), 2, 0.18D, 0.15D, 0.18D, 0.005D);
						}
					}

					if (mob.getTarget() == player) {
						huntingThisPlayer.add(mob);
					} else {
						access.novai$setPackSlot(0);
						access.novai$setPackSize(1);
					}
				}

				// Лидер координирует стаю: подтягивает ближайших сородичей к общей цели с характерным рыком/кличем
				if (activeLeader != null && activeLeader.getTarget() == player) {
					for (MobEntity ally : mobs) {
						if (ally != activeLeader && ally.getTarget() == null && ally.squaredDistanceTo(activeLeader) <= 13.0D * 13.0D) {
							EvolvedMobAccess allyAcc = (EvolvedMobAccess) ally;
							allyAcc.novai$setLeaderUuid(activeLeader.getUuid());
							allyAcc.novai$setLastKnownTargetPos(player.getBlockPos());
							allyAcc.novai$setTrailTicksLeft(100);
						}
					}
				}

				int packSize = huntingThisPlayer.size();
				for (int i = 0; i < packSize; i++) {
					EvolvedMobAccess acc = (EvolvedMobAccess) huntingThisPlayer.get(i);
					acc.novai$setPackSize(packSize);
					acc.novai$setPackSlot(i);
				}
			}
		}
	}

	private static void updateEnvironmentalLightModifiers(ServerWorld world, MobEntity mob, EvolvedMobAccess access) {
		BlockPos pos = mob.getBlockPos();
		int blockLight = world.getLightLevel(LightType.BLOCK, pos);
		int totalLight = world.getLightLevel(pos);
		float evol = access.novai$getEvolutionLevel();

		EntityAttributeInstance dmgInst = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		EntityAttributeInstance spdInst = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);

		if (blockLight >= 11) {
			// Рядом с факелами/фонарями мобы заметно слабее
			access.novai$setNearTorchLight(true);
			if (dmgInst != null) {
				dmgInst.updateModifier(new EntityAttributeModifier(MOD_ENV_LIGHT_DMG, -0.12D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (spdInst != null) {
				spdInst.updateModifier(new EntityAttributeModifier(MOD_ENV_LIGHT_SPD, -0.06D, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else if (totalLight <= 3 && (!world.isSkyVisible(pos) || world.isNight()) && evol >= 0.15F) {
			// В тёмных пещерах и без освещения мобы сильнее
			access.novai$setNearTorchLight(false);
			if (dmgInst != null) {
				dmgInst.updateModifier(new EntityAttributeModifier(MOD_ENV_LIGHT_DMG, 0.14D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (spdInst != null) {
				spdInst.updateModifier(new EntityAttributeModifier(MOD_ENV_LIGHT_SPD, 0.07D * evol, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			access.novai$setNearTorchLight(false);
			if (dmgInst != null) {
				dmgInst.removeModifier(MOD_ENV_LIGHT_DMG);
			}
			if (spdInst != null) {
				spdInst.removeModifier(MOD_ENV_LIGHT_SPD);
			}
		}
	}

	/**
	 * Слух: мобы реагируют на громкие шаги (бег, прыжки) и звуки боя в радиусе, даже если не видят игрока!
	 */
	private static void processPlayerNoiseAndHearing(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
					continue;
				}

				double noiseRadius = computePlayerNoiseRadius(player, world);
				if (noiseRadius <= 1.0D) {
					continue;
				}

				Box hearBox = player.getBoundingBox().expand(noiseRadius + 6.0D, 10.0D, noiseRadius + 6.0D);
				List<MobEntity> listeners = world.getEntitiesByClass(MobEntity.class, hearBox, MobEvolutionManager::isEvolvableMob);

				for (MobEntity mob : listeners) {
					if (mob.getTarget() != null) {
						continue;
					}
					EvolvedMobAccess acc = (EvolvedMobAccess) mob;
					if (acc.novai$getEvolutionLevel() < 0.08F) {
						continue;
					}

					double effectiveRadius = noiseRadius;
					if (acc.novai$getTrait() == MobTrait.KEEN_EAR) {
						effectiveRadius *= 1.45D; // Чуткие на звук зомби слышат намного дальше!
					}

					double distSq = mob.squaredDistanceTo(player);
					if (distSq <= effectiveRadius * effectiveRadius) {
						// Записываем точку источника звука и отправляем моба проверить шум!
						acc.novai$setLastKnownTargetPos(player.getBlockPos());
						acc.novai$setTrailTicksLeft(90);
						mob.getLookControl().lookAt(player.getX(), player.getEyeY(), player.getZ());

						// Очень близкий звук боя (< 8 блоков) сразу выдаёт позицию игрока
						if (noiseRadius >= 15.0D && distSq <= 8.0D * 8.0D) {
							mob.setTarget(player);
						}
					}
				}
			}
		}
	}

	private static double computePlayerNoiseRadius(ServerPlayerEntity player, ServerWorld world) {
		double radius;
		Vec3d vel = player.getVelocity();
		double horizSpeed = Math.hypot(vel.x, vel.z);

		if (player.handSwinging || (player.isUsingItem() && player.getItemUseTime() > 4) || player.hurtTime > 0) {
			// Звуки боя, стрельбы или ломания блоков
			radius = 17.5D;
		} else if (player.isSprinting() || (!player.isOnGround() && Math.abs(vel.y) > 0.18D)) {
			// Быстрый бег и прыжки сразу выдают игрока!
			radius = 13.0D;
		} else if (player.isSneaking()) {
			// Крадущийся игрок практически бесшумен
			radius = 0.0D;
		} else if (horizSpeed > 0.06D) {
			// Обычный шаг
			radius = 5.5D;
		} else {
			radius = 0.0D;
		}

		// Дождь заглушает шаги
		if (radius > 0.0D && world.hasRain(player.getBlockPos())) {
			radius *= 0.68D;
		}
		return radius;
	}

	/**
	 * Звуки приближения и мелькающие тени в углу зрения:
	 * Создают напряжение и дают внимательному игроку честную звуковую/визуальную подсказку об опасности сзади.
	 */
	private static void processApproachSoundsAndShadows(MinecraftServer server, long overworldTime) {
		for (ServerWorld world : server.getWorlds()) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
					continue;
				}

				Box nearBox = player.getBoundingBox().expand(16.5D, 8.0D, 16.5D);
				List<HostileEntity> hostiles = world.getEntitiesByClass(
						HostileEntity.class,
						nearBox,
						h -> h.isAlive() && h.squaredDistanceTo(player) >= 5.0D * 5.0D
				);
				if (hostiles.isEmpty()) {
					continue;
				}

				Vec3d look = player.getRotationVec(1.0F).multiply(1.0D, 0.0D, 1.0D);
				if (look.lengthSquared() < 0.001D) {
					continue;
				}
				look = look.normalize();

				for (HostileEntity mob : hostiles) {
					boolean stalkingPlayer = (mob.getTarget() == player)
							|| (mob instanceof EvolvedMobAccess ea && ea.novai$getTrailTicksLeft() > 0);
					if (!stalkingPlayer) {
						continue;
					}

					Vec3d toMob = mob.getPos().subtract(player.getPos()).multiply(1.0D, 0.0D, 1.0D);
					if (toMob.lengthSquared() < 1.0D) {
						continue;
					}
					Vec3d dir = toMob.normalize();
					double dot = look.dotProduct(dir); // < 0.15 означает сбоку или за спиной вне прямого взгляда

					// 1. Тихий направленный звук приближения вне поля зрения
					long lastSound = LAST_APPROACH_SOUND_TICK.getOrDefault(player.getUuid(), -1000L);
					if (dot < 0.12D && overworldTime - lastSound >= 150L) {
						LAST_APPROACH_SOUND_TICK.put(player.getUuid(), overworldTime);
						SoundEvent cue = pickSubtleApproachSound(mob);
						player.networkHandler.sendPacket(new PlaySoundS2CPacket(
								Registries.SOUND_EVENT.getEntry(cue),
								SoundCategory.HOSTILE,
								mob.getX(),
								mob.getY(),
								mob.getZ(),
								0.26F,
								0.68F + world.getRandom().nextFloat() * 0.16F,
								world.getRandom().nextLong()
						));
						break;
					}

					// 2. Мелькающий силуэт/тень в углу зрения при подкрадывании в полумраке
					long lastShadow = LAST_SHADOW_CUE_TICK.getOrDefault(player.getUuid(), -1000L);
					int light = world.getLightLevel(player.getBlockPos());
					if (dot >= -0.45D && dot <= 0.38D && light <= 9 && overworldTime - lastShadow >= 380L) {
						if (mob instanceof EvolvedMobAccess ea && ea.novai$getEvolutionLevel() >= 0.28F) {
							LAST_SHADOW_CUE_TICK.put(player.getUuid(), overworldTime);
							// Определяем, слева или справа от взгляда игрока промелькнула тень (2D векторное произведение)
							double crossY = look.x * dir.z - look.z * dir.x;
							boolean leftSide = crossY < 0.0D;
							ServerPlayNetworking.send(player, new ModPayloads.ShadowCuePayload(leftSide, 0.72F));
							world.spawnParticles(ParticleTypes.SMOKE, mob.getX(), mob.getBodyY(0.5D), mob.getZ(), 4, 0.2D, 0.3D, 0.2D, 0.01D);
							break;
						}
					}
				}
			}
		}
	}

	private static SoundEvent pickSubtleApproachSound(HostileEntity mob) {
		if (mob instanceof AbstractSkeletonEntity) {
			return SoundEvents.ENTITY_SKELETON_STEP;
		}
		if (mob instanceof SpiderEntity) {
			return SoundEvents.ENTITY_SPIDER_STEP;
		}
		if (mob instanceof ZombieEntity) {
			return SoundEvents.ENTITY_ZOMBIE_STEP;
		}
		return SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES;
	}

	/**
	 * Восприятие (Скрытность + Чувствительность к свету + Потеря следа):
	 * Вызывается из LivingEntityMixin.getAttackDistanceScalingFactor для расчёта дистанции обнаружения игрока мобом.
	 */
	public static double adjustVisualDetectionFactor(PlayerEntity player, Entity detector, double baseFactor) {
		if (detector == null || !isEvolvableMob(detector)) {
			return baseFactor;
		}
		World world = player.getWorld();
		BlockPos playerPos = player.getBlockPos();
		int totalLight = world.getLightLevel(playerPos);
		int blockLight = world.getLightLevel(LightType.BLOCK, playerPos);

		double factor = baseFactor;
		Vec3d vel = player.getVelocity();
		double horizSpeed = Math.hypot(vel.x, vel.z);

		// 1. Скрытность: крадущийся в темноте игрок долго остаётся незамеченным, но быстрое движение сразу выдаёт!
		if (player.isSprinting() || (!player.isOnGround() && Math.abs(vel.y) > 0.2D) || horizSpeed > 0.17D) {
			factor *= 1.42D;
		} else if (player.isSneaking() && totalLight <= 5) {
			factor *= 0.28D;
		} else if (player.isSneaking()) {
			factor *= 0.62D;
		}

		// 2. Чувствительность к свету: мобы хуже видят в ярко освещённых местах и острее — в темноте,
		// но освещённые базы НЕ становятся полностью безопасными (сохраняется порог видимости!).
		if (blockLight >= 10) {
			factor *= 0.68D;
			// Не даём одному только свету опустить видимость до нуля
			if (!player.isSneaking()) {
				factor = Math.max(factor, baseFactor * 0.52D);
			}
		} else if (totalLight <= 3 && !player.isSneaking()) {
			float evol = (detector instanceof EvolvedMobAccess ea) ? ea.novai$getEvolutionLevel() : 0.25F;
			factor *= (1.0D + 0.24D * evol);
		}

		// 3. Потеря следа: дождь, вода или густые листья мешают мобам замечать игрока
		if (world.hasRain(playerPos)) {
			factor *= 0.74D;
		}
		if (player.isTouchingWater()) {
			factor *= 0.68D;
		}
		if (hasDenseLeavesBetween(world, detector.getEyePos(), player.getEyePos())) {
			factor *= 0.56D;
		}

		return MathHelper.clamp(factor, 0.15D, 1.85D);
	}

	private static boolean hasDenseLeavesBetween(World world, Vec3d from, Vec3d to) {
		Vec3d diff = to.subtract(from);
		double len = diff.length();
		if (len < 2.0D || len > 36.0D) {
			return false;
		}
		int steps = Math.min(16, (int) Math.ceil(len));
		Vec3d step = diff.multiply(1.0D / steps);
		BlockPos.Mutable m = new BlockPos.Mutable();
		int leafBlocks = 0;
		for (int i = 1; i < steps; i++) {
			Vec3d p = from.add(step.multiply(i));
			m.set(BlockPos.ofFloored(p.x, p.y, p.z));
			if (world.getBlockState(m).isIn(BlockTags.LEAVES)) {
				leafBlocks++;
				if (leafBlocks >= 2) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Адаптация к снаряжению игрока:
	 * Если у игрока сильная броня, развитые мобы чуть чаще целятся в незащищённые части
	 * или используют короткие тактические эффекты (со звуковой подсказкой).
	 */
	public static float adaptAttackAgainstArmoredPlayer(
			ServerPlayerEntity player,
			DamageSource source,
			float appliedDamage
	) {
		Entity attacker = source.getAttacker();
		if (!(attacker instanceof MobEntity mob) || !isEvolvableMob(mob)) {
			return appliedDamage;
		}

		EvolvedMobAccess access = (EvolvedMobAccess) mob;
		float evol = access.novai$getEvolutionLevel();
		if (evol < 0.38F || player.getArmor() < 12) {
			return appliedDamage;
		}

		ModPersistentState state = ModPersistentState.get(player.getServer());
		float mercy = getMercyMultiplier(state.getAccount(player.getUuid()), categorizeMob(mob));
		float triggerScale = evol * mercy;
		Random rng = player.getRandom();

		// Проверяем, есть ли у игрока незащищённая часть тела при общей крепкой броне
		int emptySlots = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (player.getEquippedStack(slot).isEmpty()) {
				emptySlots++;
			}
		}

		if (emptySlots > 0 && rng.nextFloat() < (0.28F * triggerScale)) {
			// Попадание в открытое незащищённое место!
			player.getServerWorld().playSound(
					null,
					player.getBlockPos(),
					SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
					SoundCategory.HOSTILE,
					0.65F,
					1.25F
			);
			return appliedDamage + (1.4F * triggerScale);
		}

		// Если вся броня тяжёлая (>= 15 ед.) — мобы иногда применяют ослабляющий тактический эффект
		if (player.getArmor() >= 15 && rng.nextFloat() < (0.22F * triggerScale)) {
			if (mob instanceof SpiderEntity) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), mob);
			} else if (mob instanceof AbstractSkeletonEntity) {
				// Выстрел по ногам тяжёлого латника
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 65, 0), mob);
			} else {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 70, 0), mob);
			}
			player.getServerWorld().playSound(
					null,
					player.getBlockPos(),
					SoundEvents.BLOCK_BREWING_STAND_BREW,
					SoundCategory.HOSTILE,
					0.45F,
					1.35F
			);
		}

		return appliedDamage;
	}

	private static void applyPersistentMod(
			MobEntity mob,
			RegistryEntry<EntityAttribute> attribute,
			Identifier id,
			double value,
			EntityAttributeModifier.Operation op
	) {
		EntityAttributeInstance inst = mob.getAttributeInstance(attribute);
		if (inst != null) {
			inst.overwritePersistentModifier(new EntityAttributeModifier(id, value, op));
		}
	}
}
