package com.novaimod.client.hud;

import com.novaimod.client.ClientAccountData;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Отрисовывает:
 * 1) Виртуальный Счёт и Долг (плюс таймер СИЗО) сверху слева.
 * 2) Мимолётный силуэт и тень в углу экрана, когда за спиной или сбоку подкрадывается моб.
 */
public class AccountHudOverlay implements HudRenderCallback {
	private static final long SHADOW_DURATION_MS = 460L;

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}

		renderPeripheralShadowCue(context);

		TextRenderer tr = client.textRenderer;
		long balance = ClientAccountData.getBalance();
		long debt = ClientAccountData.getDebt();
		int sizoSec = ClientAccountData.getSizoSecondsLeft();

		Text balanceText = Text.literal("Счёт: ").formatted(Formatting.GRAY)
				.append(Text.literal(balance + " ₽").formatted(Formatting.GREEN, Formatting.BOLD));

		Text debtText = Text.literal("Долг: ").formatted(Formatting.GRAY)
				.append(Text.literal(debt + " ₽").formatted(
						debt > 0L ? Formatting.RED : Formatting.DARK_GRAY,
						debt > 0L ? Formatting.BOLD : Formatting.RESET
				));

		int width = Math.max(tr.getWidth(balanceText), tr.getWidth(debtText)) + 14;
		int height = sizoSec > 0 ? 38 : 26;

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
		int borderColor = debt > 0L ? 0xFFCC3333 : 0xFF3A8F52;
		context.drawBorder(x, y, width, height, borderColor);

		context.drawText(tr, balanceText, x + 6, y + 5, 0xFFFFFF, true);
		context.drawText(tr, debtText, x + 6, y + 15, 0xFFFFFF, true);
		if (sizoText != null) {
			context.drawText(tr, sizoText, x + 6, y + 26, 0xFF5555, true);
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
}
