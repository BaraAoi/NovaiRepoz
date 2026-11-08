package com.novaimod.world;

import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.LuceEntity;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Управляет святынями паломницы Люче (Luce) и её верным пёсиком Сантино в мире:
 * - Генерирует «Часовню Святой Люче» (`luce_shrine`) в обычном мире с 5-цветным витражным крестом,
 *   колокольней Ватикана, святым источником, сундуком пилигрима, самой Люче и Сантино!
 * - Позволяет отслеживать часовню на вращающемся Компасе и строить по команде `/novai spawn luce_shrine`.
 */
public final class LuceWorldManager {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private LuceWorldManager() {
	}

	public static void tickLuceWorld(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		ServerWorld overworld = server.getOverworld();
		if (overworld.getPlayers().isEmpty()) {
			return;
		}
		if (hasShrineRecorded(state)) {
			return;
		}

		for (ServerPlayerEntity sp : overworld.getPlayers()) {
			if (sp.isSpectator()) {
				continue;
			}
			BlockPos spawn = overworld.getSpawnPos();
			int targetX = spawn.getX() - 96;
			int targetZ = spawn.getZ() + 96;
			if (!overworld.isChunkLoaded((targetX - 12) >> 4, (targetZ - 12) >> 4)
					|| !overworld.isChunkLoaded((targetX + 12) >> 4, (targetZ + 12) >> 4)) {
				// Если игрок далеко от спавна — строим часовню паломников неподалёку от загруженной области игрока
				targetX = sp.getBlockX() + 64;
				targetZ = sp.getBlockZ() - 64;
				if (!overworld.isChunkLoaded((targetX - 12) >> 4, (targetZ - 12) >> 4)
						|| !overworld.isChunkLoaded((targetX + 12) >> 4, (targetZ + 12) >> 4)) {
					continue;
				}
			}

			int surfY = Math.max(overworld.getSeaLevel() + 1, overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ));
			BlockPos shrineOrigin = new BlockPos(targetX, surfY, targetZ);
			buildLuceShrine(overworld, shrineOrigin);
			state.recordStructure("luce_shrine", World.OVERWORLD.getValue().toString(), shrineOrigin);
			break;
		}
	}

	public static boolean hasShrineRecorded(ModPersistentState state) {
		for (ModPersistentState.StructureEntry se : state.getGeneratedStructures()) {
			if ("luce_shrine".equals(se.type)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Призывает пёсика паломницы Люче — Сантино!
	 * Если кто-то обидит Сантино, Люче немедленно пробудит режим Божественного Гнева!
	 */
	public static WolfEntity spawnSantinoDog(ServerWorld world, BlockPos pos) {
		WolfEntity wolf = EntityType.WOLF.create(world);
		if (wolf == null) {
			return null;
		}
		wolf.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
		wolf.setCustomName(Text.literal("Сантино (Пёсик Люче)").formatted(Formatting.AQUA, Formatting.BOLD));
		wolf.setCustomNameVisible(true);
		wolf.addCommandTag(LuceEntity.TAG_SANTINO_DOG);
		wolf.setPersistent();
		EntityAttributeInstance maxHp = wolf.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
		if (maxHp != null) {
			maxHp.setBaseValue(60.0D);
		}
		wolf.setHealth(60.0F);
		world.spawnEntity(wolf);
		return wolf;
	}

	/**
	 * Строит живописную «Часовню Святой Люче» (15x17):
	 * белоснежный кварц Ватикана, золотой купол со звонким колоколом и крестом из стержней Энда,
	 * пятицветный миссионерский витраж (зелёный, красный, белый, жёлтый, синий),
	 * святой фонтан паломников, глиняная дорожка со следами пилигримов, Люче и её пёсик Сантино!
	 */
	public static void buildLuceShrine(ServerWorld world, BlockPos origin) {
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();

		BlockState air = Blocks.AIR.getDefaultState();
		BlockState quartz = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.getDefaultState();
		BlockState chiseled = Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
		BlockState gold = Blocks.GOLD_BLOCK.getDefaultState();

		// 1. Расчистка воздуха и каменный фундамент под часовней
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

		// 2. Площадка часовни из гладкого кварца и утоптанная глиняная тропа паломника (+Z вход)
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

		// Синие васильки и жёлтые одуванчики вдоль тропы (цвета волос и дождевика Люче!)
		int[][] flowerSpots = {{-3, 6}, {3, 6}, {-4, 8}, {4, 8}, {-6, 2}, {6, 2}, {-6, -2}, {6, -2}};
		for (int i = 0; i < flowerSpots.length; i++) {
			BlockState fl = (i % 2 == 0) ? Blocks.CORNFLOWER.getDefaultState() : Blocks.DANDELION.getDefaultState();
			world.setBlockState(new BlockPos(ox + flowerSpots[i][0], oy + 1, oz + flowerSpots[i][1]), fl, FLAGS);
		}
		world.setBlockState(new BlockPos(ox - 6, oy + 1, oz + 4), Blocks.FLOWERING_AZALEA.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 6, oy + 1, oz + 4), Blocks.FLOWERING_AZALEA.getDefaultState(), FLAGS);

		// 3. Колоннада и стены Часовни (X = -4..+4, Z = -5..+3, высота 6)
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -5; dz <= 3; dz++) {
				boolean wallX = Math.abs(dx) == 4;
				boolean wallZ = dz == -5 || dz == 3;
				if (!wallX && !wallZ) {
					continue;
				}
				// Парадный арочный вход на юге (dz == 3, dx = -1..+1)
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

		// 4. Пятицветный миссионерский витраж на северной алтарной стене (dz = -5)
		world.setBlockState(new BlockPos(ox, oy + 3, oz - 5), Blocks.WHITE_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 4, oz - 5), Blocks.YELLOW_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 2, oz - 5), Blocks.GREEN_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 1, oy + 3, oz - 5), Blocks.RED_STAINED_GLASS.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 1, oy + 3, oz - 5), Blocks.BLUE_STAINED_GLASS.getDefaultState(), FLAGS);

		// 5. Ступенчатая бело-золотая крыша + колокольня Ватикана с колоколом и сияющим крестом
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
		// Башенка-колокольня в центре крыши
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
		// Святой сияющий крест на вершине колокольни
		world.setBlockState(new BlockPos(ox, oy + 12, oz - 1), gold, FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 13, oz - 1), gold, FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 14, oz - 1), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 1, oy + 13, oz - 1), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 1, oy + 13, oz - 1), Blocks.END_ROD.getDefaultState(), FLAGS);

		// 6. Интерьер часовни: дорожка жёлто-голубого ковра, Алтарь, Сундук Паломника и Святой Источник
		for (int dz = -3; dz <= 3; dz++) {
			BlockState carpet = (dz % 2 == 0) ? Blocks.YELLOW_CARPET.getDefaultState() : Blocks.LIGHT_BLUE_CARPET.getDefaultState();
			world.setBlockState(new BlockPos(ox, oy + 1, oz + dz), carpet, FLAGS);
		}
		world.setBlockState(new BlockPos(ox, oy + 6, oz - 1), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), FLAGS);

		// Алтарь у северной стены
		world.setBlockState(new BlockPos(ox, oy + 1, oz - 4), gold, FLAGS);
		world.setBlockState(new BlockPos(ox, oy + 2, oz - 4), Blocks.LECTERN.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox - 2, oy + 1, oz - 4), Blocks.END_ROD.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(ox + 2, oy + 1, oz - 4), Blocks.END_ROD.getDefaultState(), FLAGS);

		// Малый святой источник во дворе справа (dx = +6..+7, dz = +5..+6)
		for (int dx = 4; dx <= 6; dx++) {
			for (int dz = 5; dz <= 7; dz++) {
				boolean rim = dx == 4 || dx == 6 || dz == 5 || dz == 7;
				world.setBlockState(new BlockPos(ox + dx, oy, oz + dz), Blocks.SEA_LANTERN.getDefaultState(), FLAGS);
				world.setBlockState(new BlockPos(ox + dx, oy + 1, oz + dz), rim ? Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.NORTH) : Blocks.WATER.getDefaultState(), FLAGS);
			}
		}

		// Сундук пилигрима с Миссионерскими Чётками и припасами в дорогу
		BlockPos chestPos = new BlockPos(ox + 3, oy + 1, oz - 4);
		world.setBlockState(chestPos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(ModItems.LUCE_ROSARY, 1));
			chest.setStack(1, new ItemStack(Items.BREAD, 8));
			chest.setStack(2, new ItemStack(Items.GOLDEN_APPLE, 2));
			chest.setStack(4, new ItemStack(ModItems.WHITE_CAP_COMPASS, 1));
			chest.setStack(13, new ItemStack(Items.EMERALD, 5));
			chest.setStack(22, new ItemStack(Items.NAUTILUS_SHELL, 2));
			chest.markDirty();
		}

		// Табличка у входа в Часовню Святой Люче
		BlockPos signPos = new BlockPos(ox + 2, oy + 2, oz + 4);
		world.setBlockState(signPos, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, Direction.SOUTH), FLAGS);
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
			SignText txt = sign.getFrontText()
					.withMessage(0, Text.literal("✝ ЧАСОВНЯ ЛЮЧЕ").formatted(Formatting.GOLD, Formatting.BOLD))
					.withMessage(1, Text.literal("Пилигрим Света"))
					.withMessage(2, Text.literal("3 дара -> реликвии"))
					.withMessage(3, Text.literal("Не греши здесь!"));
			sign.setText(txt, true);
			sign.setWaxed(true);
			sign.markDirty();
		}

		// 7. Призываем саму Люче и её верного пёсика Сантино!
		LuceEntity luce = ModEntities.LUCE_ENTITY.create(world);
		if (luce != null) {
			BlockPos home = new BlockPos(ox, oy + 1, oz - 1);
			luce.refreshPositionAndAngles(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.0F, 0.0F);
			luce.setHomePos(home);
			world.spawnEntity(luce);
		}
		spawnSantinoDog(world, new BlockPos(ox - 1, oy + 1, oz));
	}
}
