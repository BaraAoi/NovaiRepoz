package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.entity.WhiteCapEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;

/**
 * Серебряно-алхимическая печать Клана Белых Колпаков:
 * поражённый ею (или запертый в карантинной клетке Цитадели) эндермен НЕ МОЖЕТ сбежать телепортацией!
 */
@Mixin(EndermanEntity.class)
public abstract class EndermanEntityMixin {
	@Inject(method = "teleportTo(DDD)Z", at = @At("HEAD"), cancellable = true)
	private void novai$blockSealedEndermanTeleport(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (self.getCommandTags().contains(WhiteCapEntity.TAG_SEALED_ENDER)) {
			cir.setReturnValue(false);
		}
	}
}
