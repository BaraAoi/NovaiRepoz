package com.novaimod.entity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Креативная сущность «Сфера Сингулярности» (объёмная Чёрная дыра без ограничений):
 * затягивает в гравитационную воронку всех мобов, боссов, снаряды и предметы в радиусе 40 блоков
 * и схлопывается мощнейшим взрывом сверхновой!
 */
public class SingularityOrbEntity extends ThrownItemEntity {
	private static final double PULL_RADIUS = 40.0D;
	private static final int TOTAL_LIFETIME = 140;

	@Nullable
	private UUID casterUuid;
	private boolean breakBlocks = true;

	public SingularityOrbEntity(EntityType<? extends SingularityOrbEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	public SingularityOrbEntity(World world, ServerPlayerEntity caster, boolean breakBlocks) {
		super(ModEntities.SINGULARITY_ORB_ENTITY, caster, world);
		this.casterUuid = caster.getUuid();
		this.breakBlocks = breakBlocks;
		this.setNoGravity(true);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0D * 256.0D;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SINGULARITY_ORB;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		this.setVelocity(this.getVelocity().multiply(0.15D));
	}

	@Override
	public void tick() {
		this.noClip = true;
		super.tick();

		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.age > 20) {
			this.setVelocity(this.getVelocity().multiply(0.75D));
			this.velocityModified = true;
		}

		Vec3d center = this.getPos().add(0.0D, 0.5D, 0.0D);

		sw.spawnParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 8, 0.4D, 0.4D, 0.4D, 0.02D);
		for (int i = 0; i < 8; i++) {
			double a = this.age * 0.4D + i * (Math.PI / 4.0D);
			double r = 2.4D + (i % 3) * 1.4D;
			double px = center.x + Math.cos(a) * r;
			double py = center.y + Math.sin(a * 2.0D) * 0.4D;
			double pz = center.z + Math.sin(a) * r;
			sw.spawnParticles(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.FLAME, px, py, pz, 2, 0.08D, 0.08D, 0.08D, 0.02D);
		}

		if (this.age % 14 == 1) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.6F, 0.5F);
		}

		Box box = Box.of(center, PULL_RADIUS * 2.0D, PULL_RADIUS * 2.0D, PULL_RADIUS * 2.0D);
		List<Entity> targets = sw.getOtherEntities(this, box, e ->
				e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof SingularityOrbEntity)
						&& !(e instanceof InfinityEyeEntity)
						&& !(e instanceof CelestialOrbEntity)
						&& (e instanceof MobEntity || e instanceof ItemEntity || e instanceof ProjectileEntity)
		);

		for (Entity e : targets) {
			Vec3d toCenter = center.subtract(e.getPos().add(0.0D, e.getHeight() * 0.4D, 0.0D));
			double dist = toCenter.length();
			if (dist > PULL_RADIUS || dist < 1.0E-3D) {
				continue;
			}
			Vec3d radial = toCenter.normalize();
			Vec3d tangent = new Vec3d(-radial.z, 0.0D, radial.x);
			double pullStrength = 0.25D + (1.0D - dist / PULL_RADIUS) * 0.72D;
			Vec3d desiredVel = radial.multiply(pullStrength).add(tangent.multiply(0.24D));

			if (e instanceof EnderDragonEntity dragon) {
				dragon.setPosition(dragon.getX() + desiredVel.x * 0.65D, dragon.getY() + desiredVel.y * 0.65D, dragon.getZ() + desiredVel.z * 0.65D);
			} else {
				e.setVelocity(e.getVelocity().multiply(0.48D).add(desiredVel));
				e.velocityModified = true;
			}
			e.fallDistance = 0.0F;

			if (dist < 3.6D && this.age % 8 == 0 && e instanceof LivingEntity living && !(living instanceof ModNpcEntity)) {
				living.timeUntilRegen = 0;
				if (living instanceof EnderDragonEntity dragon) {
					dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), 20.0F);
				} else {
					living.damage(sw.getDamageSources().outOfWorld(), 20.0F);
				}
			}
		}

		if (this.age >= TOTAL_LIFETIME) {
			sw.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
			sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 5, 0.5D, 0.5D, 0.5D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 180, 1.0D, 1.0D, 1.0D, 0.85D);
			sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 200, 1.2D, 1.2D, 1.2D, 0.9D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 2.0F, 0.75F);

			sw.createExplosion(
					this,
					center.x,
					center.y,
					center.z,
					8.0F,
					false,
					this.breakBlocks ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE
			);

			for (Entity e : targets) {
				if (e instanceof LivingEntity living && !(living instanceof ModNpcEntity)) {
					living.timeUntilRegen = 0;
					if (living instanceof EnderDragonEntity dragon) {
						dragon.damagePart(dragon.head, sw.getDamageSources().sonicBoom(this), 80.0F);
					} else {
						living.damage(sw.getDamageSources().sonicBoom(this), 120.0F);
					}
					Vec3d away = e.getPos().subtract(center);
					Vec3d push = away.lengthSquared() > 0.01D ? away.normalize().multiply(2.6D) : new Vec3d(0.0D, 1.5D, 0.0D);
					e.setVelocity(push.x, Math.max(0.95D, push.y + 0.75D), push.z);
					e.velocityModified = true;
				}
			}
			this.discard();
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.casterUuid != null) {
			nbt.putUuid("CasterUuid", this.casterUuid);
		}
		nbt.putBoolean("BreakBlocks", this.breakBlocks);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
		if (nbt.contains("BreakBlocks")) {
			this.breakBlocks = nbt.getBoolean("BreakBlocks");
		}
	}
}
