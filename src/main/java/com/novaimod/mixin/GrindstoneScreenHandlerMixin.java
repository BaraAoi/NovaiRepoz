package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.equipment.MfoArmorSetManager;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;

/**
 * Запрещает снимать скрытые зачарования с заблокированной брони на точиле.
 */
@Mixin(GrindstoneScreenHandler.class)
public abstract class GrindstoneScreenHandlerMixin {
	@Inject(method = "getOutputStack", at = @At("HEAD"), cancellable = true)
	private void novai$blockGrindstoneDisenchant(ItemStack firstInput, ItemStack secondInput, CallbackInfoReturnable<ItemStack> cir) {
		if (MfoArmorSetManager.isEnchantLocked(firstInput) || MfoArmorSetManager.isEnchantLocked(secondInput)) {
			cir.setReturnValue(ItemStack.EMPTY);
		}
	}
}
