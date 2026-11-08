package com.novaimod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.economy.ItemPricingRegistry;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.equipment.MfoArmorSetManager;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.ShearsItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

/**
 * Управляет:
 * 1) Ежедневным скрытым сложным процентом 20% по кредитам МФО.
 * 2) Приходом личностей (Володя -> Коллекторы -> Бригада армян -> Мастера спорта -> ОМОН)
 *    в зависимости от терпения банка, суммы долга и действий игрока.
 * 3) Обезьянником (неприятной землянкой) и СИЗО на 10 минут.
 */
public final class DetentionManager {
	public static final int SIZO_DURATION_TICKS = 20 * 60 * 10; // 10 реальных минут = 12000 тиков
	private static final int FAST_FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private DetentionManager() {
	}

	public static void tickDebtAndDetentions(MinecraftServer server) {
		ServerWorld overworld = server.getOverworld();
		long worldTime = overworld.getTime();
		long currentDay = worldTime / 24000L;
		ModPersistentState state = ModPersistentState.get(server);

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());

			// 1. Обработка СИЗО (10 минут)
			if (acc.sizoRemainingTicks > 0) {
				tickSizoPlayer(server, overworld, player, acc, state);
				continue;
			}

			// 2. Обработка Обезьянника (неприятной землянки с бригадой армян)
			if (acc.inMonkeyCage) {
				tickMonkeyCagePlayer(server, overworld, player, acc, state);
				continue;
			}

			// 3. Ежедневный скрытый сложный процент (20% в день) — нигде не объявляется игроку в чате!
			if (acc.debt > 0L) {
				if (acc.lastInterestDay < 0L) {
					acc.lastInterestDay = currentDay;
					state.markDirty();
				} else if (currentDay > acc.lastInterestDay) {
					long daysPassed = currentDay - acc.lastInterestDay;
					acc.lastInterestDay = currentDay;
					for (long d = 0; d < daysPassed; d++) {
						long interest = Math.max(1L, Math.round(acc.debt * acc.hiddenDailyRate));
						acc.debt = Math.min(Long.MAX_VALUE / 4L, acc.debt + interest);
						acc.mfoLockedHalf = Math.max(acc.mfoLockedHalf, Math.max(1L, (acc.debt + 1L) / 2L));
						acc.daysOverdue++;
						int patienceDrop = 15 + (acc.debt >= 5000L ? 15 : (acc.debt >= 1000L ? 8 : 0));
						acc.bankPatience = Math.max(0, acc.bankPatience - patienceDrop);
					}
					if (acc.bankPatience <= 30 && acc.enforcementStage < 2) {
						acc.enforcementStage = 2;
					} else if (acc.bankPatience <= 65 && acc.enforcementStage < 1) {
						acc.enforcementStage = 1;
					}
					state.markDirty();
					ModServerNetworking.syncAccount(player);
				}
			}

			// 4. Запланированный приезд ОМОНа через 1 день после убийства Мастера спорта
			if (acc.omonScheduledTick > 0L && worldTime >= acc.omonScheduledTick) {
				acc.omonScheduledTick = -1L;
				state.markDirty();
				spawnOmonSquad(player);
				continue;
			}

			// 5. Запланированный приезд Бригады армян через 1 день после убийства Коллектора
			if (acc.brigadaScheduledTick > 0L && worldTime >= acc.brigadaScheduledTick) {
				acc.brigadaScheduledTick = -1L;
				state.markDirty();
				spawnArmenianBrigade(player);
				continue;
			}

			// 6. Обычные визиты (Володя -> Коллекторы -> Мастера спорта) по сумме долга и терпению банка
			if (acc.debt > 0L && acc.nextVisitTick > 0L && worldTime >= acc.nextVisitTick) {
				// Не спавним поверх уже активных взыскателей рядом
				if (!hasActiveEnforcersNear(player)) {
					spawnScheduledDebtVisitor(player, acc);
				}
				// Следующий визит через 1 игровой день (или быстрее при огромном долге)
				long interval = acc.debt >= 10000L ? 16000L : 24000L;
				acc.nextVisitTick = worldTime + interval;
				state.markDirty();
			}
		}
	}

	private static boolean hasActiveEnforcersNear(ServerPlayerEntity player) {
		Box box = player.getBoundingBox().expand(36.0D);
		List<ModNpcEntity> nearby = player.getServerWorld().getEntitiesByClass(
				ModNpcEntity.class,
				box,
				npc -> npc.isAlive() && !npc.getNpcRole().isStationaryShopNpc()
		);
		return !nearby.isEmpty();
	}

	private static void spawnScheduledDebtVisitor(ServerPlayerEntity player, ModPersistentState.PlayerFinanceData acc) {
		// В первый раз (или пока терпение банка высокое) всегда через день приходит Володя
		if (acc.enforcementStage <= 0 && acc.bankPatience > 65) {
			ModNpcEntity volodya = spawnNearPlayer(player, NpcRole.VOLODYA, 10, 14);
			if (volodya != null) {
				player.sendMessage(
						Text.literal("[Володя]: Привет, дружище! Слушай, по-хорошему прошу: за тобой должок " + acc.debt + " ₽. Верни в МФО, не доводи банк до крайностей.")
								.formatted(Formatting.YELLOW),
						false
				);
			}
			// Постепенно терпение снижается, если долг продолжает висеть
			acc.bankPatience = Math.max(0, acc.bankPatience - 20);
			if (acc.bankPatience <= 65) {
				acc.enforcementStage = 1;
			}
		} else if (acc.enforcementStage == 1 && acc.bankPatience > 25) {
			// Коллекторы (уровень кольчуги и каменного меча, но ни броня, ни меч не отображаются)
			int count = acc.debt >= 3000L ? 3 : 2;
			for (int i = 0; i < count; i++) {
				spawnNearPlayer(player, NpcRole.COLLECTOR, 9, 13);
			}
			player.sendMessage(
					Text.literal("[Коллекторы]: Ну здравствуй, должник! Хорошие разговоры кончились — пора возвращать " + acc.debt + " ₽!")
							.formatted(Formatting.GOLD, Formatting.BOLD),
					false
			);
			acc.bankPatience = Math.max(0, acc.bankPatience - 20);
			if (acc.bankPatience <= 25) {
				acc.enforcementStage = 2;
			}
		} else {
			// Мастера спорта (уровень железной брони и железного меча на Остроту I, без отображения брони и меча)
			int count = acc.debt >= 8000L ? 3 : 2;
			for (int i = 0; i < count; i++) {
				spawnNearPlayer(player, NpcRole.MASTER_OF_SPORTS, 8, 12);
			}
			player.sendMessage(
					Text.literal("[Мастера спорта]: Банк больше ждать не намерен! Разминай челюсть, забираем половину имущества в счёт долга!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
		}
	}

	public static void spawnArmenianBrigade(ServerPlayerEntity player) {
		for (int i = 0; i < 3; i++) {
			spawnNearPlayer(player, NpcRole.ARMENIAN_BRIGADE, 5, 9);
		}
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PILLAGER_AMBIENT, SoundCategory.HOSTILE, 1.0F, 0.85F);
		player.sendMessage(
				Text.literal("[Бригада армян]: Вах, дорогой, ты зачем нашего человека тронул?! Пакуем его в обезьянник, ребята!")
						.formatted(Formatting.DARK_RED, Formatting.BOLD),
				false
		);
	}

	public static void spawnOmonSquad(ServerPlayerEntity player) {
		for (int i = 0; i < 4; i++) {
			spawnNearPlayer(player, NpcRole.OMON, 6, 10);
		}
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 1.2F, 1.1F);
		player.getServer().getPlayerManager().broadcast(
				Text.literal("[ОМОН]: ВСЕМ ОСТАВАТЬСЯ НА МЕСТАХ! РАБОТАЕТ ОМОН!")
						.formatted(Formatting.RED, Formatting.BOLD),
				false
		);
	}

	private static ModNpcEntity spawnNearPlayer(ServerPlayerEntity player, NpcRole role, int minRadius, int maxRadius) {
		ServerWorld world = player.getServerWorld();
		Random random = world.getRandom();
		BlockPos base = player.getBlockPos();

		BlockPos spawnPos = base;
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0D;
			double dist = minRadius + random.nextDouble() * (maxRadius - minRadius);
			int x = MathHelper.floor(player.getX() + Math.cos(angle) * dist);
			int z = MathHelper.floor(player.getZ() + Math.sin(angle) * dist);
			int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - base.getY()) > 14) {
				y = base.getY();
			}
			BlockPos candidate = new BlockPos(x, y, z);
			if (world.getBlockState(candidate).isAir() && world.getBlockState(candidate.up()).isAir()) {
				spawnPos = candidate;
				break;
			}
		}

		ModNpcEntity npc = ModEntities.MOD_NPC.create(world);
		if (npc == null) {
			return null;
		}
		npc.refreshPositionAndAngles(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
		npc.configureRole(role, "");
		npc.setTargetPlayerUuid(player.getUuid());
		world.spawnEntity(npc);
		return npc;
	}

	/**
	 * Скручивает виновного и сажает в обезьянник на Уровне -1 измерения «Бюрократический Ад» вместе с бригадой
	 * (в обычном мире тюрячки больше НЕ спавнятся!).
	 */
	public static void captureIntoMonkeyCage(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		ModPersistentState state = ModPersistentState.get(server);
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		if (acc.inMonkeyCage) {
			return;
		}

		ServerWorld bureauWorld = BureaucraticHellManager.ensureComplexBuilt(server);
		BlockPos cageCenter = BureaucraticHellManager.MONKEY_CAGE_POS;

		acc.preCagePos = player.getBlockPos();
		acc.preCageDim = player.getServerWorld().getRegistryKey().getValue().toString();
		acc.monkeyCagePos = cageCenter;
		acc.inMonkeyCage = true;
		acc.monkeyCageTimer = 0;
		state.markDirty();

		// Удаляем уличных членов бригады и ставим 3 бойцов прямо в карцер на -1 этаже Бюрократического Ада
		Box search = player.getBoundingBox().expand(32.0D);
		for (ModNpcEntity existing : player.getServerWorld().getEntitiesByClass(
				ModNpcEntity.class,
				search,
				e -> e.getNpcRole() == NpcRole.ARMENIAN_BRIGADE && !"cage".equals(e.getVariant())
		)) {
			existing.discard();
		}

		player.teleport(bureauWorld, cageCenter.getX() + 0.5D, cageCenter.getY(), cageCenter.getZ() + 0.5D, 90.0F, 0.0F);
		for (int i = -1; i <= 1; i++) {
			ModNpcEntity member = ModEntities.MOD_NPC.create(bureauWorld);
			if (member != null) {
				member.refreshPositionAndAngles(cageCenter.getX() + 0.5D, cageCenter.getY(), cageCenter.getZ() + 0.5D + i * 1.1D, -90.0F, 0.0F);
				member.configureRole(NpcRole.ARMENIAN_BRIGADE, "cage");
				member.setTargetPlayerUuid(player.getUuid());
				bureauWorld.spawnEntity(member);
			}
		}

		player.sendMessage(
				Text.literal("[Бригада армян]: Добро пожаловать на -1 этаж Бюрократического Ада — в наш ведомственный обезьянник, уважаемый! Сейчас проведём воспитательную беседу!")
						.formatted(Formatting.DARK_RED, Formatting.BOLD),
				false
		);
	}

	private static void tickMonkeyCagePlayer(
			MinecraftServer server,
			ServerWorld overworld,
			ServerPlayerEntity player,
			ModPersistentState.PlayerFinanceData acc,
			ModPersistentState state
	) {
		acc.monkeyCageTimer++;
		ServerWorld bureauWorld = BureaucraticHellManager.ensureComplexBuilt(server);
		BlockPos cagePos = acc.monkeyCagePos != null ? acc.monkeyCagePos : BureaucraticHellManager.MONKEY_CAGE_POS;

		// Удерживаем игрока внутри карцера-обезьянника на Уровне -1 Бюрократического Ада
		if (player.getServerWorld() != bureauWorld || player.getBlockPos().getSquaredDistance(cagePos) > 12.0D) {
			player.teleport(
					bureauWorld,
					cagePos.getX() + 0.5D,
					cagePos.getY(),
					cagePos.getZ() + 0.5D,
					player.getYaw(),
					player.getPitch()
			);
		}

		// Если игрок берёт инструмент/оружие в руку — бригада огрызается и выбивает из рук (игрок теряет его навсегда!)
		confiscateHeldToolIfPresent(player, Hand.MAIN_HAND);
		confiscateHeldToolIfPresent(player, Hand.OFF_HAND);

		// Гарантируем, что ХП в обезьяннике НИКОГДА не падает ниже 1.5 сердечка (3.0 HP)
		if (player.getHealth() < 3.0F) {
			player.setHealth(3.0F);
		}

		// Проверяем условие окончания избиения: у всей надетой брони осталось менее 10% прочности
		// (либо, если брони нет совсем, выдерживаем минимум 240 тиков = 12 секунд воспитательной порки)
		boolean hasAnyIntactArmor = false;
		boolean wearingAnyArmor = false;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack armor = player.getEquippedStack(slot);
			if (!armor.isEmpty() && armor.isDamageable() && armor.getMaxDamage() > 0) {
				wearingAnyArmor = true;
				int remaining = armor.getMaxDamage() - armor.getDamage();
				double ratio = (double) remaining / (double) armor.getMaxDamage();
				if (ratio >= 0.10D) {
					hasAnyIntactArmor = true;
				}
			}
		}

		boolean finished = wearingAnyArmor ? (!hasAnyIntactArmor && acc.monkeyCageTimer >= 100) : (acc.monkeyCageTimer >= 260);
		if (acc.monkeyCageTimer >= 1200) {
			finished = true; // Защита от бесконечного застревания
		}

		if (finished) {
			acc.inMonkeyCage = false;
			Box cageBox = new Box(cagePos).expand(8.0D);
			for (ModNpcEntity npc : bureauWorld.getEntitiesByClass(ModNpcEntity.class, cageBox, e -> e.getNpcRole() == NpcRole.ARMENIAN_BRIGADE && "cage".equals(e.getVariant()))) {
				npc.discard();
			}
			if (acc.preCagePos != null) {
				ServerWorld retWorld = MysterySkyManager.resolveWorld(server, acc.preCageDim);
				player.teleport(retWorld, acc.preCagePos.getX() + 0.5D, acc.preCagePos.getY(), acc.preCagePos.getZ() + 0.5D, player.getYaw(), player.getPitch());
			}
			acc.enforcementStage = Math.max(acc.enforcementStage, 2); // Если не помогло — дальше придут Мастера спорта!
			state.markDirty();
			player.sendMessage(
					Text.literal("[Бригада армян]: Всё, иди отсюда и долг закрой, иначе за тобой придут люди ещё серьёзнее!")
							.formatted(Formatting.GOLD, Formatting.BOLD),
					false
			);
		}
	}

	private static void confiscateHeldToolIfPresent(ServerPlayerEntity player, Hand hand) {
		ItemStack held = player.getStackInHand(hand);
		if (held.isEmpty()
				|| held.isOf(ModItems.UBER_GUN)
				|| held.isOf(ModItems.UNKNOWN_ARTIFACT)
				|| MfoArmorSetManager.isMfoBow(held)) {
			return;
		}
		if (isToolOrWeapon(held.getItem())) {
			player.setStackInHand(hand, ItemStack.EMPTY);
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.HOSTILE, 1.0F, 0.9F);
			player.sendMessage(
					Text.literal("[Бригада армян]: Э! Руки убери, дорогой! Какой ещё инструмент в обезьяннике?! Выбили и забрали!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
		}
	}

	private static boolean isToolOrWeapon(Item item) {
		return item instanceof MiningToolItem
				|| item instanceof SwordItem
				|| item instanceof TridentItem
				|| item instanceof MaceItem
				|| item instanceof ShearsItem
				|| item instanceof BowItem
				|| item instanceof CrossbowItem
				|| item instanceof FlintAndSteelItem;
	}

	/**
	 * Списание долга вне здания микрозайма (коллекторами, мастерами спорта и др.).
	 * Полностью погасить займ, не приходя в микрозайм, НЕВОЗМОЖНО:
	 * как минимум половина долга (mfoLockedHalf >= 50%) ВСЕГДА остаётся за игроком!
	 */
	public static long applyEnforcerDebtReduction(ModPersistentState.PlayerFinanceData acc, long seizedAmount) {
		if (acc.debt <= 0L || seizedAmount <= 0L) {
			return 0L;
		}
		long minRemainingDebt = Math.max(1L, Math.max(acc.mfoLockedHalf, (acc.debt + 1L) / 2L));
		acc.mfoLockedHalf = minRemainingDebt;
		if (acc.debt <= minRemainingDebt) {
			return 0L;
		}
		long maxAllowedReduction = acc.debt - minRemainingDebt;
		long credited = Math.min(seizedAmount, maxAllowedReduction);
		acc.debt -= credited;
		return credited;
	}

	/**
	 * Мастера спорта отбирают половину вещей из инвентаря в счёт долга, когда у игрока остаётся <= 2 сердечка (4.0 HP).
	 * Но даже при любой стоимости вещей они НЕ могут погасить весь займ — минимум половина долга всегда остаётся за игроком,
	 * пока он лично не придёт к Кредитору в здание МФО!
	 */
	public static void seizeHalfInventoryTowardDebt(ServerPlayerEntity player) {
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());

		List<Integer> occupiedSlots = new ArrayList<>();
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			ItemStack st = player.getInventory().main.get(i);
			if (!st.isEmpty()
					&& !st.isOf(ModItems.PLAYER_TELEPORTER)
					&& !st.isOf(ModItems.REVIVAL_TOKEN)
					&& !st.isOf(ModItems.UBER_GUN)
					&& !st.isOf(ModItems.UNKNOWN_ARTIFACT)
					&& !MfoArmorSetManager.isMfoBow(st)) {
				occupiedSlots.add(i);
			}
		}

		long totalSeizedValue = 0L;
		int takenItemsCount = 0;
		// Отбираем половину занятых слотов / половину стаков
		for (int idx = 0; idx < occupiedSlots.size(); idx++) {
			int slot = occupiedSlots.get(idx);
			ItemStack stack = player.getInventory().main.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getCount() >= 2) {
				int takeCount = (stack.getCount() + 1) / 2;
				ItemStack taken = stack.split(takeCount);
				totalSeizedValue += ItemPricingRegistry.getStackPrice(taken);
				takenItemsCount += takeCount;
			} else if (idx % 2 == 0) {
				totalSeizedValue += ItemPricingRegistry.getStackPrice(stack);
				takenItemsCount += stack.getCount();
				player.getInventory().main.set(slot, ItemStack.EMPTY);
			}
		}

		long credited = applyEnforcerDebtReduction(acc, totalSeizedValue);
		state.markDirty();
		ModServerNetworking.syncAccount(player);

		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.HOSTILE, 1.0F, 0.9F);
		if (credited < totalSeizedValue || acc.debt <= acc.mfoLockedHalf) {
			player.sendMessage(
					Text.literal("[Мастера спорта]: Изъята половина инвентаря (" + takenItemsCount + " предм., зачтено " + credited + " ₽), но полностью закрыть займ через нас нельзя! Половина долга (" + acc.debt + " ₽) остаётся за тобой — топай лично в микрозайм к Кредитору!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
		} else {
			player.sendMessage(
					Text.literal("[Мастера спорта]: Изъята половина инвентаря (" + takenItemsCount + " предм.) на сумму " + credited + " ₽ в счёт долга! Остаток долга: " + acc.debt + " ₽ (полное погашение только в МФО).")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
		}
	}

	/**
	 * ОМОН при одном ударе вяжет ВСЕХ игроков на сервере в СИЗО на Уровне -1 «Бюрократического Ада» на 10 минут
	 * (в обычном мире тюрячки больше НЕ создаются!).
	 */
	public static void arrestEveryoneIntoSizo(ServerPlayerEntity hitTarget) {
		MinecraftServer server = hitTarget.getServer();
		ServerWorld bureauWorld = BureaucraticHellManager.ensureComplexBuilt(server);
		ModPersistentState state = ModPersistentState.get(server);
		BlockPos sizoCenter = BureaucraticHellManager.SIZO_CELL_POS;

		// Убираем выездной отряд ОМОНа после задержания
		Box box = hitTarget.getBoundingBox().expand(48.0D);
		for (ModNpcEntity omon : hitTarget.getServerWorld().getEntitiesByClass(
				ModNpcEntity.class,
				box,
				e -> e.getNpcRole() == NpcRole.OMON && !"bureau_guard".equals(e.getVariant())
		)) {
			omon.discard();
		}

		int arrestedCount = 0;
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (!p.isAlive() || p.isSpectator()) {
				continue;
			}
			if (BureaucraticHellManager.hasDiplomaticSealImmunity(p)) {
				p.sendMessage(
						Text.literal("[ОМОН]: Виноваты, товарищ с Генеральной Печатью Бюрократа! Дипломатическая неприкосновенность — вас не задерживаем!")
								.formatted(Formatting.GOLD, Formatting.BOLD),
						false
				);
				continue;
			}
			ModPersistentState.PlayerFinanceData acc = state.getAccount(p.getUuid());
			if (acc.sizoRemainingTicks <= 0) {
				acc.preSizoPos = p.getBlockPos();
				acc.preSizoDim = p.getServerWorld().getRegistryKey().getValue().toString();
			}
			acc.sizoCellPos = sizoCenter;
			acc.sizoRemainingTicks = SIZO_DURATION_TICKS;
			state.markDirty();

			p.teleport(bureauWorld, sizoCenter.getX() + 0.5D, sizoCenter.getY(), sizoCenter.getZ() + 0.5D, -90.0F, 0.0F);
			ModServerNetworking.syncAccount(p);
			arrestedCount++;
		}

		if (arrestedCount > 0) {
			bureauWorld.playSound(null, sizoCenter, SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.HOSTILE, 1.2F, 0.8F);
			server.getPlayerManager().broadcast(
					Text.literal("[ОМОН]: Задержанные доставлены в СИЗО на Уровень -1 измерения «Бюрократический Ад» на 10 минут!")
							.formatted(Formatting.DARK_RED, Formatting.BOLD),
					false
			);
		}
	}

	private static void tickSizoPlayer(
			MinecraftServer server,
			ServerWorld overworld,
			ServerPlayerEntity player,
			ModPersistentState.PlayerFinanceData acc,
			ModPersistentState state
	) {
		acc.sizoRemainingTicks--;
		ServerWorld bureauWorld = BureaucraticHellManager.ensureComplexBuilt(server);
		BlockPos cellPos = BureaucraticHellManager.SIZO_CELL_POS;

		if (player.getServerWorld() != bureauWorld || player.getBlockPos().getSquaredDistance(cellPos) > 14.0D) {
			player.teleport(
					bureauWorld,
					cellPos.getX() + 0.5D,
					cellPos.getY(),
					cellPos.getZ() + 0.5D,
					player.getYaw(),
					player.getPitch()
			);
		}

		if (acc.sizoRemainingTicks % 20 == 0) {
			ModServerNetworking.syncAccount(player);
		}

		if (acc.sizoRemainingTicks <= 0) {
			acc.sizoRemainingTicks = 0;
			if (acc.preSizoPos != null) {
				ServerWorld retWorld = MysterySkyManager.resolveWorld(server, acc.preSizoDim);
				player.teleport(retWorld, acc.preSizoPos.getX() + 0.5D, acc.preSizoPos.getY(), acc.preSizoPos.getZ() + 0.5D, player.getYaw(), player.getPitch());
			}
			boolean awardKey = !acc.bureaucracyKeyEarned;
			acc.bureaucracyKeyEarned = true;
			state.markDirty();
			ModServerNetworking.syncAccount(player);
			player.sendMessage(
					Text.literal("[ОМОН]: Срок заключения в СИЗО (10 минут) истёк. Вы свободны!").formatted(Formatting.GREEN, Formatting.BOLD),
					false
			);
			if (awardKey) {
				BureaucraticHellManager.giveBureaucracyKey(
						player,
						"🔑 [Министерство]: Вы отбыли срок в подвальной тюрячке до звонка и получили легальный «Ключ Бюрократии»! Кликните им ПКМ по любой двери в мире, чтобы посещать Бюрократический Ад!"
				);
			}
		} else if (acc.sizoRemainingTicks % 200 == 0) {
			state.markDirty();
		}
	}

	public static boolean isPlayerRestrictedFromBreaking(Entity entity) {
		if (!(entity instanceof ServerPlayerEntity player) || player.getServer() == null) {
			return false;
		}
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		return acc.sizoRemainingTicks > 0 || acc.inMonkeyCage || state.getActiveSkyArenas().containsKey(player.getUuid());
	}
}
