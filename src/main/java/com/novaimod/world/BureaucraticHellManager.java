package com.novaimod.world;

import java.util.List;

import com.novaimod.NovaiMod;
import com.novaimod.block.TradeMachineBlock;
import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.CatgirlEntity;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.LuceEntity;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.entity.WhiteCapEntity;
import com.novaimod.registry.ModBlocks;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Новое измерение v4.0.0: «БЮРОКРАТИЧЕСКИЙ АД» (novaimod:bureaucratic_hell).
 * Колоссальное 7-уровневое закрытое министерское здание (49x49 блоков на каждом этаже):
 *  - Уровень -1 (Y=20): Подвал с тюрячкой (действующее СИЗО ОМОНа, Карцер-Обезьянник Бригады армян,
 *    камеры Дебилов-неплательщиков МФО, спецприёмник разумных Кошкодевочек без Справки №38-МЯУ + буфет охраны)
 *  - Уровень  1 (Y=32): Приёмная (очередь-змейка, окошки №1–4, направления, филиал МФО, Торговый аппарат, кофепоинт и Парадный Выход)
 *  - Уровень  2 (Y=44): Офисы (душный коворкинг уставших клерков и стажёрок-кошкодевочек + роскошные кабинеты Важных Шишек + кофепоинт)
 *  - Уровень  3 (Y=56): Склад с бумагами (лабиринт архивных стеллажей, сундуки улик и БЕССМЕРТНЫЙ Ревизор Архива + каморка грузчиков)
 *  - Уровень  4 (Y=68): Общественный Кафетерий (главный комбинат пищевой бюрократии: талоны на вилку, Шеф-повар со Странным мясом, Бармен, Сталин и Володя за обедом)
 *  - Уровень  5 (Y=80): Заброшенный этаж (запечатанный отдел XVIII века, куда столетиями никто не ходил: паутина, скелеты просителей, реликвии и забытая Кошкодевочка)
 *  - Уровень  6 (Y=92): Кабинет Босса (пентхаус Верховного Председателя Бюрократии с секретаршей-кошкодевочкой, казино, джакузи и битвой с Боссом!)
 */
public final class BureaucraticHellManager {
	public static final RegistryKey<World> BUREAUCRATIC_HELL_WORLD = RegistryKey.of(
			RegistryKeys.WORLD,
			Identifier.of(NovaiMod.MOD_ID, "bureaucratic_hell")
	);

	public static final int HALF_SIZE = 24;
	public static final int FLOOR_MINUS_1_Y = 20;
	public static final int FLOOR_1_Y = 32;
	public static final int FLOOR_2_Y = 44;
	public static final int FLOOR_3_Y = 56;
	public static final int FLOOR_4_Y = 68;
	public static final int FLOOR_5_Y = 80;
	public static final int FLOOR_6_Y = 92;

	private static final int[] ALL_FLOOR_YS = new int[]{
			FLOOR_MINUS_1_Y, FLOOR_1_Y, FLOOR_2_Y, FLOOR_3_Y, FLOOR_4_Y, FLOOR_5_Y, FLOOR_6_Y
	};
	private static final String[] FLOOR_NAMES = new String[]{
			"Уровень -1: Подвал и Тюрячка",
			"Уровень 1: Приёмная Министерств",
			"Уровень 2: Офисы Шишек и Коворкинг",
			"Уровень 3: Архивный Склад Бумаг",
			"Уровень 4: Общественный Кафетерий",
			"Уровень 5: Заброшенный Этаж (1724 г.)",
			"Уровень 6: Кабинет Верховного Босса"
	};
	private static final String[] FLOOR_SHORT_SIGNS = new String[]{
			"[-1] ТЮРЯЧКА",
			"[1] ПРИЁМНАЯ",
			"[2] ОФИСЫ",
			"[3] СКЛАД",
			"[4] СТОЛОВАЯ",
			"[5] ЗАБРОШЕН",
			"[6] БОСС"
	};

	/** Координаты действующей камеры СИЗО и Карцера-Обезьянника на Уровне -1 Бюрократического Ада! */
	public static final BlockPos SIZO_CELL_POS = new BlockPos(-14, FLOOR_MINUS_1_Y + 1, -8);
	public static final BlockPos MONKEY_CAGE_POS = new BlockPos(14, FLOOR_MINUS_1_Y + 1, -8);

	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private BureaucraticHellManager() {
	}

	public static ServerWorld getBureaucraticWorld(MinecraftServer server) {
		ServerWorld bw = server.getWorld(BUREAUCRATIC_HELL_WORLD);
		return bw != null ? bw : server.getOverworld();
	}

	public static boolean isInBureaucraticHell(World world) {
		return world.getRegistryKey().equals(BUREAUCRATIC_HELL_WORLD);
	}

	/**
	 * Проверяет и выдаёт «Ключ Бюрократии» за легальные заслуги (например, крупный баланс >= 25 000 ₽ — повестка Налоговой!).
	 */
	public static void tickBureaucraticChecks(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
			if (!acc.taxKeyIssued && acc.balance >= 25_000L) {
				acc.taxKeyIssued = true;
				acc.bureaucracyKeyEarned = true;
				state.markDirty();
				giveBureaucracyKey(
						player,
						"🏛 [Налоговая Инспекция]: На вашем счёте зафиксировано 25 000+ ₽! Вам вручён легальный «Ключ Бюрократии» — нажмите им ПКМ по ЛЮБОЙ ДВЕРИ, чтобы явиться в Приёмную Бюрократического Ада!"
				);
			}
		}
	}

	public static void giveBureaucracyKey(ServerPlayerEntity player, String message) {
		ItemStack key = new ItemStack(ModItems.BUREAUCRACY_KEY, 1);
		if (!player.getInventory().insertStack(key)) {
			player.dropItem(key, false);
		}
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.15F);
		player.sendMessage(Text.literal(message).formatted(Formatting.GOLD, Formatting.BOLD), false);
	}

	public static boolean hasDiplomaticSealImmunity(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			if (player.getInventory().getStack(i).isOf(ModItems.BUREAUCRACY_SEAL)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Обработка ПКМ по блокам:
	 * 1) ПКМ «Ключом Бюрократии» (или «Генеральной Печатью») по ЛЮБОЙ ДВЕРИ в мире -> вход в Бюрократический Ад!
	 * 2) Внутри Бюрократического Ада: ПКМ по Парадной Двери Выхода на Уровне 1 (или по любой двери с Ключом в руке) -> возврат домой!
	 * 3) Внутри Бюрократического Ада: ПКМ по любой из 7 табличек Лифта (X = -3..3, Z = -23..-24) -> поездка на нужный этаж!
	 */
	public static boolean handleBlockInteract(ServerPlayerEntity player, Hand hand, BlockPos pos) {
		if (hand != Hand.MAIN_HAND) {
			return false;
		}
		ServerWorld world = player.getServerWorld();
		BlockState state = world.getBlockState(pos);
		ItemStack held = player.getMainHandStack();
		boolean holdingKeyOrSeal = held.isOf(ModItems.BUREAUCRACY_KEY) || held.isOf(ModItems.BUREAUCRACY_SEAL);

		// 1. Снаружи Бюрократического Ада: клик Ключом по ЛЮБОЙ ДВЕРИ ведёт в Бюрократический Ад!
		if (!isInBureaucraticHell(world)) {
			if (holdingKeyOrSeal && state.getBlock() instanceof DoorBlock) {
				if (DetentionManager.isPlayerRestrictedFromBreaking(player)) {
					player.sendMessage(Text.literal("Сначала отбудьте наказание!").formatted(Formatting.RED), true);
					return true;
				}
				enterBureaucraticHellViaDoor(player);
				return true;
			}
			return false;
		}

		// --- ВНУТРИ БЮРОКРАТИЧЕСКОГО АДА ---
		ModPersistentState pState = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = pState.getAccount(player.getUuid());
		if (acc.sizoRemainingTicks > 0 || acc.inMonkeyCage) {
			return false;
		}

		// 2. Панель Лифта Министерства на северной стене (Z = -23..-24, X = -3..+3)
		if ((pos.getZ() == -23 || pos.getZ() == -24) && pos.getX() >= -3 && pos.getX() <= 3) {
			for (int fIdx = 0; fIdx < ALL_FLOOR_YS.length; fIdx++) {
				int fy = ALL_FLOOR_YS[fIdx];
				if (pos.getY() >= fy + 1 && pos.getY() <= fy + 4) {
					int targetFloorIndex = pos.getX() + 3; // -3 -> 0 (-1 этаж), ..., +3 -> 6 (6 этаж)
					rideElevatorToFloor(player, targetFloorIndex);
					return true;
				}
			}
		}

		// 3. Парадная Дверь Выхода на Уровне 1 (южная стена Z >= 22, X ∈ [-2..2], Y ∈ [33..36]) ИЛИ любая дверь с Ключом в руке
		boolean isMainExitDoor = (state.getBlock() instanceof DoorBlock)
				&& pos.getZ() >= 22
				&& Math.abs(pos.getX()) <= 2
				&& pos.getY() >= FLOOR_1_Y + 1
				&& pos.getY() <= FLOOR_1_Y + 4;
		if (isMainExitDoor || (holdingKeyOrSeal && state.getBlock() instanceof DoorBlock)) {
			exitBureaucraticHellToWorld(player);
			return true;
		}

		return false;
	}

	public static void enterBureaucraticHellViaDoor(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerWorld fromWorld = player.getServerWorld();
		ServerWorld bureauWorld = ensureComplexBuilt(server);

		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		acc.hasPreBureauReturn = true;
		acc.preBureauDim = fromWorld.getRegistryKey().getValue().toString();
		acc.preBureauX = player.getX();
		acc.preBureauY = player.getY();
		acc.preBureauZ = player.getZ();
		acc.preBureauYaw = player.getYaw();
		state.markDirty();

		fromWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.PLAYERS, 1.0F, 0.75F);
		fromWorld.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getBodyY(0.5D), player.getZ(), 28, 0.4D, 0.8D, 0.4D, 0.15D);

		double destX = 0.5D;
		double destY = FLOOR_1_Y + 1.0D;
		double destZ = 20.5D;
		player.teleport(bureauWorld, destX, destY, destZ, 180.0F, 0.0F);
		teleportFollowingCatgirls(player, fromWorld, bureauWorld, destX, destY, destZ);

		bureauWorld.playSound(null, BlockPos.ofFloored(destX, destY, destZ), SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.PLAYERS, 1.0F, 0.8F);
		player.sendMessage(
				Text.literal("🏛 Дверь открылась в измерение «БЮРОКРАТИЧЕСКИЙ АД»! (Уровень 1 — Приёмная. Лифт — прямо по коридору на севере, Выход — дверь позади вас!)")
						.formatted(Formatting.GOLD, Formatting.BOLD),
				false
		);
	}

	public static void exitBureaucraticHellToWorld(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerWorld fromWorld = player.getServerWorld();
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());

		ServerWorld destWorld = server.getOverworld();
		double dx = destWorld.getSpawnPos().getX() + 0.5D;
		double dy = destWorld.getSpawnPos().getY() + 1.0D;
		double dz = destWorld.getSpawnPos().getZ() + 0.5D;
		float yaw = player.getYaw();

		if (acc.hasPreBureauReturn) {
			ServerWorld resolved = MysterySkyManager.resolveWorld(server, acc.preBureauDim);
			if (resolved != null && !isInBureaucraticHell(resolved)) {
				destWorld = resolved;
				dx = acc.preBureauX;
				dy = acc.preBureauY;
				dz = acc.preBureauZ;
				yaw = acc.preBureauYaw;
			}
		}

		player.teleport(destWorld, dx, dy, dz, yaw, 0.0F);
		teleportFollowingCatgirls(player, fromWorld, destWorld, dx, dy, dz);
		destWorld.playSound(null, BlockPos.ofFloored(dx, dy, dz), SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.PLAYERS, 1.0F, 1.0F);
		player.sendMessage(
				Text.literal("🚪 Вы вышли через дверь обратно в обычный мир!").formatted(Formatting.GREEN, Formatting.BOLD),
				true
		);
	}

	private static void rideElevatorToFloor(ServerPlayerEntity player, int floorIndex) {
		if (floorIndex < 0 || floorIndex >= ALL_FLOOR_YS.length) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		int targetY = ALL_FLOOR_YS[floorIndex];
		double dx = 0.5D;
		double dy = targetY + 1.0D;
		double dz = -19.5D;

		player.teleport(world, dx, dy, dz, 0.0F, 0.0F);
		teleportFollowingCatgirls(player, world, world, dx, dy, dz);

		if (targetY == FLOOR_5_Y) {
			world.playSound(null, BlockPos.ofFloored(dx, dy, dz), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, 0.55F);
			player.sendMessage(
					Text.literal("🕸 Лифт со скрежетом остановился... " + FLOOR_NAMES[floorIndex] + " (воздух полон вековой пыли!)")
							.formatted(Formatting.GRAY, Formatting.ITALIC),
					true
			);
		} else {
			world.playSound(null, BlockPos.ofFloored(dx, dy, dz), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 1.1F, 1.25F);
			player.sendMessage(
					Text.literal("🔔 ДИНЬ! " + FLOOR_NAMES[floorIndex]).formatted(Formatting.YELLOW, Formatting.BOLD),
					true
			);
		}
	}

	/**
	 * Переносит всех спасённых Кошкодевочек в режиме «Следовать за хозяином» вместе с игроком между этажами и через дверь домой!
	 */
	private static void teleportFollowingCatgirls(
			ServerPlayerEntity owner,
			ServerWorld fromWorld,
			ServerWorld toWorld,
			double destX,
			double destY,
			double destZ
	) {
		Box search = new Box(owner.getX() - 28, owner.getY() - 12, owner.getZ() - 28, owner.getX() + 28, owner.getY() + 12, owner.getZ() + 28);
		List<CatgirlEntity> girls = fromWorld.getEntitiesByClass(
				CatgirlEntity.class,
				search,
				c -> c.isAlive() && c.isRescued() && c.isOwnedBy(owner) && c.getBehaviorMode() == CatgirlEntity.MODE_FOLLOW
		);
		for (CatgirlEntity girl : girls) {
			if (fromWorld == toWorld) {
				girl.refreshPositionAndAngles(destX + 1.0D, destY, destZ, owner.getYaw(), 0.0F);
			} else {
				girl.teleport(toWorld, destX + 1.0D, destY, destZ, java.util.Set.of(), owner.getYaw(), 0.0F);
			}
		}

		List<LuceEntity> luces = fromWorld.getEntitiesByClass(
				LuceEntity.class,
				search,
				l -> l.isAlive() && !l.isWrathMode() && l.isFriendOf(owner) && l.getCompanionMode() == LuceEntity.MODE_FOLLOW
		);
		for (LuceEntity luce : luces) {
			if (fromWorld == toWorld) {
				luce.refreshPositionAndAngles(destX - 1.0D, destY, destZ, owner.getYaw(), 0.0F);
			} else {
				luce.teleport(toWorld, destX - 1.0D, destY, destZ, java.util.Set.of(), owner.getYaw(), 0.0F);
			}
		}
	}

	/**
	 * Защищает несущие стены, полы и потолки Министерства от случайного разрушения в выживании
	 * (разрешая ломать паутину на 5-м этаже и открывать любые калитки, двери, сундуки, бочки и автоматы!).
	 */
	public static boolean isProtectedMinistryBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
		if (!isInBureaucraticHell(world) || player.isCreative()) {
			return false;
		}
		BlockState st = world.getBlockState(pos);
		if (st.isOf(Blocks.COBWEB)) {
			return false; // паутину на заброшенном 5-м этаже можно рубить мечом!
		}
		return Math.abs(pos.getX()) <= HALF_SIZE + 2 && Math.abs(pos.getZ()) <= HALF_SIZE + 2 && pos.getY() >= 16 && pos.getY() <= 108;
	}

	public static ServerWorld ensureComplexBuilt(MinecraftServer server) {
		ServerWorld world = getBureaucraticWorld(server);
		ModPersistentState state = ModPersistentState.get(server);
		if (!state.isBureaucracyBuilt()) {
			// Предпрогружаем чанки центра комплекса [-2..2]
			for (int cx = -2; cx <= 2; cx++) {
				for (int cz = -2; cz <= 2; cz++) {
					world.getChunk(cx, cz);
				}
			}
			buildEntireMinistryComplex(world);
			state.setBureaucracyBuilt(true);
		}
		return world;
	}

	public static void buildEntireMinistryComplex(ServerWorld world) {
		// Строим оболочку всех 7 этажей, лифтовой узел и маршевую лестницу
		for (int i = 0; i < ALL_FLOOR_YS.length; i++) {
			int fy = ALL_FLOOR_YS[i];
			int ceilingH = (fy == FLOOR_6_Y) ? 10 : 8;
			buildFloorShell(world, fy, ceilingH);
		}

		// Детальная застройка каждого из 7 уровней + заселение NPC и Кошкодевочек!
		buildLevelMinusOnePrisons(world, FLOOR_MINUS_1_Y);
		buildLevelOneReception(world, FLOOR_1_Y);
		buildLevelTwoOffices(world, FLOOR_2_Y);
		buildLevelThreeWarehouse(world, FLOOR_3_Y);
		buildLevelFourCafeteria(world, FLOOR_4_Y);
		buildLevelFiveAbandoned(world, FLOOR_5_Y);
		buildLevelSixBossOffice(world, FLOOR_6_Y);

		// Сквозная западная лестница и панель Лифта на каждом этаже
		for (int i = 0; i < ALL_FLOOR_YS.length; i++) {
			int fy = ALL_FLOOR_YS[i];
			buildElevatorLobbyOnFloor(world, fy, i);
			if (i < ALL_FLOOR_YS.length - 1) {
				buildStaircaseBetween(world, fy, ALL_FLOOR_YS[i + 1], ALL_FLOOR_YS[i + 1] == FLOOR_5_Y);
			}
		}
	}

	private static void buildFloorShell(ServerWorld world, int fy, int height) {
		BlockState floorBlock;
		BlockState wallBlock;
		BlockState ceilBlock = Blocks.SMOOTH_STONE.getDefaultState();
		BlockState lightBlock = Blocks.OCHRE_FROGLIGHT.getDefaultState();

		if (fy == FLOOR_MINUS_1_Y) {
			floorBlock = Blocks.POLISHED_DEEPSLATE.getDefaultState();
			wallBlock = Blocks.STONE_BRICKS.getDefaultState();
			lightBlock = Blocks.SEA_LANTERN.getDefaultState();
		} else if (fy == FLOOR_1_Y) {
			floorBlock = Blocks.POLISHED_DIORITE.getDefaultState();
			wallBlock = Blocks.SMOOTH_SANDSTONE.getDefaultState();
		} else if (fy == FLOOR_2_Y) {
			floorBlock = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
			wallBlock = Blocks.WHITE_TERRACOTTA.getDefaultState();
		} else if (fy == FLOOR_3_Y) {
			floorBlock = Blocks.TUFF_BRICKS.getDefaultState();
			wallBlock = Blocks.POLISHED_ANDESITE.getDefaultState();
			lightBlock = Blocks.REDSTONE_LAMP.getDefaultState();
		} else if (fy == FLOOR_4_Y) {
			floorBlock = Blocks.QUARTZ_BRICKS.getDefaultState();
			wallBlock = Blocks.YELLOW_TERRACOTTA.getDefaultState();
			lightBlock = Blocks.PEARLESCENT_FROGLIGHT.getDefaultState();
		} else if (fy == FLOOR_5_Y) {
			floorBlock = Blocks.CRACKED_STONE_BRICKS.getDefaultState();
			wallBlock = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
			ceilBlock = Blocks.DEEPSLATE_TILES.getDefaultState();
		} else {
			floorBlock = Blocks.POLISHED_BLACKSTONE.getDefaultState();
			wallBlock = Blocks.DARK_OAK_PLANKS.getDefaultState();
			ceilBlock = Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
			lightBlock = Blocks.SHROOMLIGHT.getDefaultState();
		}

		BlockState air = Blocks.AIR.getDefaultState();
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int x = -HALF_SIZE; x <= HALF_SIZE; x++) {
			for (int z = -HALF_SIZE; z <= HALF_SIZE; z++) {
				// Подложка под полом, чтобы нигде не было просветов между этажами
				for (int sub = -3; sub <= -1; sub++) {
					m.set(x, fy + sub, z);
					world.setBlockState(m, Blocks.DEEPSLATE_BRICKS.getDefaultState(), FLAGS);
				}

				// Шахматный узор в Приёмной и Кафетерии
				BlockState curFloor = floorBlock;
				if (fy == FLOOR_1_Y && ((x + z) & 1) == 0) {
					curFloor = Blocks.POLISHED_ANDESITE.getDefaultState();
				} else if (fy == FLOOR_4_Y && ((x + z) & 1) == 0) {
					curFloor = Blocks.SMOOTH_QUARTZ.getDefaultState();
				} else if (fy == FLOOR_5_Y && ((x * 7 + z * 13) % 5 == 0)) {
					curFloor = Blocks.MOSSY_COBBLESTONE.getDefaultState();
				}
				m.set(x, fy, z);
				world.setBlockState(m, curFloor, FLAGS);

				// Внутренний объём и стены
				boolean outerWall = Math.abs(x) == HALF_SIZE || Math.abs(z) == HALF_SIZE;
				for (int dy = 1; dy < height; dy++) {
					m.set(x, fy + dy, z);
					world.setBlockState(m, outerWall ? wallBlock : air, FLAGS);
				}

				// Потолок со встроенными офисными лампами дневного света
				m.set(x, fy + height, z);
				boolean lampSpot = !outerWall && (x % 6 == 0) && (z % 6 == 0);
				if (fy == FLOOR_3_Y) {
					// На складе редкое тусклое освещение морскими фонарями через раз
					lampSpot = !outerWall && (x % 10 == 0) && (z % 10 == 0);
					if (lampSpot) lightBlock = Blocks.SEA_LANTERN.getDefaultState();
				} else if (fy == FLOOR_5_Y) {
					// На заброшенном этаже почти все лампы давно перегорели!
					lampSpot = false;
				}
				world.setBlockState(m, lampSpot ? lightBlock : ceilBlock, FLAGS);
			}
		}
	}

	private static void buildElevatorLobbyOnFloor(ServerWorld world, int fy, int currentIdx) {
		// Северная ниша лифта (X = -4..+4, Z = -24)
		for (int x = -4; x <= 4; x++) {
			for (int dy = 1; dy <= 4; dy++) {
				BlockPos wallPos = new BlockPos(x, fy + dy, -24);
				boolean border = Math.abs(x) == 4 || dy == 4;
				world.setBlockState(wallPos, border ? Blocks.GOLD_BLOCK.getDefaultState() : Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), FLAGS);
			}
		}
		world.setBlockState(new BlockPos(0, fy + 4, -23), Blocks.SEA_LANTERN.getDefaultState(), FLAGS);

		// 7 интерактивных табличек-кнопок этажей (X = -3..+3, Y = fy + 2, Z = -23)
		for (int i = 0; i < ALL_FLOOR_YS.length; i++) {
			int bx = -3 + i;
			BlockPos backing = new BlockPos(bx, fy + 2, -24);
			world.setBlockState(
					backing,
					i == currentIdx ? Blocks.EMERALD_BLOCK.getDefaultState() : Blocks.IRON_BLOCK.getDefaultState(),
					FLAGS
			);
			placeWaxedWallSign(
					world,
					new BlockPos(bx, fy + 2, -23),
					Direction.SOUTH,
					"ЛИФТ (ПКМ)",
					FLOOR_SHORT_SIGNS[i],
					i == currentIdx ? ">>> ВЫ ТУТ <<<" : "Нажмите ПКМ",
					""
			);
		}
	}

	private static void buildStaircaseBetween(ServerWorld world, int lowerY, int upperY, boolean cobwebAbandoned) {
		int steps = upperY - lowerY; // 12 ступеней вдоль западной стены (X = -22..-21, Z от -10 до +1)
		BlockState stair = Blocks.STONE_BRICK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH);
		BlockState air = Blocks.AIR.getDefaultState();
		for (int s = 0; s < steps; s++) {
			int z = -10 + s;
			int y = lowerY + 1 + s;
			for (int x = -22; x <= -21; x++) {
				// Расчищаем проём в межэтажном перекрытии над ступенями
				for (int head = 1; head <= 4; head++) {
					world.setBlockState(new BlockPos(x, y + head, z), air, FLAGS);
				}
				world.setBlockState(new BlockPos(x, y, z), stair, FLAGS);
				world.setBlockState(new BlockPos(x, y - 1, z), Blocks.STONE_BRICKS.getDefaultState(), FLAGS);
			}
			// Перила лестницы
			world.setBlockState(new BlockPos(-20, y + 1, z), Blocks.IRON_BARS.getDefaultState(), FLAGS);
		}
		if (cobwebAbandoned) {
			// Вход на 5-й этаж затянут вековой паутиной!
			for (int x = -22; x <= -21; x++) {
				world.setBlockState(new BlockPos(x, upperY + 1, 2), Blocks.COBWEB.getDefaultState(), FLAGS);
				world.setBlockState(new BlockPos(x, upperY + 2, 2), Blocks.COBWEB.getDefaultState(), FLAGS);
			}
			placeWaxedWallSign(world, new BlockPos(-20, upperY + 2, 2), Direction.EAST, "УРОВЕНЬ 5", "ЗАПЕЧАТАНО", "ПО УКАЗУ 1724 г.", "НЕ ВХОДИТЬ!");
		}
	}

	// =========================================================================
	// УРОВЕНЬ -1: ПОДВАЛ С ТЮРЯЧКОЙ (СИЗО, ОБЕЗЬЯННИК, ДЕБИЛЫ-ДОЛЖНИКИ И КОШКОДЕВОЧКИ)
	// =========================================================================
	private static void buildLevelMinusOnePrisons(ServerWorld world, int fy) {
		// 1. Действующее СИЗО ОМОНа (X = -18..-10, Z = -12..-4, центр SIZO_CELL_POS = (-14, fy+1, -8))
		buildLockedPrisonCell(world, -14, fy, -8, false, "КАМЕРА №1: СИЗО", "Строгий режим", "Срок: 10 минут", "Работает ОМОН");

		// 2. Действующий Сырой Карцер «Обезьянник» Бригады армян (X = 10..18, Z = -12..-4, центр MONKEY_CAGE_POS = (14, fy+1, -8))
		buildMonkeyCellInBasement(world, 14, fy, -8);

		// 3. Камеры с Дебилами-неплательщиками долгов МФО (западное и южное крыло)
		buildLockedPrisonCell(world, -14, fy, 4, false, "КАМЕРА №2", "Должник Толик", "Долг: 48 МЛРД ₽", "(20% в день!)");
		spawnNpcAt(world, -14.5D, fy + 1, 4.5D, NpcRole.DEBTOR_IDIOT, "1");

		buildLockedPrisonCell(world, -14, fy, 15, false, "КАМЕРА №3", "Должник Серёга", "Поставил кредит", "на ЗЕРО в казино");
		spawnNpcAt(world, -14.5D, fy + 1, 15.5D, NpcRole.DEBTOR_IDIOT, "2");

		buildLockedPrisonCell(world, 0, fy, 17, false, "КАМЕРА №4", "Бедолага Васян", "Купил золотую", "мотыгу в кредит");
		spawnNpcAt(world, 0.5D, fy + 1, 17.5D, NpcRole.DEBTOR_IDIOT, "3");

		// 4. Спецприёмник Кошкодевочек без Справки №38-МЯУ (С ОТКРЫВАЮЩИМИСЯ КАЛИТКАМИ — можно спасти и забрать домой!)
		buildOpenableCatgirlCell(world, 14, fy, 4, 0, "ИЗОЛЯТОР №5", "Объект: ШИРО", "Нет Справки 38-МЯУ", "(Открой калитку!)");
		buildOpenableCatgirlCell(world, 14, fy, 15, 1, "ИЗОЛЯТОР №6", "Объект: КУРО", "Нет ИНН на хвост", "(Открой калитку!)");

		// 5. Пост дежурного ОМОНа, Коллектора и Мини-буфет охраны
		spawnNpcAt(world, -4.5D, fy + 1, -7.5D, NpcRole.OMON, "bureau_guard");
		spawnNpcAt(world, 4.5D, fy + 1, -7.5D, NpcRole.COLLECTOR, "bureau_guard");
		buildMiniCoffeeCorner(world, -2, fy, -15, "Буфет Конвоя");
	}

	private static void buildLockedPrisonCell(ServerWorld world, int cx, int fy, int cz, boolean eastBars, String l1, String l2, String l3, String l4) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				boolean wall = Math.abs(dx) == 4 || Math.abs(dz) == 4;
				for (int dy = 1; dy <= 5; dy++) {
					BlockPos p = new BlockPos(cx + dx, fy + dy, cz + dz);
					if (dy == 5) {
						world.setBlockState(p, (dx == 0 && dz == 0) ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState(), FLAGS);
					} else if (wall) {
						boolean bars = (cx < 0 && dx == 4 && Math.abs(dz) <= 2 && dy <= 3)
								|| (cx == 0 && dz == -4 && Math.abs(dx) <= 2 && dy <= 3);
						world.setBlockState(p, bars ? Blocks.IRON_BARS.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState(), FLAGS);
					} else {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
					}
				}
			}
		}
		// Нары и котёл
		world.setBlockState(new BlockPos(cx - 2, fy + 1, cz + 2), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx - 2, fy + 1, cz + 1), Blocks.SMOOTH_STONE_SLAB.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx - 3, fy + 1, cz - 3), Blocks.CAULDRON.getDefaultState(), FLAGS);
		if (cx < 0) {
			placeWaxedWallSign(world, new BlockPos(cx + 5, fy + 3, cz), Direction.EAST, l1, l2, l3, l4);
		} else {
			placeWaxedWallSign(world, new BlockPos(cx, fy + 3, cz - 5), Direction.NORTH, l1, l2, l3, l4);
		}
	}

	private static void buildMonkeyCellInBasement(ServerWorld world, int cx, int fy, int cz) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				boolean wall = Math.abs(dx) == 4 || Math.abs(dz) == 4;
				world.setBlockState(new BlockPos(cx + dx, fy, cz + dz), ((dx + dz) & 1) == 0 ? Blocks.MUD.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState(), FLAGS);
				for (int dy = 1; dy <= 5; dy++) {
					BlockPos p = new BlockPos(cx + dx, fy + dy, cz + dz);
					if (dy == 5) {
						world.setBlockState(p, Blocks.MOSSY_STONE_BRICKS.getDefaultState(), FLAGS);
					} else if (wall) {
						boolean bars = (dx == -4 && Math.abs(dz) <= 2 && dy <= 3);
						world.setBlockState(p, bars ? Blocks.IRON_BARS.getDefaultState() : Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
					} else {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
					}
				}
			}
		}
		world.setBlockState(new BlockPos(cx + 2, fy + 3, cz), Blocks.REDSTONE_TORCH.getDefaultState(), FLAGS);
		placeWaxedWallSign(world, new BlockPos(cx - 5, fy + 3, cz), Direction.WEST, "КАМЕРА «ОБЕЗЬЯННИК»", "Воспитательный", "отдел Бригады", "");
	}

	private static void buildOpenableCatgirlCell(ServerWorld world, int cx, int fy, int cz, int variantId, String l1, String l2, String l3, String l4) {
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
				for (int dy = 1; dy <= 4; dy++) {
					BlockPos p = new BlockPos(cx + dx, fy + dy, cz + dz);
					if (dy == 4) {
						world.setBlockState(p, Blocks.IRON_BLOCK.getDefaultState(), FLAGS);
					} else if (wall) {
						if (dx == -3 && dz == 0 && (dy == 1 || dy == 2)) {
							world.setBlockState(p, Blocks.DARK_OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.WEST), FLAGS);
						} else if (dx == -3 || Math.abs(dz) == 3) {
							world.setBlockState(p, Blocks.IRON_BARS.getDefaultState(), FLAGS);
						} else {
							world.setBlockState(p, Blocks.POLISHED_ANDESITE.getDefaultState(), FLAGS);
						}
					} else {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
					}
				}
			}
		}
		world.setBlockState(new BlockPos(cx + 1, fy + 1, cz + 1), Blocks.WHITE_CARPET.getDefaultState(), FLAGS);
		placeWaxedWallSign(world, new BlockPos(cx - 4, fy + 3, cz), Direction.WEST, l1, l2, l3, l4);

		CatgirlEntity cat = ModEntities.CATGIRL_ENTITY.create(world);
		if (cat != null) {
			cat.refreshPositionAndAngles(cx + 0.5D, fy + 1.0D, cz + 0.5D, 90.0F, 0.0F);
			cat.configureVariant(variantId, false);
			world.spawnEntity(cat);
		}
	}

	// =========================================================================
	// УРОВЕНЬ 1: ПРИЁМНАЯ (РЕГИСТРАТУРА, НАПРАВЛЕНИЯ, ФИЛИАЛ МФО, КОФЕПОИНТ И ВЫХОД)
	// =========================================================================
	private static void buildLevelOneReception(ServerWorld world, int fy) {
		// 1. Парадная Дверь Выхода на южной стене (X = 0, Z = 23, золотой портал Приёмной)
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = 1; dy <= 4; dy++) {
				world.setBlockState(new BlockPos(dx, fy + dy, 23), Blocks.GOLD_BLOCK.getDefaultState(), FLAGS);
			}
		}
		placeDoor(world, new BlockPos(0, fy + 1, 23), Blocks.DARK_OAK_DOOR, Direction.NORTH);
		placeWaxedWallSign(world, new BlockPos(0, fy + 3, 22), Direction.NORTH, "ПАРАДНЫЙ ВЫХОД", "Нажмите ПКМ", "по двери, чтобы", "вернуться в мир!");

		// Красная ковровая дорожка от Парадного Выхода к Стойке Регистратуры и Лифту
		for (int z = -19; z <= 22; z++) {
			for (int x = -1; x <= 1; x++) {
				if (world.getBlockState(new BlockPos(x, fy + 1, z)).isAir()) {
					world.setBlockState(new BlockPos(x, fy + 1, z), Blocks.RED_CARPET.getDefaultState(), FLAGS);
				}
			}
		}

		// 2. Окошки Регистратуры и Выдачи Направлений (восточная сторона X = 12, Z = -12..+8)
		for (int z = -12; z <= 8; z++) {
			world.setBlockState(new BlockPos(12, fy + 1, z), Blocks.POLISHED_ANDESITE.getDefaultState(), FLAGS);
			boolean windowHole = (z == -9 || z == -3 || z == 3);
			world.setBlockState(new BlockPos(12, fy + 2, z), windowHole ? Blocks.GLASS_PANE.getDefaultState() : Blocks.QUARTZ_BRICKS.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(12, fy + 3, z), Blocks.QUARTZ_BRICKS.getDefaultState(), FLAGS);
		}
		// Проход за стойку для персонала
		world.setBlockState(new BlockPos(12, fy + 1, 9), Blocks.DARK_OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.WEST), FLAGS);
		world.setBlockState(new BlockPos(12, fy + 2, 9), Blocks.AIR.getDefaultState(), FLAGS);

		placeWaxedWallSign(world, new BlockPos(11, fy + 3, -9), Direction.WEST, "ОКОШКО №1", "Выдача направлений", "и талончиков", "(ПКМ по инспектору)");
		placeWaxedWallSign(world, new BlockPos(11, fy + 3, -3), Direction.WEST, "ОКОШКО №2", "Жалобы на Окошко 1", "Срок ответа:", "900 рабочих лет");
		placeWaxedWallSign(world, new BlockPos(11, fy + 3, 3), Direction.WEST, "ОКОШКО №3", "ЗАКРЫТО НА ОБЕД", "с 09:00 до 18:00", "и с 18:00 до 09:00");
		world.setBlockState(new BlockPos(12, fy + 2, 3), Blocks.IRON_BARS.getDefaultState(), FLAGS);

		spawnNpcAt(world, 14.5D, fy + 1, -8.5D, NpcRole.BUREAU_REGISTRAR, "1");
		spawnNpcAt(world, 14.5D, fy + 1, -2.5D, NpcRole.BUREAU_REGISTRAR, "2");

		// 3. Змейка очереди из заборчиков + лавочки ожидания с мягкими Подушками
		for (int z = -10; z <= 4; z += 4) {
			for (int x = 4; x <= 9; x++) {
				world.setBlockState(new BlockPos(x, fy + 1, z), Blocks.OAK_FENCE.getDefaultState(), FLAGS);
			}
		}
		spawnNpcAt(world, 6.5D, fy + 1, -8.0D, NpcRole.DEBTOR_IDIOT, "queue");
		spawnNpcAt(world, 7.5D, fy + 1, -4.0D, NpcRole.TIRED_CLERK, "");

		// Спасённая Кошкодевочка-курьер Приёмной (можно забрать домой или поболтать!)
		CatgirlEntity courierCat = ModEntities.CATGIRL_ENTITY.create(world);
		if (courierCat != null) {
			courierCat.refreshPositionAndAngles(3.5D, fy + 1.0D, 6.5D, 180.0F, 0.0F);
			courierCat.configureVariant(2, false);
			world.spawnEntity(courierCat);
		}

		// Лавочки ожидания с Подушками
		for (int z = 8; z <= 14; z += 2) {
			BlockPos bench = new BlockPos(6, fy + 1, z);
			world.setBlockState(bench, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST), FLAGS);
			spawnCushionOn(world, bench, DyeColor.RED);
		}

		// 4. Клиентское отделение МФО + Торговый аппарат + Ремонтная станция (западная сторона X = -16..-8)
		for (int z = -6; z <= 6; z++) {
			world.setBlockState(new BlockPos(-12, fy + 1, z), Blocks.GOLD_BLOCK.getDefaultState(), FLAGS);
		}
		placeWaxedWallSign(world, new BlockPos(-11, fy + 2, 0), Direction.EAST, "КАССА МФО №1", "Погашение и выдача", "микрозаймов", "(Кредитор внутри)");
		spawnNpcAt(world, -14.5D, fy + 1, 0.5D, NpcRole.MFO_LENDER, "");
		spawnNpcAt(world, -9.5D, fy + 1, 2.5D, NpcRole.MFO_BARKER, "");

		world.setBlockState(
				new BlockPos(-10, fy + 1, -9),
				ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.SOUTH),
				FLAGS
		);
		world.setBlockState(new BlockPos(-8, fy + 1, -9), ModBlocks.REPAIR_STATION.getDefaultState(), FLAGS);

		// 5. Мини-кофепоинт Приёмной и Духовный Уголок Миссии Паломницы Люче
		buildMiniCoffeeCorner(world, -16, fy, 15, "Кофепоинт Приёмной");

		world.setBlockState(new BlockPos(-6, fy + 1, 16), Blocks.GOLD_BLOCK.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(-6, fy + 2, 16), Blocks.LECTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(-7, fy + 1, 16), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(-5, fy + 1, 16), Blocks.END_ROD.getDefaultState(), FLAGS);
		placeWaxedWallSign(world, new BlockPos(-6, fy + 3, 16), Direction.NORTH, "МИССИЯ ВАТИКАНА", "Люче напоминает:", "Не берите займы", "под 20% в день!");
		LuceEntity receptionLuce = ModEntities.LUCE_ENTITY.create(world);
		if (receptionLuce != null) {
			BlockPos missionPos = new BlockPos(-6, fy + 1, 14);
			receptionLuce.refreshPositionAndAngles(missionPos.getX() + 0.5D, missionPos.getY(), missionPos.getZ() + 0.5D, 0.0F, 0.0F);
			receptionLuce.setHomePos(missionPos);
			world.spawnEntity(receptionLuce);
		}
	}

	// =========================================================================
	// УРОВЕНЬ 2: ОФИСЫ ВАЖНЫХ ШИШЕК И КОВОРКИНГ УСТАВШИХ КЛЕРКОВ
	// =========================================================================
	private static void buildLevelTwoOffices(ServerWorld world, int fy) {
		// 1. Западная и Южная половина — Душный Коворкинг (кубиклы уставших клерков и стажёрок)
		int[][] cubicles = new int[][]{
				{-14, -6}, {-14, 2}, {-14, 10},
				{-6, -6}, {-6, 2}, {-6, 10}
		};
		for (int i = 0; i < cubicles.length; i++) {
			int cx = cubicles[i][0];
			int cz = cubicles[i][1];
			// Перегородки кубикла
			for (int dx = -2; dx <= 2; dx++) {
				world.setBlockState(new BlockPos(cx + dx, fy + 1, cz - 2), Blocks.CYAN_TERRACOTTA.getDefaultState(), FLAGS);
				world.setBlockState(new BlockPos(cx + dx, fy + 2, cz - 2), Blocks.LIGHT_GRAY_STAINED_GLASS_PANE.getDefaultState(), FLAGS);
			}
			// Офисный стол с «монитором» и креслом с подушкой
			world.setBlockState(new BlockPos(cx, fy + 1, cz - 1), Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.TOP), FLAGS);
			world.setBlockState(new BlockPos(cx, fy + 2, cz - 1), Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.getDefaultState(), FLAGS);
			BlockPos chair = new BlockPos(cx, fy + 1, cz + 1);
			world.setBlockState(chair, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), FLAGS);
			spawnCushionOn(world, chair, DyeColor.GRAY);

			if (i == 2) {
				// Уставшая Кошкодевочка-стажёрка в коворкинге (можно спасти от офисного рабства!)
				CatgirlEntity intern = ModEntities.CATGIRL_ENTITY.create(world);
				if (intern != null) {
					intern.refreshPositionAndAngles(cx + 0.5D, fy + 1.0D, cz + 0.2D, 180.0F, 0.0F);
					intern.configureVariant(3, false);
					world.spawnEntity(intern);
				}
			} else {
				spawnNpcAt(world, cx + 0.5D, fy + 1, cz + 0.2D, NpcRole.TIRED_CLERK, "");
			}
		}

		// 2. Восточная половина — Отдельные роскошные Кабинеты «Важных Шишек» (X = 6..23)
		for (int z = -16; z <= 18; z++) {
			for (int dy = 1; dy <= 7; dy++) {
				if ((z == -7 || z == 9) && dy <= 2) continue; // дверные проёмы в кабинеты
				world.setBlockState(new BlockPos(6, fy + dy, z), Blocks.DARK_OAK_PLANKS.getDefaultState(), FLAGS);
			}
		}
		for (int x = 6; x <= 23; x++) {
			for (int dy = 1; dy <= 7; dy++) {
				world.setBlockState(new BlockPos(x, fy + dy, 1), Blocks.DARK_OAK_PLANKS.getDefaultState(), FLAGS);
			}
		}
		placeDoor(world, new BlockPos(6, fy + 1, -7), Blocks.DARK_OAK_DOOR, Direction.EAST);
		placeDoor(world, new BlockPos(6, fy + 1, 9), Blocks.DARK_OAK_DOOR, Direction.EAST);
		placeWaxedWallSign(world, new BlockPos(5, fy + 3, -7), Direction.WEST, "КАБИНЕТ №201", "Начальник Отдела", "Отписок и Печатей", "(ВАЖНАЯ ШИШКА)");
		placeWaxedWallSign(world, new BlockPos(5, fy + 3, 9), Direction.WEST, "КАБИНЕТ №202", "Главный Инспектор", "Взысканий МФО", "(ВАЖНАЯ ШИШКА)");

		// Обстановка Кабинета №201 и №202: красные ковры, дубовые столы, сейфы-сундуки, стол Монетки и Важные Шишки!
		furnishVipOffice(world, 15, fy, -8, "1", true);
		furnishVipOffice(world, 15, fy, 10, "2", false);

		// 3. Офисный кафе-уголок с кулером
		buildMiniCoffeeCorner(world, -2, fy, 19, "Офисный Кофе-поинт");
	}

	private static void furnishVipOffice(ServerWorld world, int cx, int fy, int cz, String vipVar, boolean withCoinflip) {
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				world.setBlockState(new BlockPos(cx + dx, fy + 1, cz + dz), Blocks.RED_CARPET.getDefaultState(), FLAGS);
			}
		}
		for (int dz = -2; dz <= 2; dz++) {
			world.setBlockState(new BlockPos(cx + 3, fy + 1, cz + dz), Blocks.DARK_OAK_LOG.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx + 6, fy + 1, cz + dz), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx + 6, fy + 2, cz + dz), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
		}
		world.setBlockState(new BlockPos(cx + 3, fy + 2, cz), Blocks.LANTERN.getDefaultState(), FLAGS);
		if (withCoinflip) {
			world.setBlockState(new BlockPos(cx - 2, fy + 1, cz + 3), ModBlocks.COINFLIP_TABLE.getDefaultState(), FLAGS);
		} else {
			spawnNpcAt(world, cx - 2.5D, fy + 1, cz + 3.0D, NpcRole.MASTER_OF_SPORTS, "bureau_guard");
		}

		// Сейф Важной Шишки
		BlockPos safePos = new BlockPos(cx + 5, fy + 1, cz - 4);
		world.setBlockState(safePos, Blocks.CHEST.getDefaultState(), FLAGS);
		BlockEntity be = world.getBlockEntity(safePos);
		if (be instanceof ChestBlockEntity chest) {
			chest.setStack(4, new ItemStack(Items.GOLD_INGOT, 12));
			chest.setStack(13, new ItemStack(Items.EMERALD, 10));
			chest.setStack(14, new ItemStack(Items.PAPER, 24));
			chest.setStack(22, new ItemStack(ModItems.POKEBALL, 1));
		}

		spawnNpcAt(world, cx + 4.5D, fy + 1, cz + 0.5D, NpcRole.VIP_BUREAUCRAT, vipVar);
	}

	// =========================================================================
	// УРОВЕНЬ 3: СКЛАД С БУМАГАМИ И БЕССМЕРТНЫЙ МОНСТР СКЛАДА
	// =========================================================================
	private static void buildLevelThreeWarehouse(ServerWorld world, int fy) {
		placeWaxedWallSign(world, new BlockPos(0, fy + 2, -16), Direction.NORTH, "⚠ АРХИВНЫЙ СКЛАД", "ОСТОРОЖНО: ВНУТРИ", "БЕССМЕРТНЫЙ РЕВИЗОР!", "(Крадитесь на Shift!)");

		// Длинные ряды высоких складских стеллажей с бумагами, книгами и бочками (лабиринт с укрытиями!)
		for (int rowX = -16; rowX <= 16; rowX += 6) {
			for (int z = -12; z <= 18; z++) {
				// Проходы между секциями стеллажей, чтобы можно было маневрировать от монстра!
				if (z == -3 || z == 7) {
					continue;
				}
				for (int dy = 1; dy <= 5; dy++) {
					BlockPos sp = new BlockPos(rowX, fy + dy, z);
					Block shelf = ((rowX + z + dy) % 4 == 0) ? Blocks.BARREL : (((z + dy) % 3 == 0) ? Blocks.CHISELED_BOOKSHELF : Blocks.BOOKSHELF);
					world.setBlockState(sp, shelf.getDefaultState(), FLAGS);
				}
			}
		}

		// Архивные столы с кафедрами и ценными сундуками в дальних рядах склада
		BlockPos[] lootSpots = new BlockPos[]{
				new BlockPos(-13, fy + 1, 16),
				new BlockPos(3, fy + 1, 17),
				new BlockPos(19, fy + 1, 14)
		};
		for (int i = 0; i < lootSpots.length; i++) {
			BlockPos lp = lootSpots[i];
			world.setBlockState(lp, Blocks.CHEST.getDefaultState(), FLAGS);
			BlockEntity be = world.getBlockEntity(lp);
			if (be instanceof ChestBlockEntity chest) {
				chest.setStack(0, new ItemStack(Items.PAPER, 32));
				chest.setStack(4, new ItemStack(Items.BOOK, 12));
				chest.setStack(12, new ItemStack(Items.DIAMOND, 4 + i * 2));
				chest.setStack(13, new ItemStack(ModItems.POKEBALL, 2));
				if (i == 2) {
					chest.setStack(14, new ItemStack(ModItems.REVIVAL_TOKEN, 1));
				}
				ItemStack dossier = new ItemStack(Items.WRITABLE_BOOK, 1);
				dossier.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Секретная папка «Дело №000-АКТ»").formatted(Formatting.RED, Formatting.BOLD));
				dossier.set(DataComponentTypes.LORE, new LoreComponent(List.of(
						Text.literal("«Ревизор Склада питается просроченными отчётами с 1881 года.»").formatted(Formatting.GRAY),
						Text.literal("«Убить его невозможно — но в полуприседе (Shift) он вас почти не слышит!»").formatted(Formatting.GOLD)
				)));
				chest.setStack(22, dossier);
			}
		}

		// Спавним НЕУБИВАЕМОГО МОНСТРА СКЛАДА («Бессмертный Ревизор Архива») в центре рядов!
		spawnNpcAt(world, 0.5D, fy + 1, 5.5D, NpcRole.ARCHIVAL_HORROR, "");

		// Пыльная каморка-буфет складских грузчиков у входа
		buildMiniCoffeeCorner(world, 17, fy, -18, "Термос Грузчиков");
	}

	// =========================================================================
	// УРОВЕНЬ 4: ОБЩЕСТВЕННЫЙ КАФЕТЕРИЙ (ПИЩЕВАЯ БЮРОКРАТИЯ, ШЕФ-ПОВАР, БАРМЕН, СТАЛИН)
	// =========================================================================
	private static void buildLevelFourCafeteria(ServerWorld world, int fy) {
		// 1. Большая линия раздачи и Окошко Талонов на Вилку и Компот (восточная стена X = 12..22, Z = -14..+12)
		for (int z = -12; z <= 10; z++) {
			world.setBlockState(new BlockPos(12, fy + 1, z), Blocks.SMOOTH_QUARTZ.getDefaultState(), FLAGS);
			if (z % 4 == 0) {
				world.setBlockState(new BlockPos(12, fy + 2, z), Blocks.CAKE.getDefaultState(), FLAGS);
			} else if (z == -2 || z == 2) {
				world.setBlockState(new BlockPos(12, fy + 2, z), Blocks.GLASS_PANE.getDefaultState(), FLAGS);
			}
		}
		placeWaxedWallSign(world, new BlockPos(11, fy + 3, -2), Direction.WEST, "ОТДЕЛ ТАЛОНОВ ЕДЫ", "Без талона на ложку", "вилку не выдаём!", "(ПКМ — суточный паёк)");

		// Кухонное оборудование с коптильнями и сундуками «Странного мяса» по ГОСТу
		for (int z = -8; z <= -4; z++) {
			world.setBlockState(new BlockPos(20, fy + 1, z), Blocks.SMOKER.getDefaultState(), FLAGS);
		}
		BlockPos meatChest = new BlockPos(20, fy + 1, 0);
		world.setBlockState(meatChest, Blocks.CHEST.getDefaultState(), FLAGS);
		BlockEntity mbe = world.getBlockEntity(meatChest);
		if (mbe instanceof ChestBlockEntity chest) {
			chest.setStack(4, new ItemStack(ModItems.STRANGE_MEAT, 6));
			chest.setStack(12, new ItemStack(Items.COOKED_BEEF, 16));
			chest.setStack(14, new ItemStack(Items.PUMPKIN_PIE, 8));
			chest.setStack(22, new ItemStack(Items.GOLDEN_CARROT, 8));
		}

		// Персонал Столовой: Инспектор Пищевой Бюрократии, Шеф-повар, Пекарь, Мясник и Бармен с напитками!
		spawnNpcAt(world, 14.5D, fy + 1, -1.5D, NpcRole.CAFETERIA_BUREAUCRAT, "");
		spawnNpcAt(world, 17.5D, fy + 1, -5.5D, NpcRole.EGO_CHEF, "");
		spawnNpcAt(world, 14.5D, fy + 1, 5.5D, NpcRole.GROCERY_SELLER_1, "");
		spawnNpcAt(world, 14.5D, fy + 1, 9.5D, NpcRole.GROCERY_SELLER_2, "");

		// Барная стойка на юге с Барменом (напитки и «Пьяное забвение»!) и радиоузлом
		for (int x = -6; x <= 4; x++) {
			world.setBlockState(new BlockPos(x, fy + 1, 16), Blocks.DARK_OAK_LOG.getDefaultState(), FLAGS);
		}
		spawnNpcAt(world, -1.0D, fy + 1, 18.5D, NpcRole.CASINO_BARTENDER, "plains");
		world.setBlockState(new BlockPos(-8, fy + 1, 16), ModBlocks.DJ_CONSOLE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(-10, fy + 1, 16), ModBlocks.TRADE_MACHINE.getDefaultState().with(TradeMachineBlock.FACING, Direction.NORTH), FLAGS);

		// 2. Обеденные столы с мягкими разноцветными Подушками + обедающие Сталин, Володя и Эйнштейн!
		int[][] tables = new int[][]{{-10, -6}, {-2, -6}, {5, -6}, {-10, 4}, {-2, 4}, {5, 4}};
		DyeColor[] colors = new DyeColor[]{DyeColor.RED, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.LIME, DyeColor.CYAN, DyeColor.PINK};
		for (int i = 0; i < tables.length; i++) {
			int tx = tables[i][0];
			int tz = tables[i][1];
			world.setBlockState(new BlockPos(tx, fy + 1, tz), Blocks.OAK_FENCE.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(tx, fy + 2, tz), Blocks.WHITE_CARPET.getDefaultState(), FLAGS);
			BlockPos s1 = new BlockPos(tx - 1, fy + 1, tz);
			BlockPos s2 = new BlockPos(tx + 1, fy + 1, tz);
			world.setBlockState(s1, Blocks.BIRCH_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST), FLAGS);
			world.setBlockState(s2, Blocks.BIRCH_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.WEST), FLAGS);
			spawnCushionOn(world, s1, colors[i]);
			spawnCushionOn(world, s2, colors[i]);
		}
		spawnNpcAt(world, -2.0D, fy + 1, -4.2D, NpcRole.STALIN, "");
		spawnNpcAt(world, 5.0D, fy + 1, 5.8D, NpcRole.EINSTEIN, "");
		spawnNpcAt(world, -10.0D, fy + 1, 5.8D, NpcRole.VOLODYA, "bureau_guard");
	}

	// =========================================================================
	// УРОВЕНЬ 5: ЗАБРОШЕННЫЙ ЭТАЖ (СТОЛЕТИЯМИ НИКТО НЕ ХОДИЛ!)
	// =========================================================================
	private static void buildLevelFiveAbandoned(ServerWorld world, int fy) {
		// Густая паутина, обвалившиеся колонны, тусклые фонари душ, черепа древних просителей XVIII века!
		int[][] ruins = new int[][]{{-12, -8}, {-4, -2}, {6, -10}, {12, 0}, {-8, 12}, {4, 14}, {15, 12}};
		for (int i = 0; i < ruins.length; i++) {
			int rx = ruins[i][0];
			int rz = ruins[i][1];
			world.setBlockState(new BlockPos(rx, fy + 1, rz), Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(rx, fy + 2, rz), (i % 2 == 0) ? Blocks.SKELETON_SKULL.getDefaultState() : Blocks.COBWEB.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(rx + 1, fy + 1, rz), Blocks.COBWEB.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(rx - 1, fy + 3, rz + 1), Blocks.COBWEB.getDefaultState(), FLAGS);
		}
		world.setBlockState(new BlockPos(0, fy + 3, 0), Blocks.SOUL_LANTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(14, fy + 3, 14), Blocks.SOUL_LANTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(-14, fy + 3, -12), Blocks.SOUL_LANTERN.getDefaultState(), FLAGS);

		// Истлевшие скелеты древних писарей
		for (int i = 0; i < 3; i++) {
			SkeletonEntity skel = EntityType.SKELETON.create(world);
			if (skel != null) {
				skel.refreshPositionAndAngles(-8.0D + i * 9.0D, fy + 1.0D, 6.0D, 0.0F, 0.0F);
				skel.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
				skel.setCustomName(Text.literal("Истлевший Писарь 1762 г.").formatted(Formatting.GRAY));
				skel.setPersistent();
				world.spawnEntity(skel);
			}
		}

		// Древний архивный сундук с забытыми сокровищами империи и забытая столетиями Кошкодевочка Юки в дальнем углу!
		BlockPos relicChest = new BlockPos(18, fy + 1, 18);
		world.setBlockState(relicChest, Blocks.CHEST.getDefaultState(), FLAGS);
		BlockEntity rbe = world.getBlockEntity(relicChest);
		if (rbe instanceof ChestBlockEntity chest) {
			chest.setStack(4, new ItemStack(Items.NETHERITE_SCRAP, 3));
			chest.setStack(12, new ItemStack(Items.GOLDEN_APPLE, 3));
			chest.setStack(13, new ItemStack(Items.DIAMOND, 8));
			chest.setStack(14, new ItemStack(ModItems.WHITE_CAP_FLASK, 4));
			ItemStack oldScroll = new ItemStack(Items.PAPER, 1);
			oldScroll.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Прошеніе отъ 1783 года (ОЖИДАЕТЪ ПОДПИСИ)").formatted(Formatting.GOLD));
			chest.setStack(22, oldScroll);
		}

		CatgirlEntity forgottenGirl = ModEntities.CATGIRL_ENTITY.create(world);
		if (forgottenGirl != null) {
			forgottenGirl.refreshPositionAndAngles(16.5D, fy + 1.0D, 16.5D, 135.0F, 0.0F);
			forgottenGirl.configureVariant(3, false); // Белоснежная Юки, прождавшая сотни лет!
			world.spawnEntity(forgottenGirl);
		}
	}

	// =========================================================================
	// УРОВЕНЬ 6: КАБИНЕТ БОССА (ВЕРХОВНЫЙ ПРЕДСЕДАТЕЛЬ БЮРОКРАТИИ)
	// =========================================================================
	private static void buildLevelSixBossOffice(ServerWorld world, int fy) {
		// 1. Приёмная Босса на севере (секретарша-Кошкодевочка + телохранители Белых Колпаков)
		for (int x = -23; x <= 23; x++) {
			for (int dy = 1; dy <= 5; dy++) {
				if (Math.abs(x) <= 1 && dy <= 3) continue; // парадный проход к Боссу
				world.setBlockState(new BlockPos(x, fy + dy, -10), Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState(), FLAGS);
			}
		}
		placeWaxedWallSign(world, new BlockPos(2, fy + 2, -11), Direction.NORTH, "КАБИНЕТ №666", "ВЕРХОВНЫЙ БОСС", "БЮРОКРАТИИ", "Вход без стука = БОЙ!");

		CatgirlEntity bossSec = ModEntities.CATGIRL_ENTITY.create(world);
		if (bossSec != null) {
			bossSec.refreshPositionAndAngles(-4.5D, fy + 1.0D, -13.5D, 0.0F, 0.0F);
			bossSec.configureVariant(2, false);
			world.spawnEntity(bossSec);
		}
		spawnNpcAt(world, 5.5D, fy + 1, -13.5D, NpcRole.MFO_ELITE_SELLER, "2");

		// 2. Исполинский Зал Босса: красная ковровая дорожка, золотые колонны, личная Рулетка, Автомат Судьбы и трон-стол!
		for (int z = -9; z <= 16; z++) {
			for (int x = -2; x <= 2; x++) {
				world.setBlockState(new BlockPos(x, fy + 1, z), Blocks.RED_CARPET.getDefaultState(), FLAGS);
			}
		}
		for (int z = -6; z <= 14; z += 6) {
			for (int dy = 1; dy <= 9; dy++) {
				world.setBlockState(new BlockPos(-10, fy + dy, z), Blocks.QUARTZ_PILLAR.getDefaultState(), FLAGS);
				world.setBlockState(new BlockPos(10, fy + dy, z), Blocks.QUARTZ_PILLAR.getDefaultState(), FLAGS);
			}
		}

		// Личный игровой уголок Босса (Стол Рулетки + Автомат Судьбы) и мини-бар
		world.setBlockState(new BlockPos(-15, fy + 1, 4), ModBlocks.ROULETTE_TABLE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(-15, fy + 1, 8), ModBlocks.FATE_MACHINE.getDefaultState(), FLAGS);
		buildMiniCoffeeCorner(world, 15, fy, 4, "Личный Бар Босса");

		// Возвышение и массивный стол Верховного Председателя на юге (Z = 15..20)
		for (int x = -6; x <= 6; x++) {
			for (int z = 15; z <= 21; z++) {
				world.setBlockState(new BlockPos(x, fy + 1, z), Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), FLAGS);
			}
		}
		for (int x = -3; x <= 3; x++) {
			world.setBlockState(new BlockPos(x, fy + 2, 16), Blocks.GOLD_BLOCK.getDefaultState(), FLAGS);
		}
		BlockPos throne = new BlockPos(0, fy + 2, 19);
		world.setBlockState(throne, Blocks.CRIMSON_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), FLAGS);
		spawnCushionOn(world, throne, DyeColor.RED);

		// ФИНАЛЬНЫЙ БОСС ИЗМЕРЕНИЯ!
		spawnNpcAt(world, 0.5D, fy + 2, 18.0D, NpcRole.BUREAUCRACY_BOSS, "");
	}

	// =========================================================================
	// ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ПОСТРОЙКИ
	// =========================================================================
	private static void buildMiniCoffeeCorner(ServerWorld world, int x, int fy, int z, String label) {
		world.setBlockState(new BlockPos(x, fy + 1, z), Blocks.SMOOTH_QUARTZ.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(x, fy + 2, z), Blocks.BREWING_STAND.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(x + 1, fy + 1, z), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3), FLAGS);
		world.setBlockState(new BlockPos(x + 2, fy + 1, z), Blocks.BARREL.getDefaultState(), FLAGS);
		BlockEntity be = world.getBlockEntity(new BlockPos(x + 2, fy + 1, z));
		if (be instanceof net.minecraft.block.entity.BarrelBlockEntity barrel) {
			barrel.setStack(0, new ItemStack(Items.COOKIE, 8));
			barrel.setStack(1, new ItemStack(Items.SUGAR, 6));
			barrel.setStack(2, new ItemStack(Items.BREAD, 4));
		}
		placeWaxedWallSign(world, new BlockPos(x, fy + 3, z), Direction.SOUTH, label, "Чай / Цикорий", "Сушки в бочке", "");
	}

	private static void placeDoor(ServerWorld world, BlockPos lowerPos, Block doorBlock, Direction facing) {
		BlockState lower = doorBlock.getDefaultState()
				.with(DoorBlock.FACING, facing)
				.with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		BlockState upper = doorBlock.getDefaultState()
				.with(DoorBlock.FACING, facing)
				.with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
		world.setBlockState(lowerPos, lower, FLAGS);
		world.setBlockState(lowerPos.up(), upper, FLAGS);
	}

	private static void placeWaxedWallSign(ServerWorld world, BlockPos pos, Direction facing, String l0, String l1, String l2, String l3) {
		BlockPos back = pos.offset(facing.getOpposite());
		if (!world.getBlockState(back).isSolidBlock(world, back)) {
			world.setBlockState(back, Blocks.CHISELED_STONE_BRICKS.getDefaultState(), FLAGS);
		}
		BlockState signState = Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, facing);
		world.setBlockState(pos, signState, FLAGS);
		BlockEntity be = world.getBlockEntity(pos);
		if (be instanceof SignBlockEntity sign) {
			SignText txt = sign.getFrontText()
					.withMessage(0, Text.literal(l0))
					.withMessage(1, Text.literal(l1))
					.withMessage(2, Text.literal(l2))
					.withMessage(3, Text.literal(l3));
			sign.setText(txt, true);
			sign.setWaxed(true);
			sign.markDirty();
		}
	}

	private static void spawnCushionOn(ServerWorld world, BlockPos support, DyeColor color) {
		CushionEntity c = new CushionEntity(ModEntities.CUSHION_ENTITY, world);
		c.refreshPositionAndAngles(support.getX() + 0.5D, support.getY() + 0.56D, support.getZ() + 0.5D, 0.0F, 0.0F);
		c.setDyeColor(color);
		c.setSupportPos(support);
		world.spawnEntity(c);
	}

	private static ModNpcEntity spawnNpcAt(ServerWorld world, double x, int y, double z, NpcRole role, String variant) {
		ModNpcEntity npc = ModEntities.MOD_NPC.create(world);
		if (npc != null) {
			npc.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
			npc.configureRole(role, variant);
			npc.setAnchorPos(BlockPos.ofFloored(x, y, z));
			world.spawnEntity(npc);
		}
		return npc;
	}
}
