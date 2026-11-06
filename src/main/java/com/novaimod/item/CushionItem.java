package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.CushionEntity;

import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Предмет «Подушка» (16 цветов): ставится на любые ванильные ступеньки, плиты или блоки,
 * превращая их в мягкое место для сидения (включая сиденья внутри джакузи!).
 */
public class CushionItem extends Item {
	private final DyeColor color;

	public CushionItem(DyeColor color, Settings settings) {
		super(settings);
		this.color = color;
	}

	public DyeColor getColor() {
		return this.color;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);

		boolean isSeatBlock = state.getBlock() instanceof StairsBlock || state.getBlock() instanceof SlabBlock;
		if (!isSeatBlock && context.getSide() != Direction.UP) {
			return ActionResult.PASS;
		}
		if (!isSeatBlock && !state.isSideSolidFullSquare(world, pos, Direction.UP)) {
			return ActionResult.PASS;
		}

		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}

		PlayerEntity player = context.getPlayer();
		Direction seatFacing = player != null ? player.getHorizontalFacing().getOpposite() : Direction.NORTH;
		CushionEntity placed = CushionEntity.placeOnBlock(world, pos, this.color, seatFacing);
		if (placed == null) {
			return ActionResult.FAIL;
		}

		world.playSound(null, pos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 0.9F, 1.0F);
		ItemStack stack = context.getStack();
		if (player == null || !player.isInCreativeMode()) {
			stack.decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.literal("Ставится на ступеньки, плиты и блоки (ПКМ — сесть, ЛКМ — снять).").formatted(Formatting.GRAY));
	}
}
