package com.novaimod.item;

import java.util.List;

import com.novaimod.world.ApocalypseEventManager;

import net.minecraft.entity.Entity;
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
 * Неизвестный проклятый предмет с постоянно меняющимися символами (§k / Formatting.OBFUSCATED),
 * появляющийся в самом первом открытом данж-сундуке мира и запускающий 10-минутное максимальное усложнение мобов!
 */
public class UnknownArtifactItem extends Item {
	public UnknownArtifactItem(Settings settings) {
		super(settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.literal("НЕИЗВЕСТНЫЙПРЕДМЕТ").formatted(Formatting.LIGHT_PURPLE, Formatting.OBFUSCATED, Formatting.BOLD);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		if (world.isClient()) {
			return;
		}
		if (entity instanceof ServerPlayerEntity serverPlayer && !serverPlayer.isCreative() && !serverPlayer.isSpectator()) {
			stack.decrement(stack.getCount());
			ApocalypseEventManager.startApocalypse(serverPlayer.getServer(), serverPlayer);
		}
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			if (!serverPlayer.isCreative()) {
				stack.decrement(1);
			}
			ApocalypseEventManager.startApocalypse(serverPlayer.getServer(), serverPlayer);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("ПРОКЛЯТИЕДРЕВНИХРУИН").formatted(Formatting.DARK_PURPLE, Formatting.OBFUSCATED));
		tooltip.add(Text.literal("ТЬМАПРОБУЖДАЕТСЯНАДЕСЯТЬМИНУТ").formatted(Formatting.RED, Formatting.OBFUSCATED));
		tooltip.add(Text.literal("ВЫЖИВИИЛИПОГИБНИ").formatted(Formatting.AQUA, Formatting.OBFUSCATED));
	}
}
