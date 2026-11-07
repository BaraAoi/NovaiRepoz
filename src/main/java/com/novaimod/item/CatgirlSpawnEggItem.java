package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.CatgirlEntity;
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
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CatgirlSpawnEggItem extends Item {
	public CatgirlSpawnEggItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		if (!(world instanceof ServerWorld sw)) {
			return ActionResult.SUCCESS;
		}

		BlockPos pos = context.getBlockPos();
		Direction side = context.getSide();
		BlockPos spawnPos = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() ? pos : pos.offset(side);

		CatgirlEntity catgirl = ModEntities.CATGIRL_ENTITY.create(sw);
		if (catgirl != null) {
			catgirl.refreshPositionAndAngles(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, sw.random.nextFloat() * 360.0F, 0.0F);
			catgirl.setVariantId(sw.random.nextInt(4));
			catgirl.setRescued(true);
			if (context.getPlayer() != null) {
				catgirl.setOwnerUuid(context.getPlayer().getUuid());
			}
			catgirl.setPersistent();
			sw.spawnEntity(catgirl);
			sw.playSound(null, spawnPos, SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.0F, 1.15F);

			if (context.getPlayer() != null && !context.getPlayer().getAbilities().creativeMode) {
				context.getStack().decrement(1);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Призывает спасённую Кошкодевочку (Широ / Куро / Мико / Юки)").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("Также их можно найти и спасти из клетки в Заброшенных Базах Учёных!").formatted(Formatting.GRAY));
	}
}
