package com.novaimod.item;

import java.util.List;

import com.novaimod.equipment.UltimateArmorManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Единый невидимый предмет Альтимейт-защиты — «Поджопник» (v3.7.0):
 * - Надевается в специальный скрытый слот инвентаря (который виден только при наличии Поджопника) или по ПКМ.
 * - Полностью невидим на персонаже (0 элементов на скине, свободные слоты обычной брони).
 * - Поглощает 100% ВСЕГО урона в игре, кроме чистого Процентного урона!
 * - Даёт свободный полёт в любом режиме игры.
 */
public class UltimateSeatPadItem extends Item {
	public UltimateSeatPadItem(Settings settings) {
		super(settings.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
			if (!UltimateArmorManager.isWearingFullUltimateArmor(sp)) {
				UltimateArmorManager.setSeatPadEquipped(sp, true);
				if (!sp.isCreative()) {
					stack.decrement(1);
				}
				world.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 1.0F, 1.15F);
				return TypedActionResult.success(stack, false);
			}
		}
		return TypedActionResult.pass(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Единая Альтимейт-Броня (Секретный слот)").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("• Полностью невидим на персонаже").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("• Поглощает 100% ВСЕГО урона в игре").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("  (кроме эффекта «Процентный урон»!)").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("• Даёт свободный полёт в любом режиме!").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Наденьте в особый слот инвентаря или нажмите ПКМ").formatted(Formatting.DARK_GRAY));
	}
}
