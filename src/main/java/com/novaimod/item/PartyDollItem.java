package com.novaimod.item;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.world.ApocalypseEventManager;

import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Предмет для установки женской Танцующей куклы высотой ровно в 1 блок.
 * Во время 5-минутного испытания куклу можно применить лишь один раз (на 30 секунд),
 * после чего до самого конца испытания повторное использование не работает!
 */
public class PartyDollItem extends Item {
	public PartyDollItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();

		if (!world.isClient() && world instanceof ServerWorld sw && ApocalypseEventManager.isActive(sw.getServer())) {
			ModPersistentState state = ModPersistentState.get(sw.getServer());
			if (state.isApocalypseDollUsed()) {
				if (player != null) {
					Text msg = Text.literal("По каким-то причинам она не работает...").formatted(Formatting.RED);
					player.sendMessage(msg, false);
					player.sendMessage(msg, true);
				}
				sw.playSound(null, context.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.9F, 0.75F);
				return ActionResult.FAIL;
			}
		}

		BlockPos targetBase = context.getBlockPos().offset(context.getSide());
		if (!world.getBlockState(targetBase).isReplaceable()) {
			return ActionResult.FAIL;
		}
		Box checkBox = new Box(targetBase).contract(0.1D);
		if (!world.getEntitiesByClass(PartyDollEntity.class, checkBox, d -> !d.isRemoved()).isEmpty()) {
			return ActionResult.FAIL;
		}

		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}

		float yaw = player != null ? (player.getYaw() + 180.0F) % 360.0F : 0.0F;
		PartyDollEntity doll = PartyDollEntity.placeDoll(world, targetBase.down(), yaw);
		if (doll == null) {
			return ActionResult.FAIL;
		}

		world.playSound(null, targetBase, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 0.9F, 1.15F);
		if (player == null || !player.isInCreativeMode()) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType options) {
		tooltip.add(Text.literal("Женская кукла в 1 блок (ПКМ — включить танец и дружбу, ЛКМ — забрать)").formatted(Formatting.LIGHT_PURPLE));
	}
}
