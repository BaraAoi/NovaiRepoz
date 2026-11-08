package com.novaimod.world;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.WhiteCapEntity;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Генератор структур и патрулей «Клана Белых Колпаков»:
 * 1) ГЛАВНАЯ БАЗА — «Цитадель Белых Колпаков» (белокаменная крепость-обсерватория с гигантским шпилем-колпаком,
 *    лабораторией чумных докторов, тюремными клетками для пленного эндермена и визер-скелета, сокровищницей
 *    и боссом «Верховный Магистр Белых Колпаков»!).
 * 2) СРЕДНЯЯ БАЗА — «Карантинный Форт-Лаборатория» (укреплённый частокол с высокой дозорной башней-колпаком).
 * 3) ПОЛЕВОЙ ЛАГЕРЬ — остроконечные белые шатры-колпаки, костёр душ, котлы и отряд охотников.
 * 4) АДСКИЙ ЛАГЕРЬ В НЕЗЕРЕ — экспедиционный пост для охоты прямо на всполохов и скелетов-иссушителей!
 * 5) НОЧНЫЕ ПАТРУЛИ — бродячие тройки чистильщиков и чумных докторов.
 */
public final class WhiteCapWorldManager {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private WhiteCapWorldManager() {
	}

	public static void tickClanWorld(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerWorld world = player.getServerWorld();
			ensureInitialStructuresNearPlayer(world, player, state);

			// Региональная генерация лагерей, фортов и редких цитаделей (сетка 240x240 блоков)
			int rx = Math.floorDiv(player.getBlockX(), 240);
			int rz = Math.floorDiv(player.getBlockZ(), 240);

			if (world.getRegistryKey() == World.OVERWORLD) {
				tryGenerateOverworldClanRegion(world, state, rx, rz);
				if (server.getTicks() % 2400 == 200) {
					trySpawnNightHuntingPatrol(world, player);
				}
			} else if (world.getRegistryKey() == World.NETHER) {
				tryGenerateNetherExpeditionRegion(world, state, player, rx, rz);
			}
		}
	}

	public static void ensureInitialStructuresNearPlayer(ServerWorld world, ServerPlayerEntity player, ModPersistentState state) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}
		// Гарантируем появление первой Главной Цитадели и первого Полевого Лагеря в мире недалеко от стартовой зоны игрока!
		if (!state.hasGeneratedRegion("wc_prime_citadel")) {
			int cx = player.getBlockX() + 96;
			int cz = player.getBlockZ() - 88;
			if (areChunksLoadedRadius(world, cx, cz, 24)) {
				BlockPos spot = findDryLandSurface(world, cx, cz);
				if (spot != null) {
					state.markRegionGenerated("wc_prime_citadel");
					buildWhiteCapCitadel(world, spot);
					state.recordStructure("white_cap_citadel", "minecraft:overworld", spot);
				}
			}
		}

		if (!state.hasGeneratedRegion("wc_prime_camp")) {
			int lx = player.getBlockX() - 68;
			int lz = player.getBlockZ() + 64;
			if (areChunksLoadedRadius(world, lx, lz, 16)) {
				BlockPos spot = findDryLandSurface(world, lx, lz);
				if (spot != null) {
					state.markRegionGenerated("wc_prime_camp");
					buildFieldCamp(world, spot);
					state.recordStructure("white_cap_camp", "minecraft:overworld", spot);
				}
			}
		}
	}

	private static void tryGenerateOverworldClanRegion(ServerWorld world, ModPersistentState state, int rx, int rz) {
		String key = "wc_ow:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}

		Random rng = Random.create(((long) rx * 9182736451L) ^ ((long) rz * 4728193657L) ^ (world.getSeed() + 7717L));
		int x = rx * 240 + 56 + rng.nextInt(128);
		int z = rz * 240 + 56 + rng.nextInt(128);

		if (!areChunksLoadedRadius(world, x, z, 24)) {
			return;
		}

		BlockPos land = findDryLandSurface(world, x, z);
		state.markRegionGenerated(key);
		if (land == null) {
			return;
		}

		// Не ставим вплотную к МФО или уже записанным базам
		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if ("minecraft:overworld".equals(se.dimension) && se.pos.getSquaredDistance(land) < 70.0D * 70.0D) {
				return;
			}
		}

		int roll = Math.floorMod(rx * 19 + rz * 37, 7);
		if (roll == 0) {
			buildWhiteCapCitadel(world, land);
			state.recordStructure("white_cap_citadel", "minecraft:overworld", land);
		} else if (roll == 1 || roll == 4) {
			buildFortifiedOutpost(world, land);
			state.recordStructure("white_cap_outpost", "minecraft:overworld", land);
		} else {
			buildFieldCamp(world, land);
			state.recordStructure("white_cap_camp", "minecraft:overworld", land);
		}
	}

	private static void tryGenerateNetherExpeditionRegion(ServerWorld world, ModPersistentState state, ServerPlayerEntity player, int rx, int rz) {
		String key = "wc_nether:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}

		Random rng = Random.create(((long) rx * 61239871L) ^ ((long) rz * 29837165L) ^ world.getSeed());
		int x = rx * 240 + 64 + rng.nextInt(112);
		int z = rz * 240 + 64 + rng.nextInt(112);
		if (!areChunksLoadedRadius(world, x, z, 16)) {
			return;
		}

		BlockPos spot = findNetherFloorSpot(world, x, z);
		if (spot == null) {
			return;
		}
		state.markRegionGenerated(key);
		buildNetherExpeditionCamp(world, spot);
		state.recordStructure("white_cap_nether_camp", "minecraft:the_nether", spot);
	}

	private static void trySpawnNightHuntingPatrol(ServerWorld world, ServerPlayerEntity player) {
		long timeOfDay = world.getTimeOfDay() % 24000L;
		if (timeOfDay < 12800L || timeOfDay > 22800L) {
			return;
		}
		List<WhiteCapEntity> existing = world.getEntitiesByClass(
				WhiteCapEntity.class,
				player.getBoundingBox().expand(64.0D),
				LivingEntity::isAlive
		);
		if (existing.size() >= 5) {
			return;
		}

		double angle = world.random.nextDouble() * Math.PI * 2.0D;
		int dist = 34 + world.random.nextInt(14);
		int px = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
		int pz = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
		if (!world.isChunkLoaded(px >> 4, pz >> 4)) {
			return;
		}
		BlockPos surf = findDryLandSurface(world, px, pz);
		if (surf == null) {
			return;
		}

		int[] patrolRanks = new int[]{WhiteCapEntity.RANK_HUNTER, WhiteCapEntity.RANK_ARBALIST, WhiteCapEntity.RANK_APOTHECARY};
		for (int i = 0; i < patrolRanks.length; i++) {
			spawnClanMember(world, surf.add(i - 1, 1, 0), patrolRanks[i], null, 0);
		}
	}

	// =========================================================================
	// 1. ГЛАВНАЯ БАЗА — «ЦИТАДЕЛЬ БЕЛЫХ КОЛПАКОВ» (25x25 + высокий шпиль-колпак!)
	// =========================================================================
	public static void buildWhiteCapCitadel(ServerWorld world, BlockPos center) {
		WorldVirginLedger.runInStructureGenScope(() -> buildWhiteCapCitadelInternal(world, center));
	}

	private static void buildWhiteCapCitadelInternal(ServerWorld world, BlockPos center) {
		int cx = center.getX();
		int y = center.getY();
		int cz = center.getZ();

		BlockState calcite = Blocks.CALCITE.getDefaultState();
		BlockState quartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState quartzBricks = Blocks.QUARTZ_BRICKS.getDefaultState();
		BlockState diorite = Blocks.POLISHED_DIORITE.getDefaultState();
		BlockState deepslate = Blocks.DEEPSLATE_BRICKS.getDefaultState();
		BlockState darkOak = Blocks.DARK_OAK_LOG.getDefaultState();
		BlockState cyanGlass = Blocks.CYAN_STAINED_GLASS.getDefaultState();
		BlockState bars = Blocks.IRON_BARS.getDefaultState();
		BlockState lantern = Blocks.SOUL_LANTERN.getDefaultState();
		BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
		BlockState carpet = Blocks.WHITE_CARPET.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		BlockPos.Mutable m = new BlockPos.Mutable();

		// Расчистка объёма и мощный каменный фундамент вниз до земли
		for (int dx = -13; dx <= 13; dx++) {
			for (int dz = -13; dz <= 15; dz++) {
				for (int dy = 1; dy <= 28; dy++) {
					m.set(cx + dx, y + dy, cz + dz);
					if (!world.getBlockState(m).isAir()) {
						world.setBlockState(m, air, FLAGS);
					}
				}
				for (int fy = 0; fy >= -6; fy--) {
					m.set(cx + dx, y + fy, cz + dz);
					if (fy == 0 || !world.getBlockState(m).isSolidBlock(world, m)) {
						world.setBlockState(m, fy == 0 ? diorite : deepslate, FLAGS);
					} else if (fy < -1) {
						break;
					}
				}
			}
		}

		// Внешние стены зала Цитадели (-11..11 по X и Z, высота 1..9)
		for (int dx = -11; dx <= 11; dx++) {
			for (int dz = -11; dz <= 11; dz++) {
				boolean edgeX = Math.abs(dx) == 11;
				boolean edgeZ = Math.abs(dz) == 11;

				// Узорчатый пол зала
				if (!edgeX && !edgeZ) {
					BlockState floor = ((Math.abs(dx) + Math.abs(dz)) % 4 == 0) ? quartzBricks : diorite;
					m.set(cx + dx, y, cz + dz);
					world.setBlockState(m, floor, FLAGS);
				}

				if (edgeX || edgeZ) {
					for (int dy = 1; dy <= 9; dy++) {
						m.set(cx + dx, y + dy, cz + dz);
						if (dy == 1 || dy == 9) {
							world.setBlockState(m, deepslate, FLAGS);
						} else if (Math.abs(dx) % 5 == 0 && Math.abs(dz) % 5 == 0) {
							world.setBlockState(m, darkOak, FLAGS);
						} else if (dy >= 4 && dy <= 7 && ((edgeX && Math.abs(dz) % 4 == 2) || (edgeZ && Math.abs(dx) % 4 == 2))) {
							world.setBlockState(m, cyanGlass, FLAGS);
						} else {
							world.setBlockState(m, (dy % 3 == 0) ? quartzBricks : calcite, FLAGS);
						}
					}
				}

				// Потолок зала / крыша террасы (y + 10)
				m.set(cx + dx, y + 10, cz + dz);
				world.setBlockState(m, (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) ? seaLantern : deepslate, FLAGS);
			}
		}

		// 4 угловые сторожевые башни с зубцами!
		int[][] corners = new int[][]{{-11, -11}, {11, -11}, {-11, 11}, {11, 11}};
		for (int[] c : corners) {
			for (int tx = -2; tx <= 2; tx++) {
				for (int tz = -2; tz <= 2; tz++) {
					boolean bEdge = Math.abs(tx) == 2 || Math.abs(tz) == 2;
					for (int dy = 1; dy <= 13; dy++) {
						m.set(cx + c[0] + tx, y + dy, cz + c[1] + tz);
						if (dy == 13) {
							world.setBlockState(m, diorite, FLAGS);
						} else if (bEdge) {
							world.setBlockState(m, (Math.abs(tx) == 2 && Math.abs(tz) == 2) ? deepslate : calcite, FLAGS);
						} else if (dy < 13) {
							world.setBlockState(m, air, FLAGS);
						}
					}
					if (bEdge && (tx + tz) % 2 == 0) {
						m.set(cx + c[0] + tx, y + 14, cz + c[1] + tz);
						world.setBlockState(m, calcite, FLAGS);
					}
				}
			}
			m.set(cx + c[0], y + 14, cz + c[1]);
			world.setBlockState(m, lantern, FLAGS);
		}

		// ГИГАНТСКИЙ ЦЕНТРАЛЬНЫЙ БЕЛЫЙ ШПИЛЬ В ФОРМЕ ГНОМЬЕГО КОЛПАКА НА КРЫШЕ ЦИТАДЕЛИ (y + 11 .. y + 27)!
		int[][] spireLayers = new int[][]{
				{11, 6, 0}, {12, 5, 0}, {13, 5, 0}, {14, 4, 0}, {15, 4, -1},
				{16, 4, -1}, {17, 3, -1}, {18, 3, -2}, {19, 3, -2}, {20, 2, -3},
				{21, 2, -3}, {22, 2, -4}, {23, 1, -5}, {24, 1, -6}, {25, 1, -7}
		};
		for (int[] sl : spireLayers) {
			int sy = y + sl[0];
			int rad = sl[1];
			int zShift = sl[2]; // Изгиб шпиля-колпака!
			for (int dx = -rad; dx <= rad; dx++) {
				for (int dz = -rad; dz <= rad; dz++) {
					if (Math.abs(dx) == rad || Math.abs(dz) == rad || rad <= 1) {
						m.set(cx + dx, sy, cz + dz + zShift);
						world.setBlockState(m, (sl[0] % 4 == 0) ? quartz : calcite, FLAGS);
					}
				}
			}
		}
		// Светящийся помпон на кончике шпиля Цитадели!
		world.setBlockState(new BlockPos(cx, y + 25, cz - 8), seaLantern, FLAGS);

		// Парадный арочный вход с юга (cz + 11) + белая ковровая дорожка к трону
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 1; dy <= 5; dy++) {
				if (dy == 5 && Math.abs(dx) == 2) continue;
				world.setBlockState(new BlockPos(cx + dx, y + dy, cz + 11), air, FLAGS);
			}
			// Ступени крыльца
			world.setBlockState(new BlockPos(cx + dx, y, cz + 12), quartzBricks, FLAGS);
			world.setBlockState(new BlockPos(cx + dx, y, cz + 13), diorite, FLAGS);
		}
		for (int dz = -6; dz <= 10; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				world.setBlockState(new BlockPos(cx + dx, y + 1, cz + dz), carpet, FLAGS);
			}
		}

		// Люстры душ под потолком зала
		int[][] lamps = new int[][]{{-5, -4}, {5, -4}, {-5, 4}, {5, 4}, {0, 0}};
		for (int[] lp : lamps) {
			world.setBlockState(new BlockPos(cx + lp[0], y + 9, cz + lp[1]), Blocks.CHAIN.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx + lp[0], y + 8, cz + lp[1]), lantern, FLAGS);
		}

		// ЛЕВОЕ КРЫЛО — Чумная Алхимическая Лаборатория (-9..-5, -3..5)
		BlockState fullCauldron = Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3);
		for (int dz = -2; dz <= 4; dz += 2) {
			world.setBlockState(new BlockPos(cx - 9, y + 1, cz + dz), quartzBricks, FLAGS);
			world.setBlockState(new BlockPos(cx - 9, y + 2, cz + dz), Blocks.BREWING_STAND.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx - 8, y + 1, cz + dz + 1), fullCauldron, FLAGS);
		}
		for (int dy = 1; dy <= 3; dy++) {
			world.setBlockState(new BlockPos(cx - 10, y + dy, cz - 5), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx - 9, y + dy, cz - 5), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
		}

		// Примечание v4.0.0: все тюремные клетки и изоляторы перенесены в измерение «Бюрократический Ад» (в обычном мире тюрячки не спавнятся!)

		// СЕВЕРНЫЙ ПОМОСТ ВЕРХОВНОГО МАГИСТРА И СОКРОВИЩНИЦА (cz - 8)
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -9; dz <= -6; dz++) {
				world.setBlockState(new BlockPos(cx + dx, y + 1, cz + dz), quartzBricks, FLAGS);
			}
		}
		// Трон и 3 богатых сундука Клана
		world.setBlockState(new BlockPos(cx, y + 2, cz - 8), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx, y + 3, cz - 9), Blocks.WHITE_BANNER.getDefaultState(), FLAGS);

		placeAndFillClanChest(world, new BlockPos(cx - 3, y + 2, cz - 8), 2);
		placeAndFillClanChest(world, new BlockPos(cx + 3, y + 2, cz - 8), 2);
		placeAndFillClanChest(world, new BlockPos(cx - 9, y + 1, cz + 6), 1);

		// ГАРНИЗОН ЦИТАДЕЛИ + БОСС «ВЕРХОВНЫЙ МАГИСТР БЕЛЫХ КОЛПАКОВ»!
		spawnClanMember(world, new BlockPos(cx, y + 2, cz - 6), WhiteCapEntity.RANK_GRAND_MASTER, center, 24);
		spawnClanMember(world, new BlockPos(cx - 4, y + 1, cz - 2), WhiteCapEntity.RANK_HUNTER, center, 26);
		spawnClanMember(world, new BlockPos(cx + 4, y + 1, cz - 2), WhiteCapEntity.RANK_HUNTER, center, 26);
		spawnClanMember(world, new BlockPos(cx - 6, y + 1, cz + 2), WhiteCapEntity.RANK_APOTHECARY, center, 26);
		spawnClanMember(world, new BlockPos(cx + 4, y + 1, cz + 5), WhiteCapEntity.RANK_APOTHECARY, center, 26);
		spawnClanMember(world, new BlockPos(cx - 2, y + 1, cz + 9), WhiteCapEntity.RANK_HUNTER, center, 28);
		spawnClanMember(world, new BlockPos(cx + 2, y + 1, cz + 9), WhiteCapEntity.RANK_ARBALIST, center, 28);
		// Снайперы-арбалетчики на угловых башнях Цитадели!
		spawnClanMember(world, new BlockPos(cx - 11, y + 14, cz + 11), WhiteCapEntity.RANK_ARBALIST, center, 32);
		spawnClanMember(world, new BlockPos(cx + 11, y + 14, cz + 11), WhiteCapEntity.RANK_ARBALIST, center, 32);
	}

	private static void buildPrisonCellWithAbomination(ServerWorld world, BlockPos cellCenter, boolean endermanPrisoner) {
		BlockState bars = Blocks.IRON_BARS.getDefaultState();
		BlockState deepslate = Blocks.DEEPSLATE_BRICKS.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		for (int dx = -1; dx <= 2; dx++) {
			for (int dz = -1; dz <= 2; dz++) {
				boolean edge = (dx == -1 || dx == 2 || dz == -1 || dz == 2);
				world.setBlockState(cellCenter.add(dx, -1, dz), deepslate, FLAGS);
				world.setBlockState(cellCenter.add(dx, 4, dz), deepslate, FLAGS);
				for (int dy = 0; dy <= 3; dy++) {
					world.setBlockState(cellCenter.add(dx, dy, dz), edge ? bars : air, FLAGS);
				}
			}
		}

		if (endermanPrisoner) {
			EndermanEntity captive = EntityType.ENDERMAN.create(world);
			if (captive != null) {
				captive.refreshPositionAndAngles(cellCenter.getX() + 0.5D, cellCenter.getY(), cellCenter.getZ() + 0.5D, 0.0F, 0.0F);
				captive.addCommandTag(WhiteCapEntity.TAG_SEALED_ENDER);
				captive.addCommandTag(WhiteCapEntity.TAG_CITADEL_PRISONER);
				captive.setCustomName(Text.literal("Подопытный Эндермен (В карантине)").formatted(Formatting.DARK_PURPLE));
				captive.setPersistent();
				world.spawnEntity(captive);
			}
		} else {
			WitherSkeletonEntity captive = EntityType.WITHER_SKELETON.create(world);
			if (captive != null) {
				captive.refreshPositionAndAngles(cellCenter.getX() + 0.5D, cellCenter.getY(), cellCenter.getZ() + 0.5D, 0.0F, 0.0F);
				captive.addCommandTag(WhiteCapEntity.TAG_CITADEL_PRISONER);
				captive.setCustomName(Text.literal("Пленный Скелет-Иссушитель").formatted(Formatting.DARK_GRAY));
				captive.setPersistent();
				world.spawnEntity(captive);
			}
		}
	}

	// =========================================================================
	// 2. СРЕДНЯЯ БАЗА — «КАРАНТИННЫЙ ФОРТ БЕЛЫХ КОЛПАКОВ» (17x17 + башня)
	// =========================================================================
	public static void buildFortifiedOutpost(ServerWorld world, BlockPos center) {
		WorldVirginLedger.runInStructureGenScope(() -> buildFortifiedOutpostInternal(world, center));
	}

	private static void buildFortifiedOutpostInternal(ServerWorld world, BlockPos center) {
		int cx = center.getX();
		int y = center.getY();
		int cz = center.getZ();

		BlockState calcite = Blocks.CALCITE.getDefaultState();
		BlockState diorite = Blocks.POLISHED_DIORITE.getDefaultState();
		BlockState darkOak = Blocks.DARK_OAK_LOG.getDefaultState();
		BlockState spruce = Blocks.SPRUCE_PLANKS.getDefaultState();
		BlockState fence = Blocks.SPRUCE_FENCE.getDefaultState();
		BlockState wool = Blocks.WHITE_WOOL.getDefaultState();
		BlockState lantern = Blocks.SOUL_LANTERN.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 9; dz++) {
				for (int dy = 1; dy <= 18; dy++) {
					m.set(cx + dx, y + dy, cz + dz);
					if (!world.getBlockState(m).isAir()) {
						world.setBlockState(m, air, FLAGS);
					}
				}
				for (int fy = 0; fy >= -5; fy--) {
					m.set(cx + dx, y + fy, cz + dz);
					if (fy == 0 || !world.getBlockState(m).isSolidBlock(world, m)) {
						world.setBlockState(m, (Math.abs(dx + dz) % 3 == 0) ? calcite : diorite, FLAGS);
					} else if (fy < -1) {
						break;
					}
				}
			}
		}

		// Частокол вокруг двора (-8..8) с южными воротами
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				boolean border = Math.abs(dx) == 8 || Math.abs(dz) == 8;
				if (!border) continue;
				if (dz == 8 && Math.abs(dx) <= 1) continue; // Ворота
				int wallH = ((dx + dz) % 2 == 0) ? 4 : 3;
				for (int dy = 1; dy <= wallH; dy++) {
					m.set(cx + dx, y + dy, cz + dz);
					world.setBlockState(m, (Math.abs(dx) % 4 == 0 && Math.abs(dz) % 4 == 0) ? darkOak : calcite, FLAGS);
				}
			}
		}

		// Дозорная Башня в северо-западном углу (-5, -5) с белой остроконечной крышей-колпаком!
		for (int tx = -7; tx <= -3; tx++) {
			for (int tz = -7; tz <= -3; tz++) {
				boolean edge = (tx == -7 || tx == -3 || tz == -7 || tz == -3);
				for (int dy = 1; dy <= 10; dy++) {
					m.set(cx + tx, y + dy, cz + tz);
					if (dy == 10) {
						world.setBlockState(m, spruce, FLAGS);
					} else if (edge) {
						world.setBlockState(m, ((tx == -7 || tx == -3) && (tz == -7 || tz == -3)) ? darkOak : calcite, FLAGS);
					}
				}
				if (edge) {
					world.setBlockState(new BlockPos(cx + tx, y + 11, cz + tz), fence, FLAGS);
				}
			}
		}
		// Вход в башню и крыша-колпак (y + 13 .. y + 17)
		world.setBlockState(new BlockPos(cx - 5, y + 1, cz - 3), air, FLAGS);
		world.setBlockState(new BlockPos(cx - 5, y + 2, cz - 3), air, FLAGS);
		for (int rx = -7; rx <= -3; rx++) {
			for (int rz = -7; rz <= -3; rz++) {
				int dist = Math.max(Math.abs(rx + 5), Math.abs(rz + 5));
				world.setBlockState(new BlockPos(cx + rx, y + 13 + (2 - dist), cz + rz), wool, FLAGS);
			}
		}
		world.setBlockState(new BlockPos(cx - 5, y + 16, cz - 5), calcite, FLAGS);
		world.setBlockState(new BlockPos(cx - 5, y + 17, cz - 6), wool, FLAGS); // Изогнутый кончик колпака!

		// Алхимический навес на востоке + костёр душ в центре
		world.setBlockState(new BlockPos(cx, y + 1, cz + 1), Blocks.SOUL_CAMPFIRE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 5, y + 1, cz - 3), Blocks.BREWING_STAND.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 5, y + 1, cz - 1), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3), FLAGS);
		world.setBlockState(new BlockPos(cx + 6, y + 3, cz - 2), lantern, FLAGS);

		placeAndFillClanChest(world, new BlockPos(cx + 5, y + 1, cz - 5), 1);
		placeAndFillClanChest(world, new BlockPos(cx - 5, y + 11, cz - 5), 1);

		// Гарнизон Форта (6 бойцов)
		spawnClanMember(world, new BlockPos(cx - 5, y + 11, cz - 4), WhiteCapEntity.RANK_ARBALIST, center, 24);
		spawnClanMember(world, new BlockPos(cx + 2, y + 1, cz + 2), WhiteCapEntity.RANK_HUNTER, center, 22);
		spawnClanMember(world, new BlockPos(cx - 2, y + 1, cz + 3), WhiteCapEntity.RANK_HUNTER, center, 22);
		spawnClanMember(world, new BlockPos(cx + 4, y + 1, cz - 2), WhiteCapEntity.RANK_APOTHECARY, center, 22);
		spawnClanMember(world, new BlockPos(cx, y + 1, cz + 6), WhiteCapEntity.RANK_ARBALIST, center, 24);
		spawnClanMember(world, new BlockPos(cx - 3, y + 1, cz - 1), WhiteCapEntity.RANK_APOTHECARY, center, 22);
	}

	// =========================================================================
	// 3. ПОЛЕВОЙ ОХОТНИЧИЙ ЛАГЕРЬ (белые шатры-колпаки + костёр + котёл)
	// =========================================================================
	public static void buildFieldCamp(ServerWorld world, BlockPos center) {
		WorldVirginLedger.runInStructureGenScope(() -> buildFieldCampInternal(world, center));
	}

	private static void buildFieldCampInternal(ServerWorld world, BlockPos center) {
		int cx = center.getX();
		int y = center.getY();
		int cz = center.getZ();

		BlockState air = Blocks.AIR.getDefaultState();
		BlockState gravel = Blocks.POLISHED_ANDESITE.getDefaultState();
		BlockState calcite = Blocks.CALCITE.getDefaultState();
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				for (int dy = 1; dy <= 10; dy++) {
					m.set(cx + dx, y + dy, cz + dz);
					if (!world.getBlockState(m).isAir()) {
						world.setBlockState(m, air, FLAGS);
					}
				}
				if (dx * dx + dz * dz <= 38) {
					for (int fy = 0; fy >= -4; fy--) {
						m.set(cx + dx, y + fy, cz + dz);
						if (fy == 0 || !world.getBlockState(m).isSolidBlock(world, m)) {
							world.setBlockState(m, ((dx + dz) & 1) == 0 ? calcite : gravel, FLAGS);
						} else {
							break;
						}
					}
				}
			}
		}

		// Два остроконечных белых шатра в форме гномьих колпаков!
		buildGnomeTent(world, new BlockPos(cx - 4, y + 1, cz - 2));
		buildGnomeTent(world, new BlockPos(cx + 4, y + 1, cz - 2));

		// Центральный костёр душ + походная алхимия + столб с трофейным черепом иссушителя!
		world.setBlockState(new BlockPos(cx, y + 1, cz + 1), Blocks.SOUL_CAMPFIRE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx - 1, y + 1, cz + 4), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3), FLAGS);
		world.setBlockState(new BlockPos(cx + 1, y + 1, cz + 4), Blocks.BREWING_STAND.getDefaultState(), FLAGS);

		world.setBlockState(new BlockPos(cx, y + 1, cz - 4), Blocks.SPRUCE_FENCE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx, y + 2, cz - 4), Blocks.SPRUCE_FENCE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx, y + 3, cz - 4), Blocks.WITHER_SKELETON_SKULL.getDefaultState(), FLAGS);

		placeAndFillClanChest(world, new BlockPos(cx, y + 1, cz - 2), 0);

		spawnClanMember(world, new BlockPos(cx - 2, y + 1, cz + 1), WhiteCapEntity.RANK_HUNTER, center, 20);
		spawnClanMember(world, new BlockPos(cx + 2, y + 1, cz + 1), WhiteCapEntity.RANK_HUNTER, center, 20);
		spawnClanMember(world, new BlockPos(cx, y + 1, cz + 3), WhiteCapEntity.RANK_APOTHECARY, center, 20);
		spawnClanMember(world, new BlockPos(cx + 2, y + 1, cz - 3), WhiteCapEntity.RANK_ARBALIST, center, 22);
	}

	// =========================================================================
	// 4. АДСКИЙ ЭКСПЕДИЦИОННЫЙ ЛАГЕРЬ В НЕЗЕРЕ (охота на Всполохов и Иссушителей!)
	// =========================================================================
	public static void buildNetherExpeditionCamp(ServerWorld world, BlockPos center) {
		WorldVirginLedger.runInStructureGenScope(() -> buildNetherExpeditionCampInternal(world, center));
	}

	private static void buildNetherExpeditionCampInternal(ServerWorld world, BlockPos center) {
		int cx = center.getX();
		int y = center.getY();
		int cz = center.getZ();

		BlockState calcite = Blocks.CALCITE.getDefaultState();
		BlockState basalt = Blocks.POLISHED_BASALT.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				if (dx * dx + dz * dz > 38) continue;
				world.setBlockState(new BlockPos(cx + dx, y, cz + dz), ((dx + dz) & 1) == 0 ? calcite : basalt, FLAGS);
				for (int dy = 1; dy <= 8; dy++) {
					world.setBlockState(new BlockPos(cx + dx, y + dy, cz + dz), air, FLAGS);
				}
			}
		}

		// Огнеупорный кварцево-кальцитовый шатёр-колпак в Незере!
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int dist = Math.max(Math.abs(dx), Math.abs(dz));
				if (dist == 2 && !(dz == 2 && dx == 0)) {
					world.setBlockState(new BlockPos(cx - 3 + dx, y + 1, cz + dz), calcite, FLAGS);
					world.setBlockState(new BlockPos(cx - 3 + dx, y + 2, cz + dz), Blocks.SMOOTH_QUARTZ.getDefaultState(), FLAGS);
				}
				world.setBlockState(new BlockPos(cx - 3 + dx, y + 3 + (2 - dist), cz + dz), calcite, FLAGS);
			}
		}
		world.setBlockState(new BlockPos(cx - 3, y + 6, cz - 1), Blocks.SEA_LANTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 2, y + 1, cz), Blocks.SOUL_CAMPFIRE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 3, y + 1, cz - 2), Blocks.BREWING_STAND.getDefaultState(), FLAGS);

		placeAndFillClanChest(world, new BlockPos(cx - 3, y + 1, cz), 1);

		// Ударный отряд против Всполохов, Гастов и Скелетов-Иссушителей!
		spawnClanMember(world, new BlockPos(cx + 1, y + 1, cz + 2), WhiteCapEntity.RANK_HUNTER, center, 26);
		spawnClanMember(world, new BlockPos(cx + 3, y + 1, cz + 1), WhiteCapEntity.RANK_ARBALIST, center, 28);
		spawnClanMember(world, new BlockPos(cx - 1, y + 1, cz + 3), WhiteCapEntity.RANK_ARBALIST, center, 28);
		spawnClanMember(world, new BlockPos(cx + 2, y + 1, cz - 1), WhiteCapEntity.RANK_APOTHECARY, center, 26);
		spawnClanMember(world, new BlockPos(cx, y + 1, cz - 2), WhiteCapEntity.RANK_HUNTER, center, 26);
	}

	private static void buildGnomeTent(ServerWorld world, BlockPos base) {
		BlockState wool = Blocks.WHITE_WOOL.getDefaultState();
		BlockState calcite = Blocks.CALCITE.getDefaultState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				int dist = Math.max(Math.abs(dx), Math.abs(dz));
				if (dist == 2 && !(dz == 2 && Math.abs(dx) <= 1)) {
					world.setBlockState(base.add(dx, 0, dz), wool, FLAGS);
					world.setBlockState(base.add(dx, 1, dz), wool, FLAGS);
				}
				world.setBlockState(base.add(dx, 2 + (2 - dist), dz), wool, FLAGS);
			}
		}
		// Вытянутый изогнутый кончик шатра-колпака!
		world.setBlockState(base.add(0, 5, 0), calcite, FLAGS);
		world.setBlockState(base.add(0, 6, -1), wool, FLAGS);
	}

	private static void placeAndFillClanChest(ServerWorld world, BlockPos pos, int tier) {
		world.setBlockState(pos, Blocks.CHEST.getDefaultState(), FLAGS);
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof ChestBlockEntity chest)) {
			return;
		}
		Random r = world.random;
		chest.setStack(0, new ItemStack(ModItems.WHITE_CAP_FLASK, 3 + tier * 2 + r.nextInt(3)));
		chest.setStack(1, new ItemStack(Items.EMERALD, 5 + tier * 6 + r.nextInt(7)));
		chest.setStack(2, new ItemStack(Items.IRON_INGOT, 6 + r.nextInt(8)));
		chest.setStack(3, new ItemStack(Items.GOLDEN_APPLE, 1 + tier));
		chest.setStack(4, new ItemStack(Items.ENDER_PEARL, 2 + r.nextInt(3))); // Конфискованные трофеи скверны!
		chest.setStack(5, new ItemStack(Items.BLAZE_ROD, 1 + r.nextInt(3)));

		if (tier >= 1) {
			chest.setStack(13, new ItemStack(ModItems.WHITE_CAP_COMPASS));
			chest.setStack(14, new ItemStack(Items.DIAMOND, 2 + tier * 2 + r.nextInt(4)));
		}
		if (tier >= 2) {
			chest.setStack(12, new ItemStack(ModItems.WHITE_CAP_HOOD));
			chest.setStack(22, new ItemStack(Items.NETHERITE_SCRAP, 1 + r.nextInt(2)));
		}
	}

	private static void spawnClanMember(ServerWorld world, BlockPos pos, int rank, BlockPos home, int radius) {
		WhiteCapEntity member = ModEntities.WHITE_CAP_ENTITY.create(world);
		if (member == null) {
			return;
		}
		if (!WorldVirginLedger.isInStructureGenScope()) {
			member.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
		}
		member.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, world.random.nextFloat() * 360.0F, 0.0F);
		member.setRank(rank);
		if (home != null) {
			member.setHomeAnchor(home, radius);
		} else {
			member.setPersistent();
		}
		world.spawnEntity(member);
	}

	private static boolean areChunksLoadedRadius(ServerWorld world, int x, int z, int r) {
		return world.isChunkLoaded((x - r) >> 4, (z - r) >> 4)
				&& world.isChunkLoaded((x + r) >> 4, (z - r) >> 4)
				&& world.isChunkLoaded((x - r) >> 4, (z + r) >> 4)
				&& world.isChunkLoaded((x + r) >> 4, (z + r) >> 4);
	}

	private static BlockPos findDryLandSurface(ServerWorld world, int startX, int startZ) {
		int[][] offsets = new int[][]{{0, 0}, {24, 0}, {-24, 0}, {0, 24}, {0, -24}, {32, 32}, {-32, -32}};
		for (int[] off : offsets) {
			int x = startX + off[0];
			int z = startZ + off[1];
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			RegistryEntry<Biome> b = world.getBiome(new BlockPos(x, world.getSeaLevel(), z));
			String bName = b.getKey().map(k -> k.getValue().getPath()).orElse("");
			if (bName.contains("ocean") || bName.contains("river")) {
				continue;
			}
			int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
			if (topY < world.getSeaLevel() - 1 || topY > 180) {
				continue;
			}
			BlockPos ground = new BlockPos(x, topY, z);
			BlockState bs = world.getBlockState(ground);
			if (!bs.getFluidState().isEmpty() || bs.isOf(Blocks.WATER) || bs.isOf(Blocks.LAVA)) {
				continue;
			}
			return ground;
		}
		return null;
	}

	private static BlockPos findNetherFloorSpot(ServerWorld world, int x, int z) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int y = 96; y >= 34; y--) {
			m.set(x, y, z);
			BlockState floor = world.getBlockState(m);
			if (floor.isSolidBlock(world, m) && floor.getFluidState().isEmpty() && !floor.isOf(Blocks.BEDROCK)) {
				boolean clear = true;
				for (int dy = 1; dy <= 7; dy++) {
					if (!world.getBlockState(m.set(x, y + dy, z)).isAir()) {
						clear = false;
						break;
					}
				}
				if (clear) {
					return new BlockPos(x, y, z);
				}
			}
		}
		return null;
	}
}
