package com.novaimod.entity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEntities;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Пылающий небесный болид от креативного «Жезла Небесной Кары»:
 * обрушивается с небес со шлейфом лавы и дыма, бьёт молнией и выжигает врагов ударной волной!
 */
public class CelestialMeteorEntity extends ThrownItemEntity {
	@Nullable
	private UUID casterUuid;
	private boolean breakTerrain = false;

	public CelestialMeteorEntity(EntityType<? extends CelestialMeteorEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
	}

	public CelestialMeteorEntity(World world, double x, double y, double z, @Nullable UUID casterUuid, boolean breakTerrain) {
		super(ModEntities.CELESTIAL_METEOR_ENTITY, x, y, z, world);
		this.casterUuid = casterUuid;
		this.breakTerrain = breakTerrain;
		this.setNoGravity(true);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIRE_CHARGE;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
			// Сохраняем стремительное падение болида без воздушного торможения
			this.setVelocity(this.getVelocity().multiply(1.02D));
			this.velocityModified = true;

			sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 6, 0.22D, 0.22D, 0.22D, 0.04D);
			sw.spawnParticles(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 2, 0.15D, 0.15D, 0.15D, 0.0D);
			sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY(), this.getZ(), 2, 0.18D, 0.18D, 0.18D, 0.01D);

			if (this.age >= 100) {
				detonateMeteor(sw, this.getPos());
				this.discard();
			}
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient() && !this.isRemoved() && this.getWorld() instanceof ServerWorld sw) {
			detonateMeteor(sw, hitResult.getPos());
			this.discard();
		}
	}

	private void detonateMeteor(ServerWorld sw, Vec3d hit) {
		sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, hit.x, hit.y + 0.3D, hit.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		sw.spawnParticles(ParticleTypes.LAVA, hit.x, hit.y + 0.5D, hit.z, 18, 0.8D, 0.5D, 0.8D, 0.15D);
		sw.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(hit), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.3F, 0.85F);

		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(hit.x, hit.y, hit.z);
			bolt.setCosmetic(!this.breakTerrain);
			sw.spawnEntity(bolt);
		}

		if (this.breakTerrain) {
			sw.createExplosion(this, hit.x, hit.y, hit.z, 3.8F, true, World.ExplosionSourceType.TNT);
		}

		Box dmgBox = Box.of(hit, 11.0D, 8.0D, 11.0D);
		List<LivingEntity> victims = sw.getEntitiesByClass(LivingEntity.class, dmgBox, e ->
				e.isAlive()
						&& !(e instanceof ModNpcEntity)
						&& (this.casterUuid == null || !this.casterUuid.equals(e.getUuid()))
		);
		for (LivingEntity victim : victims) {
			victim.setOnFireFor(6.0F);
			victim.damage(sw.getDamageSources().magic(), 20.0F);
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.casterUuid != null) {
			nbt.putUuid("CasterUuid", this.casterUuid);
		}
		nbt.putBoolean("BreakTerrain", this.breakTerrain);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
		this.breakTerrain = nbt.getBoolean("BreakTerrain");
	}
}
