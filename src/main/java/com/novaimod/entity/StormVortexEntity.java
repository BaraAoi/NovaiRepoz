package com.novaimod.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Oxidizable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * 3D-Вихрь Тысячи Молний «Storm Sovereign»:
 * - Вызывает вращающийся 30-блочный грозовой вихрь с настоящими ветвистыми 3D-молниями!
 * - Прицельно поражает всех врагов в радиусе 28 блоков, заряжает Криперов в Заряженных,
 *   и мгновенно очищает от окисления всю Медь в зоне бури!
 */
public class StormVortexEntity extends Entity {
	public static final int TOTAL_TICKS = 150;

	public StormVortexEntity(EntityType<? extends StormVortexEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.age == 2) {
			// Очищает окисление со всех медных блоков под бурей!
			BlockPos center = this.getBlockPos();
			for (int dx = -12; dx <= 12; dx++) {
				for (int dy = -6; dy <= 6; dy++) {
					for (int dz = -12; dz <= 12; dz++) {
						BlockPos bp = center.add(dx, dy, dz);
						BlockState bs = sw.getBlockState(bp);
						Oxidizable.getUnaffectedOxidationState(bs.getBlock()).ifPresent(cleanBlock -> {
							sw.setBlockState(bp, cleanBlock.getStateWithProperties(bs), Block.NOTIFY_ALL);
						});
					}
				}
			}
		}

		if (this.age % 6 == 0) {
			Box stormBox = this.getBoundingBox().expand(26.0D, 18.0D, 26.0D);
			var targets = sw.getEntitiesByClass(
					LivingEntity.class,
					stormBox,
					e -> e.isAlive() && !(e instanceof PlayerEntity)
							&& !(e instanceof LuceEntity) && !(e instanceof HolyOrderEntity) && !(e instanceof CatgirlEntity)
							&& !(e instanceof TameableEntity t && t.isTamed())
							&& (e instanceof Monster || e instanceof WhiteCapEntity)
			);
			if (!targets.isEmpty()) {
				LivingEntity victim = targets.get(sw.random.nextInt(targets.size()));
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
				if (bolt != null) {
					bolt.setCosmetic(true);
					bolt.refreshPositionAfterTeleport(victim.getPos());
					sw.spawnEntity(bolt);
				}
				if (victim instanceof net.minecraft.entity.mob.CreeperEntity creeper) {
					LightningEntity realCharge = EntityType.LIGHTNING_BOLT.create(sw);
					if (realCharge != null) {
						creeper.onStruckByLightning(sw, realCharge);
					}
				}
				victim.damage(sw.getDamageSources().lightningBolt(), 75.0F);
				sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, victim.getX(), victim.getBodyY(0.5D), victim.getZ(), 24, 0.5D, 0.7D, 0.5D, 0.25D);
			} else {
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.4F, 0.9F + sw.random.nextFloat() * 0.4F);
			}
		}

		if (this.age >= TOTAL_TICKS) {
			this.discard();
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 384.0D * 384.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
	}
}
