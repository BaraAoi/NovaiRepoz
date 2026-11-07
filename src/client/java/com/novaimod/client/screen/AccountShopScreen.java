package com.novaimod.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.novaimod.client.ClientAccountData;
import com.novaimod.economy.ItemPricingRegistry;
import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Экран магазина за виртуальный счёт:
 * - Продуктовый магазин (видно разделение ОБЫЧНЫЙ / ЭЛИТНЫЙ товар)
 * - Шахтёрская лавка (включая кнопку починки инструмента в руке!)
 * - Бармен казино (зелья биома + коктейль «Пьяное забвение»)
 * - Элитные продавцы в МФО (ЖИРНАЯ НАДПИСЬ «ПРЕДЛОЖЕНИЕ ОГРАНИЧЕНО» + точный остаток штук!)
 */
public class AccountShopScreen extends Screen {
	private static final int PANEL_W = 290;
	private static final int PANEL_H = 216;
	private static final int VISIBLE_ROWS = 5;
	private static final int ROW_H = 26;

	private final String shopKey;
	private final boolean allowRepair;
	private final boolean limitedElite;
	private final List<ModPayloads.ShopOfferNet> offers;
	private final List<ButtonWidget> buyButtons = new ArrayList<>();
	private ButtonWidget repairButton;
	private int scrollOffset = 0;

	public AccountShopScreen(
			String title,
			String shopKey,
			boolean allowRepair,
			boolean limitedElite,
			List<ModPayloads.ShopOfferNet> offers
	) {
		super(Text.literal(title));
		this.shopKey = shopKey;
		this.allowRepair = allowRepair;
		this.limitedElite = limitedElite;
		this.offers = offers;
	}

	@Override
	protected void init() {
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;
		int rowsTop = top + (this.limitedElite ? 52 : 42);

		this.buyButtons.clear();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			final int rowIdx = row;
			ButtonWidget btn = this.addDrawableChild(
					ButtonWidget.builder(
							Text.literal("Купить"),
							b -> {
								int idx = this.scrollOffset + rowIdx;
								if (idx >= 0 && idx < this.offers.size()) {
									ModPayloads.ShopOfferNet offer = this.offers.get(idx);
									ClientPlayNetworking.send(new ModPayloads.ShopBuyPayload(this.shopKey, offer.offerId()));
								}
							}
					).dimensions(left + PANEL_W - 74, rowsTop + row * ROW_H + 3, 62, 20).build()
			);
			this.buyButtons.add(btn);
		}

		if (this.allowRepair) {
			this.repairButton = this.addDrawableChild(
					ButtonWidget.builder(
							Text.literal("Починить инструмент в руке"),
							b -> ClientPlayNetworking.send(new ModPayloads.RepairToolPayload())
					).dimensions(left + 12, top + PANEL_H - 28, 190, 20).build()
			);
		}

		this.addDrawableChild(
				ButtonWidget.builder(
						Text.literal("Закрыть"),
						b -> this.close()
				).dimensions(left + PANEL_W - 74, top + PANEL_H - 28, 62, 20).build()
		);

		refreshRowButtons();
	}

	private void refreshRowButtons() {
		long balance = ClientAccountData.getBalance();
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			ButtonWidget btn = this.buyButtons.get(row);
			int idx = this.scrollOffset + row;
			if (idx >= this.offers.size()) {
				btn.visible = false;
				continue;
			}
			btn.visible = true;
			ModPayloads.ShopOfferNet offer = this.offers.get(idx);
			boolean soldOut = offer.stock() == 0;
			btn.active = !soldOut && balance >= offer.price();
			btn.setMessage(
					soldOut
							? Text.literal("НЕТ").formatted(Formatting.DARK_RED, Formatting.BOLD)
							: Text.literal(offer.price() + " ₽")
			);
		}

		if (this.repairButton != null) {
			MinecraftClient mc = MinecraftClient.getInstance();
			long cost = 0L;
			if (mc.player != null) {
				ItemStack held = mc.player.getMainHandStack();
				if (held.isEmpty() || !held.isDamaged()) {
					held = mc.player.getOffHandStack();
				}
				cost = ItemPricingRegistry.getRepairCost(held);
			}
			if (cost > 0L) {
				this.repairButton.active = ClientAccountData.getBalance() >= cost;
				this.repairButton.setMessage(Text.literal("Починить в руке (" + cost + " ₽)").formatted(Formatting.AQUA));
			} else {
				this.repairButton.active = false;
				this.repairButton.setMessage(Text.literal("Возьмите битый инструмент"));
			}
		}
	}

	@Override
	public void tick() {
		super.tick();
		refreshRowButtons();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxScroll = Math.max(0, this.offers.size() - VISIBLE_ROWS);
		if (verticalAmount < 0 && this.scrollOffset < maxScroll) {
			this.scrollOffset++;
			refreshRowButtons();
			return true;
		}
		if (verticalAmount > 0 && this.scrollOffset > 0) {
			this.scrollOffset--;
			refreshRowButtons();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;

		context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xEE141720);
		context.drawBorder(left, top, PANEL_W, PANEL_H, this.limitedElite ? 0xFFFF4444 : 0xFF4CA86B);

		context.drawCenteredTextWithShadow(
				this.textRenderer,
				this.title.copy().formatted(Formatting.GOLD, Formatting.BOLD),
				this.width / 2,
				top + 8,
				0xFFFFFF
		);

		context.drawText(
				this.textRenderer,
				Text.literal("Счёт: ").formatted(Formatting.GRAY)
						.append(Text.literal(ClientAccountData.getBalance() + " ₽").formatted(Formatting.GREEN, Formatting.BOLD)),
				left + 12,
				top + 22,
				0xFFFFFF,
				true
		);

		if (this.offers.size() > VISIBLE_ROWS) {
			context.drawText(
					this.textRenderer,
					Text.literal("Колёсико мыши: " + (this.scrollOffset + 1) + "-" + Math.min(this.offers.size(), this.scrollOffset + VISIBLE_ROWS) + "/" + this.offers.size())
							.formatted(Formatting.DARK_GRAY),
					left + PANEL_W - 128,
					top + 22,
					0xAAAAAA,
					false
			);
		}

		// Прямо жирная надпись у элитных продавцов МФО, что предложение ограничено!
		if (this.limitedElite) {
			context.fill(left + 10, top + 33, left + PANEL_W - 10, top + 48, 0xCC550000);
			context.drawCenteredTextWithShadow(
					this.textRenderer,
					Text.literal("⚠ ПРЕДЛОЖЕНИЕ СТРОГО ОГРАНИЧЕНО! УСПЕЙ ЗАБРАТЬ! ⚠")
							.formatted(Formatting.RED, Formatting.BOLD),
					this.width / 2,
					top + 37,
					0xFF5555
			);
		}

		int rowsTop = top + (this.limitedElite ? 52 : 42);
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int idx = this.scrollOffset + row;
			if (idx >= this.offers.size()) {
				break;
			}
			ModPayloads.ShopOfferNet offer = this.offers.get(idx);
			int ry = rowsTop + row * ROW_H;

			context.fill(left + 10, ry, left + PANEL_W - 10, ry + ROW_H - 2, 0xAA202532);
			context.drawBorder(left + 10, ry, PANEL_W - 20, ROW_H - 2, 0xFF343C50);

			context.drawItem(offer.stack(), left + 14, ry + 4);
			context.drawItemInSlot(this.textRenderer, offer.stack(), left + 14, ry + 4);

			// Название предмета
			context.drawText(this.textRenderer, offer.stack().getName(), left + 36, ry + 4, 0xFFFFFF, true);

			// Метка категории (Обычный / Элитный) или жирный остаток у элитных продавцов МФО
			if (offer.stock() >= 0) {
				Text stockLine;
				if (offer.stock() <= 0) {
					stockLine = Text.literal("РАСПРОДАНО (0 шт.)").formatted(Formatting.RED, Formatting.BOLD);
				} else if ("1 В МИРЕ".equals(offer.tierLabel())) {
					stockLine = Text.literal("✦ ЕДИНСТВЕННЫЙ В МИРЕ: " + offer.stock() + " шт.!").formatted(Formatting.AQUA, Formatting.BOLD);
				} else {
					stockLine = Text.literal("ОСТАЛОСЬ ВСЕГО: " + offer.stock() + " шт.!").formatted(Formatting.YELLOW, Formatting.BOLD);
				}
				context.drawText(this.textRenderer, stockLine, left + 36, ry + 14, 0xFFFFFF, true);
			} else {
				Formatting badgeColor = "Элитный".equals(offer.tierLabel()) ? Formatting.GOLD : Formatting.GRAY;
				context.drawText(
						this.textRenderer,
						Text.literal("[" + offer.tierLabel() + "]").formatted(badgeColor),
						left + 36,
						ry + 14,
						0xCCCCCC,
						false
				);
			}
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);

		// Всплывающая подсказка предмета при наведении мыши на иконку
		int left = (this.width - PANEL_W) / 2;
		int top = (this.height - PANEL_H) / 2;
		int rowsTop = top + (this.limitedElite ? 52 : 42);
		for (int row = 0; row < VISIBLE_ROWS; row++) {
			int idx = this.scrollOffset + row;
			if (idx >= this.offers.size()) {
				break;
			}
			int ix = left + 14;
			int iy = rowsTop + row * ROW_H + 4;
			if (mouseX >= ix && mouseX <= ix + 16 && mouseY >= iy && mouseY <= iy + 16) {
				context.drawItemTooltip(this.textRenderer, this.offers.get(idx).stack(), mouseX, mouseY);
				break;
			}
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
