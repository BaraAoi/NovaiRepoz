package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.ai.evolution.MobEvolutionManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 1) Защита от ваншота крипером по порогу здоровья (полное ХП или без <= 1 сердца -> оставляет 0.5 сердца).
 * 2) Адаптация мобов к сильной броне игрока (прицел в незащищённые части или тактические эффекты).
 * 3) Восприятие мобов: Скрытность в темноте, Чувствительность к свету и Потеря следа (дождь, вода, густая листва).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
	private void novai$modifyDamageRules(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof PlayerEntity player)) {
			return;
		}

		boolean fromCreeper = (source.getAttacker() instanceof CreeperEntity)
				|| (source.getSource() instanceof CreeperEntity);

		if (fromCreeper) {
			float currentHealth = player.getHealth();
			float threshold = player.getMaxHealth() - 2.01F; // Полное ХП или не хватает до 1 сердечка включительно
			if (currentHealth < threshold) {
				return;
			}

			float finalDamage = cir.getReturnValue();
			float totalPool = currentHealth + player.getAbsorptionAmount();
			if (totalPool - finalDamage >= 1.0F) {
				return;
			}

			float cappedDamage = Math.max(0.0F, totalPool - 1.0F);
			cir.setReturnValue(cappedDamage);
			return;
		}

		// Адаптация развитых мобов к сильной броне игрока
		if (player instanceof ServerPlayerEntity serverPlayer) {
			float adjusted = MobEvolutionManager.adaptAttackAgainstArmoredPlayer(serverPlayer, source, cir.getReturnValue());
			if (adjusted != cir.getReturnValue()) {
				cir.setReturnValue(adjusted);
			}
		}
	}

	@Inject(method = "getAttackDistanceScalingFactor", at = @At("RETURN"), cancellable = true)
	private void novai$perceptionStealthLightAndTrail(Entity detector, CallbackInfoReturnable<Double> cir) {
		if ((Object) this instanceof PlayerEntity player) {
			double modified = MobEvolutionManager.adjustVisualDetectionFactor(player, detector, cir.getReturnValue());
			cir.setReturnValue(modified);
		}
	}
}
