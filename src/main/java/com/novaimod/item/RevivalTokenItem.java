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
 * Жетон возрождения — выдаётся на Лавовом курорте в Аду или выигрывается в Автомате судьбы.
 * Позволяет выбрать и возродить одного погибшего / находящегося в режиме наблюдателя (хардкор) игрока.
 */
public class RevivalTokenItem extends Item {
	public RevivalTokenItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			ModServerNetworking.openRevivalSelection(serverPlayer);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Позволяет возродить одного павшего игрока на выбор").formatted(Formatting.GOLD));
		tooltip.add(Text.literal("(включая погибших в режиме Хардкор).").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("Нажмите ПКМ, чтобы выбрать игрока.").formatted(Formatting.GRAY));
	}
}
