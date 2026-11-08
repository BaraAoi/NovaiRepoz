package com.novaimod.item;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.world.WhiteCapWorldManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Интерактивный вращающийся «Компас Белых Колпаков» (`novaimod:white_cap_compass`):
 * - Стрелка плавно крутится и пружинит в реальном времени (32 кадра + физика инерции стрелки),
 *   всегда указывая СТРОГО на ближайшую Главную Цитадель Белых Колпаков (`white_cap_citadel`)!
 * - Никаких лишних надписей и показаний дистанции над полоской ХП.
 */
public class WhiteCapCompassItem extends Item {
	public static final String CITADEL_TYPE = "white_cap_citadel";
	public static final String CITADEL_TITLE = "Главная Цитадель Белых Колпаков";

	public static final String KEY_HAS_TARGET = "HasTarget";
	public static final String KEY_TARGET_X = "TargetX";
	public static final String KEY_TARGET_Y = "TargetY";
	public static final String KEY_TARGET_Z = "TargetZ";
	public static final String KEY_TARGET_DIM = "TargetDim";

	public WhiteCapCompassItem(Settings settings) {
		super(settings);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClient() || !(world instanceof ServerWorld sw) || !(entity instanceof ServerPlayerEntity sp)) {
			return;
		}
		// Обновляем координаты ближайшей Главной Цитадели раз в 15 тиков (перезаписываем NBT ТОЛЬКО при изменении точки, чтобы предмет не дёргался в руке!)
		if ((sp.age + slot) % 15 != 0) {
			return;
		}
		refreshTrackedTarget(stack, sw, sp);
	}

	public static ModPersistentState.StructureEntry refreshTrackedTarget(ItemStack stack, ServerWorld sw, ServerPlayerEntity sp) {
		ModPersistentState state = ModPersistentState.get(sw.getServer());
		WhiteCapWorldManager.ensureInitialStructuresNearPlayer(sw, sp, state);

		String dimId = sw.getRegistryKey().getValue().toString();

		ModPersistentState.StructureEntry best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if (!dimId.equals(se.dimension) || !CITADEL_TYPE.equals(se.type)) {
				continue;
			}
			double dSq = se.pos.getSquaredDistance(sp.getBlockPos());
			if (dSq < bestDistSq) {
				bestDistSq = dSq;
				best = se;
			}
		}

		NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound nbt = existing != null ? existing.copyNbt() : new NbtCompound();
		boolean prevHas = nbt.getBoolean(KEY_HAS_TARGET);

		if (best == null) {
			if (prevHas) {
				nbt.putBoolean(KEY_HAS_TARGET, false);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
			}
			return null;
		}

		int tx = best.pos.getX();
		int ty = best.pos.getY();
		int tz = best.pos.getZ();
		if (!prevHas
				|| nbt.getInt(KEY_TARGET_X) != tx
				|| nbt.getInt(KEY_TARGET_Y) != ty
				|| nbt.getInt(KEY_TARGET_Z) != tz
				|| !dimId.equals(nbt.getString(KEY_TARGET_DIM))) {
			nbt.putBoolean(KEY_HAS_TARGET, true);
			nbt.putInt(KEY_TARGET_X, tx);
			nbt.putInt(KEY_TARGET_Y, ty);
			nbt.putInt(KEY_TARGET_Z, tz);
			nbt.putString(KEY_TARGET_DIM, dimId);
			stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		}
		return best;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient()) {
			return TypedActionResult.pass(stack);
		}
		if (!(user instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) {
			return TypedActionResult.pass(stack);
		}

		if (sw.getRegistryKey() == World.OVERWORLD) {
			ModPersistentState.StructureEntry best = refreshTrackedTarget(stack, sw, sp);
			if (best != null && sp.isCreative() && sp.getPitch() > 68.0F) {
				BlockPos dest = best.pos;
				sp.teleport(sw, dest.getX() + 0.5D, dest.getY() + 1.0D, dest.getZ() + 15.5D, 180.0F, 0.0F);
				sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.15F);
				return TypedActionResult.success(stack, false);
			}
		}
		return TypedActionResult.pass(stack);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Интерактивный компас (вращающаяся стрелка!)").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("• Всегда ведёт строго в: ").formatted(Formatting.GRAY)
				.append(Text.literal(CITADEL_TITLE).formatted(Formatting.WHITE, Formatting.BOLD)));
		tooltip.add(Text.literal("• Крафт: 4 железа, 3 белой шерсти, 1 редстоун и 1 обычный компас").formatted(Formatting.DARK_GRAY));
	}
}
