package com.novaimod.world;

import com.novaimod.block.TradeMachineBlock;
import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.registry.ModBlocks;
import com.novaimod.registry.ModEntities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Генерирует все заведения мода в мире:
 * 1) Микрокредитная организация (МФО) — сразу в начале игры рядом со спавном мира.
 * 2) Продуктовый магазин — на поверхности.
 * 3) Шахтёрская лавка — под землёй (с проверкой и расчисткой свободного места перед входом!).
 * 4) Лавовый курорт — в Аду (Незере).
 * 5) Казино — в разных биомах со своим архитектурным стилем, включая подземные пещеры
 *    (со столами «Монетка» и «Рулетка», а также проверкой свободного прохода перед входом под землёй!).
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
				tryGenerateNetherRegion(world, state, regionX, regionZ);
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

		if (state.getMfoPos() != null && state.getMfoPos().getSquaredDistance(baseX, state.getMfoPos().getY(), baseZ) < 48.0D * 48.0D) {
			baseX += 64;
		}
		if (!world.isChunkLoaded(baseX >> 4, baseZ >> 4)) {
			return;
		}

		state.markRegionGenerated(key);

		int roll = Math.floorMod(rx * 31 + rz * 17, 3);
		int surfY = findSurfaceY(world, baseX, baseZ);
		BlockPos surfacePos = new BlockPos(baseX, surfY, baseZ);

		if (roll == 0) {
			buildGroceryStore(world, surfacePos);
			state.recordStructure("grocery", "minecraft:overworld", surfacePos);
		} else {
			String biomeStyle = detectBiomeCasinoStyle(world, surfacePos);
			buildBiomeCasino(world, surfacePos, biomeStyle);
			state.recordStructure("casino_" + biomeStyle, "minecraft:overworld", surfacePos);
		}

		// Подземные структуры: подбираем точку рядом с пещерной полостью И проверяем/обеспечиваем свободный проход перед входом!
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

	/**
	 * Ищет подземную позицию так, чтобы перед входом (в сторону -Z) было свободное пещерное пространство для прохода.
	 */
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
				// Проверяем наличие твёрдого пола и свободного места перед предполагаемым входом (dz = -7..-10)
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

	/**
	 * Подсчитывает количество свободных воздушных блоков перед входом в подземную структуру.
	 */
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

	/**
	 * Проверяет и гарантирует свободное безопасное место перед входом подземной структуры,
	 * чтобы игрок всегда мог свободно подойти и войти внутрь (расчищает завалы, укрепляет пол над пропастями/лавой и ставит освещение).
	 */
	private static void ensureUndergroundEntrancePassage(
			ServerWorld world,
			BlockPos doorFloorCenter,
			Block floorBlock,
			Block supportWallBlock
	) {
		BlockPos.Mutable m = new BlockPos.Mutable();

		// 1. Всегда расчищаем просторную входную площадку перед дверью (5 блоков в ширину, 4 в высоту, 5 в глубину)
		for (int step = 1; step <= 12; step++) {
			int z = doorFloorCenter.getZ() - step;
			if (!world.isChunkLoaded(doorFloorCenter.getX() >> 4, z >> 4)) {
				break;
			}

			// Проверяем, вышли ли уже в достаточно просторную пещеру после обязательного предбанника (step > 5)
			if (step > 5 && countOpenEntranceSpace(world, new BlockPos(doorFloorCenter.getX(), doorFloorCenter.getY(), z)) >= 22) {
				break;
			}

			for (int dx = -2; dx <= 2; dx++) {
				int x = doorFloorCenter.getX() + dx;
				// Надёжный пол под ногами (чтобы перед входом не было обрыва или лавы)
				m.set(x, doorFloorCenter.getY(), z);
				if (world.getBlockState(m).isAir() || world.getBlockState(m).isReplaceable()
						|| world.getBlockState(m).isOf(Blocks.LAVA) || world.getBlockState(m).isOf(Blocks.WATER)) {
					world.setBlockState(m, floorBlock.getDefaultState(), FLAGS);
				}

				// Проход высотой 4 блока (свободное место перед входом)
				for (int dy = 1; dy <= 4; dy++) {
					m.set(x, doorFloorCenter.getY() + dy, z);
					world.setBlockState(m, Blocks.AIR.getDefaultState(), FLAGS);
				}

				// Защита потолка и боковых стен от вытекания лавы/воды в проход
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

	private static void tryGenerateNetherRegion(ServerWorld nether, ModPersistentState state, int rx, int rz) {
		String key = "nether:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}
		Random rng = Random.create(((long) rx * 918273645L) ^ ((long) rz * 192837465L) ^ nether.getSeed());
		int bx = rx * 224 + 64 + rng.nextInt(96);
		int bz = rz * 224 + 64 + rng.nextInt(96);
		if (!nether.isChunkLoaded(bx >> 4, bz >> 4)) {
			return;
		}
		state.markRegionGenerated(key);

		int by = findNetherFloorY(nether, bx, bz);
		BlockPos resortPos = new BlockPos(bx, by, bz);
		buildLavaResort(nether, resortPos);
		state.recordStructure("lava_resort", "minecraft:the_nether", resortPos);
	}

	private static int findSurfaceY(ServerWorld world, int x, int z) {
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		return Math.max(world.getSeaLevel(), Math.min(200, y));
	}

	private static int findNetherFloorY(ServerWorld nether, int x, int z) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int y = 33; y <= 95; y++) {
			m.set(x, y - 1, z);
			BlockState below = nether.getBlockState(m);
			m.set(x, y, z);
			BlockState at = nether.getBlockState(m);
			m.set(x, y + 1, z);
			BlockState above = nether.getBlockState(m);
			if (!below.isAir() && !below.isOf(Blocks.BEDROCK) && at.isAir() && above.isAir()) {
				return y;
			}
		}
		return 34;
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
	// 3. ШАХТЁРСКАЯ ЛАВКА (под землёй + проверка и расчистка входа!)
	// =========================================================================
	public static void buildMinerShop(ServerWorld world, BlockPos origin) {
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

		// Проверяем и расчищаем свободный проход перед входом в подземную лавку!
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
	// 4. ЛАВОВЫЙ КУРОРТ В АДУ
	// =========================================================================
	public static void buildLavaResort(ServerWorld nether, BlockPos origin) {
		fillBox(nether, origin.add(-7, 1, -7), origin.add(7, 8, 7), Blocks.AIR.getDefaultState());
		fillBox(nether, origin.add(-7, -2, -7), origin.add(7, -1, 7), Blocks.SANDSTONE.getDefaultState());

		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				BlockPos p = origin.add(dx, 0, dz);
				if (dz >= 3 && Math.abs(dx) <= 5) {
					setBlock(nether, p, Blocks.LAVA.getDefaultState());
				} else {
					setBlock(nether, p, ((dx + dz) % 3 == 0 ? Blocks.RED_SAND : Blocks.SAND).getDefaultState());
				}
			}
		}

		setBlock(nether, origin.add(-2, 1, 0), Blocks.SANDSTONE_SLAB.getDefaultState());
		setBlock(nether, origin.add(-2, 1, -1), Blocks.SANDSTONE_STAIRS.getDefaultState());
		setBlock(nether, origin.add(2, 1, 0), Blocks.SANDSTONE_SLAB.getDefaultState());
		setBlock(nether, origin.add(2, 1, -1), Blocks.SANDSTONE_STAIRS.getDefaultState());

		buildBeachUmbrella(nether, origin.add(0, 1, -1), Blocks.RED_WOOL, Blocks.WHITE_WOOL);
		buildBeachUmbrella(nether, origin.add(-5, 1, -2), Blocks.YELLOW_WOOL, Blocks.ORANGE_WOOL);

		for (int dy = 1; dy <= 4; dy++) {
			setBlock(nether, origin.add(5, dy, -3), Blocks.CRIMSON_STEM.getDefaultState());
		}
		fillBox(nether, origin.add(4, 5, -4), origin.add(6, 5, -2), Blocks.WARPED_WART_BLOCK.getDefaultState());
		setBlock(nether, origin.add(5, 4, -2), Blocks.SHROOMLIGHT.getDefaultState());

		setBlock(
				nether,
				origin.add(-5, 1, -6),
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
	// 5. БИОМНОЕ КАЗИНО (включая пещеры!) + Столы Монетки и Рулетки + Бармен
	// =========================================================================
	public static void buildBiomeCasino(ServerWorld world, BlockPos origin, String style) {
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

		// Пол казино
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				Block f = (Math.abs(dx) <= 2 || Math.abs(dz) <= 1) ? floorAccent : floorPrimary;
				setBlock(world, origin.add(dx, 0, dz), f.getDefaultState());
			}
		}

		// Стены и потолок
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

		// Для подземного казино обеспечиваем свободный безопасный проход перед входом!
		if ("cave".equals(style)) {
			setBlock(world, origin.add(2, 0, -7), floorPrimary.getDefaultState());
			ensureUndergroundEntrancePassage(world, origin.add(0, 0, -7), floorPrimary, wallBlock);
		}

		// Неоновая вывеска над входом казино
		setBlock(world, origin.add(-2, 4, -6), Blocks.SEA_LANTERN.getDefaultState());
		setBlock(world, origin.add(0, 4, -6), Blocks.GOLD_BLOCK.getDefaultState());
		setBlock(world, origin.add(2, 4, -6), Blocks.SEA_LANTERN.getDefaultState());

		// ДВА ИГРОВЫХ СТОЛА КАЗИНО в зале:
		// 1) Стол «Монетка» (слева)
		setBlock(world, origin.add(-3, 1, -2), ModBlocks.COINFLIP_TABLE.getDefaultState());
		setBlock(world, origin.add(-4, 1, -2), Blocks.RED_CARPET.getDefaultState());
		setBlock(world, origin.add(-2, 1, -2), Blocks.RED_CARPET.getDefaultState());

		// 2) Полноценный стол «Рулетка» (справа)
		setBlock(world, origin.add(3, 1, -2), ModBlocks.ROULETTE_TABLE.getDefaultState());
		setBlock(world, origin.add(4, 1, -2), Blocks.GREEN_CARPET.getDefaultState());
		setBlock(world, origin.add(2, 1, -2), Blocks.GREEN_CARPET.getDefaultState());

		// Барная стойка и полки с напитками
		fillBox(world, origin.add(-3, 1, 2), origin.add(3, 1, 2), barCounterBlock.getDefaultState());
		setBlock(world, origin.add(-2, 2, 4), Blocks.BREWING_STAND.getDefaultState());
		setBlock(world, origin.add(2, 2, 4), Blocks.BREWING_STAND.getDefaultState());
		fillBox(world, origin.add(-3, 1, 4), origin.add(3, 1, 4), pillarBlock.getDefaultState());

		// Торговый аппарат на входе казино
		setBlock(
				world,
				origin.add(2, 1, -7),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH)
		);

		// Бармен за стойкой
		spawnNpcAt(world, origin.add(0, 1, 3), NpcRole.CASINO_BARTENDER, style);
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
