package com.novaimod.entity;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.world.SeamlessTeleportManager;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Пространственный жемчуг, выпускаемый летящим «Оком Бескрайности»:
 * при приземлении (или разрыве в небе) телепортирует владельца далеко по карте
 * или бесшовно переносит его в другое измерение (Незер / Энд / Верхний мир)!
 */
public class InfinityPearlEntity extends ThrownItemEntity {
	@Nullable
	private UUID summonerUuid;
	private boolean allowCrossDimension = true;

	public InfinityPearlEntity(EntityType<? extends InfinityPearlEntity> entityType, World world) {
		super(entityType, world);
	}

	public InfinityPearlEntity(
			World world,
			double x,
			double y,
			double z,
			UUID summonerUuid,
			boolean allowCrossDimension
	) {
		super(ModEntities.INFINITY_PEARL_ENTITY, x, y, z, world);
		this.summonerUuid = summonerUuid;
		this.allowCrossDimension = allowCrossDimension;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.INFINITY_PEARL;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
			if (this.age == 1 || this.age % 20 == 0) {
				sw.getChunkManager().addTicket(ChunkTicketType.PORTAL, this.getChunkPos(), 3, this.getBlockPos());
			}
			if (this.age % 2 == 0) {
				sw.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY(), this.getZ(), 2, 0.08D, 0.08D, 0.08D, 0.05D);
			}
			// Если жемчуг улетел высоко в небо или в пустоту — срабатывает пространственным разрывом прямо в воздухе!
			if (this.age >= 115 || this.getY() < sw.getBottomY() + 2) {
				triggerDimensionalWarp(sw, this.getPos());
				this.discard();
			}
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!this.getWorld().isClient() && !this.isRemoved() && this.getWorld() instanceof ServerWorld sw) {
			triggerDimensionalWarp(sw, hitResult.getPos());
			this.discard();
		}
	}

	private void triggerDimensionalWarp(ServerWorld pearlWorld, Vec3d hitPos) {
		pearlWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, hitPos.x, hitPos.y + 0.2D, hitPos.z, 18, 0.25D, 0.25D, 0.25D, 0.1D);
		if (this.summonerUuid == null) {
			return;
		}
		// Ищем владельца по всему серверу (во всех измерениях!), чтобы жемчуга в Верхнем мире продолжали переносить игрока даже из Незера/Энда!
		ServerPlayerEntity owner = pearlWorld.getServer().getPlayerManager().getPlayer(this.summonerUuid);
		if (owner != null) {
			SeamlessTeleportManager.onPearlImpact(owner, pearlWorld, hitPos, this.allowCrossDimension);
		}
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
