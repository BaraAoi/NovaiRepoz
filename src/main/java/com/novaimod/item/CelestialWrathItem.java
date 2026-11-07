package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.CelestialOrbEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Креативный артефакт «Жезл Небесной Кары» (v3.5.0, без ограничений и кулдауна!):
 * - ПКМ: запускает в небо ослепительный Шар Света с чисто-БЕЛЫМИ объёмными лучами (как при смерти Дракона Края!).
 *   В небесах он раскалывается на 8 бурлящих шаров белых частиц, из которых во все стороны
 *   (преимущественно прицельно в мобов!) бьют ветвистые молнии, образуя мощный взрыв в каждом месте удара!
 * - Shift + ПКМ: переключение разрушения блоков взрывами (по умолчанию включено — полная мощь!).
 */
public class CelestialWrathItem extends Item {
	private static final String NBT_BREAK_BLOCKS = "novai_wrath_break_blocks";

	public CelestialWrathItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static boolean isBreakBlocksEnabled(ItemStack stack) {
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		if (!comp.contains(NBT_BREAK_BLOCKS)) {
			return true; // По умолчанию полная креативная мощь со взрывными кратерами!
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
						Text.literal("Жезл Небесной Кары: ")
								.formatted(Formatting.GOLD)
								.append(Text.literal(next ? "«Полная мощь (взрывы с кратерами)»" : "«Взрывы без разрушения блоков»")
										.formatted(next ? Formatting.RED : Formatting.GREEN, Formatting.BOLD)),
						true
				);
				world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.MASTER, 0.85F, next ? 0.85F : 1.35F);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		if (!world.isClient() && user instanceof ServerPlayerEntity sp && world instanceof ServerWorld sw) {
			HitResult hit = sp.raycast(48.0D, 0.0F, false);
			Vec3d startPos;
			if (hit.getType() != HitResult.Type.MISS) {
				startPos = hit.getPos().add(0.0D, 1.6D, 0.0D);
			} else {
				Vec3d look = sp.getRotationVec(1.0F);
				startPos = sp.getEyePos().add(look.x * 8.0D, Math.max(-1.0D, look.y * 4.0D) + 1.0D, look.z * 8.0D);
			}

			CelestialOrbEntity ascentOrb = new CelestialOrbEntity(
					sw,
					startPos.x,
					startPos.y,
					startPos.z,
					sp.getUuid(),
					CelestialOrbEntity.PHASE_ASCENDING_LIGHT,
					isBreakBlocksEnabled(stack),
					0
			);
			sw.spawnEntity(ascentOrb);

			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 1.1F, 1.35F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.5F, 1.15F);
		}

		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		boolean breakBlocks = isBreakBlocksEnabled(stack);
		tooltip.add(Text.literal("Креативный артефакт (Без ограничений)").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — в небо взмывает Шар Света с белыми лучами,").formatted(Formatting.WHITE));
		tooltip.add(Text.literal("создавая в небесах шары частиц, из которых во все").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("стороны (преимущественно в мобов!) бьют молнии со взрывами!").formatted(Formatting.AQUA));
		tooltip.add(
				Text.literal("Shift + ПКМ — взрывы: ").formatted(Formatting.DARK_GRAY)
						.append(Text.literal(breakBlocks ? "Полная мощь (с кратерами)" : "Без порчи построек")
								.formatted(breakBlocks ? Formatting.RED : Formatting.GREEN))
		);
	}
}
