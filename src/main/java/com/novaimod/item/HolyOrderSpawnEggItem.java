package com.novaimod.item;

import java.util.List;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.HolyOrderEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Яйцо призыва бойцов «Ордена Святой Люче» (Shift + ПКМ призывает Босса — Архиепископа Собора!).
 */
public class HolyOrderSpawnEggItem extends Item {
	public HolyOrderSpawnEggItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(world instanceof ServerWorld sw)) {
			return ActionResult.PASS;
		}

		BlockPos pos = context.getBlockPos().offset(context.getSide());
		HolyOrderEntity member = ModEntities.HOLY_ORDER_ENTITY.create(sw);
		if (member != null) {
			member.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
			boolean boss = context.getPlayer() != null && context.getPlayer().isSneaking();
			int rank = boss ? HolyOrderEntity.RANK_CARDINAL : sw.random.nextInt(3);
			member.refreshPositionAndAngles(
					pos.getX() + 0.5D,
					pos.getY(),
					pos.getZ() + 0.5D,
					context.getPlayer() != null ? context.getPlayer().getYaw() + 180.0F : 0.0F,
					0.0F
			);
			member.setRank(rank);
			member.setHomeAnchor(pos, 26);
			sw.spawnEntity(member);
			sw.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.9F, 1.2F);
		}

		if (context.getPlayer() == null || !context.getPlayer().isInCreativeMode()) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Призывает Паладина, Арбалетчика Инквизиции или Священника Ордена Люче").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Shift + ПКМ: Призвать Босса «Архиепископ Собора Люче»!").formatted(Formatting.GOLD));
	}
}
