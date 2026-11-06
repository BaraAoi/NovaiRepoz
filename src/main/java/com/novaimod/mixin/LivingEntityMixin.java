package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Защита от ваншота крипером по порогу здоровья:
 * - Сам расчёт урона остаётся обычным (если взрыв слабый/далёкий — наносится обычный урон).
 * - Если у игрока полное ХП или не хватает не более 1 сердечка (health >= maxHealth - 2.0),
 *   летальный урон от взрыва крипера ограничивается так, чтобы у игрока осталось ровно 0.5 сердечка (1.0 HP).
 * - Если у игрока меньше этого порога (не хватает больше 1 сердечка), взрыв при достаточном уроне убивает как обычно.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
	private void novai$creeperAntiOneshotThreshold(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof PlayerEntity player)) {
			return;
		}
		boolean fromCreeper = (source.getAttacker() instanceof CreeperEntity)
				|| (source.getSource() instanceof CreeperEntity);
		if (!fromCreeper) {
			return;
		}

		float currentHealth = player.getHealth();
		float threshold = player.getMaxHealth() - 2.01F; // Полное ХП или не хватает до 1 сердечка включительно (18..20 HP)
		if (currentHealth < threshold) {
			// Ниже порога — если урона хватает, ваншотает как обычно
			return;
		}

		float finalDamage = cir.getReturnValue();
		float totalPool = currentHealth + player.getAbsorptionAmount();
		// Если обычный урон не опускает здоровье ниже 0.5 сердечка (1.0 HP) — не трогаем урон!
		if (totalPool - finalDamage >= 1.0F) {
			return;
		}

		// Ограничиваем урон так, чтобы осталось ровно полсердечка (1.0 HP)
		float cappedDamage = Math.max(0.0F, totalPool - 1.0F);
		cir.setReturnValue(cappedDamage);
	}
}
