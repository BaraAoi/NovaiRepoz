package com.novaimod.entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.BoggedEntity;
import net.minecraft.entity.mob.BreezeEntity;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.EndermiteEntity;
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.GuardianEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Боец «Клана Белых Колпаков»:
 * - Обычного человеческого роста (~1.95 бл.), в белой мантии чумного алхимика,
 *   тёмной клювастой маске чумного доктора со светящимися линзами и ОГРОМНОМ белом гномьем колпаке выше его самого!
 * - Фанатично ненавидит иномирную скверну и мутировавшую нежить:
 *   эндерменов, скелетов-иссушителей, эндер-драконов, всполохов, визеров, гастов, шалкеров, фантомов,
 *   зимогоров, болотников, кадавров, утопленников, ведьм, вызывателей, хранителей, зоглинов и др.!
 * - Игроков тоже недолюбливает: рычит предупреждения при приближении без маскировки и атакует всем лагерем,
 *   если игрок подходит вплотную, заходит в Цитадель или держит предметы скверны (если только на игроке не надет Колпак Чумного Доктора!).
 */
public class WhiteCapEntity extends PathAwareEntity {
	public static final int RANK_HUNTER = 0;
	public static final int RANK_ARBALIST = 1;
	public static final int RANK_APOTHECARY = 2;
	public static final int RANK_GRAND_MASTER = 3;

	public static final String TAG_SEALED_ENDER = "novai_sealed_ender";
	public static final String TAG_CITADEL_PRISONER = "novai_citadel_prisoner";

	private static final TrackedData<Integer> RANK = DataTracker.registerData(WhiteCapEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> ACTION_ANIM = DataTracker.registerData(WhiteCapEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final String[] SUSPICIOUS_WARNINGS = new String[]{
			"Держи дистанцию, безмасочный! От тебя несёт пылью Края и адской копотью...",
			"Это лагерь Клана Белых Колпаков! Ещё два шага к нашим котлам — и пойдёшь на опыты вслед за всполохами!",
			"Не путайся под ногами у Чумных Чистильщиков, чужак. Нам и без тебя хватает эндерменов и иссушителей!",
			"Чего уставился на наши колпаки?! Проваливай по-хорошему, пока не спустили егерей!"
	};

	private static final String[] BROTHER_GREETINGS = new String[]{
			"Слава Белым Колпакам, брат! Сегодня мы выжгли целую стаю всполохов и эндер-тварей!",
			"Твой клюв начищен, а колпак высок! Проходи свободно, брат по Ордену.",
			"Видел эндерменов поблизости? Наши серебряные печати не дадут им сбежать телепортом!"
	};

	private static final String[] BATTLE_CRIES = new String[]{
			"Иномирная скверна обнаружена! Очистить алхимическим огнём!!",
			"В карантин эту тварь! Ни один эндермен и всполох не уйдёт от Белых Колпаков!",
			"Натянуть арбалеты! Бей нечисть прямо под клюв!!"
	};

	private final ServerBossBar bossBar = new ServerBossBar(
			Text.literal("Верховный Магистр Белых Колпаков").formatted(Formatting.WHITE, Formatting.BOLD),
			BossBar.Color.WHITE,
			BossBar.Style.NOTCHED_10
	);

	private BlockPos homePos = null;
	private int homeRadius = 26;
	private int combatCooldown = 0;
	private int specialCooldown = 60;
	private int warningCooldown = 0;
	private int summonPhase = 0;
	private final Map<UUID, Integer> playerCloseTicks = new HashMap<>();
	private final Map<UUID, Integer> hostilePlayers = new HashMap<>();

	public WhiteCapEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.experiencePoints = 15;
	}

	public static DefaultAttributeContainer.Builder createWhiteCapAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 42.0D)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31D)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0D)
				.add(EntityAttributes.GENERIC_ARMOR, 8.0D)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.25D);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(RANK, RANK_HUNTER);
		builder.add(ACTION_ANIM, 0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.78D));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		this.goalSelector.add(7, new LookAtEntityGoal(this, LivingEntity.class, 16.0F));
		this.goalSelector.add(8, new LookAroundGoal(this));
	}

	public int getRank() {
		return this.dataTracker.get(RANK);
	}

	public void setRank(int rank) {
		int clamped = MathHelper.clamp(rank, 0, 3);
		this.dataTracker.set(RANK, clamped);
		applyRankStats(clamped);
	}

	public int getActionAnimTicks() {
		return this.dataTracker.get(ACTION_ANIM);
	}

	public void triggerActionAnim(int ticks) {
		this.dataTracker.set(ACTION_ANIM, ticks);
	}

	public void setHomeAnchor(BlockPos pos, int radius) {
		this.homePos = pos;
		this.homeRadius = radius;
		this.setPersistent();
	}

	private void applyRankStats(int rank) {
		double maxHp = switch (rank) {
			case RANK_ARBALIST -> 38.0D;
			case RANK_APOTHECARY -> 44.0D;
			case RANK_GRAND_MASTER -> 260.0D;
			default -> 44.0D;
		};
		double armor = rank == RANK_GRAND_MASTER ? 14.0D : 8.0D;
		double speed = rank == RANK_HUNTER ? 0.33D : (rank == RANK_GRAND_MASTER ? 0.30D : 0.29D);
		double dmg = rank == RANK_GRAND_MASTER ? 14.0D : 9.5D;
		double kbRes = rank == RANK_GRAND_MASTER ? 0.65D : 0.25D;

		EntityAttributeInstance hpInst = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (hpInst != null) {
			hpInst.setBaseValue(maxHp);
		}
		EntityAttributeInstance armInst = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armInst != null) {
			armInst.setBaseValue(armor);
		}
		EntityAttributeInstance spdInst = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (spdInst != null) {
			spdInst.setBaseValue(speed);
		}
		EntityAttributeInstance dmgInst = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (dmgInst != null) {
			dmgInst.setBaseValue(dmg);
		}
		EntityAttributeInstance kbInst = this.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		if (kbInst != null) {
			kbInst.setBaseValue(kbRes);
		}
		this.setHealth((float) maxHp);
		this.experiencePoints = rank == RANK_GRAND_MASTER ? 120 : 18;

		Text title = switch (rank) {
			case RANK_ARBALIST -> Text.literal("Егерь Белых Колпаков").formatted(Formatting.WHITE);
			case RANK_APOTHECARY -> Text.literal("Чумной Доктор Колпаков").formatted(Formatting.AQUA);
			case RANK_GRAND_MASTER -> Text.literal("Верховный Магистр Белых Колпаков").formatted(Formatting.GOLD, Formatting.BOLD);
			default -> Text.literal("Чистильщик Белых Колпаков").formatted(Formatting.WHITE);
		};
		this.setCustomName(title);
		this.setCustomNameVisible(rank == RANK_GRAND_MASTER);
	}

	/**
	 * Полный перечень ненавистной Кланом «не такой нежити» и порождений Измерений!
	 */
	public static boolean isHatedAbomination(LivingEntity entity) {
		if (entity == null || !entity.isAlive() || entity instanceof WhiteCapEntity
				|| entity.getCommandTags().contains(TAG_CITADEL_PRISONER)) {
			return false;
		}
		// Клан Белых Колпаков ведёт открытую войну против Церкви и Ордена Святой Люче!
		if (entity instanceof HolyOrderEntity || entity instanceof LuceEntity) {
			return true;
		}
		if (entity instanceof net.minecraft.entity.passive.WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG)) {
			return true;
		}
		return entity instanceof EndermanEntity
				|| entity instanceof EndermiteEntity
				|| entity instanceof ShulkerEntity
				|| entity instanceof EnderDragonEntity
				|| entity instanceof WitherSkeletonEntity
				|| entity instanceof WitherEntity
				|| entity instanceof BlazeEntity
				|| entity instanceof GhastEntity
				|| entity instanceof MagmaCubeEntity
				|| entity instanceof ZoglinEntity
				|| entity instanceof ZombifiedPiglinEntity
				|| entity instanceof HoglinEntity
				|| entity instanceof PiglinBruteEntity
				|| entity instanceof PhantomEntity
				|| entity instanceof StrayEntity
				|| entity instanceof BoggedEntity
				|| entity instanceof HuskEntity
				|| entity instanceof DrownedEntity
				|| entity instanceof WitchEntity
				|| entity instanceof EvokerEntity
				|| entity instanceof VindicatorEntity
				|| entity instanceof PillagerEntity
				|| entity instanceof RavagerEntity
				|| entity instanceof VexEntity
				|| entity instanceof WardenEntity
				|| entity instanceof BreezeEntity
				|| entity instanceof GuardianEntity
				|| entity instanceof ElderGuardianEntity;
	}

	public static boolean isLesserUndeadNearCamp(LivingEntity entity) {
		return entity instanceof ZombieEntity || entity instanceof SkeletonEntity;
	}

	public static boolean isWearingClanHood(PlayerEntity player) {
		return player != null && player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.WHITE_CAP_HOOD);
	}

	public static boolean isHoldingForbiddenTaint(PlayerEntity player) {
		return isTaintedStack(player.getMainHandStack()) || isTaintedStack(player.getOffHandStack());
	}

	private static boolean isTaintedStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.isOf(Items.ENDER_PEARL)
				|| stack.isOf(Items.ENDER_EYE)
				|| stack.isOf(Items.BLAZE_ROD)
				|| stack.isOf(Items.BLAZE_POWDER)
				|| stack.isOf(Items.WITHER_SKELETON_SKULL)
				|| stack.isOf(Items.DRAGON_BREATH)
				|| stack.isOf(Items.END_CRYSTAL);
	}

	public static void applyAntiEnderSealIfNeeded(LivingEntity target) {
		if (target instanceof EndermanEntity enderman) {
			enderman.addCommandTag(TAG_SEALED_ENDER);
			if (enderman.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.ENCHANTED_HIT, enderman.getX(), enderman.getBodyY(0.5D), enderman.getZ(), 14, 0.35D, 0.7D, 0.35D, 0.05D);
				sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, enderman.getX(), enderman.getBodyY(0.5D), enderman.getZ(), 8, 0.35D, 0.6D, 0.35D, 0.02D);
			}
		}
	}

	public static void damageEnderDragon(ServerWorld sw, EnderDragonEntity dragon, LivingEntity attacker, float amount) {
		if (dragon == null || !dragon.isAlive()) {
			return;
		}
		DamageSource src = attacker != null
				? sw.getDamageSources().explosion(attacker, attacker)
				: sw.getDamageSources().magic();
		dragon.damagePart(dragon.head, src, amount);
		sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, dragon.head.getX(), dragon.head.getBodyY(0.5D), dragon.head.getZ(), 18, 0.8D, 0.8D, 0.8D, 0.08D);
		sw.spawnParticles(ParticleTypes.CRIT, dragon.head.getX(), dragon.head.getBodyY(0.5D), dragon.head.getZ(), 14, 0.7D, 0.7D, 0.7D, 0.12D);
	}

	public boolean isPlayerMarkedEnemy(PlayerEntity player) {
		return hostilePlayers.getOrDefault(player.getUuid(), 0) > 0;
	}

	public void markPlayerEnemy(PlayerEntity player, int ticks) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		hostilePlayers.put(player.getUuid(), Math.max(hostilePlayers.getOrDefault(player.getUuid(), 0), ticks));
		this.setTarget(player);
	}

	public void alertNearbyClanAgainst(LivingEntity enemy) {
		if (!(this.getWorld() instanceof ServerWorld sw) || enemy == null) {
			return;
		}
		List<WhiteCapEntity> brothers = sw.getEntitiesByClass(
				WhiteCapEntity.class,
				this.getBoundingBox().expand(28.0D, 14.0D, 28.0D),
				LivingEntity::isAlive
		);
		for (WhiteCapEntity brother : brothers) {
			if (enemy instanceof PlayerEntity p) {
				brother.markPlayerEnemy(p, 600);
			} else if (brother.getTarget() == null) {
				brother.setTarget(enemy);
			}
		}
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isFireImmune() {
		// Чумные мантии пропитаны алхимическим составом против огня всполохов!
		return true;
	}

	@Override
	public boolean canHaveStatusEffect(StatusEffectInstance effect) {
		if (effect.equals(StatusEffects.WITHER) || effect.getEffectType() == StatusEffects.WITHER
				|| effect.getEffectType() == StatusEffects.POISON) {
			return false;
		}
		return super.canHaveStatusEffect(effect);
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		if (getRank() == RANK_GRAND_MASTER) {
			this.bossBar.addPlayer(player);
		}
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.bossBar.removePlayer(player);
	}

	@Override
	public void tick() {
		super.tick();

		int anim = getActionAnimTicks();
		if (anim > 0) {
			this.dataTracker.set(ACTION_ANIM, anim - 1);
		}

		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (getRank() == RANK_GRAND_MASTER) {
			this.bossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
		} else if (!this.bossBar.getPlayers().isEmpty()) {
			this.bossBar.clearPlayers();
		}

		if (combatCooldown > 0) {
			combatCooldown--;
		}
		if (specialCooldown > 0) {
			specialCooldown--;
		}
		if (warningCooldown > 0) {
			warningCooldown--;
		}

		// Обновляем таймеры враждебности к игрокам
		hostilePlayers.entrySet().removeIf(e -> {
			int next = e.getValue() - 1;
			if (next <= 0) {
				return true;
			}
			e.setValue(next);
			return false;
		});

		// Каждые 10 тиков оцениваем обстановку: ненавистные мобы и подозрительные игроки
		if (this.age % 10 == 0) {
			evaluatePlayerSuspicion(sw);
			acquirePriorityTarget(sw);
			maintainHomePatrol();
		}

		// В Краю арбалетчики и магистры также сбивают Кристаллы Края, лечащие ненавистного Дракона!
		if ((getRank() == RANK_ARBALIST || getRank() == RANK_GRAND_MASTER) && combatCooldown <= 0 && this.age % 20 == 0) {
			List<EndCrystalEntity> crystals = sw.getEntitiesByClass(
					EndCrystalEntity.class,
					this.getBoundingBox().expand(42.0D),
					Entity::isAlive
			);
			if (!crystals.isEmpty()) {
				EndCrystalEntity crystal = crystals.get(0);
				shootSilverPurgeBoltAtPoint(sw, crystal.getPos().add(0.0D, 1.0D, 0.0D));
				crystal.damage(sw.getDamageSources().mobAttack(this), 10.0F);
				combatCooldown = 32;
			}
		}

		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive()
				|| (target instanceof PlayerEntity tp && (tp.isCreative() || tp.isSpectator())))) {
			this.setTarget(null);
			target = null;
		}

		if (target != null) {
			tickCombatBehavior(sw, target);
		} else if (getRank() == RANK_APOTHECARY && combatCooldown <= 0 && this.age % 30 == 0) {
			// Вне боя чумной аптекарь подлечивает раненых братьев по клану
			List<WhiteCapEntity> wounded = sw.getEntitiesByClass(
					WhiteCapEntity.class,
					this.getBoundingBox().expand(14.0D),
					ally -> ally.isAlive() && ally.getHealth() < ally.getMaxHealth() * 0.8F
			);
			if (!wounded.isEmpty()) {
				throwPlagueFlaskAt(sw, wounded.get(0).getPos().add(0.0D, 0.8D, 0.0D));
				combatCooldown = 55;
			}
		}
	}

	private void evaluatePlayerSuspicion(ServerWorld sw) {
		List<ServerPlayerEntity> nearbyPlayers = sw.getEntitiesByClass(
				ServerPlayerEntity.class,
				this.getBoundingBox().expand(15.0D, 8.0D, 15.0D),
				p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
		);

		for (ServerPlayerEntity sp : nearbyPlayers) {
			UUID uuid = sp.getUuid();
			if (isPlayerMarkedEnemy(sp)) {
				continue;
			}

			// Если на игроке Колпак Чумного Доктора — принимаем за своего!
			if (isWearingClanHood(sp)) {
				playerCloseTicks.remove(uuid);
				if (warningCooldown <= 0 && this.squaredDistanceTo(sp) < 49.0D && sw.random.nextInt(14) == 0) {
					String line = BROTHER_GREETINGS[sw.random.nextInt(BROTHER_GREETINGS.length)];
					sp.sendMessage(Text.literal("[Белый Колпак] ").formatted(Formatting.AQUA, Formatting.BOLD)
							.append(Text.literal(line).formatted(Formatting.WHITE)), true);
					warningCooldown = 220;
				}
				continue;
			}

			double distSq = this.squaredDistanceTo(sp);

			// 0. На игроке регалии ненавистной Церкви Святой Люче (Жёлтый Дождевик, Посох или Хоругвь Ордена)!
			if (distSq <= 225.0D && HolyOrderEntity.isWearingLuceRegalia(sp)) {
				sp.sendMessage(Text.literal("[Белый Колпак] ").formatted(Formatting.RED, Formatting.BOLD)
						.append(Text.literal("Приспешник Церкви Люче в жёлтых тряпках святош!! Травить его чумными колбами!!").formatted(Formatting.YELLOW)), false);
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PILLAGER_AMBIENT, SoundCategory.HOSTILE, 1.2F, 0.75F);
				markPlayerEnemy(sp, 700);
				alertNearbyClanAgainst(sp);
				continue;
			}

			// 1. В руках игрока предметы ненавистной скверны Края/Ада -> мгновенная тревога!
			if (distSq <= 144.0D && isHoldingForbiddenTaint(sp)) {
				sp.sendMessage(Text.literal("[Белый Колпак] ").formatted(Formatting.RED, Formatting.BOLD)
						.append(Text.literal("У чужака в руках артефакты скверны! В карантин его!!").formatted(Formatting.YELLOW)), false);
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PILLAGER_AMBIENT, SoundCategory.HOSTILE, 1.2F, 0.75F);
				markPlayerEnemy(sp, 600);
				alertNearbyClanAgainst(sp);
				continue;
			}

			// 2. Верховный Магистр в Главной Цитадели вообще не терпит чужаков без колпака!
			if (getRank() == RANK_GRAND_MASTER && distSq <= 196.0D) {
				sp.sendMessage(Text.literal("[Верховный Магистр] ").formatted(Formatting.GOLD, Formatting.BOLD)
						.append(Text.literal("Безмасочный смеет входить в святыню Белых Колпаков?! Истребить!").formatted(Formatting.RED)), false);
				markPlayerEnemy(sp, 800);
				alertNearbyClanAgainst(sp);
				continue;
			}

			// 3. Недолюбливание при сближении: сначала ворчат, а если подойти вплотную и наглеть — бьют!
			if (distSq <= 22.0D) {
				int ticks = playerCloseTicks.getOrDefault(uuid, 0) + 10;
				playerCloseTicks.put(uuid, ticks);
				if (ticks >= 60) {
					sp.sendMessage(Text.literal("[Белый Колпак] ").formatted(Formatting.RED, Formatting.BOLD)
							.append(Text.literal("Тебя предупреждали не лезть под клюв, чужак!").formatted(Formatting.GRAY)), false);
					markPlayerEnemy(sp, 500);
					alertNearbyClanAgainst(sp);
				} else if (warningCooldown <= 0) {
					this.getLookControl().lookAt(sp, 35.0F, 35.0F);
					String warn = SUSPICIOUS_WARNINGS[sw.random.nextInt(SUSPICIOUS_WARNINGS.length)];
					sp.sendMessage(Text.literal("[Белый Колпак] ").formatted(Formatting.WHITE, Formatting.BOLD)
							.append(Text.literal(warn).formatted(Formatting.GRAY)), true);
					sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_VINDICATOR_AMBIENT, SoundCategory.HOSTILE, 0.9F, 0.8F);
					warningCooldown = 90;
				}
			} else if (distSq <= 110.0D) {
				playerCloseTicks.put(uuid, Math.max(0, playerCloseTicks.getOrDefault(uuid, 0) - 5));
				if (warningCooldown <= 0 && sw.random.nextInt(8) == 0) {
					this.getLookControl().lookAt(sp, 30.0F, 30.0F);
					String warn = SUSPICIOUS_WARNINGS[sw.random.nextInt(SUSPICIOUS_WARNINGS.length)];
					sp.sendMessage(Text.literal("[Белый Колпак] ").formatted(Formatting.WHITE, Formatting.BOLD)
							.append(Text.literal(warn).formatted(Formatting.GRAY)), true);
					warningCooldown = 180;
				}
			} else {
				playerCloseTicks.remove(uuid);
			}
		}
	}

	private void acquirePriorityTarget(ServerWorld sw) {
		LivingEntity current = this.getTarget();
		if (current != null && current.isAlive()) {
			if (isHatedAbomination(current) || (current instanceof PlayerEntity p && isPlayerMarkedEnemy(p))) {
				return;
			}
		}

		// 1. Сначала ищем Эндер-дракона (в радиусе 64 блоков)
		List<EnderDragonEntity> dragons = sw.getEntitiesByClass(
				EnderDragonEntity.class,
				this.getBoundingBox().expand(64.0D),
				LivingEntity::isAlive
		);
		if (!dragons.isEmpty()) {
			this.setTarget(dragons.get(0));
			return;
		}

		// 2. Ищем любых ненавистных тварей (эндермены, визер-скелеты, всполохи, визеры, фантомы, ведьмы и т.д.) в радиусе 42 блоков
		List<LivingEntity> abominations = sw.getEntitiesByClass(
				LivingEntity.class,
				this.getBoundingBox().expand(42.0D, 28.0D, 42.0D),
				WhiteCapEntity::isHatedAbomination
		);
		if (!abominations.isEmpty()) {
			abominations.sort(Comparator.comparingDouble(this::squaredDistanceTo));
			LivingEntity chosen = abominations.get(0);
			this.setTarget(chosen);
			if (warningCooldown <= 0 && sw.random.nextInt(6) == 0) {
				shoutBattleCryNearby(sw);
				warningCooldown = 160;
			}
			return;
		}

		// 3. Враждебные игроки
		List<ServerPlayerEntity> enemyPlayers = sw.getEntitiesByClass(
				ServerPlayerEntity.class,
				this.getBoundingBox().expand(32.0D, 16.0D, 32.0D),
				p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && isPlayerMarkedEnemy(p)
		);
		if (!enemyPlayers.isEmpty()) {
			enemyPlayers.sort(Comparator.comparingDouble(this::squaredDistanceTo));
			this.setTarget(enemyPlayers.get(0));
			return;
		}

		// 4. Обычная нежить (зомби/скелеты), если подошла близко к лагерю (18 блоков)
		List<LivingEntity> lesserUndead = sw.getEntitiesByClass(
				LivingEntity.class,
				this.getBoundingBox().expand(18.0D, 10.0D, 18.0D),
				e -> e.isAlive() && isLesserUndeadNearCamp(e)
		);
		if (!lesserUndead.isEmpty()) {
			lesserUndead.sort(Comparator.comparingDouble(this::squaredDistanceTo));
			this.setTarget(lesserUndead.get(0));
		}
	}

	private void shoutBattleCryNearby(ServerWorld sw) {
		boolean fightingChurch = this.getTarget() instanceof HolyOrderEntity || this.getTarget() instanceof LuceEntity;
		String cry = fightingChurch
				? "Святоши Ордена Люче! Залить их кресты и витражи чумной кислотой!! Слава КБК!!"
				: BATTLE_CRIES[sw.random.nextInt(BATTLE_CRIES.length)];
		for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, this.getBoundingBox().expand(24.0D), LivingEntity::isAlive)) {
			sp.sendMessage(Text.literal("[Клан Белых Колпаков] ").formatted(Formatting.AQUA, Formatting.BOLD)
					.append(Text.literal(cry).formatted(Formatting.WHITE)), true);
		}
	}

	private void maintainHomePatrol() {
		if (homePos == null || this.getTarget() != null) {
			return;
		}
		double distSq = this.getBlockPos().getSquaredDistance(homePos);
		if (distSq > (double) (homeRadius * homeRadius)) {
			this.getNavigation().startMovingTo(homePos.getX() + 0.5D, homePos.getY(), homePos.getZ() + 0.5D, 1.05D);
		}
	}

	private void tickCombatBehavior(ServerWorld sw, LivingEntity target) {
		this.getLookControl().lookAt(target, 35.0F, 35.0F);
		double distSq = this.squaredDistanceTo(target);
		int rank = getRank();

		if (rank == RANK_GRAND_MASTER) {
			tickGrandMasterBossCombat(sw, target, distSq);
			return;
		}

		if (rank == RANK_ARBALIST) {
			if (distSq < 36.0D) {
				// Отступает на выгодную дистанцию стрельбы
				Vec3d away = this.getPos().subtract(target.getPos()).normalize().multiply(0.26D);
				this.addVelocity(away.x, 0.0D, away.z);
			} else if (distSq > 576.0D) {
				this.getNavigation().startMovingTo(target, 1.08D);
			} else {
				this.getNavigation().stop();
			}

			if (combatCooldown <= 0 && distSq <= 2304.0D && (this.canSee(target) || target instanceof EnderDragonEntity)) {
				fireSilverBoltAtEntity(sw, target, 14.0F);
				combatCooldown = 28;
			}
			return;
		}

		if (rank == RANK_APOTHECARY) {
			if (distSq > 225.0D) {
				this.getNavigation().startMovingTo(target, 1.05D);
			} else {
				this.getNavigation().stop();
			}

			if (combatCooldown <= 0 && distSq <= 1225.0D) {
				if (target instanceof EnderDragonEntity || target.getY() - this.getY() > 7.0D) {
					fireSilverBoltAtEntity(sw, target, 16.0F);
				} else {
					throwPlagueFlaskAt(sw, target.getPos().add(0.0D, target.getHeight() * 0.45D, 0.0D));
				}
				combatCooldown = 38;
			}
			return;
		}

		// RANK_HUNTER (Чистильщик ближнего боя + серебряный гарпун по летающим тварям!)
		this.getNavigation().startMovingTo(target, 1.24D);

		// Рывок к эндермену / всполоху / врагу на средней дистанции
		if (specialCooldown <= 0 && distSq >= 16.0D && distSq <= 100.0D && this.isOnGround()) {
			Vec3d leap = target.getPos().subtract(this.getPos()).normalize().multiply(0.85D);
			this.setVelocity(leap.x, 0.42D, leap.z);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0F, 1.15F);
			specialCooldown = 70;
		}

		// Если цель высоко летает (дракон, гаст, высоко висящий всполох/фантом) — метает серебряный дротик!
		if (combatCooldown <= 0 && (target instanceof EnderDragonEntity || (target.getY() - this.getY() > 3.5D && distSq > 12.0D))) {
			fireSilverBoltAtEntity(sw, target, 13.0F);
			combatCooldown = 34;
			return;
		}

		if (combatCooldown <= 0 && distSq <= 7.8D) {
			performMeleePurgeStrike(sw, target);
			combatCooldown = 18;
		}
	}

	private void tickGrandMasterBossCombat(ServerWorld sw, LivingEntity target, double distSq) {
		// Призыв подкрепления Клана на 65% и 30% ХП
		float hpRatio = this.getHealth() / this.getMaxHealth();
		if ((summonPhase == 0 && hpRatio <= 0.65F) || (summonPhase == 1 && hpRatio <= 0.30F)) {
			summonPhase++;
			sw.playSound(null, this.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 1.6F, 0.95F);
			for (int i = 0; i < 2; i++) {
				WhiteCapEntity guard = ModEntities.WHITE_CAP_ENTITY.create(sw);
				if (guard != null) {
					double ang = sw.random.nextDouble() * Math.PI * 2.0D;
					guard.refreshPositionAndAngles(this.getX() + Math.cos(ang) * 2.5D, this.getY(), this.getZ() + Math.sin(ang) * 2.5D, this.getYaw(), 0.0F);
					guard.setRank(i == 0 ? RANK_HUNTER : RANK_APOTHECARY);
					if (homePos != null) {
						guard.setHomeAnchor(homePos, homeRadius);
					}
					guard.setTarget(target);
					sw.spawnEntity(guard);
					sw.spawnParticles(ParticleTypes.CLOUD, guard.getX(), guard.getY() + 1.0D, guard.getZ(), 18, 0.4D, 0.6D, 0.4D, 0.05D);
				}
			}
		}

		if (distSq > 36.0D) {
			this.getNavigation().startMovingTo(target, 1.15D);
		}

		// Кольцо Очищения Скверны вблизи
		if (specialCooldown <= 0 && distSq <= 42.0D) {
			triggerActionAnim(16);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.4F, 0.85F);
			for (int i = 0; i < 36; i++) {
				double rad = (Math.PI * 2.0D * i) / 36.0D;
				sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
						this.getX() + Math.cos(rad) * 4.5D, this.getY() + 0.4D, this.getZ() + Math.sin(rad) * 4.5D,
						2, 0.1D, 0.1D, 0.1D, 0.02D);
			}
			for (LivingEntity nearby : sw.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(5.5D, 3.0D, 5.5D), LivingEntity::isAlive)) {
				if (nearby instanceof WhiteCapEntity) {
					nearby.heal(10.0F);
					continue;
				}
				float novaDmg = isHatedAbomination(nearby) ? 45.0F : 16.0F;
				applyAntiEnderSealIfNeeded(nearby);
				nearby.damage(sw.getDamageSources().mobAttack(this), novaDmg);
				Vec3d push = nearby.getPos().subtract(this.getPos()).normalize().multiply(1.1D);
				nearby.addVelocity(push.x, 0.45D, push.z);
				nearby.velocityModified = true;
			}
			specialCooldown = 110;
			return;
		}

		if (combatCooldown <= 0) {
			if (distSq <= 8.5D) {
				performMeleePurgeStrike(sw, target);
				combatCooldown = 18;
			} else if (target instanceof EnderDragonEntity || target.getY() - this.getY() > 6.0D) {
				fireSilverBoltAtEntity(sw, target, 28.0F);
				combatCooldown = 24;
			} else {
				// Веер из 3 Чумных Колб!
				Vec3d aim = target.getPos().add(0.0D, target.getHeight() * 0.5D, 0.0D);
				throwPlagueFlaskAt(sw, aim);
				throwPlagueFlaskAt(sw, aim.add(1.6D, 0.0D, -1.2D));
				throwPlagueFlaskAt(sw, aim.add(-1.6D, 0.0D, 1.2D));
				combatCooldown = 42;
			}
		}
	}

	private void performMeleePurgeStrike(ServerWorld sw, LivingEntity target) {
		this.swingHand(Hand.MAIN_HAND);
		triggerActionAnim(12);
		boolean abom = isHatedAbomination(target);
		float base = (float) this.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		float dmg = abom ? base * 3.0F : base;

		applyAntiEnderSealIfNeeded(target);
		if (target instanceof EnderDragonEntity dragon) {
			damageEnderDragon(sw, dragon, this, dmg * 1.4F);
		} else {
			target.damage(sw.getDamageSources().mobAttack(this), dmg);
		}

		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.0F, 0.95F);
		sw.spawnParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getBodyY(0.5D), target.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.1D);
		if (abom) {
			sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getBodyY(0.5D), target.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.05D);
		}
	}

	private void fireSilverBoltAtEntity(ServerWorld sw, LivingEntity target, float baseDmg) {
		Vec3d hitPos = target instanceof EnderDragonEntity d
				? d.head.getPos().add(0.0D, 0.5D, 0.0D)
				: target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D);
		shootSilverPurgeBoltAtPoint(sw, hitPos);

		boolean abom = isHatedAbomination(target);
		float finalDmg = abom ? baseDmg * 2.4F : baseDmg;
		applyAntiEnderSealIfNeeded(target);

		if (target instanceof EnderDragonEntity dragon) {
			damageEnderDragon(sw, dragon, this, finalDmg);
		} else {
			// Используем mobAttack + магию, чтобы эндермены НЕ МОГЛИ уклониться от серебряного болта!
			target.damage(sw.getDamageSources().mobAttack(this), finalDmg);
		}
	}

	private void shootSilverPurgeBoltAtPoint(ServerWorld sw, Vec3d dest) {
		triggerActionAnim(10);
		sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE, 1.15F, 1.18F);
		Vec3d start = new Vec3d(this.getX(), this.getEyeY() - 0.15D, this.getZ());
		Vec3d delta = dest.subtract(start);
		int steps = MathHelper.clamp((int) (delta.length() * 2.2D), 4, 96);
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / (double) steps;
			double px = start.x + delta.x * t;
			double py = start.y + delta.y * t;
			double pz = start.z + delta.z * t;
			sw.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			if (i % 3 == 0) {
				sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 1, 0.03D, 0.03D, 0.03D, 0.0D);
			}
		}
	}

	private void throwPlagueFlaskAt(ServerWorld sw, Vec3d targetPos) {
		triggerActionAnim(12);
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.HOSTILE, 1.0F, 0.9F + sw.random.nextFloat() * 0.2F);
		PlagueFlaskEntity flask = new PlagueFlaskEntity(sw, this);
		flask.setPosition(this.getX(), this.getEyeY() - 0.1D, this.getZ());
		Vec3d diff = targetPos.subtract(flask.getPos());
		double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
		flask.setVelocity(diff.x, diff.y + horiz * 0.18D, diff.z, 1.05F, 2.0F);
		sw.spawnEntity(flask);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Бойцы Клана не наносят урон своим братьям!
		if (source.getAttacker() instanceof WhiteCapEntity) {
			return false;
		}
		boolean hurt = super.damage(source, amount);
		if (hurt && !this.getWorld().isClient()) {
			Entity attacker = source.getAttacker();
			if (attacker instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator()) {
				markPlayerEnemy(p, 900);
				alertNearbyClanAgainst(p);
			} else if (attacker instanceof LivingEntity le && !(le instanceof WhiteCapEntity)) {
				this.setTarget(le);
				alertNearbyClanAgainst(le);
			}
		}
		return hurt;
	}

	@Override
	protected void dropLoot(DamageSource damageSource, boolean causedByPlayer) {
		super.dropLoot(damageSource, causedByPlayer);
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		int rank = getRank();
		if (rank == RANK_GRAND_MASTER) {
			dropStack(sw, new ItemStack(ModItems.PLAGUE_CANE));
			dropStack(sw, new ItemStack(ModItems.WHITE_CAP_HOOD));
			dropStack(sw, new ItemStack(ModItems.WHITE_CAP_FLASK, 6));
			dropStack(sw, new ItemStack(Items.EMERALD, 12 + sw.random.nextInt(9)));
			return;
		}

		dropStack(sw, new ItemStack(Items.EMERALD, 1 + sw.random.nextInt(3)));
		if (sw.random.nextFloat() < 0.45F) {
			dropStack(sw, new ItemStack(ModItems.WHITE_CAP_FLASK, 1 + sw.random.nextInt(2)));
		}
		if (sw.random.nextFloat() < 0.14F) {
			dropStack(sw, new ItemStack(ModItems.WHITE_CAP_HOOD));
		}
	}

	private void dropStack(ServerWorld sw, ItemStack stack) {
		ItemEntity ie = new ItemEntity(sw, this.getX(), this.getY() + 0.5D, this.getZ(), stack);
		ie.setToDefaultPickupDelay();
		sw.spawnEntity(ie);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("ClanRank", getRank());
		nbt.putInt("SummonPhase", summonPhase);
		nbt.putInt("HomeRadius", homeRadius);
		if (homePos != null) {
			nbt.putLong("HomePos", homePos.asLong());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("ClanRank")) {
			setRank(nbt.getInt("ClanRank"));
		}
		this.summonPhase = nbt.getInt("SummonPhase");
		if (nbt.contains("HomeRadius")) {
			this.homeRadius = nbt.getInt("HomeRadius");
		}
		if (nbt.contains("HomePos")) {
			this.homePos = BlockPos.fromLong(nbt.getLong("HomePos"));
		}
	}
}
