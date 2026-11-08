package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.BabylonTreasuryEntity;
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

public class GateOfBabylonItem extends Item {
	public GateOfBabylonItem(Settings settings) {
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
			boolean crown = sp.isSneaking();
			BabylonTreasuryEntity treasury = ModEntities.BABYLON_TREASURY_ENTITY.create(sw);
			if (treasury != null) {
				treasury.refreshPositionAndAngles(sp.getX(), sp.getY(), sp.getZ(), sp.getYaw(), 0.0F);
				treasury.setOwner(sp);
				treasury.setCrownMode(crown);
				sw.spawnEntity(treasury);
			}
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.5F, 1.35F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.8F, 1.15F);
			sp.sendMessage(
					Text.literal(crown
							? "⚜ ВРАТА ВАВИЛОНА: 32 золотых портала сомкнулись в Небесную Корону!"
							: "⚜ ВРАТА ВАВИЛОНА: 18 золотых порталов открылись за вашей спиной!")
							.formatted(Formatting.GOLD, Formatting.BOLD),
					true
			);
			sp.getItemCooldownManager().set(this, 28);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("⚜ КРЕАТИВНАЯ НЕБЕСНАЯ СОКРОВИЩНИЦА ⚜").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ: Открыть за плечами 18 сияющих золотых 3D-порталов,").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("выпускающих ураганный залп световых копий по вашему взгляду!").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("Shift + ПКМ: Небесная Корона из 32 золотых врат вокруг вас!").formatted(Formatting.AQUA));
	}
}
