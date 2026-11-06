package com.novaimod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.entity.PartyDollEntity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Синхронный визуальный танец под мем с прыгающей Toyota Yaris (135 BPM) для ВСЕХ сущностей
 * (игроков, всех мобов, жителей, животных и кастомных NPC) в радиусе 20 блоков от активной куклы:
 * - Ритмично сплющивает вниз-вширь и возвращает в нормальный размер ровно под каждый удар бочки 135 BPM!
 * - Крутит 3D-модель вокруг оси Y ТОЛЬКО ВНЕШНЕ (в MatrixStack рендера),
 *   никак не затрагивая камеру (ни от 1-го лица, ни в F5) и управление игроком!
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
	@Inject(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V"
			)
	)
	private void novai$applyYarisSquishAndVisualSpinToAllInRadius(
			T entity,
			float entityYaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light,
			CallbackInfo ci
	) {
		if (!PartyDollEntity.isUnderActiveDollAura(entity)) {
			return;
		}

		// Синхронизируем фазу от мирового времени под точный бит 135 BPM — все плющатся одновременно с куклой!
		float worldTime = (entity.getWorld().getTime() % 240000L) + tickDelta;
		float wave = 0.5F - 0.5F * MathHelper.cos(worldTime * PartyDollEntity.YARIS_BEAT_RAD_PER_TICK);
		float squish = wave * wave * (3.0F - 2.0F * wave);

		float scaleY = MathHelper.lerp(squish, 1.0F, 0.25F);
		float scaleXZ = MathHelper.lerp(squish, 1.0F, 1.40F);
		float visualSpinDeg = (worldTime * 20.25F) % 360.0F;

		matrices.scale(scaleXZ, scaleY, scaleXZ);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(visualSpinDeg));
	}
}
