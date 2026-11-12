package com.novaimod.client.screen;

import com.novaimod.client.ClientAccountData;
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
 * Экран игрового стола «Монетка» в казино (Орёл или Решка, x2).
 */
public class CoinflipScreen extends Screen {
	private static final int PANEL_W = 260;
	private static final int PANEL_H = 210;

	private TextFieldWidget betField;
	private ButtonWidget headsBtn;
	private ButtonWidget tailsBtn;

	private boolean spinning = false;
	private int animElapsed = 0;
	private int animTotal = 45;
	private boolean resultHeads = true;
	private boolean lastWon = false;
	private long lastPayout = 0L;
	private boolean hasLastResult = false;
	private int lastFlipHalfTurn = -1;

	public CoinflipScreen(long initialBalance) {
		super(Text.literal("Стол «Монетка»"));
		ClientAccountData.update(initialBalance, ClientAccountData.getDebt(), ClientAccountData.getSizoSecondsLeft());
	}

	public void onSpinResult(boolean landedHeads, boolean won, long payout, int animTicks) {
		this.spinning = true;
		this.animElapsed = 0;
		this.animTotal = Math.max(20, animTicks);
		this.resultHeads = landedHeads;
		this.lastWon = won;
		this.lastPayout = payout;
		this.hasLastResult = false;
		this.lastFlipHalfTurn = -1;
		updateButtonsState();
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		this.betField = new TextFieldWidget(this.textRenderer, left + 20, top + 112, 130, 18, Text.literal("Ставка"));
		this.betField.setMaxLength(10);
		this.betField.setText("250");
		this.betField.setTextPredicate(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
		this.addDrawableChild(this.betField);

		this.addDrawableChild(
				ButtonWidget.builder(Text.literal("x2"), b -> multiplyBet(2))
						.dimensions(left + 156, top + 112, 38, 18)
						.build()
		);
		this.addDrawableChild(
				ButtonWidget.builder(Text.literal("MAX"), b -> {
							long bal = ClientAccountData.getBalance();
							if (bal > 0L) {
								this.betField.setText(Long.toString(bal));
							}
						})
						.dimensions(left + 198, top + 112, 42, 18)
						.build()
		);

		int[] chips = new int[]{100, 500, 1000, 5000};
		for (int i = 0; i < chips.length; i++) {
			int add = chips[i];
			this.addDrawableChild(
					ButtonWidget.builder(Text.literal("+" + add), b -> addBet(add))
							.dimensions(left + 20 + i * 56, top + 134, 52, 16)
							.build()
			);
		}

		this.headsBtn = this.addDrawableChild(
				ButtonWidget.builder(
								Text.literal("ОРЁЛ (x2)").formatted(Formatting.GOLD, Formatting.BOLD),
								b -> sendBet(true)
						)
						.dimensions(left + 20, top + 156, 106, 22)
						.build()
		);

		this.tailsBtn = this.addDrawableChild(
				ButtonWidget.builder(
								Text.literal("РЕШКА (x2)").formatted(Formatting.AQUA, Formatting.BOLD),
								b -> sendBet(false)
						)
						.dimensions(left + 134, top + 156, 106, 22)
						.build()
		);

		this.addDrawableChild(
				ButtonWidget.builder(Text.literal("Закрыть"), b -> this.close())
						.dimensions(left + 90, top + 183, 80, 18)
						.build()
		);

		updateButtonsState();
	}

	private void updateButtonsState() {
		if (this.headsBtn != null) {
			this.headsBtn.active = !this.spinning;
		}
		if (this.tailsBtn != null) {
			this.tailsBtn.active = !this.spinning;
		}
	}

	private void sendBet(boolean chooseHeads) {
		if (this.spinning) {
			return;
		}
		long bet = parseBet();
		if (bet <= 0L) {
			return;
		}
		ClientPlayNetworking.send(new ModPayloads.PlayCoinflipPayload(bet, chooseHeads));
	}

	private void addBet(long delta) {
		long next = Math.min(999_999_999L, parseBet() + delta);
		this.betField.setText(Long.toString(next));
	}

	private void multiplyBet(int factor) {
		long cur = Math.max(50L, parseBet());
		long next = Math.min(999_999_999L, cur * factor);
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
	public void tick() {
		super.tick();
		if (this.spinning) {
			this.animElapsed++;
			float u = MathHelper.clamp((float) this.animElapsed / (float) this.animTotal, 0.0F, 1.0F);
			float eased = 1.0F - (float) Math.pow(1.0F - u, 2.5D);
			int totalHalfTurns = this.resultHeads ? 10 : 11;
			int halfTurn = (int) Math.floor(eased * totalHalfTurns);
			if (halfTurn != this.lastFlipHalfTurn && this.client != null) {
				this.lastFlipHalfTurn = halfTurn;
				this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.35F, 0.25F));
			}

			if (this.animElapsed >= this.animTotal) {
				this.spinning = false;
				this.hasLastResult = true;
				updateButtonsState();
			}
		}
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xF0141820);
		context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFFD4AF37);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("КАЗИНО — СТОЛ «МОНЕТКА»").formatted(Formatting.GOLD, Formatting.BOLD),
				this.width / 2,
				top + 9,
				0xFFFFFF
		);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Ваш баланс: ").formatted(Formatting.GRAY)
						.append(Text.literal(ClientAccountData.getBalance() + " ₽").formatted(Formatting.GREEN, Formatting.BOLD)),
				this.width / 2,
				top + 22,
				0xFFFFFF
		);

		// Анимированная монетка в центре экрана
		int coinCenterX = this.width / 2;
		int coinCenterY = top + 64;
		renderAnimatedCoin(context, coinCenterX, coinCenterY, delta);

		// Статус броска
		if (this.spinning) {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("Монетка вращается в воздухе...").formatted(Formatting.YELLOW),
					this.width / 2,
					top + 96,
					0xFFFFFF
			);
		} else if (this.hasLastResult) {
			String side = this.resultHeads ? "ОРЁЛ" : "РЕШКА";
			Text banner = this.lastWon
					? Text.literal("Выпал " + side + "! ВЫИГРЫШ +" + this.lastPayout + " ₽!").formatted(Formatting.GREEN, Formatting.BOLD)
					: Text.literal("Выпал " + side + " — ставка не сыграла.").formatted(Formatting.RED);
			context.drawCenteredTextWithShadow(this.textRenderer, banner, this.width / 2, top + 96, 0xFFFFFF);
		} else {
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("Введите ставку и выберите сторону монеты:").formatted(Formatting.GRAY),
					this.width / 2,
					top + 96,
					0xFFFFFF
			);
		}
	}

	private void renderAnimatedCoin(DrawContext context, int cx, int cy, float delta) {
		boolean showHeads = this.resultHeads;
		float scaleX = 1.0F;

		if (this.spinning) {
			float progress = MathHelper.clamp(((float) this.animElapsed + delta) / (float) this.animTotal, 0.0F, 1.0F);
			float eased = 1.0F - (float) Math.pow(1.0F - progress, 2.5D);
			int totalHalfTurns = 10; // Чётное число полуоборотов к финальной стороне
			float angle = eased * totalHalfTurns * MathHelper.PI;
			float cosVal = MathHelper.cos(angle);
			scaleX = Math.max(0.12F, Math.abs(cosVal));
			// Чередуем стороны при каждом перевороте, приходя ровно к resultHeads в конце
			boolean sameAsFinal = (cosVal >= 0.0F);
			showHeads = sameAsFinal == this.resultHeads;
		}

		int halfW = Math.max(3, Math.round(24 * scaleX));
		int halfH = 24;

		int outerColor = showHeads ? 0xFFD99B16 : 0xFF3BA3B8;
		int innerColor = showHeads ? 0xFFFFCA3A : 0xFF66D7EE;

		context.fill(cx - halfW - 2, cy - halfH - 2, cx + halfW + 2, cy + halfH + 2, outerColor);
		context.fill(cx - halfW, cy - halfH, cx + halfW, cy + halfH, innerColor);
		context.drawBorder(cx - halfW - 2, cy - halfH - 2, (halfW + 2) * 2, (halfH + 2) * 2, 0xFFFFF3B0);

		if (scaleX > 0.42F) {
			String label = showHeads ? "ОРЁЛ" : "РЕШКА";
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal(label).formatted(Formatting.BOLD),
					cx,
					cy - 4,
					0xFF1B1B1B
			);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
