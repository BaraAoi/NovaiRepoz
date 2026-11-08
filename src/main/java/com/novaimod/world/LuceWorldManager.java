package com.novaimod.world;

import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.HolyOrderEntity;
import com.novaimod.entity.LuceEntity;
import com.novaimod.entity.WhiteCapEntity;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Генератор структур, гарнизонов и военных патрулей фракции «Святой Орден Люче и Церковь Света»:
 * 1) ГЛАВНАЯ ТВЕРДЫНЯ — «Кафедральный Собор Святой Люче» (`luce_cathedral`):
 *    огромная бело-золотая базилика с двумя колокольнями, витражной розой, рядами скамей с подушками,
 *    исповедальней Священника, сокровищницей, самой Люче, Сантино, рыцарями Ордена и Боссом — Архиепископом Собора!
 * 2) УКРЕПЛЁННАЯ БАЗА — «Монастырская Застава Ордена» (`luce_abbey`):
 *    придорожный форт крестоносцев и инквизиторов для защиты паломников от КБК.
 * 3) СВЯТЫНЯ — «Часовня Святой Люче» (`luce_shrine`) с Люче, Сантино и Паладином-стражем.
 * 4) КРЕСТОВЫЕ ПАТРУЛИ И ПОЛЕВЫЕ СТЫЧКИ С КБК — живые битвы рыцарей Церкви против Клана Белых Колпаков!
 */
public final class LuceWorldManager {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private LuceWorldManager() {
	}

	public static void tickLuceWorld(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			if (sp.isSpectator()) {
				continue;
			}
			ServerWorld world = sp.getServerWorld();
			if (world.getRegistryKey() != World.OVERWORLD) {
				continue;
			}

			ensureInitialChurchStructures(world, sp, state);

			int rx = Math.floorDiv(sp.getBlockX(), 240);
			int rz = Math.floorDiv(sp.getBlockZ(), 240);
			tryGenerateRegionalChurchStructure(world, state, rx, rz);

			if (server.getTicks() % 2200 == 900) {
				trySpawnCrusaderPatrolOrSkirmish(world, sp);
			}
		}
	}

	public static void ensureInitialChurchStructures(ServerWorld world, ServerPlayerEntity sp, ModPersistentState state) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return;
		}

		// 1. Главный Кафедральный Собор Святой Люче неподалёку от стартовой зоны
		if (!state.hasGeneratedRegion("luce_prime_cathedral")) {
			int cx = sp.getBlockX() - 108;
			int cz = sp.getBlockZ() - 92;
			if (areChunksLoaded(world, cx, cz, 24)) {
				BlockPos spot = findDrySurface(world, cx, cz);
				if (spot != null) {
					state.markRegionGenerated("luce_prime_cathedral");
					buildLuceCathedral(world, spot);
					state.recordStructure("luce_cathedral", World.OVERWORLD.getValue().toString(), spot);
				}
			}
		}

		// 2. Первая Часовня Святой Люче
		if (!hasShrineRecorded(state)) {
			int tx = sp.getBlockX() + 72;
			int tz = sp.getBlockZ() - 68;
			if (areChunksLoaded(world, tx, tz, 16)) {
				BlockPos spot = findDrySurface(world, tx, tz);
				if (spot != null) {
					buildLuceShrine(world, spot);
					state.recordStructure("luce_shrine", World.OVERWORLD.getValue().toString(), spot);
				}
			}
		}
	}

	private static void tryGenerateRegionalChurchStructure(ServerWorld world, ModPersistentState state, int rx, int rz) {
		String key = "luce_ow:" + rx + ":" + rz;
		if (state.hasGeneratedRegion(key)) {
			return;
		}

		Random rng = Random.create(((long) rx * 6172839451L) ^ ((long) rz * 8391027465L) ^ (world.getSeed() + 4109L));
		if (rng.nextInt(3) != 0) {
			state.markRegionGenerated(key);
			return;
		}

		int x = rx * 240 + 44 + rng.nextInt(140);
		int z = rz * 240 + 44 + rng.nextInt(140);
		if (!areChunksLoaded(world, x, z, 20)) {
			return;
		}

		BlockPos spot = findDrySurface(world, x, z);
		state.markRegionGenerated(key);
		if (spot == null) {
			return;
		}

		int roll = rng.nextInt(10);
		if (roll == 0) {
			buildLuceCathedral(world, spot);
			state.recordStructure("luce_cathedral", World.OVERWORLD.getValue().toString(), spot);
		} else if (roll <= 5) {
			buildLuceAbbeyOutpost(world, spot);
			state.recordStructure("luce_abbey", World.OVERWORLD.getValue().toString(), spot);
		} else {
			buildLuceShrine(world, spot);
			state.recordStructure("luce_shrine", World.OVERWORLD.getValue().toString(), spot);
		}
	}

	private static void trySpawnCrusaderPatrolOrSkirmish(ServerWorld world, ServerPlayerEntity player) {
		Random rng = world.random;
		double angle = rng.nextDouble() * Math.PI * 2.0D;
		int dist = 28 + rng.nextInt(14);
		int px = player.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
		int pz = player.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
		if (!areChunksLoaded(world, px, pz, 12)) {
			return;
		}
		BlockPos spot = findDrySurface(world, px, pz);
		if (spot == null) {
			return;
		}

		// Призываем Крестовый Патруль Ордена Люче (2 Паладина + 1 Инквизитор + 1 Священник)
		spawnOrderMember(world, spot.add(-1, 1, 0), HolyOrderEntity.RANK_CRUSADER, spot, 40);
		spawnOrderMember(world, spot.add(1, 1, 0), HolyOrderEntity.RANK_CRUSADER, spot, 40);
		spawnOrderMember(world, spot.add(0, 1, -1), HolyOrderEntity.RANK_INQUISITOR, spot, 40);
		spawnOrderMember(world, spot.add(0, 1, 1), HolyOrderEntity.RANK_PRIEST, spot, 40);

		// В 50% случаев навстречу патрулю Церкви выходит вражеский отряд КБК — начинается полевая битва фракций!
		if (rng.nextBoolean()) {
			BlockPos kbkSpot = findDrySurface(world, px + 14, pz + 8);
			if (kbkSpot != null) {
				int[] kbkRanks = {WhiteCapEntity.RANK_HUNTER, WhiteCapEntity.RANK_HUNTER, WhiteCapEntity.RANK_ARBALIST, WhiteCapEntity.RANK_APOTHECARY};
				for (int i = 0; i < kbkRanks.length; i++) {
					WhiteCapEntity kbk = ModEntities.WHITE_CAP_ENTITY.create(world);
					if (kbk != null) {
						kbk.refreshPositionAndAngles(kbkSpot.getX() + (i - 1) + 0.5D, kbkSpot.getY() + 1.0D, kbkSpot.getZ() + 0.5D, 180.0F, 0.0F);
						kbk.setRank(kbkRanks[i]);
						kbk.setHomeAnchor(kbkSpot, 40);
						world.spawnEntity(kbk);
					}
				}
				world.playSound(null, spot, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.NEUTRAL, 1.3F, 1.05F);
				player.sendMessage(
						Text.literal("⚔ [Священная Война]: Неподалёку столкнулись рыцари Ордена Святой Люче и отряд Клана Белых Колпаков (КБК)! Помогите Церкви ради Доверия и Обета Люче!")
								.formatted(Formatting.GOLD, Formatting.BOLD),
						false
				);
			}
		}
	}

	private static boolean areChunksLoaded(ServerWorld world, int cx, int cz, int radiusBlocks) {
		return world.isChunkLoaded((cx - radiusBlocks) >> 4, (cz - radiusBlocks) >> 4)
				&& world.isChunkLoaded((cx + radiusBlocks) >> 4, (cz + radiusBlocks) >> 4)
				&& world.isChunkLoaded((cx - radiusBlocks) >> 4, (cz + radiusBlocks) >> 4)
				&& world.isChunkLoaded((cx + radiusBlocks) >> 4, (cz - radiusBlocks) >> 4);
	}

	private static BlockPos findDrySurface(ServerWorld world, int x, int z) {
		RegistryEntry<Biome> biome = world.getBiome(new BlockPos(x, 64, z));
		String id = biome.getKey().map(k -> k.getValue().getPath()).orElse("");
		if (id.contains("ocean") || id.contains("river")) {
			return null;
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= world.getSeaLevel() - 1 || y > 180) {
			return null;
		}
		BlockState below = world.getBlockState(new BlockPos(x, y - 1, z));
		if (below.isOf(Blocks.WATER) || below.isOf(Blocks.LAVA)) {
			return null;
		}
		return new BlockPos(x, y, z);
	}

	public static boolean hasShrineRecorded(ModPersistentState state) {
		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if ("luce_shrine".equals(se.type)) {
				return true;
			}
		}
		return false;
	}

	public static HolyOrderEntity spawnOrderMember(ServerWorld world, BlockPos pos, int rank, BlockPos anchor, int radius) {
		HolyOrderEntity m = ModEntities.HOLY_ORDER_ENTITY.create(world);
		if (m == null) {
			return null;
		}
		m.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		m.setRank(rank);
		m.setHomeAnchor(anchor, radius);
		world.spawnEntity(m);
		return m;
	}

	/**
	 * Призывает верного пёсика паломницы Люче — Сантино!
	 * Немедленно закрепляется за священным UUID самой Люче (приручить костью и натравить на Люче НЕВОЗМОЖНО!).
	 */
	public static WolfEntity spawnSantinoDog(ServerWorld world, BlockPos pos) {
		WolfEntity wolf = EntityType.WOLF.create(world);
		if (wolf == null) {
			return null;
		}
		wolf.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		wolf.setTamed(true, false);
		wolf.setOwnerUuid(LuceEntity.SANTINO_SACRED_OWNER_UUID);
		wolf.setCollarColor(DyeColor.YELLOW);
		wolf.setSitting(false);
		wolf.setInSittingPose(false);
		wolf.setCustomName(Text.literal("Сантино (Пёсик Люче)").formatted(Formatting.AQUA, Formatting.BOLD));
		wolf.setCustomNameVisible(true);
		wolf.addCommandTag(LuceEntity.TAG_SANTINO_DOG);
		wolf.setPersistent();
		EntityAttributeInstance maxHp = wolf.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHp != null) {
			maxHp.setBaseValue(60.0D);
		}
		EntityAttributeInstance atk = wolf.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		if (atk != null) {
			atk.setBaseValue(8.0D);
		}
		wolf.setHealth(60.0F);
		world.spawnEntity(wolf);
		return wolf;
	}

	// =========================================================================
	// 1. ГЛАВНАЯ ТВЕРДЫНЯ ЦЕРКВИ — «КАФЕДРАЛЬНЫЙ СОБОР СВЯТОЙ ЛЮЧЕ» (23x29)
	// =========================================================================
	public static void buildLuceCathedral(ServerWorld world, BlockPos origin) {
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		BlockState air = Blocks.AIR.getDefaultState();
		BlockState quartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState bricks = Blocks.QUARTZ_BRICKS.getDefaultState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();

		// Расчистка и каменный фундамент
		for (int dx = -12; dx <= 12; dx++) {
			for (int dz = -14; dz <= 15; dz++) {
				for (int dy = 1; dy <= 22; dy++) {
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), air, FLAGS);
				}
				for (int sub = -5; sub <= 0; sub++) {
					BlockState st = (sub == 0) ? Blocks.STONE_BRICKS.getDefaultState() : Blocks.DEEPSLATE_BRICKS.getDefaultState();
					world.setBlockState(new BlockPos(ox + dx, oy + sub, oz + dz), st, FLAGS);
				}
			}
		}

		// Мраморный пол базилики (X = -9..+9, Z = -12..+10) + парадная лестница на +Z
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -12; dz <= 10; dz++) {
				BlockState tile;
				if (Math.abs(dx) <= 1) {
					tile = (dz % 2 == 0) ? Blocks.YELLOW_TERRACOTTA.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState();
				} else if (((dx + dz) & 1) == 0) {
					tile = quartz;
				} else {
					tile = bricks;
				}
				world.setBlockState(new BlockPos(ox + dx, oy + 1, oz + dz), tile, FLAGS);
			}
		}
		for (int dx = -4; dx <= 4; dx++) {
			world.setBlockState(new BlockPos(ox + dx, oy + 1, oz + 11), Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), FLAGS);
		}
		for (int dz = 12; dz <= 15; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				world.setBlockState(new BlockPos(ox + dx, oy, oz + dz), Blocks.CHISELED_STONE_BRICKS.getDefaultState(), FLAGS);
			}
		}

		// Стены и контрфорсы нефа (высота dy = 2..10)
		for (int dx = -9; dx <= 9; dx++) {
			for (int dz = -12; dz <= 10; dz++) {
				boolean wx = Math.abs(dx) == 9;
				boolean wz = dz == -12 || dz == 10;
				if (!wx && !wz) {
					continue;
				}
				// Высокий золотой портал входа на юге (dz == 10, dx = -2..+2, dy = 2..6)
				if (dz == 10 && Math.abs(dx) <= 2) {
					for (int dy = 7; dy <= 10; dy++) {
						world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), (dy == 7) ? gold : bricks, FLAGS);
					}
					if (Math.abs(dx) == 2) {
						for (int dy = 2; dy <= 6; dy++) {
							world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), gold, FLAGS);
						}
					}
					continue;
				}

				boolean buttress = (wx && (dz % 4 == 0)) || (wx && wz);
				for (int dy = 2; dy <= 10; dy++) {
					BlockState wall = buttress ? pillar : bricks;
					if (wx && !buttress && dy >= 4 && dy <= 8) {
						wall = ((dz + dy) % 2 == 0) ? Blocks.YELLOW_STAINED_GLASS.getDefaultState() : Blocks.BLUE_STAINED_GLASS.getDefaultState();
					}
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), wall, FLAGS);
				}
			}
		}

		// Огромная 5-цветная витражная Роза в алтарной стене (dz == -12) и над главным входом (dz == 10)
		BlockState[] roseCols = {
				Blocks.YELLOW_STAINED_GLASS.getDefaultState(),
				Blocks.BLUE_STAINED_GLASS.getDefaultState(),
				Blocks.RED_STAINED_GLASS.getDefaultState(),
				Blocks.GREEN_STAINED_GLASS.getDefaultState(),
				Blocks.WHITE_STAINED_GLASS.getDefaultState()
		};
		for (int rx = -2; rx <= 2; rx++) {
			for (int ry = 5; ry <= 9; ry++) {
				if (Math.abs(rx) == 2 && (ry == 5 || ry == 9)) continue;
				BlockState glass = roseCols[Math.floorMod(rx + ry, roseCols.length)];
				world.setBlockState(new BlockPos(ox + rx, oy + ry, oz - 12), glass, FLAGS);
			}
		}

		// Сводчатая крыша нефа (dy = 11..13)
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -13; dz <= 11; dz++) {
				int roofY = oy + 11 + Math.max(0, (6 - Math.abs(dx)) / 3);
				world.setBlockState(new BlockPos(ox + dx, roofY, oz + dz), (Math.abs(dx) <= 1) ? gold : quartz, FLAGS);
			}
		}

		// Две высокие Колокольни Ватикана на южном фасаде (X = -8..-6 и X = +6..+8, Z = 8..10, высота до oy + 18!)
		for (int side : new int[]{-7, 7}) {
			for (int tx = side - 2; tx <= side + 2; tx++) {
				for (int tz = 7; tz <= 11; tz++) {
					boolean edge = Math.abs(tx - side) == 2 || tz == 7 || tz == 11;
					for (int dy = 11; dy <= 16; dy++) {
						if (dy <= 14) {
							world.setBlockState(new BlockPos(ox + tx, oy + dy, oz + tz), edge ? pillar : air, FLAGS);
						} else if (dy == 15) {
							boolean corner = Math.abs(tx - side) == 2 && (tz == 7 || tz == 11);
							world.setBlockState(new BlockPos(ox + tx, oy + dy, oz + tz), corner ? gold : air, FLAGS);
						} else {
							world.setBlockState(new BlockPos(ox + tx, oy + dy, oz + tz), gold, FLAGS);
						}
					}
				}
			}
			world.setBlockState(new BlockPos(ox + side, oy + 15, oz + 9), Blocks.BELL.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(ox + side, oy + 17, oz + 9), gold, FLAGS);
			world.setBlockState(new BlockPos(ox + side, oy + 18, oz + 9), gold, FLAGS);
			world.setBlockState(new BlockPos(ox + side, oy + 19, oz + 9), Blocks.END_ROD.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(ox + side - 1, oy + 18, oz + 9), Blocks.END_ROD.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(ox + side + 1, oy + 18, oz + 9), Blocks.END_ROD.getDefaultState(), FLAGS);
		}

		// Внутренние колонны, люстры и ряды скамей с Подушками!
		for (int dz = -8; dz <= 6; dz += 4) {
			for (int side : new int[]{-5, 5}) {
				for (int dy = 2; dy <= 10; dy++) {
					world.setBlockState(new BlockPos(ox + side, oy + dy, oz + dz), pillar, FLAGS);
				}
				world.setBlockState(new BlockPos(ox + (side > 0 ? 3 : -3), oy + 10, oz + dz), Blocks.CHAIN.getDefaultState(), FLAGS);
				world.setBlockState(new BlockPos(ox + (side > 0 ? 3 : -3), oy + 9, oz + dz), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), FLAGS);
			}
		}

		for (int dz : new int[]{-4, -1, 2, 5}) {
			for (int dx : new int[]{-4, -3, 3, 4}) {
				BlockPos pew = new BlockPos(ox + dx, oy + 2, oz + dz);
				world.setBlockState(pew, Blocks.DARK_OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), FLAGS);
				if (Math.abs(dx) == 3) {
					CushionEntity.placeOnBlock(world, pew, (dz % 2 == 0) ? DyeColor.YELLOW : DyeColor.LIGHT_BLUE, Direction.NORTH);
				}
			}
		}

		// Главный Алтарь Собора на севере (Z = -11..-8) + Колокол Молитвы + 2 сундука Ризницы
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -11; dz <= -8; dz++) {
				world.setBlockState(new BlockPos(ox + dx, oy + 2, oz + dz), (Math.abs(dx) == 4 || dz == -8) ? Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState() : gold, FLAGS);
			}
		}
		world.setBlockState(new BlockPos(ox, oy + 3, oz - 10), Blocks.LECTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 2, oy + 3, oz - 10), Blocks.BELL.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 2, oy + 3, oz - 10), Blocks.END_ROD.getDefaultState(), FLAGS);

		// Сундуки Ризницы Собора
		BlockPos leftChest = new BlockPos(ox - 7, oy + 2, oz - 10);
		BlockPos rightChest = new BlockPos(ox + 7, oy + 2, oz - 10);
		world.setBlockState(leftChest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), FLAGS);
		world.setBlockState(rightChest, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(leftChest) instanceof ChestBlockEntity c1) {
			c1.setStack(0, new ItemStack(ModItems.HOLY_WATER, 4));
			c1.setStack(1, new ItemStack(ModItems.LUCE_ROSARY, 1));
			c1.setStack(2, new ItemStack(Items.GOLDEN_APPLE, 2));
			c1.setStack(4, new ItemStack(Items.BREAD, 12));
			c1.setStack(13, new ItemStack(Items.EMERALD, 6));
			c1.markDirty();
		}
		if (world.getBlockEntity(rightChest) instanceof ChestBlockEntity c2) {
			c2.setStack(0, new ItemStack(ModItems.HOLY_WATER, 4));
			c2.setStack(2, new ItemStack(ModItems.WHITE_CAP_COMPASS, 1));
			c2.setStack(4, new ItemStack(Items.GOLD_INGOT, 8));
			c2.setStack(13, new ItemStack(Items.CANDLE, 8));
			c2.markDirty();
		}

		// Табличка Собора у парадного входа
		BlockPos signPos = new BlockPos(ox + 3, oy + 3, oz + 11);
		world.setBlockState(signPos, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			SignText txt = sign.getFrontText()
					.withMessage(0, Text.literal("✝ СОБОР СВ. ЛЮЧЕ").formatted(Formatting.GOLD, Formatting.BOLD))
					.withMessage(1, Text.literal("Оплот Ордена Света"))
					.withMessage(2, Text.literal("Смерть ереси КБК!"))
					.withMessage(3, Text.literal("Вход в маске КБК ✖"));
			sign.setText(txt, true);
			sign.setWaxed(true);
			sign.markDirty();
		}

		// Гарнизон Кафедрального Собора: Архиепископ, Святая Люче + Сантино, Священник, Паладины и Инквизиторы!
		BlockPos altarHome = new BlockPos(ox, oy + 3, oz - 9);
		spawnOrderMember(world, altarHome, HolyOrderEntity.RANK_CARDINAL, altarHome, 24);
		spawnOrderMember(world, new BlockPos(ox - 6, oy + 2, oz - 2), HolyOrderEntity.RANK_PRIEST, origin, 22);
		spawnOrderMember(world, new BlockPos(ox - 3, oy + 2, oz + 9), HolyOrderEntity.RANK_CRUSADER, origin, 28);
		spawnOrderMember(world, new BlockPos(ox + 3, oy + 2, oz + 9), HolyOrderEntity.RANK_CRUSADER, origin, 28);
		spawnOrderMember(world, new BlockPos(ox, oy + 2, oz + 3), HolyOrderEntity.RANK_CRUSADER, origin, 26);
		spawnOrderMember(world, new BlockPos(ox - 4, oy + 2, oz + 12), HolyOrderEntity.RANK_INQUISITOR, origin, 30);
		spawnOrderMember(world, new BlockPos(ox + 4, oy + 2, oz + 12), HolyOrderEntity.RANK_INQUISITOR, origin, 30);

		LuceEntity cathedralLuce = ModEntities.LUCE_ENTITY.create(world);
		if (cathedralLuce != null) {
			BlockPos lp = new BlockPos(ox + 2, oy + 2, oz - 6);
			cathedralLuce.refreshPositionAndAngles(lp.getX() + 0.5D, lp.getY(), lp.getZ() + 0.5D, 0.0F, 0.0F);
			cathedralLuce.setHomePos(lp);
			world.spawnEntity(cathedralLuce);
		}
		spawnSantinoDog(world, new BlockPos(ox + 1, oy + 2, oz - 5));
	}

	// =========================================================================
	// 2. УКРЕПЛЁННАЯ ЗАСТАВА ОРДЕНА ЛЮЧЕ («luce_abbey», 15x15)
	// =========================================================================
	public static void buildLuceAbbeyOutpost(ServerWorld world, BlockPos origin) {
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();
		BlockState air = Blocks.AIR.getDefaultState();

		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				for (int dy = 1; dy <= 12; dy++) {
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), air, FLAGS);
				}
				for (int sub = -4; sub <= 0; sub++) {
					world.setBlockState(new BlockPos(ox + dx, oy + sub, oz + dz), Blocks.STONE_BRICKS.getDefaultState(), FLAGS);
				}
			}
		}

		// Зубчатая кварцево-каменная ограда и сторожевая башня с колоколом
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = -6; dz <= 6; dz++) {
				boolean wall = Math.abs(dx) == 6 || Math.abs(dz) == 6;
				if (!wall) continue;
				if (dz == 6 && Math.abs(dx) <= 1) continue;
				for (int dy = 1; dy <= 3; dy++) {
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), (dy == 3 && ((dx + dz) & 1) == 0) ? Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState() : Blocks.QUARTZ_BRICKS.getDefaultState(), FLAGS);
				}
			}
		}

		// Дозорная башня Крестоносцев в глубине заставы (dx = -2..+2, dz = -5..-1)
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -5; dz <= -1; dz++) {
				boolean edge = Math.abs(dx) == 2 || dz == -5 || dz == -1;
				for (int dy = 1; dy <= 7; dy++) {
					if (dz == -1 && dx == 0 && dy <= 2) continue;
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), edge ? Blocks.SMOOTH_QUARTZ.getDefaultState() : air, FLAGS);
				}
				world.setBlockState(new BlockPos(ox + dx, oy + 8, oz + dz), Blocks.GOLD_BLOCK.getDefaultState(), FLAGS);
			}
		}
		world.setBlockState(new BlockPos(ox, oy + 9, oz - 3), Blocks.BELL.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 10, oz - 3), Blocks.GOLD_BLOCK.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 11, oz - 3), Blocks.END_ROD.getDefaultState(), FLAGS);

		world.setBlockState(new BlockPos(ox, oy + 1, oz + 2), Blocks.CAMPFIRE.getDefaultState(), FLAGS);
		BlockPos chestPos = new BlockPos(ox - 1, oy + 1, oz - 4);
		world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(ModItems.HOLY_WATER, 3));
			chest.setStack(2, new ItemStack(Items.BREAD, 6));
			chest.setStack(4, new ItemStack(Items.GOLD_INGOT, 4));
			chest.setStack(13, new ItemStack(Items.ARROW, 16));
			chest.markDirty();
		}

		spawnOrderMember(world, new BlockPos(ox - 2, oy + 1, oz + 3), HolyOrderEntity.RANK_CRUSADER, origin, 24);
		spawnOrderMember(world, new BlockPos(ox + 2, oy + 1, oz + 3), HolyOrderEntity.RANK_CRUSADER, origin, 24);
		spawnOrderMember(world, new BlockPos(ox + 3, oy + 1, oz), HolyOrderEntity.RANK_INQUISITOR, origin, 24);
		spawnOrderMember(world, new BlockPos(ox - 3, oy + 1, oz), HolyOrderEntity.RANK_PRIEST, origin, 22);
	}

	// =========================================================================
	// 3. «ЧАСОВНЯ СВЯТОЙ ЛЮЧЕ» (`luce_shrine`, 15x17)
	// =========================================================================
	public static void buildLuceShrine(ServerWorld world, BlockPos origin) {
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		BlockState air = Blocks.AIR.getDefaultState();
		BlockState quartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.getDefaultState();
		BlockState chiseled = Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();

		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 10; dz++) {
				for (int dy = 1; dy <= 15; dy++) {
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), air, FLAGS);
				}
				for (int sub = -4; sub <= 0; sub++) {
					BlockState base = sub == 0 ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState();
					world.setBlockState(new BlockPos(ox + dx, oy + sub, oz + dz), base, FLAGS);
				}
			}
		}

		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -6; dz <= 4; dz++) {
				BlockState floor = (Math.abs(dx) == 5 || dz == -6 || dz == 4)
						? Blocks.GOLD_BLOCK.getDefaultState()
						: (((dx + dz) & 1) == 0 ? quartz : chiseled);
				world.setBlockState(new BlockPos(ox + dx, oy, oz + dz), floor, FLAGS);
			}
		}
		for (int dz = 5; dz <= 10; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				BlockState mudRoad = ((dx + dz) & 1) == 0 ? Blocks.PACKED_MUD.getDefaultState() : Blocks.MUD_BRICKS.getDefaultState();
				world.setBlockState(new BlockPos(ox + dx, oy, oz + dz), mudRoad, FLAGS);
			}
		}

		int[][] flowerSpots = {{-3, 6}, {3, 6}, {-4, 8}, {4, 8}, {-6, 2}, {6, 2}, {-6, -2}, {6, -2}};
		for (int i = 0; i < flowerSpots.length; i++) {
			BlockState fl = (i % 2 == 0) ? Blocks.CORNFLOWER.getDefaultState() : Blocks.DANDELION.getDefaultState();
			world.setBlockState(new BlockPos(ox + flowerSpots[i][0], oy + 1, oz + flowerSpots[i][1]), fl, FLAGS);
		}
		world.setBlockState(new BlockPos(ox - 6, oy + 1, oz + 4), Blocks.FLOWERING_AZALEA.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 4), Blocks.FLOWERING_AZALEA.getDefaultState(), FLAGS);

		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -5; dz <= 3; dz++) {
				boolean wallX = Math.abs(dx) == 4;
				boolean wallZ = dz == -5 || dz == 3;
				if (!wallX && !wallZ) {
					continue;
				}
				if (dz == 3 && Math.abs(dx) <= 1) {
					world.setBlockState(new BlockPos(ox + dx, oy + 5, oz + dz), gold, FLAGS);
					world.setBlockState(new BlockPos(ox + dx, oy + 6, oz + dz), quartz, FLAGS);
					continue;
				}
				boolean corner = wallX && wallZ;
				for (int dy = 1; dy <= 6; dy++) {
					BlockState st = corner ? pillar : quartz;
					if (wallX && (dz == -3 || dz == -1 || dz == 1) && (dy == 3 || dy == 4)) {
						st = (dz == -1) ? Blocks.YELLOW_STAINED_GLASS.getDefaultState() : Blocks.BLUE_STAINED_GLASS.getDefaultState();
					}
					world.setBlockState(new BlockPos(ox + dx, oy + dy, oz + dz), st, FLAGS);
				}
			}
		}

		world.setBlockState(new BlockPos(ox, oy + 3, oz - 5), Blocks.WHITE_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 4, oz - 5), Blocks.YELLOW_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 2, oz - 5), Blocks.GREEN_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 1, oy + 3, oz - 5), Blocks.RED_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 1, oy + 3, oz - 5), Blocks.BLUE_STAINED_GLASS.getDefaultState(), FLAGS);

		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -6; dz <= 4; dz++) {
				world.setBlockState(new BlockPos(ox + dx, oy + 7, oz + dz), Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState(), FLAGS);
			}
		}
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -4; dz <= 2; dz++) {
				world.setBlockState(new BlockPos(ox + dx, oy + 8, oz + dz), quartz, FLAGS);
			}
		}
		int[][] belfryPosts = {{-1, -2}, {1, -2}, {-1, 0}, {1, 0}};
		for (int[] p : belfryPosts) {
			world.setBlockState(new BlockPos(ox + p[0], oy + 9, oz + p[1]), pillar, FLAGS);
			world.setBlockState(new BlockPos(ox + p[0], oy + 10, oz + p[1]), pillar, FLAGS);
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -2; dz <= 0; dz++) {
				world.setBlockState(new BlockPos(ox + dx, oy + 11, oz + dz), gold, FLAGS);
			}
		}
		world.setBlockState(new BlockPos(ox, oy + 10, oz - 1), Blocks.BELL.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 12, oz - 1), gold, FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 13, oz - 1), gold, FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 14, oz - 1), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 1, oy + 13, oz - 1), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 1, oy + 13, oz - 1), Blocks.END_ROD.getDefaultState(), FLAGS);

		for (int dz = -3; dz <= 3; dz++) {
			BlockState carpet = (dz % 2 == 0) ? Blocks.YELLOW_CARPET.getDefaultState() : Blocks.LIGHT_BLUE_CARPET.getDefaultState();
			world.setBlockState(new BlockPos(ox, oy + 1, oz + dz), carpet, FLAGS);
		}
		world.setBlockState(new BlockPos(ox, oy + 6, oz - 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), FLAGS);

		world.setBlockState(new BlockPos(ox, oy + 1, oz - 4), gold, FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 2, oz - 4), Blocks.LECTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 2, oy + 1, oz - 4), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 2, oy + 1, oz - 4), Blocks.END_ROD.getDefaultState(), FLAGS);

		for (int dx = 4; dx <= 6; dx++) {
			for (int dz = 5; dz <= 7; dz++) {
				boolean rim = dx == 4 || dx == 6 || dz == 5 || dz == 7;
				world.setBlockState(new BlockPos(ox + dx, oy, oz + dz), Blocks.SEA_LANTERN.getDefaultState(), FLAGS);
				world.setBlockState(new BlockPos(ox + dx, oy + 1, oz + dz), rim ? Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH) : Blocks.WATER.getDefaultState(), FLAGS);
			}
		}

		BlockPos chestPos = new BlockPos(ox + 3, oy + 1, oz - 4);
		world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(ModItems.LUCE_ROSARY, 1));
			chest.setStack(1, new ItemStack(ModItems.HOLY_WATER, 2));
			chest.setStack(2, new ItemStack(Items.BREAD, 8));
			chest.setStack(4, new ItemStack(ModItems.WHITE_CAP_COMPASS, 1));
			chest.setStack(13, new ItemStack(Items.EMERALD, 5));
			chest.setStack(22, new ItemStack(Items.NAUTILUS_SHELL, 2));
			chest.markDirty();
		}

		BlockPos signPos = new BlockPos(ox + 2, oy + 2, oz + 4);
		world.setBlockState(signPos, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			SignText txt = sign.getFrontText()
					.withMessage(0, Text.literal("✝ ЧАСОВНЯ ЛЮЧЕ").formatted(Formatting.GOLD, Formatting.BOLD))
					.withMessage(1, Text.literal("Пройди 3 Обета"))
					.withMessage(2, Text.literal("и сокруши КБК!"))
					.withMessage(3, Text.literal("Сантино верен Люче"));
			sign.setText(txt, true);
			sign.setWaxed(true);
			sign.markDirty();
		}

		LuceEntity luce = ModEntities.LUCE_ENTITY.create(world);
		if (luce != null) {
			BlockPos home = new BlockPos(ox, oy + 1, oz - 1);
			luce.refreshPositionAndAngles(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.0F, 0.0F);
			luce.setHomePos(home);
			world.spawnEntity(luce);
		}
		spawnSantinoDog(world, new BlockPos(ox - 1, oy + 1, oz));
		spawnOrderMember(world, new BlockPos(ox + 2, oy + 1, oz + 5), HolyOrderEntity.RANK_CRUSADER, origin, 22);
	}
}
