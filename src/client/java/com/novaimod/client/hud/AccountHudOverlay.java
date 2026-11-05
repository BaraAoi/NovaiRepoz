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
 * Отрисовывает у каждого игрока сверху слева его виртуальный Счёт и Долг
 * (а также таймер СИЗО, если игрок задержан ОМОНом).
 */
public class AccountHudOverlay implements HudRenderCallback {
	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.options.hudHidden) {
			return;
		}

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
}
