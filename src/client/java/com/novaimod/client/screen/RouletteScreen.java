package com.novaimod.client.screen;

import com.novaimod.client.ClientAccountData;
import com.novaimod.economy.CasinoGamesManager;
import com.novaimod.economy.CasinoGamesManager.RouletteBetType;
import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * Полноценная европейская рулетка (0–36):
 * - Интерактивное сукно 0–36 (можно кликнуть прямо на любое число 0..36 для ставки x36),
 *   а также все классические ставки: Красное, Чёрное, Зелёное (0), Чётное, Нечётное, 1–18, 19–36 и 3 дюжины.
 * - Колесо прокручивается с комфортной умеренной скоростью и плавно замедляется по мере приближения к выпавшему сектору.
 */
public class RouletteScreen extends Screen {
	private static final int PANEL_W = 344;
	private static final int PANEL_H = 236;

	// Координаты интерактивного поля чисел 0..36 относительно (left, top)
	private static final int GRID_X = 16;
	private static final int GRID_Y = 90;
	private static final int ZERO_W = 24;
	private static final int CELL_W = 24;
	private static final int CELL_H = 16;

	private TextFieldWidget betField;
	private ButtonWidget spinButton;

	private RouletteBetType selectedType = RouletteBetType.RED;
	private int selectedNumber = 0;

	// Состояние плавной анимации колеса
	private boolean spinning = false;
	private int animElapsed = 0;
	private int animTotal = CasinoGamesManager.ROULETTE_ANIM_TICKS;
	private double wheelStartPos = 0.0D;
	private double wheelTargetPos = 0.0D;
	private double currentWheelPos = 0.0D;
	private int lastTickedPocket = -1;

	private boolean hasLastResult = false;
	private int lastWinningNumber = 0;
	private boolean lastWon = false;
	private long lastPayout = 0L;

	public RouletteScreen(long initialBalance) {
		super(Text.literal("Стол «Рулетка»"));
		ClientAccountData.update(initialBalance, ClientAccountData.getDebt(), ClientAccountData.getSizoSecondsLeft());
	}

	public void onSpinResult(int winningNumber, boolean won, long payout, int animTicks) {
		int targetWheelIdx = CasinoGamesManager.findWheelIndex(winningNumber);
		int currentBaseIdx = Math.floorMod((int) Math.round(this.currentWheelPos), CasinoGamesManager.WHEEL_ORDER.length);
		this.wheelStartPos = currentBaseIdx;

		int deltaIdx = Math.floorMod(targetWheelIdx - currentBaseIdx, CasinoGamesManager.WHEEL_ORDER.length);
		// Умеренная дистанция прокрутки (от 22 до 58 секторов за ~6.5 секунд):
		// скорость хорошо читается глазом и плавно гаснет до полной остановки на точном числе!
		int totalTravel = (deltaIdx < 22) ? (deltaIdx + CasinoGamesManager.WHEEL_ORDER.length) : deltaIdx;
		this.wheelTargetPos = this.wheelStartPos + totalTravel;

		this.spinning = true;
		this.animElapsed = 0;
		this.animTotal = Math.max(60, animTicks);
		this.lastWinningNumber = winningNumber;
		this.lastWon = won;
		this.lastPayout = payout;
		this.hasLastResult = false;
		this.lastTickedPocket = currentBaseIdx;
		if (this.spinButton != null) {
			this.spinButton.active = false;
		}
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		// Внешние ставки и дюжины под игровым полем чисел (Y = top + 142 и top + 162)
		addBetTypeButton(left + 16, top + 142, 60, 18, "Красное x2", RouletteBetType.RED);
		addBetTypeButton(left + 80, top + 142, 60, 18, "Чёрное x2", RouletteBetType.BLACK);
		addBetTypeButton(left + 144, top + 142, 66, 18, "Зелёное 0 x36", RouletteBetType.GREEN);
		addBetTypeButton(left + 214, top + 142, 56, 18, "Чётное x2", RouletteBetType.EVEN);
		addBetTypeButton(left + 274, top + 142, 54, 18, "Нечёт x2", RouletteBetType.ODD);

		addBetTypeButton(left + 16, top + 162, 56, 18, "1–18 x2", RouletteBetType.LOW_1_18);
		addBetTypeButton(left + 76, top + 162, 56, 18, "19–36 x2", RouletteBetType.HIGH_19_36);
		addBetTypeButton(left + 136, top + 162, 62, 18, "1–12 x3", RouletteBetType.DOZEN_1);
		addBetTypeButton(left + 202, top + 162, 62, 18, "13–24 x3", RouletteBetType.DOZEN_2);
		addBetTypeButton(left + 268, top + 162, 60, 18, "25–36 x3", RouletteBetType.DOZEN_3);

		// Поле суммы ставки и быстрые фишки
		this.betField = new TextFieldWidget(this.textRenderer, left + 16, top + 186, 86, 18, Text.literal("Ставка"));
		this.betField.setMaxLength(10);
		this.betField.setText("250");
		this.betField.setTextPredicate(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
		this.addDrawableChild(this.betField);

		int[] chips = new int[]{100, 500, 1000};
		for (int i = 0; i < chips.length; i++) {
			int val = chips[i];
			this.addDrawableChild(
					ButtonWidget.builder(Text.literal("+" + val), b -> addBet(val))
							.dimensions(left + 106 + i * 44, top + 186, 41, 18)
							.build()
			);
		}
		this.addDrawableChild(
				ButtonWidget.builder(Text.literal("x2"), b -> {
							long cur = Math.max(50L, parseBet());
							this.betField.setText(Long.toString(Math.min(999_999_999L, cur * 2L)));
						})
						.dimensions(left + 238, top + 186, 30, 18)
						.build()
		);
		this.addDrawableChild(
				ButtonWidget.builder(Text.literal("Закр."), b -> this.close())
						.dimensions(left + 272, top + 186, 56, 18)
						.build()
		);

		this.spinButton = this.addDrawableChild(
				ButtonWidget.builder(
								Text.literal("★ КРУТИТЬ РУЛЕТКУ ★").formatted(Formatting.GOLD, Formatting.BOLD),
								b -> startSpinRequest()
						)
						.dimensions(left + 16, top + 208, 312, 20)
						.build()
		);
		this.spinButton.active = !this.spinning;
	}

	private void addBetTypeButton(int x, int y, int w, int h, String label, RouletteBetType type) {
		this.addDrawableChild(
				ButtonWidget.builder(Text.literal(label), b -> {
							if (!this.spinning) {
								this.selectedType = type;
								if (type == RouletteBetType.GREEN) {
									this.selectedNumber = 0;
								}
							}
						})
						.dimensions(x, y, w, h)
						.build()
		);
	}

	private void startSpinRequest() {
		if (this.spinning) {
			return;
		}
		long bet = parseBet();
		if (bet <= 0L) {
			return;
		}
		ClientPlayNetworking.send(
				new ModPayloads.PlayRoulettePayload(bet, this.selectedType.ordinal(), this.selectedNumber)
		);
	}

	private void addBet(long delta) {
		long next = Math.min(999_999_999L, parseBet() + delta);
		this.betField.setText(Long.toString(next));
	}

	private long parseBet() {
		if (this.betField == null) {
			return 0L;
		}
		String raw = this.betField.getText().trim();
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
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.spinning && button == 0) {
			int left = (this.width - PANEL_W) / 2;
			int top = (this.height - PANEL_H) / 2;
			int gx = left + GRID_X;
			int gy = top + GRID_Y;

			// Клик по сектору "0" (Зелёное / Точное число 0 x36)
			if (mouseX >= gx && mouseX < gx + ZERO_W && mouseY >= gy && mouseY < gy + CELL_H * 3) {
				this.selectedType = RouletteBetType.STRAIGHT;
				this.selectedNumber = 0;
				playClick();
				return true;
			}

			// Клик по любому числу 1..36 на сукне рулетки (3 ряда x 12 колонок)
			int numStartX = gx + ZERO_W;
			if (mouseX >= numStartX && mouseX < numStartX + CELL_W * 12 && mouseY >= gy && mouseY < gy + CELL_H * 3) {
				int col = (int) ((mouseX - numStartX) / CELL_W); // 0..11
				int row = (int) ((mouseY - gy) / CELL_H);        // 0..2 (верхний ряд: 3,6..36; средний: 2,5..35; нижний: 1,4..34)
				int number = col * 3 + (3 - row);
				if (number >= 1 && number <= 36) {
					this.selectedType = RouletteBetType.STRAIGHT;
					this.selectedNumber = number;
					playClick();
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void playClick() {
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.15F));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.spinning) {
			this.animElapsed++;
			double u = MathHelper.clamp((double) this.animElapsed / (double) this.animTotal, 0.0D, 1.0D);
			// Плавное замедление (чем ближе к результату — тем медленнее ползёт колесо!)
			double eased = 1.0D - Math.pow(1.0D - u, 2.75D);
			this.currentWheelPos = MathHelper.lerp(eased, this.wheelStartPos, this.wheelTargetPos);

			int curPocket = Math.floorMod((int) Math.round(this.currentWheelPos), CasinoGamesManager.WHEEL_ORDER.length);
			if (curPocket != this.lastTickedPocket && this.client != null) {
				this.lastTickedPocket = curPocket;
				float pitch = 1.35F - (float) (u * 0.45D);
				this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), pitch, 0.35F));
			}

			if (this.animElapsed >= this.animTotal) {
				this.spinning = false;
				this.currentWheelPos = this.wheelTargetPos;
				this.hasLastResult = true;
				if (this.spinButton != null) {
					this.spinButton.active = true;
				}
			}
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF2101A14);
		context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFFD4AF37);

		// Шапка и баланс
		context.drawText(
				this.textRenderer,
				Text.literal("РУЛЕТКА КАЗИНО (0–36)").formatted(Formatting.GOLD, Formatting.BOLD),
				left + 16,
				top + 8,
				0xFFFFFF,
				true
		);
		context.drawText(
				this.textRenderer,
				Text.literal("Счёт: ").formatted(Formatting.GRAY)
						.append(Text.literal(ClientAccountData.getBalance() + " ₽").formatted(Formatting.GREEN, Formatting.BOLD)),
				left + PANEL_W - 135,
				top + 8,
				0xFFFFFF,
				true
		);

		// 1. Окно прокрутки колеса рулетки (плавная лента секторов)
		renderWheelStrip(context, left + 16, top + 24, 312, 38, delta);

		// 2. Информационная строка текущей ставки / результата
		renderStatusLine(context, this.width / 2, top + 68);

		// 3. Интерактивное сукно чисел 0..36
		renderRouletteFeltGrid(context, left + GRID_X, top + GRID_Y, mouseX, mouseY);
	}

	private void renderWheelStrip(DrawContext context, int x, int y, int w, int h, float delta) {
		context.fill(x, y, x + w, y + h, 0xFF0A0E0C);
		context.drawBorder(x, y, w, h, 0xFFB8922E);

		double renderPos = this.currentWheelPos;
		if (this.spinning && this.animElapsed < this.animTotal) {
			double u = MathHelper.clamp(((double) this.animElapsed + delta) / (double) this.animTotal, 0.0D, 1.0D);
			double eased = 1.0D - Math.pow(1.0D - u, 2.75D);
			renderPos = MathHelper.lerp(eased, this.wheelStartPos, this.wheelTargetPos);
		}

		int pocketW = 32;
		int pocketH = 26;
		int centerX = x + w / 2;
		int pocketY = y + 6;

		int baseIndex = (int) Math.floor(renderPos);
		double frac = renderPos - baseIndex;

		context.enableScissor(x + 2, y + 2, x + w - 2, y + h - 2);
		for (int offset = -5; offset <= 6; offset++) {
			int wheelIdx = Math.floorMod(baseIndex + offset, CasinoGamesManager.WHEEL_ORDER.length);
			int num = CasinoGamesManager.WHEEL_ORDER[wheelIdx];

			int px = (int) Math.round(centerX + (offset - frac) * pocketW - pocketW / 2.0D);
			int bg = getNumberColorHex(num);

			context.fill(px + 1, pocketY, px + pocketW - 1, pocketY + pocketH, bg);
			context.drawBorder(px + 1, pocketY, pocketW - 2, pocketH, 0xAAFFFFFF);
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal(Integer.toString(num)).formatted(Formatting.BOLD),
					px + pocketW / 2,
					pocketY + 9,
					0xFFFFFF
			);
		}
		context.disableScissor();

		// Золотой указатель по центру колеса
		context.fill(centerX - 1, y, centerX + 1, y + h, 0xFFFFEA00);
		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("▼"), centerX, y - 2, 0xFFFFEA00);
	}

	private void renderStatusLine(DrawContext context, int centerX, int y) {
		if (this.spinning) {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("Шарик катится по колесу... Ставка: " + describeSelection()).formatted(Formatting.YELLOW),
					centerX,
					y,
					0xFFFFFF
			);
		} else if (this.hasLastResult) {
			String col = this.lastWinningNumber == 0 ? "ЗЕЛЁНОЕ" : (CasinoGamesManager.isRed(this.lastWinningNumber) ? "КРАСНОЕ" : "ЧЁРНОЕ");
			Text line = this.lastWon
					? Text.literal("Выпало " + this.lastWinningNumber + " (" + col + ") — ВЫИГРЫШ +" + this.lastPayout + " ₽!").formatted(Formatting.GREEN, Formatting.BOLD)
					: Text.literal("Выпало " + this.lastWinningNumber + " (" + col + ") | Выбрано: " + describeSelection()).formatted(Formatting.GOLD);
			context.drawCenteredTextWithShadow(this.textRenderer, line, centerX, y, 0xFFFFFF);
		} else {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("Выбрана ставка: ").formatted(Formatting.GRAY)
							.append(Text.literal(describeSelection()).formatted(Formatting.AQUA, Formatting.BOLD))
							.append(Text.literal(" (кликните по числу 0–36 или кнопке ниже)").formatted(Formatting.DARK_GRAY)),
					centerX,
					y,
					0xFFFFFF
			);
		}
	}

	private String describeSelection() {
		if (this.selectedType == RouletteBetType.STRAIGHT) {
			return "Точное число " + this.selectedNumber + " (x36)";
		}
		return this.selectedType.getLabel() + " (x" + this.selectedType.getMultiplier() + ")";
	}

	private void renderRouletteFeltGrid(DrawContext context, int gx, int gy, int mouseX, int mouseY) {
		// Сектор "0" слева (высотой в 3 ряда)
		boolean zeroSelected = (this.selectedType == RouletteBetType.STRAIGHT && this.selectedNumber == 0)
				|| this.selectedType == RouletteBetType.GREEN;
		context.fill(gx, gy, gx + ZERO_W - 1, gy + CELL_H * 3, 0xFF15803D);
		context.drawBorder(gx, gy, ZERO_W - 1, CELL_H * 3, zeroSelected ? 0xFFFFEA00 : 0xFF86EFAC);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("0").formatted(Formatting.BOLD),
				gx + ZERO_W / 2,
				gy + (CELL_H * 3) / 2 - 4,
				0xFFFFFF
		);

		// Числа 1..36 (12 столбцов x 3 строки)
		int numStartX = gx + ZERO_W;
		for (int col = 0; col < 12; col++) {
			for (int row = 0; row < 3; row++) {
				int num = col * 3 + (3 - row);
				int cx = numStartX + col * CELL_W;
				int cy = gy + row * CELL_H;
				int bg = getNumberColorHex(num);

				boolean isSelectedStraight = (this.selectedType == RouletteBetType.STRAIGHT && this.selectedNumber == num);
				boolean isCoveredByBet = !this.spinning && CasinoGamesManager.evaluateRouletteBet(this.selectedType, this.selectedNumber, num);
				boolean hovered = mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H;

				context.fill(cx, cy, cx + CELL_W - 1, cy + CELL_H - 1, bg);
				int border = isSelectedStraight ? 0xFFFFEA00 : (hovered || isCoveredByBet ? 0xFFE2E8F0 : 0x55FFFFFF);
				context.drawBorder(cx, cy, CELL_W - 1, CELL_H - 1, border);

				context.drawCenteredTextWithShadow(
						this.textRenderer,
						Text.literal(Integer.toString(num)),
						cx + CELL_W / 2,
						cy + 4,
						isSelectedStraight ? 0xFFFFEA00 : 0xFFFFFF
				);
			}
		}
	}

	private static int getNumberColorHex(int num) {
		if (num == 0) {
			return 0xFF16A34A; // Зелёный
		}
		return CasinoGamesManager.isRed(num) ? 0xFFB91C1C : 0xFF1F2937; // Красный / Чёрный
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
