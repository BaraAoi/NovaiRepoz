package com.novaimod.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.novaimod.network.ModPayloads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

/**
 * Движок бесшовной телепортации по карте и между измерениями (Верхний мир <-> Незер <-> Энд)
 * без экрана «Загрузка территории», с предзагрузкой чанков и гарантией безопасной точки приземления.
 */
public final class SeamlessTeleportManager {
	private static final int MIN_TICKS_BETWEEN_WARPS = 4; // стремительный бесшовный каскад скачков жемчугов!
	private static final Map<UUID, Long> LAST_WARP_TICK = new HashMap<>();
	private static final Map<UUID, Integer> WARP_CHAIN_COUNTER = new HashMap<>();

	private SeamlessTeleportManager() {
	}

	public static void onPearlImpact(
			ServerPlayerEntity owner,
			ServerWorld pearlWorld,
			Vec3d hitPos,
			boolean allowCrossDimension
	) {
		if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.isSpectator()) {
			return;
		}
		MinecraftServer server = owner.getServer();
		if (server == null) {
			return;
		}

		long now = server.getOverworld().getTime();
		long prev = LAST_WARP_TICK.getOrDefault(owner.getUuid(), -1000L);
		if (now - prev < MIN_TICKS_BETWEEN_WARPS) {
			return;
		}
		LAST_WARP_TICK.put(owner.getUuid(), now);

		int step = WARP_CHAIN_COUNTER.getOrDefault(owner.getUuid(), 0) + 1;
		WARP_CHAIN_COUNTER.put(owner.getUuid(), step);

		Random rng = owner.getRandom();
		ServerWorld currentWorld = owner.getServerWorld();

		// Каждый 3-й скачок (или с шансом 35%) при включённом межпространственном режиме переносит игрока в ДРУГОЕ измерение!
		boolean jumpDimension = allowCrossDimension && (step % 3 == 0 || rng.nextFloat() < 0.35F);
		ServerWorld targetWorld = jumpDimension ? pickDifferentDimension(server, currentWorld, rng) : currentWorld;
		if (targetWorld == null) {
			targetWorld = currentWorld;
		}

		Vec3d safeDest = pickDestinationInWorld(owner, pearlWorld, hitPos, targetWorld, rng, step);
		teleportPlayerSeamlessly(owner, targetWorld, safeDest);
	}

	private static ServerWorld pickDifferentDimension(MinecraftServer server, ServerWorld current, Random rng) {
		RegistryKey<World> curKey = current.getRegistryKey();
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		ServerWorld nether = server.getWorld(World.NETHER);
		ServerWorld end = server.getWorld(World.END);

		if (curKey == World.OVERWORLD) {
			ServerWorld chosen = rng.nextFloat() < 0.58F ? nether : end;
			return chosen != null ? chosen : overworld;
		}
		if (curKey == World.NETHER) {
			ServerWorld chosen = rng.nextFloat() < 0.55F ? overworld : end;
			return chosen != null ? chosen : overworld;
		}
		ServerWorld chosen = rng.nextFloat() < 0.60F ? overworld : nether;
		return chosen != null ? chosen : overworld;
	}

	private static Vec3d pickDestinationInWorld(
			ServerPlayerEntity player,
			ServerWorld pearlWorld,
			Vec3d hitPos,
			ServerWorld targetWorld,
			Random rng,
			int step
	) {
		RegistryKey<World> dim = targetWorld.getRegistryKey();

		if (dim == World.END) {
			// В Энде: либо главный остров [-38..38], либо дальние острова Энда [1040..1280]
			int tx;
			int tz;
			if (rng.nextFloat() < 0.68F) {
				tx = rng.nextBetween(-36, 36);
				tz = rng.nextBetween(-36, 36);
			} else {
				double ang = rng.nextDouble() * Math.PI * 2.0D;
				double dist = 1040.0D + rng.nextDouble() * 220.0D;
				tx = MathHelper.floor(Math.cos(ang) * dist);
				tz = MathHelper.floor(Math.sin(ang) * dist);
			}
			return findSafeEndPos(targetWorld, tx, tz);
		}

		if (dim == World.NETHER) {
			int baseX = targetWorld == player.getServerWorld() ? player.getBlockX() : MathHelper.floor(player.getX() / 8.0D);
			int baseZ = targetWorld == player.getServerWorld() ? player.getBlockZ() : MathHelper.floor(player.getZ() / 8.0D);
			double ang = rng.nextDouble() * Math.PI * 2.0D;
			double dist = 48.0D + rng.nextDouble() * 220.0D;
			int tx = clampToBorder(targetWorld, baseX + MathHelper.floor(Math.cos(ang) * dist), true);
			int tz = clampToBorder(targetWorld, baseZ + MathHelper.floor(Math.sin(ang) * dist), false);
			return findSafeNetherPos(targetWorld, tx, tz);
		}

		// Верхний мир (или любое другое поверхностное измерение):
		// Чередуем скачки по вектору полёта жемчуга (умножая дистанцию в 5..18 раз) и далёкие скачки по карте (200..1500 блоков)!
		int tx;
		int tz;
		if (pearlWorld == targetWorld && step % 2 == 1) {
			Vec3d delta = hitPos.subtract(player.getPos());
			double scale = 4.5D + rng.nextDouble() * 12.0D;
			double ox = MathHelper.clamp(delta.x * scale, -1400.0D, 1400.0D);
			double oz = MathHelper.clamp(delta.z * scale, -1400.0D, 1400.0D);
			if (ox * ox + oz * oz < 64.0D * 64.0D) {
				double ang = rng.nextDouble() * Math.PI * 2.0D;
				ox = Math.cos(ang) * (140.0D + rng.nextDouble() * 420.0D);
				oz = Math.sin(ang) * (140.0D + rng.nextDouble() * 420.0D);
			}
			tx = clampToBorder(targetWorld, MathHelper.floor(player.getX() + ox), true);
			tz = clampToBorder(targetWorld, MathHelper.floor(player.getZ() + oz), false);
		} else {
			double ang = rng.nextDouble() * Math.PI * 2.0D;
			double dist = 180.0D + rng.nextDouble() * 1100.0D;
			double refX = targetWorld == player.getServerWorld() ? player.getX() : player.getX() * 2.0D;
			double refZ = targetWorld == player.getServerWorld() ? player.getZ() : player.getZ() * 2.0D;
			tx = clampToBorder(targetWorld, MathHelper.floor(refX + Math.cos(ang) * dist), true);
			tz = clampToBorder(targetWorld, MathHelper.floor(refZ + Math.sin(ang) * dist), false);
		}
		return findSafeOverworldPos(targetWorld, tx, tz);
	}

	private static int clampToBorder(ServerWorld world, int coord, boolean isX) {
		WorldBorder border = world.getWorldBorder();
		int min = MathHelper.floor((isX ? border.getBoundWest() : border.getBoundNorth()) + 16.0D);
		int max = MathHelper.floor((isX ? border.getBoundEast() : border.getBoundSouth()) - 16.0D);
		min = Math.max(min, -29_999_000);
		max = Math.min(max, 29_999_000);
		return MathHelper.clamp(coord, min, max);
	}

	private static Vec3d findSafeOverworldPos(ServerWorld world, int centerX, int centerZ) {
		int[][] offsets = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}};
		BlockPos fallbackWaterPos = null;

		for (int[] off : offsets) {
			int x = centerX + off[0];
			int z = centerZ + off[1];
			world.getChunk(x >> 4, z >> 4);
			int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos groundPos = new BlockPos(x, topY - 1, z);
			BlockState ground = world.getBlockState(groundPos);
			if (ground.getFluidState().isIn(FluidTags.LAVA) || ground.isOf(Blocks.MAGMA_BLOCK) || ground.isOf(Blocks.CACTUS) || ground.isOf(Blocks.FIRE)) {
				continue;
			}
			if (ground.getFluidState().isIn(FluidTags.WATER)) {
				if (fallbackWaterPos == null) {
					fallbackWaterPos = new BlockPos(x, topY, z);
				}
				continue;
			}
			return new Vec3d(x + 0.5D, topY + 0.1D, z + 0.5D);
		}

		if (fallbackWaterPos != null) {
			// Над океаном создаём крошечный кристальный островок аметиста на поверхности воды
			BlockPos pad = fallbackWaterPos.down();
			if (world.getBlockState(pad).getFluidState().isIn(FluidTags.WATER)) {
				world.setBlockState(pad, Blocks.AMETHYST_BLOCK.getDefaultState());
			}
			return new Vec3d(fallbackWaterPos.getX() + 0.5D, fallbackWaterPos.getY() + 0.1D, fallbackWaterPos.getZ() + 0.5D);
		}

		world.getChunk(centerX >> 4, centerZ >> 4);
		int y = Math.max(world.getBottomY() + 5, world.getTopY(Heightmap.Type.MOTION_BLOCKING, centerX, centerZ));
		BlockPos safePad = new BlockPos(centerX, y, centerZ);
		world.setBlockState(safePad.down(), Blocks.CRYING_OBSIDIAN.getDefaultState());
		world.setBlockState(safePad, Blocks.AIR.getDefaultState());
		world.setBlockState(safePad.up(), Blocks.AIR.getDefaultState());
		return new Vec3d(centerX + 0.5D, y + 0.1D, centerZ + 0.5D);
	}

	private static Vec3d findSafeNetherPos(ServerWorld nether, int centerX, int centerZ) {
		int[][] offsets = {{0, 0}, {6, 0}, {-6, 0}, {0, 6}, {0, -6}};
		BlockPos.Mutable m = new BlockPos.Mutable();

		for (int[] off : offsets) {
			int x = centerX + off[0];
			int z = centerZ + off[1];
			nether.getChunk(x >> 4, z >> 4);
			// Ищем безопасную пещеру строго ПОД бедроковой крышей Незера (y = 33..114)
			for (int y = 108; y >= 33; y--) {
				m.set(x, y - 1, z);
				BlockState floor = nether.getBlockState(m);
				if (!floor.isSolidBlock(nether, m) || floor.isOf(Blocks.MAGMA_BLOCK) || floor.isOf(Blocks.BEDROCK)) {
					continue;
				}
				BlockPos feet = new BlockPos(x, y, z);
				BlockPos head = feet.up();
				BlockState s1 = nether.getBlockState(feet);
				BlockState s2 = nether.getBlockState(head);
				if (s1.isAir() && s2.isAir()) {
					return new Vec3d(x + 0.5D, y + 0.1D, z + 0.5D);
				}
			}
		}

		// Если вокруг сплошной незерак или море лавы — создаём уютную безопасную 3x3 площадку из плачущего обсидиана на Y=64
		int safeY = 64;
		nether.getChunk(centerX >> 4, centerZ >> 4);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos p = new BlockPos(centerX + dx, safeY - 1, centerZ + dz);
				if (!nether.getBlockState(p).isOf(Blocks.BEDROCK)) {
					nether.setBlockState(p, Blocks.CRYING_OBSIDIAN.getDefaultState());
				}
				for (int dy = 0; dy <= 2; dy++) {
					BlockPos airPos = new BlockPos(centerX + dx, safeY + dy, centerZ + dz);
					if (!nether.getBlockState(airPos).isOf(Blocks.BEDROCK)) {
						nether.setBlockState(airPos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
		return new Vec3d(centerX + 0.5D, safeY + 0.1D, centerZ + 0.5D);
	}

	private static Vec3d findSafeEndPos(ServerWorld end, int centerX, int centerZ) {
		int[][] offsets = {{0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8}};
		for (int[] off : offsets) {
			int x = centerX + off[0];
			int z = centerZ + off[1];
			end.getChunk(x >> 4, z >> 4);
			int topY = end.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (topY > 42) {
				return new Vec3d(x + 0.5D, topY + 0.1D, z + 0.5D);
			}
		}

		// Если точка попала над пустотой Энда — создаём парящий астральный островок 3x3 из эндернякового кирпича!
		int padY = 68;
		end.getChunk(centerX >> 4, centerZ >> 4);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				end.setBlockState(new BlockPos(centerX + dx, padY - 1, centerZ + dz), Blocks.END_STONE_BRICKS.getDefaultState());
				end.setBlockState(new BlockPos(centerX + dx, padY, centerZ + dz), Blocks.AIR.getDefaultState());
				end.setBlockState(new BlockPos(centerX + dx, padY + 1, centerZ + dz), Blocks.AIR.getDefaultState());
			}
		}
		return new Vec3d(centerX + 0.5D, padY + 0.1D, centerZ + 0.5D);
	}

	/**
	 * Выполняет мгновенный БЕСШОВНЫЙ перенос игрока (включая смену измерения без экрана загрузки территории!).
	 */
	public static void teleportPlayerSeamlessly(ServerPlayerEntity player, ServerWorld targetWorld, Vec3d dest) {
		ServerWorld originWorld = player.getServerWorld();
		boolean crossDimension = (originWorld != targetWorld);

		// 1. Заранее прогреваем чанк назначения и соседние 3x3 чанка + ставим тикет POST_TELEPORT,
		//    чтобы пакеты чанков ушли клиенту в том же сетевом цикле, что и смена измерения!
		BlockPos destBlock = BlockPos.ofFloored(dest);
		ChunkPos centerChunk = new ChunkPos(destBlock);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				targetWorld.getChunk(centerChunk.x + dx, centerChunk.z + dz);
			}
		}
		targetWorld.getChunkManager().addTicket(ChunkTicketType.POST_TELEPORT, centerChunk, 2, player.getId());

		// 2. Эффект отбытия в старой точке
		originWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(), 28, 0.35D, 0.65D, 0.35D, 0.12D);
		originWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.85F, 1.25F);

		// 3. Отправляем клиенту пакет бесшовного перехода СТРОГО ПЕРЕД player.teleport(...),
		//    чтобы клиент подавил экран «Загрузка территории» ещё до прихода PlayerRespawnS2CPacket!
		String dimPath = targetWorld.getRegistryKey().getValue().getPath();
		ServerPlayNetworking.send(player, new ModPayloads.SeamlessTeleportPayload(dimPath, crossDimension));

		float yaw = player.getYaw();
		float pitch = player.getPitch();
		player.fallDistance = 0.0F;
		player.setVelocity(Vec3d.ZERO);

		// 4. Сам перенос (внутри измерения или в другое измерение)
		player.teleport(targetWorld, dest.x, dest.y, dest.z, yaw, pitch);
		player.fallDistance = 0.0F;

		// 5. Краткая защита от падения/урона при стремительной серии межпространственных скачков
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 35, 0, true, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 3, true, false, false));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 100, 0, true, false, false));

		// 6. Астральная вспышка и звук в точке прибытия (без вывода координат!)
		targetWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1.0D, dest.z, 36, 0.45D, 0.8D, 0.45D, 0.16D);
		targetWorld.spawnParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1.0D, dest.z, 16, 0.4D, 0.6D, 0.4D, 0.06D);
		targetWorld.playSound(
				null,
				destBlock,
				crossDimension ? SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN : SoundEvents.ENTITY_ENDERMAN_TELEPORT,
				SoundCategory.PLAYERS,
				0.95F,
				crossDimension ? 1.45F : 1.18F
		);
	}
}
