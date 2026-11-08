package com.novaimod.item;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.world.AbandonedLabManager;
import com.novaimod.world.LuceWorldManager;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Интерактивный вращающийся «Компас Белых Колпаков» (`novaimod:white_cap_compass`):
 * - Стрелка плавно крутится и пружинит в реальном времени (32 кадра + физика инерции стрелки),
 *   всегда указывая прямо на выбранную базу/лагерь или Заброшенную Лабораторию Учёных!
 * - ПКМ — показывает точное расстояние и название цели.
 * - Shift + ПКМ — переключает цель поиска (Базы Клана / Цитадель / Форты / Лагеря / Заброшенные Базы Учёных).
 * - Имеет крафт на верстаке!
 */
public class WhiteCapCompassItem extends Item {
	public static final String KEY_FILTER = "ClanCompassFilter";
	public static final String KEY_HAS_TARGET = "HasTarget";
	public static final String KEY_TARGET_X = "TargetX";
	public static final String KEY_TARGET_Y = "TargetY";
	public static final String KEY_TARGET_Z = "TargetZ";
	public static final String KEY_TARGET_DIM = "TargetDim";

	public WhiteCapCompassItem(Settings settings) {
		super(settings);
	}

	public static int getFilter(ItemStack stack) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (custom == null) {
			return 0;
		}
		return MathHelper.clamp(custom.copyNbt().getInt(KEY_FILTER), 0, 5);
	}

	public static String filterTitle(int filter) {
		return switch (filter) {
			case 1 -> "Главная Цитадель Белых Колпаков";
			case 2 -> "Карантинный Форт-Лаборатория";
			case 3 -> "Полевые Лагеря Клана";
			case 4 -> "Здание МФО (Получение Ключа Бюрократии)";
			case 5 -> "Часовня Святой Люче (Паломница и Сантино)";
			default -> "Любая база / лагерь Белых Колпаков";
		};
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClient() || !(world instanceof ServerWorld sw) || !(entity instanceof ServerPlayerEntity sp)) {
			return;
		}
		// Обновляем координаты ближайшей цели раз в 15 тиков (перезаписываем NBT ТОЛЬКО при изменении точки, чтобы предмет не дёргался в руке!)
		if ((sp.age + slot) % 15 != 0) {
			return;
		}
		refreshTrackedTarget(stack, sw, sp);
	}

	public static ModPersistentState.StructureEntry refreshTrackedTarget(ItemStack stack, ServerWorld sw, ServerPlayerEntity sp) {
		ModPersistentState state = ModPersistentState.get(sw.getServer());
		WhiteCapWorldManager.ensureInitialStructuresNearPlayer(sw, sp, state);
		LuceWorldManager.tickLuceWorld(sw.getServer());

		int filter = getFilter(stack);
		String dimId = sw.getRegistryKey().getValue().toString();

		ModPersistentState.StructureEntry best = null;
		double bestDistSq = Double.MAX_VALUE;
		if (filter == 4 && sw.getRegistryKey() == World.OVERWORLD && state.getMfoPos() != null) {
			best = new ModPersistentState.StructureEntry("mfo", dimId, state.getMfoPos());
			bestDistSq = best.pos.getSquaredDistance(sp.getBlockPos());
		}
		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if (!dimId.equals(se.dimension) || !matchesFilter(se.type, filter)) {
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
			return TypedActionResult.success(stack, true);
		}
		if (!(user instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) {
			return TypedActionResult.pass(stack);
		}

		if (sp.isSneaking()) {
			int next = (getFilter(stack) + 1) % 6;
			NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
			NbtCompound nbt = custom != null ? custom.copyNbt() : new NbtCompound();
			nbt.putInt(KEY_FILTER, next);
			nbt.putBoolean(KEY_HAS_TARGET, false);
			stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

			refreshTrackedTarget(stack, sw, sp);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9F, 1.25F);
			sp.sendMessage(Text.literal("🧭 Режим Компаса: ").formatted(Formatting.GRAY)
					.append(Text.literal(filterTitle(next)).formatted(Formatting.AQUA, Formatting.BOLD)), true);
			return TypedActionResult.success(stack, false);
		}

		ModPersistentState.StructureEntry best = refreshTrackedTarget(stack, sw, sp);
		int filter = getFilter(stack);
		if (best == null) {
			sp.sendMessage(Text.literal("🧭 Поблизости ещё нет «" + filterTitle(filter) + "» — пройдитесь немного по миру, стрелка подхватит цель!").formatted(Formatting.YELLOW), true);
			return TypedActionResult.success(stack, false);
		}

		BlockPos dest = best.pos;
		String label = prettyStructureName(best.type);

		if (sp.isCreative() && sp.getPitch() > 68.0F) {
			int zOff = "abandoned_lab".equals(best.type) ? 6 : ("luce_shrine".equals(best.type) ? 7 : 15);
			sp.teleport(sw, dest.getX() + 0.5D, dest.getY() + 1.0D, dest.getZ() + zOff + 0.5D, 180.0F, 0.0F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.15F);
			sp.sendMessage(Text.literal("🧭 Перенос ко входу: " + label + "!").formatted(Formatting.GREEN, Formatting.BOLD), true);
			return TypedActionResult.success(stack, false);
		}

		int dist = (int) Math.round(Math.sqrt(dest.getSquaredDistance(sp.getBlockPos())));
		sw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.85F, 1.2F);
		sp.sendMessage(
				Text.literal("🧭 Стрелка ведёт к «" + label + "» — осталось ").formatted(Formatting.WHITE)
						.append(Text.literal(dist + " бл.").formatted(Formatting.AQUA, Formatting.BOLD)),
				true
		);
		return TypedActionResult.success(stack, false);
	}

	private static boolean matchesFilter(String type, int filter) {
		if (type == null) {
			return false;
		}
		return switch (filter) {
			case 1 -> "white_cap_citadel".equals(type);
			case 2 -> "white_cap_outpost".equals(type);
			case 3 -> "white_cap_camp".equals(type) || "white_cap_nether_camp".equals(type);
			case 4 -> "mfo".equals(type);
			case 5 -> "luce_shrine".equals(type);
			default -> type.startsWith("white_cap_");
		};
	}

	private static String prettyStructureName(String type) {
		return switch (type) {
			case "white_cap_citadel" -> "Главная Цитадель Белых Колпаков";
			case "white_cap_outpost" -> "Карантинный Форт Колпаков";
			case "white_cap_nether_camp" -> "Адский Лагерь Белых Колпаков";
			case "mfo" -> "Здание МФО (Ключ Бюрократии)";
			case "luce_shrine" -> "Часовня Святой Люче";
			default -> "Полевой Лагерь Белых Колпаков";
		};
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Интерактивный компас (вращающаяся стрелка!)").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("• Цель стрелки: ").formatted(Formatting.GRAY)
				.append(Text.literal(filterTitle(getFilter(stack))).formatted(Formatting.WHITE)));
		tooltip.add(Text.literal("• ПКМ — проверить дистанцию | Shift + ПКМ — сменить цель").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("• Крафт: 4 железа, 3 белой шерсти, 1 редстоун и 1 обычный компас").formatted(Formatting.DARK_GRAY));
	}
}
