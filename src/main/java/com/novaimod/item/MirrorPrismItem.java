package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.PrismCathedralEntity;
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
import net.minecraft.world.World;

public class MirrorPrismItem extends Item {
	public MirrorPrismItem(Settings settings) {
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
			PrismCathedralEntity cathedral = ModEntities.PRISM_CATHEDRAL_ENTITY.create(sw);
			if (cathedral != null) {
				cathedral.refreshPositionAndAngles(sp.getX(), sp.getY(), sp.getZ(), 0.0F, 0.0F);
				cathedral.setOwner(sp);
				sw.spawnEntity(cathedral);
			}
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.0F, 1.35F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 1.5F, 1.55F);
			sp.sendMessage(Text.literal("💠 Вокруг вас раскрылся Купол Зеркального Измерения!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
			sp.getItemCooldownManager().set(this, 30);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("💠 КРЕАТИВНАЯ ПРИЗМА ЗЕРКАЛЬНОГО ИЗМЕРЕНИЯ 💠").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ: Развернуть вращающийся 3D-собор из 24 призматических").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("осколков, мандал и преломлённых радужных лазеров!").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("Подвешивает врагов и снаряды в невесомости и поражает залпом призм!").formatted(Formatting.GRAY));
	}
}
