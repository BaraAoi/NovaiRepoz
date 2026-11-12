package com.novaimod.block;

import java.util.List;

import com.novaimod.network.ModServerNetworking;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Полноценный игровой стол «Рулетка» в казино (0–36, плавное замедление колеса,
 * и если 1 раз выпало зелёное — второй раз подряд выпадает со 100% шансом).
 */
public class RouletteTableBlock extends Block {
	public RouletteTableBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (player instanceof ServerPlayerEntity serverPlayer) {
			ModServerNetworking.openRouletteScreen(serverPlayer);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.literal("Полноценная европейская рулетка казино (0–36).").formatted(Formatting.GREEN));
	}
}
