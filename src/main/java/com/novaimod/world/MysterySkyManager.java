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
 * Управляет таинственной небесной локацией после выпивания коктейля «Пьяное забвение»:
 * - Платформа 100 блоков во все стороны высоко в небе (Y=272): сверху цветное стекло одного случайного цвета,
 *   снизу морские фонари + центральный помост из 7 блоков цветного стекла перед автоматом.
 * - Кастомные необычные деревья вокруг.
 * - Эффект Темнота + личное сообщение красным жирным текстом: готов ли ты рискнуть всем ради " шанса"?
 * - При попытке убежать от автомата телепортирует игрока обратно к автомату.
 * - Шансы автомата: 50% жетон на одно возрождение, 25% ничего, 25% смерть.
 * - После исхода вся небесная структура полностью исчезает, чтобы её нельзя было случайно найти в небе.
 */
public final class MysterySkyManager {
	private static final int PLATFORM_RADIUS = 100;
	private static final int SKY_BASE_Y = 272;
	private static final int FAST_BLOCK_FLAGS = Block.NOTIFY_CLIENTS | Block.FORCE_STATE;

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

	public static void enterMysteryArena(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		// Если игрок уже в небесной локации — не дублируем
		if (state.getActiveSkyArenas().containsKey(player.getUuid())) {
			return;
		}

		ServerWorld overworld = server.getOverworld();
		int slotIdx = state.nextSkyArenaIndex();
		// Разносим арены далеко друг от друга по координатам, чтобы игроки не пересекались
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

		// Телепортируем игрока прямо перед автоматом лицом на север (yaw = 180)
		player.teleport(overworld, centerX + 0.5D, baseY + 2.0D, centerZ + 1.5D, 180.0F, 0.0F);
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 20 * 600, 0, false, false, true));

		// Только для этого игрока красным жирным текстом:
		player.sendMessage(
				Text.literal("готов ли ты рискнуть всем ради \" шанса\"?")
						.formatted(Formatting.RED, Formatting.BOLD),
				false
		);
	}

	private static void buildSkyPlatform(ServerWorld world, ModPersistentState.SkyArenaSession s, Random random) {
		Block chosenGlass = STAINED_GLASS_COLORS[random.nextInt(STAINED_GLASS_COLORS.length)];
		BlockState glassState = chosenGlass.getDefaultState();
		BlockState lanternState = Blocks.SEA_LANTERN.getDefaultState();

		BlockPos.Mutable mutable = new BlockPos.Mutable();

		// Платформа 100 блоков во все стороны: снизу морские фонари, сверху цветное стекло одного рандомного цвета
		for (int dx = -s.radius; dx <= s.radius; dx++) {
			for (int dz = -s.radius; dz <= s.radius; dz++) {
				int x = s.centerX + dx;
				int z = s.centerZ + dz;
				mutable.set(x, s.baseY, z);
				world.setBlockState(mutable, lanternState, FAST_BLOCK_FLAGS);
				mutable.set(x, s.baseY + 1, z);
				world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
			}
		}

		// Дорожка из 7 блоков цветного стекла того же цвета к автомату в центре
		for (int i = -2; i <= 4; i++) {
			// По бокам акцентные колонны/дорожка из того же стекла
			mutable.set(s.centerX - 2, s.baseY + 2, s.centerZ - i);
			world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
			mutable.set(s.centerX + 2, s.baseY + 2, s.centerZ - i);
			world.setBlockState(mutable, glassState, FAST_BLOCK_FLAGS);
		}

		// Пьедестал и сам Автомат судьбы прямо перед игроком
		BlockPos machinePos = new BlockPos(s.machineX, s.machineY, s.machineZ);
		world.setBlockState(
				machinePos,
				ModBlocks.FATE_MACHINE.getDefaultState().with(FateMachineBlock.FACING, Direction.SOUTH),
				FAST_BLOCK_FLAGS
		);
		world.setBlockState(machinePos.up(), glassState, FAST_BLOCK_FLAGS);

		// Генерируем разные кастомные деревья на платформе (не слишком близко к автомату)
		int treeCount = 42;
		for (int i = 0; i < treeCount; i++) {
			int tx = s.centerX + random.nextBetween(-85, 85);
			int tz = s.centerZ + random.nextBetween(-85, 85);
			if (Math.abs(tx - s.centerX) < 8 && Math.abs(tz - s.centerZ) < 10) {
				continue;
			}
			buildCustomTree(world, tx, s.baseY + 2, tz, i % 4, glassState, random);
		}
	}

	private static void buildCustomTree(ServerWorld world, int x, int y, int z, int style, BlockState arenaGlass, Random random) {
		BlockState trunk;
		BlockState canopyPrimary;
		BlockState canopyAccent;

		switch (style) {
			case 0 -> {
				// Кристальное аметистово-обсидиановое плакучее дерево
				trunk = Blocks.CRYING_OBSIDIAN.getDefaultState();
				canopyPrimary = Blocks.AMETHYST_BLOCK.getDefaultState();
				canopyAccent = arenaGlass;
			}
			case 1 -> {
				// Искажённое бирюзовое дерево с морскими огнями
				trunk = Blocks.WARPED_HYPHAE.getDefaultState();
				canopyPrimary = Blocks.WARPED_WART_BLOCK.getDefaultState();
				canopyAccent = Blocks.SEA_LANTERN.getDefaultState();
			}
			case 2 -> {
				// Эфирное кварцево-вишнёвое дерево со стеклом
				trunk = Blocks.QUARTZ_PILLAR.getDefaultState();
				canopyPrimary = Blocks.CHERRY_LEAVES.getDefaultState();
				canopyAccent = arenaGlass;
			}
			default -> {
				// Багрово-чернитное мистическое дерево с грибосветом
				trunk = Blocks.BLACKSTONE.getDefaultState();
				canopyPrimary = Blocks.NETHER_WART_BLOCK.getDefaultState();
				canopyAccent = Blocks.SHROOMLIGHT.getDefaultState();
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

		// Необычная многоярусная крона со свисающими ветвями
		int topY = y + height;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int dist = Math.abs(dx) + Math.abs(dz);
				if (dist > 4) {
					continue;
				}
				m.set(curX + dx, topY, curZ + dz);
				world.setBlockState(m, (dist == 2 && random.nextInt(3) == 0) ? canopyAccent : canopyPrimary, FAST_BLOCK_FLAGS);

				if (dist <= 2) {
					m.set(curX + dx, topY + 1, curZ + dz);
					world.setBlockState(m, canopyPrimary, FAST_BLOCK_FLAGS);
				}
				// Свисающие лозы стекла / кристаллов по краям
				if (dist == 3 && random.nextBoolean()) {
					int drop = 1 + random.nextInt(3);
					for (int k = 1; k <= drop; k++) {
						m.set(curX + dx, topY - k, curZ + dz);
						world.setBlockState(m, canopyAccent, FAST_BLOCK_FLAGS);
					}
				}
			}
		}
		m.set(curX, topY + 2, curZ);
		world.setBlockState(m, canopyAccent, FAST_BLOCK_FLAGS);
	}

	/**
	 * Проверяет активные сессии каждый тик: не даёт убежать от автомата, поддерживает эффект Темноты.
	 */
	public static void tickSkyArenas(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
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

			// Поддерживаем эффект темноты
			if (!player.hasStatusEffect(StatusEffects.DARKNESS)) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 0, false, false, true));
			}

			// Если игрок пытается убежать от автомата или спрыгнуть — телепортирует обратно к автомату!
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

	/**
	 * Розыгрыш в автомате:
	 * 50% — Жетон на одно возрождение
	 * 25% — Ничего (просто возвращает)
	 * 25% — Смерть
	 */
	public static void rollFateMachine(ServerPlayerEntity player, BlockPos clickedPos) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.SkyArenaSession session = state.removeSkyArena(player.getUuid());
		ServerWorld overworld = server.getOverworld();

		double roll = overworld.getRandom().nextDouble(); // [0.0, 1.0)
		player.removeStatusEffect(StatusEffects.DARKNESS);

		// Возвращаем игрока на исходное место ПЕРЕД очисткой небесной платформы
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
			// Полностью уничтожаем структуру в небе, чтобы её нельзя было случайно найти!
			eraseSkyPlatform(overworld, session);
		} else {
			overworld.setBlockState(clickedPos, Blocks.AIR.getDefaultState(), FAST_BLOCK_FLAGS);
		}

		if (roll < 0.50D) {
			// 50% — Выигрыш Жетона возрождения!
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
		} else if (roll < 0.75D) {
			// 25% — Ничего, просто возвращает обратно
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1.0F, 0.8F);
			player.sendMessage(
					Text.literal("Пустота... Ты ничего не выиграл, но остался жив.").formatted(Formatting.GRAY, Formatting.ITALIC),
					false
			);
		} else {
			// 25% — Смерть!
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
				// Слой морских фонарей и стекла есть везде
				m.set(x, s.baseY, z);
				overworld.setBlockState(m, air, FAST_BLOCK_FLAGS);
				m.set(x, s.baseY + 1, z);
				overworld.setBlockState(m, air, FAST_BLOCK_FLAGS);

				// Выше убираем только непустые блоки (деревья, помост и автомат)
				for (int dy = 2; dy <= 16; dy++) {
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
