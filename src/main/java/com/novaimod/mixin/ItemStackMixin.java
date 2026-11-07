package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.equipment.MfoArmorSetManager;

import net.minecraft.item.ItemStack;

/**
 * Запрещает отдельное зачарование заблокированной брони (сетов МФО и проклятой брони испытания) на чародейском столе и командой /enchant.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "isEnchantable", at = @At("HEAD"), cancellable = true)
	private void novai$preventSeparateEnchanting(CallbackInfoReturnable<Boolean> cir) {
		if (MfoArmorSetManager.isEnchantLocked((ItemStack) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
