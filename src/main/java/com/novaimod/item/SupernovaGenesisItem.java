package com.novaimod.item;

import java.util.List;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.SupernovaStarEntity;
import com.novaimod.registry.ModEntities;

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
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class SupernovaGenesisItem extends Item {
	private static final String KEY_MODE = "SupernovaMode";

	public SupernovaGenesisItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		return (comp != null && comp.copyNbt().getInt(KEY_MODE) == 1) ? 1 : 0;
	}

	public static String modeTitle(int mode) {
		return mode == 1
				? "Звёздный Рентген-Карьер и Кузница (Испаряет породу, оголяя все руды + Уголь -> Алмазы)"
				: "Термоядерный Катаклизм (Вырывает глыбы в небо + Кратер Древних Обломков и Алмазов)";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking()) {
				int next = getMode(stack) == 0 ? 1 : 0;
				NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				nbt.putInt(KEY_MODE, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.35F);
				sp.sendMessage(Text.literal("✹ Режим Сверхновой: ").formatted(Formatting.GOLD)
						.append(Text.literal(modeTitle(next)).formatted(Formatting.AQUA, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			int mode = getMode(stack);
			Vec3d eye = sp.getCameraPosVec(1.0F);
			Vec3d look = sp.getRotationVec(1.0F);
			HitResult hit = sw.raycast(new RaycastContext(
					eye, eye.add(look.multiply(84.0D)),
					RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp
			));
			Vec3d spawnPos = hit.getType() != HitResult.Type.MISS
					? hit.getPos().add(0.0D, 6.0D, 0.0D)
					: eye.add(look.multiply(26.0D));

			SupernovaStarEntity star = ModEntities.SUPERNOVA_STAR_ENTITY.create(sw);
			if (star != null) {
				star.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
				star.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
				star.setForgeMode(mode);
				sw.spawnEntity(star);
			}

			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.6F, 0.65F);
			sp.sendMessage(Text.literal(mode == 0
					? "✹ КАТАКЛИЗМ СВЕРХНОВОЙ: Гипергигант разрывает гравитацию над целью!"
					: "✹ КУЗНИЦА СВЕРХНОВОЙ: Запущен рентген-карьер руд и синтез алмазов!")
					.formatted(Formatting.GOLD, Formatting.BOLD), true);
			sp.getItemCooldownManager().set(this, 30);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int mode = getMode(stack);
		tooltip.add(Text.literal("✹ КРЕАТИВНЫЙ КАТАКЛИЗМ И КУЗНИЦА ЗВЁЗД ✹").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("Текущая задача: ").formatted(Formatting.GRAY)
				.append(Text.literal(mode == 0 ? "Апокалипсис и Метеоритный Кратер" : "3D Рентген-Карьер Руд и Алхимия").formatted(Formatting.AQUA)));
		tooltip.add(Text.literal("• Режим 1: Вырывает глыбы в небо, бьёт 650 урона боссам и плавит").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("  кратер с жилами Древних Обломков, Алмазов, Золота и Аметистов!").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Режим 2: Испаряет пустую породу в сфере 16 бл., оголяя ВСЕ руды,").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("  переплавляет Уголь -> Алмазы, Обсидиан -> Незерит и чинит броню!").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("Shift + ПКМ: Переключить режим Сверхновой").formatted(Formatting.GRAY));
	}
}
