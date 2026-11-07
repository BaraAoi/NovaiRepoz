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
 * Креативный артефакт «Хроно-Скипетр»:
 * - ПКМ: создаёт купол остановки времени радиусом 28 блоков на 8 секунд (мобы и стрелы застывают в воздухе!).
 * - Shift + ПКМ: досрочно восстанавливает ход времени.
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
			if (user.isSneaking() && TimeStopManager.hasActiveDomeOwnedBy(sp.getUuid())) {
				TimeStopManager.releaseOwnedDomesEarly(sp);
			} else {
				TimeStopManager.activateDome(sp);
				user.getItemCooldownManager().set(this, 20);
			}
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Креативный артефакт").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — останавливает время на 8 сек в радиусе 28 блоков:").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("все мобы и летящие снаряды замирают прямо в воздухе!").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("Shift + ПКМ — досрочно снять заморозку времени.").formatted(Formatting.DARK_GRAY));
	}
}
