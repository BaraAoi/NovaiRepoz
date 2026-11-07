package com.novaimod.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Парящая сущность «Око Бескрайности»:
 * летит сквозь воздух в вихре пространственных частиц и выпускает десятки (48 шт.)
 * жемчугов Бескрайности веером и спиралью во все стороны!
 */
public class InfinityEyeEntity extends ThrownItemEntity {
	private static final int MAX_FLIGHT_TICKS = 105;

	@Nullable
	private UUID summonerUuid;
	private boolean allowCrossDimension = true;
	private double spiralAngle = 0.0D;

	public InfinityEyeEntity(EntityType<? extends InfinityEyeEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.noClip = true;
	}

	public InfinityEyeEntity(World world, ServerPlayerEntity owner, boolean allowCrossDimension) {
		super(ModEntities.INFINITY_EYE_ENTITY, owner, world);
		this.summonerUuid = owner.getUuid();
		this.allowCrossDimension = allowCrossDimension;
		this.setNoGravity(true);
		this.noClip = true;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.INFINITY_EYE;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		// Око Бескрайности парит сквозь преграды и не разбивается о блоки раньше времени
	}

	@Override
	public void tick() {
		this.noClip = true;
		super.tick();

		Vec3d vel = this.getVelocity();
		// Плавный полёт без торможения воздухом и с лёгким набором высоты над рельефом
		if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
			if (this.age == 1 || this.age % 15 == 0) {
				// Удерживаем чанки активными (ENTITY_TICKING), даже когда первый жемчуг уже унёс игрока за 1500 блоков или в Незер/Энд!
				sw.getChunkManager().addTicket(ChunkTicketType.PORTAL, this.getChunkPos(), 3, this.getBlockPos());
			}

			BlockPos below = BlockPos.ofFloored(this.getX(), this.getY() - 2.5D, this.getZ());
			double lift = !sw.getBlockState(below).isAir() ? 0.035D : 0.004D;
			Vec3d maintained = new Vec3d(vel.x * 1.008D, Math.min(0.22D, vel.y * 0.98D + lift), vel.z * 1.008D);
			if (maintained.horizontalLengthSquared() < 0.14D) {
				maintained = maintained.add(0.0D, 0.02D, 0.0D);
			}
			this.setVelocity(maintained);
			this.velocityModified = true;

			// Спиральные ореолы частиц вокруг летящего Ока
			for (int i = 0; i < 3; i++) {
				double a = this.age * 0.45D + i * (Math.PI * 2.0D / 3.0D);
				double rx = this.getX() + Math.cos(a) * 0.85D;
				double rz = this.getZ() + Math.sin(a) * 0.85D;
				sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, rx, this.getY() + 0.15D, rz, 2, 0.05D, 0.05D, 0.05D, 0.02D);
			}
			if (this.age % 2 == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.15D, this.getZ(), 2, 0.18D, 0.18D, 0.18D, 0.03D);
			}

			// Каждые 4 тика выпускаем по 3 жемчуга Бескрайности по расходящейся спирали (72 жемчуга в полёте!)
			if (this.age >= 4 && this.age <= 100 && this.age % 4 == 0) {
				emitPearlVolley(sw, 3);
			}

			// Финальный звёздный веер из ещё 12 жемчугов (всего 84 жемчуга с одного запуска Ока!)
			if (this.age >= MAX_FLIGHT_TICKS) {
				emitPearlVolley(sw, 12);
				sw.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
				sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, this.getX(), this.getY(), this.getZ(), 55, 0.5D, 0.5D, 0.5D, 0.28D);
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDER_EYE_DEATH, SoundCategory.PLAYERS, 1.2F, 0.85F);
				this.discard();
			}
		}
	}

	private void emitPearlVolley(ServerWorld world, int count) {
		if (this.summonerUuid == null) {
			if (this.getOwner() instanceof LivingEntity le) {
				this.summonerUuid = le.getUuid();
			} else {
				return;
			}
		}

		for (int i = 0; i < count; i++) {
			this.spiralAngle += 2.399963229728653D; // Золотой угол для идеального покрытия на 360 градусов!
			double horizSpeed = 0.55D + world.getRandom().nextDouble() * 0.95D;
			double vy = 0.18D + world.getRandom().nextDouble() * 0.52D;
			double vx = Math.cos(this.spiralAngle) * horizSpeed;
			double vz = Math.sin(this.spiralAngle) * horizSpeed;

			InfinityPearlEntity pearl = new InfinityPearlEntity(world, this.getX(), this.getY() + 0.1D, this.getZ(), this.summonerUuid, this.allowCrossDimension);
			pearl.setVelocity(vx, vy, vz);
			world.spawnEntity(pearl);
		}

		world.playSound(
				null,
				this.getBlockPos(),
				SoundEvents.ENTITY_ENDER_PEARL_THROW,
				SoundCategory.PLAYERS,
				0.65F,
				0.85F + world.getRandom().nextFloat() * 0.55F
		);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.summonerUuid != null) {
			nbt.putUuid("SummonerUuid", this.summonerUuid);
		}
		nbt.putBoolean("AllowCrossDim", this.allowCrossDimension);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.containsUuid("SummonerUuid")) {
			this.summonerUuid = nbt.getUuid("SummonerUuid");
		}
		if (nbt.contains("AllowCrossDim")) {
			this.allowCrossDimension = nbt.getBoolean("AllowCrossDim");
		}
	}
}
