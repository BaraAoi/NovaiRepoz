package com.novaimod.entity;

import com.novaimod.registry.ModEntities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Невидимая сущность-сиденье для Джакузи на Эго-острове:
 * позволяет игроку реально сидеть в воде с пузырьками, даёт лёгкое расслабление (Регенерацию)
 * и автоматически исчезает, как только игрок встаёт (Shift).
 */
public class SeatEntity extends Entity {
	public SeatEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
		this.setInvisible(true);
		this.setInvulnerable(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient()) {
			return;
		}
		if (!this.hasPassengers()) {
			this.discard();
			return;
		}

		// Пока игрок сидит в джакузи — идут пузырьки, тёплый пар и эффект релакса!
		if (this.age % 15 == 0 && this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(
					ParticleTypes.BUBBLE_POP,
					this.getX(),
					this.getY() + 0.55D,
					this.getZ(),
					6,
					0.35D,
					0.1D,
					0.35D,
					0.03D
			);
			sw.spawnParticles(
					ParticleTypes.CAMPFIRE_COSY_SMOKE,
					this.getX(),
					this.getY() + 0.85D,
					this.getZ(),
					1,
					0.2D,
					0.05D,
					0.2D,
					0.006D
			);

			Entity first = this.getFirstPassenger();
			if (first instanceof PlayerEntity player) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 60, 0, true, false, false));
			}
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}

	public static void sitPlayer(World world, BlockPos pos, PlayerEntity player) {
		if (world.isClient() || player.hasVehicle()) {
			return;
		}
		Box checkBox = new Box(pos).expand(0.2D);
		if (!world.getEntitiesByClass(SeatEntity.class, checkBox, Entity::hasPassengers).isEmpty()) {
			return;
		}

		SeatEntity seat = ModEntities.SEAT_ENTITY.create(world);
		if (seat == null) {
			return;
		}
		// Размещаем точку посадки так, чтобы игрок сидел по плечи в бурлящей воде джакузи
		seat.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D, player.getYaw(), 0.0F);
		world.spawnEntity(seat);
		player.startRiding(seat, true);
		world.playSound(null, pos, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.BLOCKS, 0.9F, 1.0F);
	}
}
