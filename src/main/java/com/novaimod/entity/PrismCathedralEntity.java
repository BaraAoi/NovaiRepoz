package com.novaimod.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 3D-собор кристаллов «Призма Зеркального Измерения» (Mirror Dimension Prism):
 * - Разворачивает вокруг заклинателя вращающийся 20-блочный 3D геодезический купол,
 *   двойные священные мандалы и 24 парящих призматических осколка с преломлением радужных лучей.
 * - Подвешивает всех врагов и вражеские снаряды внутри купола в невесомости,
 *   а на 135-м тике все 24 осколка одновременно поражают захваченные цели схождением призматических лазеров!
 */
public class PrismCathedralEntity extends Entity {
	public static final int CONVERGENCE_TICK = 135;
	public static final int TOTAL_TICKS = 180;
	public static final double DOME_RADIUS = 16.0D;

	private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(
			PrismCathedralEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
	);

	public PrismCathedralEntity(EntityType<? extends PrismCathedralEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(OWNER_UUID, Optional.empty());
	}

	public void setOwner(PlayerEntity player) {
		this.dataTracker.set(OWNER_UUID, Optional.of(player.getUuid()));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		ServerPlayerEntity owner = this.dataTracker.get(OWNER_UUID)
				.map(id -> sw.getServer().getPlayerManager().getPlayer(id))
				.orElse(null);

		Box domeBox = this.getBoundingBox().expand(DOME_RADIUS, 12.0D, DOME_RADIUS);

		if (this.age < CONVERGENCE_TICK) {
			// Невесомый призматический стазис для врагов и вражеских снарядов!
			for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, domeBox, e -> e.isAlive() && (e instanceof Monster || e instanceof WhiteCapEntity))) {
				mob.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 12, 1, true, false, false));
				mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 25, 0, true, false, false));
				mob.setVelocity(mob.getVelocity().x * 0.25D, Math.min(0.08D, mob.getVelocity().y), mob.getVelocity().z * 0.25D);
			}
			for (ProjectileEntity proj : sw.getEntitiesByClass(ProjectileEntity.class, domeBox, p -> p.isAlive() && p.getOwner() != owner)) {
				proj.setVelocity(Vec3d.ZERO);
				proj.setNoGravity(true);
			}
			if (this.age % 14 == 0) {
				float pitch = 0.85F + (this.age / (float) CONVERGENCE_TICK) * 0.75F;
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_STEP, SoundCategory.MASTER, 1.5F, pitch);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.2F, pitch);
			}
		} else if (this.age == CONVERGENCE_TICK) {
			// СИНХРОННОЕ СХОЖДЕНИЕ ВСЕХ 24 ПРИЗМАТИЧЕСКИХ ОСКОЛКОВ!
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 2.2F, 0.75F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.MASTER, 2.2F, 1.25F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.MASTER, 1.5F, 1.45F);

			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 3.0D, this.getZ(), 4, 2.5D, 1.5D, 2.5D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0D, this.getZ(), 110, 8.0D, 4.0D, 8.0D, 0.28D);

			for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, domeBox, e -> e.isAlive() && (e instanceof Monster || e instanceof WhiteCapEntity))) {
				if (owner != null) {
					mob.damage(sw.getDamageSources().playerAttack(owner), 130.0F);
				} else {
					mob.damage(sw.getDamageSources().magic(), 130.0F);
				}
				sw.spawnParticles(ParticleTypes.FIREWORK, mob.getX(), mob.getBodyY(0.5D), mob.getZ(), 24, 0.4D, 0.6D, 0.4D, 0.16D);
			}
		} else if (this.age >= TOTAL_TICKS) {
			this.discard();
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 320.0D * 320.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("Owner")) {
			this.dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("Owner")));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		this.dataTracker.get(OWNER_UUID).ifPresent(id -> nbt.putUuid("Owner", id));
	}
}
