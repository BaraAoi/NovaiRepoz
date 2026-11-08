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
 * Творческий предмет, позволяющий настраивать шансы на любой исход всех автоматов и столов казино.
 */
public class OddsConfiguratorItem extends Item {
	public OddsConfiguratorItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			if (!serverPlayer.isCreative() && !serverPlayer.hasPermissionLevel(2)) {
				return TypedActionResult.fail(stack);
			}
			ModServerNetworking.openOddsConfigScreen(serverPlayer);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Творческий пульт управления вероятностями.").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("ПКМ — изменить шансы всех автоматов и столов казино.").formatted(Formatting.GRAY));
	}
}
