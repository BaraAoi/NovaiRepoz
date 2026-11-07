package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.InfinityEyeEntity;

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
 * Креативный артефакт «Око Бескрайности»:
 * - ПКМ: запускает летящую сущность Ока, которая парит и выпускает 48 пространственных жемчугов,
 *   каскадно телепортирующих владельца по всей карте и БЕСШОВНО в другие измерения (Незер, Энд, Верхний мир)!
 * - Shift + ПКМ: переключает режим переноса (Все измерения + Карта <-> Только текущее измерение).
 */
public class InfinityEyeItem extends Item {
	private static final String NBT_CROSS_DIM = "novai_eye_cross_dim";

	public InfinityEyeItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static boolean isCrossDimensionEnabled(ItemStack stack) {
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		if (!comp.contains(NBT_CROSS_DIM)) {
			return true; // По умолчанию включён полный межпространственный режим!
		}
		return comp.copyNbt().getBoolean(NBT_CROSS_DIM);
	}

	private static void setCrossDimensionEnabled(ItemStack stack, boolean enabled) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_CROSS_DIM, enabled);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (user.isSneaking()) {
			if (!world.isClient()) {
				boolean next = !isCrossDimensionEnabled(stack);
				setCrossDimensionEnabled(stack, next);
				user.sendMessage(
						Text.literal("Око Бескрайности: режим ")
								.formatted(Formatting.LIGHT_PURPLE)
								.append(Text.literal(next ? "«Карта + Другие измерения (Бесшовно)»" : "«Только по текущей карте»")
										.formatted(next ? Formatting.AQUA : Formatting.GOLD, Formatting.BOLD)),
						true
				);
				world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 0.85F, next ? 1.35F : 0.95F);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
			boolean crossDim = isCrossDimensionEnabled(stack);
			InfinityEyeEntity eye = new InfinityEyeEntity(world, serverPlayer, crossDim);
			Vec3d look = serverPlayer.getRotationVec(1.0F);
			eye.setPosition(
					serverPlayer.getX() + look.x * 1.1D,
					serverPlayer.getEyeY() - 0.1D + look.y * 0.6D,
					serverPlayer.getZ() + look.z * 1.1D
			);
			eye.setVelocity(look.x * 0.56D, Math.max(0.08D, look.y * 0.45D + 0.12D), look.z * 0.56D);
			world.spawnEntity(eye);

			world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.ENTITY_ENDER_EYE_LAUNCH, SoundCategory.PLAYERS, 1.15F, 0.78F);
			world.playSound(null, serverPlayer.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 0.95F, 1.25F);
		}

		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		boolean crossDim = isCrossDimensionEnabled(stack);
		tooltip.add(Text.literal("Креативный артефакт").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — запускает парящее Око, выпускающее десятки").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("жемчугов с телепортом по карте и в другие измерения!").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Бесшовный переход между мирами без экрана загрузки.").formatted(Formatting.AQUA));
		tooltip.add(
				Text.literal("Shift + ПКМ — режим: ").formatted(Formatting.DARK_GRAY)
						.append(Text.literal(crossDim ? "Карта + Измерения" : "Только по карте")
								.formatted(crossDim ? Formatting.GREEN : Formatting.YELLOW))
		);
	}
}
