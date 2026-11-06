package com.novaimod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.entity.PartyDollEntity;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Чисто визуальный эффект танца вокруг активной женской куклы:
 * - Сплющивает модель вниз и вширь и возвращает обратно в нормальный размер (как в меме «Приора / Ярис»).
 * - Плавно крутит 3D-модель вокруг оси Y ТОЛЬКО ВНЕШНЕ (в MatrixStack рендера),
 *   абсолютно НЕ затрагивая угол камеры (yaw/pitch), прицел или управление движением игрока!
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
	@Inject(method = "setupTransforms", at = @At("HEAD"))
	private void novai$applyPartyDollSquishAndVisualSpin(
			T entity,
			MatrixStack matrices,
			float animationProgress,
			float bodyYaw,
			float tickDelta,
			float scale,
			CallbackInfo ci
	) {
		if (!PartyDollEntity.isUnderActiveDollAura(entity)) {
			return;
		}

		float time = entity.age + tickDelta;
		// Ритмичное сплющивание «Приора / Ярис»: норм размер (1.0) -> сжался (0.26) -> вернулся в норм размер (1.0)!
		float wave = 0.5F - 0.5F * MathHelper.cos(time * 0.48F);
		float squish = wave * wave * (3.0F - 2.0F * wave);
		float scaleY = MathHelper.lerp(squish, 1.0F, 0.26F);
		float scaleXZ = MathHelper.lerp(squish, 1.0F, 1.38F);

		// Внешнее вращение модели (на камеру игрока никак не влияет!)
		float visualSpinDeg = (time * 18.0F) % 360.0F;

		matrices.scale(scaleXZ, scaleY, scaleXZ);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(visualSpinDeg));
	}
}
