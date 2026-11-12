package com.novaimod.entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEffects;
import com.novaimod.registry.ModEntities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Пространственный разрез от «Клинка Разлома Реальности»:
 * - Режим 0 (Волна Разлома): стремительный 3D-серп, рассекающий стены и всех врагов на 85 блоков вперёд!
 * - Режим 1 (Приговор Пространства / Judgment Cut End): покрывает радиус 48 блоков десятками 3D-трещин пространства
 *   и через 1.3 секунды раскалывает реальность, нанося колоссальный урон всем врагам одновременно!
 */
public class RealitySlashEntity extends Entity {
	public static final int MODE_CRESCENT_WAVE = 0;
	public static final int MODE_JUDGMENT_CUT_END = 1;

	private static final TrackedData<Integer> MODE =
			DataTracker.registerData(RealitySlashEntity.class, TrackedDataHandlerRegistry.INTEGER);

	@Nullable
	private UUID casterUuid;
	private Vec3d flightVel = Vec3d.ZERO;
	private final Set<UUID> alreadySliced = new HashSet<>();

	public RealitySlashEntity(EntityType<? extends RealitySlashEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	public RealitySlashEntity(World world, double x, double y, double z, Vec3d velocity, float yaw, float pitch, @Nullable UUID casterUuid, int mode) {
		this(ModEntities.REALITY_SLASH_ENTITY, world);
		this.setPosition(x, y, z);
		this.setYaw(yaw);
		this.setPitch(pitch);
		this.flightVel = velocity;
		this.casterUuid = casterUuid;
		this.dataTracker.set(MODE, mode);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(MODE, MODE_CRESCENT_WAVE);
	}

	public int getMode() {
		return this.dataTracker.get(MODE);
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0D * 256.0D;
	}

	@Override
	public void tick() {
		this.noClip = true;
		this.prevX = this.getX();
		this.prevY = this.getY();
		this.prevZ = this.getZ();
		super.tick();

		if (getMode() == MODE_CRESCENT_WAVE) {
			tickCrescentWave();
		} else {
			tickJudgmentCutEnd();
		}
	}

	private void tickCrescentWave() {
		if (!this.getWorld().isClient()) {
			this.setPosition(this.getX() + this.flightVel.x, this.getY() + this.flightVel.y, this.getZ() + this.flightVel.z);
		} else {
			return;
		}

		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 8, 1.4D, 0.8D, 1.4D, 0.08D);
		sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX(), this.getY(), this.getZ(), 2, 0.8D, 0.4D, 0.8D, 0.0D);

		Entity caster = this.casterUuid != null ? sw.getPlayerByUuid(this.casterUuid) : null;
		Box sliceBox = Box.of(this.getPos(), 8.5D, 5.5D, 8.5D);
		for (LivingEntity victim : sw.getEntitiesByClass(LivingEntity.class, sliceBox, LivingEntity::isAlive)) {
			if (victim instanceof ModNpcEntity) {
				continue;
			}
			if (this.casterUuid != null && this.casterUuid.equals(victim.getUuid())) {
				continue;
			}
			if (!this.alreadySliced.add(victim.getUuid())) {
				continue;
			}
			victim.timeUntilRegen = 0;
			victim.addStatusEffect(new StatusEffectInstance(ModEffects.PERCENT_EROSION, 60, 3, false, true, true), caster);
			if (victim instanceof EnderDragonEntity dragon) {
				dragon.damagePart(dragon.head, sw.getDamageSources().magic(), 180.0F);
			} else {
				victim.damage(sw.getDamageSources().magic(), 250.0F);
			}
			sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, victim.getX(), victim.getBodyY(0.5D), victim.getZ(), 6, 0.6D, 0.6D, 0.6D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, victim.getX(), victim.getBodyY(0.5D), victim.getZ(), 25, 0.5D, 0.6D, 0.5D, 0.25D);
			sw.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.MASTER, 1.4F, 1.45F);
		}

		if (this.age >= 36) {
			this.discard();
		}
	}

	private void tickJudgmentCutEnd() {
		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		Box domainBox = Box.of(this.getPos(), 96.0D, 64.0D, 96.0D);
		List<MobEntity> mobs = sw.getEntitiesByClass(MobEntity.class, domainBox, m -> m.isAlive() && !(m instanceof ModNpcEntity));

		// Удерживаем всех врагов замершими среди пространственных нитей до финального раскола!
		for (MobEntity m : mobs) {
			m.getNavigation().stop();
			m.setVelocity(Vec3d.ZERO);
			m.velocityModified = true;
			if (this.age % 4 == 0) {
				sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, m.getX(), m.getBodyY(0.5D), m.getZ(), 2, 0.5D, 0.5D, 0.5D, 0.0D);
			}
		}

		if (this.age % 5 == 1) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.MASTER, 1.5F, 1.6F + this.age * 0.02F);
		}

		if (this.age >= 26) {
			// Раскол реальности!
			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.5D, this.getZ(), 4, 2.0D, 1.0D, 2.0D, 0.0D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 2.4F, 0.65F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.MASTER, 2.2F, 1.45F);

			for (MobEntity victim : mobs) {
				victim.timeUntilRegen = 0;
				sw.spawnParticles(ParticleTypes.END_ROD, victim.getX(), victim.getBodyY(0.5D), victim.getZ(), 30, 0.5D, 0.6D, 0.5D, 0.35D);
				sw.spawnParticles(ParticleTypes.SONIC_BOOM, victim.getX(), victim.getBodyY(0.5D), victim.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
				if (victim instanceof EnderDragonEntity dragon) {
					dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), 350.0F);
				} else {
					victim.damage(sw.getDamageSources().outOfWorld(), 600.0F);
				}
			}
			this.discard();
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(MODE, nbt.getInt("SlashMode"));
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
		this.flightVel = new Vec3d(nbt.getDouble("Vx"), nbt.getDouble("Vy"), nbt.getDouble("Vz"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("SlashMode", getMode());
		if (this.casterUuid != null) {
			nbt.putUuid("CasterUuid", this.casterUuid);
		}
		nbt.putDouble("Vx", this.flightVel.x);
		nbt.putDouble("Vy", this.flightVel.y);
		nbt.putDouble("Vz", this.flightVel.z);
	}
}
