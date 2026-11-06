package com.novaimod.client.screen;

import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Творческий экран настройки шансов на любой исход для всех автоматов и столов казино:
 * 1) Автомат судьбы (Жетон возрождения / Ничего / Смерть)
 * 2) Стол «Монетка» (Орёл / Решка)
 * 3) Стол «Рулетка» (Зелёное 0 / Красное / Чёрное)
 */
public class OddsConfigScreen extends Screen {
	private static final int PANEL_W = 310;
	private static final int PANEL_H = 216;

	private int initFateToken;
	private int initFateNothing;
	private int initFateDeath;
	private int initCoinHeads;
	private int initRouletteGreen;
	private int initRouletteRed;
	private int initRouletteBlack;

	private TextFieldWidget fateTokenField;
	private TextFieldWidget fateNothingField;
	private TextFieldWidget fateDeathField;
	private TextFieldWidget coinHeadsField;
	private TextFieldWidget rouletteGreenField;
	private TextFieldWidget rouletteRedField;
	private TextFieldWidget rouletteBlackField;

	public OddsConfigScreen(
			int fateToken,
			int fateNothing,
			int fateDeath,
			int coinHeads,
			int rouletteGreen,
			int rouletteRed,
			int rouletteBlack
	) {
		super(Text.literal("Пульт вероятностей"));
		this.initFateToken = fateToken;
		this.initFateNothing = fateNothing;
		this.initFateDeath = fateDeath;
		this.initCoinHeads = coinHeads;
		this.initRouletteGreen = rouletteGreen;
		this.initRouletteRed = rouletteRed;
		this.initRouletteBlack = rouletteBlack;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		// 1. Автомат судьбы
		this.fateTokenField = createPercentField(left + 26, top + 52, 72, this.initFateToken);
		this.fateNothingField = createPercentField(left + 118, top + 52, 72, this.initFateNothing);
		this.fateDeathField = createPercentField(left + 210, top + 52, 72, this.initFateDeath);

		// 2. Монетка
		this.coinHeadsField = createPercentField(left + 26, top + 100, 90, this.initCoinHeads);

		// 3. Рулетка
		this.rouletteGreenField = createPercentField(left + 26, top + 148, 72, this.initRouletteGreen);
		this.rouletteRedField = createPercentField(left + 118, top + 148, 72, this.initRouletteRed);
		this.rouletteBlackField = createPercentField(left + 210, top + 148, 72, this.initRouletteBlack);

		// Кнопки сохранения и сброса
		this.addDrawableChild(
				ButtonWidget.builder(
								Text.literal("Сохранить").formatted(Formatting.GREEN, Formatting.BOLD),
								b -> saveAndClose()
						)
						.dimensions(left + 20, top + 184, 96, 20)
						.build()
		);

		this.addDrawableChild(
				ButtonWidget.builder(
								Text.literal("По умолчанию").formatted(Formatting.YELLOW),
								b -> resetDefaults()
						)
						.dimensions(left + 122, top + 184, 102, 20)
						.build()
		);

		this.addDrawableChild(
				ButtonWidget.builder(Text.literal("Закрыть"), b -> this.close())
						.dimensions(left + 230, top + 184, 60, 20)
						.build()
		);
	}

	private TextFieldWidget createPercentField(int x, int y, int width, int initialVal) {
		TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, width, 18, Text.literal("%"));
		f.setMaxLength(3);
		f.setText(Integer.toString(Math.clamp(initialVal, 0, 100)));
		f.setTextPredicate(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
		this.addDrawableChild(f);
		return f;
	}

	private void resetDefaults() {
		this.fateTokenField.setText("50");
		this.fateNothingField.setText("25");
		this.fateDeathField.setText("25");
		this.coinHeadsField.setText("50");
		this.rouletteGreenField.setText("3");
		this.rouletteRedField.setText("48");
		this.rouletteBlackField.setText("49");
	}

	private void saveAndClose() {
		ClientPlayNetworking.send(
				new ModPayloads.SaveOddsConfigPayload(
						parsePercent(this.fateTokenField, 50),
						parsePercent(this.fateNothingField, 25),
						parsePercent(this.fateDeathField, 25),
						parsePercent(this.coinHeadsField, 50),
						parsePercent(this.rouletteGreenField, 3),
						parsePercent(this.rouletteRedField, 48),
						parsePercent(this.rouletteBlackField, 49)
				)
		);
		this.close();
	}

	private int parsePercent(TextFieldWidget field, int fallback) {
		if (field == null || field.getText().trim().isEmpty()) {
			return fallback;
		}
		try {
			return Math.clamp(Integer.parseInt(field.getText().trim()), 0, 100);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF2181224);
		context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFFC084FC);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("ПУЛЬТ ВЕРОЯТНОСТЕЙ (ТВОРЧЕСКИЙ)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
				this.width / 2,
				top + 10,
				0xFFFFFF
		);

		// Секция 1: Автомат судьбы
		context.drawText(
				this.textRenderer,
				Text.literal("1. Автомат судьбы (веса в %):").formatted(Formatting.GOLD, Formatting.BOLD),
				left + 20,
				top + 28,
				0xFFFFFF,
				true
		);
		context.drawText(this.textRenderer, Text.literal("Жетон %").formatted(Formatting.GREEN), left + 26, top + 41, 0xFFFFFF, true);
		context.drawText(this.textRenderer, Text.literal("Ничего %").formatted(Formatting.GRAY), left + 118, top + 41, 0xFFFFFF, true);
		context.drawText(this.textRenderer, Text.literal("Смерть %").formatted(Formatting.RED), left + 210, top + 41, 0xFFFFFF, true);

		// Секция 2: Монетка
		int heads = parsePercent(this.coinHeadsField, 50);
		int tails = Math.max(0, 100 - heads);
		context.drawText(
				this.textRenderer,
				Text.literal("2. Стол «Монетка»:").formatted(Formatting.GOLD, Formatting.BOLD),
				left + 20,
				top + 76,
				0xFFFFFF,
				true
		);
		context.drawText(this.textRenderer, Text.literal("Шанс Орла %").formatted(Formatting.YELLOW), left + 26, top + 89, 0xFFFFFF, true);
		context.drawText(
				this.textRenderer,
				Text.literal("→ Шанс Решки: " + tails + "%").formatted(Formatting.AQUA),
				left + 128,
				top + 105,
				0xFFFFFF,
				true
		);

		// Секция 3: Рулетка
		context.drawText(
				this.textRenderer,
				Text.literal("3. Стол «Рулетка» (2-е Зелёное подряд = 100%):").formatted(Formatting.GOLD, Formatting.BOLD),
				left + 20,
				top + 124,
				0xFFFFFF,
				true
		);
		context.drawText(this.textRenderer, Text.literal("Зелёное %").formatted(Formatting.GREEN), left + 26, top + 137, 0xFFFFFF, true);
		context.drawText(this.textRenderer, Text.literal("Красное %").formatted(Formatting.RED), left + 118, top + 137, 0xFFFFFF, true);
		context.drawText(this.textRenderer, Text.literal("Чёрное %").formatted(Formatting.GRAY), left + 210, top + 137, 0xFFFFFF, true);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
