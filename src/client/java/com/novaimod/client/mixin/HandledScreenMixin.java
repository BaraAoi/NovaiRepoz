package com.novaimod.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.client.ClientAccountData;
import com.novaimod.equipment.UltimateArmorManager;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModItems;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Добавляет в инвентарь игрока («Инвентарь выживания» и вкладку выживания в Креативе)
 * секретный слот для единого предмета Альтимейт-брони — «Поджопника»!
 * Слот виден ТОЛЬКО тогда, когда у игрока в инвентаре / на курсоре есть «Поджопник», либо он уже надет в этот слот.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {
	@Shadow
	protected int x;

	@Shadow
	protected int y;

	@Shadow
	@Final
	protected T handler;

	@Unique
	private int novai$getSeatPadRelX() {
		Object self = this;
		if (self instanceof InventoryScreen) {
			return 76; // Прямо над слотом щита (76, 61) рядом с моделью игрока!
		}
		if (self instanceof CreativeInventoryScreen creative && creative.isInventoryTabSelected()) {
			return 127; // Симметрично слоту щита во вкладке инвентаря Креатива!
		}
		return -1;
	}

	@Unique
	private int novai$getSeatPadRelY() {
		Object self = this;
		if (self instanceof InventoryScreen) {
			return 43;
		}
		if (self instanceof CreativeInventoryScreen creative && creative.isInventoryTabSelected()) {
			return 19;
		}
		return -1;
	}

	@Unique
	private boolean novai$isSeatPadSlotVisible() {
		int rx = novai$getSeatPadRelX();
		if (rx < 0) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return false;
		}
		return ClientAccountData.isSeatPadEquipped()
				|| UltimateArmorManager.playerHasSeatPadItem(client.player, this.handler.getCursorStack());
	}

	@Inject(method = "drawForeground", at = @At("TAIL"))
	private void novai$drawSeatPadSlot(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
		if (!novai$isSeatPadSlotVisible()) {
			return;
		}
		int rx = novai$getSeatPadRelX();
		int ry = novai$getSeatPadRelY();

		// Классическая утопленная ванильная рамка слота 18x18
		context.fill(rx, ry, rx + 18, ry + 18, 0xFF8B8B8B);
		context.fill(rx, ry, rx + 17, ry + 1, 0xFF373737);
		context.fill(rx, ry, rx + 1, ry + 17, 0xFF373737);
		context.fill(rx + 1, ry + 17, rx + 18, ry + 18, 0xFFFFFFFF);
		context.fill(rx + 17, ry + 1, rx + 18, ry + 18, 0xFFFFFFFF);

		if (ClientAccountData.isSeatPadEquipped()) {
			ItemStack display = new ItemStack(ModItems.ULTIMATE_SEAT_PAD, 1);
			context.drawItem(display, rx + 1, ry + 1);
		} else {
			// Полупрозрачный контур-силуэт поджопника внутри пустого слота
			context.fill(rx + 4, ry + 3, rx + 14, ry + 5, 0x554A3E60);
			context.fill(rx + 7, ry + 3, rx + 11, ry + 5, 0x66FFD54A);
			context.fill(rx + 3, ry + 6, rx + 15, ry + 15, 0x55D43A98);
			context.fill(rx + 6, ry + 9, rx + 12, ry + 12, 0x5548F2FF);
		}

		// Подсветка слота при наведении мыши (mouseX/mouseY в экранных координатах)
		int relMouseX = mouseX - this.x;
		int relMouseY = mouseY - this.y;
		if (relMouseX >= rx && relMouseX < rx + 18 && relMouseY >= ry && relMouseY < ry + 18) {
			context.fill(rx + 1, ry + 1, rx + 17, ry + 17, 0x80FFFFFF);
		}
	}

	@Inject(method = "drawMouseoverTooltip", at = @At("TAIL"))
	private void novai$drawSeatPadTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
		if (!novai$isSeatPadSlotVisible() || !this.handler.getCursorStack().isEmpty()) {
			return;
		}
		int sx = this.x + novai$getSeatPadRelX();
		int sy = this.y + novai$getSeatPadRelY();
		if (x >= sx && x < sx + 18 && y >= sy && y < sy + 18) {
			MinecraftClient client = MinecraftClient.getInstance();
			if (ClientAccountData.isSeatPadEquipped()) {
				context.drawItemTooltip(client.textRenderer, new ItemStack(ModItems.ULTIMATE_SEAT_PAD, 1), x, y);
			} else {
				context.drawTooltip(
						client.textRenderer,
						Text.literal("Секретный слот: Поджопник").formatted(Formatting.LIGHT_PURPLE),
						x,
						y
				);
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void novai$handleSeatPadSlotClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button != 0 && button != 1) {
			return;
		}
		if (!novai$isSeatPadSlotVisible()) {
			return;
		}
		int sx = this.x + novai$getSeatPadRelX();
		int sy = this.y + novai$getSeatPadRelY();
		if (mouseX < sx || mouseX >= sx + 18 || mouseY < sy || mouseY >= sy + 18) {
			return;
		}

		boolean isCreativeScreen = ((Object) this) instanceof CreativeInventoryScreen;
		if (Screen.hasShiftDown()) {
			if (ClientAccountData.isSeatPadEquipped()) {
				ClientAccountData.setSeatPadEquipped(false);
				ClientPlayNetworking.send(new ModPayloads.SeatPadSlotActionPayload(1, -1));
				cir.setReturnValue(true);
			}
			return;
		}

		ItemStack cursor = this.handler.getCursorStack();
		if (!ClientAccountData.isSeatPadEquipped() && cursor.isOf(ModItems.ULTIMATE_SEAT_PAD)) {
			ClientAccountData.setSeatPadEquipped(true);
			if (isCreativeScreen) {
				cursor.decrement(1);
				this.handler.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
				ClientPlayNetworking.send(new ModPayloads.SeatPadSlotActionPayload(3, -1));
			} else {
				ClientPlayNetworking.send(new ModPayloads.SeatPadSlotActionPayload(0, -1));
			}
			cir.setReturnValue(true);
		} else if (ClientAccountData.isSeatPadEquipped() && cursor.isEmpty()) {
			ClientAccountData.setSeatPadEquipped(false);
			if (isCreativeScreen) {
				this.handler.setCursorStack(new ItemStack(ModItems.ULTIMATE_SEAT_PAD, 1));
				ClientPlayNetworking.send(new ModPayloads.SeatPadSlotActionPayload(4, -1));
			} else {
				ClientPlayNetworking.send(new ModPayloads.SeatPadSlotActionPayload(0, -1));
			}
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
	private void novai$quickMoveSeatPadIntoSlot(Slot slot, int slotId, int button, SlotActionType actionType, CallbackInfo ci) {
		if (actionType != SlotActionType.QUICK_MOVE || slot == null || ClientAccountData.isSeatPadEquipped()) {
			return;
		}
		if (novai$getSeatPadRelX() < 0) {
			return;
		}
		ItemStack st = slot.getStack();
		if (!st.isOf(ModItems.ULTIMATE_SEAT_PAD)) {
			return;
		}
		int invIdx = (slot.inventory instanceof PlayerInventory) ? slot.getIndex() : -1;
		ClientAccountData.setSeatPadEquipped(true);
		if (((Object) this) instanceof CreativeInventoryScreen) {
			slot.setStack(ItemStack.EMPTY);
		}
		ClientPlayNetworking.send(new ModPayloads.SeatPadSlotActionPayload(2, invIdx));
		ci.cancel();
	}
}
