package com.novaimod.client.hud;

import com.novaimod.client.ClientAccountData;
import com.novaimod.client.TimeOfTheWorldClientState;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Отрисовывает:
 * 1) Виртуальный Счёт (без отображения долга на экране, чтобы не мозолить глаза игроку) и таймер СИЗО при аресте.
 * 2) Мимолётный силуэт и тень в углу экрана, когда за спиной или сбоку подкрадывается моб.
 */
public class AccountHudOverlay implements HudRenderCallback {
	private static final long SHADOW_DURATION_MS = 460L;

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}

		// Полноэкранный эффект Сингулярности и световых потоков «Made in Heaven: Time of the World» (работает даже при F1!)
		renderMadeInHeavenOverlay(context, client.textRenderer);

		if (client.options.hudHidden) {
			return;
		}

		renderPeripheralShadowCue(context);

		TextRenderer tr = client.textRenderer;
		long balance = ClientAccountData.getBalance();
		int sizoSec = ClientAccountData.getSizoSecondsLeft();

		Text balanceText = Text.literal("Счёт: ").formatted(Formatting.GRAY)
				.append(Text.literal(balance + " ₽").formatted(Formatting.GREEN, Formatting.BOLD));

		int width = tr.getWidth(balanceText) + 14;
		int height = sizoSec > 0 ? 28 : 17;

		Text sizoText = null;
		if (sizoSec > 0) {
			int mins = sizoSec / 60;
			int secs = sizoSec % 60;
			String fmt = String.format("%02d:%02d", mins, secs);
			sizoText = Text.literal("СИЗО: " + fmt).formatted(Formatting.RED, Formatting.BOLD);
			width = Math.max(width, tr.getWidth(sizoText) + 14);
		}

		int x = 8;
		int y = 8;

		context.fill(x, y, x + width, y + height, 0xAA101218);
		context.drawBorder(x, y, width, height, 0xFF3A8F52);

		context.drawText(tr, balanceText, x + 6, y + 5, 0xFFFFFF, true);
		if (sizoText != null) {
			context.drawText(tr, sizoText, x + 6, y + 16, 0xFF5555, true);
		}
	}

	private void renderPeripheralShadowCue(DrawContext context) {
		long elapsed = System.currentTimeMillis() - ClientAccountData.getShadowCueStartMs();
		if (elapsed < 0L || elapsed >= SHADOW_DURATION_MS) {
			return;
		}

		float progress = (float) elapsed / (float) SHADOW_DURATION_MS; // 0..1
		// Быстро проявляется и ускользает за край экрана
		float envelope = (float) Math.sin(progress * Math.PI);
		int maxAlpha = Math.round(135.0F * envelope * ClientAccountData.getShadowCueIntensity());
		if (maxAlpha <= 4) {
			return;
		}

		int sw = context.getScaledWindowWidth();
		int sh = context.getScaledWindowHeight();
		boolean left = ClientAccountData.isShadowCueLeftSide();

		// Скользящий к краю экрана смазанный тёмный силуэт (голова + плечи + теневой шлейф)
		int drift = Math.round(progress * 28.0F);
		int baseX = left ? (14 - drift) : (sw - 42 + drift);
		int baseY = sh / 2 + 12 + Math.round((progress - 0.5F) * 14.0F);

		int coreColor = (maxAlpha << 24) | 0x050608;
		int softColor = ((maxAlpha / 2) << 24) | 0x080A0E;

		// Краевое затенение угла экрана
		if (left) {
			context.fill(0, 0, 26, sh, ((maxAlpha / 3) << 24) | 0x030406);
		} else {
			context.fill(sw - 26, 0, sw, sh, ((maxAlpha / 3) << 24) | 0x030406);
		}

		// Силуэт головы и сгорбленной фигуры, мелькающий боковым зрением
		context.fill(baseX + 6, baseY - 34, baseX + 22, baseY - 18, coreColor);
		context.fill(baseX + 2, baseY - 18, baseX + 26, baseY + 26, coreColor);
		context.fill(baseX - 4, baseY - 22, baseX + 32, baseY + 32, softColor);
	}

	private void renderMadeInHeavenOverlay(DrawContext context, TextRenderer tr) {
		int sw = context.getScaledWindowWidth();
		int sh = context.getScaledWindowHeight();

		if (TimeOfTheWorldClientState.isAccelerating()) {
			float prog = TimeOfTheWorldClientState.getNormalizedProgress();
			if (prog > 0.30F) {
				// Тонкие золотисто-бирюзовые хроно-полосы по краям экрана (центр неба полностью чист!)
				float intensity = (prog - 0.30F) / 0.70F;
				int borderAlpha = MathHelper.clamp((int) (intensity * 95.0F), 0, 110);
				int goldEdge = (borderAlpha << 24) | 0xFFE688;
				int cyanEdge = ((borderAlpha * 3 / 4) << 24) | 0x70F0FF;

				int band = 4 + (int) (intensity * 12.0F);
				context.fill(0, 0, sw, band, goldEdge);
				context.fill(0, sh - band, sw, sh, goldEdge);
				context.fill(0, 0, band, sh, cyanEdge);
				context.fill(sw - band, 0, sw, sh, cyanEdge);

				// Мерцающие скоростные штрихи на периферии экрана
				long ms = Util.getMeasuringTimeMs();
				int streaks = 6 + (int) (intensity * 14.0F);
				for (int i = 0; i < streaks; i++) {
					int hash = (int) ((ms / 28L + i * 73L) * 2654435761L);
					int sy = Math.floorMod(hash, Math.max(1, sh - 4));
					int len = 18 + Math.floorMod(hash >> 8, 46);
					boolean leftSide = (i % 2 == 0);
					int sx0 = leftSide ? 0 : (sw - len);
					int col = (i % 3 == 0) ? goldEdge : cyanEdge;
					context.fill(sx0, sy, sx0 + len, sy + 1, col);
				}
			}
		}

		float flashAlpha = TimeOfTheWorldClientState.getSingularityFlashAlpha();
		if (flashAlpha > 0.005F) {
			int a = MathHelper.clamp(Math.round(flashAlpha * 255.0F), 0, 255);
			int whiteGold = (a << 24) | 0xFFFDF2;
			context.fill(0, 0, sw, sh, whiteGold);
		}

		if (TimeOfTheWorldClientState.isInPostResetBannerWindow()) {
			Text title = Text.literal("✦ MADE IN HEAVEN — МИР ПЕРЕЗАПУЩЕН ✦").formatted(Formatting.GOLD, Formatting.BOLD);
			int tw = tr.getWidth(title);
			context.drawText(tr, title, (sw - tw) / 2, sh / 4, 0xFFF5C0, true);
		}
	}
}
