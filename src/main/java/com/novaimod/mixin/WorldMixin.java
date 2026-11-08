package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.data.WorldVirginLedger;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Перехватывает самое ПЕРВОЕ изменение любого блока в мире и сохраняет его девственное состояние
 * в WorldVirginLedger, чтобы «Time of the World (Made in Heaven)» мог перезапустить мир
 * в абсолютно девственно чистое изначальное состояние — даже если блоки были сломаны или взорваны задолго до нажатия!
 */
@Mixin(World.class)
public abstract class WorldMixin {
	@Inject(
			method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z",
			at = @At("HEAD")
	)
	private void novai$recordVirginBlockStateOnFirstChange(
			BlockPos pos,
			BlockState state,
			int flags,
			int maxUpdateDepth,
			CallbackInfoReturnable<Boolean> cir
	) {
		if ((Object) this instanceof ServerWorld sw && !WorldVirginLedger.isBypassActive()) {
			WorldVirginLedger.recordBlockBeforeFirstChange(sw, pos, state);
		}
	}
}
