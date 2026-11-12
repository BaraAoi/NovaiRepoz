package com.novaimod.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Небесная 3D-Астролябия и Часовой Механизм Вселенной («Made in Heaven: Time of the World»):
 * парит высоко в небесах над заклинателем и вращает золотые кольца и стрелки часов
 * синхронно с экспоненциально ускоряющимся временем мира!
 */
public class ChronoAstrolabeEntity extends Entity {
	private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(
			ChronoAstrolabeEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
	);
	private static final TrackedData<Integer> TOTAL_TICKS = DataTracker.registerData(
			ChronoAstrolabeEntity.class, TrackedDataHandlerRegistry.INTEGER
	);

	public ChronoAstrolabeEntity(EntityType<? extends ChronoAstrolabeEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(OWNER_UUID, Optional.empty());
		builder.add(TOTAL_TICKS, 400);
	}

	public void setOwnerUuid(UUID uuid) {
		this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
	}

	public void setTotalTicks(int ticks) {
		this.dataTracker.set(TOTAL_TICKS, Math.max(40, ticks));
	}

	public int getTotalTicks() {
		return this.dataTracker.get(TOTAL_TICKS);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld sw) {
			UUID ownerId = this.dataTracker.get(OWNER_UUID).orElse(null);
			if (ownerId != null) {
				ServerPlayerEntity owner = sw.getServer().getPlayerManager().getPlayer(ownerId);
				if (owner != null && owner.getServerWorld() == sw) {
					double tx = MathHelper.lerp(0.18D, this.getX(), owner.getX());
					double ty = MathHelper.lerp(0.14D, this.getY(), owner.getY() + 34.0D);
					double tz = MathHelper.lerp(0.18D, this.getZ(), owner.getZ());
					this.setPosition(tx, ty, tz);
				}
			}
			if (this.age > this.getTotalTicks() + 40) {
				this.discard();
			}
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 512.0D * 512.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("Owner")) {
			setOwnerUuid(nbt.getUuid("Owner"));
		}
		if (nbt.contains("TotalTicks")) {
			setTotalTicks(nbt.getInt("TotalTicks"));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		this.dataTracker.get(OWNER_UUID).ifPresent(id -> nbt.putUuid("Owner", id));
		nbt.putInt("TotalTicks", getTotalTicks());
	}
}
