package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.world.TimeOfTheWorldManager;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Перехватывает любые изменения блоков во время ускорения Вселенной «Made in Heaven: Time of the World»,
 * чтобы в момент Перезапуска Мира (Сингулярности) каждый сломанный, поставленный, взорванный или выросший блок
 * вернулся в точности на своё исходное место!
 */
@Mixin(World.class)
public abstract class WorldMixin {
	@Inject(
			method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
			at = @At("HEAD")
	)
	private void novai$snapshotBlockBeforeMadeInHeavenChange(
			BlockPos pos,
			BlockState state,
			int flags,
			int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof ServerWorld sw && TimeOfTheWorldManager.isRecordingChanges()) {
			TimeOfTheWorldManager.recordBlockBeforeChange(sw, pos);
		}
	}
}
