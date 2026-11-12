package com.novaimod.world;

import com.novaimod.data.ModPersistentState;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Глобальная вечная остановка ВСЕГО времени в игре («Хроно-Скипетр» v3.5.0):
 * - Останавливает всё время во всех измерениях НАВСЕГДА (день/ночь, погоду, жидкости, редстоун, мобов, боссов, снаряды),
 *   пока игрок сам повторно не запустит ход времени!
 * - Использует серверный и клиентский движок ServerTickManager + полную блокировку анимаций рендера (0 дёрганий даже у Дракона Края).
 * - Сохраняется в сейве мира (даже после перезахода время остаётся остановленным).
 */
public final class TimeStopManager {
	private TimeStopManager() {
	}

	public static boolean isGlobalTimeFrozen(MinecraftServer server) {
		if (server == null) {
			return false;
		}
		return ModPersistentState.get(server).isGlobalTimeFrozen();
	}

	public static void toggleGlobalTimeStop(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		boolean freezeNow = !state.isGlobalTimeFrozen();
		state.setGlobalTimeFrozen(freezeNow);

		ServerTickManager tickManager = server.getTickManager();
		if (tickManager.isSprinting()) {
			tickManager.stopSprinting();
		}
		if (tickManager.isStepping()) {
			tickManager.stopStepping();
		}
		tickManager.setFrozen(freezeNow);

		ServerWorld world = player.getServerWorld();
		if (freezeNow) {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 1.5F, 0.5F);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.75F, 0.55F);
			world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 60, 1.4D, 1.0D, 1.4D, 0.18D);
			server.getPlayerManager().broadcast(
					Text.literal("⏳ ВСЕЛЕНСКОЕ ВРЕМЯ ОСТАНОВЛЕНО НАВСЕГДА! ")
							.formatted(Formatting.AQUA, Formatting.BOLD)
							.append(Text.literal("(ПКМ Хроно-Скипетром — запустить время снова)").formatted(Formatting.GRAY)),
					false
			);
		} else {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.5F, 1.45F);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.4F, 1.25F);
			world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.2D, player.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
			server.getPlayerManager().broadcast(
					Text.literal("⏳ ХОД ВРЕМЕНИ ВОССТАНОВЛЕН ВО ВСЕХ МИРАХ!")
							.formatted(Formatting.GOLD, Formatting.BOLD),
					false
			);
		}
	}

	/**
	 * Синхронизирует состояние заморозки времени при загрузке мира и подключении игроков.
	 */
	public static void tickServer(MinecraftServer server) {
		boolean shouldBeFrozen = ModPersistentState.get(server).isGlobalTimeFrozen();
		ServerTickManager tm = server.getTickManager();
		if (shouldBeFrozen && !tm.isFrozen() && !tm.isStepping()) {
			tm.setFrozen(true);
		}
	}

	public static void onPlayerJoin(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server != null) {
			boolean shouldBeFrozen = ModPersistentState.get(server).isGlobalTimeFrozen();
			if (shouldBeFrozen && !server.getTickManager().isFrozen()) {
				server.getTickManager().setFrozen(true);
			}
			server.getTickManager().sendPackets(player);
		}
	}

	/**
	 * Оставлено для обратной совместимости (вся заморозка теперь выполняется на уровне движка TickManager без дёрганий!).
	 */
	public static boolean isEntityFrozen(Entity entity) {
		return false;
	}
}
