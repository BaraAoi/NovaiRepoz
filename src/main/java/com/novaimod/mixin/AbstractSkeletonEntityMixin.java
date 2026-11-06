package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.mob.AbstractSkeletonEntity;

/**
 * Слегка ускоряет темп стрельбы скелетов так, чтобы это ощущалось в бою,
 * но выглядело полностью естественно и незаметно по анимации.
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
}
