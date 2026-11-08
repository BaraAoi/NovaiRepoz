package com.novaimod.world;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.ChronoAstrolabeEntity;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEntities;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Fertilizable;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * «Time of the World (Made in Heaven)» — Космическое Ускорение Времени и Полный Перезапуск в Девственный Мир:
 * - Режим 0 (Полный Перезапуск Мира): разгоняет смену Дня и Ночи от 1x до >2400x (плавно на 60–240 FPS)
 *   под 32-блочной 3D Небесной Астролябией, а в Точке Сингулярности совершает Большой Взрыв и возвращает
 *   ВЕСЬ МИР в девственно чистое изначальное состояние:
 *     • восстанавливаются ВСЕ сломанные, взорванные, сгоревшие и поставленные за всю игру блоки (включая сундуки);
 *     • воскрешаются ВСЕ когда-либо убитые мобы, жители, животные и NPC на их изначальных местах;
 *     • удаляется весь мусор, стрелы, воронки и дроп, зажигается чистое солнечное утро Дня 1 (1000L)!
 * - Режим 1 (Колесо Эпох — Хозяйственное Ускорение без отката): ускоряет дни и ночи, мгновенно выращивает
 *   все посевы/саженцы и ускоряет плавку во всех печах вокруг игрока!
 */
public final class TimeOfTheWorldManager {
	public static final int TOTAL_ACCEL_TICKS = 400; // 20.0 секунд разгона Вселенной
	public static final long VIRGIN_MORNING_TIME = 1000L;

	private static boolean active = false;
	private static boolean virginRebootMode = true;
	private static int elapsedTicks = 0;
	private static long initialTimeOfDay = 0L;
	private static int astrolabeEntityId = -1;

	private static final ServerBossBar COSMIC_BAR = new ServerBossBar(
			Text.literal("✦ TIME OF THE WORLD (MADE IN HEAVEN) — 1.0x ✦").formatted(Formatting.GOLD, Formatting.BOLD),
			BossBar.Color.YELLOW,
			BossBar.Style.NOTCHED_20
	);

	private TimeOfTheWorldManager() {
	}

	public static long computeVirtualTimeOfDay(long startTimeOfDay, double exactElapsedTicks, int totalTicks) {
		double clamped = MathHelper.clamp(exactElapsedTicks, 0.0D, totalTicks);
		double tSec = clamped / 20.0D;
		double u = clamped / (double) Math.max(1, totalTicks);
		double deltaTicks = 20.0D * tSec
				+ 2800.0D * Math.pow(u, 2.3D)
				+ 36000.0D * Math.pow(u, 4.2D)
				+ 210000.0D * Math.pow(u, 7.2D);
		return startTimeOfDay + Math.round(deltaTicks);
	}

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

	public static void startOrFinish(ServerPlayerEntity player, boolean virginResetMode) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		if (active) {
			// Повторное нажатие ПКМ мгновенно завершает ускорение и запускает Сингулярность!
			finishAcceleration(server);
			return;
		}

		if (TimeStopManager.isGlobalTimeFrozen(server)) {
			TimeStopManager.toggleGlobalTimeStop(player);
		}

		ServerWorld overworld = server.getOverworld();
		initialTimeOfDay = overworld.getTimeOfDay();
		virginRebootMode = virginResetMode;
		elapsedTicks = 0;
		active = true;

		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			COSMIC_BAR.addPlayer(sp);
		}

		ServerWorld casterWorld = player.getServerWorld();
		ChronoAstrolabeEntity astrolabe = ModEntities.CHRONO_ASTROLABE_ENTITY.create(casterWorld);
		if (astrolabe != null) {
			astrolabe.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
			astrolabe.refreshPositionAndAngles(player.getX(), player.getY() + 34.0D, player.getZ(), 0.0F, 0.0F);
			astrolabe.setOwnerUuid(player.getUuid());
			astrolabe.setTotalTicks(TOTAL_ACCEL_TICKS);
			casterWorld.spawnEntity(astrolabe);
			astrolabeEntityId = astrolabe.getId();
		}

		casterWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.6F, 0.75F);
		casterWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.8F, 0.85F);
		casterWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.5F, 0.7F);

		COSMIC_BAR.setPercent(0.0F);
		COSMIC_BAR.setColor(BossBar.Color.YELLOW);
		COSMIC_BAR.setVisible(true);

		broadcastSync(server, true, initialTimeOfDay, false);

		WorldVirginLedger ledger = WorldVirginLedger.get(server);
		String subtitle = virginRebootMode
				? " (К откату: " + ledger.getTotalRecordedBlocks() + " блоков | Эталон мира: " + ledger.getVirginRosterCount() + " коренных существ)"
				: " (Режим Прокрутки Эпох: ускорение ферм и печей)";
		server.getPlayerManager().broadcast(
				Text.literal("✦ 「TIME OF THE WORLD — MADE IN HEAVEN」: ВРЕМЯ НАЧИНАЕТ РАЗГОН!" + subtitle + " ✦")
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
			finishAcceleration(server);
			return;
		}

		long virtualTime = computeVirtualTimeOfDay(initialTimeOfDay, elapsedTicks, TOTAL_ACCEL_TICKS);
		double speedMult = computeSpeedMultiplier(elapsedTicks, TOTAL_ACCEL_TICKS);
		float progress = MathHelper.clamp((float) elapsedTicks / (float) TOTAL_ACCEL_TICKS, 0.0F, 1.0F);

		boolean doDaylight = server.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
		for (ServerWorld sw : server.getWorlds()) {
			sw.setTimeOfDay(virtualTime);
		}
		server.getPlayerManager().sendToAll(new WorldTimeUpdateS2CPacket(server.getOverworld().getTime(), virtualTime, doDaylight));

		if (elapsedTicks % 8 == 0) {
			broadcastSync(server, true, initialTimeOfDay, false);
		}

		String multStr = speedMult < 100.0D
				? String.format("%.1fx", speedMult)
				: String.format("%.0fx", speedMult);
		String phaseLabel = progress < 0.30F ? "Пробуждение Хроноса"
				: (progress < 0.65F ? "Вальс Рассветов и Закатов"
				: (progress < 0.88F ? "Световой Поток Эпох" : "ТОЧКА СИНГУЛЯРНОСТИ"));
		COSMIC_BAR.setPercent(progress);
		COSMIC_BAR.setColor(progress < 0.55F ? BossBar.Color.YELLOW : (progress < 0.85F ? BossBar.Color.BLUE : BossBar.Color.WHITE));
		COSMIC_BAR.setName(
				Text.literal("✦ TIME OF THE WORLD [" + phaseLabel + "] — ").formatted(Formatting.GOLD, Formatting.BOLD)
						.append(Text.literal(multStr).formatted(Formatting.AQUA, Formatting.BOLD))
						.append(Text.literal(" ✦").formatted(Formatting.WHITE))
		);

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

			int streamCount = 2 + (int) (progress * 14.0F);
			double angle = (elapsedTicks * 0.22D);
			for (int i = 0; i < streamCount; i++) {
				double a = angle + i * (Math.PI * 2.0D / streamCount);
				double r = 3.5D + pw.random.nextDouble() * 14.0D;
				double px = sp.getX() + Math.cos(a) * r;
				double py = sp.getY() + 0.5D + pw.random.nextDouble() * 6.5D;
				double pz = sp.getZ() + Math.sin(a) * r;
				pw.spawnParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.05D, 0.02D, 0.05D, 0.02D + progress * 0.14D);
			}

			// Ускоренный рост всех посевов/деревьев и ускоренная плавка печей!
			if (progress > 0.20F && elapsedTicks % 3 == 0) {
				accelerateEnvironmentAround(pw, sp.getBlockPos(), progress);
			}
		}

		if (elapsedTicks == TOTAL_ACCEL_TICKS - 30) {
			for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
				sp.getServerWorld().playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.95F, 1.45F);
			}
		}
	}

	private static void accelerateEnvironmentAround(ServerWorld pw, BlockPos center, float progress) {
		int checks = 2 + (int) (progress * 12.0F);
		for (int c = 0; c < checks; c++) {
			BlockPos bp = center.add(pw.random.nextBetween(-18, 18), pw.random.nextBetween(-3, 5), pw.random.nextBetween(-18, 18));
			BlockState bs = pw.getBlockState(bp);
			if (bs.getBlock() instanceof Fertilizable fert && fert.isFertilizable(pw, bp, bs)) {
				if (virginRebootMode) {
					fert.grow(pw, pw.random, bp, bs);
				} else {
					WorldVirginLedger.runWithBypass(() -> fert.grow(pw, pw.random, bp, bs));
				}
			}
			BlockEntity be = pw.getBlockEntity(bp);
			if (be instanceof AbstractFurnaceBlockEntity furnace) {
				// Дополнительные тики плавки печам/плавильням/коптильням!
				for (int f = 0; f < 10; f++) {
					AbstractFurnaceBlockEntity.tick(pw, bp, bs, furnace);
				}
			}
		}
	}

	private static void finishAcceleration(MinecraftServer server) {
		if (!active) {
			return;
		}
		active = false;

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

		if (!virginRebootMode) {
			// Режим 1: Остановка в новой эпохе с полным созреванием урожая вокруг!
			long newTime = server.getOverworld().getTimeOfDay();
			broadcastSync(server, false, newTime, true);
			for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
				ServerWorld pw = sp.getServerWorld();
				accelerateEnvironmentAround(pw, sp.getBlockPos(), 2.5F);
				pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.0F, 1.25F);
			}
			server.getPlayerManager().broadcast(
					Text.literal("✦ 「TIME OF THE WORLD」 — Новая эпоха наступила! Все посевы и печи вокруг ускорены. ✦")
							.formatted(Formatting.GOLD, Formatting.BOLD),
					false
			);
			return;
		}

		// === РЕЖИМ 0: ПОЛНЫЙ ПЕРЕЗАПУСК ВСЕЛЕННОЙ В ДЕВСТВЕННО ЧИСТЫЙ МИР! ===
		broadcastSync(server, false, VIRGIN_MORNING_TIME, true);

		int[] stats = WorldVirginLedger.get(server).restoreEntireWorldToVirginState(server);
		int restoredBlocks = stats[0];
		int restoredNatives = stats[1];
		int purgedArtificial = stats[2];

		ServerWorld overworld = server.getOverworld();
		for (ServerWorld sw : server.getWorlds()) {
			sw.setTimeOfDay(VIRGIN_MORNING_TIME);
		}
		overworld.setWeather(24000, 0, false, false);

		boolean doDaylight = server.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
		server.getPlayerManager().sendToAll(new WorldTimeUpdateS2CPacket(overworld.getTime(), VIRGIN_MORNING_TIME, doDaylight));

		// Исцеляем игроков и мягко поднимаем на чистую поверхность, если под ними восстановилась гора/здание!
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			ServerWorld pw = sp.getServerWorld();
			sp.setHealth(sp.getMaxHealth());
			sp.getHungerManager().setFoodLevel(20);
			sp.getHungerManager().setSaturationLevel(20.0F);
			sp.setFireTicks(0);
			sp.fallDistance = 0.0F;
			sp.setVelocity(Vec3d.ZERO);
			sp.velocityModified = true;

			BlockPos feet = sp.getBlockPos();
			if (!pw.getBlockState(feet).getCollisionShape(pw, feet).isEmpty()
					|| !pw.getBlockState(feet.up()).getCollisionShape(pw, feet.up()).isEmpty()) {
				int safeY = pw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
				ServerPlayNetworking.send(sp, new ModPayloads.SeamlessTeleportPayload(
						pw.getRegistryKey().getValue().toString(), false
				));
				sp.teleport(pw, sp.getX(), Math.max(feet.getY(), safeY + 0.1D), sp.getZ(), sp.getYaw(), sp.getPitch());
			}

			pw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.MASTER, 0.9F, 1.35F);
			pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.0F, 1.2F);
			pw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.7F, 1.25F);
			pw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.1D, sp.getZ(), 65, 1.8D, 1.3D, 1.8D, 0.08D);
		}

		server.getPlayerManager().broadcast(
				Text.literal("✦ 「MADE IN HEAVEN」 — МИР ПЕРЕЗАПУЩЕН В ДЕВСТВЕННО ЧИСТОЕ СОСТОЯНИЕ! ")
						.formatted(Formatting.AQUA, Formatting.BOLD)
						.append(Text.literal("(Блоков: " + restoredBlocks + " | Коренных существ на местах: " + restoredNatives + " | Стерто чужеродных: " + purgedArtificial + ")")
								.formatted(Formatting.GOLD, Formatting.BOLD)),
				false
		);
	}

	public static void onPlayerJoin(ServerPlayerEntity player) {
		if (active) {
			COSMIC_BAR.addPlayer(player);
			ServerPlayNetworking.send(player, new ModPayloads.TimeOfTheWorldSyncPayload(
					true, initialTimeOfDay, elapsedTicks, TOTAL_ACCEL_TICKS, false
			));
		}
	}

	private static void broadcastSync(MinecraftServer server, boolean isActive, long targetTime, boolean flash) {
		ModPayloads.TimeOfTheWorldSyncPayload payload = new ModPayloads.TimeOfTheWorldSyncPayload(
				isActive, targetTime, elapsedTicks, TOTAL_ACCEL_TICKS, flash
		);
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			ServerPlayNetworking.send(sp, payload);
		}
	}
}
