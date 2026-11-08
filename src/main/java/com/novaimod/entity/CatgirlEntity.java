package com.novaimod.entity;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.PhantomEntity;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Кошкодевочка («Проект НЕКО-09») — жертва жестокого эксперимента учёных в заброшенных подземных лабораториях:
 * - Полностью в своём разуме: помнит опыты, просит игрока открыть клетку в глубине базы и забрать её к себе домой!
 * - После спасения переодевается из больничной робы в уютный домашний наряд с золотым колокольчиком.
 * - Режимы (ПКМ пустой рукой):
 *   1) Идти за игроком (легко довести домой или перевезти в Покеболе!),
 *   2) Жить дома (привязывается к вашему дому, гуляет по комнатам, создаёт ауру уюта Регенерации, отпугивает криперов и фантомов, дарит подарки!),
 *   3) Сидеть и ждать (аккуратно садится, помахивая хвостиком).
 */
public class CatgirlEntity extends PathAwareEntity {
	public static final int MODE_FOLLOW = 0;
	public static final int MODE_HOME = 1;
	public static final int MODE_SIT = 2;

	private static final TrackedData<Integer> VARIANT = DataTracker.registerData(CatgirlEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> RESCUED = DataTracker.registerData(CatgirlEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Integer> BEHAVIOR_MODE = DataTracker.registerData(CatgirlEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final String[] VARIANT_NAMES = new String[]{"Широ", "Куро", "Мико", "Юки"};

	private static final String[] CAGE_PLEA_LINES = new String[]{
			"Я полностью в своём разуме, я всё понимаю! Пожалуйста, открой калитку и забери меня к себе домой!",
			"Бюрократы посадили меня в этот изолятор, потому что у меня нет Справки №38-МЯУ на пушистые ушки и хвост!",
			"Здесь вечные очереди, пыль и бумаги... Пожалуйста, выведи меня через Приёмную в настоящий живой мир!",
			"Обещаю, я не доставлю хлопот! Просто забери меня отсюда к себе домой..."
	};

	private static final String[] HOME_HAPPY_LINES = new String[]{
			"У тебя дома так тепло и уютно... Даже не верится, что та жуткая лаборатория навсегда позади! Мяу~",
			"Я приглядываю за домом — ни один крипер и фантом даже близко не подойдёт к нашим окнам!",
			"Мне так нравится здесь жить! Спасибо, что подарил мне настоящий дом~",
			"Если захочешь взять меня с собой в далёкое путешествие — можешь позвать за собой или посадить в Покебол!"
	};

	private static final String[] FOOD_THANKS_LINES = new String[]{
			"Ммм, как вкусно!! Спасибо большое, ты самый добрый! Мурр~",
			"В лаборатории нас кормили только безвкусными смесями... А это просто объедение! Мяу~",
			"Спасибо за угощение!! У меня сразу поднялось настроение~"
	};

	private UUID ownerUuid = null;
	private BlockPos homePos = null;
	private int speechCooldown = 0;
	private int giftCooldown = 2400;

	public CatgirlEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
		super(entityType, world);
	}

	public static DefaultAttributeContainer.Builder createCatgirlAttributes() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0D)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.31D)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0D)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28.0D);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, 0);
		builder.add(RESCUED, false);
		builder.add(BEHAVIOR_MODE, MODE_FOLLOW);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
		this.goalSelector.add(7, new LookAroundGoal(this));
	}

	public int getVariantId() {
		return this.dataTracker.get(VARIANT);
	}

	public void setVariantId(int variant) {
		int v = MathHelper.clamp(variant, 0, 3);
		this.dataTracker.set(VARIANT, v);
		updateDisplayTitle();
	}

	public boolean isRescued() {
		return this.dataTracker.get(RESCUED);
	}

	public void setRescued(boolean rescued) {
		this.dataTracker.set(RESCUED, rescued);
		updateDisplayTitle();
	}

	public int getBehaviorMode() {
		return this.dataTracker.get(BEHAVIOR_MODE);
	}

	public void setBehaviorMode(int mode) {
		this.dataTracker.set(BEHAVIOR_MODE, MathHelper.clamp(mode, 0, 2));
		if (mode == MODE_SIT) {
			this.getNavigation().stop();
		}
	}

	public boolean isSittingPose() {
		return isRescued() && getBehaviorMode() == MODE_SIT;
	}

	public void setOwnerUuid(UUID uuid) {
		this.ownerUuid = uuid;
	}

	public boolean isOwnedBy(PlayerEntity player) {
		return player != null && this.ownerUuid != null && this.ownerUuid.equals(player.getUuid());
	}

	public void configureVariant(int variant, boolean rescued) {
		setVariantId(variant);
		setRescued(rescued);
	}

	public void setHomePos(BlockPos pos) {
		this.homePos = pos;
	}

	private void updateDisplayTitle() {
		String name = VARIANT_NAMES[getVariantId()];
		if (!isRescued()) {
			this.setCustomName(Text.literal("Подопытная №09 «" + name + "»").formatted(Formatting.AQUA));
		} else {
			this.setCustomName(Text.literal("Кошкодевочка " + name).formatted(Formatting.LIGHT_PURPLE));
		}
	}

	@Override
	public boolean canImmediatelyDespawn(double distanceSquared) {
		return false;
	}

	private static boolean isFavoriteFood(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		return stack.isOf(Items.COOKED_COD)
				|| stack.isOf(Items.COOKED_SALMON)
				|| stack.isOf(Items.COD)
				|| stack.isOf(Items.SALMON)
				|| stack.isOf(Items.TROPICAL_FISH)
				|| stack.isOf(Items.MILK_BUCKET)
				|| stack.isOf(Items.COOKIE)
				|| stack.isOf(Items.SWEET_BERRIES)
				|| stack.isOf(Items.GLOW_BERRIES)
				|| stack.isOf(Items.GOLDEN_APPLE)
				|| stack.isOf(Items.BREAD)
				|| stack.isOf(Items.COOKED_CHICKEN);
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		ItemStack held = player.getStackInHand(hand);
		// Не перехватываем бросок Покебола!
		if (held.getItem().getTranslationKey().contains("pokeball")) {
			return ActionResult.PASS;
		}

		if (this.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(this.getWorld() instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
			return ActionResult.SUCCESS;
		}

		String name = VARIANT_NAMES[getVariantId()];

		// 1. Первое спасение из клетки учёных!
		if (!isRescued()) {
			setRescued(true);
			this.ownerUuid = sp.getUuid();
			setBehaviorMode(MODE_FOLLOW);
			this.setHealth(this.getMaxHealth());
			this.setPersistent();

			sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getBodyY(0.85D), this.getZ(), 14, 0.4D, 0.4D, 0.4D, 0.03D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.1F, 1.15F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.7F, 1.4F);

			sp.sendMessage(
					Text.literal("[" + name + "] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
							.append(Text.literal("Спасибо, что вытащил меня из этой жуткой клетки!! Веди меня к себе домой — я иду за тобой! (ПКМ — сменить режим / прописать дома)").formatted(Formatting.WHITE)),
					false
			);
			return ActionResult.SUCCESS;
		}

		// 2. Угощение вкусной едой!
		if (isFavoriteFood(held)) {
			this.heal(12.0F);
			sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getBodyY(0.85D), this.getZ(), 10, 0.35D, 0.35D, 0.35D, 0.02D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 1.2F, 1.1F);

			if (!sp.getAbilities().creativeMode) {
				if (held.isOf(Items.MILK_BUCKET)) {
					sp.setStackInHand(hand, new ItemStack(Items.BUCKET));
				} else {
					held.decrement(1);
				}
			}

			String thanks = FOOD_THANKS_LINES[sw.random.nextInt(FOOD_THANKS_LINES.length)];
			sp.sendMessage(
					Text.literal("[" + name + "] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
							.append(Text.literal(thanks).formatted(Formatting.GREEN)),
					false
			);
			return ActionResult.SUCCESS;
		}

		// 3. Переключение поведения (Следовать -> Жить дома -> Сидеть)
		if (this.ownerUuid == null) {
			this.ownerUuid = sp.getUuid();
		}

		int nextMode = (getBehaviorMode() + 1) % 3;
		setBehaviorMode(nextMode);
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_CAT_AMBIENT, SoundCategory.NEUTRAL, 0.9F, 1.2F);

		if (nextMode == MODE_FOLLOW) {
			sp.sendMessage(
					Text.literal("[" + name + "] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
							.append(Text.literal("Хорошо! Я побегу следом за тобой! Мяу~").formatted(Formatting.AQUA)),
					false
			);
		} else if (nextMode == MODE_HOME) {
			this.homePos = this.getBlockPos();
			sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getBodyY(0.85D), this.getZ(), 8, 0.35D, 0.35D, 0.35D, 0.02D);
			sp.sendMessage(
					Text.literal("[" + name + "] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
							.append(Text.literal("Ура, теперь это мой новый уютный Дом! Буду жить здесь и ждать тебя~").formatted(Formatting.GOLD)),
					false
			);
		} else {
			sp.sendMessage(
					Text.literal("[" + name + "] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
							.append(Text.literal("Присяду здесь и подожду тебя!").formatted(Formatting.YELLOW)),
					false
			);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (speechCooldown > 0) {
			speechCooldown--;
		}
		if (giftCooldown > 0) {
			giftCooldown--;
		}

		// Пока заперта в клетке лаборатории — просит помощи у подошедшего игрока!
		if (!isRescued()) {
			if (speechCooldown <= 0 && this.age % 25 == 0) {
				List<ServerPlayerEntity> nearby = sw.getEntitiesByClass(
						ServerPlayerEntity.class,
						this.getBoundingBox().expand(9.5D, 5.0D, 9.5D),
						p -> p.isAlive() && !p.isSpectator()
				);
				if (!nearby.isEmpty()) {
					ServerPlayerEntity p = nearby.get(0);
					this.getLookControl().lookAt(p, 35.0F, 35.0F);
					sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_CAT_BEG_FOR_FOOD, SoundCategory.NEUTRAL, 0.85F, 1.2F);
					speechCooldown = 140;
				}
			}
			return;
		}

		// Отпугиваем криперов и фантомов вокруг себя и дома (как настоящая кошкодевочка!)
		if (this.age % 10 == 0) {
			for (CreeperEntity creeper : sw.getEntitiesByClass(CreeperEntity.class, this.getBoundingBox().expand(13.0D), LivingEntity::isAlive)) {
				creeper.setTarget(null);
				creeper.setFuseSpeed(-1);
				Vec3d flee = creeper.getPos().subtract(this.getPos()).normalize().multiply(0.42D);
				creeper.addVelocity(flee.x, 0.05D, flee.z);
			}
			for (PhantomEntity phantom : sw.getEntitiesByClass(PhantomEntity.class, this.getBoundingBox().expand(16.0D), LivingEntity::isAlive)) {
				Vec3d flee = phantom.getPos().subtract(this.getPos()).normalize().multiply(0.55D);
				phantom.addVelocity(flee.x, 0.25D, flee.z);
			}
		}

		int mode = getBehaviorMode();
		if (mode == MODE_SIT) {
			this.getNavigation().stop();
			return;
		}

		if (mode == MODE_FOLLOW && ownerUuid != null) {
			PlayerEntity owner = sw.getPlayerByUuid(ownerUuid);
			if (owner != null && owner.isAlive() && !owner.isSpectator()) {
				double distSq = this.squaredDistanceTo(owner);
				if (distSq > 324.0D) {
					// Безопасный перенос поближе к хозяину, если отстала на лестнице бункера или в лесу!
					teleportNearOwner(sw, owner);
				} else if (distSq > 10.5D) {
					this.getNavigation().startMovingTo(owner, 1.22D);
				} else if (distSq < 4.5D) {
					this.getNavigation().stop();
				}
			}
			return;
		}

		if (mode == MODE_HOME) {
			if (homePos == null) {
				homePos = this.getBlockPos();
			}
			// Гуляет по дому и дарит хозяину уютную ауру Регенерации и маленькие подарки!
			if (this.age % 40 == 0) {
				double distHomeSq = this.getBlockPos().getSquaredDistance(homePos);
				if (distHomeSq > 100.0D) {
					this.getNavigation().startMovingTo(homePos.getX() + 0.5D, homePos.getY(), homePos.getZ() + 0.5D, 1.05D);
				} else if (sw.random.nextInt(3) == 0) {
					int rx = homePos.getX() + sw.random.nextInt(9) - 4;
					int rz = homePos.getZ() + sw.random.nextInt(9) - 4;
					this.getNavigation().startMovingTo(rx + 0.5D, homePos.getY(), rz + 0.5D, 0.85D);
				}

				if (ownerUuid != null) {
					PlayerEntity owner = sw.getPlayerByUuid(ownerUuid);
					if (owner instanceof ServerPlayerEntity sp && this.squaredDistanceTo(sp) <= 196.0D) {
						sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 120, 0, true, false, true));
						if (speechCooldown <= 0 && sw.random.nextInt(8) == 0) {
							sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 0.9F, 1.15F);
							speechCooldown = 360;
						}
						if (giftCooldown <= 0 && this.squaredDistanceTo(sp) <= 25.0D) {
							giftCooldown = 4800;
							ItemStack[] gifts = new ItemStack[]{
									new ItemStack(Items.COOKED_SALMON, 2),
									new ItemStack(Items.AMETHYST_SHARD, 2),
									new ItemStack(Items.SWEET_BERRIES, 4),
									new ItemStack(Items.PHANTOM_MEMBRANE, 1),
									new ItemStack(Items.COOKIE, 3)
							};
							ItemStack gift = gifts[sw.random.nextInt(gifts.length)];
							ItemEntity drop = new ItemEntity(sw, this.getX(), this.getY() + 0.6D, this.getZ(), gift);
							sw.spawnEntity(drop);
							sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getBodyY(0.85D), this.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.02D);
							sp.sendMessage(
									Text.literal("[" + VARIANT_NAMES[getVariantId()] + "] ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
											.append(Text.literal("Смотри, я нашла кое-что полезное для тебя! Держи, хозяин~ Мяу!").formatted(Formatting.GOLD)),
									false
							);
						}
					}
				}
			}
		}
	}

	private void teleportNearOwner(ServerWorld sw, PlayerEntity owner) {
		BlockPos base = owner.getBlockPos();
		for (int i = 0; i < 10; i++) {
			int dx = sw.random.nextInt(5) - 2;
			int dz = sw.random.nextInt(5) - 2;
			if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
			for (int dy = -2; dy <= 2; dy++) {
				BlockPos p = base.add(dx, dy, dz);
				if (sw.getBlockState(p.down()).isSolidBlock(sw, p.down())
						&& sw.getBlockState(p).isAir()
						&& sw.getBlockState(p.up()).isAir()) {
					this.refreshPositionAndAngles(p.getX() + 0.5D, p.getY(), p.getZ() + 0.5D, this.getYaw(), 0.0F);
					this.getNavigation().stop();
					return;
				}
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		// Хозяин случайно задел рукой — не обижаемся и почти не получаем урона!
		if (isRescued() && ownerUuid != null && source.getAttacker() instanceof PlayerEntity p && ownerUuid.equals(p.getUuid())) {
			if (!p.isSneaking()) {
				return false;
			}
		}
		return super.damage(source, amount);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("CatVariant", getVariantId());
		nbt.putBoolean("Rescued", isRescued());
		nbt.putInt("BehaviorMode", getBehaviorMode());
		if (ownerUuid != null) {
			nbt.putUuid("OwnerUuid", ownerUuid);
		}
		if (homePos != null) {
			nbt.putLong("HomePos", homePos.asLong());
		}
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.contains("CatVariant")) {
			setVariantId(nbt.getInt("CatVariant"));
		}
		if (nbt.contains("Rescued")) {
			setRescued(nbt.getBoolean("Rescued"));
		}
		if (nbt.contains("BehaviorMode")) {
			setBehaviorMode(nbt.getInt("BehaviorMode"));
		}
		if (nbt.containsUuid("OwnerUuid")) {
			this.ownerUuid = nbt.getUuid("OwnerUuid");
		}
		if (nbt.contains("HomePos")) {
			this.homePos = BlockPos.fromLong(nbt.getLong("HomePos"));
		}
	}
}
