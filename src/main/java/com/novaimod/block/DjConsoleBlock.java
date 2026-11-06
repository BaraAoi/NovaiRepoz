package com.novaimod.block;

import java.util.List;

import com.novaimod.world.EgoPartyManager;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Диджейский пульт у танцпола на Эго-острове:
 * запускает светомузыку переливающегося танцпола, заставляет гостей танцевать и даёт бонус за танцы!
 */
public class DjConsoleBlock extends Block {
	public DjConsoleBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
			EgoPartyManager.startDancefloorRave(serverWorld, pos, serverPlayer);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.literal("ПКМ — запустить светомузыку и тусовку на танцполе!").formatted(Formatting.LIGHT_PURPLE));
	}
}
