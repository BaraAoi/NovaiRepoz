package com.novaimod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.novaimod.block.FateMachineBlock;
import com.novaimod.data.ModPersistentState;
import com.novaimod.registry.ModBlocks;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Управляет секретной локацией после выпивания коктейля «Пьяное забвение»:
 * - Сначала накладывается эффект Темноты, и только затем происходит перенос.
 * - Пол выполнен как глубокое «туманное ничего»: на самом дне скрыт сплошной источник света,
 *   а над ним уложено множество чередующихся слоёв цветного стекла с воздушными и оттеночными прослойками,
 *   благодаря чему каждая грань стекла рендерится и создаёт густую светящуюся дымку без видимого дна.
 * - Кастомные деревья со стеклянными кронами стоят плотными кольцами вблизи центра.
 */
public final class MysterySkyManager {
	// Компактный радиус 22 (вместо огромного 72 = 240 000 блоков!): под эффектом DARKNESS края всё так же скрыты во тьме, но спавнится вообще БЕЗ ЛАГОВ!
	private static final int PLATFORM_RADIUS = 22;
	private static final int SKY_BASE_Y = 272;
	/** Компактная глубина многослойного туманного пола (в 25 раз легче для рендера и освещения при такой же густой дымке!). */
	private static final int FOG_DEPTH = 4;
	private static final int FAST_BLOCK_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final int DARKNESS_PRE_TELEPORT_TICKS = 45;

	private static final Block[] STAINED_GLASS_COLORS = new Block[]{
			Blocks.WHITE_STAINED_GLASS,
			Blocks.ORANGE_STAINED_GLASS,
			Blocks.MAGENTA_STAINED_GLASS,
			Blocks.LIGHT_BLUE_STAINED_GLASS,
			Blocks.YELLOW_STAINED_GLASS,
			Blocks.LIME_STAINED_GLASS,
			Blocks.PINK_STAINED_GLASS,
			Blocks.GRAY_STAINED_GLASS,
			Blocks.LIGHT_GRAY_STAINED_GLASS,
			Blocks.CYAN_STAINED_GLASS,
			Blocks.PURPLE_STAINED_GLASS,
			Blocks.BLUE_STAINED_GLASS,
			Blocks.BROWN_STAINED_GLASS,
			Blocks.GREEN_STAINED_GLASS,
			Blocks.RED_STAINED_GLASS,
			Blocks.BLACK_STAINED_GLASS
	};

	private MysterySkyManager() {
	}

	public static void beginDrunkenOblivionSequence(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		if (state.getActiveSkyArenas().containsKey(player.getUuid())) {
			return;
		}
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		if (acc.pendingOblivionTicks > 0) {
			return;
		}

		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 20 * 600, 0, false, false, true));
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.7F, 0.65F);

		acc.pendingOblivionTicks = DARKNESS_PRE_TELEPORT_TICKS;
		state.markDirty();
	}

	public static void enterMysteryArena(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		if (state.getActiveSkyArenas().containsKey(player.getUuid())) {
			return;
		}

		ServerWorld overworld = server.getOverworld();
		int slotIdx = state.nextSkyArenaIndex();
		int centerX = ((int) Math.floor(player.getX()) & ~15) + (slotIdx % 7) * 512;
		int centerZ = ((int) Math.floor(player.getZ()) & ~15) + ((slotIdx / 7) % 7) * 512;
		int baseY = SKY_BASE_Y;

		ModPersistentState.SkyArenaSession session = new ModPersistentState.SkyArenaSession();
		session.playerUuid = player.getUuid();
		session.centerX = centerX;
		session.baseY = baseY;
		session.centerZ = centerZ;
		session.radius = PLATFORM_RADIUS;
		session.machineX = centerX;
		session.machineY = baseY + 2;
		session.machineZ = centerZ - 4;
		session.returnDim = player.getServerWorld().getRegistryKey().getValue().toString();
		session.returnX = player.getX();
		session.returnY = player.getY();
		session.returnZ = player.getZ();
		session.returnYaw = player.getYaw();
		session.returnPitch = player.getPitch();

		buildSkyPlatform(overworld, session, overworld.getRandom());
		state.putSkyArena(session);

		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 20 * 600, 0, false, false, true));
		player.teleport(overworld, centerX + 0.5D, baseY + 2.0D, centerZ + 1.5D, 180.0F, 0.0F);

		player.sendMessage(
				Text.literal("готов ли ты рискнуть всем ради \" шанса\"?")
						.formatted(Formatting.RED, Formatting.BOLD),
				false
		);
	}

	/**
	 * Подбирает второй близкий дымчатый оттенок стекла для чередования слоёв тумана:
	 * в Minecraft одинаковые соседние блоки стекла скрывают внутреннюю грань, а при чередовании
	 * двух оттенков стекла и воздушных прослоек рендерится КАЖДЫЙ слой, создавая густое «туманное ничего»!
	 */
	private static Block pickFogHazeLayer(Block primaryGlass) {
		if (primaryGlass == Blocks.LIGHT_GRAY_STAINED_GLASS) {
			return Blocks.WHITE_STAINED_GLASS;
		}
		if (primaryGlass == Blocks.BLACK_STAINED_GLASS || primaryGlass == Blocks.GRAY_STAINED_GLASS) {
			return Blocks.LIGHT_GRAY_STAINED_GLASS;
		}
		return Blocks.LIGHT_GRAY_STAINED_GLASS;
	}

	private static void buildSkyPlatform(ServerWorld world, ModPersistentState.SkyArenaSession s, Random random) {
		Block chosenGlass = STAINED_GLASS_COLORS[random.nextInt(STAINED_GLASS_COLORS.length)];
		Block hazeGlass = pickFogHazeLayer(chosenGlass);

		BlockState glassState = chosenGlass.getDefaultState();
		BlockState hazeState = hazeGlass.getDefaultState();
		BlockState lanternState = Blocks.SEA_LANTERN.getDefaultState();
		BlockState airState = Blocks.AIR.getDefaultState();

		BlockPos.Mutable mutable = new BlockPos.Mutable();

		// 1. Оптимизированный пол «Туманное ничего» (в 25 раз легче, 0 лагов при спавне!):
		// На дне (baseY - 4) — разреженная подсветка морскими фонарями (каждая 2-я клетка, в 2 раза меньше пересчётов света!),
		// а над ней чередующиеся слои цветного и дымчатого стекла с воздушной прослойкой до (baseY + 1).
		for (int dx = -s.radius; dx <= s.radius; dx++) {
			for (int dz = -s.radius; dz <= s.radius; dz++) {
				int x = s.centerX + dx;
				int z = s.centerZ + dz;

				if (((dx + dz) & 1) == 0) {
					mutable.set(x, s.baseY - FOG_DEPTH, z);
					world.setBlockState(mutable, lanternState, FAST_BLOCK_FLAGS);
				}

				mutable.set(x, s.baseY - 3, z);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);

				mutable.set(x, s.baseY - 2, z);
				world.setBlockState(mutable, hazeState, FAST_BLOCK_FLAGS);

				mutable.set(x, s.baseY - 1, z);
				world.setBlockState(mutable, airState, FAST_BLOCK_FLAGS);

				mutable.set(x, s.baseY, z);
				world.setBlockState(mutable, hazeState, FAST_BLOCK_FLAGS);

				// Верхний ходовой слой цветного стекла
				mutable.set(x, s.baseY + 1, z);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
			}
		}

		// 2. Концентрические витражные кольца вокруг площади (в пределах компактного радиуса 22)
		int[] glassRings = new int[]{6, 11, 17};
		for (int ringR : glassRings) {
			for (int dx = -ringR; dx <= ringR; dx++) {
				for (int dz = -ringR; dz <= ringR; dz++) {
					int distSq = dx * dx + dz * dz;
					if (distSq >= (ringR - 1) * (ringR - 1) && distSq <= ringR * ringR) {
						if (Math.abs(dx) <= 2 && dz >= -5 && dz <= 4) {
							continue;
						}
						mutable.set(s.centerX + dx, s.baseY + 2, s.centerZ + dz);
						world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
						if ((dx + dz) % 2 == 0) {
							mutable.set(s.centerX + dx, s.baseY + 8, s.centerZ + dz);
							world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
						}
					}
				}
			}
		}

		// 3. Витражный коридор из 7 арок цветного стекла к Автомату судьбы
		for (int i = -2; i <= 4; i++) {
			int z = s.centerZ - i;
			for (int dy = 2; dy <= 5; dy++) {
				mutable.set(s.centerX - 3, s.baseY + dy, z);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
				mutable.set(s.centerX + 3, s.baseY + dy, z);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
			}
			for (int dx = -2; dx <= 2; dx++) {
				mutable.set(s.centerX + dx, s.baseY + 6, z);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
			}
			mutable.set(s.centerX - 2, s.baseY + 2, z);
			world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
			mutable.set(s.centerX + 2, s.baseY + 2, z);
			world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
		}

		// 4. Высокие витражные обелиски из цветного стекла вокруг центра
		int[][] crystalPillars = new int[][]{
				{-5, -6}, {5, -6}, {-7, 0}, {7, 0}, {-5, 6}, {5, 6},
				{0, -9}, {-10, -4}, {10, -4}, {-10, 4}, {10, 4}
		};
		for (int[] p : crystalPillars) {
			int px = s.centerX + p[0];
			int pz = s.centerZ + p[1];
			int pHeight = 7 + random.nextInt(4);
			for (int dy = 2; dy <= pHeight; dy++) {
				mutable.set(px, s.baseY + dy, pz);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
				if (dy == 4 || dy == pHeight - 1) {
					mutable.set(px + 1, s.baseY + dy, pz);
					world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
					mutable.set(px - 1, s.baseY + dy, pz);
					world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
					mutable.set(px, s.baseY + dy, pz + 1);
					world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
					mutable.set(px, s.baseY + dy, pz - 1);
					world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
				}
			}
			mutable.set(px, s.baseY + pHeight + 1, pz);
			world.setBlockState(mutable, lanternState, FAST_BLOCK_FLAGS);
			mutable.set(px, s.baseY + pHeight + 2, pz);
			world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
		}

		// 5. Автомат судьбы в обрамлении витражного ореола
		BlockPos machinePos = new BlockPos(s.machineX, s.machineY, s.machineZ);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 0; dy <= 4; dy++) {
				if (Math.abs(dx) == 2 || dy >= 3) {
					mutable.set(s.machineX + dx, s.machineY + dy, s.machineZ - 1);
					world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
				}
			}
		}
		world.setBlockState(
				machinePos,
				ModBlocks.FATE_MACHINE.getDefaultState().with(FateMachineBlock.FACING, Direction.SOUTH),
				FAST_BLOCK_FLAGS
		);
		world.setBlockState(machinePos.up(), glassState, FAST_BLOCK_FLAGS);

		// 6. Компактные кольца деревьев вокруг центра (без перегрузки чанков!)
		int innerTrees = 10;
		for (int i = 0; i < innerTrees; i++) {
			double angle = (Math.PI * 2.0D * i) / innerTrees + (random.nextDouble() - 0.5D) * 0.18D;
			int dist = 8 + random.nextInt(5);
			int tx = s.centerX + (int) Math.round(Math.cos(angle) * dist);
			int tz = s.centerZ + (int) Math.round(Math.sin(angle) * dist);
			if (Math.abs(tx - s.centerX) <= 4 && tz >= s.centerZ - 7 && tz <= s.centerZ + 5) {
				continue;
			}
			buildCustomTree(world, tx, s.baseY + 2, tz, i % 4, glassState, random);
		}

		int outerTrees = 8;
		for (int i = 0; i < outerTrees; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			int dist = 13 + random.nextInt(6);
			int tx = s.centerX + (int) Math.round(Math.cos(angle) * dist);
			int tz = s.centerZ + (int) Math.round(Math.sin(angle) * dist);
			if (Math.abs(tx - s.centerX) <= 4 && tz >= s.centerZ - 7 && tz <= s.centerZ + 5) {
				continue;
			}
			buildCustomTree(world, tx, s.baseY + 2, tz, i % 4, glassState, random);
		}
	}

	private static void buildCustomTree(ServerWorld world, int x, int y, int z, int style, BlockState arenaGlass, Random random) {
		BlockState trunk;
		BlockState canopyPrimary;
		BlockState canopyAccent = arenaGlass;

		switch (style) {
			case 0 -> {
				trunk = Blocks.CRYING_OBSIDIAN.getDefaultState();
				canopyPrimary = Blocks.AMETHYST_BLOCK.getDefaultState();
			}
			case 1 -> {
				trunk = Blocks.WARPED_HYPHAE.getDefaultState();
				canopyPrimary = Blocks.WARPED_WART_BLOCK.getDefaultState();
			}
			case 2 -> {
				trunk = Blocks.QUARTZ_PILLAR.getDefaultState();
				canopyPrimary = Blocks.CHERRY_LEAVES.getDefaultState();
			}
			default -> {
				trunk = Blocks.BLACKSTONE.getDefaultState();
				canopyPrimary = Blocks.NETHER_WART_BLOCK.getDefaultState();
			}
		}

		int height = 6 + random.nextInt(5);
		int leanX = random.nextBetween(-1, 1);
		int leanZ = random.nextBetween(-1, 1);
		int curX = x;
		int curZ = z;

		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dy = 0; dy < height; dy++) {
			if (dy == height / 2) {
				curX += leanX;
				curZ += leanZ;
			}
			m.set(curX, y + dy, curZ);
			world.setBlockState(m, trunk, FAST_BLOCK_FLAGS);
		}

		int topY = y + height;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int dist = Math.abs(dx) + Math.abs(dz);
				if (dist > 4) {
					continue;
				}
				m.set(curX + dx, topY, curZ + dz);
				boolean useGlass = (dist >= 2 && ((dx + dz) & 1) == 0) || random.nextInt(3) == 0;
				world.setBlockState(m, useGlass ? canopyAccent : canopyPrimary, FAST_BLOCK_FLAGS);

				if (dist <= 2) {
					m.set(curX + dx, topY + 1, curZ + dz);
					world.setBlockState(m, dist == 2 ? canopyAccent : canopyPrimary, FAST_BLOCK_FLAGS);
				}
				if (dist >= 3 && random.nextInt(3) != 0) {
					int drop = 2 + random.nextInt(3);
					for (int k = 1; k <= drop; k++) {
						m.set(curX + dx, topY - k, curZ + dz);
						world.setBlockState(m, canopyAccent, FAST_BLOCK_FLAGS);
					}
				}
			}
		}
		m.set(curX, topY + 2, curZ);
		world.setBlockState(m, Blocks.SEA_LANTERN.getDefaultState(), FAST_BLOCK_FLAGS);
		m.set(curX, topY + 3, curZ);
		world.setBlockState(m, canopyAccent, FAST_BLOCK_FLAGS);
	}

	public static void tickSkyArenas(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);

		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			ModPersistentState.PlayerFinanceData acc = state.getAccount(p.getUuid());
			if (acc.pendingOblivionTicks > 0) {
				if (!p.isAlive() || p.isSpectator()) {
					acc.pendingOblivionTicks = 0;
					state.markDirty();
					continue;
				}
				if (!p.hasStatusEffect(StatusEffects.DARKNESS)) {
					p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 20 * 600, 0, false, false, true));
				}
				acc.pendingOblivionTicks--;
				state.markDirty();
				if (acc.pendingOblivionTicks <= 0) {
					enterMysteryArena(p);
				}
			}
		}

		if (state.getActiveSkyArenas().isEmpty()) {
			return;
		}

		List<UUID> toCleanup = new ArrayList<>();
		ServerWorld overworld = server.getOverworld();

		for (ModPersistentState.SkyArenaSession s : state.getActiveSkyArenas().values()) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(s.playerUuid);
			if (player == null) {
				continue;
			}
			if (!player.isAlive()) {
				toCleanup.add(s.playerUuid);
				continue;
			}

			if (!player.hasStatusEffect(StatusEffects.DARKNESS)) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 0, false, false, true));
			}

			double dx = player.getX() - (s.machineX + 0.5D);
			double dz = player.getZ() - (s.machineZ + 0.5D);
			double horizDistSq = dx * dx + dz * dz;
			boolean wrongWorld = player.getServerWorld() != overworld;
			boolean fellBelow = player.getY() < s.baseY + 1.0D;

			if (wrongWorld || fellBelow || horizDistSq > 16.0D * 16.0D) {
				player.teleport(overworld, s.centerX + 0.5D, s.baseY + 2.0D, s.centerZ + 1.5D, 180.0F, 0.0F);
				player.setVelocity(0.0D, 0.0D, 0.0D);
				overworld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, 0.7F);
				player.sendMessage(
						Text.literal("От автомата не убежать... Сделай свой выбор.").formatted(Formatting.DARK_RED, Formatting.BOLD),
						true
				);
			}
		}

		for (UUID uuid : toCleanup) {
			ModPersistentState.SkyArenaSession removed = state.removeSkyArena(uuid);
			if (removed != null) {
				eraseSkyPlatform(overworld, removed);
			}
		}
	}

	public static void rollFateMachine(ServerPlayerEntity player, BlockPos clickedPos) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.SkyArenaSession session = state.removeSkyArena(player.getUuid());
		ServerWorld overworld = server.getOverworld();

		ModPersistentState.MachineOddsConfig odds = state.getOddsConfig();
		int wToken = Math.max(0, odds.fateTokenChance);
		int wNothing = Math.max(0, odds.fateNothingChance);
		int wDeath = Math.max(0, odds.fateDeathChance);
		int total = wToken + wNothing + wDeath;

		int outcome;
		if (total <= 0) {
			outcome = 1;
		} else {
			int pick = overworld.getRandom().nextInt(total);
			if (pick < wToken) {
				outcome = 0;
			} else if (pick < wToken + wNothing) {
				outcome = 1;
			} else {
				outcome = 2;
			}
		}

		player.removeStatusEffect(StatusEffects.DARKNESS);

		if (session != null) {
			ServerWorld returnWorld = resolveWorld(server, session.returnDim);
			player.teleport(
					returnWorld,
					session.returnX,
					session.returnY,
					session.returnZ,
					session.returnYaw,
					session.returnPitch
			);
			eraseSkyPlatform(overworld, session);
		} else {
			overworld.setBlockState(clickedPos, Blocks.AIR.getDefaultState(), FAST_BLOCK_FLAGS);
		}

		if (outcome == 0) {
			ItemStack token = new ItemStack(ModItems.REVIVAL_TOKEN, 1);
			if (!player.getInventory().insertStack(token)) {
				player.dropItem(token, false);
			}
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
			player.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0D, player.getZ(), 45, 0.4D, 0.7D, 0.4D, 0.2D);
			player.sendMessage(
					Text.literal("★ Судьба улыбнулась тебе! Ты выиграл Жетон возрождения!").formatted(Formatting.GOLD, Formatting.BOLD),
					false
			);
		} else if (outcome == 1) {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1.0F, 0.8F);
			player.sendMessage(
					Text.literal("Пустота... Ты ничего не выиграл, но остался жив.").formatted(Formatting.GRAY, Formatting.ITALIC),
					false
			);
		} else {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.9F, 0.6F);
			player.sendMessage(
					Text.literal("☠ Ставка не сыграла. Твоя жизнь принадлежит казино.").formatted(Formatting.DARK_RED, Formatting.BOLD),
					false
			);
			player.damage(player.getServerWorld().getDamageSources().outOfWorld(), Float.MAX_VALUE);
			if (player.isAlive()) {
				player.kill();
			}
		}
	}

	public static void eraseSkyPlatform(ServerWorld overworld, ModPersistentState.SkyArenaSession s) {
		BlockState air = Blocks.AIR.getDefaultState();
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -s.radius; dx <= s.radius; dx++) {
			for (int dz = -s.radius; dz <= s.radius; dz++) {
				int x = s.centerX + dx;
				int z = s.centerZ + dz;
				// Очищаем все слои туманного пола (включая старые сессии с глубиной -9)
				for (int dy = -9; dy <= 1; dy++) {
					m.set(x, s.baseY + dy, z);
					if (!overworld.getBlockState(m).isAir()) {
						overworld.setBlockState(m, air, FAST_BLOCK_FLAGS);
					}
				}
				// И всю надземную часть (арки, обелиски, деревья, автомат)
				for (int dy = 2; dy <= 22; dy++) {
					m.set(x, s.baseY + dy, z);
					if (!overworld.getBlockState(m).isAir()) {
						overworld.setBlockState(m, air, FAST_BLOCK_FLAGS);
					}
				}
			}
		}
	}

	public static ServerWorld resolveWorld(MinecraftServer server, String dimId) {
		if (dimId != null && !dimId.isEmpty()) {
			Identifier id = Identifier.tryParse(dimId);
			if (id != null) {
				RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id);
				ServerWorld found = server.getWorld(key);
				if (found != null) {
					return found;
				}
			}
		}
		return server.getOverworld();
	}
}
