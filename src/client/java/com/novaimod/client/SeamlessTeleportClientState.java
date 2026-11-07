package com.novaimod.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

/**
 * Клиентское состояние БЕСШОВНОЙ телепортации между измерениями:
 * - Подавляет ванильный экран «Загрузка территории» (DownloadingTerrainScreen) и разблокировку курсора мыши.
 * - Отрисовывает полупрозрачную пространственную вспышку по краям экрана поверх живого 3D-рендера нового измерения!
 */
public final class SeamlessTeleportClientState implements HudRenderCallback {
	private static volatile long seamlessExpiresAtMs = 0L;
	private static volatile long flashStartMs = 0L;
	private static volatile long flashDurationMs = 450L;
	private static volatile int riftRgb = 0x7A42FF;

	private SeamlessTeleportClientState() {
	}

	public static final SeamlessTeleportClientState HUD_OVERLAY = new SeamlessTeleportClientState();

	public static void onSeamlessTeleportPacket(String dimensionPath, boolean crossDimension) {
		long now = System.currentTimeMillis();
		// Окно подавления экрана загрузки территории (2.5 секунды покрывает приход PlayerRespawnS2CPacket и первых чанков)
		if (crossDimension) {
			seamlessExpiresAtMs = Math.max(seamlessExpiresAtMs, now + 2500L);
			flashDurationMs = 520L;
		} else {
			seamlessExpiresAtMs = Math.max(seamlessExpiresAtMs, now + 900L);
			flashDurationMs = 260L;
		}
		flashStartMs = now;

		riftRgb = switch (dimensionPath) {
			case "the_nether" -> 0xFF4D28;
			case "the_end" -> 0xB84DFF;
			default -> 0x3CE8FF;
		};
	}

	public static boolean isSeamlessActive() {
		return System.currentTimeMillis() < seamlessExpiresAtMs;
	}

	public static void ensureCursorLocked(MinecraftClient client) {
		if (client != null && client.currentScreen == null && !client.mouse.isCursorLocked()) {
			client.mouse.lockCursor();
		}
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		long elapsed = System.currentTimeMillis() - flashStartMs;
		if (elapsed < 0L || elapsed >= flashDurationMs) {
			return;
		}

		float progress = (float) elapsed / (float) flashDurationMs;
		float intensity = (1.0F - progress) * (1.0F - progress);

		int sw = context.getScaledWindowWidth();
		int sh = context.getScaledWindowHeight();

		// 1. Очень мягкая прозрачная вуаль вспышки (3D-мир под ней остаётся полностью видимым!)
		int centerAlpha = MathHelper.clamp(Math.round(intensity * 68.0F), 0, 90);
		if (centerAlpha > 0) {
			context.fill(0, 0, sw, sh, (centerAlpha << 24) | (riftRgb & 0x00FFFFFF));
		}

		// 2. Сияющая рамка пространственного разлома по краям экрана
		int edgeAlpha = MathHelper.clamp(Math.round(intensity * 155.0F), 0, 185);
		if (edgeAlpha > 0) {
			int edgeColor = (edgeAlpha << 24) | (riftRgb & 0x00FFFFFF);
			int band = Math.max(8, Math.round(Math.min(sw, sh) * 0.055F * (0.5F + 0.5F * intensity)));
			context.fill(0, 0, sw, band, edgeColor);
			context.fill(0, sh - band, sw, sh, edgeColor);
			context.fill(0, band, band, sh - band, edgeColor);
			context.fill(sw - band, band, sw, sh - band, edgeColor);
		}
	}
}
