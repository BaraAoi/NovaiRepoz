package com.novaimod.client.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;

/**
 * Полностью устраняет любые дёргания и остаточные анимации сущностей (включая крылья, шею и хвост Дракона Края!),
 * облаков и осадков во время вечной остановки времени от «Хроно-Скипетра».
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	@Shadow
	@Nullable
	private ClientWorld world;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void novai$freezeSkyAndWeatherAnimationOnTimeStop(CallbackInfo ci) {
		if (this.world != null && this.world.getTickManager().isFrozen()) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "renderEntity", at = @At("HEAD"), argsOnly = true)
	private float novai$lockEntityTickDeltaToZeroWhenFrozen(
			float tickDelta,
			Entity entity,
			double cameraX,
			double cameraY,
			double cameraZ,
			float originalTickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers
	) {
		if (this.world != null && this.world.getTickManager().isFrozen() && this.world.getTickManager().shouldSkipTick(entity)) {
			return 0.0F;
		}
		return tickDelta;
	}

	@Inject(method = "renderEntity", at = @At("HEAD"))
	private void novai$pinDragonAndLivingPoseWhenFrozen(
			Entity entity,
			double cameraX,
			double cameraY,
			double cameraZ,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			CallbackInfo ci
	) {
		if (this.world == null || !this.world.getTickManager().isFrozen() || !this.world.getTickManager().shouldSkipTick(entity)) {
			return;
		}

		entity.lastRenderX = entity.getX();
		entity.lastRenderY = entity.getY();
		entity.lastRenderZ = entity.getZ();
		entity.prevX = entity.getX();
		entity.prevY = entity.getY();
		entity.prevZ = entity.getZ();
		entity.prevYaw = entity.getYaw();
		entity.prevPitch = entity.getPitch();

		if (entity instanceof LivingEntity living) {
			living.prevBodyYaw = living.bodyYaw;
			living.prevHeadYaw = living.headYaw;
		}
		if (entity instanceof EnderDragonEntity dragon) {
			dragon.prevWingPosition = dragon.wingPosition;
		}
	}
}
