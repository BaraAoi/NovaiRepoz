package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.WhiteCapEntity;
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

/**
 * Яйцо призыва бойцов Клана Белых Колпаков (обычный отряд или Босс — Верховный Магистр).
 */
public class WhiteCapSpawnEggItem extends Item {
	private final boolean grandMaster;

	public WhiteCapSpawnEggItem(boolean grandMaster, Settings settings) {
		super(settings);
		this.grandMaster = grandMaster;
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

		WhiteCapEntity entity = ModEntities.WHITE_CAP_ENTITY.create(sw);
		if (entity != null) {
			entity.refreshPositionAndAngles(
					spawnPos.getX() + 0.5D,
					spawnPos.getY(),
					spawnPos.getZ() + 0.5D,
					sw.random.nextFloat() * 360.0F,
					0.0F
			);
			if (grandMaster) {
				entity.setRank(WhiteCapEntity.RANK_GRAND_MASTER);
			} else {
				entity.setRank(sw.random.nextInt(3)); // Чистильщик, Егерь-арбалетчик или Чумной Доктор
			}
			entity.setPersistent();
			sw.spawnEntity(entity);
			sw.playSound(null, spawnPos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.NEUTRAL, 1.0F, 0.9F);

			if (context.getPlayer() != null && !context.getPlayer().getAbilities().creativeMode) {
				context.getStack().decrement(1);
			}
		}
		return ActionResult.CONSUME;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		if (grandMaster) {
			tooltip.add(Text.literal("Босс Главной Цитадели Белых Колпаков (260 ХП)").formatted(Formatting.GOLD, Formatting.BOLD));
			tooltip.add(Text.literal("Вооружён Тростью Чумного Магистра и призывает охрану Клана!").formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.literal("Призывает Чистильщика, Егеря или Чумного Доктора Белых Колпаков").formatted(Formatting.AQUA));
			tooltip.add(Text.literal("Охотятся на эндерменов, визер-скелетов, всполохов, драконов и нежить!").formatted(Formatting.GRAY));
		}
	}
}
