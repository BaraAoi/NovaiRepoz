package com.novaimod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.entity.ChronoAstrolabeEntity;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEntities;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * «Made in Heaven: Time of the World» — Космическое Ускорение Времени и Перезапуск Вселенной:
 * 1) Сохраняет полную точку отсчёта мира: точное время суток, погоду, координаты/здоровье/взгляд всех игроков,
 *    всех существ вокруг (включая полный NBT для воскрешения погибших во время ускорения мобов!)
 *    и перехватывает любые изменения блоков (сломанные, поставленные, взорванные или выросшие блоки).
 * 2) В течение 20 секунд плавно и экспоненциально разгоняет смену Дня и Ночи от 1x до >2400x
 *    с субпиксельной интерполяцией 60–240 FPS на клиенте и огромной 3D Небесной Астролябией в небе!
 * 3) В кульминационной Точке Сингулярности вспыхивает бело-золотой Большой Взрыв, и весь мир
 *    бесшовно перезапускается — солнце, луна, блоки, игроки и существа возвращаются на свои исходные места!
 */
public final class TimeOfTheWorldManager {
	public static final int TOTAL_ACCEL_TICKS = 400; // 20.0 секунд разгона Вселенной
	private static final int MAX_SAVED_BLOCKS = 48_000;

	private record SavedBlock(RegistryKey<World> worldKey, BlockPos pos, BlockState state, @Nullable NbtCompound blockEntityNbt) {
	}

	private record SavedPlayerState(
			RegistryKey<World> dimension,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			Vec3d velocity,
			float health,
			int foodLevel,
			float saturation,
			int fireTicks
	) {
	}

	private record SavedEntityState(
			UUID uuid,
			RegistryKey<World> dimension,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			Vec3d velocity,
			float health,
			NbtCompound fullNbt
	) {
	}

	private static boolean active = false;
	private static boolean restoringNow = false;
	private static int elapsedTicks = 0;
	private static long initialTimeOfDay = 0L;
	private static boolean initialRaining = false;
	private static boolean initialThundering = false;
	@Nullable
	private static UUID casterUuid = null;
	private static int astrolabeEntityId = -1;

	private static final Map<UUID, SavedPlayerState> SAVED_PLAYERS = new HashMap<>();
	private static final Map<UUID, SavedEntityState> SAVED_ENTITIES = new LinkedHashMap<>();
	private static final Set<UUID> SPAWNED_DURING_ACCEL = new HashSet<>();
	private static final Map<GlobalBlockKey, SavedBlock> SAVED_BLOCKS = new LinkedHashMap<>();

	private record GlobalBlockKey(RegistryKey<World> worldKey, BlockPos pos) {
	}

	private static final ServerBossBar COSMIC_BAR = new ServerBossBar(
			Text.literal("✦ MADE IN HEAVEN: TIME OF THE WORLD — 1.0x ✦").formatted(Formatting.GOLD, Formatting.BOLD),
			BossBar.Color.YELLOW,
			BossBar.Style.NOTCHED_20
	);

	private TimeOfTheWorldManager() {
	}

	/**
	 * Единая гладкая математическая формула времени суток для Сервера и Клиента (60–240 FPS):
	 * за 20 секунд плавно проходит ~10.4 полных солнечных и звёздных циклов, разгоняясь от 1x до ~2450x!
	 */
	public static long computeVirtualTimeOfDay(long startTimeOfDay, double exactElapsedTicks, int totalTicks) {
		double clamped = MathHelper.clamp(exactElapsedTicks, 0.0D, totalTicks);
		double tSec = clamped / 20.0D;
		double u = clamped / (double) Math.max(1, totalTicks); // 0.0 .. 1.0
		double deltaTicks = 20.0D * tSec
				+ 2800.0D * Math.pow(u, 2.3D)
				+ 36000.0D * Math.pow(u, 4.2D)
				+ 210000.0D * Math.pow(u, 7.2D);
		return startTimeOfDay + Math.round(deltaTicks);
	}

	/**
	 * Текущий множитель скорости хода времени (1.0x в начале -> ~2450x у Сингулярности).
	 */
	public static double computeSpeedMultiplier(double exactElapsedTicks, int totalTicks) {
		double u = MathHelper.clamp(exactElapsedTicks / (double) Math.max(1, totalTicks), 0.0D, 1.0D);
		double ticksPerSec = 20.0D
				+ (2800.0D * 2.3D / 20.0D) * Math.pow(u, 1.3D)
				+ (36000.0D * 4.2D / 20.0D) * Math.pow(u, 3.2D)
				+ (210000.0D * 7.2D / 20.0D) * Math.pow(u, 6.2D);
		return Math.max(1.0D, ticksPerSec / 20.0D);
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean isRecordingChanges() {
		return active && !restoringNow;
	}

	public static void recordBlockBeforeChange(ServerWorld sw, BlockPos pos) {
		if (!active || restoringNow || SAVED_BLOCKS.size() >= MAX_SAVED_BLOCKS) {
			return;
		}
		BlockPos imm = pos.toImmutable();
		GlobalBlockKey key = new GlobalBlockKey(sw.getRegistryKey(), imm);
		if (SAVED_BLOCKS.containsKey(key)) {
			return;
		}
		BlockState oldState = sw.getBlockState(imm);
		NbtCompound beNbt = null;
		BlockEntity be = sw.getBlockEntity(imm);
		if (be != null) {
			beNbt = be.createNbt(sw.getRegistryManager());
		}
		SAVED_BLOCKS.put(key, new SavedBlock(sw.getRegistryKey(), imm, oldState, beNbt));
	}

	public static void onEntityLoaded(Entity entity, ServerWorld sw) {
		if (!active || restoringNow) {
			return;
		}
		if (entity instanceof ServerPlayerEntity || entity instanceof ChronoAstrolabeEntity) {
			return;
		}
		if (!SAVED_ENTITIES.containsKey(entity.getUuid())) {
			SPAWNED_DURING_ACCEL.add(entity.getUuid());
		}
	}

	public static void activateOrAdvance(ServerPlayerEntity player, boolean sneaking) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		if (active) {
			if (sneaking) {
				cancelWithoutReset(server);
				player.sendMessage(Text.literal("⏳ [Made in Heaven]: Ускорение времени остановлено на текущей эпохе без отката.").formatted(Formatting.AQUA, Formatting.BOLD), false);
			} else {
				// Мгновенный переход к кульминационной Сингулярности и перезапуску мира!
				triggerUniverseSingularityReset(server);
			}
			return;
		}

		// Если мир был заморожен Хроно-Скипетром — снимаем вечную паузу для полёта сквозь эпохи
		if (TimeStopManager.isGlobalTimeFrozen(server)) {
			TimeStopManager.toggleGlobalTimeStop(player);
		}

		SAVED_PLAYERS.clear();
		SAVED_ENTITIES.clear();
		SPAWNED_DURING_ACCEL.clear();
		SAVED_BLOCKS.clear();

		ServerWorld overworld = server.getOverworld();
		initialTimeOfDay = overworld.getTimeOfDay();
		initialRaining = overworld.isRaining();
		initialThundering = overworld.isThundering();
		casterUuid = player.getUuid();
		elapsedTicks = 0;
		active = true;
		restoringNow = false;

		// 1. Сохраняем состояние всех игроков и всех существ в радиусе 104 блоков вокруг них
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			SAVED_PLAYERS.put(sp.getUuid(), new SavedPlayerState(
					sp.getServerWorld().getRegistryKey(),
					sp.getX(), sp.getY(), sp.getZ(),
					sp.getYaw(), sp.getPitch(),
					sp.getVelocity(),
					sp.getHealth(),
					sp.getHungerManager().getFoodLevel(),
					sp.getHungerManager().getSaturationLevel(),
					sp.getFireTicks()
			));
			COSMIC_BAR.addPlayer(sp);

			ServerWorld pw = sp.getServerWorld();
			List<Entity> nearby = pw.getOtherEntities(sp, sp.getBoundingBox().expand(104.0D),
					e -> !(e instanceof ServerPlayerEntity) && !(e instanceof ChronoAstrolabeEntity) && e.isAlive());
			for (Entity e : nearby) {
				if (SAVED_ENTITIES.containsKey(e.getUuid())) {
					continue;
				}
				NbtCompound nbt = new NbtCompound();
				if (e.saveSelfNbt(nbt)) {
					float hp = (e instanceof LivingEntity le) ? le.getHealth() : 1.0F;
					SAVED_ENTITIES.put(e.getUuid(), new SavedEntityState(
							e.getUuid(),
							pw.getRegistryKey(),
							e.getX(), e.getY(), e.getZ(),
							e.getYaw(), e.getPitch(),
							e.getVelocity(),
							hp,
							nbt
					));
				}
			}
		}

		// 2. Призываем гигантскую 3D Небесную Астролябию в небо над заклинателем
		ServerWorld casterWorld = player.getServerWorld();
		ChronoAstrolabeEntity astrolabe = ModEntities.CHRONO_ASTROLABE_ENTITY.create(casterWorld);
		if (astrolabe != null) {
			astrolabe.refreshPositionAndAngles(player.getX(), player.getY() + 34.0D, player.getZ(), 0.0F, 0.0F);
			astrolabe.setOwnerUuid(player.getUuid());
			astrolabe.setTotalTicks(TOTAL_ACCEL_TICKS);
			casterWorld.spawnEntity(astrolabe);
			astrolabeEntityId = astrolabe.getId();
		}

		// 3. Звуковой аккорд пробуждения Вселенной и рассылка стартового пакета всем клиентам
		casterWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.6F, 0.75F);
		casterWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.8F, 0.85F);
		casterWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.5F, 0.7F);

		COSMIC_BAR.setPercent(0.0F);
		COSMIC_BAR.setColor(BossBar.Color.YELLOW);
		COSMIC_BAR.setVisible(true);

		broadcastSync(server, true, false);
		server.getPlayerManager().broadcast(
				Text.literal("✦ 「MADE IN HEAVEN: TIME OF THE WORLD」 — ВРЕМЯ ВСЕЛЕННОЙ НАЧИНАЕТ УСКОРЯТЬСЯ! ✦")
						.formatted(Formatting.GOLD, Formatting.BOLD),
				false
		);
	}

	public static void tickServer(MinecraftServer server) {
		if (!active) {
			return;
		}
		elapsedTicks++;
		if (elapsedTicks >= TOTAL_ACCEL_TICKS) {
			triggerUniverseSingularityReset(server);
			return;
		}

		long virtualTime = computeVirtualTimeOfDay(initialTimeOfDay, elapsedTicks, TOTAL_ACCEL_TICKS);
		double speedMult = computeSpeedMultiplier(elapsedTicks, TOTAL_ACCEL_TICKS);
		float progress = MathHelper.clamp((float) elapsedTicks / (float) TOTAL_ACCEL_TICKS, 0.0F, 1.0F);

		// Обновляем серверное время суток во всех мирах + рассылаем пакет времени
		boolean doDaylight = server.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
		for (ServerWorld sw : server.getWorlds()) {
			sw.setTimeOfDay(virtualTime);
		}
		WorldTimeUpdateS2CPacket timePacket = new WorldTimeUpdateS2CPacket(server.getOverworld().getTime(), virtualTime, doDaylight);
		server.getPlayerManager().sendToAll(timePacket);

		// Каждые 8 тиков синхронизируем точный эталон прогресса с клиентами для идеальной 60–240 FPS плавности
		if (elapsedTicks % 8 == 0) {
			broadcastSync(server, true, false);
		}

		// BossBar с живым множителем ускорения
		String multStr = speedMult < 100.0D
				? String.format("%.1fx", speedMult)
				: String.format("%.0fx", speedMult);
		String phaseLabel = progress < 0.30F ? "Пробуждение Хроноса"
				: (progress < 0.65F ? "Вальс Рассветов и Закатов"
				: (progress < 0.88F ? "Световой Поток Эпох" : "ГОРИЗОНТ СИНГУЛЯРНОСТИ"));
		COSMIC_BAR.setPercent(progress);
		COSMIC_BAR.setColor(progress < 0.55F ? BossBar.Color.YELLOW : (progress < 0.85F ? BossBar.Color.BLUE : BossBar.Color.WHITE));
		COSMIC_BAR.setName(
				Text.literal("✦ MADE IN HEAVEN [" + phaseLabel + "] — ").formatted(Formatting.GOLD, Formatting.BOLD)
						.append(Text.literal(multStr).formatted(Formatting.AQUA, Formatting.BOLD))
						.append(Text.literal(" ✦").formatted(Formatting.WHITE))
		);

		// Ускоряющийся ритм часов + звёздные потоки ветра и бурное цветение растений вокруг игроков!
		int tickInterval = Math.max(1, (int) Math.round(18.0D * Math.pow(1.0D - progress, 1.65D)));
		boolean chimeTick = (elapsedTicks % tickInterval == 0);

		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			COSMIC_BAR.addPlayer(sp);
			ServerWorld pw = sp.getServerWorld();

			if (chimeTick) {
				float pitch = 0.65F + progress * 1.35F;
				pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.MASTER, 0.45F, pitch);
				if (progress > 0.45F && elapsedTicks % Math.max(2, tickInterval * 2) == 0) {
					pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 0.55F, pitch);
				}
			}

			// Горизонтальные звёздные линии ускорения времени вокруг игрока
			int streamCount = 2 + (int) (progress * 14.0F);
			double angle = (elapsedTicks * 0.22D);
			for (int i = 0; i < streamCount; i++) {
				double a = angle + i * (Math.PI * 2.0D / streamCount);
				double r = 3.5D + pw.random.nextDouble() * 14.0D;
				double px = sp.getX() + Math.cos(a) * r;
				double py = sp.getY() + 0.5D + pw.random.nextDouble() * 6.5D;
				double pz = sp.getZ() + Math.sin(a) * r;
				pw.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.05D, 0.02D, 0.05D, 0.02D + progress * 0.14D);
				if (progress > 0.5F && i % 2 == 0) {
					pw.spawnParticles(ParticleTypes.WAX_ON, px, py, pz, 1, 0.1D, 0.05D, 0.1D, 0.04D);
				}
			}

			// Живое ускорение роста посевов/ростков вокруг игрока (после перезапуска вернётся на исходную стадию!)
			if (progress > 0.25F && elapsedTicks % 4 == 0) {
				int checks = 1 + (int) (progress * 5.0F);
				BlockPos center = sp.getBlockPos();
				for (int c = 0; c < checks; c++) {
					BlockPos bp = center.add(pw.random.nextBetween(-14, 14), pw.random.nextBetween(-2, 3), pw.random.nextBetween(-14, 14));
					BlockState bs = pw.getBlockState(bp);
					if (bs.getBlock() instanceof Fertilizable fert && fert.isFertilizable(pw, bp, bs)) {
						recordBlockBeforeChange(pw, bp);
						fert.grow(pw, pw.random, bp, bs);
					}
				}
			}
		}

		// На подходе к Сингулярности поднимается торжественный гул маяка и портала
		if (elapsedTicks == TOTAL_ACCEL_TICKS - 30) {
			for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
				sp.getServerWorld().playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.9F, 1.45F);
			}
		}
	}

	public static void triggerUniverseSingularityReset(MinecraftServer server) {
		if (!active) {
			return;
		}
		active = false;
		restoringNow = true;

		try {
			// 1. Отправляем клиентам сигнал бело-золотой вспышки Большого Взрыва и мгновенного возврата времени!
			for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
				ServerPlayNetworking.send(sp, new ModPayloads.TimeOfTheWorldSyncPayload(
						false, initialTimeOfDay, TOTAL_ACCEL_TICKS, TOTAL_ACCEL_TICKS, true
				));
				SavedPlayerState ps = SAVED_PLAYERS.get(sp.getUuid());
				if (ps != null) {
					boolean crossDim = !sp.getServerWorld().getRegistryKey().equals(ps.dimension());
					ServerPlayNetworking.send(sp, new ModPayloads.SeamlessTeleportPayload(
							ps.dimension().getValue().toString(),
							crossDim
					));
				}
			}

			// 2. Удаляем Небесную Астролябию и все сущности/дропы, появившиеся ПОСЛЕ точки отсчёта
			for (ServerWorld sw : server.getWorlds()) {
				if (astrolabeEntityId != -1) {
					Entity astro = sw.getEntityById(astrolabeEntityId);
					if (astro instanceof ChronoAstrolabeEntity) {
						astro.discard();
					}
				}
				for (UUID spawnedId : SPAWNED_DURING_ACCEL) {
					Entity extra = sw.getEntity(spawnedId);
					if (extra != null && !(extra instanceof ServerPlayerEntity)) {
						extra.discard();
					}
				}
			}
			astrolabeEntityId = -1;

			// 3. Восстанавливаем ВСЕ изменённые блоки (и содержимое сундуков/BlockEntity!) в обратном порядке!
			List<SavedBlock> blockList = new ArrayList<>(SAVED_BLOCKS.values());
			for (int i = blockList.size() - 1; i >= 0; i--) {
				SavedBlock sb = blockList.get(i);
				ServerWorld sw = server.getWorld(sb.worldKey());
				if (sw == null) {
					continue;
				}
				sw.setBlockState(sb.pos(), sb.state(), Block.NOTIFY_ALL | Block.FORCE_STATE | Block.SKIP_DROPS);
				if (sb.blockEntityNbt() != null) {
					BlockEntity be = sw.getBlockEntity(sb.pos());
					if (be != null) {
						be.read(sb.blockEntityNbt(), sw.getRegistryManager());
						be.markDirty();
					}
				}
			}

			// 4. Восстанавливаем (и при необходимости воскрешаем!) всех существ на их точные исходные места!
			for (SavedEntityState es : SAVED_ENTITIES.values()) {
				ServerWorld targetWorld = server.getWorld(es.dimension());
				if (targetWorld == null) {
					continue;
				}
				Entity existing = null;
				for (ServerWorld sw : server.getWorlds()) {
					Entity found = sw.getEntity(es.uuid());
					if (found != null) {
						existing = found;
						break;
					}
				}
				if (existing != null && existing.isAlive()) {
					if (existing.getWorld() == targetWorld) {
						existing.refreshPositionAndAngles(es.x(), es.y(), es.z(), es.yaw(), es.pitch());
						existing.setVelocity(es.velocity());
						existing.velocityModified = true;
						existing.setFireTicks(0);
						if (existing instanceof LivingEntity le) {
							le.setHealth(es.health());
						}
					}
				} else {
					if (existing != null) {
						existing.discard();
					}
					Entity recreated = EntityType.loadEntityWithPassengers(es.fullNbt().copy(), targetWorld, entity -> {
						entity.refreshPositionAndAngles(es.x(), es.y(), es.z(), es.yaw(), es.pitch());
						entity.setVelocity(es.velocity());
						return entity;
					});
					if (recreated != null) {
						if (recreated instanceof LivingEntity le) {
							le.setHealth(es.health());
						}
						targetWorld.spawnEntity(recreated);
					}
				}
			}

			// 5. Возвращаем точное исходное Время Суток и Погоду!
			ServerWorld overworld = server.getOverworld();
			for (ServerWorld sw : server.getWorlds()) {
				sw.setTimeOfDay(initialTimeOfDay);
			}
			if (!initialRaining && !initialThundering) {
				overworld.setWeather(12000, 0, false, false);
			} else {
				overworld.setWeather(0, 6000, initialRaining, initialThundering);
			}
			boolean doDaylight = server.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
			server.getPlayerManager().sendToAll(new WorldTimeUpdateS2CPacket(overworld.getTime(), initialTimeOfDay, doDaylight));

			// 6. Бесшовно возвращаем каждого игрока в его точную исходную точку, поворот, здоровье и сытость!
			for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
				SavedPlayerState ps = SAVED_PLAYERS.get(sp.getUuid());
				if (ps != null) {
					ServerWorld dest = server.getWorld(ps.dimension());
					if (dest != null) {
						sp.teleport(dest, ps.x(), ps.y(), ps.z(), ps.yaw(), ps.pitch());
						sp.setVelocity(ps.velocity());
						sp.velocityModified = true;
						sp.fallDistance = 0.0F;
						sp.setFireTicks(ps.fireTicks());
						sp.setHealth(Math.max(1.0F, ps.health()));
						sp.getHungerManager().setFoodLevel(ps.foodLevel());
						sp.getHungerManager().setSaturationLevel(ps.saturation());
					}
				}
				ServerWorld pw = sp.getServerWorld();
				pw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.MASTER, 0.85F, 1.35F);
				pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.0F, 1.2F);
				pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.6F, 1.25F);
				pw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.1D, sp.getZ(), 55, 1.6D, 1.2D, 1.6D, 0.08D);
			}

			COSMIC_BAR.clearPlayers();
			COSMIC_BAR.setVisible(false);

			server.getPlayerManager().broadcast(
					Text.literal("✦ 「MADE IN HEAVEN」 — ВСЕЛЕННАЯ ПЕРЕЗАПУЩЕНА! Всё вернулось на свои исходные места. ✦")
							.formatted(Formatting.AQUA, Formatting.BOLD),
					false
			);
		} finally {
			SAVED_PLAYERS.clear();
			SAVED_ENTITIES.clear();
			SPAWNED_DURING_ACCEL.clear();
			SAVED_BLOCKS.clear();
			restoringNow = false;
		}
	}

	private static void cancelWithoutReset(MinecraftServer server) {
		active = false;
		restoringNow = false;
		for (ServerWorld sw : server.getWorlds()) {
			if (astrolabeEntityId != -1) {
				Entity astro = sw.getEntityById(astrolabeEntityId);
				if (astro instanceof ChronoAstrolabeEntity) {
					astro.discard();
				}
			}
		}
		astrolabeEntityId = -1;
		COSMIC_BAR.clearPlayers();
		COSMIC_BAR.setVisible(false);
		long nowTime = server.getOverworld().getTimeOfDay();
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(sp, new ModPayloads.TimeOfTheWorldSyncPayload(
					false, nowTime, 0, TOTAL_ACCEL_TICKS, false
			));
		}
		SAVED_PLAYERS.clear();
		SAVED_ENTITIES.clear();
		SPAWNED_DURING_ACCEL.clear();
		SAVED_BLOCKS.clear();
	}

	public static void onPlayerJoin(ServerPlayerEntity player) {
		if (active) {
			COSMIC_BAR.addPlayer(player);
			ServerPlayNetworking.send(player, new ModPayloads.TimeOfTheWorldSyncPayload(
					true, initialTimeOfDay, elapsedTicks, TOTAL_ACCEL_TICKS, false
			));
		}
	}

	private static void broadcastSync(MinecraftServer server, boolean isActive, boolean flash) {
		ModPayloads.TimeOfTheWorldSyncPayload payload = new ModPayloads.TimeOfTheWorldSyncPayload(
				isActive, initialTimeOfDay, elapsedTicks, TOTAL_ACCEL_TICKS, flash
		);
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(sp, payload);
		}
	}
}
