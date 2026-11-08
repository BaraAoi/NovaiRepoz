package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.HolyWaterEntity;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Метательный флакон «Святой Воды Собора Люче» (`novaimod:holy_water`).
 */
public class HolyWaterItem extends Item {
	public HolyWaterItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw) {
			sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_SPLASH_POTION_THROW, SoundCategory.PLAYERS, 0.9F, 1.1F);
			HolyWaterEntity flask = new HolyWaterEntity(sw, user);
			flask.setItem(stack);
			flask.setPosition(user.getX(), user.getEyeY() - 0.1D, user.getZ());
			flask.setVelocity(user, user.getPitch(), user.getYaw(), -12.0F, 1.05F, 1.0F);
			sw.spawnEntity(flask);
			user.getItemCooldownManager().set(this, 10);
			if (!user.getAbilities().creativeMode) {
				stack.decrement(1);
			}
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Освящённый флакон Кафедрального Собора Люче").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("• ПКМ: Лечит союзников и Орден (+10 HP), смывает яд и иссушение КБК").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("• Выжигает еретиков Клана Белых Колпаков (КБК) и нежить!").formatted(Formatting.YELLOW));
	}
}
