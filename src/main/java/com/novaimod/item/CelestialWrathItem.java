package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.CelestialMeteorEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Креативный артефакт «Жезл Небесной Кары»:
 * обрушивает с небес шквал из 9 пылающих болидов и молний в точку взгляда (до 96 блоков)!
 * Shift + ПКМ переключает разрушение ландшафта (по умолчанию бережёт постройки).
 */
public class CelestialWrathItem extends Item {
	private static final String NBT_CRATER_MODE = "novai_wrath_craters";

	public CelestialWrathItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	private static boolean isCraterMode(ItemStack stack) {
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return comp.contains(NBT_CRATER_MODE) && comp.copyNbt().getBoolean(NBT_CRATER_MODE);
	}

	private static void setCraterMode(ItemStack stack, boolean value) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_CRATER_MODE, value);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);

		if (user.isSneaking()) {
			if (!world.isClient()) {
				boolean next = !isCraterMode(stack);
				setCraterMode(stack, next);
				user.sendMessage(
						Text.literal("Жезл Небесной Кары: ")
								.formatted(Formatting.GOLD)
								.append(Text.literal(next ? "«Катаклизм (разрушает блоки)»" : "«Чистая кара (без порчи построек)»")
										.formatted(next ? Formatting.RED : Formatting.GREEN, Formatting.BOLD)),
						true
				);
				world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 0.7F, next ? 0.8F : 1.3F);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		if (!world.isClient() && user instanceof ServerPlayerEntity sp && world instanceof ServerWorld sw) {
			HitResult hit = sp.raycast(96.0D, 0.0F, false);
			Vec3d target = hit.getPos();
			boolean breakBlocks = isCraterMode(stack);
			Random rng = sw.getRandom();

			// Целеуказательное солнечное кольцо на земле
			for (int i = 0; i < 28; i++) {
				double ang = (Math.PI * 2.0D * i) / 28.0D;
				sw.spawnParticles(ParticleTypes.FLAME, target.x + Math.cos(ang) * 5.0D, target.y + 0.25D, target.z + Math.sin(ang) * 5.0D, 2, 0.1D, 0.05D, 0.1D, 0.02D);
			}

			// Запускаем 9 пылающих метеоров с разной высоты под наклоном прямо в цель!
			for (int i = 0; i < 9; i++) {
				double spreadAngle = rng.nextDouble() * Math.PI * 2.0D;
				double spreadDist = i == 0 ? 0.0D : (1.5D + rng.nextDouble() * 6.5D);
				double impactX = target.x + Math.cos(spreadAngle) * spreadDist;
				double impactZ = target.z + Math.sin(spreadAngle) * spreadDist;

				double spawnHeight = 28.0D + i * 3.8D;
				double slantX = (rng.nextDouble() - 0.5D) * 10.0D;
				double slantZ = (rng.nextDouble() - 0.5D) * 10.0D;
				Vec3d start = new Vec3d(impactX + slantX, target.y + spawnHeight, impactZ + slantZ);
				Vec3d dir = new Vec3d(impactX, target.y, impactZ).subtract(start).normalize().multiply(1.45D);

				CelestialMeteorEntity meteor = new CelestialMeteorEntity(sw, start.x, start.y, start.z, sp.getUuid(), breakBlocks);
				meteor.setVelocity(dir);
				sw.spawnEntity(meteor);
			}

			sw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 1.3F, 0.9F);
			user.getItemCooldownManager().set(this, 20);
		}

		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		boolean craters = isCraterMode(stack);
		tooltip.add(Text.literal("Креативный артефакт").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ — обрушивает с небес шквал из 9 пылающих").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("болидов и молний в точку взгляда (до 96 блоков)!").formatted(Formatting.GRAY));
		tooltip.add(
				Text.literal("Shift + ПКМ — режим: ").formatted(Formatting.DARK_GRAY)
						.append(Text.literal(craters ? "Катаклизм (с кратерами)" : "Без порчи построек")
								.formatted(craters ? Formatting.RED : Formatting.GREEN))
		);
	}
}
