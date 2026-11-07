package com.novaimod.item;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.world.WhiteCapWorldManager;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * «Компас Белых Колпаков»:
 * - ПКМ: находит ближайшую базу/лагерь Клана Белых Колпаков и показывает точное направление и дистанцию!
 * - Shift + ПКМ: переключает фильтр (Все объекты Клана / Главная Цитадель / Карантинный Форт / Полевой Лагерь).
 * - В Креативе: если посмотреть вертикально себе под ноги и нажать ПКМ — мгновенно телепортирует ко входу найденной базы!
 */
public class WhiteCapCompassItem extends Item {
	private static final String KEY_FILTER = "ClanCompassFilter";

	public WhiteCapCompassItem(Settings settings) {
		super(settings);
	}

	public static int getFilter(ItemStack stack) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (custom == null) {
			return 0;
		}
		return MathHelper.clamp(custom.copyNbt().getInt(KEY_FILTER), 0, 3);
	}

	private static void setFilter(ItemStack stack, int filter) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		NbtCompound nbt = custom != null ? custom.copyNbt() : new NbtCompound();
		nbt.putInt(KEY_FILTER, MathHelper.clamp(filter, 0, 3));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static String filterTitle(int filter) {
		return switch (filter) {
			case 1 -> "Главная Цитадель Белых Колпаков";
			case 2 -> "Карантинный Форт-Лаборатория";
			case 3 -> "Полевые Лагеря Клана";
			default -> "Любая база / лагерь Клана";
		};
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
			int next = (getFilter(stack) + 1) % 4;
			setFilter(stack, next);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9F, 1.25F);
			sp.sendMessage(Text.literal("🧭 Поиск Компаса: ").formatted(Formatting.GRAY)
					.append(Text.literal(filterTitle(next)).formatted(Formatting.AQUA, Formatting.BOLD)), true);
			return TypedActionResult.success(stack, false);
		}

		int filter = getFilter(stack);
		String dimId = sw.getRegistryKey().getValue().toString();
		ModPersistentState state = ModPersistentState.get(sw.getServer());

		// Убедимся, что хотя бы ближайшая структура в текущем регионе проверена
		WhiteCapWorldManager.ensureInitialStructuresNearPlayer(sw, sp, state);

		ModPersistentState.StructureEntry best = null;
		double bestDistSq = Double.MAX_VALUE;
		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if (!dimId.equals(se.dimension)) {
				continue;
			}
			if (!matchesFilter(se.type, filter)) {
				continue;
			}
			double dSq = se.pos.getSquaredDistance(sp.getBlockPos());
			if (dSq < bestDistSq) {
				bestDistSq = dSq;
				best = se;
			}
		}

		if (best == null) {
			sp.sendMessage(Text.literal("🧭 Поблизости ещё не разведаны объекты «" + filterTitle(filter) + "» — пройдитесь немного по миру!").formatted(Formatting.YELLOW), true);
			return TypedActionResult.success(stack, false);
		}

		BlockPos dest = best.pos;
		String label = prettyStructureName(best.type);

		// В Креативе при взгляде под ноги — быстрый перенос ко входу базы!
		if (sp.isCreative() && sp.getPitch() > 68.0F) {
			sp.teleport(sw, dest.getX() + 0.5D, dest.getY() + 1.0D, dest.getZ() + 15.5D, sp.getYaw(), 0.0F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.15F);
			sp.sendMessage(Text.literal("🧭 Перенос ко входу: " + label + "!").formatted(Formatting.GREEN, Formatting.BOLD), true);
			return TypedActionResult.success(stack, false);
		}

		int dist = (int) Math.round(Math.sqrt(bestDistSq));
		String arrow = computeDirectionArrow(sp, dest);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.9F, 1.15F);
		sp.sendMessage(
				Text.literal("🧭 " + label + ": ").formatted(Formatting.WHITE, Formatting.BOLD)
						.append(Text.literal(dist + " бл. " + arrow).formatted(Formatting.AQUA, Formatting.BOLD))
						.append(Text.literal(" [" + dest.getX() + ", " + dest.getY() + ", " + dest.getZ() + "]").formatted(Formatting.GRAY)),
				true
		);
		return TypedActionResult.success(stack, false);
	}

	private static boolean matchesFilter(String type, int filter) {
		if (type == null || !type.startsWith("white_cap_")) {
			return false;
		}
		return switch (filter) {
			case 1 -> "white_cap_citadel".equals(type);
			case 2 -> "white_cap_outpost".equals(type);
			case 3 -> "white_cap_camp".equals(type) || "white_cap_nether_camp".equals(type);
			default -> true;
		};
	}

	private static String prettyStructureName(String type) {
		return switch (type) {
			case "white_cap_citadel" -> "Главная Цитадель Белых Колпаков";
			case "white_cap_outpost" -> "Карантинный Форт Колпаков";
			case "white_cap_nether_camp" -> "Адский Лагерь Белых Колпаков";
			default -> "Полевой Лагерь Белых Колпаков";
		};
	}

	private static String computeDirectionArrow(ServerPlayerEntity sp, BlockPos dest) {
		double dx = (dest.getX() + 0.5D) - sp.getX();
		double dz = (dest.getZ() + 0.5D) - sp.getZ();
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = MathHelper.wrapDegrees(targetYaw - sp.getYaw());
		if (rel >= -22.5D && rel < 22.5D) return "⬆ ПРЯМО";
		if (rel >= 22.5D && rel < 67.5D) return "↗ ВПРАВО-ВПЕРЁД";
		if (rel >= 67.5D && rel < 112.5D) return "➡ НАПРАВО";
		if (rel >= 112.5D && rel < 157.5D) return "↘ НАЗАД-ВПРАВО";
		if (rel < -22.5D && rel >= -67.5D) return "↖ ВЛЕВО-ВПЕРЁД";
		if (rel < -67.5D && rel >= -112.5D) return "⬅ НАЛЕВО";
		if (rel < -112.5D && rel >= -157.5D) return "↙ НАЗАД-ВЛЕВО";
		return "⬇ СЗАДИ";
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Навигатор Клана Белых Колпаков").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("• ПКМ — указать путь к ближайшей Цитадели или лагерю Клана").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("• Shift + ПКМ — фильтр: ").formatted(Formatting.DARK_GRAY)
				.append(Text.literal(filterTitle(getFilter(stack))).formatted(Formatting.WHITE)));
		tooltip.add(Text.literal("• В Креативе (взгляд под ноги + ПКМ) — мгновенный телепорт к базе!").formatted(Formatting.GOLD));
	}
}
