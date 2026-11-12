package com.novaimod.item;

import java.util.List;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/**
 * «Странное мясо» из кухонных сундуков на Эго-острове.
 * Очень сытное, но с непредсказуемым гастрономическим послевкусием!
 */
public class StrangeMeatItem extends Item {
	public static final FoodComponent STRANGE_MEAT_FOOD = new FoodComponent.Builder()
			.nutrition(8)
			.saturationModifier(0.85F)
			.alwaysEdible()
			.build();

	public StrangeMeatItem(Settings settings) {
		super(settings.food(STRANGE_MEAT_FOOD));
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		ItemStack result = super.finishUsing(stack, world, user);
		if (!world.isClient() && user instanceof ServerPlayerEntity player) {
			int roll = world.getRandom().nextInt(3);
			if (roll == 0) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 1));
			} else if (roll == 1) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 260, 0));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 160, 0));
			} else {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 90, 0));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, 1));
			}
		}
		return result;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Фирменная вырезка с кухни Эго-острова.").formatted(Formatting.GOLD));
		tooltip.add(Text.literal("Пахнет специями, но происхождение вызывает вопросы...").formatted(Formatting.GRAY, Formatting.ITALIC));
	}
}
