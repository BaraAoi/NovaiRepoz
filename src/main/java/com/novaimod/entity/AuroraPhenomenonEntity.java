package com.novaimod.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Небесный 3D-феномен «Ткач Северного Сияния и Звёздного Дождя» (Aurora Borealis Weaver):
 * раскрывает высоко в небе гигантские колышущиеся занавеси полярного сияния шириной 95 блоков
 * и поток падающих 3D-звёзд с искристыми хвостами!
 */
public class AuroraPhenomenonEntity extends Entity {
	public static final int TOTAL_DURATION_TICKS = 520; // 26 секунд небесной красоты

	private static final TrackedData<Integer> PALETTE = DataTracker.registerData(
			AuroraPhenomenonEntity.class, TrackedDataHandlerRegistry.INTEGER
	);

	public AuroraPhenomenonEntity(EntityType<? extends AuroraPhenomenonEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PALETTE, 0);
	}

	public void setPalette(int palette) {
		this.dataTracker.set(PALETTE, MathHelper.clamp(palette, 0, 3));
	}

	public int getPalette() {
		return this.dataTracker.get(PALETTE);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		// Мягкий звездопад вокруг игроков под куполом Северного Сияния
		if (this.age % 4 == 0) {
			for (int i = 0; i < 5; i++) {
				double ox = (sw.random.nextDouble() - 0.5D) * 44.0D;
				double oz = (sw.random.nextDouble() - 0.5D) * 44.0D;
				double oy = -6.0D - sw.random.nextDouble() * 16.0D;
				sw.spawnParticles(ParticleTypes.END_ROD, this.getX() + ox, this.getY() + oy, this.getZ() + oz, 1, 0.1D, 0.1D, 0.1D, 0.015D);
			}
		}

		if (this.age % 30 == 0) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 1.6F, 0.9F + sw.random.nextFloat() * 0.4F);
			Box blessBox = this.getBoundingBox().expand(48.0D, 42.0D, 48.0D);
			for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, blessBox, ServerPlayerEntity::isAlive)) {
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2, true, false, true));
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, true, false, true));
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
			}
		}

		if (this.age >= TOTAL_DURATION_TICKS) {
			this.discard();
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 512.0D * 512.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		setPalette(nbt.getInt("Palette"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Palette", getPalette());
	}
}
