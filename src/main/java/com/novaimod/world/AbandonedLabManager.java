package com.novaimod.world;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.CatgirlEntity;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Заброшенные Базы Учёных («Секретный Комплекс "Проект НЕКО"»):
 * - На поверхности: полуразрушенный бункер со знаком карантина, железными решётками и паутиной.
 * - Удобная широкая каменная лестница ведёт глубоко под землю:
 *   Зал 1: Разбитые крио-капсулы и одичавшие мутанты охраны;
 *   Зал 2: Операционная и архив учёных (дневник жестокого эксперимента + Покеболы + припасы);
 *   Зал 3 (В самой глубине!): Бронированный каземат с железной клеткой, внутри которой сидит
 *          разумная Кошкодевочка в своём уме, которую можно спасти и привести к себе домой!
 */
public final class AbandonedLabManager {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private AbandonedLabManager() {
	}

	public static void tickLabs(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerWorld world = player.getServerWorld();
			if (world.getRegistryKey() != World.OVERWORLD) {
				continue;
			}

			ensureInitialLabNearPlayer(world, player, state);

			int rx = Math.floorDiv(player.getBlockX(), 260);
			int rz = Math.floorDiv(player.getBlockZ(), 260);
			tryGenerateRegionalLab(world, state, rx, rz);
		}
	}

	public static void ensureInitialLabNearPlayer(ServerWorld world, ServerPlayerEntity player, ModPersistentState state) {
		if (world.getRegistryKey() != World.OVERWORLD || state.hasGeneratedRegion("neko_prime_lab")) {
			return;
		}
		int lx = player.getBlockX() + 76;
		int lz = player.getBlockZ() + 82;
		if (!areLabChunksLoaded(world, lx, lz)) {
			return;
		}
		BlockPos spot = findDrySurface(world, lx, lz);
		if (spot != null) {
			state.markRegionGenerated("neko_prime_lab");
			buildAbandonedScientistLab(world, spot);
			state.recordStructure("abandoned_lab", "minecraft:overworld", spot);
		}
	}

	private static void tryGenerateRegionalLab(ServerWorld world, ModPersistentState state, int rx, int rz) {
		String key = "neko_lab:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}
		// Примерно в каждом 2-м регионе — заброшенная база учёных
		if (Math.floorMod(rx * 23 + rz * 41, 2) != 0) {
			state.markRegionGenerated(key);
			return;
		}

		Random rng = Random.create(((long) rx * 817263541L) ^ ((long) rz * 192837465L) ^ (world.getSeed() + 909L));
		int x = rx * 260 + 64 + rng.nextInt(132);
		int z = rz * 260 + 64 + rng.nextInt(132);
		if (!areLabChunksLoaded(world, x, z)) {
			return;
		}

		BlockPos spot = findDrySurface(world, x, z);
		state.markRegionGenerated(key);
		if (spot == null) {
			return;
		}

		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if ("minecraft:overworld".equals(se.dimension) && se.pos.getSquaredDistance(spot) < 68.0D * 68.0D) {
				return;
			}
		}

		buildAbandonedScientistLab(world, spot);
		state.recordStructure("abandoned_lab", "minecraft:overworld", spot);
	}

	public static void buildAbandonedScientistLab(ServerWorld world, BlockPos surfaceOrigin) {
		int cx = surfaceOrigin.getX();
		int surfY = surfaceOrigin.getY();
		int cz = surfaceOrigin.getZ();

		BlockState bricks = Blocks.STONE_BRICKS.getDefaultState();
		BlockState cracked = Blocks.CRACKED_STONE_BRICKS.getDefaultState();
		BlockState mossy = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
		BlockState deepslate = Blocks.DEEPSLATE_TILES.getDefaultState();
		BlockState crackedDeep = Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState();
		BlockState quartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState ironBlock = Blocks.IRON_BLOCK.getDefaultState();
		BlockState bars = Blocks.IRON_BARS.getDefaultState();
		BlockState yellowWarn = Blocks.YELLOW_CONCRETE.getDefaultState();
		BlockState blackWarn = Blocks.BLACK_CONCRETE.getDefaultState();
		BlockState cyanGlass = Blocks.CYAN_STAINED_GLASS.getDefaultState();
		BlockState cobweb = Blocks.COBWEB.getDefaultState();
		BlockState soulLantern = Blocks.SOUL_LANTERN.getDefaultState();
		BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		Random rng = world.random;
		BlockPos.Mutable m = new BlockPos.Mutable();

		// 1. НАЗЕМНЫЙ ПОЛУРАЗРУШЕННЫЙ БУНКЕР-ВХОД (9x9, y = surfY .. surfY + 5)
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				for (int fy = 0; fy >= -4; fy--) {
					m.set(cx + dx, surfY + fy, cz + dz);
					if (fy == 0 || !world.getBlockState(m).isSolidBlock(world, m)) {
						world.setBlockState(m, bricks, FLAGS);
					} else {
						break;
					}
				}
				boolean border = Math.abs(dx) == 4 || Math.abs(dz) == 4;
				for (int dy = 1; dy <= 5; dy++) {
					m.set(cx + dx, surfY + dy, cz + dz);
					if (dy == 5) {
						world.setBlockState(m, (dx == 0 && dz == 0) ? seaLantern : pickRuinedStone(rng, bricks, cracked, mossy), FLAGS);
					} else if (border) {
						if (dz == 4 && Math.abs(dx) <= 1 && dy <= 3) {
							world.setBlockState(m, air, FLAGS); // Вход с юга
						} else if (dy == 1) {
							world.setBlockState(m, ((dx + dz) & 1) == 0 ? yellowWarn : blackWarn, FLAGS);
						} else if (dy == 3 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) {
							world.setBlockState(m, bars, FLAGS);
						} else {
							world.setBlockState(m, pickRuinedStone(rng, bricks, cracked, mossy), FLAGS);
						}
					} else {
						world.setBlockState(m, air, FLAGS);
					}
				}
			}
		}
		world.setBlockState(new BlockPos(cx - 3, surfY + 4, cz + 3), cobweb, FLAGS);
		world.setBlockState(new BlockPos(cx + 3, surfY + 1, cz - 3), cobweb, FLAGS);
		world.setBlockState(new BlockPos(cx, surfY + 4, cz + 2), soulLantern, FLAGS);

		// 2. ШИРОКИЙ СПУСК-ЛЕСТНИЦА ПОД ЗЕМЛЮ (16 ступеней на север: step = 1..16)
		int depth = 16;
		if (surfY - depth < world.getBottomY() + 8) {
			depth = Math.max(6, surfY - (world.getBottomY() + 8));
		}
		BlockState stairSouth = Blocks.STONE_BRICK_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.SOUTH)
				.with(StairsBlock.HALF, BlockHalf.BOTTOM);

		for (int s = 1; s <= depth; s++) {
			int sy = surfY - s;
			int sz = cz - s;
			for (int dx = -2; dx <= 2; dx++) {
				boolean wall = Math.abs(dx) == 2;
				world.setBlockState(new BlockPos(cx + dx, sy - 1, sz), deepslate, FLAGS);
				world.setBlockState(new BlockPos(cx + dx, sy + 5, sz), deepslate, FLAGS);
				for (int dy = 0; dy <= 4; dy++) {
					BlockPos p = new BlockPos(cx + dx, sy + dy, sz);
					if (wall) {
						world.setBlockState(p, (dy == 1 && s % 4 == 0) ? yellowWarn : pickRuinedDeep(rng, deepslate, crackedDeep), FLAGS);
					} else if (dy == 0) {
						world.setBlockState(p, stairSouth, FLAGS);
					} else {
						world.setBlockState(p, air, FLAGS);
					}
				}
			}
			if (s % 5 == 0) {
				world.setBlockState(new BlockPos(cx, sy + 4, sz), soulLantern, FLAGS);
			}
		}

		int labY = surfY - depth;
		int zStart = cz - depth - 1;

		// 3. ПОДЗЕМНЫЙ КОМПЛЕКС УЧЁНЫХ (длина 28 блоков вглубь на север: zStart .. zStart - 27, ширина -6..+6, высота 6)
		for (int dz = 0; dz >= -27; dz--) {
			int wz = zStart + dz;
			for (int dx = -6; dx <= 6; dx++) {
				boolean wallX = Math.abs(dx) == 6;
				boolean wallEnd = (dz == -27) || (dz == 0 && Math.abs(dx) > 1);
				// Перегородки между Залами 1, 2 и 3 с широкими проёмами (dx = -1..1)
				boolean bulkhead = (dz == -9 || dz == -19) && Math.abs(dx) > 1;

				// Пол и потолок лаборатории
				world.setBlockState(new BlockPos(cx + dx, labY - 1, wz),
						((dx + dz) % 3 == 0) ? ironBlock : quartz, FLAGS);
				world.setBlockState(new BlockPos(cx + dx, labY + 5, wz),
						(dx == 0 && Math.abs(dz) % 5 == 2) ? seaLantern : deepslate, FLAGS);

				for (int dy = 0; dy <= 4; dy++) {
					BlockPos p = new BlockPos(cx + dx, labY + dy, wz);
					if (wallX || wallEnd || bulkhead) {
						if (bulkhead && dy >= 2 && dy <= 3 && Math.abs(dx) == 3) {
							world.setBlockState(p, bars, FLAGS); // Смотровые решётки между отсеками!
						} else {
							world.setBlockState(p, pickRuinedDeep(rng, deepslate, crackedDeep), FLAGS);
						}
					} else {
						world.setBlockState(p, air, FLAGS);
					}
				}
			}
		}

		// --- ЗАЛ 1 (dz = -1 .. -8): Разбитые крио-капсулы и одичавшие подопытные ---
		int[][] pods = new int[][]{{-4, -3}, {4, -3}, {-4, -6}, {4, -6}};
		for (int[] pod : pods) {
			int px = cx + pod[0];
			int pz = zStart + pod[1];
			world.setBlockState(new BlockPos(px, labY, pz), seaLantern, FLAGS);
			world.setBlockState(new BlockPos(px, labY + 1, pz), Blocks.SKELETON_SKULL.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(px, labY + 2, pz), cyanGlass, FLAGS);
			world.setBlockState(new BlockPos(px, labY + 3, pz), ironBlock, FLAGS);
		}
		world.setBlockState(new BlockPos(cx - 5, labY + 4, zStart - 2), cobweb, FLAGS);
		world.setBlockState(new BlockPos(cx + 5, labY, zStart - 7), cobweb, FLAGS);

		// 2 мутировавших охранника/образца в Зале 1
		spawnLabMutantGuard(world, new BlockPos(cx - 2, labY, zStart - 5));
		spawnLabMutantGuard(world, new BlockPos(cx + 2, labY, zStart - 5));

		// --- ЗАЛ 2 (dz = -10 .. -18): Операционная и Сундуки Безумных Учёных (с дневником и Покеболами!) ---
		// Операционный кварцевый стол в центре
		world.setBlockState(new BlockPos(cx, labY, zStart - 14), quartz, FLAGS);
		world.setBlockState(new BlockPos(cx, labY, zStart - 15), quartz, FLAGS);
		// Алхимические стойки и книжные стеллажи учёных
		world.setBlockState(new BlockPos(cx - 5, labY, zStart - 12), quartz, FLAGS);
		world.setBlockState(new BlockPos(cx - 5, labY + 1, zStart - 12), Blocks.BREWING_STAND.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx - 5, labY, zStart - 14), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3), FLAGS);
		for (int dy = 0; dy <= 2; dy++) {
			world.setBlockState(new BlockPos(cx + 5, labY + dy, zStart - 12), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx + 5, labY + dy, zStart - 13), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
		}
		world.setBlockState(new BlockPos(cx + 5, labY + 4, zStart - 16), cobweb, FLAGS);

		fillScientistChest(world, new BlockPos(cx - 5, labY, zStart - 17), true);
		fillScientistChest(world, new BlockPos(cx + 5, labY, zStart - 17), false);

		// --- ЗАЛ 3 — В САМОЙ ГЛУБИНЕ БАЗЫ (dz = -20 .. -26): ЖЕЛЕЗНАЯ КЛЕТКА С КОШКОДЕВОЧКОЙ! ---
		int cageZ = zStart - 23;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				world.setBlockState(new BlockPos(cx + dx, labY - 1, cageZ + dz), ironBlock, FLAGS);
				world.setBlockState(new BlockPos(cx + dx, labY + 3, cageZ + dz), ironBlock, FLAGS);
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos cp = new BlockPos(cx + dx, labY + dy, cageZ + dz);
					if (edge) {
						// Калитка клетки спереди (южная стенка dz == +2, dx == 0, dy == 0..1)
						if (dz == 2 && dx == 0 && dy <= 1) {
							world.setBlockState(cp, Blocks.DARK_OAK_FENCE_GATE.getDefaultState(), FLAGS);
						} else {
							world.setBlockState(cp, bars, FLAGS);
						}
					} else {
						world.setBlockState(cp, air, FLAGS);
					}
				}
			}
		}
		world.setBlockState(new BlockPos(cx - 3, labY + 2, cageZ + 2), soulLantern, FLAGS);
		world.setBlockState(new BlockPos(cx + 3, labY + 2, cageZ + 2), soulLantern, FLAGS);

		// Спавним разумную Кошкодевочку прямо внутри глубинной клетки!
		CatgirlEntity catgirl = ModEntities.CATGIRL_ENTITY.create(world);
		if (catgirl != null) {
			catgirl.refreshPositionAndAngles(cx + 0.5D, labY, cageZ + 0.5D, 0.0F, 0.0F);
			catgirl.setVariantId(rng.nextInt(4));
			catgirl.setRescued(false);
			catgirl.setPersistent();
			world.spawnEntity(catgirl);
		}
	}

	private static BlockState pickRuinedStone(Random rng, BlockState a, BlockState b, BlockState c) {
		int r = rng.nextInt(5);
		if (r == 0) return b;
		if (r == 1) return c;
		return a;
	}

	private static BlockState pickRuinedDeep(Random rng, BlockState a, BlockState b) {
		return rng.nextInt(4) == 0 ? b : a;
	}

	private static void spawnLabMutantGuard(ServerWorld world, BlockPos pos) {
		HuskEntity mutant = EntityType.HUSK.create(world);
		if (mutant != null) {
			mutant.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.random.nextFloat() * 360.0F, 0.0F);
			mutant.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			mutant.setCustomName(Text.literal("Одичавший Образец Лаборатории").formatted(Formatting.DARK_AQUA));
			mutant.setPersistent();
			world.spawnEntity(mutant);
		}
	}

	private static void fillScientistChest(ServerWorld world, BlockPos pos, boolean includeJournal) {
		world.setBlockState(pos, Blocks.CHEST.getDefaultState(), FLAGS);
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof ChestBlockEntity chest)) {
			return;
		}
		Random r = world.random;
		if (includeJournal) {
			ItemStack journal = new ItemStack(Items.BOOK);
			journal.set(DataComponentTypes.CUSTOM_NAME,
					Text.literal("Дневник Проекта «НЕКО-09» (Секретно)").formatted(Formatting.AQUA, Formatting.BOLD));
			journal.set(DataComponentTypes.LORE, new LoreComponent(List.of(
					Text.literal("Запись №41: Генетический синтез завершён, но мы зашли слишком далеко...").formatted(Formatting.GRAY),
					Text.literal("Подопытная №09 в дальней клетке сохранила 100% человеческий разум и память.").formatted(Formatting.WHITE),
					Text.literal("Она плачет и просит отпустить её домой. Персонал эвакуируется из-за аварии.").formatted(Formatting.YELLOW),
					Text.literal("Если кто-то спустится сюда — откройте клетку в глубине и спасите её!").formatted(Formatting.GREEN),
					Text.literal("P.S. В соседних слотах — карманные Покеболы для безопасного переноса существ.").formatted(Formatting.LIGHT_PURPLE)
			)));
			chest.setStack(0, journal);
		}

		chest.setStack(2, new ItemStack(ModItems.POKEBALL, 2 + r.nextInt(2)));
		chest.setStack(4, new ItemStack(Items.COOKED_SALMON, 4 + r.nextInt(4)));
		chest.setStack(5, new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
		chest.setStack(13, new ItemStack(Items.DIAMOND, 2 + r.nextInt(3)));
		chest.setStack(15, new ItemStack(Items.EMERALD, 4 + r.nextInt(6)));
		chest.setStack(22, new ItemStack(Items.IRON_INGOT, 6 + r.nextInt(6)));
	}

	private static boolean areLabChunksLoaded(ServerWorld world, int x, int z) {
		return world.isChunkLoaded((x - 16) >> 4, (z - 48) >> 4)
				&& world.isChunkLoaded((x + 16) >> 4, (z - 48) >> 4)
				&& world.isChunkLoaded((x - 16) >> 4, (z + 16) >> 4)
				&& world.isChunkLoaded((x + 16) >> 4, (z + 16) >> 4);
	}

	private static BlockPos findDrySurface(ServerWorld world, int startX, int startZ) {
		int[][] offsets = new int[][]{{0, 0}, {20, 0}, {-20, 0}, {0, 20}, {0, -20}};
		for (int[] off : offsets) {
			int x = startX + off[0];
			int z = startZ + off[1];
			if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
			RegistryEntry<Biome> b = world.getBiome(new BlockPos(x, world.getSeaLevel(), z));
			String bName = b.getKey().map(k -> k.getValue().getPath()).orElse("");
			if (bName.contains("ocean") || bName.contains("river")) continue;
			int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			if (topY < world.getSeaLevel() - 1 || topY > 170) continue;
			BlockPos g = new BlockPos(x, topY, z);
			BlockState bs = world.getBlockState(g);
			if (!bs.getFluidState().isEmpty() || bs.isOf(Blocks.WATER) || bs.isOf(Blocks.LAVA)) continue;
			return g;
		}
		return null;
	}
}
