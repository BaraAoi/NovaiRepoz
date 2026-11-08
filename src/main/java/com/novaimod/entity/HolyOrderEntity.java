package com.novaimod.entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.data.WorldVirginLedger;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
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
import net.minecraft.entity.mob.EvokerEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.RavagerEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
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
 * Боец и священнослужитель фракции Люче — «Святой Орден Паломников и Церковь Света» (`novaimod:holy_order`):
 * Ведут непримиримую Священную Войну против еретиков-алхимиков «Клана Белых Колпаков» (КБК) и сил тьмы!
 *
 * 4 ранга Церкви:
 *  - 0: Паладин Храма Люче (меч + щит с крестом, рывок щитом, блок стрел КБК)
 *  - 1: Арбалетчик Святой Инквизиции (стреляет лучами солнечных болтов по КБК и нежити)
 *  - 2: Священник Собора Люче (лечит союзников Святой Водой, принимает пожертвования, выдаёт Индульгенции и Хоругвь!)
 *  - 3: БОСС СОБОРА — Архиепископ Ватикана (320 HP, высокая митра, призыв стражи и Кольцо Небесных Столбов!)
 */
public class HolyOrderEntity extends PathAwareEntity {
	public static final int RANK_CRUSADER = 0;
	public static final int RANK_INQUISITOR = 1;
	public static final int RANK_PRIEST = 2;
	public static final int RANK_CARDINAL = 3;

	private static final TrackedData<Integer> RANK = DataTracker.registerData(HolyOrderEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> ACTION_ANIM = DataTracker.registerData(HolyOrderEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final String[] KBK_WAR_CRIES = new String[]{
			"Еретики Клана Белых Колпаков!! За Святую Люче и Мать-Церковь — В АТАКУ!!",
			"Сорвать клювастые маски с этих чумных богохульников! Свет очистит их котлы!",
			"Именем Собора Люче: ни один Белый Колпак не уйдёт от праведного меча!!"
	};

	private static final String[] FAITHFUL_GREETINGS = new String[]{
			"Да хранит тебя Свет и молитва Святой Люче, добрый паломник!",
			"Если встретишь еретиков КБК в белых колпаках — рази их без пощады, Церковь щедро вознаградит!",
			"В нашем Кафедральном Соборе всегда открыты врата для чистых сердцем и верного пёсика Сантино!"
	};

	private final ServerBossBar cardinalBossBar = new ServerBossBar(
			Text.literal("✝ Архиепископ Собора Святой Люче ✝").formatted(Formatting.GOLD, Formatting.BOLD),
			BossBar.Color.YELLOW,
			BossBar.Style.NOTCHED_10
	);

	private BlockPos homePos = null;
	private int homeRadius = 26;
	private UUID bannerCommanderUuid = null;
	private boolean followBannerCommander = true;
	private int temporarySummonTicks = -1;

	private int combatCooldown = 0;
	private int specialCooldown = 50;
	private int speechCooldown = 0;
	private int cardinalSummonStage = 0;
	private final Map<UUID, Integer> hostilePlayers = new HashMap<>();

	public HolyOrderEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
		this.experiencePoints = 16;
	}

	public static DefaultAttributeContainer.Builder createHolyOrderAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 56.0D)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31D)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 10.0D)
				.add(EntityAttributes.GENERIC_ARMOR, 10.0D)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0D)
				.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.35D);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(RANK, RANK_CRUSADER);
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
		int r = MathHelper.clamp(rank, 0, 3);
		this.dataTracker.set(RANK, r);
		applyRankStats(r);
	}

	public int getActionAnimTicks() {
		return this.dataTracker.get(ACTION_ANIM);
	}

	public void triggerActionAnim(int ticks) {
		this.dataTracker.set(ACTION_ANIM, ticks);
	}

	public void setHomeAnchor(BlockPos pos, int radius) {
		this.homePos = pos != null ? pos.toImmutable() : null;
		this.homeRadius = radius;
		this.setPersistent();
	}

	public void configureBannerSummon(ServerPlayerEntity commander, int durationTicks) {
		this.bannerCommanderUuid = commander.getUuid();
		this.followBannerCommander = true;
		this.temporarySummonTicks = durationTicks;
		this.setPersistent();
	}

	public boolean isCommandedBy(PlayerEntity player) {
		return player != null && this.bannerCommanderUuid != null && this.bannerCommanderUuid.equals(player.getUuid());
	}

	public boolean isVirginGarrisonCandidate() {
		return this.homePos != null && this.temporarySummonTicks < 0 && this.bannerCommanderUuid == null;
	}

	public boolean toggleCommanderOrder() {
		this.followBannerCommander = !this.followBannerCommander;
		if (!this.followBannerCommander) {
			this.homePos = this.getBlockPos().toImmutable();
			this.homeRadius = 14;
			this.getNavigation().stop();
		}
		return this.followBannerCommander;
	}

	private void applyRankStats(int rank) {
		double maxHp = switch (rank) {
			case RANK_INQUISITOR -> 46.0D;
			case RANK_PRIEST -> 50.0D;
			case RANK_CARDINAL -> 320.0D;
			default -> 60.0D;
		};
		double armor = switch (rank) {
			case RANK_CRUSADER -> 12.0D;
			case RANK_CARDINAL -> 15.0D;
			default -> 9.0D;
		};
		double speed = rank == RANK_CRUSADER ? 0.33D : (rank == RANK_CARDINAL ? 0.30D : 0.30D);
		double dmg = rank == RANK_CARDINAL ? 15.0D : 10.5D;
		double kb = rank == RANK_CARDINAL ? 0.70D : (rank == RANK_CRUSADER ? 0.45D : 0.25D);

		EntityAttributeInstance hpInst = this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (hpInst != null) hpInst.setBaseValue(maxHp);
		EntityAttributeInstance armInst = this.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
		if (armInst != null) armInst.setBaseValue(armor);
		EntityAttributeInstance spdInst = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (spdInst != null) spdInst.setBaseValue(speed);
		EntityAttributeInstance dmgInst = this.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (dmgInst != null) dmgInst.setBaseValue(dmg);
		EntityAttributeInstance kbInst = this.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
		if (kbInst != null) kbInst.setBaseValue(kb);

		this.setHealth((float) maxHp);
		this.experiencePoints = rank == RANK_CARDINAL ? 140 : 20;

		Text title = switch (rank) {
			case RANK_INQUISITOR -> Text.literal("Арбалетчик Инквизиции Люче").formatted(Formatting.AQUA);
			case RANK_PRIEST -> Text.literal("Священник Собора Люче").formatted(Formatting.YELLOW);
			case RANK_CARDINAL -> Text.literal("✝ Архиепископ Собора Люче ✝").formatted(Formatting.GOLD, Formatting.BOLD);
			default -> Text.literal("Паладин Ордена Люче").formatted(Formatting.WHITE);
		};
		this.setCustomName(title);
		this.setCustomNameVisible(rank == RANK_PRIEST || rank == RANK_CARDINAL);
	}

	public static boolean isWearingLuceRegalia(PlayerEntity player) {
		if (player == null) {
			return false;
		}
		return player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.LUCE_RAINCOAT)
				|| player.getMainHandStack().isOf(ModItems.LUCE_STAFF)
				|| player.getMainHandStack().isOf(ModItems.CRUSADE_BANNER)
				|| player.getOffHandStack().isOf(ModItems.LUCE_ROSARY);
	}

	public static boolean isUnholyOrRaiderMonster(LivingEntity e) {
		if (e == null || !e.isAlive() || e instanceof HolyOrderEntity || e instanceof LuceEntity || e instanceof ModNpcEntity || e instanceof CatgirlEntity) {
			return false;
		}
		if (e.getCommandTags().contains(WhiteCapEntity.TAG_CITADEL_PRISONER)) {
			return false;
		}
		return e.getType().isIn(EntityTypeTags.UNDEAD)
				|| e instanceof BlazeEntity
				|| e instanceof GhastEntity
				|| e instanceof WitherSkeletonEntity
				|| e instanceof WitchEntity
				|| e instanceof PhantomEntity
				|| e instanceof PillagerEntity
				|| e instanceof VindicatorEntity
				|| e instanceof EvokerEntity
				|| e instanceof RavagerEntity
				|| e instanceof VexEntity;
	}

	public boolean isPlayerMarkedHeretic(PlayerEntity player) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return false;
		}
		if (hostilePlayers.getOrDefault(player.getUuid(), 0) > 0) {
			return true;
		}
		if (WhiteCapEntity.isWearingClanHood(player)) {
			return true;
		}
		if (this.getWorld() instanceof ServerWorld sw) {
			ModPersistentState.PlayerFinanceData acc = ModPersistentState.get(sw.getServer()).getAccount(player.getUuid());
			return acc.holyOrderRep <= -30;
		}
		return false;
	}

	public void markPlayerEnemy(PlayerEntity player, int ticks) {
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		hostilePlayers.put(player.getUuid(), Math.max(hostilePlayers.getOrDefault(player.getUuid(), 0), ticks));
		this.setTarget(player);
	}

	public void clearPlayerHostility(PlayerEntity player) {
		if (player == null) {
			return;
		}
		hostilePlayers.remove(player.getUuid());
		if (this.getTarget() == player) {
			this.setTarget(null);
		}
	}

	public void alertNearbyChurchAgainst(LivingEntity enemy) {
		if (!(this.getWorld() instanceof ServerWorld sw) || enemy == null) {
			return;
		}
		List<HolyOrderEntity> allies = sw.getEntitiesByClass(
				HolyOrderEntity.class,
				this.getBoundingBox().expand(30.0D, 14.0D, 30.0D),
				LivingEntity::isAlive
		);
		for (HolyOrderEntity ally : allies) {
			if (enemy instanceof PlayerEntity p) {
				if (!ally.isCommandedBy(p)) {
					ally.markPlayerEnemy(p, 600);
				}
			} else if (ally.getTarget() == null) {
				ally.setTarget(enemy);
			}
		}
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		if (source.isOf(DamageTypes.LIGHTNING_BOLT)
				|| source.isOf(DamageTypes.IN_FIRE)
				|| source.isOf(DamageTypes.ON_FIRE)) {
			return true;
		}
		if (source.getAttacker() instanceof HolyOrderEntity
				|| source.getAttacker() instanceof LuceEntity
				|| (source.getAttacker() instanceof WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG))) {
			return true;
		}
		return super.isInvulnerableTo(source);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		this.cardinalBossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		this.cardinalBossBar.clearPlayers();
		super.remove(reason);
	}

	/**
	 * Взаимодействие со Священником / Архиепископом / Паладинами Церкви Люче!
	 */
	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getStackInHand(hand);
		if (held.isOf(ModItems.POKEBALL)) {
			return ActionResult.PASS;
		}
		if (this.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(this.getWorld() instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
			return ActionResult.SUCCESS;
		}

		if (WhiteCapEntity.isWearingClanHood(sp)) {
			sp.sendMessage(Text.literal("✝ [Орден Люче]: СНИМИ МЕРЗКИЙ КЛЮВ ЕРЕТИКОВ КБК, ПРЕЖДЕ ЧЕМ ЗАГОВОРИТЬ С ЦЕРКОВЬЮ!!").formatted(Formatting.RED, Formatting.BOLD), false);
			markPlayerEnemy(sp, 400);
			alertNearbyChurchAgainst(sp);
			return ActionResult.SUCCESS;
		}

		ModPersistentState state = ModPersistentState.get(sw.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(sp.getUuid());

		// 1. ИСПОВЕДЬ И ИНДУЛЬГЕНЦИЯ у Священника (даже если репутация отрицательная!)
		if (getRank() == RANK_PRIEST && acc.holyOrderRep < 0) {
			if (held.isOf(Items.GOLD_INGOT) && held.getCount() >= 8) {
				if (!sp.isInCreativeMode()) {
					held.decrement(8);
				}
				forgivePlayerSins(sw, sp, state, acc);
				return ActionResult.SUCCESS;
			}
			if (acc.balance >= 5_000L && sp.isSneaking()) {
				acc.balance -= 5_000L;
				ModServerNetworking.syncAccount(sp);
				forgivePlayerSins(sw, sp, state, acc);
				return ActionResult.SUCCESS;
			}
			sp.sendMessage(
					Text.literal("✝ [Священник Собора]: ").formatted(Formatting.YELLOW, Formatting.BOLD)
							.append(Text.literal("На тебе лежит грех против Церкви (Репутация: " + acc.holyOrderRep + ")! Дай 8 Золотых слитков (ПКМ) ИЛИ нажми Shift+ПКМ для покаянного взноса 5 000 ₽ со счёта, чтобы получить Индульгенцию!").formatted(Formatting.RED)),
					false
			);
			return ActionResult.SUCCESS;
		}

		if (isPlayerMarkedHeretic(sp)) {
			return ActionResult.PASS;
		}

		// 2. Церковная десятина и трофеи войны с КБК у Священника или Архиепископа!
		if (getRank() == RANK_PRIEST || getRank() == RANK_CARDINAL) {
			int repGain = 0;
			boolean consumeOne = true;
			if (held.isOf(ModItems.WHITE_CAP_FLASK)) {
				// Трофейная чумная колба КБК — уничтожается на алтаре!
				repGain = 15;
			} else if (held.isOf(ModItems.WHITE_CAP_HOOD)) {
				// Сорванный колпак чумного доктора КБК — главный военный трофей!
				repGain = 45;
				acc.kbkKilledForChurch += 2;
			} else if (held.isOf(Items.GOLDEN_APPLE) || held.isOf(Items.ENCHANTED_GOLDEN_APPLE)) {
				repGain = 18;
			} else if (held.isOf(Items.DIAMOND)) {
				repGain = 14;
			} else if (held.isOf(Items.EMERALD)) {
				repGain = 8;
			} else if (held.isOf(Items.GOLD_INGOT)) {
				repGain = 6;
			} else if (held.isOf(Items.AMETHYST_SHARD)) {
				repGain = 5;
			} else if (held.isOf(Items.BREAD) || held.isIn(ItemTags.CANDLES)) {
				long now = sw.getTime();
				if (now - acc.lastSmallTitheTick < 1200L) {
					sp.sendMessage(Text.literal("✝ [Священник]: Скромные приношения хлебом и свечами принимаются раз в минуту — для великого доверия принеси золото, изумруды или трофеи с еретиков КБК!").formatted(Formatting.YELLOW), true);
					return ActionResult.SUCCESS;
				}
				acc.lastSmallTitheTick = now;
				repGain = 3;
			} else {
				consumeOne = false;
			}

			if (consumeOne && repGain > 0) {
				if (!sp.isInCreativeMode()) {
					held.decrement(1);
				}
				acc.holyOrderRep = Math.min(500, acc.holyOrderRep + repGain);
				state.markDirty();
				this.triggerActionAnim(20);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.25F);
				sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.6D), sp.getZ(), 18, 0.4D, 0.5D, 0.4D, 0.04D);

				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 15, 1));
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 60, 0));

				// Выдача Святой Воды за каждые ценные дары и Хоругви при высоком доверии!
				if (repGain >= 14) {
					ItemStack hw = new ItemStack(ModItems.HOLY_WATER, 2);
					if (!sp.getInventory().insertStack(hw)) sp.dropItem(hw, false);
				}
				sp.sendMessage(
						Text.literal("✝ [Церковь Люче]: Пожертвование принято! (+").formatted(Formatting.GOLD, Formatting.BOLD)
								.append(Text.literal(repGain + " Доверия Ордена -> " + acc.holyOrderRep + "/100").formatted(Formatting.GREEN))
								.append(Text.literal(", убито КБК: " + acc.kbkKilledForChurch + "/5)").formatted(Formatting.AQUA)),
						false
				);
				return ActionResult.SUCCESS;
			}

			// Если у игрока высокая репутация (>= 80) и нет Хоругви в руках — Священник/Кардинал вручает Святую Хоругвь Похода!
			if (acc.holyOrderRep >= 80 && sp.isSneaking()) {
				boolean alreadyHasBanner = false;
				for (int i = 0; i < sp.getInventory().size(); i++) {
					if (sp.getInventory().getStack(i).isOf(ModItems.CRUSADE_BANNER)) {
						alreadyHasBanner = true;
						break;
					}
				}
				if (!alreadyHasBanner) {
					ItemStack banner = new ItemStack(ModItems.CRUSADE_BANNER, 1);
					if (!sp.getInventory().insertStack(banner)) sp.dropItem(banner, false);
					sw.playSound(null, this.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.15F);
					sp.sendMessage(Text.literal("✝ [Орден Святой Люче]: За верную службу Церкви и разгром КБК тебе вручается «Святая Хоругвь Ордена Люче»! Подними её в бою, чтобы призвать рыцарей!").formatted(Formatting.GOLD, Formatting.BOLD), false);
					return ActionResult.SUCCESS;
				}
			}

			sp.sendMessage(
					Text.literal("✝ [Священник Собора]: ").formatted(Formatting.YELLOW, Formatting.BOLD)
							.append(Text.literal("Твоё Доверие Церкви: " + acc.holyOrderRep + "/100 | Побеждено еретиков КБК: " + acc.kbkKilledForChurch + "/5. Приноси золото, изумруды или трофеи КБК (колбы/колпаки)! (При Доверии 80+ нажми Shift+ПКМ, чтобы получить Хоругвь Похода!)").formatted(Formatting.WHITE)),
					false
			);
			return ActionResult.SUCCESS;
		}

		// 3. Рыцари-Паладины и Арбалетчики Инквизиции
		if (isCommandedBy(sp)) {
			boolean following = toggleCommanderOrder();
			sp.sendMessage(
					Text.literal(following
							? "⚔ [Рыцарь Люче]: Есть, знаменосец! Иду за твоей Хоругвью!"
							: "🛡 [Рыцарь Люче]: Принято! Держу оборону на этом рубеже!").formatted(Formatting.GOLD),
					true
			);
			return ActionResult.SUCCESS;
		}

		String greet = FAITHFUL_GREETINGS[sw.random.nextInt(FAITHFUL_GREETINGS.length)];
		sp.sendMessage(
				Text.literal("✝ [Орден Люче]: ").formatted(Formatting.GOLD, Formatting.BOLD)
						.append(Text.literal(greet + " (Ваше доверие: " + acc.holyOrderRep + "/100, убито КБК: " + acc.kbkKilledForChurch + "/5)").formatted(Formatting.WHITE)),
				true
		);
		return ActionResult.SUCCESS;
	}

	private void forgivePlayerSins(ServerWorld sw, ServerPlayerEntity sp, ModPersistentState state, ModPersistentState.PlayerFinanceData acc) {
		acc.holyOrderRep = 0;
		state.markDirty();
		for (HolyOrderEntity member : sw.getEntitiesByClass(HolyOrderEntity.class, sp.getBoundingBox().expand(48.0D), LivingEntity::isAlive)) {
			member.clearPlayerHostility(sp);
		}
		for (LuceEntity luce : sw.getEntitiesByClass(LuceEntity.class, sp.getBoundingBox().expand(48.0D), LivingEntity::isAlive)) {
			if (luce.isWrathMode()) {
				luce.calmDownWithRosary(sp);
			}
		}
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.2F);
		sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.6D), sp.getZ(), 28, 0.5D, 0.6D, 0.5D, 0.05D);
		sp.sendMessage(Text.literal("✝ [Индульгенция Собора]: Твои прегрешения перед Церковью прощены! Орден Света снова в мире с тобой!").formatted(Formatting.GREEN, Formatting.BOLD), false);
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
		if (PartyDollEntity.isUnderActiveDollAura(this)) {
			return;
		}

		if (temporarySummonTicks > 0) {
			temporarySummonTicks--;
			if (temporarySummonTicks == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.5D), this.getZ(), 18, 0.3D, 0.5D, 0.3D, 0.04D);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.NEUTRAL, 0.8F, 1.3F);
				this.discard();
				return;
			}
		}

		if (combatCooldown > 0) combatCooldown--;
		if (specialCooldown > 0) specialCooldown--;
		if (speechCooldown > 0) speechCooldown--;

		hostilePlayers.entrySet().removeIf(e -> {
			int n = e.getValue() - 1;
			if (n <= 0) return true;
			e.setValue(n);
			return false;
		});

		// Босс-полоска Архиепископа показывается только если он вступил в бой!
		if (getRank() == RANK_CARDINAL) {
			boolean fighting = this.getTarget() != null && this.getTarget().isAlive();
			this.cardinalBossBar.setVisible(fighting);
			if (fighting) {
				this.cardinalBossBar.setPercent(MathHelper.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
				for (ServerPlayerEntity sp : sw.getPlayers()) {
					if (sp.squaredDistanceTo(this) <= 44.0D * 44.0D) {
						this.cardinalBossBar.addPlayer(sp);
					} else {
						this.cardinalBossBar.removePlayer(sp);
					}
				}
			} else {
				this.cardinalBossBar.clearPlayers();
			}
		}

		if (this.age % 10 == 0) {
			checkKbkHoodHeretics(sw);
			acquireChurchTarget(sw);
			maintainFormationOrHome(sw);
		}

		LivingEntity target = this.getTarget();
		if (target != null && (!target.isAlive()
				|| (target instanceof PlayerEntity tp && (tp.isCreative() || tp.isSpectator() || !isPlayerMarkedHeretic(tp))))) {
			this.setTarget(null);
			target = null;
		}

		if (target != null) {
			tickHolyCombat(sw, target);
		} else if (getRank() == RANK_PRIEST && combatCooldown <= 0 && this.age % 30 == 0) {
			healNearbyChurchAllies(sw);
		}
	}

	private void checkKbkHoodHeretics(ServerWorld sw) {
		for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, this.getBoundingBox().expand(18.0D, 8.0D, 18.0D),
				p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
			if (WhiteCapEntity.isWearingClanHood(sp) && !isCommandedBy(sp)) {
				if (speechCooldown <= 0) {
					speechCooldown = 160;
					sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.2F, 0.9F);
					sp.sendMessage(Text.literal("✝ [Орден Люче]: Лазутчик Клана Белых Колпаков в чумной маске!! К ОРУЖИЮ, БРАТЬЯ!!").formatted(Formatting.GOLD, Formatting.BOLD), false);
				}
				markPlayerEnemy(sp, 500);
				alertNearbyChurchAgainst(sp);
			}
		}
	}

	private void acquireChurchTarget(ServerWorld sw) {
		LivingEntity cur = this.getTarget();
		if (cur != null && cur.isAlive()) {
			if (cur instanceof WhiteCapEntity || isUnholyOrRaiderMonster(cur)
					|| (cur instanceof PlayerEntity p && isPlayerMarkedHeretic(p))) {
				return;
			}
		}

		// 1. ГЛАВНЫЙ ПРИОРИТЕТ: Заклятые враги Церкви — «Клан Белых Колпаков» (КБК) в радиусе 44 блоков!
		List<WhiteCapEntity> kbkEnemies = sw.getEntitiesByClass(
				WhiteCapEntity.class,
				this.getBoundingBox().expand(44.0D, 20.0D, 44.0D),
				LivingEntity::isAlive
		);
		if (!kbkEnemies.isEmpty()) {
			kbkEnemies.sort(Comparator.comparingDouble(this::squaredDistanceTo));
			this.setTarget(kbkEnemies.get(0));
			if (speechCooldown <= 0 && sw.random.nextInt(5) == 0) {
				speechCooldown = 160;
				String cry = KBK_WAR_CRIES[sw.random.nextInt(KBK_WAR_CRIES.length)];
				for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, this.getBoundingBox().expand(26.0D), LivingEntity::isAlive)) {
					sp.sendMessage(Text.literal("✝ [Орден Святой Люче] ").formatted(Formatting.GOLD, Formatting.BOLD)
							.append(Text.literal(cry).formatted(Formatting.YELLOW)), true);
				}
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.1F, 1.15F);
			}
			return;
		}

		// 2. Если боец призван Хоругвью игрока — атакует цель командира или его обидчика!
		if (this.bannerCommanderUuid != null) {
			PlayerEntity cmd = sw.getPlayerByUuid(this.bannerCommanderUuid);
			if (cmd != null && cmd.isAlive()) {
				LivingEntity attacker = cmd.getAttacker();
				if (attacker != null && attacker.isAlive() && !(attacker instanceof HolyOrderEntity) && !(attacker instanceof LuceEntity) && attacker != cmd) {
					this.setTarget(attacker);
					return;
				}
				LivingEntity attacking = cmd.getAttacking();
				if (attacking != null && attacking.isAlive() && !(attacking instanceof HolyOrderEntity) && !(attacking instanceof LuceEntity) && attacking != cmd) {
					this.setTarget(attacking);
					return;
				}
			}
		}

		// 3. Еретики-игроки (в колпаке КБК или напавшие на Церковь)
		List<ServerPlayerEntity> heretics = sw.getEntitiesByClass(
				ServerPlayerEntity.class,
				this.getBoundingBox().expand(30.0D, 14.0D, 30.0D),
				p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && !isCommandedBy(p) && isPlayerMarkedHeretic(p)
		);
		if (!heretics.isEmpty()) {
			heretics.sort(Comparator.comparingDouble(this::squaredDistanceTo));
			this.setTarget(heretics.get(0));
			return;
		}

		// 4. Нежить, демоны и разбойники (в радиусе 28 блоков)
		List<LivingEntity> monsters = sw.getEntitiesByClass(
				LivingEntity.class,
				this.getBoundingBox().expand(28.0D, 14.0D, 28.0D),
				HolyOrderEntity::isUnholyOrRaiderMonster
		);
		if (!monsters.isEmpty()) {
			monsters.sort(Comparator.comparingDouble(this::squaredDistanceTo));
			this.setTarget(monsters.get(0));
		}
	}

	private void maintainFormationOrHome(ServerWorld sw) {
		if (this.getTarget() != null) {
			return;
		}
		if (this.bannerCommanderUuid != null && this.followBannerCommander) {
			PlayerEntity cmd = sw.getPlayerByUuid(this.bannerCommanderUuid);
			if (cmd != null && cmd.isAlive()) {
				double dSq = this.squaredDistanceTo(cmd);
				if (dSq > 24.0D * 24.0D) {
					this.refreshPositionAndAngles(cmd.getX() + (sw.random.nextDouble() - 0.5D) * 3.0D, cmd.getY(), cmd.getZ() + (sw.random.nextDouble() - 0.5D) * 3.0D, cmd.getYaw(), 0.0F);
					this.getNavigation().stop();
				} else if (dSq > 4.5D * 4.5D) {
					this.getNavigation().startMovingTo(cmd, 1.15D);
				}
				return;
			}
		}
		if (this.homePos != null && this.getBlockPos().getSquaredDistance(this.homePos) > (double) (this.homeRadius * this.homeRadius)) {
			this.getNavigation().startMovingTo(this.homePos.getX() + 0.5D, this.homePos.getY(), this.homePos.getZ() + 0.5D, 1.05D);
		}
	}

	private void tickHolyCombat(ServerWorld sw, LivingEntity target) {
		this.getLookControl().lookAt(target, 35.0F, 35.0F);
		double distSq = this.squaredDistanceTo(target);
		int rank = getRank();

		if (rank == RANK_INQUISITOR) {
			// Арбалетчик Святой Инквизиции держит дистанцию и бьёт солнечными болтами!
			if (distSq > 16.0D * 16.0D) {
				this.getNavigation().startMovingTo(target, 1.12D);
			} else if (distSq < 6.0D * 6.0D) {
				Vec3d back = this.getPos().subtract(target.getPos()).normalize().multiply(0.22D);
				this.addVelocity(back.x, 0.0D, back.z);
			} else {
				this.getNavigation().stop();
			}

			if (combatCooldown <= 0 && distSq <= 36.0D * 36.0D && this.canSee(target)) {
				combatCooldown = 28;
				this.triggerActionAnim(14);
				fireSunbeamBolt(sw, target);
			}
			return;
		}

		if (rank == RANK_PRIEST) {
			// Священник лечит своих рыцарей в бою И мечет Святую Воду во врагов КБК!
			if (specialCooldown <= 0 && healNearbyChurchAllies(sw)) {
				specialCooldown = 45;
				return;
			}
			if (distSq > 13.0D * 13.0D) {
				this.getNavigation().startMovingTo(target, 1.05D);
			} else {
				this.getNavigation().stop();
			}
			if (combatCooldown <= 0 && distSq <= 22.0D * 22.0D) {
				combatCooldown = 38;
				this.triggerActionAnim(16);
				throwHolyWaterAt(sw, target.getPos().add(0.0D, 0.6D, 0.0D));
			}
			return;
		}

		if (rank == RANK_CARDINAL) {
			// Босс Собора — Архиепископ Ватикана!
			this.getNavigation().startMovingTo(target, 1.16D);

			float hpRatio = this.getHealth() / this.getMaxHealth();
			if (hpRatio <= 0.65F && cardinalSummonStage < 1) {
				cardinalSummonStage = 1;
				summonCathedralGuard(sw);
			} else if (hpRatio <= 0.30F && cardinalSummonStage < 2) {
				cardinalSummonStage = 2;
				summonCathedralGuard(sw);
			}

			if (distSq <= 3.3D * 3.3D && combatCooldown <= 0) {
				combatCooldown = 20;
				this.swingHand(Hand.MAIN_HAND);
				this.triggerActionAnim(12);
				float dmg = (target instanceof WhiteCapEntity) ? 22.0F : 15.0F;
				target.damage(sw.getDamageSources().mobAttack(this), dmg);
				Vec3d kb = target.getPos().subtract(this.getPos()).normalize().multiply(0.65D);
				target.addVelocity(kb.x, 0.28D, kb.z);
				target.velocityModified = true;
			}

			if (specialCooldown <= 0) {
				specialCooldown = 65;
				this.triggerActionAnim(22);
				castCardinalBellJudgment(sw, target);
			}
			return;
		}

		// RANK_CRUSADER: Паладин Ордена Люче!
		this.getNavigation().startMovingTo(target, 1.24D);
		// Святой рывок щитом к стрелкам КБК!
		if (specialCooldown <= 0 && distSq >= 5.0D * 5.0D && distSq <= 15.0D * 15.0D) {
			specialCooldown = 75;
			this.triggerActionAnim(16);
			Vec3d dash = target.getPos().subtract(this.getPos()).normalize().multiply(0.95D);
			this.addVelocity(dash.x, 0.26D, dash.z);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.NEUTRAL, 1.0F, 1.2F);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.5D), this.getZ(), 10, 0.25D, 0.3D, 0.25D, 0.04D);
		}

		if (distSq <= 2.9D * 2.9D && combatCooldown <= 0) {
			combatCooldown = 18;
			this.swingHand(Hand.MAIN_HAND);
			this.triggerActionAnim(10);
			float bonus = (target instanceof WhiteCapEntity || target.getType().isIn(EntityTypeTags.UNDEAD)) ? 16.0F : 10.5F;
			target.damage(sw.getDamageSources().mobAttack(this), bonus);
			if (target instanceof WhiteCapEntity || target.getType().isIn(EntityTypeTags.UNDEAD)) {
				target.setOnFireFor(3);
				sw.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.6D), target.getZ(), 8, 0.25D, 0.3D, 0.25D, 0.04D);
			}
		}
	}

	private void fireSunbeamBolt(ServerWorld sw, LivingEntity target) {
		sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.NEUTRAL, 1.1F, 1.2F);
		Vec3d start = this.getPos().add(0.0D, 1.45D, 0.0D);
		Vec3d end = target.getPos().add(0.0D, target.getHeight() * 0.55D, 0.0D);
		int steps = 14;
		for (int i = 0; i <= steps; i++) {
			double t = (double) i / (double) steps;
			sw.spawnParticles(
					ParticleTypes.END_ROD,
					MathHelper.lerp(t, start.x, end.x),
					MathHelper.lerp(t, start.y, end.y),
					MathHelper.lerp(t, start.z, end.z),
					1, 0.02D, 0.02D, 0.02D, 0.0D
			);
		}
		float dmg = (target instanceof WhiteCapEntity || target.getType().isIn(EntityTypeTags.UNDEAD)) ? 17.0F : 10.0F;
		target.damage(sw.getDamageSources().mobProjectile(this, this), dmg);
		if (target instanceof WhiteCapEntity || target.getType().isIn(EntityTypeTags.UNDEAD)) {
			target.setOnFireFor(4);
		}
	}

	private boolean healNearbyChurchAllies(ServerWorld sw) {
		Box box = this.getBoundingBox().expand(14.0D, 6.0D, 14.0D);
		List<LivingEntity> wounded = sw.getEntitiesByClass(LivingEntity.class, box, e -> {
			if (!e.isAlive() || e.getHealth() >= e.getMaxHealth() * 0.85F) {
				return false;
			}
			if (e instanceof HolyOrderEntity || e instanceof LuceEntity) {
				return true;
			}
			if (e instanceof WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG)) {
				return true;
			}
			if (e instanceof ServerPlayerEntity sp && !isPlayerMarkedHeretic(sp)) {
				return isWearingLuceRegalia(sp) || ModPersistentState.get(sw.getServer()).getAccount(sp.getUuid()).holyOrderRep >= 40;
			}
			return false;
		});
		if (wounded.isEmpty()) {
			return false;
		}
		this.triggerActionAnim(16);
		throwHolyWaterAt(sw, wounded.get(0).getPos().add(0.0D, 0.5D, 0.0D));
		return true;
	}

	private void throwHolyWaterAt(ServerWorld sw, Vec3d dest) {
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.NEUTRAL, 0.9F, 1.1F);
		HolyWaterEntity flask = new HolyWaterEntity(sw, this);
		flask.setItem(new ItemStack(ModItems.HOLY_WATER));
		flask.setPosition(this.getX(), this.getEyeY() - 0.1D, this.getZ());
		Vec3d delta = dest.subtract(flask.getPos());
		double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		flask.setVelocity(delta.x, delta.y + horiz * 0.18D, delta.z, 1.05F, 1.5F);
		sw.spawnEntity(flask);
	}

	private void castCardinalBellJudgment(ServerWorld sw, LivingEntity target) {
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 1.5F, 0.85F);
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 1.2F, 1.1F);

		Vec3d center = target.getPos();
		for (double y = 0.0D; y <= 12.0D; y += 0.7D) {
			sw.spawnParticles(ParticleTypes.END_ROD, center.x, center.y + y, center.z, 4, 0.3D, 0.15D, 0.3D, 0.02D);
		}
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(center.x, center.y, center.z);
			bolt.setCosmetic(true);
			sw.spawnEntity(bolt);
		}
		target.damage(sw.getDamageSources().magic(), (target instanceof WhiteCapEntity) ? 26.0F : 13.0F);
		this.heal(8.0F);
	}

	private void summonCathedralGuard(ServerWorld sw) {
		sw.playSound(null, this.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 1.3F, 1.25F);
		for (int i = -1; i <= 1; i += 2) {
			HolyOrderEntity guard = ModEntities.HOLY_ORDER_ENTITY.create(sw);
			if (guard != null) {
				guard.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
				guard.refreshPositionAndAngles(this.getX() + i * 2.2D, this.getY(), this.getZ() + 1.5D, this.getYaw(), 0.0F);
				guard.setRank(i < 0 ? RANK_CRUSADER : RANK_INQUISITOR);
				guard.setHomeAnchor(this.getBlockPos(), 24);
				if (this.getTarget() != null) {
					guard.setTarget(this.getTarget());
				}
				sw.spawnEntity(guard);
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Паладин со щитом частично блокирует арбалетные стрелы и колбы КБК!
		if (getRank() == RANK_CRUSADER && source.getAttacker() instanceof WhiteCapEntity) {
			amount *= 0.65F;
			if (this.getWorld() instanceof ServerWorld sw) {
				sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.NEUTRAL, 0.75F, 1.15F);
			}
		}
		boolean hurt = super.damage(source, amount);
		if (!hurt || !(this.getWorld() instanceof ServerWorld sw)) {
			return hurt;
		}
		if (source.getAttacker() instanceof LivingEntity attacker && !(attacker instanceof HolyOrderEntity) && !(attacker instanceof LuceEntity)) {
			if (attacker instanceof ServerPlayerEntity sp && !sp.isCreative() && !isCommandedBy(sp)) {
				ModPersistentState state = ModPersistentState.get(sw.getServer());
				ModPersistentState.PlayerFinanceData acc = state.getAccount(sp.getUuid());
				acc.holyOrderRep = Math.max(-500, acc.holyOrderRep - 15);
				state.markDirty();
				markPlayerEnemy(sp, 600);
				alertNearbyChurchAgainst(sp);
			} else if (!(attacker instanceof PlayerEntity)) {
				this.setTarget(attacker);
				alertNearbyChurchAgainst(attacker);
			}
		}
		return true;
	}

	@Override
	public void onDeath(DamageSource damageSource) {
		this.cardinalBossBar.clearPlayers();
		super.onDeath(damageSource);
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		if (damageSource.getAttacker() instanceof ServerPlayerEntity sp && !sp.isCreative()) {
			ModPersistentState state = ModPersistentState.get(sw.getServer());
			ModPersistentState.PlayerFinanceData acc = state.getAccount(sp.getUuid());
			int penalty = getRank() == RANK_CARDINAL ? 100 : 30;
			acc.holyOrderRep = Math.max(-500, acc.holyOrderRep - penalty);
			state.markDirty();
			sp.sendMessage(Text.literal("✝ [Гнев Церкви]: Вы убили служителя Ордена Люче! (-" + penalty + " Репутации Церкви -> " + acc.holyOrderRep + ")").formatted(Formatting.RED, Formatting.BOLD), true);
		}
		if (getRank() == RANK_CARDINAL) {
			this.dropStack(new ItemStack(ModItems.CRUSADE_BANNER, 1));
			this.dropStack(new ItemStack(ModItems.HOLY_WATER, 4));
			this.dropStack(new ItemStack(ModItems.REVIVAL_TOKEN, 1));
		} else if (sw.random.nextInt(3) == 0) {
			this.dropStack(new ItemStack(ModItems.HOLY_WATER, 1 + sw.random.nextInt(2)));
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("OrderRank", getRank());
		nbt.putInt("HomeRadius", this.homeRadius);
		nbt.putInt("TempSummonTicks", this.temporarySummonTicks);
		nbt.putBoolean("FollowCommander", this.followBannerCommander);
		nbt.putInt("CardinalStage", this.cardinalSummonStage);
		if (this.homePos != null) {
			nbt.putLong("HomePos", this.homePos.asLong());
		}
		if (this.bannerCommanderUuid != null) {
			nbt.putUuid("CommanderUuid", this.bannerCommanderUuid);
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("OrderRank")) {
			setRank(nbt.getInt("OrderRank"));
		}
		if (nbt.contains("HomeRadius")) {
			this.homeRadius = nbt.getInt("HomeRadius");
		}
		if (nbt.contains("TempSummonTicks")) {
			this.temporarySummonTicks = nbt.getInt("TempSummonTicks");
		}
		if (nbt.contains("FollowCommander")) {
			this.followBannerCommander = nbt.getBoolean("FollowCommander");
		}
		this.cardinalSummonStage = nbt.getInt("CardinalStage");
		if (nbt.contains("HomePos")) {
			this.homePos = BlockPos.fromLong(nbt.getLong("HomePos"));
		}
		if (nbt.containsUuid("CommanderUuid")) {
			this.bannerCommanderUuid = nbt.getUuid("CommanderUuid");
		}
	}
}
