package com.novaimod.economy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.network.ModPayloads;
import com.novaimod.network.ModServerNetworking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;

/**
 * Логика игр казино:
 * 1) Стол «Монетка» (50/50 по умолчанию, настраивается творческим пультом).
 * 2) Полноценный стол «Рулетка» (европейское колесо 0–36, все виды ставок,
 *    и если 1 раз выпало зелёное — второй раз подряд выпадает зелёное со 100% вероятностью!).
 */
public final class CasinoGamesManager {
	public static final int COINFLIP_ANIM_TICKS = 45;  // ~2.25 сек
	public static final int ROULETTE_ANIM_TICKS = 130; // ~6.5 сек плавного замедления

	/** Порядок секторов настоящего европейского колеса рулетки (37 секторов: 0..36). */
	public static final int[] WHEEL_ORDER = new int[]{
			0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
			5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
	};

	private static final Set<Integer> RED_NUMBERS = Set.of(
			1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
	);

	private static final int[] RED_ARRAY = new int[]{
			1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36
	};

	private static final int[] BLACK_ARRAY = new int[]{
			2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35
	};

	public enum RouletteBetType {
		RED("Красное", 2),
		BLACK("Чёрное", 2),
		GREEN("Зелёное (0)", 36),
		EVEN("Чётное", 2),
		ODD("Нечётное", 2),
		LOW_1_18("1–18", 2),
		HIGH_19_36("19–36", 2),
		DOZEN_1("1-я дюжина (1–12)", 3),
		DOZEN_2("2-я дюжина (13–24)", 3),
		DOZEN_3("3-я дюжина (25–36)", 3),
		STRAIGHT("Точное число", 36);

		private final String label;
		private final int multiplier;

		RouletteBetType(String label, int multiplier) {
			this.label = label;
			this.multiplier = multiplier;
		}

		public String getLabel() {
			return label;
		}

		public int getMultiplier() {
			return multiplier;
		}

		public static RouletteBetType fromOrdinal(int ord) {
			RouletteBetType[] vals = values();
			if (ord < 0 || ord >= vals.length) {
				return RED;
			}
			return vals[ord];
		}
	}

	private static class PendingResolution {
		final UUID playerUuid;
		int ticksLeft;
		final long payout;
		final Text resultMessage;
		final boolean won;

		PendingResolution(UUID playerUuid, int ticksLeft, long payout, Text resultMessage, boolean won) {
			this.playerUuid = playerUuid;
			this.ticksLeft = ticksLeft;
			this.payout = payout;
			this.resultMessage = resultMessage;
			this.won = won;
		}
	}

	private static final List<PendingResolution> PENDING = new ArrayList<>();

	private CasinoGamesManager() {
	}

	public static boolean isRed(int number) {
		return RED_NUMBERS.contains(number);
	}

	public static boolean isBlack(int number) {
		return number > 0 && !RED_NUMBERS.contains(number);
	}

	public static int findWheelIndex(int number) {
		for (int i = 0; i < WHEEL_ORDER.length; i++) {
			if (WHEEL_ORDER[i] == number) {
				return i;
			}
		}
		return 0;
	}

	public static boolean hasPendingSpin(UUID uuid) {
		for (PendingResolution p : PENDING) {
			if (p.playerUuid.equals(uuid)) {
				return true;
			}
		}
		return false;
	}

	public static void handlePlayCoinflip(ServerPlayerEntity player, long bet, boolean betHeads) {
		if (bet <= 0L || hasPendingSpin(player.getUuid())) {
			return;
		}
		MinecraftServer server = player.getServer();
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		if (acc.balance < bet) {
			player.sendMessage(Text.literal("[Монетка]: Недостаточно средств на счету!").formatted(Formatting.RED), false);
			return;
		}

		acc.balance -= bet;
		state.markDirty();
		ModServerNetworking.syncAccount(player);

		int headsChance = Math.clamp(state.getOddsConfig().coinHeadsChance, 0, 100);
		boolean landedHeads = player.getServerWorld().getRandom().nextInt(100) < headsChance;
		boolean won = (landedHeads == betHeads);
		long payout = won ? Math.min(Long.MAX_VALUE / 4L, bet * 2L) : 0L;

		String sideText = landedHeads ? "ОРЁЛ" : "РЕШКА";
		Text msg = won
				? Text.literal("[Монетка] Выпал " + sideText + "! Вы выиграли +" + payout + " ₽!").formatted(Formatting.GREEN, Formatting.BOLD)
				: Text.literal("[Монетка] Выпал " + sideText + ". Ставка " + bet + " ₽ проиграна.").formatted(Formatting.RED);

		PENDING.add(new PendingResolution(player.getUuid(), COINFLIP_ANIM_TICKS, payout, msg, won));
		ServerPlayNetworking.send(
				player,
				new ModPayloads.CoinflipResultPayload(landedHeads, won, payout, COINFLIP_ANIM_TICKS)
		);
	}

	public static void handlePlayRoulette(ServerPlayerEntity player, long bet, int betTypeOrd, int chosenNumber) {
		if (bet <= 0L || hasPendingSpin(player.getUuid())) {
			return;
		}
		MinecraftServer server = player.getServer();
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		if (acc.balance < bet) {
			player.sendMessage(Text.literal("[Рулетка]: Недостаточно средств на счету!").formatted(Formatting.RED), false);
			return;
		}

		RouletteBetType betType = RouletteBetType.fromOrdinal(betTypeOrd);
		int safeTargetNum = Math.clamp(chosenNumber, 0, 36);

		acc.balance -= bet;

		Random rng = player.getServerWorld().getRandom();
		int winningNumber;

		// Если в прошлый раз выпало зелёное (0), то второй раз подряд выпадает ЗЕЛЁНОЕ со 100% вероятностью!
		if (acc.rouletteForceNextGreen) {
			winningNumber = 0;
			acc.rouletteForceNextGreen = false;
		} else {
			ModPersistentState.MachineOddsConfig odds = state.getOddsConfig();
			int wGreen = Math.max(0, odds.rouletteGreenChance);
			int wRed = Math.max(0, odds.rouletteRedChance);
			int wBlack = Math.max(0, odds.rouletteBlackChance);
			int totalWeight = wGreen + wRed + wBlack;

			if (totalWeight <= 0) {
				winningNumber = WHEEL_ORDER[rng.nextInt(WHEEL_ORDER.length)];
			} else {
				int pick = rng.nextInt(totalWeight);
				if (pick < wGreen) {
					winningNumber = 0;
				} else if (pick < wGreen + wRed) {
					winningNumber = RED_ARRAY[rng.nextInt(RED_ARRAY.length)];
				} else {
					winningNumber = BLACK_ARRAY[rng.nextInt(BLACK_ARRAY.length)];
				}
			}

			// Если выпало зелёное впервые — заряжаем 100% зелёное на следующий спин!
			if (winningNumber == 0) {
				acc.rouletteForceNextGreen = true;
			}
		}

		state.markDirty();
		ModServerNetworking.syncAccount(player);

		boolean won = evaluateRouletteBet(betType, safeTargetNum, winningNumber);
		long payout = won ? Math.min(Long.MAX_VALUE / 4L, bet * (long) betType.getMultiplier()) : 0L;

		String colorName = winningNumber == 0 ? "ЗЕЛЁНОЕ" : (isRed(winningNumber) ? "КРАСНОЕ" : "ЧЁРНОЕ");
		Formatting colorFmt = winningNumber == 0 ? Formatting.GREEN : (isRed(winningNumber) ? Formatting.RED : Formatting.GRAY);

		Text msg = Text.literal("[Рулетка] Выпало: ")
				.formatted(Formatting.GOLD)
				.append(Text.literal(winningNumber + " (" + colorName + ")").formatted(colorFmt, Formatting.BOLD))
				.append(
						won
								? Text.literal(" — ВЫИГРЫШ +" + payout + " ₽!").formatted(Formatting.GREEN, Formatting.BOLD)
								: Text.literal(" — мимо.").formatted(Formatting.RED)
				);

		PENDING.add(new PendingResolution(player.getUuid(), ROULETTE_ANIM_TICKS, payout, msg, won));
		ServerPlayNetworking.send(
				player,
				new ModPayloads.RouletteResultPayload(winningNumber, won, payout, ROULETTE_ANIM_TICKS)
		);
	}

	public static boolean evaluateRouletteBet(RouletteBetType type, int targetNumber, int resultNumber) {
		return switch (type) {
			case RED -> isRed(resultNumber);
			case BLACK -> isBlack(resultNumber);
			case GREEN -> resultNumber == 0;
			case EVEN -> resultNumber > 0 && (resultNumber % 2 == 0);
			case ODD -> resultNumber > 0 && (resultNumber % 2 == 1);
			case LOW_1_18 -> resultNumber >= 1 && resultNumber <= 18;
			case HIGH_19_36 -> resultNumber >= 19 && resultNumber <= 36;
			case DOZEN_1 -> resultNumber >= 1 && resultNumber <= 12;
			case DOZEN_2 -> resultNumber >= 13 && resultNumber <= 24;
			case DOZEN_3 -> resultNumber >= 25 && resultNumber <= 36;
			case STRAIGHT -> resultNumber == targetNumber;
		};
	}

	public static void tickPendingResolutions(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		Iterator<PendingResolution> it = PENDING.iterator();
		while (it.hasNext()) {
			PendingResolution p = it.next();
			p.ticksLeft--;
			ServerPlayerEntity online = server.getPlayerManager().getPlayer(p.playerUuid);
			if (p.ticksLeft <= 0 || online == null) {
				if (p.payout > 0L) {
					ModPersistentState.PlayerFinanceData acc = state.getAccount(p.playerUuid);
					acc.balance = Math.min(Long.MAX_VALUE / 4L, acc.balance + p.payout);
					state.markDirty();
				}
				if (online != null) {
					ModServerNetworking.syncAccount(online);
					online.sendMessage(p.resultMessage, false);
					online.getServerWorld().playSound(
							null,
							online.getBlockPos(),
							p.won ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
							SoundCategory.PLAYERS,
							0.9F,
							p.won ? 1.2F : 0.75F
					);
				}
				it.remove();
			}
		}
	}
}
