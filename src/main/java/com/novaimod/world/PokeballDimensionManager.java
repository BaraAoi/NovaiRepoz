package com.novaimod.world;

import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.CushionEntity;
import com.novaimod.item.PokeballItem;
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
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Управляет личным обустроенным мини-пространством ВНУТРИ Покебола для пойманных игроков:
 * - Сферический купол Покебола (красный верх с тёплыми светильниками, чёрный пояс экватора с белой кнопкой-замком,
 *   белый низ, паркетный пол с ковром Покебола, мягкая двуспальная тахта с подушками, верстак, коптильня,
 *   раковина, торт на столе, сундук с едой и пластинкой, книжный уголок и проигрыватель!).
 * - Если владелец снаружи бросает Покебол на землю — пойманный игрок выпрыгивает из Покебола в новой точке!
 * - Изнутри игрок также может в любой момент нажать ПКМ по белой Кнопке Покебола на северной стене, чтобы выбраться наружу!
 */
public final class PokeballDimensionManager {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final int POCKET_RADIUS = 8;
	private static final int POCKET_Y = 296;

	private PokeballDimensionManager() {
	}

	public static int capturePlayerIntoPokeball(ServerPlayerEntity targetPlayer, int existingPocketId) {
		MinecraftServer server = targetPlayer.getServer();
		if (server == null) {
			return 0;
		}
		ModPersistentState state = ModPersistentState.get(server);
		ServerWorld overworld = server.getOverworld();

		int pocketId = existingPocketId > 0 ? existingPocketId : state.nextPokeballPocketId();
		ModPersistentState.PokeballPocketSession session = state.getPokeballPockets().get(pocketId);
		if (session == null) {
			session = new ModPersistentState.PokeballPocketSession();
			session.pocketId = pocketId;
			session.centerPos = new BlockPos(40000 + pocketId * 256, POCKET_Y, 40000);
		}

		// Если игрок ещё не внутри другого Покебола — сохраняем его исходные координаты в мире
		if (!isPlayerInsideAnyPokeball(targetPlayer)) {
			session.returnDim = targetPlayer.getServerWorld().getRegistryKey().getValue().toString();
			session.returnX = targetPlayer.getX();
			session.returnY = targetPlayer.getY();
			session.returnZ = targetPlayer.getZ();
			session.returnYaw = targetPlayer.getYaw();
			session.returnPitch = targetPlayer.getPitch();
		}
		session.occupantUuid = targetPlayer.getUuid();

		// Прогружаем чанки мини-пространства и строим уютную комнату Покебола, если она ещё не построена
		overworld.getChunk(session.centerPos.getX() >> 4, session.centerPos.getZ() >> 4);
		if (!session.built) {
			buildCozyPokeballInterior(overworld, session.centerPos);
			session.built = true;
		}
		state.putPokeballPocket(session);

		// Переносим игрока внутрь уютного Покебола лицом к белой Кнопке Покебола!
		BlockPos c = session.centerPos;
		targetPlayer.fallDistance = 0.0F;
		targetPlayer.teleport(overworld, c.getX() + 0.5D, c.getY(), c.getZ() + 0.5D, 180.0F, 0.0F);
		targetPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 60, 0, true, false));
		overworld.playSound(null, c, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0F, 1.3F);
		overworld.spawnParticles(ParticleTypes.END_ROD, c.getX() + 0.5D, c.getY() + 1.0D, c.getZ() + 0.5D, 28, 0.8D, 0.8D, 0.8D, 0.06D);

		targetPlayer.sendMessage(
				Text.literal("🔴 Вы попали внутрь Покебола! ").formatted(Formatting.RED, Formatting.BOLD)
						.append(Text.literal("Здесь обустроено уютное жилое мини-пространство. Нажмите ПКМ по белой Кнопке Покебола на стене, если захотите выбраться сами!").formatted(Formatting.WHITE)),
				false
		);
		return pocketId;
	}

	public static boolean releasePlayerAt(MinecraftServer server, int pocketId, ServerWorld destWorld, Vec3d destPos) {
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PokeballPocketSession session = state.getPokeballPockets().get(pocketId);
		if (session == null || session.occupantUuid == null) {
			return false;
		}

		ServerPlayerEntity occupant = server.getPlayerManager().getPlayer(session.occupantUuid);
		session.occupantUuid = null;
		state.markDirty();

		if (occupant == null) {
			return false;
		}

		occupant.fallDistance = 0.0F;
		occupant.teleport(destWorld, destPos.x, destPos.y, destPos.z, occupant.getYaw(), 0.0F);
		destWorld.playSound(null, BlockPos.ofFloored(destPos), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.25F);
		destWorld.spawnParticles(ParticleTypes.END_ROD, destPos.x, destPos.y + 1.0D, destPos.z, 32, 0.5D, 0.7D, 0.5D, 0.08D);
		destWorld.spawnParticles(ParticleTypes.FLAME, destPos.x, destPos.y + 0.8D, destPos.z, 14, 0.4D, 0.4D, 0.4D, 0.04D);

		occupant.sendMessage(
				Text.literal("⚪ Покебол открылся — вы снова снаружи!").formatted(Formatting.AQUA, Formatting.BOLD),
				true
		);
		return true;
	}

	public static boolean tryExitFromInsideLatch(ServerPlayerEntity player, BlockPos clickedPos) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PokeballPocketSession mySession = null;
		for (ModPersistentState.PokeballPocketSession s : state.getPokeballPockets().values()) {
			if (player.getUuid().equals(s.occupantUuid)
					&& s.centerPos != null
					&& clickedPos.getSquaredDistance(s.centerPos) <= 100.0D) {
				mySession = s;
				break;
			}
		}
		if (mySession == null) {
			return false;
		}

		BlockState clicked = player.getServerWorld().getBlockState(clickedPos);
		if (!clicked.isOf(Blocks.CHISELED_QUARTZ_BLOCK)
				&& !clicked.isOf(Blocks.STONE_BUTTON)
				&& !clicked.isOf(Blocks.POLISHED_BLACKSTONE_BUTTON)
				&& !clicked.isOf(Blocks.SEA_LANTERN)) {
			return false;
		}
		// Кнопка Покебола расположена на северной стене (dz <= -5 от центра)
		if (clickedPos.getZ() > mySession.centerPos.getZ() - 5) {
			return false;
		}

		int pid = mySession.pocketId;
		mySession.occupantUuid = null;
		state.markDirty();

		// 1. Проверяем, несёт ли кто-то из игроков сейчас этот Покебол в инвентаре!
		for (ServerPlayerEntity holder : server.getPlayerManager().getPlayerList()) {
			boolean cleared = clearMatchingPokeballInInventory(holder, pid);
			if (cleared && holder != player && !isPlayerInsideAnyPokeball(holder)) {
				ServerWorld hw = holder.getServerWorld();
				player.fallDistance = 0.0F;
				player.teleport(hw, holder.getX() + 0.8D, holder.getY(), holder.getZ() + 0.8D, holder.getYaw(), 0.0F);
				hw.playSound(null, holder.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.25F);
				hw.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 28, 0.45D, 0.6D, 0.45D, 0.07D);
				holder.sendMessage(Text.literal("⚪ " + player.getName().getString() + " сам выбрался из вашего Покебола!").formatted(Formatting.YELLOW), true);
				player.sendMessage(Text.literal("⚪ Вы нажали кнопку изнутри и выпрыгнули из Покебола!").formatted(Formatting.GREEN, Formatting.BOLD), true);
				return true;
			}
		}

		// 2. Иначе возвращаем игрока на исходную точку (и очищаем лежащий на земле Покебол этого ID, если он рядом!)
		ServerWorld retWorld = MysterySkyManager.resolveWorld(server, mySession.returnDim);
		Vec3d retPos = new Vec3d(mySession.returnX, mySession.returnY, mySession.returnZ);
		for (ItemEntity ie : retWorld.getEntitiesByClass(ItemEntity.class, Box.of(retPos, 16.0D, 16.0D, 16.0D), e -> e.isAlive())) {
			ItemStack st = ie.getStack();
			if (st.isOf(ModItems.POKEBALL) && PokeballItem.getPocketId(st) == pid) {
				PokeballItem.clearPokeballData(st);
				ie.setStack(st);
			}
		}

		player.fallDistance = 0.0F;
		player.teleport(retWorld, mySession.returnX, mySession.returnY, mySession.returnZ, mySession.returnYaw, mySession.returnPitch);
		retWorld.playSound(null, BlockPos.ofFloored(retPos), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.25F);
		retWorld.spawnParticles(ParticleTypes.END_ROD, retPos.x, retPos.y + 1.0D, retPos.z, 28, 0.45D, 0.6D, 0.45D, 0.07D);
		player.sendMessage(Text.literal("⚪ Вы выбрались наружу из Покебола!").formatted(Formatting.GREEN, Formatting.BOLD), true);
		return true;
	}

	private static boolean clearMatchingPokeballInInventory(ServerPlayerEntity player, int pocketId) {
		boolean found = false;
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack st = player.getInventory().getStack(i);
			if (st.isOf(ModItems.POKEBALL) && PokeballItem.getPocketId(st) == pocketId) {
				PokeballItem.clearPokeballData(st);
				found = true;
			}
		}
		return found;
	}

	public static boolean isPlayerInsideAnyPokeball(PlayerEntity player) {
		if (!(player.getWorld() instanceof ServerWorld sw)) {
			return false;
		}
		ModPersistentState state = ModPersistentState.get(sw.getServer());
		for (ModPersistentState.PokeballPocketSession s : state.getPokeballPockets().values()) {
			if (player.getUuid().equals(s.occupantUuid) && s.centerPos != null
					&& player.getBlockPos().getSquaredDistance(s.centerPos) <= 196.0D) {
				return true;
			}
		}
		return false;
	}

	public static boolean isProtectedPokeballShell(ServerWorld world, BlockPos pos) {
		if (world.getRegistryKey() != net.minecraft.world.World.OVERWORLD || pos.getY() < POCKET_Y - 12 || pos.getY() > POCKET_Y + 12) {
			return false;
		}
		ModPersistentState state = ModPersistentState.get(world.getServer());
		for (ModPersistentState.PokeballPocketSession s : state.getPokeballPockets().values()) {
			if (s.centerPos != null) {
				double dSq = pos.getSquaredDistance(s.centerPos);
				if (dSq >= 30.0D && dSq <= 110.0D) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Строит уютную сферическую квартиру внутри Покебола!
	 */
	public static void buildCozyPokeballInterior(ServerWorld world, BlockPos center) {
		int cx = center.getX();
		int cy = center.getY();
		int cz = center.getZ();

		BlockState redDome = Blocks.RED_CONCRETE.getDefaultState();
		BlockState blackBelt = Blocks.BLACK_CONCRETE.getDefaultState();
		BlockState whiteBowl = Blocks.SMOOTH_QUARTZ.getDefaultState();
		BlockState shroomlight = Blocks.SHROOMLIGHT.getDefaultState();
		BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
		BlockState oakFloor = Blocks.OAK_PLANKS.getDefaultState();
		BlockState spruceTrim = Blocks.SPRUCE_PLANKS.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();

		BlockPos.Mutable m = new BlockPos.Mutable();

		// 1. Сферическая оболочка Покебола (радиус 8)
		for (int dx = -POCKET_RADIUS; dx <= POCKET_RADIUS; dx++) {
			for (int dy = -5; dy <= POCKET_RADIUS; dy++) {
				for (int dz = -POCKET_RADIUS; dz <= POCKET_RADIUS; dz++) {
					double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (dist > POCKET_RADIUS + 0.45D) {
						continue;
					}
					m.set(cx + dx, cy + dy, cz + dz);
					if (dist >= POCKET_RADIUS - 1.05D || dy == -5) {
						if (dy >= 2) {
							if (dy >= 6 && (Math.abs(dx) == 2 || Math.abs(dz) == 2)) {
								world.setBlockState(m, shroomlight, FLAGS);
							} else {
								world.setBlockState(m, redDome, FLAGS);
							}
						} else if (dy == 1) {
							world.setBlockState(m, blackBelt, FLAGS);
						} else {
							world.setBlockState(m, whiteBowl, FLAGS);
						}
					} else if (dy == -1) {
						// Уютный деревянный паркетный пол!
						world.setBlockState(m, ((dx + dz) % 4 == 0) ? spruceTrim : oakFloor, FLAGS);
					} else if (dy < -1) {
						world.setBlockState(m, whiteBowl, FLAGS);
					} else {
						world.setBlockState(m, air, FLAGS);
					}
				}
			}
		}

		// 2. Светящаяся Белая Кнопка-Замок Покебола на северной стене (выход изнутри по ПКМ!)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				BlockPos latch = new BlockPos(cx + dx, cy + dy, cz - 7);
				if (dx == 0 && dy == 1) {
					world.setBlockState(latch, Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState(), FLAGS);
				} else if (Math.abs(dx) == 1 && Math.abs(dy - 1) == 1) {
					world.setBlockState(latch, blackBelt, FLAGS);
				} else {
					world.setBlockState(latch, seaLantern, FLAGS);
				}
			}
		}

		// 3. Ковёр в виде Покебола в центре пола (dy = 0)
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
				BlockState rug = (dx == 0 && dz == 0)
						? Blocks.WHITE_CARPET.getDefaultState()
						: (dz == 0 ? Blocks.BLACK_CARPET.getDefaultState()
						: (dz < 0 ? Blocks.RED_CARPET.getDefaultState() : Blocks.WHITE_CARPET.getDefaultState()));
				world.setBlockState(new BlockPos(cx + dx, cy, cz + dz), rug, FLAGS);
			}
		}

		// 4. МЯГКАЯ ДВУСПАЛЬНАЯ КРОВАТЬ-ТАХТА С ПОДУШКАМИ + КНИЖНЫЙ УГОЛОК (западная стена dx = -5..-4)
		for (int dz = -1; dz <= 1; dz++) {
			world.setBlockState(new BlockPos(cx - 5, cy, cz + dz), Blocks.WHITE_WOOL.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx - 4, cy, cz + dz), Blocks.RED_WOOL.getDefaultState(), FLAGS);
		}
		CushionEntity pillow1 = new CushionEntity(ModEntities.CUSHION_ENTITY, world);
		pillow1.refreshPositionAndAngles(cx - 4.5D, cy + 1.01D, cz - 0.5D, 0.0F, 0.0F);
		pillow1.setDyeColor(DyeColor.WHITE);
		pillow1.setSupportPos(new BlockPos(cx - 5, cy, cz - 1));
		world.spawnEntity(pillow1);

		CushionEntity pillow2 = new CushionEntity(ModEntities.CUSHION_ENTITY, world);
		pillow2.refreshPositionAndAngles(cx - 4.5D, cy + 1.01D, cz + 1.5D, 0.0F, 0.0F);
		pillow2.setDyeColor(DyeColor.RED);
		pillow2.setSupportPos(new BlockPos(cx - 5, cy, cz + 1));
		world.spawnEntity(pillow2);

		world.setBlockState(new BlockPos(cx - 5, cy, cz - 2), spruceTrim, FLAGS);
		world.setBlockState(new BlockPos(cx - 5, cy + 1, cz - 2), Blocks.POTTED_RED_TULIP.getDefaultState(), FLAGS);
		for (int dy = 0; dy <= 2; dy++) {
			world.setBlockState(new BlockPos(cx - 5, cy + dy, cz + 3), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
			world.setBlockState(new BlockPos(cx - 4, cy + dy, cz + 4), Blocks.BOOKSHELF.getDefaultState(), FLAGS);
		}
		world.setBlockState(new BlockPos(cx - 5, cy + 3, cz + 3), Blocks.LANTERN.getDefaultState(), FLAGS);

		// 5. КУХНЯ, МАСТЕРСКАЯ, СТОЛ С ТОРТОМ И СУНДУК С ПРИПАСАМИ ДЛЯ ЖИЗНИ (восточная стена dx = 4..5)
		world.setBlockState(new BlockPos(cx + 5, cy, cz - 2), Blocks.CRAFTING_TABLE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 5, cy, cz - 1), Blocks.SMOKER.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 5, cy, cz), Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3), FLAGS);
		world.setBlockState(new BlockPos(cx + 5, cy, cz + 1), Blocks.ANVIL.getDefaultState(), FLAGS);

		// Столик со свежим Тортом!
		world.setBlockState(new BlockPos(cx + 3, cy, cz + 3), Blocks.SPRUCE_FENCE.getDefaultState(), FLAGS);
		world.setBlockState(new BlockPos(cx + 3, cy + 1, cz + 3), Blocks.CAKE.getDefaultState(), FLAGS);

		// Сундук с едой и вещами для комфортной жизни в Покеболе!
		BlockPos pantryPos = new BlockPos(cx + 5, cy, cz + 2);
		world.setBlockState(pantryPos, Blocks.CHEST.getDefaultState(), FLAGS);
		BlockEntity be = world.getBlockEntity(pantryPos);
		if (be instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(Items.COOKED_BEEF, 16));
			chest.setStack(1, new ItemStack(Items.GOLDEN_APPLE, 4));
			chest.setStack(2, new ItemStack(Items.BREAD, 16));
			chest.setStack(3, new ItemStack(Items.COOKIE, 16));
			chest.setStack(4, new ItemStack(Items.MILK_BUCKET, 1));
			chest.setStack(5, new ItemStack(Items.MUSIC_DISC_OTHERSIDE, 1));
			chest.setStack(6, new ItemStack(Items.BOOK, 4));
		}

		// 6. ДИВАНЧИК И ПРОИГРЫВАТЕЛЬ МУЗЫКИ (южная стена dz = 5)
		BlockState sofa = Blocks.QUARTZ_STAIRS.getDefaultState()
				.with(StairsBlock.FACING, Direction.SOUTH)
				.with(StairsBlock.HALF, BlockHalf.BOTTOM);
		for (int dx = -1; dx <= 1; dx++) {
			world.setBlockState(new BlockPos(cx + dx, cy, cz + 5), sofa, FLAGS);
		}
		world.setBlockState(new BlockPos(cx - 2, cy, cz + 5), Blocks.JUKEBOX.getDefaultState(), FLAGS);
	}
}
