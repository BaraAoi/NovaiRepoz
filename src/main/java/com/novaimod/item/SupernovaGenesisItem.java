package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.SupernovaStarEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class SupernovaGenesisItem extends Item {
	public SupernovaGenesisItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			Vec3d look = sp.getRotationVec(1.0F);
			Vec3d pos = sp.getEyePos().add(look.multiply(22.0D)).add(0.0D, 11.0D, 0.0D);

			SupernovaStarEntity star = ModEntities.SUPERNOVA_STAR_ENTITY.create(sw);
			if (star != null) {
				star.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
				star.setSeedOffset(sw.random.nextInt(100_000));
				sw.spawnEntity(star);
			}

			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.5F, 0.65F);
			sp.sendMessage(Text.literal("✹ В небе зарождается Красный Гипергигант — приготовьтесь к взрыву Сверхновой!").formatted(Formatting.GOLD, Formatting.BOLD), true);
			sp.getItemCooldownManager().set(this, 35);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("✹ КРЕАТИВНЫЙ АРТЕФАКТ КОСМОСА ✹").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ: Зажечь в небе умирающую Звезду — она раздуется в Гипергигант,").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("бесшумно схлопнется в точку и взорвётся тройным ударным кольцом,").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("оставив в небе живую вращающуюся 3D-Туманность с пульсаром!").formatted(Formatting.AQUA));
	}
}
