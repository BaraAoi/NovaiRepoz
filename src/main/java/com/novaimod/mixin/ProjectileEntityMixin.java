package com.novaimod.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.novaimod.ai.evolution.EvolvedMobAccess;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

/**
 * Корректирует точность стрел скелетов в зависимости от биома (в пустыне стреляют точнее),
 * черты характера (снайпер) и ослепления ярким светом факелов.
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {
	@Shadow
	@Nullable
	public abstract Entity getOwner();

	@ModifyVariable(method = "setVelocity(DDDFF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float novai$adjustSkeletonArrowDivergence(float uncertainty) {
		Entity owner = this.getOwner();
		if (owner instanceof AbstractSkeletonEntity && owner instanceof EvolvedMobAccess access) {
			float modified = uncertainty;
			if ("desert_skeleton".equals(access.novai$getBiomeTag())) {
				modified *= 0.50F; // В пустыне скелеты стреляют ощутимо точнее!
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
}
