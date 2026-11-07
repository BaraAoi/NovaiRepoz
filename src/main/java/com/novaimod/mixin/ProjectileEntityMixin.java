package com.novaimod.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.ai.evolution.EvolvedMobAccess;
import com.novaimod.world.ApocalypseEventManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 1) Корректирует точность стрел скелетов в зависимости от биома, черты характера и освещения.
 * 2) Во время 5-минутного испытания: если игрок стоит или убегает СПИНОЙ к лучникам — 90% их выстрелов попадают точно в цель!
 * 3) Обрабатывает спецрежимы стрел УБЕРГАНА (Пулемёт, Снайперский, Аннигиляция со взрывом самого моба).
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin extends Entity {
	@Unique
	private static final String TAG_TRIAL_BACKSHOT = "novai_trial_backshot";

	protected ProjectileEntityMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Shadow
	@Nullable
	public abstract Entity getOwner();

	@ModifyVariable(method = "setVelocity(DDDFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float novai$adjustSkeletonArrowDivergence(float uncertainty) {
		Entity owner = this.getOwner();
		if (owner instanceof AbstractSkeletonEntity && owner instanceof EvolvedMobAccess access) {
			float modified = uncertainty;
			if ("desert_skeleton".equals(access.novai$getBiomeTag())) {
				modified *= 0.50F;
			}
			if (access.novai$getTrait() == EvolvedMobAccess.MobTrait.SNIPER) {
				modified *= 0.58F;
			}
			if (access.novai$isNearTorchLight()) {
				modified *= 1.18F;
			}
			return modified;
		}
		return uncertainty;
	}

	@Inject(method = "setVelocity(DDDFF)V", at = @At("TAIL"))
	private void novai$trialBackturnedPrecisionShot(
			double x,
			double y,
			double z,
			float power,
			float uncertainty,
			CallbackInfo ci
	) {
		if (this.getWorld().isClient() || !ApocalypseEventManager.isActiveForWorld(this.getWorld())) {
			return;
		}
		Entity owner = this.getOwner();
		if (!(owner instanceof MobEntity archer) || !(archer.getTarget() instanceof ServerPlayerEntity targetPlayer)) {
			return;
		}
		if (!targetPlayer.isAlive() || targetPlayer.isSpectator()) {
			return;
		}
		if (!novai$isPlayerBackTurnedTo(targetPlayer, archer)) {
			return;
		}

		// Если игрок стоит/убегает спиной к лучнику во время испытания — 90% попаданий!
		if (archer.getRandom().nextFloat() < 0.90F) {
			this.addCommandTag(TAG_TRIAL_BACKSHOT);
			double arrowSpeed = Math.max(3.20D, power);
			Vec3d start = this.getPos();
			double moveX = MathHelper.clamp(targetPlayer.getX() - targetPlayer.prevX, -0.8D, 0.8D);
			double moveZ = MathHelper.clamp(targetPlayer.getZ() - targetPlayer.prevZ, -0.8D, 0.8D);
			double dist = start.distanceTo(targetPlayer.getPos());
			double flightTicks = Math.min(28.0D, dist / arrowSpeed);

			Vec3d aimPoint = new Vec3d(
					targetPlayer.getX() + moveX * flightTicks,
					targetPlayer.getBodyY(0.55D),
					targetPlayer.getZ() + moveZ * flightTicks
			);
			Vec3d diff = aimPoint.subtract(start);
			double horizDist = Math.hypot(diff.x, diff.z);
			// Точная компенсация гравитации стрелы (0.05/тик) по времени подлёта
			double gravComp = 0.5D * 0.05D * flightTicks * flightTicks;
			Vec3d launchDir = new Vec3d(diff.x, diff.y + gravComp * 0.55D + horizDist * 0.015D, diff.z);
			if (launchDir.lengthSquared() > 1.0E-4D) {
				Vec3d vel = launchDir.normalize().multiply(arrowSpeed);
				this.setVelocity(vel);
				novai$syncArrowRotation(vel);
			}
		} else {
			// Оставшиеся 10% гарантированно промахиваются чуть в сторону
			Vec3d cur = this.getVelocity();
			double side = archer.getRandom().nextBoolean() ? 0.34D : -0.34D;
			Vec3d missed = new Vec3d(cur.x - cur.z * side, cur.y, cur.z + cur.x * side);
			this.setVelocity(missed);
			novai$syncArrowRotation(missed);
		}
	}

	@Inject(method = "onCollision", at = @At("HEAD"))
	private void novai$beforeUberGunHit(HitResult hitResult, CallbackInfo ci) {
		if (this.getWorld().isClient()) {
			return;
		}
		if (this.getCommandTags().contains("novai_uber_mg") && hitResult instanceof EntityHitResult entityHit) {
			entityHit.getEntity().timeUntilRegen = 0;
		}
	}

	@Inject(method = "onCollision", at = @At("TAIL"))
	private void novai$afterUberGunHit(HitResult hitResult, CallbackInfo ci) {
		if (this.getWorld().isClient() || hitResult.getType() == HitResult.Type.MISS) {
			return;
		}
		if (!this.getCommandTags().remove("novai_uber_annihilate")) {
			return;
		}

		if (hitResult instanceof EntityHitResult entityHit) {
			Entity victim = entityHit.getEntity();
			victim.timeUntilRegen = 0;
			double ex = victim.getX();
			double ey = victim.getBodyY(0.5D);
			double ez = victim.getZ();

			if (this.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, ex, ey, ez, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				sw.spawnParticles(ParticleTypes.FLAME, ex, ey, ez, 32, 0.35D, 0.45D, 0.35D, 0.14D);
				sw.spawnParticles(ParticleTypes.LAVA, ex, ey, ez, 14, 0.25D, 0.35D, 0.25D, 0.05D);
			}

			this.getWorld().createExplosion(
					this,
					ex,
					ey,
					ez,
					4.0F,
					World.ExplosionSourceType.TNT
			);
		} else {
			Vec3d hitPos = hitResult.getPos();
			this.getWorld().createExplosion(
					this,
					hitPos.x,
					hitPos.y,
					hitPos.z,
					3.6F,
					World.ExplosionSourceType.TNT
			);
		}

		if (!this.isRemoved()) {
			this.discard();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void novai$uberGunTrailAndTrialBackshotGuidance(CallbackInfo ci) {
		if (!(this.getWorld() instanceof ServerWorld sw) || this.isOnGround() || this.getVelocity().lengthSquared() < 0.25D) {
			return;
		}

		// Плавная доводка стрелы в спину игрока (для 90% точности по убегающему/стоящему спиной игроку во время испытания!)
		if (this.getCommandTags().contains(TAG_TRIAL_BACKSHOT) && this.age <= 35) {
			Entity owner = this.getOwner();
			if (owner instanceof MobEntity archer
					&& archer.getTarget() instanceof ServerPlayerEntity targetPlayer
					&& targetPlayer.isAlive()
					&& !targetPlayer.isSpectator()
					&& novai$isPlayerBackTurnedTo(targetPlayer, archer)
					&& this.squaredDistanceTo(targetPlayer) <= 44.0D * 44.0D) {
				Vec3d curVel = this.getVelocity();
				double speed = Math.max(2.9D, curVel.length());
				Vec3d targetCenter = new Vec3d(targetPlayer.getX(), targetPlayer.getBodyY(0.55D), targetPlayer.getZ());
				Vec3d toTarget = targetCenter.subtract(this.getPos());
				if (toTarget.lengthSquared() > 0.04D) {
					Vec3d desired = toTarget.normalize().multiply(speed).add(0.0D, 0.042D, 0.0D);
					Vec3d steered = curVel.lerp(desired, 0.38D);
					this.setVelocity(steered);
					novai$syncArrowRotation(steered);
				}
			}
		}

		if (this.getCommandTags().contains("novai_uber_sniper")) {
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 2, 0.02D, 0.02D, 0.02D, 0.0D);
		} else if (this.getCommandTags().contains("novai_uber_annihilate")) {
			sw.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.03D, 0.03D, 0.03D, 0.0D);
		}
	}

	@Unique
	private static boolean novai$isPlayerBackTurnedTo(ServerPlayerEntity player, Entity archer) {
		Vec3d toArcher = new Vec3d(archer.getX() - player.getX(), 0.0D, archer.getZ() - player.getZ());
		if (toArcher.lengthSquared() < 0.04D) {
			return false;
		}
		Vec3d playerLook = Vec3d.fromPolar(0.0F, player.getYaw());
		Vec3d playerLookHoriz = new Vec3d(playerLook.x, 0.0D, playerLook.z);
		if (playerLookHoriz.lengthSquared() < 1.0E-4D) {
			return false;
		}
		// dot < -0.15 означает, что игрок стоит/бежит спиной к лучнику!
		return playerLookHoriz.normalize().dotProduct(toArcher.normalize()) < -0.15D;
	}

	@Unique
	private void novai$syncArrowRotation(Vec3d v) {
		double h = Math.hypot(v.x, v.z);
		this.setYaw((float) (MathHelper.atan2(v.x, v.z) * (180.0D / Math.PI)));
		this.setPitch((float) (MathHelper.atan2(v.y, h) * (180.0D / Math.PI)));
		this.prevYaw = this.getYaw();
		this.prevPitch = this.getPitch();
	}
}
