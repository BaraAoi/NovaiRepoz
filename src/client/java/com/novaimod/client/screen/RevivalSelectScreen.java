package com.novaimod.client.screen;

import java.util.List;

import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Экран выбора погибшего игрока (в т.ч. на Хардкоре) для возрождения Жетоном возрождения.
 */
public class RevivalSelectScreen extends Screen {
	private static final int PANEL_W = 220;
	private static final int PANEL_H = 176;

	private final List<ModPayloads.ReviveCandidateNet> candidates;

	public RevivalSelectScreen(List<ModPayloads.ReviveCandidateNet> candidates) {
		super(Text.literal("Возрождение игрока"));
		this.candidates = candidates;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		int maxShown = Math.min(5, this.candidates.size());
		for (int i = 0; i < maxShown; i++) {
			ModPayloads.ReviveCandidateNet c = this.candidates.get(i);
			this.addDrawableChild(
					ButtonWidget.builder(
							Text.literal("✦ Возродить: " + c.name()).formatted(Formatting.GOLD),
							btn -> {
								ClientPlayNetworking.send(new ModPayloads.RevivePlayerPayload(c.uuid()));
								this.close();
							}
					).dimensions(left + 16, top + 34 + i * 24, PANEL_W - 32, 20).build()
			);
		}

		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Отмена"),
						btn -> this.close()
				).dimensions(left + 60, top + PANEL_H - 26, 100, 20).build()
		);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE18141E);
		context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFFFFAA00);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("ЖЕТОН ВОЗРОЖДЕНИЯ").formatted(Formatting.GOLD, Formatting.BOLD),
				this.width / 2,
				top + 10,
				0xFFFFFF
		);
		context.drawCenteredTextWithShadow(
				this.textRenderer,
				Text.literal("Выберите павшего игрока:").formatted(Formatting.GRAY),
				this.width / 2,
				top + 21,
				0xCCCCCC
		);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
