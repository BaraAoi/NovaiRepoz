package com.novaimod.world;

import com.novaimod.block.TradeMachineBlock;
import com.novaimod.data.ModPersistentState;
import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.HolyOrderEntity;
import com.novaimod.entity.LuceEntity;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.registry.ModBlocks;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Генерирует все заведения и локации мода:
 * 1) Микрокредитная организация (МФО) — рядом со спавном мира.
 * 2) Продуктовый магазин — на поверхности.
 * 3) Шахтёрская лавка — под землёй (с проверкой и расчисткой прохода у входа).
 * 4) Лавовый курорт с Адольфом Гидлером — строго на берегу настоящего лавового озера в Незере!
 * 5) Биомные и пещерные Казино — со столами «Монетка» и «Рулетка».
 * 6) «Эго-остров» — тусовочный тропический остров в океане/воде с кухней и сундуками «Странного мяса»,
 *    Шеф-поваром, Эйнштейном, Сталиным, Свити Фиком, Тусовщицами, переливающимся Танцполом с диджейским пультом,
 *    Бассейном и Джакузи с пузырьками, в котором можно сидеть!
 */
public final class ModStructureGenerator {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private ModStructureGenerator() {
	}

	public static void tickWorldGeneration(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		ServerWorld overworld = server.getOverworld();

		if (!state.isMfoSpawned() && !overworld.getPlayers().isEmpty()) {
			BlockPos worldSpawn = overworld.getSpawnPos();
			int mx = worldSpawn.getX() + 18;
			int mz = worldSpawn.getZ() + 14;
			if (overworld.isChunkLoaded(mx >> 4, mz >> 4)) {
				int my = findSurfaceY(overworld, mx, mz);
				BlockPos mfoOrigin = new BlockPos(mx, my, mz);
				buildMfoOffice(overworld, mfoOrigin);
				state.setMfoSpawned(mfoOrigin);
			}
		}

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.isSpectator()) {
				continue;
			}
			ServerWorld world = player.getServerWorld();
			int regionX = Math.floorDiv(player.getBlockX(), 224);
			int regionZ = Math.floorDiv(player.getBlockZ(), 224);

			if (world.getRegistryKey() == World.OVERWORLD) {
				tryGenerateOverworldRegion(world, state, regionX, regionZ);
			} else if (world.getRegistryKey() == World.NETHER) {
				tryGenerateNetherRegion(world, state, player, regionX, regionZ);
			}
		}
	}

	private static void tryGenerateOverworldRegion(ServerWorld world, ModPersistentState state, int rx, int rz) {
		String key = "ow:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}

		Random rng = Random.create(((long) rx * 341873128712L) ^ ((long) rz * 132897987541L) ^ world.getSeed());
		int baseX = rx * 224 + 48 + rng.nextInt(112);
		int baseZ = rz * 224 + 48 + rng.nextInt(112);

		if (state.getMfoPos() != null && state.getMfoPos().getSquaredDistance(baseX, state.getMfoPos().getY(), baseZ) < 56.0D * 56.0D) {
			baseX += 72;
		}
		// Для крупного острова проверяем загрузку соседних чанков
		if (!world.isChunkLoaded((baseX - 32) >> 4, (baseZ - 32) >> 4)
				|| !world.isChunkLoaded((baseX + 32) >> 4, (baseZ + 32) >> 4)) {
			return;
		}

		state.markRegionGenerated(key);

		BlockPos checkPos = new BlockPos(baseX, world.getSeaLevel(), baseZ);
		if (isOceanOrOpenWaterSpot(world, checkPos)) {
			// В океанах и крупных водоёмах генерируем тусовочный «Эго-остров»!
			BlockPos islandOrigin = new BlockPos(baseX, world.getSeaLevel() + 1, baseZ);
			buildEgoIsland(world, islandOrigin);
			state.recordStructure("ego_island", "minecraft:overworld", islandOrigin);
		} else {
			int roll = Math.floorMod(rx * 31 + rz * 17, 4);
			int surfY = findSurfaceY(world, baseX, baseZ);
			BlockPos surfacePos = new BlockPos(baseX, surfY, baseZ);

			if (roll == 0) {
				buildGroceryStore(world, surfacePos);
				state.recordStructure("grocery", "minecraft:overworld", surfacePos);
			} else if (roll == 3) {
				// Иногда генерируем Эго-остров с собственной лагуной даже у побережья/рек
				BlockPos waterSpot = findNearbyWaterOrCoast(world, baseX, baseZ);
				if (waterSpot != null) {
					buildEgoIsland(world, waterSpot);
					state.recordStructure("ego_island", "minecraft:overworld", waterSpot);
				} else {
					String biomeStyle = detectBiomeCasinoStyle(world, surfacePos);
					buildBiomeCasino(world, surfacePos, biomeStyle);
					state.recordStructure("casino_" + biomeStyle, "minecraft:overworld", surfacePos);
				}
			} else {
				String biomeStyle = detectBiomeCasinoStyle(world, surfacePos);
				buildBiomeCasino(world, surfacePos, biomeStyle);
				state.recordStructure("casino_" + biomeStyle, "minecraft:overworld", surfacePos);
			}
		}

		// Подземные структуры: Шахтёрская лавка или Пещерное казино со свободным входом
		int ugX = baseX + (rng.nextBoolean() ? 24 : -24);
		int ugZ = baseZ + (rng.nextBoolean() ? 24 : -24);
		if (world.isChunkLoaded(ugX >> 4, (ugZ - 16) >> 4) && world.isChunkLoaded(ugX >> 4, ugZ >> 4)) {
			BlockPos ugPos = findAccessibleUndergroundSpot(world, ugX, ugZ, rng);
			if ((rx + rz) % 2 == 0) {
				buildMinerShop(world, ugPos);
				state.recordStructure("miner_shop", "minecraft:overworld", ugPos);
			} else {
				buildBiomeCasino(world, ugPos, "cave");
				state.recordStructure("casino_cave", "minecraft:overworld", ugPos);
			}
		}
	}

	private static boolean isOceanOrOpenWaterSpot(ServerWorld world, BlockPos center) {
		RegistryEntry<Biome> entry = world.getBiome(center);
		String biomeId = entry.getKey().map(k -> k.getValue().getPath()).orElse("");
		if (biomeId.contains("ocean")) {
			return true;
		}
		int sea = world.getSeaLevel();
		int waterCols = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -12; dx <= 12; dx += 6) {
			for (int dz = -12; dz <= 12; dz += 6) {
				m.set(center.getX() + dx, sea - 1, center.getZ() + dz);
				if (world.getBlockState(m).isOf(Blocks.WATER)) {
					waterCols++;
				}
			}
		}
		return waterCols >= 14;
	}

	private static BlockPos findNearbyWaterOrCoast(ServerWorld world, int cx, int cz) {
		int sea = world.getSeaLevel();
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -36; dx <= 36; dx += 18) {
			for (int dz = -36; dz <= 36; dz += 18) {
				int x = cx + dx;
				int z = cz + dz;
				if (!world.isChunkLoaded((x - 28) >> 4, (z - 28) >> 4)
						|| !world.isChunkLoaded((x + 28) >> 4, (z + 28) >> 4)) {
					continue;
				}
				m.set(x, sea - 1, z);
				if (world.getBlockState(m).isOf(Blocks.WATER) && world.getBlockState(m.up(2)).isAir()) {
					return new BlockPos(x, sea + 1, z);
				}
			}
		}
		return null;
	}

	/**
	 * Генерирует Лавовый курорт в Незере СТРОГО на берегу настоящего открытого лавового озера (Y ≈ 31),
	 * а не в случайных замкнутых пещерах!
	 */
	private static void tryGenerateNetherRegion(
			ServerWorld nether,
			ModPersistentState state,
			ServerPlayerEntity nearPlayer,
			int rx,
			int rz
	) {
		String key = "nether:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}

		// Ищем настоящий берег открытого лавового озера среди загруженных чанков вокруг игрока в этом регионе
		BlockPos shoreOrigin = findNetherLavaLakeShore(nether, nearPlayer.getBlockX(), nearPlayer.getBlockZ());
		if (shoreOrigin == null) {
			// Пока рядом нет настоящего озера с лавой — НЕ спавним курорт в скале, ждём выхода к озеру лавы!
			return;
		}

		state.markRegionGenerated(key);
		buildLavaResort(nether, shoreOrigin);
		state.recordStructure("lava_resort", "minecraft:the_nether", shoreOrigin);
	}

	/**
	 * Находит точку прямо на берегу открытого лавового озера в Аду:
	 * с южной стороны (+Z) должно простираться настоящее открытое озеро лавы (Y = 31..35 с воздухом сверху),
	 * а сама площадка стоит у кромки берега на 1 блок выше уровня лавы!
	 */
	private static BlockPos findNetherLavaLakeShore(ServerWorld nether, int searchCenterX, int searchCenterZ) {
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int radius = 8; radius <= 56; radius += 8) {
			for (int dx = -radius; dx <= radius; dx += 8) {
				for (int dz = -radius; dz <= radius; dz += 8) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}
					int cx = searchCenterX + dx;
					int cz = searchCenterZ + dz;
					if (!nether.isChunkLoaded((cx - 10) >> 4, (cz - 10) >> 4)
							|| !nether.isChunkLoaded((cx + 10) >> 4, (cz + 14) >> 4)) {
						continue;
					}

					// Проверяем уровень лавового моря (Y = 31) и чуть более высокие озёра (до Y = 36)
					for (int lavaY = 31; lavaY <= 36; lavaY++) {
						// На месте самого пляжа (dz = -2..0) должен быть твёрдый берег у кромки, а не глубокая лава
						m.set(cx, lavaY, cz - 1);
						BlockState shoreGround = nether.getBlockState(m);
						if (shoreGround.isAir() || shoreGround.isOf(Blocks.LAVA) || shoreGround.isOf(Blocks.BEDROCK)) {
							continue;
						}

						// Над берегом должен быть воздух открытого грота (не монолитный камень!)
						m.set(cx, lavaY + 2, cz - 1);
						if (!nether.getBlockState(m).isAir()) {
							continue;
						}

						// А спереди (dz = +4 .. +11) должно лежать настоящее открытое озеро с лавой и свободным пространством над ним!
						int openLavaCount = 0;
						for (int lx = -4; lx <= 4; lx += 2) {
							for (int lz = 4; lz <= 10; lz += 2) {
								m.set(cx + lx, lavaY, cz + lz);
								if (nether.getBlockState(m).isOf(Blocks.LAVA)) {
									boolean openAirAbove = true;
									for (int ay = 1; ay <= 4; ay++) {
										m.set(cx + lx, lavaY + ay, cz + lz);
										if (!nether.getBlockState(m).isAir()) {
											openAirAbove = false;
											break;
										}
									}
									if (openAirAbove) {
										openLavaCount++;
									}
								}
							}
						}

						if (openLavaCount >= 12) {
							return new BlockPos(cx, lavaY + 1, cz);
						}
					}
				}
			}
		}
		return null;
	}

	private static BlockPos findAccessibleUndergroundSpot(ServerWorld world, int centerX, int centerZ, Random rng) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		BlockPos bestPos = null;
		int bestOpenScore = -1;

		for (int attempt = 0; attempt < 18; attempt++) {
			int cx = centerX + (attempt == 0 ? 0 : rng.nextBetween(-12, 12));
			int cz = centerZ + (attempt == 0 ? 0 : rng.nextBetween(-12, 12));
			if (!world.isChunkLoaded(cx >> 4, cz >> 4) || !world.isChunkLoaded(cx >> 4, (cz - 12) >> 4)) {
				continue;
			}

			for (int y = 26; y >= -30; y -= 4) {
				m.set(cx, y, cz - 7);
				BlockState floor = world.getBlockState(m);
				if (floor.isAir() || floor.isOf(Blocks.LAVA) || floor.isOf(Blocks.WATER) || floor.isOf(Blocks.BEDROCK)) {
					continue;
				}
				int openAhead = countOpenEntranceSpace(world, new BlockPos(cx, y, cz - 7));
				if (openAhead > bestOpenScore) {
					bestOpenScore = openAhead;
					bestPos = new BlockPos(cx, y, cz);
					if (openAhead >= 12) {
						return bestPos;
					}
				}
			}
		}

		return bestPos != null ? bestPos : new BlockPos(centerX, rng.nextBetween(-20, 18), centerZ);
	}

	private static int countOpenEntranceSpace(ServerWorld world, BlockPos entranceFrontFloor) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		int airCount = 0;
		for (int dz = 0; dz >= -6; dz--) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = 1; dy <= 3; dy++) {
					m.set(entranceFrontFloor.getX() + dx, entranceFrontFloor.getY() + dy, entranceFrontFloor.getZ() + dz);
					if (world.getBlockState(m).isAir()) {
						airCount++;
					}
				}
			}
		}
		return airCount;
	}

	private static void ensureUndergroundEntrancePassage(
			ServerWorld world,
			BlockPos doorFloorCenter,
			Block floorBlock,
			Block supportWallBlock
	) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int step = 1; step <= 12; step++) {
			int z = doorFloorCenter.getZ() - step;
			if (!world.isChunkLoaded(doorFloorCenter.getX() >> 4, z >> 4)) {
				break;
			}
			if (step > 5 && countOpenEntranceSpace(world, new BlockPos(doorFloorCenter.getX(), doorFloorCenter.getY(), z)) >= 22) {
				break;
			}

			for (int dx = -2; dx <= 2; dx++) {
				int x = doorFloorCenter.getX() + dx;
				m.set(x, doorFloorCenter.getY(), z);
				if (world.getBlockState(m).isAir() || world.getBlockState(m).isReplaceable()
						|| world.getBlockState(m).isOf(Blocks.LAVA) || world.getBlockState(m).isOf(Blocks.WATER)) {
					world.setBlockState(m, floorBlock.getDefaultState(), FLAGS);
				}
				for (int dy = 1; dy <= 4; dy++) {
					m.set(x, doorFloorCenter.getY() + dy, z);
					world.setBlockState(m, Blocks.AIR.getDefaultState(), FLAGS);
				}
				m.set(x, doorFloorCenter.getY() + 5, z);
				BlockState above = world.getBlockState(m);
				if (above.isOf(Blocks.LAVA) || above.isOf(Blocks.WATER) || above.isOf(Blocks.GRAVEL) || above.isOf(Blocks.SAND)) {
					world.setBlockState(m, supportWallBlock.getDefaultState(), FLAGS);
				}
			}

			for (int side : new int[]{-3, 3}) {
				for (int dy = 1; dy <= 4; dy++) {
					m.set(doorFloorCenter.getX() + side, doorFloorCenter.getY() + dy, z);
					BlockState sideState = world.getBlockState(m);
					if (sideState.isOf(Blocks.LAVA) || sideState.isOf(Blocks.WATER)) {
						world.setBlockState(m, supportWallBlock.getDefaultState(), FLAGS);
					}
				}
			}

			if (step == 2 || step == 6 || step == 10) {
				m.set(doorFloorCenter.getX(), doorFloorCenter.getY() + 4, z);
				if (!world.getBlockState(m.up()).isAir()) {
					world.setBlockState(m, Blocks.LANTERN.getDefaultState(), FLAGS);
				} else {
					world.setBlockState(m, Blocks.SEA_LANTERN.getDefaultState(), FLAGS);
				}
			}
		}
	}

	private static int findSurfaceY(ServerWorld world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return Math.max(world.getSeaLevel(), Math.min(200, y));
	}

	public static String detectBiomeCasinoStyle(ServerWorld world, BlockPos pos) {
		RegistryEntry<Biome> entry = world.getBiome(pos);
		String id = entry.getKey().map(k -> k.getValue().getPath()).orElse("");
		if (id.contains("desert") || id.contains("badlands") || id.contains("eroded")) {
			return "desert";
		}
		if (id.contains("snow") || id.contains("ice") || id.contains("frozen") || id.contains("taiga") || id.contains("grove")) {
			return "snowy";
		}
		if (id.contains("jungle") || id.contains("swamp") || id.contains("mangrove") || id.contains("bamboo")) {
			return "jungle";
		}
		return "plains";
	}

	// =========================================================================
	// 1. МИКРОКРЕДИТНАЯ ОРГАНИЗАЦИЯ (МФО)
	// =========================================================================
	public static void buildMfoOffice(ServerWorld world, BlockPos origin) {
		WorldVirginLedger.runInStructureGenScope(() -> buildMfoOfficeInternal(world, origin));
	}

	private static void buildMfoOfficeInternal(ServerWorld world, BlockPos origin) {
		fillFoundation(world, origin.add(-6, -4, -6), origin.add(6, -1, 7), Blocks.STONE_BRICKS.getDefaultState());
		fillBox(world, origin.add(-7, 0, -8), origin.add(7, 7, 7), Blocks.AIR.getDefaultState());

		fillBox(world, origin.add(-6, 0, -6), origin.add(6, 0, 6), Blocks.QUARTZ_BRICKS.getDefaultState());
		fillBox(world, origin.add(-1, 0, -6), origin.add(1, 0, 3), Blocks.RED_WOOL.getDefaultState());

		for (int dx = -6; dx <= 6; dx++) {
			for (int dy = 1; dy <= 5; dy++) {
				for (int dz = -6; dz <= 6; dz++) {
					boolean edgeX = Math.abs(dx) == 6;
					boolean edgeZ = Math.abs(dz) == 6;
					if (dy == 5) {
						Block roof = (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ;
						setBlock(world, origin.add(dx, dy, dz), roof.getDefaultState());
					} else if (edgeX && edgeZ) {
						setBlock(world, origin.add(dx, dy, dz), Blocks.GOLD_BLOCK.getDefaultState());
					} else if (edgeX || edgeZ) {
						if (dz == -6 && Math.abs(dx) <= 1 && dy <= 3) {
							setBlock(world, origin.add(dx, dy, dz), Blocks.AIR.getDefaultState());
						} else if (dy >= 2 && dy <= 3 && (Math.abs(dx) == 3 || Math.abs(dz) == 3)) {
							setBlock(world, origin.add(dx, dy, dz), Blocks.TINTED_GLASS.getDefaultState());
						} else {
							setBlock(world, origin.add(dx, dy, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
						}
					}
				}
			}
		}

		fillBox(world, origin.add(-4, 5, -7), origin.add(4, 6, -7), Blocks.RED_CONCRETE.getDefaultState());
		setBlock(world, origin.add(0, 6, -7), Blocks.GOLD_BLOCK.getDefaultState());
		setBlock(world, origin.add(-2, 6, -7), Blocks.SEA_LANTERN.getDefaultState());
		setBlock(world, origin.add(2, 6, -7), Blocks.SEA_LANTERN.getDefaultState());

		fillBox(world, origin.add(-2, 1, 3), origin.add(2, 1, 3), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
		fillBox(world, origin.add(-4, 1, 0), origin.add(-4, 1, 2), Blocks.GOLD_BLOCK.getDefaultState());
		fillBox(world, origin.add(4, 1, 0), origin.add(4, 1, 2), Blocks.GOLD_BLOCK.getDefaultState());

		setBlock(
				world,
				origin.add(2, 1, -7),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH)
		);

		spawnNpcAt(world, origin.add(-2, 1, -8), NpcRole.MFO_BARKER, "");
		spawnNpcAt(world, origin.add(0, 1, 4), NpcRole.MFO_LENDER, "");
		spawnNpcAt(world, origin.add(-5, 1, 1), NpcRole.MFO_ELITE_SELLER, "1");
		spawnNpcAt(world, origin.add(5, 1, 1), NpcRole.MFO_ELITE_SELLER, "2");
	}

	// =========================================================================
	// 2. ПРОДУКТОВЫЙ МАГАЗИН
	// =========================================================================
	public static void buildGroceryStore(ServerWorld world, BlockPos origin) {
		WorldVirginLedger.runInStructureGenScope(() -> buildGroceryStoreInternal(world, origin));
	}

	private static void buildGroceryStoreInternal(ServerWorld world, BlockPos origin) {
		fillFoundation(world, origin.add(-5, -4, -5), origin.add(5, -1, 5), Blocks.COBBLESTONE.getDefaultState());
		fillBox(world, origin.add(-6, 0, -7), origin.add(6, 6, 6), Blocks.AIR.getDefaultState());

		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				Block floor = ((dx + dz) & 1) == 0 ? Blocks.BIRCH_PLANKS : Blocks.SPRUCE_PLANKS;
				setBlock(world, origin.add(dx, 0, dz), floor.getDefaultState());
			}
		}

		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = 1; dy <= 4; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					boolean edgeX = Math.abs(dx) == 5;
					boolean edgeZ = Math.abs(dz) == 5;
					if (edgeX && edgeZ) {
						setBlock(world, origin.add(dx, dy, dz), Blocks.OAK_LOG.getDefaultState());
					} else if (edgeX || edgeZ) {
						if (dz == -5 && Math.abs(dx) <= 1 && dy <= 2) {
							setBlock(world, origin.add(dx, dy, dz), Blocks.AIR.getDefaultState());
						} else if (dy == 2 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) {
							setBlock(world, origin.add(dx, dy, dz), Blocks.GLASS_PANE.getDefaultState());
						} else {
							setBlock(world, origin.add(dx, dy, dz), Blocks.BRICKS.getDefaultState());
						}
					}
				}
			}
		}

		fillBox(world, origin.add(-5, 5, -5), origin.add(5, 5, 5), Blocks.DARK_OAK_PLANKS.getDefaultState());
		setBlock(world, origin.add(0, 5, 0), Blocks.SEA_LANTERN.getDefaultState());
		for (int dx = -3; dx <= 3; dx++) {
			setBlock(world, origin.add(dx, 4, -6), ((dx & 1) == 0 ? Blocks.GREEN_WOOL : Blocks.WHITE_WOOL).getDefaultState());
		}

		fillBox(world, origin.add(-4, 1, 2), origin.add(-1, 1, 2), Blocks.OAK_PLANKS.getDefaultState());
		fillBox(world, origin.add(1, 1, 2), origin.add(4, 1, 2), Blocks.SPRUCE_PLANKS.getDefaultState());
		setBlock(world, origin.add(-4, 1, 4), Blocks.HAY_BLOCK.getDefaultState());
		setBlock(world, origin.add(-3, 1, 4), Blocks.MELON.getDefaultState());
		setBlock(world, origin.add(3, 1, 4), Blocks.BARREL.getDefaultState());
		setBlock(world, origin.add(4, 1, 4), Blocks.SMOKER.getDefaultState());

		setBlock(
				world,
				origin.add(2, 1, -6),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH)
		);

		spawnNpcAt(world, origin.add(-3, 1, 3), NpcRole.GROCERY_SELLER_1, "");
		spawnNpcAt(world, origin.add(3, 1, 3), NpcRole.GROCERY_SELLER_2, "");
	}

	// =========================================================================
	// 3. ШАХТЁРСКАЯ ЛАВКА
	// =========================================================================
	public static void buildMinerShop(ServerWorld world, BlockPos origin) {
		WorldVirginLedger.runInStructureGenScope(() -> buildMinerShopInternal(world, origin));
	}

	private static void buildMinerShopInternal(ServerWorld world, BlockPos origin) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = 0; dy <= 5; dy++) {
				for (int dz = -6; dz <= 5; dz++) {
					BlockPos p = origin.add(dx, dy, dz);
					if (dy == 0) {
						setBlock(world, p, Blocks.COBBLED_DEEPSLATE.getDefaultState());
					} else if (dy == 5) {
						setBlock(world, p, Blocks.DEEPSLATE_BRICKS.getDefaultState());
					} else if (Math.abs(dx) == 5 || dz == 5 || dz == -6) {
						if (dz == -6 && Math.abs(dx) <= 1 && dy <= 3) {
							setBlock(world, p, Blocks.AIR.getDefaultState());
						} else if (Math.abs(dx) == 5 && (dz % 3 == 0)) {
							setBlock(world, p, Blocks.OAK_LOG.getDefaultState());
						} else {
							setBlock(world, p, Blocks.DEEPSLATE_BRICKS.getDefaultState());
						}
					} else {
						setBlock(world, p, Blocks.AIR.getDefaultState());
					}
				}
			}
		}

		ensureUndergroundEntrancePassage(world, origin.add(0, 0, -6), Blocks.COBBLED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS);

		setBlock(world, origin.add(-3, 4, -2), Blocks.LANTERN.getDefaultState());
		setBlock(world, origin.add(3, 4, -2), Blocks.LANTERN.getDefaultState());
		setBlock(world, origin.add(0, 4, 2), Blocks.SEA_LANTERN.getDefaultState());

		setBlock(world, origin.add(-4, 1, 4), Blocks.COAL_ORE.getDefaultState());
		setBlock(world, origin.add(-3, 1, 4), Blocks.IRON_ORE.getDefaultState());
		setBlock(world, origin.add(3, 1, 4), Blocks.GOLD_ORE.getDefaultState());
		setBlock(world, origin.add(4, 1, 4), Blocks.DIAMOND_ORE.getDefaultState());

		fillBox(world, origin.add(-2, 1, 2), origin.add(2, 1, 2), Blocks.SPRUCE_PLANKS.getDefaultState());
		setBlock(world, origin.add(-3, 1, 0), ModBlocks.REPAIR_STATION.getDefaultState());
		setBlock(world, origin.add(-3, 1, 1), Blocks.BLAST_FURNACE.getDefaultState());

		setBlock(
				world,
				origin.add(2, 1, -5),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH)
		);

		spawnNpcAt(world, origin.add(0, 1, 3), NpcRole.MINER_TRADER, "");
	}

	// =========================================================================
	// 4. ЛАВОВЫЙ КУРОРТ НА БЕРЕГУ НАСТОЯЩЕГО ОЗЕРА ЛАВЫ В НЕЗЕРЕ
	// =========================================================================
	public static void buildLavaResort(ServerWorld nether, BlockPos origin) {
		WorldVirginLedger.runInStructureGenScope(() -> buildLavaResortInternal(nether, origin));
	}

	private static void buildLavaResortInternal(ServerWorld nether, BlockPos origin) {
		// Расчищаем береговой пляж с видом прямо на естественное озеро лавы спереди (+Z)
		fillBox(nether, origin.add(-8, 1, -6), origin.add(8, 7, 3), Blocks.AIR.getDefaultState());

		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -6; dz <= 4; dz++) {
				// Естественная полукруглая береговая линия песчаного пляжа
				if (dx * dx + dz * dz > 76) {
					continue;
				}
				if (dz <= 2) {
					fillBox(nether, origin.add(dx, -2, dz), origin.add(dx, -1, dz), Blocks.SANDSTONE.getDefaultState());
					Block sand = ((dx + dz) % 3 == 0) ? Blocks.RED_SAND : Blocks.SAND;
					setBlock(nether, origin.add(dx, 0, dz), sand.getDefaultState());
				} else {
					// Плавный спуск песчаной отмели прямо в настоящее озеро с лавой на уровне origin.getY() - 1!
					setBlock(nether, origin.add(dx, -2, dz), Blocks.SANDSTONE.getDefaultState());
					if (!nether.getBlockState(origin.add(dx, -1, dz)).isOf(Blocks.LAVA)) {
						setBlock(nether, origin.add(dx, -1, dz), Blocks.LAVA.getDefaultState());
					}
					setBlock(nether, origin.add(dx, 0, dz), Blocks.AIR.getDefaultState());
				}
			}
		}

		// Шезлонги и пляжные зонтики у кромки лавового озера
		setBlock(nether, origin.add(-2, 1, 0), Blocks.SANDSTONE_SLAB.getDefaultState());
		setBlock(nether, origin.add(-2, 1, -1), Blocks.SANDSTONE_STAIRS.getDefaultState());
		setBlock(nether, origin.add(2, 1, 0), Blocks.SANDSTONE_SLAB.getDefaultState());
		setBlock(nether, origin.add(2, 1, -1), Blocks.SANDSTONE_STAIRS.getDefaultState());

		buildBeachUmbrella(nether, origin.add(0, 1, -1), Blocks.RED_WOOL, Blocks.WHITE_WOOL);
		buildBeachUmbrella(nether, origin.add(-5, 1, -2), Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL);

		for (int dy = 1; dy <= 4; dy++) {
			setBlock(nether, origin.add(5, dy, -2), Blocks.CRIMSON_STEM.getDefaultState());
		}
		fillBox(nether, origin.add(4, 5, -3), origin.add(6, 5, -1), Blocks.WARPED_WART_BLOCK.getDefaultState());
		setBlock(nether, origin.add(5, 4, -1), Blocks.SHROOMLIGHT.getDefaultState());

		setBlock(
				nether,
				origin.add(-5, 1, -4),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.SOUTH)
		);

		spawnNpcAt(nether, origin.add(0, 1, 0), NpcRole.ADOLF_GIDLER, "");
	}

	private static void buildBeachUmbrella(ServerWorld world, BlockPos base, Block c1, Block c2) {
		for (int dy = 0; dy < 3; dy++) {
			setBlock(world, base.up(dy), Blocks.OAK_FENCE.getDefaultState());
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Block wool = ((dx + dz) & 1) == 0 ? c1 : c2;
				setBlock(world, base.add(dx, 3, dz), wool.getDefaultState());
			}
		}
	}

	// =========================================================================
	// 5. БИОМНОЕ КАЗИНО
	// =========================================================================
	public static void buildBiomeCasino(ServerWorld world, BlockPos origin, String style) {
		WorldVirginLedger.runInStructureGenScope(() -> buildBiomeCasinoInternal(world, origin, style));
	}

	private static void buildBiomeCasinoInternal(ServerWorld world, BlockPos origin, String style) {
		Block wallBlock;
		Block pillarBlock;
		Block floorPrimary;
		Block floorAccent;
		Block barCounterBlock;

		switch (style) {
			case "cave" -> {
				wallBlock = Blocks.DEEPSLATE_TILES;
				pillarBlock = Blocks.AMETHYST_BLOCK;
				floorPrimary = Blocks.POLISHED_DEEPSLATE;
				floorAccent = Blocks.CYAN_WOOL;
				barCounterBlock = Blocks.POLISHED_BASALT;
			}
			case "desert" -> {
				wallBlock = Blocks.CUT_SANDSTONE;
				pillarBlock = Blocks.GOLD_BLOCK;
				floorPrimary = Blocks.SMOOTH_SANDSTONE;
				floorAccent = Blocks.ORANGE_TERRACOTTA;
				barCounterBlock = Blocks.ACACIA_PLANKS;
			}
			case "snowy" -> {
				wallBlock = Blocks.SPRUCE_PLANKS;
				pillarBlock = Blocks.PACKED_ICE;
				floorPrimary = Blocks.QUARTZ_BRICKS;
				floorAccent = Blocks.BLUE_WOOL;
				barCounterBlock = Blocks.DARK_OAK_PLANKS;
			}
			case "jungle" -> {
				wallBlock = Blocks.MOSSY_STONE_BRICKS;
				pillarBlock = Blocks.STRIPPED_JUNGLE_LOG;
				floorPrimary = Blocks.JUNGLE_PLANKS;
				floorAccent = Blocks.LIME_WOOL;
				barCounterBlock = Blocks.BAMBOO_PLANKS;
			}
			default -> {
				wallBlock = Blocks.DARK_OAK_PLANKS;
				pillarBlock = Blocks.GOLD_BLOCK;
				floorPrimary = Blocks.BLACK_CONCRETE;
				floorAccent = Blocks.RED_WOOL;
				barCounterBlock = Blocks.QUARTZ_BRICKS;
			}
		}

		if (!"cave".equals(style)) {
			fillFoundation(world, origin.add(-6, -4, -6), origin.add(6, -1, 6), wallBlock.getDefaultState());
		}
		fillBox(world, origin.add(-6, 0, -7), origin.add(6, 6, 6), Blocks.AIR.getDefaultState());

		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				Block f = (Math.abs(dx) <= 2 || Math.abs(dz) <= 1) ? floorAccent : floorPrimary;
				setBlock(world, origin.add(dx, 0, dz), f.getDefaultState());
			}
		}

		for (int dx = -6; dx <= 6; dx++) {
			for (int dy = 1; dy <= 5; dy++) {
				for (int dz = -6; dz <= 6; dz++) {
					boolean edgeX = Math.abs(dx) == 6;
					boolean edgeZ = Math.abs(dz) == 6;
					if (dy == 5) {
						Block roof = ((dx % 3 == 0) && (dz % 3 == 0)) ? Blocks.SEA_LANTERN : wallBlock;
						setBlock(world, origin.add(dx, dy, dz), roof.getDefaultState());
					} else if (edgeX && edgeZ) {
						setBlock(world, origin.add(dx, dy, dz), pillarBlock.getDefaultState());
					} else if (edgeX || edgeZ) {
						if (dz == -6 && Math.abs(dx) <= 1 && dy <= 3) {
							setBlock(world, origin.add(dx, dy, dz), Blocks.AIR.getDefaultState());
						} else {
							setBlock(world, origin.add(dx, dy, dz), wallBlock.getDefaultState());
						}
					}
				}
			}
		}

		if ("cave".equals(style)) {
			setBlock(world, origin.add(2, 0, -7), floorPrimary.getDefaultState());
			ensureUndergroundEntrancePassage(world, origin.add(0, 0, -7), floorPrimary, wallBlock);
		}

		setBlock(world, origin.add(-2, 4, -6), Blocks.SEA_LANTERN.getDefaultState());
		setBlock(world, origin.add(0, 4, -6), Blocks.GOLD_BLOCK.getDefaultState());
		setBlock(world, origin.add(2, 4, -6), Blocks.SEA_LANTERN.getDefaultState());

		setBlock(world, origin.add(-3, 1, -2), ModBlocks.COINFLIP_TABLE.getDefaultState());
		setBlock(world, origin.add(-4, 1, -2), Blocks.RED_CARPET.getDefaultState());
		setBlock(world, origin.add(-2, 1, -2), Blocks.RED_CARPET.getDefaultState());

		setBlock(world, origin.add(3, 1, -2), ModBlocks.ROULETTE_TABLE.getDefaultState());
		setBlock(world, origin.add(4, 1, -2), Blocks.GREEN_CARPET.getDefaultState());
		setBlock(world, origin.add(2, 1, -2), Blocks.GREEN_CARPET.getDefaultState());

		fillBox(world, origin.add(-3, 1, 2), origin.add(3, 1, 2), barCounterBlock.getDefaultState());
		setBlock(world, origin.add(-2, 2, 4), Blocks.BREWING_STAND.getDefaultState());
		setBlock(world, origin.add(2, 2, 4), Blocks.BREWING_STAND.getDefaultState());
		fillBox(world, origin.add(-3, 1, 4), origin.add(3, 1, 4), pillarBlock.getDefaultState());

		setBlock(
				world,
				origin.add(2, 1, -7),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH)
		);

		spawnNpcAt(world, origin.add(0, 1, 3), NpcRole.CASINO_BARTENDER, style);
	}

	// =========================================================================
	// 6. «ЭГО-ОСТРОВ» (Знаменитая бело-синяя церковь с золотым куполом, Кухня со Странным мясом,
	//    Повар, Эйнштейн, Сталин, Свити Фик, Тусовщицы, Танцпол, Бассейн и Джакузи с мозаичным полом и Подушками!)
	// =========================================================================
	public static void buildEgoIsland(ServerWorld world, BlockPos origin) {
		WorldVirginLedger.runInStructureGenScope(() -> buildEgoIslandInternal(world, origin));
	}

	private static void buildEgoIslandInternal(ServerWorld world, BlockPos origin) {
		// Расчищаем воздух над всем островом (включая высокий золотой купол церкви!)
		fillBox(world, origin.add(-25, 1, -27), origin.add(25, 16, 25), Blocks.AIR.getDefaultState());

		// 1. Просторный тропический остров с золотистыми песчаными пляжами (радиус ~23 блока)
		for (int dx = -23; dx <= 23; dx++) {
			for (int dz = -23; dz <= 23; dz++) {
				int r2 = dx * dx + dz * dz;
				if (r2 > 23 * 23) {
					continue;
				}
				fillBox(world, origin.add(dx, -4, dz), origin.add(dx, -2, dz), Blocks.SANDSTONE.getDefaultState());
				if (r2 > 19 * 19) {
					setBlock(world, origin.add(dx, -1, dz), Blocks.SAND.getDefaultState());
					if (r2 <= 21 * 21) {
						setBlock(world, origin.add(dx, 0, dz), Blocks.SAND.getDefaultState());
					} else {
						setBlock(world, origin.add(dx, 0, dz), Blocks.WATER.getDefaultState());
					}
				} else {
					setBlock(world, origin.add(dx, -1, dz), Blocks.SMOOTH_SANDSTONE.getDefaultState());
					boolean boardwalk = (Math.abs(dx) <= 1 && dz >= -19 && dz <= 9)
							|| (Math.abs(dz) <= 1 && dx >= -16 && dx <= 15);
					Block surface = boardwalk ? Blocks.JUNGLE_PLANKS : Blocks.SMOOTH_SANDSTONE;
					setBlock(world, origin.add(dx, 0, dz), surface.getDefaultState());
				}
			}
		}

		// Северный деревянный причал (-Z) + Торговый аппарат
		fillBox(world, origin.add(-2, 0, -26), origin.add(2, 0, -20), Blocks.SPRUCE_PLANKS.getDefaultState());
		setBlock(world, origin.add(-2, 1, -26), Blocks.OAK_FENCE.getDefaultState());
		setBlock(world, origin.add(2, 1, -26), Blocks.OAK_FENCE.getDefaultState());
		setBlock(world, origin.add(-2, 2, -26), Blocks.LANTERN.getDefaultState());
		setBlock(world, origin.add(2, 2, -26), Blocks.LANTERN.getDefaultState());
		setBlock(
				world,
				origin.add(2, 1, -21),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH)
		);

		// 2. КУХНЯ НА ОСТРОВЕ + СУНДУКИ СО «СТРАННЫМ МЯСОМ» + ШЕФ-ПОВАР (северо-запад: dx = -16..-9, dz = -11..-4)
		fillBox(world, origin.add(-16, 0, -11), origin.add(-9, 0, -4), Blocks.POLISHED_ANDESITE.getDefaultState());
		for (int cx : new int[]{-16, -9}) {
			for (int cz : new int[]{-11, -4}) {
				fillBox(world, origin.add(cx, 1, cz), origin.add(cx, 3, cz), Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
			}
		}
		fillBox(world, origin.add(-16, 4, -11), origin.add(-9, 4, -4), Blocks.DARK_OAK_SLAB.getDefaultState());
		setBlock(world, origin.add(-12, 3, -7), Blocks.LANTERN.getDefaultState());

		setBlock(world, origin.add(-15, 1, -10), Blocks.SMOKER.getDefaultState());
		setBlock(world, origin.add(-14, 1, -10), Blocks.SMOKER.getDefaultState());
		setBlock(world, origin.add(-13, 1, -10), Blocks.CAMPFIRE.getDefaultState());
		fillBox(world, origin.add(-15, 1, -6), origin.add(-11, 1, -6), Blocks.QUARTZ_BRICKS.getDefaultState());

		BlockPos chestPos1 = origin.add(-11, 1, -10);
		BlockPos chestPos2 = origin.add(-10, 1, -10);
		setBlock(world, chestPos1, Blocks.CHEST.getDefaultState());
		setBlock(world, chestPos2, Blocks.CHEST.getDefaultState());
		populateKitchenChestWithStrangeMeat(world, chestPos1);
		populateKitchenChestWithStrangeMeat(world, chestPos2);

		spawnNpcAt(world, origin.add(-13, 1, -8), NpcRole.EGO_CHEF, "");

		// 3. СВЕТЯЩИЙСЯ ТАНЦПОЛ + ДИДЖЕЙСКИЙ ПУЛЬТ (западная сторона: dx = -15..-7, dz = +1..+9)
		Block[] discoPalette = new Block[]{
				Blocks.MAGENTA_STAINED_GLASS,
				Blocks.CYAN_STAINED_GLASS,
				Blocks.YELLOW_STAINED_GLASS,
				Blocks.LIME_STAINED_GLASS,
				Blocks.PURPLE_STAINED_GLASS,
				Blocks.PINK_STAINED_GLASS
		};
		for (int dx = -15; dx <= -7; dx++) {
			for (int dz = 1; dz <= 9; dz++) {
				if (dx == -15 || dx == -7 || dz == 1 || dz == 9) {
					setBlock(world, origin.add(dx, 0, dz), Blocks.GOLD_BLOCK.getDefaultState());
				} else {
					setBlock(world, origin.add(dx, -1, dz), Blocks.SEA_LANTERN.getDefaultState());
					Block glass = discoPalette[Math.floorMod(dx * 3 + dz * 5, discoPalette.length)];
					setBlock(world, origin.add(dx, 0, dz), glass.getDefaultState());
				}
			}
		}
		setBlock(world, origin.add(-13, 1, 1), Blocks.NOTE_BLOCK.getDefaultState());
		setBlock(world, origin.add(-12, 1, 1), Blocks.JUKEBOX.getDefaultState());
		setBlock(world, origin.add(-11, 1, 1), ModBlocks.DJ_CONSOLE.getDefaultState());
		setBlock(world, origin.add(-10, 1, 1), Blocks.JUKEBOX.getDefaultState());
		setBlock(world, origin.add(-9, 1, 1), Blocks.NOTE_BLOCK.getDefaultState());

		// 4. БОЛЬШОЙ БИРЮЗОВЫЙ БАССЕЙН (северо-восток: dx = +6..+15, dz = -12..-3)
		for (int dx = 6; dx <= 15; dx++) {
			for (int dz = -12; dz <= -3; dz++) {
				boolean rim = (dx == 6 || dx == 15 || dz == -12 || dz == -3);
				if (rim) {
					setBlock(world, origin.add(dx, 0, dz), Blocks.SMOOTH_QUARTZ.getDefaultState());
				} else {
					Block poolBottom = ((dx + dz) % 3 == 0) ? Blocks.SEA_LANTERN : Blocks.PRISMARINE_BRICKS;
					setBlock(world, origin.add(dx, -3, dz), poolBottom.getDefaultState());
					setBlock(world, origin.add(dx, -2, dz), Blocks.WATER.getDefaultState());
					setBlock(world, origin.add(dx, -1, dz), Blocks.WATER.getDefaultState());
					setBlock(world, origin.add(dx, 0, dz), Blocks.WATER.getDefaultState());
				}
			}
		}
		// Трамплин для прыжков в бассейн + ванильные шезлонги с мягкими Подушками у бассейна!
		setBlock(world, origin.add(10, 1, -3), Blocks.QUARTZ_SLAB.getDefaultState());
		setBlock(world, origin.add(10, 1, -4), Blocks.QUARTZ_SLAB.getDefaultState());
		BlockPos lounger1 = origin.add(8, 1, -14);
		BlockPos lounger2 = origin.add(12, 1, -14);
		setBlock(world, lounger1, Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
		setBlock(world, lounger2, Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
		CushionEntity.placeOnBlock(world, lounger1, DyeColor.YELLOW, Direction.SOUTH);
		CushionEntity.placeOnBlock(world, lounger2, DyeColor.PINK, Direction.SOUTH);
		buildBeachUmbrella(world, origin.add(10, 1, -14), Blocks.CYAN_WOOL, Blocks.WHITE_WOOL);

		// 5. ПЕРЕРАБОТАННОЕ ДЖАКУЗИ (восточная сторона: dx = +7..+15, dz = +1..+9)
		//    БЕЗ песка душ! Красивый мозаичный пол из глазурованной керамики и морских фонарей,
		//    обычные ванильные кварцевые ступеньки-сидушки по периметру и Подушки из новых обновлений!
		buildLuxuryJacuzzi(world, origin.add(11, 0, 5));

		// 6. ЗНАМЕНИТАЯ ЦЕРКОВЬ / ХРАМ НА ОСТРОВЕ (южный мыс: dx = -5..+5, dz = +10..+20)
		//    Бело-синие горизонтальные полосы, золотой купол, парадная лестница и интерьер с подушками на скамьях!
		buildFamousIslandChurch(world, origin.add(0, 0, 15));

		// 7. Тропические пальмы по краям острова
		buildTropicalPalm(world, origin.add(-17, 1, 11));
		buildTropicalPalm(world, origin.add(-17, 1, -13));
		buildTropicalPalm(world, origin.add(17, 1, -14));
		buildTropicalPalm(world, origin.add(17, 1, 11));

		// 8. Гости вечеринки на Эго-острове со своими скинами и фразами + женская Танцующая кукла в центре площади!
		setBlock(world, origin.add(0, 0, 0), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
		PartyDollEntity.placeDoll(world, origin.add(0, 0, 0), 180.0F);

		spawnNpcAt(world, origin.add(5, 1, 5), NpcRole.EINSTEIN, "");
		spawnNpcAt(world, origin.add(-5, 1, 4), NpcRole.STALIN, "");
		spawnNpcAt(world, origin.add(4, 1, -6), NpcRole.SWEETIE_FIK, "");

		spawnNpcAt(world, origin.add(-13, 1, 4), NpcRole.PARTY_GIRL, "1");
		spawnNpcAt(world, origin.add(-9, 1, 6), NpcRole.PARTY_GIRL, "2");
		spawnNpcAt(world, origin.add(6, 1, -14), NpcRole.PARTY_GIRL, "3");
		spawnNpcAt(world, origin.add(11, 1, 0), NpcRole.PARTY_GIRL, "1");
	}

	/**
	 * Роскошное Джакузи 9x9 БЕЗ песка душ:
	 * - На дне: симметричный узорчатый пол из голубой/бирюзовой глазурованной керамики, резного кварца и морских фонарей.
	 * - Сидушки: обычные ванильные затапливаемые кварцевые ступеньки (SMOOTH_QUARTZ_STAIRS) вдоль бортиков.
	 * - На сидушках: мягкие Подушки из новых обновлений (CushionEntity), на которых можно сидеть в тёплой воде!
	 */
	private static void buildLuxuryJacuzzi(ServerWorld world, BlockPos center) {
		for (int rx = -4; rx <= 4; rx++) {
			for (int rz = -4; rz <= 4; rz++) {
				BlockPos base = center.add(rx, 0, rz);
				boolean outerWall = (Math.abs(rx) == 4 || Math.abs(rz) == 4);
				boolean seatRing = !outerWall && (Math.abs(rx) == 3 || Math.abs(rz) == 3);

				if (outerWall) {
					setBlock(world, base.down(1), Blocks.QUARTZ_BRICKS.getDefaultState());
					setBlock(world, base, Blocks.SMOOTH_QUARTZ.getDefaultState());
					// Вход в джакузи с западной стороны (rx == -4, rz == 0)
					if (rx == -4 && rz == 0) {
						setBlock(world, base.up(1), Blocks.AIR.getDefaultState());
					} else {
						setBlock(world, base.up(1), Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
					}
				} else {
					// Красивый мозаичный пол на глубине dy = -2 (никакого песка душ!)
					Block mosaicFloor;
					if ((rx == 0 && rz == 0) || (Math.abs(rx) == 2 && Math.abs(rz) == 2)) {
						mosaicFloor = Blocks.SEA_LANTERN;
					} else if (rx == 0 || rz == 0) {
						mosaicFloor = Blocks.CHISELED_QUARTZ_BLOCK;
					} else if (((rx + rz) & 1) == 0) {
						mosaicFloor = Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA;
					} else {
						mosaicFloor = Blocks.CYAN_GLAZED_TERRACOTTA;
					}
					setBlock(world, base.down(2), mosaicFloor.getDefaultState());

					if (seatRing) {
						// Опора под ванильными сидушками из призмаринового/кварцевого кирпича
						Block underSeat = ((rx + rz) & 1) == 0 ? Blocks.PRISMARINE_BRICKS : Blocks.QUARTZ_BRICKS;
						setBlock(world, base.down(1), underSeat.getDefaultState());

						// Обычные ванильные затапливаемые ступеньки (SMOOTH_QUARTZ_STAIRS) спинкой к бортику!
						Direction backFacing;
						if (rx == -3) {
							backFacing = Direction.WEST;
						} else if (rx == 3) {
							backFacing = Direction.EAST;
						} else if (rz == -3) {
							backFacing = Direction.NORTH;
						} else {
							backFacing = Direction.SOUTH;
						}
						BlockState vanillaSeatStair = Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState()
								.with(StairsBlock.FACING, backFacing)
								.with(StairsBlock.HALF, BlockHalf.BOTTOM)
								.with(StairsBlock.WATERLOGGED, true);
						setBlock(world, base, vanillaSeatStair);
					} else {
						// Чистая прозрачная вода глубиной 2 блока над мозаичным полом
						setBlock(world, base.down(1), Blocks.WATER.getDefaultState());
						setBlock(world, base, Blocks.WATER.getDefaultState());
					}
				}
			}
		}

		// Угловые фонари на бортиках джакузи
		setBlock(world, center.add(-4, 1, -4), Blocks.LANTERN.getDefaultState());
		setBlock(world, center.add(4, 1, -4), Blocks.LANTERN.getDefaultState());
		setBlock(world, center.add(-4, 1, 4), Blocks.LANTERN.getDefaultState());
		setBlock(world, center.add(4, 1, 4), Blocks.LANTERN.getDefaultState());

		// Размещаем мягкие Подушки из новых обновлений прямо на ванильных ступеньках внутри джакузи!
		CushionEntity.placeOnBlock(world, center.add(-3, 0, -1), DyeColor.WHITE, Direction.EAST);
		CushionEntity.placeOnBlock(world, center.add(-3, 0, 1), DyeColor.LIGHT_BLUE, Direction.EAST);
		CushionEntity.placeOnBlock(world, center.add(3, 0, -1), DyeColor.CYAN, Direction.WEST);
		CushionEntity.placeOnBlock(world, center.add(3, 0, 1), DyeColor.WHITE, Direction.WEST);
		CushionEntity.placeOnBlock(world, center.add(-1, 0, -3), DyeColor.LIGHT_BLUE, Direction.SOUTH);
		CushionEntity.placeOnBlock(world, center.add(1, 0, -3), DyeColor.CYAN, Direction.SOUTH);
		CushionEntity.placeOnBlock(world, center.add(-1, 0, 3), DyeColor.CYAN, Direction.NORTH);
		CushionEntity.placeOnBlock(world, center.add(1, 0, 3), DyeColor.LIGHT_BLUE, Direction.NORTH);
	}

	/**
	 * Знаменитая бело-синяя полосатая церковь (храм) на острове с золотым куполом:
	 * - Возвышенный подиум с парадной кварцевой лестницей.
	 * - Узнаваемый квадратный фасад в горизонтальную бело-синюю полоску с колоннами по углам.
	 * - Золочёный ступенчатый купол наверху и балюстрада.
	 * - Мраморно-лазуритовый пол внутри, скамьи с мягкими подушками, люстра и алтарь.
	 */
	private static void buildFamousIslandChurch(ServerWorld world, BlockPos center) {
		// 1. Каменный подиум (dy = +1) и парадная лестница со стороны центра острова (-Z)
		fillBox(world, center.add(-6, 0, -6), center.add(6, 1, 6), Blocks.QUARTZ_BRICKS.getDefaultState());
		for (int dx = -2; dx <= 2; dx++) {
			setBlock(
					world,
					center.add(dx, 1, -6),
					Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH)
			);
		}

		// 2. Узорчатый мраморно-лазуритовый пол внутри церкви (dy = +1)
		for (int rx = -4; rx <= 4; rx++) {
			for (int rz = -4; rz <= 4; rz++) {
				Block floorTile;
				if (Math.abs(rx) <= 1 && Math.abs(rz) <= 1) {
					floorTile = (rx == 0 && rz == 0) ? Blocks.GOLD_BLOCK : Blocks.CHISELED_QUARTZ_BLOCK;
				} else if (Math.abs(rx) == 4 || Math.abs(rz) == 4) {
					floorTile = Blocks.LAPIS_BLOCK;
				} else {
					floorTile = ((rx + rz) & 1) == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.QUARTZ_BRICKS;
				}
				setBlock(world, center.add(rx, 1, rz), floorTile.getDefaultState());
			}
		}

		// 3. Стены 11x11 (rx, rz = -5..+5, dy = +2..+7) с фирменными горизонтальными бело-синими полосами!
		for (int dy = 2; dy <= 7; dy++) {
			Block stripeBlock = (dy % 2 == 0) ? Blocks.WHITE_CONCRETE : Blocks.BLUE_CONCRETE;
			for (int rx = -5; rx <= 5; rx++) {
				for (int rz = -5; rz <= 5; rz++) {
					boolean edgeX = Math.abs(rx) == 5;
					boolean edgeZ = Math.abs(rz) == 5;
					BlockPos p = center.add(rx, dy, rz);

					if (edgeX && edgeZ) {
						// Угловые пилястры
						setBlock(world, p, Blocks.QUARTZ_PILLAR.getDefaultState());
					} else if (edgeX || edgeZ) {
						// Парадный арочный портал входа на северном фасаде (rz == -5, rx = -1..+1)
						if (rz == -5 && Math.abs(rx) <= 1 && dy <= 5) {
							if (Math.abs(rx) == 1 && dy <= 5) {
								setBlock(world, p, Blocks.GOLD_BLOCK.getDefaultState());
							} else if (dy == 5) {
								setBlock(world, p, Blocks.GOLD_BLOCK.getDefaultState());
							} else {
								setBlock(world, p, Blocks.AIR.getDefaultState());
							}
						} else if (dy >= 3 && dy <= 5 && ((edgeX && Math.abs(rz) <= 1) || (rz == 5 && Math.abs(rx) <= 1))) {
							// Высокие витражные окна на боковых и южном фасадах
							Block pane = (dy == 4) ? Blocks.WHITE_STAINED_GLASS_PANE : Blocks.BLUE_STAINED_GLASS_PANE;
							setBlock(world, p, pane.getDefaultState());
						} else {
							setBlock(world, p, stripeBlock.getDefaultState());
						}
					} else {
						setBlock(world, p, Blocks.AIR.getDefaultState());
					}
				}
			}
		}

		// 4. Плоская крыша с белоснежным карнизом-балюстрадой (dy = +8..+9) и ЗОЛОТЫМ КУПОЛОМ (dy = +9..+12)!
		fillBox(world, center.add(-5, 8, -5), center.add(5, 8, 5), Blocks.SMOOTH_QUARTZ.getDefaultState());
		setBlock(world, center.add(0, 8, 0), Blocks.SEA_LANTERN.getDefaultState());

		for (int rx = -5; rx <= 5; rx++) {
			for (int rz = -5; rz <= 5; rz++) {
				if (Math.abs(rx) == 5 || Math.abs(rz) == 5) {
					Block parapet = (Math.abs(rx) == 5 && Math.abs(rz) == 5) ? Blocks.GOLD_BLOCK : Blocks.SMOOTH_QUARTZ_SLAB;
					setBlock(world, center.add(rx, 9, rz), parapet.getDefaultState());
				}
			}
		}

		// Ступенчатый золотой купол в центре крыши
		for (int rx = -3; rx <= 3; rx++) {
			for (int rz = -3; rz <= 3; rz++) {
				if (rx * rx + rz * rz <= 10) {
					setBlock(world, center.add(rx, 9, rz), Blocks.GOLD_BLOCK.getDefaultState());
				}
				if (rx * rx + rz * rz <= 5) {
					setBlock(world, center.add(rx, 10, rz), Blocks.GOLD_BLOCK.getDefaultState());
				}
				if (rx * rx + rz * rz <= 1) {
					setBlock(world, center.add(rx, 11, rz), Blocks.GOLD_BLOCK.getDefaultState());
				}
			}
		}
		setBlock(world, center.add(0, 12, 0), Blocks.LIGHTNING_ROD.getDefaultState());

		// 5. Интерьер знаменитой церкви: ковровая дорожка, скамьи с Подушками, люстра и алтарь!
		for (int rz = -4; rz <= 2; rz++) {
			setBlock(world, center.add(0, 2, rz), Blocks.RED_CARPET.getDefaultState());
		}

		// Подвесные люстры под куполом
		setBlock(world, center.add(-2, 7, 0), Blocks.CHAIN.getDefaultState());
		setBlock(world, center.add(-2, 6, 0), Blocks.LANTERN.getDefaultState());
		setBlock(world, center.add(2, 7, 0), Blocks.CHAIN.getDefaultState());
		setBlock(world, center.add(2, 6, 0), Blocks.LANTERN.getDefaultState());

		// Скамьи из тёмного дуба (лицом к алтарю на +Z, спинка на NORTH? Нет — чтобы сидеть лицом к алтарю +Z, спинка сзади на NORTH!)
		for (int rz : new int[]{-2, 0}) {
			for (int rx : new int[]{-3, -2, 2, 3}) {
				BlockPos pewPos = center.add(rx, 2, rz);
				setBlock(world, pewPos, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH));
				if (Math.abs(rx) == 2) {
					DyeColor pewCushion = (rz == -2) ? DyeColor.BLUE : DyeColor.WHITE;
					CushionEntity.placeOnBlock(world, pewPos, pewCushion, Direction.SOUTH);
				}
			}
		}

		// Алтарь у южной стены (rz = +3..+4) + сундук с подушками и припасами
		fillBox(world, center.add(-2, 2, 3), center.add(2, 2, 4), Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
		setBlock(world, center.add(0, 2, 3), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState());
		setBlock(world, center.add(0, 3, 3), Blocks.LECTERN.getDefaultState());
		setBlock(world, center.add(-2, 3, 3), Blocks.END_ROD.getDefaultState());
		setBlock(world, center.add(2, 3, 3), Blocks.END_ROD.getDefaultState());
		setBlock(world, center.add(0, 3, 4), Blocks.BELL.getDefaultState());

		BlockPos churchChestPos = center.add(3, 2, 4);
		setBlock(world, churchChestPos, Blocks.CHEST.getDefaultState());
		if (world.getBlockEntity(churchChestPos) instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(ModItems.getCushionItem(DyeColor.WHITE), 4));
			chest.setStack(1, new ItemStack(ModItems.getCushionItem(DyeColor.LIGHT_BLUE), 4));
			chest.setStack(2, new ItemStack(ModItems.getCushionItem(DyeColor.RED), 4));
			chest.setStack(4, new ItemStack(ModItems.STRANGE_MEAT, 3));
			chest.setStack(6, new ItemStack(ModItems.LUCE_ROSARY, 1));
			chest.setStack(13, new ItemStack(Items.GOLD_INGOT, 6));
			chest.markDirty();
		}

		// Паломница Люче, её пёсик Сантино, Священник и Паладин Ордена в полосатом храме Острова Эго!
		LuceEntity churchLuce = ModEntities.LUCE_ENTITY.create(world);
		if (churchLuce != null) {
			BlockPos luceHome = center.add(0, 2, 1);
			churchLuce.refreshPositionAndAngles(luceHome.getX() + 0.5D, luceHome.getY(), luceHome.getZ() + 0.5D, 180.0F, 0.0F);
			churchLuce.setHomePos(luceHome);
			world.spawnEntity(churchLuce);
		}
		LuceWorldManager.spawnSantinoDog(world, center.add(-1, 2, 1));
		LuceWorldManager.spawnOrderMember(world, center.add(2, 2, 2), HolyOrderEntity.RANK_PRIEST, center.add(0, 2, 0), 18);
		LuceWorldManager.spawnOrderMember(world, center.add(2, 2, -6), HolyOrderEntity.RANK_CRUSADER, center.add(0, 2, 0), 22);
	}

	private static void populateKitchenChestWithStrangeMeat(ServerWorld world, BlockPos chestPos) {
		BlockEntity be = world.getBlockEntity(chestPos);
		if (be instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(ModItems.STRANGE_MEAT, 6));
			chest.setStack(2, new ItemStack(ModItems.STRANGE_MEAT, 4));
			chest.setStack(4, new ItemStack(Items.COOKED_BEEF, 5));
			chest.setStack(11, new ItemStack(ModItems.STRANGE_MEAT, 8));
			chest.setStack(13, new ItemStack(Items.HONEY_BOTTLE, 3));
			chest.setStack(15, new ItemStack(ModItems.STRANGE_MEAT, 5));
			chest.setStack(22, new ItemStack(Items.GOLDEN_CARROT, 4));
			chest.markDirty();
		}
	}

	private static void buildTropicalPalm(ServerWorld world, BlockPos base) {
		for (int dy = 0; dy < 6; dy++) {
			int lean = dy >= 3 ? 1 : 0;
			setBlock(world, base.add(lean, dy, 0), Blocks.JUNGLE_LOG.getDefaultState());
		}
		BlockPos top = base.add(1, 6, 0);
		setBlock(world, top.down(), Blocks.SHROOMLIGHT.getDefaultState());
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) + Math.abs(dz) <= 3) {
					setBlock(world, top.add(dx, 0, dz), Blocks.JUNGLE_LEAVES.getDefaultState());
				}
				if (Math.abs(dx) == 2 && dz == 0 || Math.abs(dz) == 2 && dx == 0) {
					setBlock(world, top.add(dx, -1, dz), Blocks.JUNGLE_LEAVES.getDefaultState());
				}
			}
		}
	}

	private static void spawnNpcAt(ServerWorld world, BlockPos pos, NpcRole role, String variant) {
		ModNpcEntity npc = ModEntities.MOD_NPC.create(world);
		if (npc == null) {
			return;
		}
		npc.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		npc.configureRole(role, variant);
		npc.setAnchorPos(pos);
		world.spawnEntity(npc);
	}

	private static void fillFoundation(ServerWorld world, BlockPos min, BlockPos max, BlockState state) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = min.getX(); x <= max.getX(); x++) {
			for (int y = min.getY(); y <= max.getY(); y++) {
				for (int z = min.getZ(); z <= max.getZ(); z++) {
					m.set(x, y, z);
					if (world.getBlockState(m).isReplaceable() || world.getBlockState(m).isAir()) {
						world.setBlockState(m, state, FLAGS);
					}
				}
			}
		}
	}

	private static void fillBox(ServerWorld world, BlockPos min, BlockPos max, BlockState state) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int x = min.getX(); x <= max.getX(); x++) {
			for (int y = min.getY(); y <= max.getY(); y++) {
				for (int z = min.getZ(); z <= max.getZ(); z++) {
					m.set(x, y, z);
					world.setBlockState(m, state, FLAGS);
				}
			}
		}
	}

	private static void setBlock(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, FLAGS);
	}
}
