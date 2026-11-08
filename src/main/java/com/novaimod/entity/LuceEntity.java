package com.novaimod.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * «Люче» (Luce) — аниме-паломница Ватикана в жёлтом дождевике и зелёных сапожках («Сила Бога и Аниме!»).
 * Сочетает ОБА РЕЖИМА:
 *  1) МИРНАЯ СПУТНИЦА-ПАЛОМНИЦА для праведников: благословляет, лечит, изгоняет нежить небесными лучами,
 *     даёт скорость под дождём, спасает хозяина от смерти «Чудом Паломника» и за 3 пожертвования дарит свой комплект реликвий!
 *  2) КАРАЮЩИЙ СВЯТОЙ БОСС-ЭКЗОРЦИСТ (400 HP, 3 фазы, золотой BossBar): пробуждается, если ударить Люче,
 *     обидеть её пёсика Сантино или провоцировать её тяжкими грехами/долгами и нечестивыми предметами!
 */
public class LuceEntity extends PathAwareEntity {
	public static final String TAG_SANTINO_DOG = "novai_santino_dog";

	public static final int MODE_FOLLOW = 0;
	public static final int MODE_GUARD_HOME = 1;
	public static final int MODE_PRAY = 2;

	private static final TrackedData<Boolean> WRATH_MODE = DataTracker.registerData(LuceEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> COMPANION_MODE = DataTracker.registerData(LuceEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> CAST_ANIM_TICKS = DataTracker.registerData(LuceEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> BOSS_PHASE = DataTracker.registerData(LuceEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final String[] PEACEFUL_LINES = new String[]{
			"Да пребудет с тобой Свет в пути, странник! Вместе мы пройдём любую бурю — мой дождевик не промокает!",
			"Видишь ракушку паломника в моих глазах? Она всегда ведёт к надежде, даже сквозь самый тёмный лес!",
			"Сантино сегодня так звонко тявкает — чует поблизости скелетов или очередных коллекторов МФО!",
			"Дождик — это благословение с небес! Не зря я надела любимые зелёные резиновые сапожки~"
	};

	private static final String[] SINNER_WARNING_LINES = new String[]{
			"Ох... От твоего кошелька несёт ростовщическими долгами МФО и грехами казино! Принеси покаянное пожертвование (изумруд, золото или хлеб)!",
			"Убери от меня эту мерзость! Положи пожертвование паломникам, пока я не взялась за посох всерьёз!"
	};

	private final ServerBossBar wrathBossBar = new ServerBossBar(
			Text.literal("✝ ЛЮЧЕ — КАРАЮЩИЙ СВЕТ ПАЛОМНИКА (СИЛА БОГА И АНИМЕ) ✝").formatted(Formatting.GOLD, Formatting.BOLD),
			BossBar.Color.YELLOW,
			BossBar.Style.NOTCHED_12
	);

	private UUID friendUuid = null;
	private BlockPos homePos = null;
	private int donationsCount = 0;
	private boolean relicsGifted = false;
	private int wrathPhase = 0;
	private int attackCooldown = 0;
	private int smiteCooldown = 40;
	private int miracleCooldown = 0;
	private int chatCooldown = 0;

	private static class PendingSkyPillar {
		final Vec3d pos;
		int ticksLeft;

		PendingSkyPillar(Vec3d pos, int ticksLeft) {
			this.pos = pos;
			this.ticksLeft = ticksLeft;
		}
	}

	private final List<PendingSkyPillar> pendingPillars = new ArrayList<>();

	public LuceEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.setPersistent();
		this.experiencePoints = 50;
		updateDisplayName();
	}

	public static DefaultAttributeContainer.Builder createLuceAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 120.0D)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32D)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0D)
				.add(EntityAttributes.GENERIC_ARMOR, 12.0D)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.45D)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 44.0D);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(WRATH_MODE, false);
		builder.add(COMPANION_MODE, MODE_GUARD_HOME);
		builder.add(CAST_ANIM_TICKS, 0);
		builder.add(BOSS_PHASE, 1);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.80D));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0F));
		this.goalSelector.add(7, new LookAroundGoal(this));
	}

	public boolean isWrathMode() {
		return this.dataTracker.get(WRATH_MODE);
	}

	public int getCompanionMode() {
		return this.dataTracker.get(COMPANION_MODE);
	}

	public boolean isPrayingPose() {
		return !isWrathMode() && getCompanionMode() == MODE_PRAY;
	}

	public int getBossPhase() {
		return this.dataTracker.get(BOSS_PHASE);
	}

	public int getCastAnimTicks() {
		return this.dataTracker.get(CAST_ANIM_TICKS);
	}

	public void triggerCastAnim(int ticks) {
		this.dataTracker.set(CAST_ANIM_TICKS, ticks);
	}

	public boolean isFriendOf(PlayerEntity player) {
		return player != null && this.friendUuid != null && this.friendUuid.equals(player.getUuid());
	}

	public void setHomePos(BlockPos pos) {
		this.homePos = pos != null ? pos.toImmutable() : null;
	}

	public void awakenDivineWrath(ServerPlayerEntity offender, String reasonLine) {
		if (isWrathMode()) {
			return;
		}
		this.dataTracker.set(WRATH_MODE, true);
		this.wrathPhase = 0;
		this.dataTracker.set(BOSS_PHASE, 1);
		EntityAttributeInstance maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHp != null) {
			maxHp.setBaseValue(400.0D);
		}
		EntityAttributeInstance armor = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armor != null) {
			armor.setBaseValue(15.0D);
		}
		this.setHealth(400.0F);
		this.triggerCastAnim(30);
		updateDisplayName();

		if (this.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.5F, 0.75F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.75F, 1.35F);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.6D), this.getZ(), 60, 0.8D, 1.1D, 0.8D, 0.14D);
		}
		if (offender != null) {
			offender.sendMessage(
					Text.literal("✝ [Люче — Праведный Гнев]: ").formatted(Formatting.GOLD, Formatting.BOLD)
							.append(Text.literal(reasonLine).formatted(Formatting.RED, Formatting.BOLD)),
					false
			);
		}
	}

	public void calmDownWithRosary(ServerPlayerEntity penitent) {
		if (!isWrathMode()) {
			return;
		}
		this.dataTracker.set(WRATH_MODE, false);
		this.wrathPhase = 0;
		this.dataTracker.set(BOSS_PHASE, 1);
		this.wrathBossBar.clearPlayers();
		this.pendingPillars.clear();
		EntityAttributeInstance maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHp != null) {
			maxHp.setBaseValue(120.0D);
		}
		this.setHealth(120.0F);
		updateDisplayName();

		if (this.getWorld() instanceof ServerWorld sw) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1.3F, 1.2F);
			sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getBodyY(0.8D), this.getZ(), 14, 0.5D, 0.5D, 0.5D, 0.03D);
		}
		penitent.sendMessage(
				Text.literal("✝ [Люче]: Я вижу твоё искреннее раскаяние перед Святыми Чётками... Гнев сменяется милостью! Больше не греши!")
						.formatted(Formatting.YELLOW, Formatting.BOLD),
				false
		);
	}

	private void updateDisplayName() {
		if (isWrathMode()) {
			this.setCustomName(Text.literal("✝ Люче (Гнев Божий и Аниме) ✝").formatted(Formatting.GOLD, Formatting.BOLD));
		} else {
			this.setCustomName(Text.literal("Люче — Паломница Света").formatted(Formatting.YELLOW));
		}
		this.setCustomNameVisible(true);
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.wrathBossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.wrathBossBar.clearPlayers();
		super.remove(reason);
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.isOf(DamageTypes.LIGHTNING_BOLT)
				|| source.isOf(DamageTypes.FALL)
				|| source.isOf(DamageTypes.IN_FIRE)
				|| source.isOf(DamageTypes.ON_FIRE)) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	private static boolean isPilgrimOffering(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.isOf(Items.EMERALD)
				|| stack.isOf(Items.GOLD_INGOT)
				|| stack.isOf(Items.GOLDEN_APPLE)
				|| stack.isOf(Items.BREAD)
				|| stack.isOf(Items.AMETHYST_SHARD)
				|| stack.isIn(ItemTags.CANDLES);
	}

	private static boolean isUnholyProvocation(ItemStack stack) {
		return stack.isOf(ModItems.STRANGE_MEAT) || stack.isOf(ModItems.DRUNKEN_OBLIVION);
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getStackInHand(hand);
		if (held.isOf(ModItems.POKEBALL) || held.isOf(ModItems.LUCE_ROSARY)) {
			return ActionResult.PASS;
		}
		if (this.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(this.getWorld() instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
			return ActionResult.SUCCESS;
		}

		if (isWrathMode()) {
			return ActionResult.PASS;
		}

		// 1. Покаянное пожертвование паломника!
		if (isPilgrimOffering(held)) {
			if (!sp.isInCreativeMode()) {
				held.decrement(1);
			}
			this.friendUuid = sp.getUuid();
			if (this.homePos == null) {
				this.homePos = sp.getBlockPos().toImmutable();
			}
			this.donationsCount++;
			this.setHealth(this.getMaxHealth());
			this.triggerCastAnim(24);

			// Очищаем негативные эффекты и даруем благословение!
			sp.removeStatusEffect(StatusEffects.POISON);
			sp.removeStatusEffect(StatusEffects.WITHER);
			sp.removeStatusEffect(StatusEffects.SLOWNESS);
			sp.removeStatusEffect(StatusEffects.WEAKNESS);
			sp.removeStatusEffect(StatusEffects.DARKNESS);
			sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 20, 1));
			sp.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 90, 1));

			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.25F);
			sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.6D), sp.getZ(), 24, 0.45D, 0.6D, 0.45D, 0.05D);

			if (!this.relicsGifted && this.donationsCount >= 3) {
				this.relicsGifted = true;
				giveStackOrDrop(sp, new ItemStack(ModItems.LUCE_RAINCOAT, 1));
				giveStackOrDrop(sp, new ItemStack(ModItems.LUCE_STAFF, 1));
				giveStackOrDrop(sp, new ItemStack(ModItems.LUCE_ROSARY, 1));
				this.dataTracker.set(COMPANION_MODE, MODE_FOLLOW);
				sw.playSound(null, this.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.1F, 1.1F);
				sp.sendMessage(
						Text.literal("✝ [Люче]: ").formatted(Formatting.GOLD, Formatting.BOLD)
								.append(Text.literal("Твоё сердце чисто, паломник! Прими мои святые дары: Жёлтый Дождевик, Посох Бордоне и Миссионерские Чётки — и пойдём вместе нести Свет!").formatted(Formatting.YELLOW)),
						false
				);
			} else {
				this.dataTracker.set(COMPANION_MODE, MODE_FOLLOW);
				int rem = Math.max(0, 3 - this.donationsCount);
				String tail = !this.relicsGifted
						? " (Ещё пожертвований до дара реликвий Люче: " + rem + "!)"
						: " (Я иду с тобой!)";
				sp.sendMessage(
						Text.literal("✝ [Люче]: ").formatted(Formatting.YELLOW, Formatting.BOLD)
								.append(Text.literal("Благодарю за пожертвование! Твои грехи отпущены, благословение даровано — я пойду с тобой в паломничество!" + tail).formatted(Formatting.WHITE)),
						false
				);
			}
			return ActionResult.SUCCESS;
		}

		// 2. Провокация нечестивыми предметами или тяжкими грехами без покаяния!
		ModPersistentState state = ModPersistentState.get(sw.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(sp.getUuid());
		if (isUnholyProvocation(held)) {
			awakenDivineWrath(sp, "ТЫ СМЕЕШЬ СОВАТЬ МНЕ В ЛИЦО ЭТУ ЕРЕТИЧЕСКУЮ ГАДОСТЬ?! ПОКАЙСЯ ПЕРЕД СИЛОЙ БОГА И АНИМЕ!!");
			return ActionResult.SUCCESS;
		}
		if (!isFriendOf(sp) && acc.debt >= 15_000L) {
			String warn = SINNER_WARNING_LINES[sw.random.nextInt(SINNER_WARNING_LINES.length)];
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.NEUTRAL, 0.9F, 0.9F);
			sp.sendMessage(
					Text.literal("✝ [Люче]: ").formatted(Formatting.GOLD, Formatting.BOLD)
							.append(Text.literal(warn).formatted(Formatting.RED)),
					false
			);
			return ActionResult.SUCCESS;
		}

		// 3. Дружественное переключение режима спутницы (или знакомство для безгрешного игрока!)
		if (this.friendUuid == null) {
			this.friendUuid = sp.getUuid();
			this.dataTracker.set(COMPANION_MODE, MODE_FOLLOW);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
			sp.sendMessage(
					Text.literal("✝ [Люче]: Привет, добрый путник! На тебе нет тяжких грехов — пойдём в паломничество вместе! (ПКМ — сменить режим, 3 дара золотом/изумрудом/хлебом — получить мои реликвии!)")
							.formatted(Formatting.YELLOW),
					false
			);
			return ActionResult.SUCCESS;
		}

		if (isFriendOf(sp)) {
			int next = (getCompanionMode() + 1) % 3;
			this.dataTracker.set(COMPANION_MODE, next);
			if (next == MODE_GUARD_HOME) {
				this.homePos = this.getBlockPos().toImmutable();
				sp.sendMessage(Text.literal("✝ [Люче]: Я буду хранить этот дом и часовню от любой нечисти!").formatted(Formatting.GREEN), true);
			} else if (next == MODE_FOLLOW) {
				sp.sendMessage(Text.literal("✝ [Люче]: В путь! Я иду за тобой с посохом паломника!").formatted(Formatting.AQUA), true);
			} else {
				this.getNavigation().stop();
				sp.sendMessage(Text.literal("✝ [Люче]: Я останусь здесь и помолюсь о твоём здравии~").formatted(Formatting.YELLOW), true);
			}
			return ActionResult.SUCCESS;
		}

		String line = PEACEFUL_LINES[sw.random.nextInt(PEACEFUL_LINES.length)];
		sp.sendMessage(Text.literal("✝ [Люче]: ").formatted(Formatting.YELLOW, Formatting.BOLD)
				.append(Text.literal(line).formatted(Formatting.WHITE)), false);
		return ActionResult.SUCCESS;
	}

	private static void giveStackOrDrop(ServerPlayerEntity sp, ItemStack stack) {
		if (!sp.getInventory().insertStack(stack)) {
			sp.dropItem(stack, false);
		}
	}

	@Override
	public void tick() {
		super.tick();
		int anim = getCastAnimTicks();
		if (anim > 0) {
			this.dataTracker.set(CAST_ANIM_TICKS, anim - 1);
		}

		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		if (PartyDollEntity.isUnderActiveDollAura(this)) {
			return;
		}

		if (this.attackCooldown > 0) this.attackCooldown--;
		if (this.smiteCooldown > 0) this.smiteCooldown--;
		if (this.miracleCooldown > 0) this.miracleCooldown--;
		if (this.chatCooldown > 0) this.chatCooldown--;

		tickPendingSkyPillars(sw);

		if (isWrathMode()) {
			tickWrathBoss(sw);
		} else {
			tickPeacefulCompanion(sw);
		}
	}

	private void tickPeacefulCompanion(ServerWorld sw) {
		this.wrathBossBar.setVisible(false);

		if (this.age % 25 == 0) {
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.75D), this.getZ(), 1, 0.25D, 0.3D, 0.25D, 0.01D);
		}

		// Экзорцизм нежити и демонов вокруг Люче!
		if (this.smiteCooldown <= 0) {
			LivingEntity unholy = findNearbyUnholyMonster(sw, 13.0D);
			if (unholy != null) {
				this.smiteCooldown = 36;
				this.triggerCastAnim(16);
				this.getLookControl().lookAt(unholy, 35.0F, 35.0F);
				sw.playSound(null, unholy.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, SoundCategory.NEUTRAL, 1.0F, 1.35F);
				for (double y = 0; y <= 6.0D; y += 0.6D) {
					sw.spawnParticles(ParticleTypes.END_ROD, unholy.getX(), unholy.getY() + y, unholy.getZ(), 3, 0.15D, 0.1D, 0.15D, 0.02D);
				}
				unholy.damage(sw.getDamageSources().mobAttack(this), 16.0F);
				unholy.setOnFireFor(5);
			}
		}

		if (this.friendUuid != null) {
			PlayerEntity p = sw.getPlayerByUuid(this.friendUuid);
			if (p instanceof ServerPlayerEntity owner && owner.isAlive() && !owner.isSpectator()) {
				// Бонус в дождь и грозу (жёлтый дождевик Люче!)
				if (this.age % 40 == 0 && (sw.isRaining() || owner.isTouchingWaterOrRain()) && this.squaredDistanceTo(owner) <= 18.0D * 18.0D) {
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 70, 1, true, false, true));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 70, 0, true, false, true));
				}

				// Чудо Паломника: спасает хозяина от гибели при < 5 HP!
				if (owner.getHealth() <= 5.0F && this.miracleCooldown <= 0 && this.squaredDistanceTo(owner) <= 20.0D * 20.0D) {
					this.miracleCooldown = 20 * 150;
					this.triggerCastAnim(25);
					owner.setHealth(Math.min(owner.getMaxHealth(), owner.getHealth() + 12.0F));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 30, 2));
					owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 10, 2));
					sw.playSound(null, owner.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.85F, 1.25F);
					sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, owner.getX(), owner.getBodyY(0.6D), owner.getZ(), 40, 0.5D, 0.7D, 0.5D, 0.18D);
					owner.sendMessage(
							Text.literal("✝ [Чудо Люче]: НЕ ПАДАЙ ДУХОМ! Сила Света хранит тебя в пути! (+6 сердец)").formatted(Formatting.GOLD, Formatting.BOLD),
							false
					);
				}

				if (getCompanionMode() == MODE_FOLLOW) {
					double dSq = this.squaredDistanceTo(owner);
					if (dSq > 22.0D * 22.0D) {
						this.refreshPositionAndAngles(owner.getX() + 1.2D, owner.getY(), owner.getZ() + 1.2D, owner.getYaw(), 0.0F);
						this.getNavigation().stop();
					} else if (dSq > 3.6D * 3.6D) {
						this.getNavigation().startMovingTo(owner, 1.15D);
					}
					return;
				}
			}
		}

		if (getCompanionMode() == MODE_PRAY) {
			this.getNavigation().stop();
		} else if (this.homePos != null && this.getBlockPos().getSquaredDistance(this.homePos) > 16.0D * 16.0D) {
			this.getNavigation().startMovingTo(this.homePos.getX() + 0.5D, this.homePos.getY(), this.homePos.getZ() + 0.5D, 1.0D);
		}
	}

	private LivingEntity findNearbyUnholyMonster(ServerWorld sw, double radius) {
		Box box = this.getBoundingBox().expand(radius, 6.0D, radius);
		List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> {
			if (!e.isAlive() || e == this) return false;
			return e.getType().isIn(EntityTypeTags.UNDEAD)
					|| e instanceof BlazeEntity
					|| e instanceof GhastEntity
					|| e instanceof PhantomEntity
					|| e instanceof WitchEntity
					|| e instanceof EndermanEntity
					|| (e instanceof HostileEntity && !(e instanceof ModNpcEntity));
		});
		return list.isEmpty() ? null : list.get(0);
	}

	// =========================================================================
	// РЕЖИМ 2: БОСС «ЛЮЧЕ — КАРАЮЩИЙ СВЕТ ВАТИКАНА (СИЛА БОГА И АНИМЕ)»
	// =========================================================================
	private void tickWrathBoss(ServerWorld sw) {
		this.wrathBossBar.setVisible(true);
		this.wrathBossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));

		ServerPlayerEntity target = null;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayerEntity sp : sw.getPlayers()) {
			if (sp.isAlive() && sp.squaredDistanceTo(this) <= 48.0D * 48.0D) {
				this.wrathBossBar.addPlayer(sp);
				if (!sp.isCreative() && !sp.isSpectator()) {
					double d = this.squaredDistanceTo(sp);
					if (d < bestDist) {
						bestDist = d;
						target = sp;
					}
				}
			} else {
				this.wrathBossBar.removePlayer(sp);
			}
		}

		if (this.age % 6 == 0) {
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.65D), this.getZ(), 4, 0.35D, 0.55D, 0.35D, 0.04D);
			sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.15D, this.getZ(), 2, 0.25D, 0.05D, 0.25D, 0.01D);
		}

		float hpRatio = this.getHealth() / this.getMaxHealth();
		if (hpRatio <= 0.65F && this.wrathPhase < 1) {
			this.wrathPhase = 1;
			this.dataTracker.set(BOSS_PHASE, 2);
			summonAngelicAllays(sw);
			broadcastWrath(sw, "✝ [Люче]: НЕБЕСНЫЙ СОБОР ОТКРЫВАЕТ ВРАТА! ПОКАЙСЯ, ГРЕШНИК!");
		} else if (hpRatio <= 0.30F && this.wrathPhase < 2) {
			this.wrathPhase = 2;
			this.dataTracker.set(BOSS_PHASE, 3);
			broadcastWrath(sw, "⚡ [Люче]: НА МОЕЙ СТОРОНЕ СИЛА БОГА И АНИМЕ!! СУДНЫЙ ПЕРЕЗВОН!!");
		}

		if (target == null) {
			return;
		}

		this.getLookControl().lookAt(target, 35.0F, 35.0F);
		this.getNavigation().startMovingTo(target, this.wrathPhase >= 2 ? 1.34D : 1.18D);

		if (bestDist <= 3.0D * 3.0D && this.attackCooldown <= 0) {
			this.attackCooldown = this.wrathPhase >= 2 ? 16 : 22;
			this.swingHand(Hand.MAIN_HAND);
			this.triggerCastAnim(12);
			target.damage(sw.getDamageSources().mobAttack(this), 10.5F);
			Vec3d kb = target.getPos().subtract(this.getPos()).normalize().multiply(0.55D);
			target.addVelocity(kb.x, 0.25D, kb.z);
			target.velocityModified = true;
			sw.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 0.9F, 1.4F);
		}

		// Спецатака: Небесные Столбы Света под ногами грешника!
		if (this.smiteCooldown <= 0) {
			this.smiteCooldown = this.wrathPhase >= 2 ? 48 : (this.wrathPhase == 1 ? 68 : 90);
			this.triggerCastAnim(22);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.3F, 1.15F);

			int count = this.wrathPhase >= 2 ? 3 : (this.wrathPhase == 1 ? 2 : 1);
			for (int i = 0; i < count; i++) {
				double ox = (i == 0) ? 0.0D : (sw.random.nextDouble() - 0.5D) * 7.0D;
				double oz = (i == 0) ? 0.0D : (sw.random.nextDouble() - 0.5D) * 7.0D;
				this.pendingPillars.add(new PendingSkyPillar(target.getPos().add(ox, 0.0D, oz), 24));
			}
		}
	}

	private void tickPendingSkyPillars(ServerWorld sw) {
		Iterator<PendingSkyPillar> it = this.pendingPillars.iterator();
		while (it.hasNext()) {
			PendingSkyPillar p = it.next();
			p.ticksLeft--;
			// Предупреждающее золотое кольцо на земле
			for (int i = 0; i < 12; i++) {
				double a = (Math.PI * 2.0D * i) / 12.0D;
				sw.spawnParticles(ParticleTypes.END_ROD, p.pos.x + Math.cos(a) * 2.2D, p.pos.y + 0.15D, p.pos.z + Math.sin(a) * 2.2D, 1, 0.0D, 0.02D, 0.0D, 0.0D);
			}
			if (p.ticksLeft <= 0) {
				it.remove();
				// Удар Небесного Столба Света!
				for (double y = 0.0D; y <= 14.0D; y += 0.65D) {
					sw.spawnParticles(ParticleTypes.END_ROD, p.pos.x, p.pos.y + y, p.pos.z, 5, 0.35D, 0.15D, 0.35D, 0.03D);
					sw.spawnParticles(ParticleTypes.FLASH, p.pos.x, p.pos.y + 1.0D, p.pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				}
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
				if (bolt != null) {
					bolt.refreshPositionAfterTeleport(p.pos.x, p.pos.y, p.pos.z);
					bolt.setCosmetic(true);
					sw.spawnEntity(bolt);
				}
				Box hitBox = new Box(p.pos.x - 2.4D, p.pos.y - 1.5D, p.pos.z - 2.4D, p.pos.x + 2.4D, p.pos.y + 4.5D, p.pos.z + 2.4D);
				for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, hitBox, e -> e.isAlive() && !e.isCreative() && !e.isSpectator())) {
					sp.damage(sw.getDamageSources().magic(), 11.0F);
					sp.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
				}
			}
		}
	}

	private void summonAngelicAllays(ServerWorld sw) {
		for (int i = -1; i <= 1; i += 2) {
			AllayEntity allay = EntityType.ALLAY.create(sw);
			if (allay != null) {
				allay.refreshPositionAndAngles(this.getX() + i * 2.0D, this.getY() + 1.5D, this.getZ(), 0.0F, 0.0F);
				allay.setCustomName(Text.literal("Херувим Света Люче").formatted(Formatting.AQUA));
				sw.spawnEntity(allay);
			}
		}
	}

	private void broadcastWrath(ServerWorld sw, String msg) {
		Text line = Text.literal(msg).formatted(Formatting.GOLD, Formatting.BOLD);
		for (ServerPlayerEntity sp : sw.getPlayers()) {
			if (sp.squaredDistanceTo(this) <= 48.0D * 48.0D) {
				sp.sendMessage(line, false);
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (!hurt || this.getWorld().isClient() || !this.isAlive()) {
			return hurt;
		}
		if (!isWrathMode() && source.getAttacker() instanceof ServerPlayerEntity sp && !sp.isCreative()) {
			awakenDivineWrath(sp, "ТЫ ПОДНЯЛ РУКУ НА БОЖЬЮ ПАЛОМНИЦУ?! ГОТОВЬСЯ К НЕМЕДЛЕННОМУ ЭКЗОРЦИЗМУ!!");
		}
		return true;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		this.wrathBossBar.clearPlayers();
		super.onDeath(damageSource);
		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		if (!this.relicsGifted) {
			this.dropStack(new ItemStack(ModItems.LUCE_RAINCOAT, 1));
			this.dropStack(new ItemStack(ModItems.LUCE_STAFF, 1));
			this.dropStack(new ItemStack(ModItems.LUCE_ROSARY, 1));
		}
		this.dropStack(new ItemStack(ModItems.REVIVAL_TOKEN, 1));
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.4F, 0.85F);
	}

	/**
	 * Если кто-то ударил пёсика Сантино — ближайшая Люче мгновенно впадает в Праведный Гнев!
	 */
	public static void onSantinoHurt(WolfEntity santino, ServerPlayerEntity attacker) {
		if (!(santino.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		for (LuceEntity luce : sw.getEntitiesByClass(LuceEntity.class, santino.getBoundingBox().expand(36.0D), LivingEntity::isAlive)) {
			luce.awakenDivineWrath(attacker, "ТЫ ТРОНУЛ МОЕГО ПЁСИКА САНТИНО?! ДАЖЕ СВЯТЫЕ УГОДНИКИ ТЕБЯ НЕ СПАСУТ!!");
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("WrathMode", isWrathMode());
		nbt.putInt("CompanionMode", getCompanionMode());
		nbt.putInt("DonationsCount", this.donationsCount);
		nbt.putBoolean("RelicsGifted", this.relicsGifted);
		nbt.putInt("WrathPhase", this.wrathPhase);
		if (this.friendUuid != null) {
			nbt.putUuid("FriendUuid", this.friendUuid);
		}
		if (this.homePos != null) {
			nbt.putLong("HomePos", this.homePos.asLong());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		boolean wrath = nbt.getBoolean("WrathMode");
		this.dataTracker.set(WRATH_MODE, wrath);
		if (wrath) {
			EntityAttributeInstance maxHp = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (maxHp != null) maxHp.setBaseValue(400.0D);
		}
		this.dataTracker.set(COMPANION_MODE, nbt.getInt("CompanionMode"));
		this.donationsCount = nbt.getInt("DonationsCount");
		this.relicsGifted = nbt.getBoolean("RelicsGifted");
		this.wrathPhase = nbt.getInt("WrathPhase");
		this.dataTracker.set(BOSS_PHASE, MathHelper.clamp(this.wrathPhase + 1, 1, 3));
		if (nbt.containsUuid("FriendUuid")) {
			this.friendUuid = nbt.getUuid("FriendUuid");
		}
		if (nbt.contains("HomePos")) {
			this.homePos = BlockPos.fromLong(nbt.getLong("HomePos"));
		}
		updateDisplayName();
	}
}
