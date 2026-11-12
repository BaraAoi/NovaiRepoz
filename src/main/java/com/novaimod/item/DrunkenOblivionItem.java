package com.novaimod.item;

import java.util.List;

import com.novaimod.world.MysterySkyManager;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Коктейль «Пьяное забвение».
 * Описание не раскрывает никаких подробностей о переносе или локации.
 */
public class DrunkenOblivionItem extends Item {
	public DrunkenOblivionItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		return ItemUsage.consumeHeldItem(world, user, hand);
	}

	@Override
	public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			if (!user.isInCreativeMode()) {
				stack.decrement(1);
			}
			// Сначала накладывается Темнота, и только затем происходит перенос!
			MysterySkyManager.beginDrunkenOblivionSequence(serverPlayer);
		}
		return stack;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 32;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.DRINK;
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(
				Text.literal("Пьяное забвение, коктель который заглушает все тревоги о ушедших людях")
						.formatted(Formatting.GRAY)
		);
	}
}
