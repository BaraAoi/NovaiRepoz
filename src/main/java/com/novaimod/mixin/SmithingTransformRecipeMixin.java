package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.equipment.MfoArmorSetManager;

import net.minecraft.item.ItemStack;
import net.minecraft.recipe.SmithingTransformRecipe;
import net.minecraft.recipe.input.SmithingRecipeInput;
import net.minecraft.registry.RegistryWrapper;

/**
 * Сохраняет бонусы и характеристики сетов МФО (Мечник и Лучник) при улучшении алмазной брони до незеритовой на кузнечном столе!
 */
@Mixin(SmithingTransformRecipe.class)
public abstract class SmithingTransformRecipeMixin {
	@Inject(method = "craft(Lnet/minecraft/recipe/input/SmithingRecipeInput;Lnet/minecraft/registry/RegistryWrapper$WrapperLookup;)Lnet/minecraft/item/ItemStack;", at = @At("RETURN"))
	private void novai$preserveMfoSetBonusesOnNetheriteUpgrade(
			SmithingRecipeInput input,
			RegistryWrapper.WrapperLookup lookup,
			CallbackInfoReturnable<ItemStack> cir
	) {
		MfoArmorSetManager.onSmithingUpgrade(cir.getReturnValue());
	}
}
