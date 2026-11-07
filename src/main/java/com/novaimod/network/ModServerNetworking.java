package com.novaimod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.novaimod.block.RepairStationBlock;
import com.novaimod.data.ModPersistentState;
import com.novaimod.economy.CasinoGamesManager;
import com.novaimod.economy.ShopCatalogs;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.item.PlayerTeleporterItem;
import com.novaimod.registry.ModItems;
import com.novaimod.screen.VendingScreenHandler;
import com.novaimod.world.DetentionManager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

public final class ModServerNetworking {
	private ModServerNetworking() {
	}

	public static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.LoanActionPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			handleLoanAction(player, payload.borrow(), payload.amount());
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.ShopBuyPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			handleShopPurchase(player, payload.shopKey(), payload.offerId());
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.RepairToolPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			RepairStationBlock.repairHeldItem(player);
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.VendingSellAllPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			if (player.currentScreenHandler instanceof VendingScreenHandler vendingHandler) {
				vendingHandler.sellAllItems(player);
			}
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.RevivePlayerPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			handleRevivePlayer(player, payload.targetUuid());
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.PlayCoinflipPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			CasinoGamesManager.handlePlayCoinflip(player, payload.bet(), payload.betHeads());
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.PlayRoulettePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			CasinoGamesManager.handlePlayRoulette(player, payload.bet(), payload.betTypeOrd(), payload.chosenNumber());
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.SaveOddsConfigPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			handleSaveOddsConfig(player, payload);
		});

		ServerPlayNetworking.registerGlobalReceiver(ModPayloads.UseTeleporterPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			handleUseTeleporter(player, payload.targetUuid(), payload.bringTargetToMe());
		});
	}

	public static void openCoinflipScreen(ServerPlayerEntity player) {
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		syncAccount(player);
		ServerPlayNetworking.send(player, new ModPayloads.OpenCoinflipScreenPayload(acc.balance));
	}

	public static void openRouletteScreen(ServerPlayerEntity player) {
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		syncAccount(player);
		ServerPlayNetworking.send(player, new ModPayloads.OpenRouletteScreenPayload(acc.balance));
	}

	public static void openOddsConfigScreen(ServerPlayerEntity player) {
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.MachineOddsConfig cfg = state.getOddsConfig();
		ServerPlayNetworking.send(
				player,
				new ModPayloads.OpenOddsConfigScreenPayload(
						cfg.fateTokenChance,
						cfg.fateNothingChance,
						cfg.fateDeathChance,
						cfg.coinHeadsChance,
						cfg.rouletteGreenChance,
						cfg.rouletteRedChance,
						cfg.rouletteBlackChance
				)
		);
	}

	private static void handleSaveOddsConfig(ServerPlayerEntity player, ModPayloads.SaveOddsConfigPayload payload) {
		if (!player.isCreative() && !player.hasPermissionLevel(2)) {
			player.sendMessage(Text.literal("Настройка шансов доступна только в творческом режиме!").formatted(Formatting.RED), false);
			return;
		}
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.MachineOddsConfig cfg = state.getOddsConfig();
		cfg.fateTokenChance = Math.clamp(payload.fateTokenChance(), 0, 100);
		cfg.fateNothingChance = Math.clamp(payload.fateNothingChance(), 0, 100);
		cfg.fateDeathChance = Math.clamp(payload.fateDeathChance(), 0, 100);
		cfg.coinHeadsChance = Math.clamp(payload.coinHeadsChance(), 0, 100);
		cfg.rouletteGreenChance = Math.clamp(payload.rouletteGreenChance(), 0, 100);
		cfg.rouletteRedChance = Math.clamp(payload.rouletteRedChance(), 0, 100);
		cfg.rouletteBlackChance = Math.clamp(payload.rouletteBlackChance(), 0, 100);
		state.markDirty();

		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0F, 1.3F);
		player.sendMessage(
				Text.literal("[Пульт вероятностей] Шансы всех автоматов и столов казино успешно обновлены!")
						.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
				false
		);
	}

	public static void syncAccount(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		int sizoSec = (acc.sizoRemainingTicks + 19) / 20;
		ServerPlayNetworking.send(player, new ModPayloads.AccountSyncPayload(acc.balance, acc.debt, sizoSec));
	}

	public static void openLoanScreen(ServerPlayerEntity player) {
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		ServerPlayNetworking.send(player, new ModPayloads.OpenLoanScreenPayload(acc.balance, acc.debt));
	}

	public static void openShopScreen(ServerPlayerEntity player, String title, String shopKey) {
		List<ShopCatalogs.OfferEntry> catalog = ShopCatalogs.buildCatalog(player.getServer(), shopKey);
		List<ModPayloads.ShopOfferNet> netOffers = new ArrayList<>(catalog.size());
		for (ShopCatalogs.OfferEntry e : catalog) {
			netOffers.add(new ModPayloads.ShopOfferNet(e.offerId(), e.stack(), e.price(), e.tierLabel(), e.stock()));
		}
		boolean allowRepair = "miner".equals(shopKey);
		boolean limitedElite = shopKey.startsWith("elite_mfo");
		ServerPlayNetworking.send(
				player,
				new ModPayloads.OpenShopScreenPayload(title, shopKey, allowRepair, limitedElite, netOffers)
		);
	}

	public static void openTeleporterScreen(ServerPlayerEntity player) {
		if (DetentionManager.isPlayerRestrictedFromBreaking(player)) {
			player.sendMessage(
					Text.literal("[Телепорт]: Здесь стоят глушилки связи — телепортация недоступна!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
			return;
		}
		MinecraftServer server = player.getServer();
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		syncAccount(player);

		List<ModPayloads.ReviveCandidateNet> targets = new ArrayList<>();
		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			if (other != player && other.isAlive() && !other.isSpectator()) {
				targets.add(new ModPayloads.ReviveCandidateNet(other.getUuid(), other.getName().getString()));
			}
		}
		ServerPlayNetworking.send(player, new ModPayloads.OpenTeleporterScreenPayload(acc.balance, targets));
	}

	public static void openRevivalSelection(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		List<ModPayloads.ReviveCandidateNet> candidates = new ArrayList<>();
		for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
			if (other.isSpectator() || !other.isAlive()) {
				candidates.add(new ModPayloads.ReviveCandidateNet(other.getUuid(), other.getName().getString()));
			}
		}
		if (candidates.isEmpty()) {
			player.sendMessage(
					Text.literal("[Жетон возрождения] Сейчас на сервере нет погибших игроков для возрождения!")
							.formatted(Formatting.YELLOW),
					false
			);
			return;
		}
		ServerPlayNetworking.send(player, new ModPayloads.OpenRevivalScreenPayload(candidates));
	}

	private static boolean isNearMfoLender(ServerPlayerEntity player) {
		List<ModNpcEntity> lenders = player.getServerWorld().getEntitiesByClass(
				ModNpcEntity.class,
				player.getBoundingBox().expand(14.0D),
				npc -> npc.isAlive() && npc.getNpcRole() == NpcRole.MFO_LENDER
		);
		return !lenders.isEmpty();
	}

	private static void handleLoanAction(ServerPlayerEntity player, boolean borrow, long amount) {
		if (amount <= 0L) {
			return;
		}
		// Займ и особенно полное погашение долга доступны ТОЛЬКО лично у Кредитора внутри здания микрозайма!
		if (!isNearMfoLender(player)) {
			player.sendMessage(
					Text.literal("[МФО]: Оформление и погашение займа возможно только лично у Кредитора в здании микрозайма!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
			return;
		}
		// Ограничение от переполнения long
		long safeAmount = Math.min(amount, 1_000_000_000L);
		MinecraftServer server = player.getServer();
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		long currentDay = server.getOverworld().getTime() / 24000L;

		if (borrow) {
			boolean wasDebtFree = acc.debt <= 0L;
			acc.balance = Math.min(Long.MAX_VALUE / 4L, acc.balance + safeAmount);
			acc.debt = Math.min(Long.MAX_VALUE / 4L, acc.debt + safeAmount);
			acc.mfoLockedHalf = Math.max(acc.mfoLockedHalf, Math.max(1L, (acc.debt + 1L) / 2L));
			// Скрытый ежедневный сложный процент 20% запускается без уведомления игрока!
			acc.hiddenDailyRate = 0.20D;
			if (wasDebtFree) {
				acc.lastInterestDay = currentDay;
				acc.daysOverdue = 0;
				acc.bankPatience = 100;
				acc.enforcementStage = 0;
				acc.nextVisitTick = server.getOverworld().getTime() + 24000L; // Через день придёт Володя
			}
			state.markDirty();
			syncAccount(player);
			openLoanScreen(player);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0F, 1.2F);
			player.sendMessage(
					Text.literal("[Кредитор]: Одобрено! На ваш счёт зачислено " + safeAmount + " ₽. Приходите ещё!")
							.formatted(Formatting.GREEN),
					false
			);
		} else {
			if (acc.debt <= 0L) {
				player.sendMessage(Text.literal("[Кредитор]: У вас нет активного долга!").formatted(Formatting.GREEN), false);
				return;
			}
			long pay = Math.min(safeAmount, Math.min(acc.balance, acc.debt));
			if (pay <= 0L) {
				player.sendMessage(Text.literal("[Кредитор]: На счету недостаточно средств для погашения!").formatted(Formatting.RED), false);
				return;
			}
			acc.balance -= pay;
			acc.debt -= pay;
			if (acc.debt <= 0L) {
				acc.debt = 0L;
				acc.mfoLockedHalf = 0L;
				acc.daysOverdue = 0;
				acc.bankPatience = 100;
				acc.enforcementStage = 0;
				acc.nextVisitTick = -1L;
				player.sendMessage(
						Text.literal("[Кредитор]: Поздравляем! Ваш долг полностью погашен!").formatted(Formatting.GOLD, Formatting.BOLD),
						false
				);
			} else {
				acc.mfoLockedHalf = Math.max(1L, (acc.debt + 1L) / 2L);
				acc.bankPatience = Math.min(100, acc.bankPatience + 15);
				player.sendMessage(
						Text.literal("[Кредитор]: Внесено " + pay + " ₽. Остаток долга: " + acc.debt + " ₽.").formatted(Formatting.YELLOW),
						false
				);
			}
			state.markDirty();
			syncAccount(player);
			openLoanScreen(player);
			player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.9F, 1.1F);
		}
	}

	private static void handleShopPurchase(ServerPlayerEntity player, String shopKey, String offerId) {
		MinecraftServer server = player.getServer();
		List<ShopCatalogs.OfferEntry> catalog = ShopCatalogs.buildCatalog(server, shopKey);
		ShopCatalogs.OfferEntry chosen = null;
		for (ShopCatalogs.OfferEntry entry : catalog) {
			if (entry.offerId().equals(offerId)) {
				chosen = entry;
				break;
			}
		}
		if (chosen == null) {
			return;
		}

		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());

		boolean isEliteMfo = shopKey.startsWith("elite_mfo");
		if (isEliteMfo) {
			int currentStock = state.getEliteStock(offerId, chosen.stock());
			if (currentStock <= 0) {
				player.sendMessage(Text.literal("Этот лимитированный товар уже РАСПРОДАН!").formatted(Formatting.RED, Formatting.BOLD), false);
				openShopScreen(player, getShopTitle(shopKey), shopKey);
				return;
			}
		}

		if (acc.balance < chosen.price()) {
			player.sendMessage(
					Text.literal("Недостаточно средств на счету! Нужно " + chosen.price() + " ₽ (у вас " + acc.balance + " ₽).")
							.formatted(Formatting.RED),
					false
			);
			return;
		}

		if (isEliteMfo) {
			if (!state.consumeEliteStock(offerId)) {
				return;
			}
		}

		acc.balance -= chosen.price();
		state.markDirty();

		ItemStack reward = chosen.stack().copy();
		if (!player.getInventory().insertStack(reward)) {
			player.dropItem(reward, false);
		}

		player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.PLAYERS, 0.85F, 1.1F);
		syncAccount(player);
		openShopScreen(player, getShopTitle(shopKey), shopKey);
	}

	public static String getShopTitle(String shopKey) {
		if ("grocery_1".equals(shopKey)) {
			return "Продуктовый — Пекарь и Фермер";
		}
		if ("grocery_2".equals(shopKey)) {
			return "Продуктовый — Мясные деликатесы";
		}
		if ("miner".equals(shopKey)) {
			return "Шахтёрская лавка и Ремонт";
		}
		if ("elite_mfo_1".equals(shopKey) || "elite_mfo".equals(shopKey)) {
			return "Элитный отдел МФО — Снаряжение";
		}
		if ("elite_mfo_2".equals(shopKey)) {
			return "Элитный отдел МФО — Артефакты";
		}
		if (shopKey.startsWith("bartender")) {
			return "Барная стойка Казино";
		}
		return "Магазин";
	}

	private static void handleRevivePlayer(ServerPlayerEntity reviver, UUID targetUuid) {
		ItemStack tokenStack = ItemStack.EMPTY;
		if (reviver.getMainHandStack().isOf(ModItems.REVIVAL_TOKEN)) {
			tokenStack = reviver.getMainHandStack();
		} else if (reviver.getOffHandStack().isOf(ModItems.REVIVAL_TOKEN)) {
			tokenStack = reviver.getOffHandStack();
		}
		if (tokenStack.isEmpty()) {
			reviver.sendMessage(Text.literal("У вас в руке нет Жетона возрождения!").formatted(Formatting.RED), false);
			return;
		}

		MinecraftServer server = reviver.getServer();
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
		if (target == null) {
			reviver.sendMessage(Text.literal("Выбранный игрок не в сети!").formatted(Formatting.RED), false);
			return;
		}
		if (!target.isSpectator() && target.isAlive()) {
			reviver.sendMessage(Text.literal("Этот игрок уже жив!").formatted(Formatting.YELLOW), false);
			return;
		}

		if (!reviver.isInCreativeMode()) {
			tokenStack.decrement(1);
		}

		ServerWorld destWorld = reviver.getServerWorld();
		target.teleport(destWorld, reviver.getX(), reviver.getY(), reviver.getZ(), reviver.getYaw(), reviver.getPitch());
		target.changeGameMode(GameMode.SURVIVAL);
		target.setHealth(target.getMaxHealth());
		target.getHungerManager().setFoodLevel(20);
		target.getHungerManager().setSaturationLevel(10.0F);
		target.clearStatusEffects();
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 300, 0));
		target.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 2));

		destWorld.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, target.getX(), target.getY() + 1.0D, target.getZ(), 60, 0.5D, 0.8D, 0.5D, 0.25D);
		destWorld.playSound(null, target.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0F, 1.0F);

		server.getPlayerManager().broadcast(
				Text.literal("✦ Игрок " + reviver.getName().getString() + " использовал Жетон возрождения и вернул к жизни " + target.getName().getString() + "!")
						.formatted(Formatting.GOLD, Formatting.BOLD),
				false
		);
	}

	private static void handleUseTeleporter(ServerPlayerEntity user, UUID targetUuid, boolean bringTargetToMe) {
		boolean hasTeleporter = user.getMainHandStack().isOf(ModItems.PLAYER_TELEPORTER)
				|| user.getOffHandStack().isOf(ModItems.PLAYER_TELEPORTER);
		if (!hasTeleporter) {
			user.sendMessage(Text.literal("У вас в руке нет Карманного телепорта!").formatted(Formatting.RED), false);
			return;
		}
		if (user.getItemCooldownManager().isCoolingDown(ModItems.PLAYER_TELEPORTER)) {
			return;
		}
		if (DetentionManager.isPlayerRestrictedFromBreaking(user)) {
			user.sendMessage(
					Text.literal("[Телепорт]: Здесь стоят глушилки связи — телепортация заблокирована!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
			return;
		}

		MinecraftServer server = user.getServer();
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
		if (target == null || target == user || !target.isAlive() || target.isSpectator()) {
			user.sendMessage(Text.literal("[Телепорт]: Выбранный игрок сейчас недоступен!").formatted(Formatting.RED), false);
			return;
		}
		if (DetentionManager.isPlayerRestrictedFromBreaking(target)) {
			user.sendMessage(
					Text.literal("[Телепорт]: Игрок " + target.getName().getString() + " находится под стражей (СИЗО / обезьянник) — сигнал заблокирован!")
							.formatted(Formatting.RED),
					false
			);
			return;
		}

		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(user.getUuid());
		long cost = PlayerTeleporterItem.TELEPORT_USE_COST;
		if (acc.balance < cost) {
			user.sendMessage(
					Text.literal("[Телепорт]: Недостаточно средств на счету! Стоимость телепортации: " + cost + " ₽ (у вас " + acc.balance + " ₽).")
							.formatted(Formatting.RED),
					false
			);
			return;
		}

		acc.balance -= cost;
		state.markDirty();
		syncAccount(user);
		user.getItemCooldownManager().set(ModItems.PLAYER_TELEPORTER, 40);

		if (bringTargetToMe) {
			ServerWorld srcWorld = target.getServerWorld();
			srcWorld.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1.0D, target.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.2D);
			srcWorld.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);

			ServerWorld destWorld = user.getServerWorld();
			target.fallDistance = 0.0F;
			target.teleport(destWorld, user.getX(), user.getY(), user.getZ(), user.getYaw(), user.getPitch());
			destWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, user.getX(), user.getY() + 1.0D, user.getZ(), 50, 0.5D, 0.9D, 0.5D, 0.15D);
			destWorld.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.15F);

			user.sendMessage(
					Text.literal("[Телепорт]: Игрок " + target.getName().getString() + " телепортирован к вам! Списано " + cost + " ₽.")
							.formatted(Formatting.AQUA, Formatting.BOLD),
					false
			);
			target.sendMessage(
					Text.literal("[Телепорт]: Игрок " + user.getName().getString() + " телепортировал вас к себе!")
							.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
					false
			);
		} else {
			ServerWorld srcWorld = user.getServerWorld();
			srcWorld.spawnParticles(ParticleTypes.PORTAL, user.getX(), user.getY() + 1.0D, user.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.2D);
			srcWorld.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);

			ServerWorld destWorld = target.getServerWorld();
			user.fallDistance = 0.0F;
			user.teleport(destWorld, target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
			destWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 1.0D, target.getZ(), 50, 0.5D, 0.9D, 0.5D, 0.15D);
			destWorld.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.15F);

			user.sendMessage(
					Text.literal("[Телепорт]: Вы телепортировались к игроку " + target.getName().getString() + "! Списано " + cost + " ₽.")
							.formatted(Formatting.AQUA, Formatting.BOLD),
					false
			);
			target.sendMessage(
					Text.literal("[Телепорт]: Игрок " + user.getName().getString() + " телепортировался к вам!")
							.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
					false
			);
		}
	}
}
