package com.novaimod.client.screen;

import com.novaimod.network.ModPayloads;
import com.novaimod.screen.VendingScreenHandler;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Интерфейс Торгового аппарата на входе каждого заведения.
 * Показывает оценку положенных предметов в реальном времени и кнопку мгновенной продажи на счёт.
 */
public class VendingScreen extends HandledScreen<VendingScreenHandler> {
	private ButtonWidget sellButton;

	public VendingScreen(VendingScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = 176;
		this.backgroundHeight = 166;
	}

	@Override
	protected void init() {
		super.init();
		this.titleX = 8;
		this.titleY = 6;
		this.playerInventoryTitleY = this.backgroundHeight - 94;

		this.sellButton = this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Сдать всё"),
						btn -> ClientPlayNetworking.send(new ModPayloads.VendingSellAllPayload())
				).dimensions(this.x + 88, this.y + 48, 80, 20).build()
		);
	}

	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();
		if (this.sellButton != null) {
			long total = this.handler.calculateTotalValue();
			this.sellButton.active = total > 0L;
			this.sellButton.setMessage(
					total > 0L
							? Text.literal("Сдать +" + total + " ₽").formatted(Formatting.GREEN)
							: Text.literal("Положите вещи")
			);
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = this.x;
		int top = this.y;

		// Стильная тёмная панель контейнера
		context.fill(left, top, left + this.backgroundWidth, top + this.backgroundHeight, 0xEE181C24);
		context.drawBorder(left, top, this.backgroundWidth, this.backgroundHeight, 0xFF4A9E62);

		// Панель оценки справа от 3x3 слотов
		context.fill(left + 86, top + 18, left + 168, top + 44, 0xCC0E1116);
		context.drawBorder(left + 86, top + 18, 82, 26, 0xFF2F533B);

		long total = this.handler.calculateTotalValue();
		context.drawText(this.textRenderer, Text.literal("Оценка:"), left + 91, top + 21, 0xAAAAAA, false);
		context.drawText(
				this.textRenderer,
				Text.literal(total + " ₽").formatted(Formatting.GOLD, Formatting.BOLD),
				left + 91,
				top + 32,
				0xFFD700,
				true
		);

		// Отрисовка рамок всех слотов (3x3 приёмник + инвентарь игрока)
		for (Slot slot : this.handler.slots) {
			int sx = left + slot.x - 1;
			int sy = top + slot.y - 1;
			boolean machineSlot = slot.id < VendingScreenHandler.MACHINE_SLOTS;
			context.fill(sx, sy, sx + 18, sy + 18, machineSlot ? 0xFF1F2B24 : 0xFF252830);
			context.drawBorder(sx, sy, 18, 18, machineSlot ? 0xFF478F5B : 0xFF484E5C);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0xFFE6FFF0, false);
		context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0xFFBBBBBB, false);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
