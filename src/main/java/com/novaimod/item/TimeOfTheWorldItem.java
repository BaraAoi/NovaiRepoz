package com.novaimod.item;

import java.util.List;

import com.novaimod.world.TimeOfTheWorldManager;

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
 * «Made in Heaven: Time of the World» — верховный креативный хроно-артефакт:
 * - ПКМ: фиксирует Точку Отсчёта Вселенной и плавно ускоряет смену Дня и Ночи всё быстрее и быстрее
 *   (до 2450x скорости под огромной 3D Небесной Астролябией!), а в кульминации совершает Большой Взрыв
 *   и перезапускает мир, возвращая солнце, блоки, существ и игроков на свои исходные места!
 * - Повторный ПКМ во время ускорения: мгновенно вызывает Сингулярность и перезапуск мира.
 * - Shift + ПКМ во время ускорения: останавливает разгон в текущей эпохе без отката.
 */
public class TimeOfTheWorldItem extends Item {
	public TimeOfTheWorldItem(Settings settings) {
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
			TimeOfTheWorldManager.activateOrAdvance(sp, sp.isSneaking());
			sp.getItemCooldownManager().set(this, 12);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("✦ КРЕАТИВНЫЙ АРТЕФАКТ ЭПОХ («MADE IN HEAVEN») ✦").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ: Запустить плавное ускорение Времени и смену Дня и Ночи").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("от 1x до ∞x под Небесной Астролябией — в финале Вселенная").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("перезапускается, и ВСЁ возвращается на свои места!").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("Повторный ПКМ: Мгновенный перезапуск | Shift+ПКМ: Оставить эпоху").formatted(Formatting.GRAY));
	}
}
