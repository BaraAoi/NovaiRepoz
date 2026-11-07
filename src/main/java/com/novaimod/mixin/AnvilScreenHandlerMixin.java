package com.novaimod.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.equipment.MfoArmorSetManager;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;

/**
 * Запрещает отдельно зачаровывать, объединять или модифицировать заблокированную броню (броню МФО и проклятую броню) на наковальне.
 */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin extends ForgingScreenHandler {
	@Shadow
	@Final
	private Property levelCost;

	protected AnvilScreenHandlerMixin(
			@Nullable ScreenHandlerType<?> type,
			int syncId,
			PlayerInventory playerInventory,
			ScreenHandlerContext context
	) {
		super(type, syncId, playerInventory, context);
	}

	@Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
	private void novai$blockAnvilEnchanting(CallbackInfo ci) {
		ItemStack left = this.input.getStack(0);
		ItemStack right = this.input.getStack(1);
		if (MfoArmorSetManager.isEnchantLocked(left) || MfoArmorSetManager.isEnchantLocked(right)) {
			this.output.setStack(0, ItemStack.EMPTY);
			this.levelCost.set(0);
			ci.cancel();
		}
	}
}
