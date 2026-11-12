package com.novaimod.item;

import java.util.List;

import com.novaimod.network.ModServerNetworking;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Карманный телепорт — строго 1 экземпляр на весь мир, продаётся только в микрозайме (МФО).
 * За 1000 ₽ со счёта позволяет телепортировать выбранного игрока к себе или себя к выбранному игроку.
 */
public class PlayerTeleporterItem extends Item {
	public static final long TELEPORT_USE_COST = 1000L;

	public PlayerTeleporterItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			ModServerNetworking.openTeleporterScreen(serverPlayer);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Единственный экземпляр в мире (эксклюзив МФО)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("За 1000 ₽ телепортирует игрока к вам или вас к игроку.").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("Нажмите ПКМ, чтобы выбрать игрока и режим.").formatted(Formatting.GRAY));
	}
}
