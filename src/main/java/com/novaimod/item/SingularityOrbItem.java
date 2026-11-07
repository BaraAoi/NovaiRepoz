package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.SingularityOrbEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Креативный артефакт «Сфера Сингулярности» (без ограничений и кулдауна):
 * запускает объёмную Чёрную дыру с 3D аккреционным диском, затягивающую всех мобов, боссов, снаряды и предметы
 * в радиусе 40 блоков, а в финале схлопывающуюся колоссальным взрывом сверхновой!
 */
public class SingularityOrbItem extends Item {
	private static final String NBT_BREAK_BLOCKS = "novai_singularity_break";

	public SingularityOrbItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static boolean isBreakBlocksEnabled(ItemStack stack) {
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		if (!comp.contains(NBT_BREAK_BLOCKS)) {
			return true;
		}
		return comp.copyNbt().getBoolean(NBT_BREAK_BLOCKS);
	}

	private static void setBreakBlocksEnabled(ItemStack stack, boolean value) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_BREAK_BLOCKS, value);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.isSneaking()) {
			if (!world.isClient()) {
				boolean next = !isBreakBlocksEnabled(stack);
				setBreakBlocksEnabled(stack, next);
				user.sendMessage(
						Text.literal("Сфера Сингулярности: ")
								.formatted(Formatting.LIGHT_PURPLE)
								.append(Text.literal(next ? "«Полный коллапс (разрушает блоки)»" : "«Гравитация без разрушения блоков»")
										.formatted(next ? Formatting.RED : Formatting.GREEN, Formatting.BOLD)),
						true
				);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
			SingularityOrbEntity orb = new SingularityOrbEntity(world, sp, isBreakBlocksEnabled(stack));
			Vec3d look = sp.getRotationVec(1.0F);
			orb.setPosition(sp.getX() + look.x * 1.4D, sp.getEyeY() - 0.1D + look.y * 0.9D, sp.getZ() + look.z * 1.4D);
			orb.setVelocity(look.multiply(0.85D));
			world.spawnEntity(orb);

			world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.MASTER, 1.4F, 0.55F);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		boolean breakBlocks = isBreakBlocksEnabled(stack);
		tooltip.add(Text.literal("Креативный артефакт (Без ограничений)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — запускает объёмную 3D Чёрную дыру,").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("стягивающую всё живое в радиусе 40 блоков,").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("и взрывающуюся сверхновой в финале!").formatted(Formatting.GOLD));
		tooltip.add(
				Text.literal("Shift + ПКМ — коллапс: ").formatted(Formatting.DARK_GRAY)
						.append(Text.literal(breakBlocks ? "С разрушением ландшафта" : "Без порчи построек")
								.formatted(breakBlocks ? Formatting.RED : Formatting.GREEN))
		);
	}
}
