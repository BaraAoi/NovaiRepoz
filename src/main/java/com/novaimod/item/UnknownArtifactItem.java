package com.novaimod.item;

import java.util.List;

import com.novaimod.world.ApocalypseEventManager;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Неизвестный проклятый предмет с постоянно меняющимися символами (§k / Formatting.OBFUSCATED),
 * появляющийся в самом первом открытом данж-сундуке мира.
 * Испытание на 10 минут начинается ТОЛЬКО после того, как игрок сам воспользуется предметом (ПКМ)!
 */
public class UnknownArtifactItem extends Item {
	public UnknownArtifactItem(Settings settings) {
		super(settings);
	}

	@Override
	public Text getName(ItemStack stack) {
		return Text.literal("НЕИЗВЕСТНЫЙПРЕДМЕТ").formatted(Formatting.LIGHT_PURPLE, Formatting.OBFUSCATED, Formatting.BOLD);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			if (ApocalypseEventManager.isActive(serverPlayer.getServer())) {
				return TypedActionResult.fail(stack);
			}
			if (!serverPlayer.isCreative()) {
				stack.decrement(1);
			}
			ApocalypseEventManager.startApocalypse(serverPlayer.getServer(), serverPlayer);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("ПРОКЛЯТИЕДРЕВНИХРУИН").formatted(Formatting.DARK_PURPLE, Formatting.OBFUSCATED));
		tooltip.add(Text.literal("ТЬМАПРОБУЖДАЕТСЯНАПЯТЬМИНУТ").formatted(Formatting.RED, Formatting.OBFUSCATED));
		tooltip.add(Text.literal("ИСПОЛЬЗУЙПКМЧТОБЫНАЧАТЬ").formatted(Formatting.AQUA, Formatting.OBFUSCATED));
	}
}
