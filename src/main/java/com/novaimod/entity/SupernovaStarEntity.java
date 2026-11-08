package com.novaimod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 3D-феномен «Рождение Сверхновой» (Supernova Genesis):
 * - Тики 0..65: Красный Гипергигант раздувается в небе под учащающееся сердцебиение звезды.
 * - Тики 65..92: Гравитационный коллапс — гигантская звезда бесшумно схлопывается в ослепительную нейтронную точку!
 * - Тики 92..260: ВЗРЫВ СВЕРХНОВОЙ! Тройные наклонные 3D-ударные кольца разлетаются на 44 блока,
 *   испепеляя враждебных монстров, а в небе расцветает живая вращающаяся 3D-Туманность с пульсаром в центре!
 */
public class SupernovaStarEntity extends Entity {
	public static final int SWELL_END_TICK = 65;
	public static final int COLLAPSE_END_TICK = 92;
	public static final int TOTAL_LIFE_TICKS = 260;

	private static final TrackedData<Integer> SEED_OFFSET = DataTracker.registerData(
			SupernovaStarEntity.class, TrackedDataHandlerRegistry.INTEGER
	);

	public SupernovaStarEntity(EntityType<? extends SupernovaStarEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(SEED_OFFSET, 0);
	}

	public void setSeedOffset(int seed) {
		this.dataTracker.set(SEED_OFFSET, seed);
	}

	public int getSeedOffset() {
		return this.dataTracker.get(SEED_OFFSET);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.age < SWELL_END_TICK) {
			// Плавный подъём звезды и нарастающий пульс ядра
			if (this.age < 25) {
				this.setPosition(this.getX(), this.getY() + 0.22D, this.getZ());
			}
			int beatPeriod = Math.max(4, 16 - this.age / 5);
			if (this.age % beatPeriod == 0) {
				float pitch = 0.55F + (this.age / (float) SWELL_END_TICK) * 0.85F;
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 2.0F, pitch);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.MASTER, 0.9F, pitch);
			}
			sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 8, 1.8D, 1.8D, 1.8D, 0.04D);
		} else if (this.age == SWELL_END_TICK) {
			// Начало гравитационного схлопывания в точку!
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 2.2F, 1.75F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.MASTER, 1.8F, 0.85F);
		} else if (this.age < COLLAPSE_END_TICK) {
			// Гравитационное притяжение враждебных мобов к точке коллапса
			Box pullBox = this.getBoundingBox().expand(28.0D);
			for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, pullBox, e -> e instanceof Monster && e.isAlive())) {
				Vec3d pull = this.getPos().subtract(mob.getPos()).normalize().multiply(0.32D);
				mob.addVelocity(pull.x, pull.y * 0.5D + 0.05D, pull.z);
				mob.velocityModified = true;
			}
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 10, 3.2D, 3.2D, 3.2D, -0.25D);
		} else if (this.age == COLLAPSE_END_TICK) {
			// ВЗРЫВ СВЕРХНОВОЙ!
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 2.5F, 0.65F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 2.5F, 0.55F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 1.6F, 1.25F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.4F, 0.75F);

			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 6, 1.5D, 1.5D, 1.5D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 160, 2.0D, 2.0D, 2.0D, 0.65D);
			sw.spawnParticles(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), 120, 2.5D, 2.5D, 2.5D, 0.55D);

			Box blastBox = this.getBoundingBox().expand(36.0D);
			for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, blastBox, e -> e.isAlive() && !(e instanceof PlayerEntity))) {
				if (target instanceof Monster || target instanceof WhiteCapEntity) {
					Vec3d push = target.getPos().subtract(this.getPos()).normalize().multiply(2.2D);
					target.damage(sw.getDamageSources().magic(), 180.0F);
					target.addVelocity(push.x, 0.85D, push.z);
					target.setOnFireFor(10);
				}
			}
		} else if (this.age < TOTAL_LIFE_TICKS) {
			// Мерцающая звёздная пыль рождённой Туманности
			if (this.age % 3 == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 6, 9.0D, 6.0D, 9.0D, 0.02D);
			}
			if (this.age % 28 == 0) {
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.2F, 1.15F);
			}
		} else {
			this.discard();
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 512.0D * 512.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		setSeedOffset(nbt.getInt("Seed"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Seed", getSeedOffset());
	}
}
