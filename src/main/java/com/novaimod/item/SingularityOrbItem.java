package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.SingularityOrbEntity;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Креативный артефакт «Сфера Сингулярности»:
 * запускает парящую чёрную дыру, которая затягивает всех врагов, предметы и снаряды в радиусе 22 блоков
 * и через 6 секунд схлопывается мощным сверхновым импульсом!
 */
public class SingularityOrbItem extends Item {
	public SingularityOrbItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
			SingularityOrbEntity orb = new SingularityOrbEntity(world, sp);
			Vec3d look = sp.getRotationVec(1.0F);
			orb.setPosition(sp.getX() + look.x * 1.2D, sp.getEyeY() - 0.15D + look.y * 0.8D, sp.getZ() + look.z * 1.2D);
			orb.setVelocity(look.multiply(0.72D));
			world.spawnEntity(orb);

			world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.2F, 0.6F);
			user.getItemCooldownManager().set(this, 30);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Креативный артефакт").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — запускает парящую Чёрную дыру на 6 сек,").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("стягивающую мобов и снаряды в радиусе 22 блоков,").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("после чего она взрывается звуковой сверхновой!").formatted(Formatting.GOLD));
	}
}
