package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.ai.goal.BowAttackGoal;
import net.minecraft.entity.mob.HostileEntity;

/**
 * Делает паузу перезарядки лука чуть короче без поломки ванильной анимации натяжения тетивы.
 */
@Mixin(BowAttackGoal.class)
public abstract class BowAttackGoalMixin {
	@Shadow
	@Final
	private HostileEntity actor;

	@Shadow
	private int cooldown;

	@Inject(method = "tick", at = @At("TAIL"))
	private void novai$subtleFasterReload(CallbackInfo ci) {
		if (this.cooldown > 3 && this.actor.age % 4 == 0) {
			this.cooldown--;
		}
	}
}
