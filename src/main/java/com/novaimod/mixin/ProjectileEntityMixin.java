package com.novaimod.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.ai.evolution.EvolvedMobAccess;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 1) Корректирует точность стрел скелетов в зависимости от биома, черты характера и освещения.
 * 2) Обрабатывает спецрежимы стрел УБЕРГАНА (Пулемёт без кд неуязвимости, Снайперский со следом, Аннигиляция со взрывом местности).
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin extends Entity {
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

	@Inject(method = "onCollision", at = @At("HEAD"))
	private void novai$beforeUberGunHit(HitResult hitResult, CallbackInfo ci) {
		if (this.getWorld().isClient()) {
			return;
		}
		// В режиме Пулемёта (выстрел каждые 0.2 сек = 4 тика) снимаем тики неуязвимости цели, чтобы попадала каждая стрела очереди!
		if (this.getCommandTags().contains("novai_uber_mg") && hitResult instanceof EntityHitResult entityHit) {
			entityHit.getEntity().timeUntilRegen = 0;
		}
	}

	@Inject(method = "onCollision", at = @At("TAIL"))
	private void novai$afterUberGunHit(HitResult hitResult, CallbackInfo ci) {
		if (this.getWorld().isClient() || hitResult.getType() == HitResult.Type.MISS) {
			return;
		}
		// В режиме Аннигиляции стрела при попадании куда угодно (в блок или существо) взрывает местность!
		if (this.getCommandTags().contains("novai_uber_annihilate") && !this.isRemoved()) {
			Vec3d hitPos = hitResult.getPos();
			this.getWorld().createExplosion(
					this.getOwner(),
					hitPos.x,
					hitPos.y,
					hitPos.z,
					3.6F,
					World.ExplosionSourceType.TNT
			);
			this.discard();
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void novai$uberGunArrowTrail(CallbackInfo ci) {
		if (!(this.getWorld() instanceof ServerWorld sw) || this.isOnGround() || this.getVelocity().lengthSquared() < 0.25D) {
			return;
		}
		if (this.getCommandTags().contains("novai_uber_sniper")) {
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 2, 0.02D, 0.02D, 0.02D, 0.0D);
		} else if (this.getCommandTags().contains("novai_uber_annihilate")) {
			sw.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY(), this.getZ(), 1, 0.03D, 0.03D, 0.03D, 0.0D);
		}
	}
}
