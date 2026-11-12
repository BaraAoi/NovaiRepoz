package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.ai.evolution.EvolvedMobAccess;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;

/**
 * Слегка ускоряет темп стрельбы скелетов и отмечает момент выстрела,
 * чтобы скелеты смещались между выстрелами (умная тактика против лучников).
 */
@Mixin(AbstractSkeletonEntity.class)
public abstract class AbstractSkeletonEntityMixin {
	@Inject(method = "getHardAttackInterval", at = @At("RETURN"), cancellable = true)
	private void novai$fasterHardBowInterval(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(14); // Вместо ванильных 20 тиков
	}

	@Inject(method = "getRegularAttackInterval", at = @At("RETURN"), cancellable = true)
	private void novai$fasterRegularBowInterval(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(28); // Вместо ванильных 40 тиков
	}

	@Inject(method = "shootAt", at = @At("TAIL"))
	private void novai$onArrowFired(LivingEntity target, float pullProgress, CallbackInfo ci) {
		if ((Object) this instanceof EvolvedMobAccess access) {
			access.novai$markShotFired();
		}
	}
}
