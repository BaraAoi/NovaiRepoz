package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.ai.SmartCreeperIgniteGoal;
import com.novaimod.ai.WallhackCreeperTargetGoal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Улучшает ИИ ванильного крипера:
 * - Радиус обнаружения 64 блока, рентгеновское зрение сквозь блоки (canSee = true для игроков).
 * - Подрыв сквозь стены и пробитие преград (SmartCreeperIgniteGoal), при этом explosionRadius (сила взрыва) НЕ меняется!
 * - Ускоренное преследование и умное всплытие в воде за игроком.
 */
@Mixin(CreeperEntity.class)
public abstract class CreeperEntityMixin extends HostileEntity {
	protected CreeperEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "createCreeperAttributes", at = @At("RETURN"), cancellable = true)
	private static void novai$enhanceCreeperAttributes(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
		cir.setReturnValue(
				cir.getReturnValue()
						.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0D)
						.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.285D)
		);
	}

	@Inject(method = "initGoals", at = @At("TAIL"))
	private void novai$upgradeCreeperGoals(CallbackInfo ci) {
		CreeperEntity self = (CreeperEntity) (Object) this;
		this.goalSelector.clear(goal -> goal instanceof CreeperIgniteGoal);
		this.targetSelector.clear(goal -> goal instanceof ActiveTargetGoal<?>);

		this.goalSelector.add(2, new SmartCreeperIgniteGoal(self));
		this.targetSelector.add(1, new WallhackCreeperTargetGoal(self));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void novai$smartPursuitTick(CallbackInfo ci) {
		if (this.getWorld().isClient()) {
			return;
		}
		LivingEntity target = this.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		// Улучшенное движение в воде и прыжки через уступы при погоне
		if (this.isTouchingWater() && target.getY() >= this.getY() - 0.5D) {
			Vec3d vel = this.getVelocity();
			Vec3d dir = new Vec3d(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
			if (dir.lengthSquared() > 0.001D) {
				dir = dir.normalize().multiply(0.04D);
			}
			this.setVelocity(vel.x + dir.x, Math.min(0.18D, vel.y + 0.045D), vel.z + dir.z);
		}
	}

	@Override
	public boolean canSee(Entity entity) {
		if (entity instanceof PlayerEntity player && !player.isSpectator() && !player.isCreative()) {
			if (this.squaredDistanceTo(player) <= 64.0D * 64.0D) {
				return true;
			}
		}
		return super.canSee(entity);
	}
}
