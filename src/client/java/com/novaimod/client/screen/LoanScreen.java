package com.novaimod.client.screen;

import com.novaimod.client.ClientAccountData;
import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Экран Кредитора внутри Микрокредитной организации.
 * Позволяет ввести и взять ЛЮБУЮ сумму кредита (или погасить долг),
 * при этом скрытая ставка 20% сложных процентов в день НИГДЕ не упоминается!
 */
public class LoanScreen extends Screen {
	private static final int PANEL_W = 240;
	private static final int PANEL_H = 180;

	private TextFieldWidget amountField;

	public LoanScreen(long initialBalance, long initialDebt) {
		super(Text.literal("Микрокредитная организация"));
		ClientAccountData.update(initialBalance, initialDebt, ClientAccountData.getSizoSecondsLeft());
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		this.amountField = new TextFieldWidget(
				this.textRenderer,
				left + 20,
				top + 66,
				200,
				20,
				Text.literal("Сумма")
		);
		this.amountField.setMaxLength(10);
		this.amountField.setText("1000");
		this.amountField.setTextPredicate(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
		this.addDrawableChild(this.amountField);

		// Быстрые кнопки выбора суммы
		int[] presets = new int[]{100, 500, 1000, 5000};
		for (int i = 0; i < presets.length; i++) {
			int val = presets[i];
			this.addDrawableChild(
					ButtonWidget.builder(
							Text.literal("+" + val),
							btn -> addAmount(val)
					).dimensions(left + 20 + i * 51, top + 92, 47, 18).build()
			);
		}

		// Взять кредит
		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Взять займ").formatted(Formatting.GREEN, Formatting.BOLD),
						btn -> {
							long amt = parseAmount();
							if (amt > 0L) {
								ClientPlayNetworking.send(new ModPayloads.LoanActionPayload(true, amt));
							}
						}
				).dimensions(left + 20, top + 118, 96, 22).build()
		);

		// Погасить указанную сумму
		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Погасить").formatted(Formatting.YELLOW),
						btn -> {
							long amt = parseAmount();
							if (amt > 0L) {
								ClientPlayNetworking.send(new ModPayloads.LoanActionPayload(false, amt));
							}
						}
				).dimensions(left + 124, top + 118, 96, 22).build()
		);

		// Погасить весь долг целиком
		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Погасить весь долг"),
						btn -> {
							long debt = ClientAccountData.getDebt();
							if (debt > 0L) {
								ClientPlayNetworking.send(new ModPayloads.LoanActionPayload(false, debt));
							}
						}
				).dimensions(left + 20, top + 146, 136, 20).build()
		);

		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Закрыть"),
						btn -> this.close()
				).dimensions(left + 162, top + 146, 58, 20).build()
		);
	}

	private void addAmount(long delta) {
		long cur = parseAmount();
		long next = Math.min(999_999_999L, cur + delta);
		this.amountField.setText(Long.toString(next));
	}

	private long parseAmount() {
		String raw = this.amountField != null ? this.amountField.getText().trim() : "";
		if (raw.isEmpty()) {
			return 0L;
		}
		try {
			return Long.parseLong(raw);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE161922);
		context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFFD4AF37);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("МФО «БЫСТРЫЕ ДЕНЬГИ»").formatted(Formatting.GOLD, Formatting.BOLD),
				this.width / 2,
				top + 10,
				0xFFFFFF
		);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Мгновенное одобрение любой суммы!").formatted(Formatting.GREEN),
				this.width / 2,
				top + 22,
				0xFFFFFF
		);

		long bal = ClientAccountData.getBalance();
		long debt = ClientAccountData.getDebt();
		context.drawText(
				this.textRenderer,
				Text.literal("Ваш счёт: ").formatted(Formatting.GRAY)
						.append(Text.literal(bal + " ₽").formatted(Formatting.GREEN, Formatting.BOLD)),
				left + 20,
				top + 38,
				0xFFFFFF,
				true
		);
		context.drawText(
				this.textRenderer,
				Text.literal("Текущий долг: ").formatted(Formatting.GRAY)
						.append(Text.literal(debt + " ₽").formatted(debt > 0L ? Formatting.RED : Formatting.GRAY, Formatting.BOLD)),
				left + 20,
				top + 50,
				0xFFFFFF,
				true
		);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
