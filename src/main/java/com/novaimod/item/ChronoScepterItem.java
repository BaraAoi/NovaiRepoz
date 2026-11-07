package com.novaimod.item;

import java.util.List;

import com.novaimod.world.TimeStopManager;

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
 * Креативный артефакт «Хроно-Скипетр» (без ограничений и кулдауна):
 * - ПКМ: останавливает ВСЁ время в игре НАВСЕГДА во всех измерениях (включая анимации боссов, Дракона Края, частиц, жидкостей и неба)!
 * - Повторный ПКМ: запускает ход времени обратно.
 */
public class ChronoScepterItem extends Item {
	public ChronoScepterItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
			TimeStopManager.toggleGlobalTimeStop(sp);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Креативный артефакт (Без ограничений)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — останавливает ВСЁ время в игре НАВСЕГДА:").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("замирают все измерения, солнце, жидкости, частицы,").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("снаряды и анимации всех мобов и Дракона Края!").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Повторный ПКМ — возобновляет ход времени.").formatted(Formatting.GOLD));
	}
}
