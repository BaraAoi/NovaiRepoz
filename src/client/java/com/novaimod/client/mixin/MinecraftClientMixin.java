package com.novaimod.client.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.client.SeamlessTeleportClientState;
import com.novaimod.client.TimeOfTheWorldClientState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.DownloadingTerrainScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.world.ClientWorld;

/**
 * Обеспечивает полностью БЕСШОВНЫЙ переход между измерениями для «Ока Бескрайности»:
 * перехватывает открытие и промежуточную отрисовку экрана «Загрузка территории» (DownloadingTerrainScreen),
 * оставляя 3D-рендер мира и управление камерой непрерывными!
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Shadow
	@Nullable
	public Screen currentScreen;

	@Shadow
	@Nullable
	public ClientWorld world;

	@Shadow
	@Final
	public Mouse mouse;

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void novai$skipLoadingScreenDuringSeamlessTeleport(@Nullable Screen screen, CallbackInfo ci) {
		if (screen instanceof DownloadingTerrainScreen && SeamlessTeleportClientState.isSeamlessActive()) {
			if (this.currentScreen == null && !this.mouse.isCursorLocked()) {
				this.mouse.lockCursor();
			}
			ci.cancel();
		}
	}

	@Inject(method = "setScreenAndRender", at = @At("HEAD"), cancellable = true)
	private void novai$skipResetRenderFrameDuringSeamlessTeleport(Screen screen, CallbackInfo ci) {
		if (screen instanceof DownloadingTerrainScreen && SeamlessTeleportClientState.isSeamlessActive()) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void novai$keepSeamlessStateClean(CallbackInfo ci) {
		if (SeamlessTeleportClientState.isSeamlessActive()) {
			if (this.currentScreen instanceof DownloadingTerrainScreen) {
				this.currentScreen = null;
			}
			if (this.currentScreen == null && !this.mouse.isCursorLocked()) {
				this.mouse.lockCursor();
			}
		}
	}
	@Inject(method = "tick", at = @At("TAIL"))
	private void novai$applySmoothMadeInHeavenTimeAfterTick(CallbackInfo ci) {
		if (this.world == null) {
			return;
		}
		if (TimeOfTheWorldClientState.isAccelerating()) {
			this.world.setTimeOfDay(TimeOfTheWorldClientState.computeSmoothTimeOfDay());
		} else {
			Long restored = TimeOfTheWorldClientState.pollPendingRestoredTime();
			if (restored != null) {
				this.world.setTimeOfDay(restored);
			}
		}
	}
}
