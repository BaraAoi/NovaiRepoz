package com.novaimod.block;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.economy.ItemPricingRegistry;
import com.novaimod.network.ModServerNetworking;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Ремонтный станок в шахтёрской лавке — чинит повреждённый инструмент/оружие/броню в руке за счёт игрока.
 */
public class RepairStationBlock extends Block {
	public RepairStationBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			repairHeldItem(serverPlayer);
		}
		return ActionResult.CONSUME;
	}

	public static void repairHeldItem(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty() || !stack.isDamageable() || !stack.isDamaged()) {
			stack = player.getOffHandStack();
		}
		if (stack.isEmpty() || !stack.isDamageable()) {
			player.sendMessage(
					Text.literal("[Ремонт] Возьмите повреждённый инструмент в руку для починки!").formatted(Formatting.YELLOW),
					false
			);
			return;
		}
		if (!stack.isDamaged()) {
			player.sendMessage(
					Text.literal("[Ремонт] Этот предмет уже в идеальном состоянии!").formatted(Formatting.GREEN),
					false
			);
			return;
		}

		long cost = ItemPricingRegistry.getRepairCost(stack);
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData account = state.getAccount(player.getUuid());
		if (account.balance < cost) {
			player.sendMessage(
					Text.literal("[Ремонт] Недостаточно средств! Требуется: " + cost + " ₽ (на счету: " + account.balance + " ₽)")
							.formatted(Formatting.RED),
					false
			);
			return;
		}

		account.balance -= cost;
		stack.setDamage(0);
		state.markDirty();
		ModServerNetworking.syncAccount(player);

		player.getWorld().playSound(
				null,
				player.getBlockPos(),
				SoundEvents.BLOCK_ANVIL_USE,
				SoundCategory.BLOCKS,
				0.85F,
				1.05F
		);
		player.sendMessage(
				Text.literal("[Ремонт] Инструмент полностью починен за " + cost + " ₽!").formatted(Formatting.GREEN),
				false
		);
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.literal("Нажмите ПКМ с инструментом в руке для починки за деньги со счёта.").formatted(Formatting.GRAY));
	}
}
