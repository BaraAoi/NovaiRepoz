package com.novaimod.entity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Женская кукла высотой ровно в 1 блок («Танцующая кукла»):
 * - По нажатию ПКМ включается / выключается.
 * - Пока кукла активна:
 *   1) Все мобы и NPC в радиусе 20 блоков становятся полностью дружелюбными, собираются вокруг куклы и танцуют!
 *   2) Игроки и мобы в зоне куклы не наносят друг другу урон (мир и дружба!).
 *   3) Все сущности вокруг (и мобы, и игроки, и сама кукла) визуально крутятся и плющатся вниз-вверх
 *      как в меме «Приора / Ярис» (сжался -> вернулся в нормальный размер), при этом игроки спокойно ходят
 *      и на их камеру это никак не влияет!
 */
public class PartyDollEntity extends Entity {
	public static final double AURA_RADIUS = 20.0D;
	public static final double AURA_RADIUS_SQ = AURA_RADIUS * AURA_RADIUS;

	private static final TrackedData<Boolean> ACTIVE = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> DOLL_YAW = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private static final Set<PartyDollEntity> LOADED_ACTIVE_DOLLS =
			Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private static final float[] MEME_MELODY = new float[]{
			0.89F, 1.06F, 1.19F, 1.33F, 1.19F, 1.06F, 1.49F, 1.33F
	};

	public PartyDollEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(ACTIVE, false);
		builder.add(DOLL_YAW, 0.0F);
	}

	public boolean isActive() {
		return this.dataTracker.get(ACTIVE);
	}

	public void setActive(boolean active) {
		this.dataTracker.set(ACTIVE, active);
		if (active) {
			LOADED_ACTIVE_DOLLS.add(this);
		} else {
			LOADED_ACTIVE_DOLLS.remove(this);
		}
	}

	public float getDollYaw() {
		return this.dataTracker.get(DOLL_YAW);
	}

	public void setDollYaw(float yaw) {
		this.dataTracker.set(DOLL_YAW, yaw);
	}

	/**
	 * Быстрая O(1) проверка (и на сервере, и на клиенте): находится ли сущность в зоне активной куклы.
	 */
	public static boolean isUnderActiveDollAura(Entity entity) {
		if (entity == null || entity.getWorld() == null) {
			return false;
		}
		synchronized (LOADED_ACTIVE_DOLLS) {
			for (PartyDollEntity doll : LOADED_ACTIVE_DOLLS) {
				if (doll != null
						&& !doll.isRemoved()
						&& doll.isActive()
						&& doll.getWorld() == entity.getWorld()
						&& entity.squaredDistanceTo(doll) <= AURA_RADIUS_SQ) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (this.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}

		boolean nextState = !this.isActive();
		this.setActive(nextState);

		if (nextState) {
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.RECORDS, 1.0F, 1.25F);
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.RECORDS, 0.9F, 1.5F);
			if (this.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.05D, this.getZ(), 8, 0.45D, 0.25D, 0.45D, 0.02D);
				sw.spawnParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.15D, this.getZ(), 10, 0.55D, 0.3D, 0.55D, 0.35D);
			}
			player.sendMessage(
					Text.literal("♫ Кукла активна! Все вокруг подружились, собрались и плющатся в танце! (ПКМ — выключить)")
							.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
					true
			);
		} else {
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.RECORDS, 0.85F, 0.75F);
			player.sendMessage(
					Text.literal("Кукла остановлена — все вернулись в обычный режим.").formatted(Formatting.YELLOW),
					true
			);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.isActive() && !this.isRemoved()) {
			LOADED_ACTIVE_DOLLS.add(this);
		} else {
			LOADED_ACTIVE_DOLLS.remove(this);
		}

		// Мягкое приземление, если блок под куклой сломали
		if (!this.isOnGround()) {
			this.setVelocity(this.getVelocity().add(0.0D, -0.06D, 0.0D));
		} else {
			this.setVelocity(Vec3d.ZERO);
		}
		if (this.getVelocity().lengthSquared() > 1.0E-5D) {
			this.move(MovementType.SELF, this.getVelocity());
		}

		if (this.getWorld().isClient() || !this.isActive()) {
			return;
		}

		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		// Ритмичная мелодия и партиклы сердечек/нот над куклой
		if (this.age % 6 == 0) {
			int step = (this.age / 6) % MEME_MELODY.length;
			float pitch = MEME_MELODY[step];
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.RECORDS, 0.85F, pitch);
			if (step % 2 == 0) {
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), SoundCategory.RECORDS, 0.75F, 1.0F);
			}
			sw.spawnParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.15D, this.getZ(), 2, 0.35D, 0.15D, 0.35D, 0.3D);
		}
		if (this.age % 18 == 0) {
			sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.05D, this.getZ(), 2, 0.4D, 0.2D, 0.4D, 0.01D);
		}

		// Каждые 2 тика делаем всех мобов вокруг дружелюбными, а каждые 10 тиков собираем их в хоровод вокруг куклы!
		if (this.age % 2 == 0) {
			Box auraBox = Box.of(this.getPos(), AURA_RADIUS * 2.0D, 14.0D, AURA_RADIUS * 2.0D);
			boolean updatePaths = (this.age % 10 == 0);

			for (MobEntity mob : sw.getEntitiesByClass(MobEntity.class, auraBox, m -> m.isAlive() && m.squaredDistanceTo(this) <= AURA_RADIUS_SQ)) {
				// 1) Полное миролюбие мобов пока кукла активна
				if (mob.getTarget() != null) {
					mob.setTarget(null);
				}
				mob.setAttacking(false);
				if (mob instanceof CreeperEntity creeper) {
					creeper.setFuseSpeed(-1);
				}
				if (mob instanceof Angerable angerable) {
					angerable.stopAnger();
				}

				// 2) Все мобы и NPC собираются в круг вокруг куклы и пританцовывают!
				mob.getLookControl().lookAt(this, 30.0F, 30.0F);
				if (updatePaths) {
					double angle = (mob.getId() * 2.399963D) + (this.age * 0.02D);
					double ringRadius = 2.4D + (Math.abs(mob.getId()) % 3) * 0.85D;
					double targetX = this.getX() + Math.cos(angle) * ringRadius;
					double targetZ = this.getZ() + Math.sin(angle) * ringRadius;
					double distToRingSq = mob.squaredDistanceTo(targetX, mob.getY(), targetZ);
					if (distToRingSq > 1.35D * 1.35D) {
						mob.getNavigation().startMovingTo(targetX, this.getY(), targetZ, 1.15D);
					}
				}

				// Лёгкие весёлые подпрыгивания мобов в такт вблизи куклы
				if ((this.age + mob.getId() * 3) % 16 == 0 && mob.isOnGround() && mob.squaredDistanceTo(this) <= 8.0D * 8.0D) {
					mob.addVelocity(0.0D, 0.20D, 0.0D);
				}
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		LOADED_ACTIVE_DOLLS.remove(this);
		super.remove(reason);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source) || this.getWorld().isClient() || this.isRemoved()) {
			return false;
		}
		LOADED_ACTIVE_DOLLS.remove(this);
		this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.BLOCKS, 0.9F, 1.1F);
		boolean drop = !(source.getAttacker() instanceof PlayerEntity p && p.isInCreativeMode());
		if (drop) {
			this.dropStack(new ItemStack(ModItems.PARTY_DOLL));
		}
		this.discard();
		return true;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(ModItems.PARTY_DOLL);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Active")) {
			this.setActive(nbt.getBoolean("Active"));
		}
		if (nbt.contains("DollYaw")) {
			this.setDollYaw(nbt.getFloat("DollYaw"));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("Active", this.isActive());
		nbt.putFloat("DollYaw", this.getDollYaw());
	}

	public static PartyDollEntity placeDoll(World world, BlockPos supportPos, float yaw) {
		BlockState below = world.getBlockState(supportPos);
		double y = supportPos.getY() + (below.isOf(Blocks.AIR) ? 0.0D : 1.0D);
		PartyDollEntity doll = ModEntities.PARTY_DOLL_ENTITY.create(world);
		if (doll == null) {
			return null;
		}
		doll.refreshPositionAndAngles(supportPos.getX() + 0.5D, y, supportPos.getZ() + 0.5D, yaw, 0.0F);
		doll.setDollYaw(yaw);
		world.spawnEntity(doll);
		return doll;
	}
}
