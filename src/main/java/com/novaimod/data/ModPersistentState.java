package com.novaimod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Хранит на сервере:
 * - Виртуальные счета и долги игроков (не предметы, а счёт).
 * - Скрытые данные МФО (20% ежедневный сложный процент, терпение банка, стадии коллекторов, таймеры бригады и ОМОНа).
 * - Состояние обезьянника, СИЗО (10 минут) и активных небесных арен казино.
 * - Ограниченные запасы товаров у элитных продавцов МФО.
 * - Сгенерированные структуры магазинов.
 */
public class ModPersistentState extends PersistentState {
	public static final String STATE_ID = "novaimod_world_state";

	public static class PlayerFinanceData {
		public long balance = 0L;
		public long debt = 0L;
		// Несгораемая половина долга: коллекторы/мастера спорта и др. никогда не могут опустить долг ниже этой планки!
		// Полностью погасить займ можно только лично придя в микрозайм к Кредитору.
		public long mfoLockedHalf = 0L;

		// Скрытые от глаз игрока внутренние параметры банка
		public double hiddenDailyRate = 0.20D;
		public long lastInterestDay = -1L;
		public int daysOverdue = 0;
		public int bankPatience = 100;
		public int enforcementStage = 0; // 0=Володя, 1=Коллекторы, 2=Мастера спорта
		public long nextVisitTick = -1L;
		public long brigadaScheduledTick = -1L;
		public long omonScheduledTick = -1L;
		public long lastGidlerTokenTick = -240000L;

		// Обезьянник (бригада армян)
		public boolean inMonkeyCage = false;
		public BlockPos monkeyCagePos = null;
		public BlockPos preCagePos = null;
		public String preCageDim = "minecraft:overworld";
		public int monkeyCageTimer = 0;

		// СИЗО (ОМОН на 10 минут = 12000 тиков)
		public int sizoRemainingTicks = 0;
		public BlockPos sizoCellPos = null;
		public BlockPos preSizoPos = null;
		public String preSizoDim = "minecraft:overworld";

		// Рулетка: гарантия 100% второго зелёного подряд после первого выпадения зелёного
		public boolean rouletteForceNextGreen = false;

		// Отложенный перенос после наложения Темноты от коктейля «Пьяное забвение»
		public int pendingOblivionTicks = 0;

		// Надет ли единый невидимый предмет Альтимейт-брони («Поджопник») в секретный слот инвентаря
		public boolean seatPadEquipped = false;

		// === v4.0.0: БЮРОКРАТИЧЕСКИЙ АД ===
		public boolean bureaucracyKeyEarned = false;
		public boolean taxKeyIssued = false;
		public long totalLoanRepaid = 0L;
		public boolean hasPreBureauReturn = false;
		public String preBureauDim = "minecraft:overworld";
		public double preBureauX = 0.0D;
		public double preBureauY = 64.0D;
		public double preBureauZ = 0.0D;
		public float preBureauYaw = 0.0F;
		public long lastCafeteriaRationDay = -1L;

		// Плавное развитие мобов (прогресс игрока и учёт смертей от конкретных типов мобов)
		public long ticksLivedInWorld = 0L;
		public int highestGearScore = 0;
		public long lastMercyDecayTick = 0L;
		public final Map<String, Integer> recentMobDeaths = new HashMap<>();

		public NbtCompound toNbt() {
			NbtCompound tag = new NbtCompound();
			tag.putLong("balance", balance);
			tag.putLong("debt", debt);
			tag.putLong("mfoLockedHalf", mfoLockedHalf);
			tag.putDouble("hiddenDailyRate", hiddenDailyRate);
			tag.putLong("lastInterestDay", lastInterestDay);
			tag.putInt("daysOverdue", daysOverdue);
			tag.putInt("bankPatience", bankPatience);
			tag.putInt("enforcementStage", enforcementStage);
			tag.putLong("nextVisitTick", nextVisitTick);
			tag.putLong("brigadaScheduledTick", brigadaScheduledTick);
			tag.putLong("omonScheduledTick", omonScheduledTick);
			tag.putLong("lastGidlerTokenTick", lastGidlerTokenTick);

			tag.putBoolean("inMonkeyCage", inMonkeyCage);
			if (monkeyCagePos != null) {
				tag.putLong("monkeyCagePos", monkeyCagePos.asLong());
			}
			if (preCagePos != null) {
				tag.putLong("preCagePos", preCagePos.asLong());
			}
			tag.putString("preCageDim", preCageDim);
			tag.putInt("monkeyCageTimer", monkeyCageTimer);

			tag.putInt("sizoRemainingTicks", sizoRemainingTicks);
			if (sizoCellPos != null) {
				tag.putLong("sizoCellPos", sizoCellPos.asLong());
			}
			if (preSizoPos != null) {
				tag.putLong("preSizoPos", preSizoPos.asLong());
			}
			tag.putString("preSizoDim", preSizoDim);
			tag.putBoolean("rouletteForceNextGreen", rouletteForceNextGreen);
			tag.putInt("pendingOblivionTicks", pendingOblivionTicks);
			tag.putBoolean("seatPadEquipped", seatPadEquipped);
			tag.putBoolean("bureaucracyKeyEarned", bureaucracyKeyEarned);
			tag.putBoolean("taxKeyIssued", taxKeyIssued);
			tag.putLong("totalLoanRepaid", totalLoanRepaid);
			tag.putBoolean("hasPreBureauReturn", hasPreBureauReturn);
			tag.putString("preBureauDim", preBureauDim);
			tag.putDouble("preBureauX", preBureauX);
			tag.putDouble("preBureauY", preBureauY);
			tag.putDouble("preBureauZ", preBureauZ);
			tag.putFloat("preBureauYaw", preBureauYaw);
			tag.putLong("lastCafeteriaRationDay", lastCafeteriaRationDay);

			tag.putLong("ticksLivedInWorld", ticksLivedInWorld);
			tag.putInt("highestGearScore", highestGearScore);
			tag.putLong("lastMercyDecayTick", lastMercyDecayTick);
			NbtCompound deathsTag = new NbtCompound();
			for (Map.Entry<String, Integer> e : recentMobDeaths.entrySet()) {
				deathsTag.putInt(e.getKey(), e.getValue());
			}
			tag.put("recentMobDeaths", deathsTag);
			return tag;
		}

		public static PlayerFinanceData fromNbt(NbtCompound tag) {
			PlayerFinanceData d = new PlayerFinanceData();
			d.balance = tag.getLong("balance");
			d.debt = tag.getLong("debt");
			d.mfoLockedHalf = tag.contains("mfoLockedHalf")
					? tag.getLong("mfoLockedHalf")
					: (d.debt > 0L ? Math.max(1L, (d.debt + 1L) / 2L) : 0L);
			if (d.debt > 0L && d.mfoLockedHalf <= 0L) {
				d.mfoLockedHalf = Math.max(1L, (d.debt + 1L) / 2L);
			}
			d.hiddenDailyRate = tag.contains("hiddenDailyRate") ? tag.getDouble("hiddenDailyRate") : 0.20D;
			d.lastInterestDay = tag.contains("lastInterestDay") ? tag.getLong("lastInterestDay") : -1L;
			d.daysOverdue = tag.getInt("daysOverdue");
			d.bankPatience = tag.contains("bankPatience") ? tag.getInt("bankPatience") : 100;
			d.enforcementStage = tag.getInt("enforcementStage");
			d.nextVisitTick = tag.contains("nextVisitTick") ? tag.getLong("nextVisitTick") : -1L;
			d.brigadaScheduledTick = tag.contains("brigadaScheduledTick") ? tag.getLong("brigadaScheduledTick") : -1L;
			d.omonScheduledTick = tag.contains("omonScheduledTick") ? tag.getLong("omonScheduledTick") : -1L;
			d.lastGidlerTokenTick = tag.contains("lastGidlerTokenTick") ? tag.getLong("lastGidlerTokenTick") : -240000L;

			d.inMonkeyCage = tag.getBoolean("inMonkeyCage");
			if (tag.contains("monkeyCagePos")) {
				d.monkeyCagePos = BlockPos.fromLong(tag.getLong("monkeyCagePos"));
			}
			if (tag.contains("preCagePos")) {
				d.preCagePos = BlockPos.fromLong(tag.getLong("preCagePos"));
			}
			if (tag.contains("preCageDim")) {
				d.preCageDim = tag.getString("preCageDim");
			}
			d.monkeyCageTimer = tag.getInt("monkeyCageTimer");

			d.sizoRemainingTicks = tag.getInt("sizoRemainingTicks");
			if (tag.contains("sizoCellPos")) {
				d.sizoCellPos = BlockPos.fromLong(tag.getLong("sizoCellPos"));
			}
			if (tag.contains("preSizoPos")) {
				d.preSizoPos = BlockPos.fromLong(tag.getLong("preSizoPos"));
			}
			if (tag.contains("preSizoDim")) {
				d.preSizoDim = tag.getString("preSizoDim");
			}
			d.rouletteForceNextGreen = tag.getBoolean("rouletteForceNextGreen");
			d.pendingOblivionTicks = tag.getInt("pendingOblivionTicks");
			d.seatPadEquipped = tag.getBoolean("seatPadEquipped");
			d.bureaucracyKeyEarned = tag.getBoolean("bureaucracyKeyEarned");
			d.taxKeyIssued = tag.getBoolean("taxKeyIssued");
			d.totalLoanRepaid = tag.getLong("totalLoanRepaid");
			d.hasPreBureauReturn = tag.getBoolean("hasPreBureauReturn");
			if (tag.contains("preBureauDim")) d.preBureauDim = tag.getString("preBureauDim");
			d.preBureauX = tag.getDouble("preBureauX");
			d.preBureauY = tag.contains("preBureauY") ? tag.getDouble("preBureauY") : 64.0D;
			d.preBureauZ = tag.getDouble("preBureauZ");
			d.preBureauYaw = tag.getFloat("preBureauYaw");
			d.lastCafeteriaRationDay = tag.contains("lastCafeteriaRationDay") ? tag.getLong("lastCafeteriaRationDay") : -1L;

			d.ticksLivedInWorld = tag.getLong("ticksLivedInWorld");
			d.highestGearScore = tag.getInt("highestGearScore");
			d.lastMercyDecayTick = tag.getLong("lastMercyDecayTick");
			if (tag.contains("recentMobDeaths", NbtElement.COMPOUND_TYPE)) {
				NbtCompound deathsTag = tag.getCompound("recentMobDeaths");
				for (String key : deathsTag.getKeys()) {
					d.recentMobDeaths.put(key, deathsTag.getInt(key));
				}
			}
			return d;
		}
	}

	public static class MachineOddsConfig {
		public int fateTokenChance = 50;
		public int fateNothingChance = 25;
		public int fateDeathChance = 25;
		public int coinHeadsChance = 50;
		public int rouletteGreenChance = 3;
		public int rouletteRedChance = 48;
		public int rouletteBlackChance = 49;

		public void resetDefaults() {
			fateTokenChance = 50;
			fateNothingChance = 25;
			fateDeathChance = 25;
			coinHeadsChance = 50;
			rouletteGreenChance = 3;
			rouletteRedChance = 48;
			rouletteBlackChance = 49;
		}

		public NbtCompound toNbt() {
			NbtCompound t = new NbtCompound();
			t.putInt("fateTokenChance", fateTokenChance);
			t.putInt("fateNothingChance", fateNothingChance);
			t.putInt("fateDeathChance", fateDeathChance);
			t.putInt("coinHeadsChance", coinHeadsChance);
			t.putInt("rouletteGreenChance", rouletteGreenChance);
			t.putInt("rouletteRedChance", rouletteRedChance);
			t.putInt("rouletteBlackChance", rouletteBlackChance);
			return t;
		}

		public static MachineOddsConfig fromNbt(NbtCompound t) {
			MachineOddsConfig c = new MachineOddsConfig();
			if (t.contains("fateTokenChance")) c.fateTokenChance = Math.max(0, t.getInt("fateTokenChance"));
			if (t.contains("fateNothingChance")) c.fateNothingChance = Math.max(0, t.getInt("fateNothingChance"));
			if (t.contains("fateDeathChance")) c.fateDeathChance = Math.max(0, t.getInt("fateDeathChance"));
			if (t.contains("coinHeadsChance")) c.coinHeadsChance = Math.clamp(t.getInt("coinHeadsChance"), 0, 100);
			if (t.contains("rouletteGreenChance")) c.rouletteGreenChance = Math.max(0, t.getInt("rouletteGreenChance"));
			if (t.contains("rouletteRedChance")) c.rouletteRedChance = Math.max(0, t.getInt("rouletteRedChance"));
			if (t.contains("rouletteBlackChance")) c.rouletteBlackChance = Math.max(0, t.getInt("rouletteBlackChance"));
			return c;
		}
	}

	public static class SkyArenaSession {
		public UUID playerUuid;
		public int centerX;
		public int baseY;
		public int centerZ;
		public int radius;
		public int machineX;
		public int machineY;
		public int machineZ;
		public String returnDim;
		public double returnX;
		public double returnY;
		public double returnZ;
		public float returnYaw;
		public float returnPitch;

		public NbtCompound toNbt() {
			NbtCompound t = new NbtCompound();
			t.putUuid("playerUuid", playerUuid);
			t.putInt("centerX", centerX);
			t.putInt("baseY", baseY);
			t.putInt("centerZ", centerZ);
			t.putInt("radius", radius);
			t.putInt("machineX", machineX);
			t.putInt("machineY", machineY);
			t.putInt("machineZ", machineZ);
			t.putString("returnDim", returnDim);
			t.putDouble("returnX", returnX);
			t.putDouble("returnY", returnY);
			t.putDouble("returnZ", returnZ);
			t.putFloat("returnYaw", returnYaw);
			t.putFloat("returnPitch", returnPitch);
			return t;
		}

		public static SkyArenaSession fromNbt(NbtCompound t) {
			SkyArenaSession s = new SkyArenaSession();
			s.playerUuid = t.getUuid("playerUuid");
			s.centerX = t.getInt("centerX");
			s.baseY = t.getInt("baseY");
			s.centerZ = t.getInt("centerZ");
			s.radius = t.getInt("radius");
			s.machineX = t.getInt("machineX");
			s.machineY = t.getInt("machineY");
			s.machineZ = t.getInt("machineZ");
			s.returnDim = t.getString("returnDim");
			s.returnX = t.getDouble("returnX");
			s.returnY = t.getDouble("returnY");
			s.returnZ = t.getDouble("returnZ");
			s.returnYaw = t.getFloat("returnYaw");
			s.returnPitch = t.getFloat("returnPitch");
			return s;
		}
	}

	public static class StructureEntry {
		public String type;
		public String dimension;
		public BlockPos pos;

		public StructureEntry(String type, String dimension, BlockPos pos) {
			this.type = type;
			this.dimension = dimension;
			this.pos = pos;
		}
	}

	public static class PokeballPocketSession {
		public int pocketId;
		public UUID occupantUuid;
		public BlockPos centerPos;
		public String returnDim = "minecraft:overworld";
		public double returnX;
		public double returnY;
		public double returnZ;
		public float returnYaw;
		public float returnPitch;
		public boolean built = false;

		public NbtCompound toNbt() {
			NbtCompound t = new NbtCompound();
			t.putInt("pocketId", pocketId);
			if (occupantUuid != null) {
				t.putString("occupantUuid", occupantUuid.toString());
			}
			t.putLong("centerPos", centerPos.asLong());
			t.putString("returnDim", returnDim);
			t.putDouble("returnX", returnX);
			t.putDouble("returnY", returnY);
			t.putDouble("returnZ", returnZ);
			t.putFloat("returnYaw", returnYaw);
			t.putFloat("returnPitch", returnPitch);
			t.putBoolean("built", built);
			return t;
		}

		public static PokeballPocketSession fromNbt(NbtCompound t) {
			PokeballPocketSession s = new PokeballPocketSession();
			s.pocketId = t.getInt("pocketId");
			if (t.contains("occupantUuid")) {
				try {
					s.occupantUuid = UUID.fromString(t.getString("occupantUuid"));
				} catch (IllegalArgumentException ignored) {
				}
			}
			s.centerPos = BlockPos.fromLong(t.getLong("centerPos"));
			s.returnDim = t.getString("returnDim");
			s.returnX = t.getDouble("returnX");
			s.returnY = t.getDouble("returnY");
			s.returnZ = t.getDouble("returnZ");
			s.returnYaw = t.getFloat("returnYaw");
			s.returnPitch = t.getFloat("returnPitch");
			s.built = t.getBoolean("built");
			return s;
		}
	}

	private final Map<UUID, PlayerFinanceData> accounts = new HashMap<>();
	private final Map<String, Integer> eliteStocks = new HashMap<>();
	private final Set<String> generatedRegionKeys = new HashSet<>();
	private final List<StructureEntry> generatedStructures = new ArrayList<>();
	private final Map<UUID, SkyArenaSession> activeSkyArenas = new HashMap<>();
	private final Map<Integer, PokeballPocketSession> pokeballPockets = new HashMap<>();
	private MachineOddsConfig oddsConfig = new MachineOddsConfig();
	private boolean mfoSpawned = false;
	private BlockPos mfoPos = null;
	private int skyArenaCounter = 0;
	private int pokeballCounter = 0;

	// Событие первого данж-сундука с Неизвестным предметом (10-минутный апокалипсис мобов)
	private boolean firstDungeonChestOpened = false;
	private int pendingArtifactAwakenTicks = 0;
	private boolean apocalypseActive = false;
	private int apocalypseTicksLeft = 0;
	private boolean apocalypseDollUsed = false;
	private boolean globalTimeFrozen = false;
	private boolean bureaucracyBuilt = false;
	private final Set<UUID> diedDuringApocalypse = new HashSet<>();

	public ModPersistentState() {
		initDefaultEliteStocks();
	}

	public MachineOddsConfig getOddsConfig() {
		return oddsConfig;
	}

	private void initDefaultEliteStocks() {
		eliteStocks.putIfAbsent("elite_sword", 2);
		eliteStocks.putIfAbsent("elite_axe", 2);
		eliteStocks.putIfAbsent("elite_mace", 2);
		eliteStocks.putIfAbsent("elite_trident", 2);
		eliteStocks.putIfAbsent("elite_crossbow", 2);
		eliteStocks.putIfAbsent("elite_pickaxe", 2);
		eliteStocks.putIfAbsent("mfo_sword_helm", 2);
		eliteStocks.putIfAbsent("mfo_sword_chest", 2);
		eliteStocks.putIfAbsent("mfo_sword_legs", 2);
		eliteStocks.putIfAbsent("mfo_sword_boots", 2);
		eliteStocks.putIfAbsent("mfo_archer_helm", 2);
		eliteStocks.putIfAbsent("mfo_archer_chest", 2);
		eliteStocks.putIfAbsent("mfo_archer_legs", 2);
		eliteStocks.putIfAbsent("mfo_archer_boots", 2);
		eliteStocks.putIfAbsent("elite_mfo_bow", 2);
		eliteStocks.putIfAbsent("elite_teleporter", 1);
		eliteStocks.putIfAbsent("elite_elytra", 1);
		eliteStocks.putIfAbsent("elite_totem", 3);
		eliteStocks.putIfAbsent("elite_egapple", 3);
		eliteStocks.putIfAbsent("elite_netherite", 2);
	}

	public PlayerFinanceData getAccount(UUID uuid) {
		return accounts.computeIfAbsent(uuid, k -> {
			markDirty();
			return new PlayerFinanceData();
		});
	}

	public Map<UUID, PlayerFinanceData> getAllAccounts() {
		return accounts;
	}

	public int getEliteStock(String offerId, int defaultStock) {
		if (!eliteStocks.containsKey(offerId)) {
			eliteStocks.put(offerId, defaultStock);
			markDirty();
		}
		return eliteStocks.get(offerId);
	}

	public boolean consumeEliteStock(String offerId) {
		int cur = eliteStocks.getOrDefault(offerId, 0);
		if (cur <= 0) {
			return false;
		}
		eliteStocks.put(offerId, cur - 1);
		markDirty();
		return true;
	}

	public boolean isMfoSpawned() {
		return mfoSpawned;
	}

	public void setMfoSpawned(BlockPos pos) {
		this.mfoSpawned = true;
		this.mfoPos = pos;
		this.generatedStructures.add(new StructureEntry("mfo", "minecraft:overworld", pos));
		markDirty();
	}

	public BlockPos getMfoPos() {
		return mfoPos;
	}

	public boolean hasGeneratedRegion(String key) {
		return generatedRegionKeys.contains(key);
	}

	public void markRegionGenerated(String key) {
		if (generatedRegionKeys.add(key)) {
			markDirty();
		}
	}

	public void recordStructure(String type, String dimension, BlockPos pos) {
		generatedStructures.add(new StructureEntry(type, dimension, pos));
		markDirty();
	}

	public List<StructureEntry> getGeneratedStructures() {
		return generatedStructures;
	}

	public int nextSkyArenaIndex() {
		skyArenaCounter++;
		markDirty();
		return skyArenaCounter;
	}

	public int nextPokeballPocketId() {
		pokeballCounter++;
		markDirty();
		return pokeballCounter;
	}

	public Map<Integer, PokeballPocketSession> getPokeballPockets() {
		return pokeballPockets;
	}

	public void putPokeballPocket(PokeballPocketSession session) {
		pokeballPockets.put(session.pocketId, session);
		markDirty();
	}

	public Map<UUID, SkyArenaSession> getActiveSkyArenas() {
		return activeSkyArenas;
	}

	public void putSkyArena(SkyArenaSession session) {
		activeSkyArenas.put(session.playerUuid, session);
		markDirty();
	}

	public SkyArenaSession removeSkyArena(UUID uuid) {
		SkyArenaSession removed = activeSkyArenas.remove(uuid);
		if (removed != null) {
			markDirty();
		}
		return removed;
	}

	public boolean isFirstDungeonChestOpened() {
		return firstDungeonChestOpened;
	}

	public void setFirstDungeonChestOpened(boolean opened) {
		this.firstDungeonChestOpened = opened;
		markDirty();
	}

	public boolean isBureaucracyBuilt() {
		return bureaucracyBuilt;
	}

	public void setBureaucracyBuilt(boolean built) {
		this.bureaucracyBuilt = built;
		markDirty();
	}

	public int getPendingArtifactAwakenTicks() {
		return pendingArtifactAwakenTicks;
	}

	public void setPendingArtifactAwakenTicks(int ticks) {
		this.pendingArtifactAwakenTicks = ticks;
		markDirty();
	}

	public boolean isApocalypseActive() {
		return apocalypseActive;
	}

	public void setApocalypseActive(boolean active) {
		this.apocalypseActive = active;
		markDirty();
	}

	public int getApocalypseTicksLeft() {
		return apocalypseTicksLeft;
	}

	public void setApocalypseTicksLeft(int ticks) {
		this.apocalypseTicksLeft = ticks;
	}

	public boolean isApocalypseDollUsed() {
		return apocalypseDollUsed;
	}

	public void setApocalypseDollUsed(boolean used) {
		this.apocalypseDollUsed = used;
		markDirty();
	}

	public boolean isGlobalTimeFrozen() {
		return globalTimeFrozen;
	}

	public void setGlobalTimeFrozen(boolean frozen) {
		this.globalTimeFrozen = frozen;
		markDirty();
	}

	public Set<UUID> getDiedDuringApocalypse() {
		return diedDuringApocalypse;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		NbtCompound accountsTag = new NbtCompound();
		for (Map.Entry<UUID, PlayerFinanceData> e : accounts.entrySet()) {
			accountsTag.put(e.getKey().toString(), e.getValue().toNbt());
		}
		nbt.put("accounts", accountsTag);

		NbtCompound stocksTag = new NbtCompound();
		for (Map.Entry<String, Integer> e : eliteStocks.entrySet()) {
			stocksTag.putInt(e.getKey(), e.getValue());
		}
		nbt.put("eliteStocks", stocksTag);

		NbtList regionsList = new NbtList();
		for (String k : generatedRegionKeys) {
			regionsList.add(NbtString.of(k));
		}
		nbt.put("generatedRegions", regionsList);

		NbtList structList = new NbtList();
		for (StructureEntry se : generatedStructures) {
			NbtCompound st = new NbtCompound();
			st.putString("type", se.type);
			st.putString("dim", se.dimension);
			st.putLong("pos", se.pos.asLong());
			structList.add(st);
		}
		nbt.put("structures", structList);

		NbtList arenasList = new NbtList();
		for (SkyArenaSession sa : activeSkyArenas.values()) {
			arenasList.add(sa.toNbt());
		}
		nbt.put("activeSkyArenas", arenasList);

		NbtList pokeballsList = new NbtList();
		for (PokeballPocketSession ps : pokeballPockets.values()) {
			pokeballsList.add(ps.toNbt());
		}
		nbt.put("pokeballPockets", pokeballsList);
		nbt.putInt("pokeballCounter", pokeballCounter);

		nbt.put("oddsConfig", oddsConfig.toNbt());
		nbt.putBoolean("mfoSpawned", mfoSpawned);
		if (mfoPos != null) {
			nbt.putLong("mfoPos", mfoPos.asLong());
		}
		nbt.putInt("skyArenaCounter", skyArenaCounter);
		nbt.putBoolean("firstDungeonChestOpened", firstDungeonChestOpened);
		nbt.putInt("pendingArtifactAwakenTicks", pendingArtifactAwakenTicks);
		nbt.putBoolean("apocalypseActive", apocalypseActive);
		nbt.putInt("apocalypseTicksLeft", apocalypseTicksLeft);
		nbt.putBoolean("apocalypseDollUsed", apocalypseDollUsed);
		nbt.putBoolean("globalTimeFrozen", globalTimeFrozen);
		nbt.putBoolean("bureaucracyBuilt", bureaucracyBuilt);
		NbtList deadList = new NbtList();
		for (UUID u : diedDuringApocalypse) {
			deadList.add(NbtString.of(u.toString()));
		}
		nbt.put("diedDuringApocalypse", deadList);
		return nbt;
	}

	public static ModPersistentState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		ModPersistentState state = new ModPersistentState();
		if (nbt.contains("accounts", NbtElement.COMPOUND_TYPE)) {
			NbtCompound acc = nbt.getCompound("accounts");
			for (String key : acc.getKeys()) {
				try {
					UUID id = UUID.fromString(key);
					state.accounts.put(id, PlayerFinanceData.fromNbt(acc.getCompound(key)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		if (nbt.contains("eliteStocks", NbtElement.COMPOUND_TYPE)) {
			NbtCompound st = nbt.getCompound("eliteStocks");
			for (String key : st.getKeys()) {
				state.eliteStocks.put(key, st.getInt(key));
			}
		}
		if (nbt.contains("generatedRegions", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("generatedRegions", NbtElement.STRING_TYPE);
			for (int i = 0; i < list.size(); i++) {
				state.generatedRegionKeys.add(list.getString(i));
			}
		}
		if (nbt.contains("structures", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("structures", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound st = list.getCompound(i);
				state.generatedStructures.add(new StructureEntry(
						st.getString("type"),
						st.getString("dim"),
						BlockPos.fromLong(st.getLong("pos"))
				));
			}
		}
		if (nbt.contains("activeSkyArenas", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("activeSkyArenas", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				SkyArenaSession s = SkyArenaSession.fromNbt(list.getCompound(i));
				state.activeSkyArenas.put(s.playerUuid, s);
			}
		}
		if (nbt.contains("pokeballPockets", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("pokeballPockets", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				PokeballPocketSession ps = PokeballPocketSession.fromNbt(list.getCompound(i));
				state.pokeballPockets.put(ps.pocketId, ps);
			}
		}
		state.pokeballCounter = nbt.getInt("pokeballCounter");
		if (nbt.contains("oddsConfig", NbtElement.COMPOUND_TYPE)) {
			state.oddsConfig = MachineOddsConfig.fromNbt(nbt.getCompound("oddsConfig"));
		}
		state.mfoSpawned = nbt.getBoolean("mfoSpawned");
		if (nbt.contains("mfoPos")) {
			state.mfoPos = BlockPos.fromLong(nbt.getLong("mfoPos"));
		}
		state.skyArenaCounter = nbt.getInt("skyArenaCounter");
		state.firstDungeonChestOpened = nbt.getBoolean("firstDungeonChestOpened");
		state.pendingArtifactAwakenTicks = nbt.getInt("pendingArtifactAwakenTicks");
		state.apocalypseActive = nbt.getBoolean("apocalypseActive");
		state.apocalypseTicksLeft = nbt.getInt("apocalypseTicksLeft");
		state.apocalypseDollUsed = nbt.getBoolean("apocalypseDollUsed");
		state.globalTimeFrozen = nbt.getBoolean("globalTimeFrozen");
		state.bureaucracyBuilt = nbt.getBoolean("bureaucracyBuilt");
		if (nbt.contains("diedDuringApocalypse", NbtElement.LIST_TYPE)) {
			NbtList dl = nbt.getList("diedDuringApocalypse", NbtElement.STRING_TYPE);
			for (int i = 0; i < dl.size(); i++) {
				try {
					state.diedDuringApocalypse.add(UUID.fromString(dl.getString(i)));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		return state;
	}

	public static ModPersistentState get(MinecraftServer server) {
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		if (overworld == null) {
			overworld = server.getOverworld();
		}
		PersistentState.Type<ModPersistentState> type = new PersistentState.Type<>(
				ModPersistentState::new,
				ModPersistentState::fromNbt,
				null
		);
		return overworld.getPersistentStateManager().getOrCreate(type, STATE_ID);
	}
}
