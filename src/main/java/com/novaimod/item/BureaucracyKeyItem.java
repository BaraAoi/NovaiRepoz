package com.novaimod.item;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * «Ключ Бюрократии» (Пропуск Формы №666-Б) — легальный способ попасть в измерение «Бюрократический Ад».
 * Нажмите ПКМ этим ключом по ЛЮБОЙ ДВЕРИ в мире — и она поведёт прямо в Приёмную Министерства (Уровень 1)!
 */
public class BureaucracyKeyItem extends Item {
	public BureaucracyKeyItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Пропуск Формы №666-Б (С сургучной печатью Министерства)").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("Нажмите ПКМ этим ключом по ЛЮБОЙ ДВЕРИ в мире —").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("и эта дверь откроет проход в измерение «Бюрократический Ад»!").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("Внутри кликните по Двери Выхода в Приёмной, чтобы вернуться домой.").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Легальная выдача: закрыть кредит от 5 000 ₽ в МФО, накопить 25 000 ₽ или отсидеть срок в СИЗО.").formatted(Formatting.DARK_GRAY));
	}
}
