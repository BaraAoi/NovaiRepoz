package com.novaimod.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * Небесный 3D-феномен «Aurora Borealis Weaver»:
 * помимо 7 гигантских колышущихся 92-блочных лент Северного Сияния и 3D-звездопада:
 * - мгновенно разгоняет грозовые тучи и дождь в ясное звёздное небо и изгоняет Фантомов;
 * - ускоряет рост урожая и исцеляет жителей, питомцев и игроков под сиянием;
 * - дарует воздушный поток для полёта на Элитрах и роняет на землю Упавшие Звёзды (опыт + кристаллы)!
 */
public class AuroraPhenomenonEntity extends Entity {
	public static final int TOTAL_DURATION_TICKS = 560;

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

		if (this.age == 1 && (sw.isRaining() || sw.isThundering())) {
			sw.setWeather(18000, 0, false, false);
		}

		if (this.age % 4 == 0) {
			for (int i = 0; i < 5; i++) {
				double ox = (sw.random.nextDouble() - 0.5D) * 44.0D;
				double oz = (sw.random.nextDouble() - 0.5D) * 44.0D;
				double oy = -6.0D - sw.random.nextDouble() * 16.0D;
				sw.spawnParticles(ParticleTypes.END_ROD, this.getX() + ox, this.getY() + oy, this.getZ() + oz, 1, 0.1D, 0.1D, 0.1D, 0.015D);
			}
		}

		Box blessBox = this.getBoundingBox().expand(48.0D, 46.0D, 48.0D);

		// Подъёмный звёздный ветер для полёта на Элитрах под Северным Сиянием!
		for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, blessBox, ServerPlayerEntity::isAlive)) {
			if (sp.isFallFlying()) {
				Vec3d look = sp.getRotationVec(1.0F);
				Vec3d v = sp.getVelocity();
				sp.setVelocity(v.add(look.x * 0.06D, 0.035D, look.z * 0.06D));
				sp.velocityModified = true;
			}
		}

		if (this.age % 25 == 0) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT, 1.5F, 0.9F + sw.random.nextFloat() * 0.4F);

			for (ServerPlayerEntity sp : sw.getEntitiesByClass(ServerPlayerEntity.class, blessBox, ServerPlayerEntity::isAlive)) {
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2, true, false, true));
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0, true, false, true));
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 280, 0, true, false, true));
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 200, 1, true, false, true));
			}
			// Изгоняет ночных Фантомов светом Авроры и исцеляет деревенских жителей
			for (PhantomEntity ph : sw.getEntitiesByClass(PhantomEntity.class, blessBox, LivingEntity::isAlive)) {
				sw.spawnParticles(ParticleTypes.END_ROD, ph.getX(), ph.getY(), ph.getZ(), 12, 0.3D, 0.3D, 0.3D, 0.05D);
				ph.discard();
			}
			for (VillagerEntity vil : sw.getEntitiesByClass(VillagerEntity.class, blessBox, LivingEntity::isAlive)) {
				vil.heal(6.0F);
			}

			// Благословение плодородия посевам под Северным Сиянием
			BlockPos groundCenter = BlockPos.ofFloored(this.getX(), this.getY() - 28.0D, this.getZ());
			for (int c = 0; c < 6; c++) {
				BlockPos bp = groundCenter.add(sw.random.nextBetween(-26, 26), sw.random.nextBetween(-4, 4), sw.random.nextBetween(-26, 26));
				BlockState bs = sw.getBlockState(bp);
				if (bs.getBlock() instanceof Fertilizable fert && fert.isFertilizable(sw, bp, bs)) {
					fert.grow(sw, sw.random, bp, bs);
				}
			}
		}

		// Каждые 55 тиков одна из падающих звёзд мягко приземляется в виде Звёздного Дара!
		if (this.age % 55 == 0) {
			int sx = MathHelper.floor(this.getX()) + sw.random.nextBetween(-20, 20);
			int sz = MathHelper.floor(this.getZ()) + sw.random.nextBetween(-20, 20);
			int sy = sw.getTopY(Heightmap.Type.MOTION_BLOCKING, sx, sz);
			sw.spawnParticles(ParticleTypes.FIREWORK, sx + 0.5D, sy + 0.5D, sz + 0.5D, 16, 0.3D, 0.4D, 0.3D, 0.08D);
			ExperienceOrbEntity.spawn(sw, new Vec3d(sx + 0.5D, sy + 0.5D, sz + 0.5D), 25);
			ItemStack starGift = sw.random.nextInt(5) == 0
					? new ItemStack(Items.DIAMOND, 1)
					: new ItemStack(Items.AMETHYST_SHARD, 2);
			ItemEntity giftEntity = new ItemEntity(sw, sx + 0.5D, sy + 0.4D, sz + 0.5D, starGift);
			giftEntity.setGlowing(true);
			sw.spawnEntity(giftEntity);
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
