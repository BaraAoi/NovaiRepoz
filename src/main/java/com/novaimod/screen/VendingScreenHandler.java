package com.novaimod.screen;

import com.novaimod.data.ModPersistentState;
import com.novaimod.economy.ItemPricingRegistry;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModScreenHandlers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Контейнер торгового аппарата (9 слотов приёма любых предметов + инвентарь игрока).
 * Если игрок закрывает окно без продажи, все предметы возвращаются ему.
 */
public class VendingScreenHandler extends ScreenHandler {
	public static final int MACHINE_SLOTS = 9;
	private final Inventory machineInventory;

	public VendingScreenHandler(int syncId, PlayerInventory playerInventory) {
		this(syncId, playerInventory, new SimpleInventory(MACHINE_SLOTS));
	}

	public VendingScreenHandler(int syncId, PlayerInventory playerInventory, Inventory machineInventory) {
		super(ModScreenHandlers.VENDING_SCREEN_HANDLER, syncId);
		checkSize(machineInventory, MACHINE_SLOTS);
		this.machineInventory = machineInventory;
		machineInventory.onOpen(playerInventory.player);

		// 3x3 сетка приёма предметов (слева вверху окна)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				this.addSlot(new Slot(machineInventory, col + row * 3, 26 + col * 18, 18 + row * 18));
			}
		}

		// Основной инвентарь игрока (27 слотов)
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}

		// Хотбар игрока (9 слотов)
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	public Inventory getMachineInventory() {
		return machineInventory;
	}

	public long calculateTotalValue() {
		long total = 0L;
		for (int i = 0; i < MACHINE_SLOTS; i++) {
			ItemStack stack = machineInventory.getStack(i);
			if (!stack.isEmpty()) {
				total += ItemPricingRegistry.getStackPrice(stack);
			}
		}
		return total;
	}

	/**
	 * Продаёт все предметы, лежащие в слотах торгового аппарата, и начисляет сумму на виртуальный счёт.
	 */
	public void sellAllItems(ServerPlayerEntity player) {
		long totalEarned = 0L;
		int soldStacks = 0;
		for (int i = 0; i < MACHINE_SLOTS; i++) {
			ItemStack stack = machineInventory.getStack(i);
			if (!stack.isEmpty()) {
				totalEarned += ItemPricingRegistry.getStackPrice(stack);
				machineInventory.setStack(i, ItemStack.EMPTY);
				soldStacks++;
			}
		}
		if (soldStacks > 0 && totalEarned > 0L) {
			ModPersistentState state = ModPersistentState.get(player.getServer());
			ModPersistentState.PlayerFinanceData account = state.getAccount(player.getUuid());
			account.balance += totalEarned;
			state.markDirty();

			ModServerNetworking.syncAccount(player);
			player.getWorld().playSound(
					null,
					player.getBlockPos(),
					SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
					SoundCategory.PLAYERS,
					0.9F,
					1.2F
			);
			this.sendContentUpdates();
		}
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == 0 && player instanceof ServerPlayerEntity serverPlayer) {
			sellAllItems(serverPlayer);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int slotIndex) {
		ItemStack newStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasStack()) {
			ItemStack originalStack = slot.getStack();
			newStack = originalStack.copy();
			if (slotIndex < MACHINE_SLOTS) {
				if (!this.insertItem(originalStack, MACHINE_SLOTS, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.insertItem(originalStack, 0, MACHINE_SLOTS, false)) {
				return ItemStack.EMPTY;
			}

			if (originalStack.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}
		}
		return newStack;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return true;
	}

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);
		this.dropInventory(player, this.machineInventory);
	}
}
