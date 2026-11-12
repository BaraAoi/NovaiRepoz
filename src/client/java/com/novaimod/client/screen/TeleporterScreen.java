package com.novaimod.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.novaimod.client.ClientAccountData;
import com.novaimod.item.PlayerTeleporterItem;
import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Экран Карманного телепорта:
 * - Позволяет за 1000 ₽ телепортировать игрока к себе ИЛИ себя к игроку.
 * - Если на счету меньше 1000 ₽, появляется кнопка «⚡ Фаст деньги (+10 000 ₽)», выдающая 10 000 ₽ в долг каждый раз, когда не хватает!
 */
public class TeleporterScreen extends Screen {
	private static final int PANEL_W = 286;
	private static final int PANEL_H = 216;
	private static final int VISIBLE_ROWS = 4;
	private static final int ROW_H = 26;

	private final List<ModPayloads.ReviveCandidateNet> targets;
	private final List<ButtonWidget> toTargetButtons = new ArrayList<>();
	private final List<ButtonWidget> toMeButtons = new ArrayList<>();
	private ButtonWidget fastMoneyButton;
	private int scrollOffset = 0;

	public TeleporterScreen(long initialBalance, List<ModPayloads.ReviveCandidateNet> targets) {
		super(Text.literal("Карманный телепорт"));
		ClientAccountData.update(initialBalance, ClientAccountData.getDebt(), ClientAccountData.getSizoSecondsLeft());
		this.targets = targets;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;
		int rowsTop = top + 58;

		this.toTargetButtons.clear();
		this.toMeButtons.clear();

		for (int row = 0; row < VISIBLE_ROWS; row++) {
			final int rowIdx = row;
			int ry = rowsTop + row * ROW_H + 3;

			ButtonWidget toPlayerBtn = this.addDrawableChild(
					ButtonWidget.builder(
							Text.literal("К игроку").formatted(Formatting.AQUA),
							btn -> {
								int idx = this.scrollOffset + rowIdx;
								if (idx >= 0 && idx < this.targets.size()) {
									ModPayloads.ReviveCandidateNet t = this.targets.get(idx);
									ClientPlayNetworking.send(new ModPayloads.UseTeleporterPayload(t.uuid(), false));
									this.close();
								}
							}
					).dimensions(left + 126, ry, 72, 20).build()
			);

			ButtonWidget bringToMeBtn = this.addDrawableChild(
					ButtonWidget.builder(
							Text.literal("К себе").formatted(Formatting.LIGHT_PURPLE),
							btn -> {
								int idx = this.scrollOffset + rowIdx;
								if (idx >= 0 && idx < this.targets.size()) {
									ModPayloads.ReviveCandidateNet t = this.targets.get(idx);
									ClientPlayNetworking.send(new ModPayloads.UseTeleporterPayload(t.uuid(), true));
									this.close();
								}
							}
					).dimensions(left + 202, ry, 72, 20).build()
			);

			this.toTargetButtons.add(toPlayerBtn);
			this.toMeButtons.add(bringToMeBtn);
		}

		// Кнопка «Фаст деньги» появляется ТОЛЬКО тогда, когда у игрока не хватает средств на телепорт (< 1000 ₽), и выдаёт +10 000 ₽ в долг!
		this.fastMoneyButton = this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("⚡ Фаст деньги (+10 000 ₽)").formatted(Formatting.GOLD, Formatting.BOLD),
						btn -> ClientPlayNetworking.send(new ModPayloads.TeleporterFastMoneyPayload())
				).dimensions(left + 12, top + PANEL_H - 28, 174, 20).build()
		);

		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Закрыть"),
						btn -> this.close()
				).dimensions(left + PANEL_W - 92, top + PANEL_H - 28, 80, 20).build()
		);

		refreshButtons();
	}

	private void refreshButtons() {
		boolean canAfford = ClientAccountData.getBalance() >= PlayerTeleporterItem.TELEPORT_USE_COST;
		if (this.fastMoneyButton != null) {
			this.fastMoneyButton.visible = !canAfford;
			this.fastMoneyButton.active = !canAfford;
		}
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int idx = this.scrollOffset + row;
			ButtonWidget b1 = this.toTargetButtons.get(row);
			ButtonWidget b2 = this.toMeButtons.get(row);
			if (idx >= this.targets.size()) {
				b1.visible = false;
				b2.visible = false;
			} else {
				b1.visible = true;
				b2.visible = true;
				b1.active = canAfford;
				b2.active = canAfford;
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		refreshButtons();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.targets.size() - VISIBLE_ROWS);
		if (verticalAmount < 0 && this.scrollOffset < maxScroll) {
			this.scrollOffset++;
			refreshButtons();
			return true;
		}
		if (verticalAmount > 0 && this.scrollOffset > 0) {
			this.scrollOffset--;
			refreshButtons();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE121622);
		context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFF42E8E0);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("✦ КАРМАННЫЙ ТЕЛЕПОРТ ✦").formatted(Formatting.AQUA, Formatting.BOLD),
				this.width / 2,
				top + 9,
				0xFFFFFF
		);

		long balance = ClientAccountData.getBalance();
		long cost = PlayerTeleporterItem.TELEPORT_USE_COST;
		context.drawText(
				this.textRenderer,
				Text.literal("Счёт: ").formatted(Formatting.GRAY)
						.append(Text.literal(balance + " ₽").formatted(balance >= cost ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)),
				left + 12,
				top + 24,
				0xFFFFFF,
				true
		);
		context.drawText(
				this.textRenderer,
				Text.literal("Цена прыжка: ").formatted(Formatting.GRAY)
						.append(Text.literal(cost + " ₽").formatted(Formatting.GOLD, Formatting.BOLD)),
				left + 148,
				top + 24,
				0xFFFFFF,
				true
		);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				balance >= cost
						? Text.literal("Выберите игрока («К игроку» или «К себе»):").formatted(Formatting.GRAY)
						: Text.literal("Не хватает средств! Нажмите «Фаст деньги (+10 000 ₽)»").formatted(Formatting.GOLD),
				this.width / 2,
				top + 40,
				0xCCCCCC
		);

		int rowsTop = top + 58;
		if (this.targets.isEmpty()) {
			context.fill(left + 12, rowsTop + 16, left + PANEL_W - 12, rowsTop + 62, 0xAA1B2233);
			context.drawBorder(left + 12, rowsTop + 16, PANEL_W - 24, 46, 0xFF2E3B56);
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("Сейчас в сети нет других живых игроков").formatted(Formatting.YELLOW),
					this.width / 2,
					rowsTop + 34,
					0xFFFFFF
			);
			return;
		}

		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int idx = this.scrollOffset + row;
			if (idx >= this.targets.size()) {
				break;
			}
			ModPayloads.ReviveCandidateNet t = this.targets.get(idx);
			int ry = rowsTop + row * ROW_H;

			context.fill(left + 12, ry, left + PANEL_W - 12, ry + ROW_H - 2, 0xAA1D2436);
			context.drawBorder(left + 12, ry, PANEL_W - 24, ROW_H - 2, 0xFF33425E);

			String shownName = this.textRenderer.trimToWidth(t.name(), 104);
			context.drawText(
					this.textRenderer,
					Text.literal(shownName).formatted(Formatting.WHITE, Formatting.BOLD),
					left + 18,
					ry + 8,
					0xFFFFFF,
					true
			);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
