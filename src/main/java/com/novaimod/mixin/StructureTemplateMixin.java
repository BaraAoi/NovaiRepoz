package com.novaimod.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.data.WorldVirginLedger;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.world.ServerWorldAccess;

/**
 * Помечает всех обитателей ванильных структурных шаблонов (жителей и железных големов в деревнях,
 * кошек, иглу, бастионов, городов Края, лесных особняков и аванпостов) как коренных жителей Девственного Мира!
 */
@Mixin(StructureTemplate.class)
public class StructureTemplateMixin {
	@Inject(method = "getEntity", at = @At("RETURN"))
	private static void novai$markStructureTemplateEntityVirgin(
			ServerWorldAccess world,
			NbtCompound nbt,
			CallbackInfoReturnable<Optional<Entity>> cir
	) {
		cir.getReturnValue().ifPresent(entity -> {
			if (WorldVirginLedger.isVirginTrackableEntity(entity)) {
				entity.removeCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
				entity.addCommandTag(WorldVirginLedger.TAG_VIRGIN_NATIVE);
			}
		});
	}
}
