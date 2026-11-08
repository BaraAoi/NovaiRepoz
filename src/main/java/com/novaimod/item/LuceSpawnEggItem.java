package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.LuceEntity;
import com.novaimod.registry.ModEntities;
import com.novaimod.world.LuceWorldManager;

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
 * Яйцо призыва: Люче (и её пёсик Сантино!).
 */
public class LuceSpawnEggItem extends Item {
	public LuceSpawnEggItem(Settings settings) {
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

		BlockPos spawnPos = context.getBlockPos().offset(context.getSide());
		LuceEntity luce = ModEntities.LUCE_ENTITY.create(sw);
		if (luce != null) {
			luce.refreshPositionAndAngles(
					spawnPos.getX() + 0.5D,
					spawnPos.getY(),
					spawnPos.getZ() + 0.5D,
					context.getPlayer() != null ? context.getPlayer().getYaw() + 180.0F : 0.0F,
					0.0F
			);
			luce.setHomePos(spawnPos);
			sw.spawnEntity(luce);
			boolean sneak = context.getPlayer() != null && context.getPlayer().isSneaking();
			boolean hasSantinoNearby = !sw.getEntitiesByClass(
					net.minecraft.entity.passive.WolfEntity.class,
					new net.minecraft.util.math.Box(spawnPos).expand(14.0D),
					w -> w.isAlive() && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG)
			).isEmpty();
			if (sneak || !hasSantinoNearby) {
				LuceWorldManager.spawnSantinoDog(sw, spawnPos.add(1, 0, 0));
			}
			sw.playSound(null, spawnPos, SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.9F, 1.3F);
		}

		if (context.getPlayer() == null || !context.getPlayer().isInCreativeMode()) {
			context.getStack().decrement(1);
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Призывает Люче в жёлтом дождевике вместе с её пёсиком Сантино!").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Мирная спутница для праведников + Гневный Святой Босс против грешников!").formatted(Formatting.GOLD));
	}
}
