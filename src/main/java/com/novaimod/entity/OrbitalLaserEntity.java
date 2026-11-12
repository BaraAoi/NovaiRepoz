package com.novaimod.entity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Сущность «Орбитального Луча Стирания»:
 * 20 тиков сводит орбитальные кольца наведения, после чего на 5.5 секунд обрушивает с небес
 * исполинский столб плазмы высотой 180 блоков, который сам ведётся за ближайшими мобами и выжигает всё на пути!
 */
public class OrbitalLaserEntity extends Entity {
	public static final int LOCK_ON_TICKS = 20;
	public static final int TOTAL_TICKS = 135;

	private static final TrackedData<Boolean> BREAK_BLOCKS =
			DataTracker.registerData(OrbitalLaserEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Nullable
	private UUID casterUuid;

	public OrbitalLaserEntity(EntityType<? extends OrbitalLaserEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	public OrbitalLaserEntity(World world, double x, double y, double z, @Nullable UUID casterUuid, boolean breakBlocks) {
		this(ModEntities.ORBITAL_LASER_ENTITY, world);
		this.setPosition(x, y, z);
		this.casterUuid = casterUuid;
		this.dataTracker.set(BREAK_BLOCKS, breakBlocks);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(BREAK_BLOCKS, true);
	}

	public boolean isBreakBlocks() {
		return this.dataTracker.get(BREAK_BLOCKS);
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

		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.age < LOCK_ON_TICKS) {
			if (this.age == 1) {
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.MASTER, 2.0F, 1.15F);
			}
			return;
		}

		if (this.age == LOCK_ON_TICKS) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 2.4F, 0.65F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.MASTER, 2.2F, 1.2F);
			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.0D, this.getZ(), 3, 0.5D, 0.5D, 0.5D, 0.0D);
		}

		// Плавное самонаведение орбитального луча на ближайших живых мобов в радиусе 45 блоков!
		Box scanBox = Box.of(this.getPos(), 90.0D, 80.0D, 90.0D);
		List<MobEntity> mobs = sw.getEntitiesByClass(MobEntity.class, scanBox, m -> m.isAlive() && !(m instanceof ModNpcEntity));
		if (!mobs.isEmpty()) {
			MobEntity closest = null;
			double bestDistSq = Double.MAX_VALUE;
			for (MobEntity m : mobs) {
				double d = m.squaredDistanceTo(this);
				if (d < bestDistSq) {
					bestDistSq = d;
					closest = m;
				}
			}
			if (closest != null) {
				Vec3d delta = closest.getPos().subtract(this.getPos());
				Vec3d horiz = new Vec3d(delta.x, 0.0D, delta.z);
				if (horiz.lengthSquared() > 0.05D) {
					Vec3d step = horiz.normalize().multiply(Math.min(0.75D, horiz.length()));
					double nx = this.getX() + step.x;
					double nz = this.getZ() + step.z;
					int surfY = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(nx), MathHelper.floor(nz));
					double ny = MathHelper.lerp(0.25D, this.getY(), surfY);
					this.setPosition(nx, ny, nz);
				}
			}
		}

		// Испепеление всех врагов в столбе плазмы (от земли до самого космоса!)
		Entity caster = this.casterUuid != null ? sw.getPlayerByUuid(this.casterUuid) : null;
		Box pillarBox = new Box(
				this.getX() - 4.2D, this.getY() - 6.0D, this.getZ() - 4.2D,
				this.getX() + 4.2D, this.getY() + 160.0D, this.getZ() + 4.2D
		);
		for (LivingEntity victim : sw.getEntitiesByClass(LivingEntity.class, pillarBox, LivingEntity::isAlive)) {
			if (victim instanceof ModNpcEntity) {
				continue;
			}
			if (this.casterUuid != null && this.casterUuid.equals(victim.getUuid())) {
				continue;
			}
			victim.timeUntilRegen = 0;
			victim.setOnFireFor(8.0F);
			if (victim instanceof EnderDragonEntity dragon) {
				dragon.damagePart(dragon.head, sw.getDamageSources().sonicBoom(caster != null ? caster : this), 55.0F);
			} else {
				victim.damage(sw.getDamageSources().sonicBoom(caster != null ? caster : this), 65.0F);
			}
		}

		// Испарение блоков под лучом при включённом режиме стирания материи
		if (isBreakBlocks() && this.age % 3 == 0) {
			BlockPos center = this.getBlockPos();
			BlockPos.Mutable m = new BlockPos.Mutable();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (dx * dx + dz * dz > 6) {
						continue;
					}
					for (int dy = -2; dy <= 3; dy++) {
						m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
						BlockState st = sw.getBlockState(m);
						if (!st.isAir() && !st.isOf(Blocks.BEDROCK) && !st.isOf(Blocks.END_PORTAL_FRAME) && !st.isOf(Blocks.END_PORTAL)) {
							sw.setBlockState(m, Blocks.AIR.getDefaultState());
						}
					}
				}
			}
			this.setPosition(this.getX(), Math.max(sw.getBottomY() + 2, this.getY() - 0.6D), this.getZ());
		}

		// Ударные кольца частиц у подножия луча
		sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.5D, this.getZ(), 14, 2.2D, 0.5D, 2.2D, 0.25D);
		if (this.age % 6 == 0) {
			sw.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5D, this.getZ(), 2, 1.5D, 0.3D, 1.5D, 0.0D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 2.0F, 1.7F);
		}

		if (this.age >= TOTAL_TICKS) {
			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 1.0D, this.getZ(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
			this.discard();
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(BREAK_BLOCKS, nbt.getBoolean("BreakBlocks"));
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("BreakBlocks", isBreakBlocks());
		if (this.casterUuid != null) {
			nbt.putUuid("CasterUuid", this.casterUuid);
		}
	}
}
