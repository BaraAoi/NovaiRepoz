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
 * Креативная сущность «Сфера Сингулярности» (чёрная дыра):
 * вылетает вперёд, зависает в воздухе на 6 секунд, закручивает аккреционный диск из частиц,
 * затягивает в воронку всех мобов, предметы и снаряды в радиусе 22 блоков и схлопывается сверхновой!
 */
public class SingularityOrbEntity extends ThrownItemEntity {
	private static final double PULL_RADIUS = 22.0D;
	private static final int TOTAL_LIFETIME = 140;

	@Nullable
	private UUID casterUuid;

	public SingularityOrbEntity(EntityType<? extends SingularityOrbEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.noClip = true;
	}

	public SingularityOrbEntity(World world, ServerPlayerEntity caster) {
		super(ModEntities.SINGULARITY_ORB_ENTITY, caster, world);
		this.casterUuid = caster.getUuid();
		this.setNoGravity(true);
		this.noClip = true;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SINGULARITY_ORB;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		// Замедляемся и зависаем при касании твёрдой преграды
		this.setVelocity(this.getVelocity().multiply(0.15D));
	}

	@Override
	public void tick() {
		this.noClip = true;
		super.tick();

		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		// Первые 18 тиков вылетает вперёд в точку зависания, затем плавно останавливается
		if (this.age > 18) {
			this.setVelocity(this.getVelocity().multiply(0.76D));
			this.velocityModified = true;
		}

		Vec3d center = this.getPos().add(0.0D, 0.35D, 0.0D);

		// Визуальный аккреционный диск и горизонт событий
		sw.spawnParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 4, 0.25D, 0.25D, 0.25D, 0.01D);
		for (int i = 0; i < 6; i++) {
			double a = this.age * 0.35D + i * (Math.PI / 3.0D);
			double r = 1.6D + (i % 2) * 1.1D;
			double px = center.x + Math.cos(a) * r;
			double py = center.y + Math.sin(a * 2.0D) * 0.28D;
			double pz = center.z + Math.sin(a) * r;
			sw.spawnParticles(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.FLAME, px, py, pz, 1, 0.05D, 0.05D, 0.05D, 0.01D);
		}

		if (this.age % 16 == 1) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.3F, 0.55F);
		}

		// Гравитационное втягивание мобов, предметов и чужих снарядов по спирали
		Box box = Box.of(center, PULL_RADIUS * 2.0D, PULL_RADIUS * 2.0D, PULL_RADIUS * 2.0D);
		List<Entity> targets = sw.getOtherEntities(this, box, e ->
				e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof SingularityOrbEntity)
						&& !(e instanceof InfinityEyeEntity)
						&& (e instanceof MobEntity || e instanceof ItemEntity || e instanceof ProjectileEntity)
		);

		for (Entity e : targets) {
			Vec3d toCenter = center.subtract(e.getPos().add(0.0D, e.getHeight() * 0.4D, 0.0D));
			double dist = toCenter.length();
			if (dist > PULL_RADIUS || dist < 1.0E-3D) {
				continue;
			}
			Vec3d radial = toCenter.normalize();
			// Касательная компонента создаёт красивое вращение вокруг чёрной дыры!
			Vec3d tangent = new Vec3d(-radial.z, 0.0D, radial.x);
			double pullStrength = 0.14D + (1.0D - dist / PULL_RADIUS) * 0.42D;
			Vec3d desiredVel = radial.multiply(pullStrength).add(tangent.multiply(0.18D));

			e.setVelocity(e.getVelocity().multiply(0.55D).add(desiredVel));
			e.velocityModified = true;
			e.fallDistance = 0.0F;

			if (dist < 2.6D && this.age % 10 == 0 && e instanceof LivingEntity living && !(living instanceof ModNpcEntity)) {
				living.damage(sw.getDamageSources().outOfWorld(), 5.0F);
			}
		}

		// Финальный коллапс сверхновой!
		if (this.age >= TOTAL_LIFETIME) {
			sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 3, 0.2D, 0.2D, 0.2D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 90, 0.6D, 0.6D, 0.6D, 0.55D);
			sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 110, 0.8D, 0.8D, 0.8D, 0.65D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.5F, 0.8F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4F, 0.7F);

			for (Entity e : targets) {
				if (e instanceof LivingEntity living && !(living instanceof ModNpcEntity)) {
					living.damage(sw.getDamageSources().sonicBoom(this), 22.0F);
					Vec3d away = e.getPos().subtract(center);
					Vec3d push = away.lengthSquared() > 0.01D ? away.normalize().multiply(1.85D) : new Vec3d(0.0D, 1.2D, 0.0D);
					e.setVelocity(push.x, Math.max(0.75D, push.y + 0.65D), push.z);
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
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
	}
}
