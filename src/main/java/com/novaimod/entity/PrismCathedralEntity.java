package com.novaimod.entity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 3D-собор кристаллов «Mirror Dimension Prism»:
 * - Практический непробиваемый силовой купол для защиты базы и союзников (22 секунды):
 *   1) Дарует игрокам и союзникам внутри Сопротивление IV и Регенерацию III;
 *   2) Гасит взрывы динамита/криперов/черепов Визера внутри купола и отражает ВСЕ вражеские снаряды
 *      (стрелы, фаерболы, чумные колбы, трезубцы) обратно в стрелка с удвоенной скоростью!
 *   3) Подвешивает вторгшихся монстров в невесомости и каждые 70 тиков прожигает их залпом 24 призм!
 */
public class PrismCathedralEntity extends Entity {
	public static final int CONVERGENCE_TICK = 135;
	public static final int TOTAL_TICKS = 400; // 20 секунд силового щита базы!
	public static final double DOME_RADIUS = 16.0D;

	private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(
			PrismCathedralEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
	);

	public PrismCathedralEntity(EntityType<? extends PrismCathedralEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(OWNER_UUID, Optional.empty());
	}

	public void setOwner(PlayerEntity player) {
		this.dataTracker.set(OWNER_UUID, Optional.of(player.getUuid()));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		ServerPlayerEntity owner = this.dataTracker.get(OWNER_UUID)
				.map(id -> sw.getServer().getPlayerManager().getPlayer(id))
				.orElse(null);

		Box domeBox = this.getBoundingBox().expand(DOME_RADIUS, 13.0D, DOME_RADIUS);

		// 1. Защита всех игроков и мирных союзников внутри Зеркального Купола!
		if (this.age % 20 == 0) {
			for (PlayerEntity ally : sw.getEntitiesByClass(PlayerEntity.class, domeBox, LivingEntity::isAlive)) {
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 50, 3, true, false, true));
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 50, 2, true, false, true));
			}
		}

		// 2. Зеркальное ОТРАЖЕНИЕ всех вражеских снарядов обратно во врагов + гашение активного ТНТ!
		for (ProjectileEntity proj : sw.getEntitiesByClass(ProjectileEntity.class, domeBox, p -> p.isAlive() && p.getOwner() != owner)) {
			if (!proj.getCommandTags().contains("novai_prism_reflected")) {
				proj.addCommandTag("novai_prism_reflected");
				Vec3d rev = proj.getVelocity().multiply(-2.2D);
				proj.setVelocity(rev.x, Math.max(0.15D, rev.y), rev.z);
				proj.velocityModified = true;
				if (owner != null) {
					proj.setOwner(owner);
				}
				sw.spawnParticles(ParticleTypes.FLASH, proj.getX(), proj.getY(), proj.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
				sw.playSound(null, proj.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.3F, 1.65F);
			}
		}
		for (net.minecraft.entity.TntEntity tnt : sw.getEntitiesByClass(net.minecraft.entity.TntEntity.class, domeBox, Entity::isAlive)) {
			sw.spawnParticles(ParticleTypes.END_ROD, tnt.getX(), tnt.getY() + 0.5D, tnt.getZ(), 15, 0.3D, 0.3D, 0.3D, 0.08D);
			sw.playSound(null, tnt.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.MASTER, 1.2F, 1.4F);
			tnt.discard(); // Поглощает взрыв динамита в зеркальное измерение!
		}

		// 3. Невесомый стазис вторгшихся монстров (включая криперов — блокирует детонацию криперов!)
		for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, domeBox, e -> e.isAlive() && (e instanceof Monster || e instanceof WhiteCapEntity))) {
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 15, 1, true, false, false));
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, true, false, false));
			mob.setVelocity(mob.getVelocity().x * 0.2D, Math.min(0.08D, mob.getVelocity().y), mob.getVelocity().z * 0.2D);
			if (mob instanceof net.minecraft.entity.mob.CreeperEntity creeper) {
				creeper.setFuseSpeed(-1);
			}
		}

		if (this.age % 24 == 0) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.1F, 1.15F);
		}

		// Каждые 80 тиков и на финале 24 призмы синхронно прожигают всех пойманных монстров!
		if (this.age == CONVERGENCE_TICK || (this.age > 60 && this.age % 80 == 0)) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.MASTER, 1.8F, 0.85F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.MASTER, 2.0F, 1.3F);
			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 3.0D, this.getZ(), 3, 2.0D, 1.2D, 2.0D, 0.0D);

			for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, domeBox, e -> e.isAlive() && (e instanceof Monster || e instanceof WhiteCapEntity))) {
				if (owner != null) {
					mob.damage(sw.getDamageSources().playerAttack(owner), 120.0F);
				} else {
					mob.damage(sw.getDamageSources().magic(), 120.0F);
				}
				sw.spawnParticles(ParticleTypes.FIREWORK, mob.getX(), mob.getBodyY(0.5D), mob.getZ(), 20, 0.4D, 0.6D, 0.4D, 0.15D);
			}
		}

		if (this.age >= TOTAL_TICKS) {
			this.discard();
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 320.0D * 320.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("Owner")) {
			this.dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("Owner")));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		this.dataTracker.get(OWNER_UUID).ifPresent(id -> nbt.putUuid("Owner", id));
	}
}
