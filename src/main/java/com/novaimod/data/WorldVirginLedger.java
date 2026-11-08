package com.novaimod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.novaimod.entity.CatgirlEntity;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.HolyOrderEntity;
import com.novaimod.entity.LuceEntity;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.entity.WhiteCapEntity;
import com.novaimod.item.PokeballItem;
import com.novaimod.world.BureaucraticHellManager;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Постоянная летопись «Девственного Мира» (WorldVirginLedger v2):
 * Хранит эталонное состояние мира В МОМЕНТ ЕГО СОЗДАНИЯ / ГЕНЕРАЦИИ СТРУКТУР:
 * 1) Блоки и сундуки: сохраняет изначальное состояние каждого впервые сломанного, взорванного,
 *    сгоревшего или поставленного блока (включая NBT контейнеров и закрытые двери/клетки лабораторий).
 * 2) СТРОГИЙ РЕЕСТР ИЗНАЧАЛЬНЫХ СУЩЕСТВ МИРА (`virginRoster`):
 *    - Мирные/пассивные животные, сгенерированные вместе с чанками (`SpawnReason.CHUNK_GENERATION`),
 *      СТРОГО исключая потомство от размножения, спавн-яйца, команды и призыв;
 *    - Все NPC и обитатели ванильных и модовых строений (деревни, Церкви и Соборы Люче, Сантино,
 *      Цитадели/Форты/Лагеря Белых Колпаков, МФО, магазины, Эго-остров, Кошкодевочки в клетках
 *      Заброшенных Лабораторий и Министерства Бюрократического Ада!).
 * 3) При Перезапуске Мира («Time of the World — Made in Heaven»):
 *    - Все искусственно созданные, размноженные, ночные и вызванные мобы бесследно исчезают;
 *    - Все приручённые или уведённые домой изначальные существа (например, спасённая из лаборатории
 *      Кошкодевочка или приручённый лесной волк) исчезают с базы игрока и возвращаются в своё
 *      первозданное неприручённое состояние на родных местах (Кошкодевочка — обратно в клетку Лаборатории!).
 */
public class WorldVirginLedger extends PersistentState {
	public static final String STATE_ID = "novaimod_virgin_ledger";
	private static final int SCHEMA_VERSION = 2;

	/** Метка существа, рождённого при генерации чанка или структуры (до первой записи в реестр). */
	public static final String TAG_VIRGIN_NATIVE = "novai_virgin_native";
	/** Метка существа, чей девственный снимок УЖЕ записан в реестр (защита от дублей при телепорте между мирами). */
	public static final String TAG_VIRGIN_RECORDED = "novai_virgin_recorded";
	/** Метка любого не-изначального существа (размножение, спавн-яйцо, ночной спавн, патруль, клон, Покебол). */
	public static final String TAG_NON_VIRGIN = "novai_non_virgin";
	/** Метка временного визуального/боевого феномена (Аврора, Астролябия, Сверхнова, Шторм, Врата, Призма). */
	public static final String TAG_TEMP_SUMMON = "novai_temp_summon";
	private static final String EPOCH_PREFIX = "novai_epoch_";

	private static final int MAX_TRACKED_BLOCKS = 350_000;
	private static final int MAX_PASSIVE_ROSTER = 14_000;
	private static final int MAX_STRUCTURE_ROSTER = 6_000;

	private static final ThreadLocal<Integer> REBOOT_BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Integer> STRUCTURE_GEN_DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<List<PendingScopeEntity>> SCOPE_SPAWNED = ThreadLocal.withInitial(ArrayList::new);

	private record PendingScopeEntity(ServerWorld world, Entity entity) {
	}

	public static final class VirginEntityRecord {
		public final UUID recordId;
		public final String dimId;
		public final int chunkX;
		public final int chunkZ;
		public final double x;
		public final double y;
		public final double z;
		public final float yaw;
		public final float pitch;
		public final boolean structureEntity;
		public final NbtCompound virginNbt;
		public boolean pendingChunkRestore;

		public VirginEntityRecord(
				UUID recordId,
				String dimId,
				double x,
				double y,
				double z,
				float yaw,
				float pitch,
				boolean structureEntity,
				NbtCompound virginNbt,
				boolean pendingChunkRestore
		) {
			this.recordId = recordId;
			this.dimId = dimId;
			this.chunkX = ChunkPos.getPackedX(ChunkPos.toLong(BlockPos.ofFloored(x, y, z)));
			this.chunkZ = ChunkPos.getPackedZ(ChunkPos.toLong(BlockPos.ofFloored(x, y, z)));
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
			this.structureEntity = structureEntity;
			this.virginNbt = virginNbt;
			this.pendingChunkRestore = pendingChunkRestore;
		}

		public long packedChunkPos() {
			return ChunkPos.toLong(this.chunkX, this.chunkZ);
		}
	}

	private final Map<String, Long2IntLinkedOpenHashMap> originalBlocksByDim = new HashMap<>();
	private final Map<String, Long2ObjectOpenHashMap<NbtCompound>> originalBlockEntitiesByDim = new HashMap<>();
	private final Map<UUID, VirginEntityRecord> virginRoster = new LinkedHashMap<>();
	private final Map<String, Long2ObjectOpenHashMap<List<VirginEntityRecord>>> pendingByChunk = new HashMap<>();

	private int totalRecordedBlocks = 0;
	private int passiveRosterCount = 0;
	private int structureRosterCount = 0;
	private int rebootEpoch = 0;

	public static boolean isBypassActive() {
		return REBOOT_BYPASS_DEPTH.get() > 0 || STRUCTURE_GEN_DEPTH.get() > 0;
	}

	public static boolean isRebootBypassActive() {
		return REBOOT_BYPASS_DEPTH.get() > 0;
	}

	public static boolean isInStructureGenScope() {
		return STRUCTURE_GEN_DEPTH.get() > 0;
	}

	/**
	 * Полный обход записи (используется во время самого отката Вселенной, временных небесных арен и Режима 1).
	 */
	public static void runWithBypass(Runnable action) {
		REBOOT_BYPASS_DEPTH.set(REBOOT_BYPASS_DEPTH.get() + 1);
		try {
			action.run();
		} finally {
			REBOOT_BYPASS_DEPTH.set(Math.max(0, REBOOT_BYPASS_DEPTH.get() - 1));
		}
	}

	public static <T> T supplyWithBypass(Supplier<T> action) {
		REBOOT_BYPASS_DEPTH.set(REBOOT_BYPASS_DEPTH.get() + 1);
		try {
			return action.get();
		} finally {
			REBOOT_BYPASS_DEPTH.set(Math.max(0, REBOOT_BYPASS_DEPTH.get() - 1));
		}
	}

	/**
	 * Область генерации строения мира (Церкви, Белые Колпаки, Лаборатории с Кошкодевочками, МФО, Эго-остров, Министерство):
	 * - Блоки самой структуры становятся частью девственного мира (не стираются при перезапуске, а восстанавливаются, если их сломать!).
	 * - Все существа, заспавненные внутри постройки, автоматически заносятся в эталонный реестр в их первозданном виде!
	 */
	public static void runInStructureGenScope(Runnable action) {
		int depth = STRUCTURE_GEN_DEPTH.get();
		STRUCTURE_GEN_DEPTH.set(depth + 1);
		try {
			action.run();
		} finally {
			int nextDepth = Math.max(0, STRUCTURE_GEN_DEPTH.get() - 1);
			STRUCTURE_GEN_DEPTH.set(nextDepth);
			if (nextDepth == 0) {
				List<PendingScopeEntity> captured = SCOPE_SPAWNED.get();
				if (!captured.isEmpty()) {
					List<PendingScopeEntity> copy = new ArrayList<>(captured);
					captured.clear();
					for (PendingScopeEntity item : copy) {
						if (!item.entity().isRemoved()) {
							captureVirginSnapshotIfNeeded(item.entity(), item.world(), true);
						}
					}
				}
			}
		}
	}

	public static boolean isTrackableDimension(ServerWorld sw) {
		RegistryKey<World> key = sw.getRegistryKey();
		return key == World.OVERWORLD
				|| key == World.NETHER
				|| key == World.END
				|| key.equals(BureaucraticHellManager.BUREAUCRATIC_HELL_WORLD);
	}

	public static boolean isVirginTrackableEntity(Entity entity) {
		if (entity instanceof PlayerEntity) {
			return false;
		}
		return entity instanceof LivingEntity
				|| entity instanceof CushionEntity
				|| entity instanceof PartyDollEntity;
	}

	public static void recordBlockBeforeFirstChange(ServerWorld sw, BlockPos pos, BlockState newState) {
		if (isBypassActive() || !isTrackableDimension(sw)) {
			return;
		}
		if (sw.getRegistryKey() == World.OVERWORLD && pos.getY() >= 264) {
			return;
		}
		BlockState current = sw.getBlockState(pos);
		if (current == newState) {
			return;
		}

		WorldVirginLedger ledger = get(sw.getServer());
		if (ledger.totalRecordedBlocks >= MAX_TRACKED_BLOCKS) {
			return;
		}

		String dimId = sw.getRegistryKey().getValue().toString();
		long packed = pos.asLong();
		Long2IntLinkedOpenHashMap dimBlocks = ledger.originalBlocksByDim.computeIfAbsent(dimId, k -> new Long2IntLinkedOpenHashMap());
		if (dimBlocks.containsKey(packed)) {
			return;
		}

		dimBlocks.put(packed, Block.getRawIdFromState(current));
		ledger.totalRecordedBlocks++;

		if (current.hasBlockEntity()) {
			BlockEntity be = sw.getBlockEntity(pos);
			if (be != null) {
				Long2ObjectOpenHashMap<NbtCompound> dimBe = ledger.originalBlockEntitiesByDim
						.computeIfAbsent(dimId, k -> new Long2ObjectOpenHashMap<>());
				dimBe.put(packed, be.createNbt(sw.getRegistryManager()));
			}
		}
		ledger.markDirty();
	}

	public static void stampEntityEpoch(Entity entity, ServerWorld sw) {
		WorldVirginLedger ledger = get(sw.getServer());
		stampEntityEpochDirect(entity, ledger.rebootEpoch);
	}

	private static void stampEntityEpochDirect(Entity entity, int epoch) {
		List<String> toRemove = new ArrayList<>();
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(EPOCH_PREFIX)) {
				toRemove.add(tag);
			}
		}
		for (String old : toRemove) {
			entity.removeCommandTag(old);
		}
		entity.addCommandTag(EPOCH_PREFIX + epoch);
	}

	private static int readEntityEpoch(Entity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(EPOCH_PREFIX)) {
				try {
					return Integer.parseInt(tag.substring(EPOCH_PREFIX.length()));
				} catch (NumberFormatException ignored) {
					return 0;
				}
			}
		}
		return 0;
	}

	public static void onEntityLoaded(Entity entity, ServerWorld sw) {
		if (isRebootBypassActive() || entity instanceof PlayerEntity || !isTrackableDimension(sw)) {
			return;
		}

		WorldVirginLedger ledger = get(sw.getServer());
		int currentEpoch = ledger.rebootEpoch;

		// 1. Временные визуальные/боевые феномены никогда не сохраняются; старые из прошлых эпох удаляются
		if (entity.getCommandTags().contains(TAG_TEMP_SUMMON)) {
			if (hasAnyEpochTag(entity) && readEntityEpoch(entity) < currentEpoch) {
				entity.discard();
				return;
			}
			stampEntityEpochDirect(entity, currentEpoch);
			return;
		}

		if (!isVirginTrackableEntity(entity)) {
			return;
		}

		// 2. Если прямо сейчас строится структура мира — откладываем финальный снимок до конца постройки структуры
		if (isInStructureGenScope() && !entity.getCommandTags().contains(TAG_NON_VIRGIN)) {
			entity.addCommandTag(TAG_VIRGIN_NATIVE);
			stampEntityEpochDirect(entity, currentEpoch);
			SCOPE_SPAWNED.get().add(new PendingScopeEntity(sw, entity));
			return;
		}

		// 3. Впервые загрузившееся девственное существо из генерации чанка или структуры ваниллы
		if (entity.getCommandTags().contains(TAG_VIRGIN_NATIVE)
				&& !entity.getCommandTags().contains(TAG_VIRGIN_RECORDED)
				&& !entity.getCommandTags().contains(TAG_NON_VIRGIN)) {
			boolean structureLike = isLikelyStructureResident(entity);
			captureVirginSnapshotIfNeeded(entity, sw, structureLike);
			return;
		}

		// 4. Поддержка уже существующих миров до первого перезапуска (epoch == 0), если у старых мобов ещё нет меток
		if (currentEpoch == 0
				&& !hasAnyEpochTag(entity)
				&& !entity.getCommandTags().contains(TAG_VIRGIN_RECORDED)
				&& !entity.getCommandTags().contains(TAG_NON_VIRGIN)) {
			if (qualifiesLegacyUntaggedVirginEntity(entity)) {
				captureVirginSnapshotIfNeeded(entity, sw, isLikelyStructureResident(entity));
				return;
			} else {
				entity.addCommandTag(TAG_NON_VIRGIN);
				stampEntityEpochDirect(entity, currentEpoch);
				return;
			}
		}

		// 5. Если существо загрузилось из старого выгруженного чанка прошлой эпохи (до Перезапуска) — стираем его!
		int entityEpoch = readEntityEpoch(entity);
		if (entityEpoch < currentEpoch) {
			entity.discard();
			return;
		}

		// 6. Любое новое существо текущей эпохи без метки Virgin — искусственное/ночное/размноженное
		if (!entity.getCommandTags().contains(TAG_VIRGIN_RECORDED)) {
			entity.addCommandTag(TAG_NON_VIRGIN);
		}
		stampEntityEpochDirect(entity, currentEpoch);
	}

	private static boolean hasAnyEpochTag(Entity entity) {
		for (String tag : entity.getCommandTags()) {
			if (tag.startsWith(EPOCH_PREFIX)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isLikelyStructureResident(Entity entity) {
		return entity instanceof ModNpcEntity
				|| entity instanceof LuceEntity
				|| entity instanceof HolyOrderEntity
				|| entity instanceof WhiteCapEntity
				|| entity instanceof CatgirlEntity
				|| entity instanceof VillagerEntity
				|| entity instanceof CushionEntity
				|| entity instanceof PartyDollEntity
				|| (entity instanceof WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG));
	}

	/**
	 * Для старых сохранений (созданных до v4.4.1, при epoch == 0): отбирает ТОЛЬКО коренных мирных взрослых
	 * неприручённых животных и гарнизоны/NPC структур, отсекая ночных монстров, детёнышей и уже приручённых питомцев!
	 */
	private static boolean qualifiesLegacyUntaggedVirginEntity(Entity entity) {
		if (entity instanceof CushionEntity || entity instanceof PartyDollEntity) {
			return true;
		}
		if (entity instanceof ModNpcEntity npc) {
			return npc.isVirginStructureCandidate();
		}
		if (entity instanceof LuceEntity) {
			return true;
		}
		if (entity instanceof WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG)) {
			return true;
		}
		if (entity instanceof HolyOrderEntity ho) {
			return ho.isVirginGarrisonCandidate();
		}
		if (entity instanceof WhiteCapEntity wc) {
			return wc.isVirginGarrisonCandidate();
		}
		if (entity instanceof CatgirlEntity cg) {
			return !cg.isRescued();
		}
		if (entity instanceof VillagerEntity vil) {
			return !vil.isBaby();
		}
		if (entity instanceof Monster) {
			return false;
		}
		if (entity instanceof PassiveEntity passive) {
			if (passive.isBaby()) {
				return false;
			}
			if (passive instanceof TameableEntity tameable && tameable.isTamed()) {
				return false;
			}
			if (passive instanceof AbstractHorseEntity horse && horse.isTame()) {
				return false;
			}
			return true;
		}
		return false;
	}

	private static void captureVirginSnapshotIfNeeded(Entity entity, ServerWorld sw, boolean structureEntity) {
		if (entity.getCommandTags().contains(TAG_VIRGIN_RECORDED) || entity.getCommandTags().contains(TAG_NON_VIRGIN)) {
			return;
		}
		WorldVirginLedger ledger = get(sw.getServer());
		if (structureEntity) {
			if (ledger.structureRosterCount >= MAX_STRUCTURE_ROSTER) {
				return;
			}
		} else {
			if (ledger.passiveRosterCount >= MAX_PASSIVE_ROSTER) {
				return;
			}
		}

		// Ставим метки ДО снятия NBT, чтобы возрождённые копии знали, что они уже учтены в реестре!
		entity.removeCommandTag(TAG_NON_VIRGIN);
		entity.addCommandTag(TAG_VIRGIN_NATIVE);
		entity.addCommandTag(TAG_VIRGIN_RECORDED);
		stampEntityEpochDirect(entity, ledger.rebootEpoch);

		NbtCompound cleanNbt = buildCleanVirginNbt(entity);
		if (cleanNbt == null) {
			return;
		}

		UUID recId = entity.getUuid();
		if (ledger.virginRoster.containsKey(recId)) {
			recId = UUID.randomUUID();
		}

		VirginEntityRecord record = new VirginEntityRecord(
				recId,
				sw.getRegistryKey().getValue().toString(),
				entity.getX(),
				entity.getY(),
				entity.getZ(),
				entity.getYaw(),
				entity.getPitch(),
				structureEntity,
				cleanNbt,
				false
		);
		ledger.virginRoster.put(recId, record);
		if (structureEntity) {
			ledger.structureRosterCount++;
		} else {
			ledger.passiveRosterCount++;
		}
		ledger.markDirty();
	}

	private static NbtCompound buildCleanVirginNbt(Entity entity) {
		NbtCompound nbt = new NbtCompound();
		if (!entity.saveSelfNbt(nbt)) {
			return null;
		}
		nbt.remove("UUID");
		nbt.remove("HurtTime");
		nbt.remove("DeathTime");
		nbt.remove("ActiveEffects");
		nbt.remove("AngryAt");
		nbt.remove("Leash");
		nbt.putShort("Fire", (short) -20);
		nbt.putFloat("FallDistance", 0.0F);
		if (entity instanceof LivingEntity le) {
			nbt.putFloat("Health", Math.max(1.0F, le.getMaxHealth()));
			nbt.putInt("AngerTime", 0);
		}
		return nbt;
	}

	public int getTotalRecordedBlocks() {
		return totalRecordedBlocks;
	}

	public int getVirginRosterCount() {
		return virginRoster.size();
	}

	public int getRebootEpoch() {
		return rebootEpoch;
	}

	/**
	 * Подгружает девственных обитателей чанков, которые были выгружены в момент Большого Взрыва,
	 * как только игрок подходит к этим чанкам (например, к далёкой Лаборатории или Церкви).
	 */
	public static void tickPendingChunkRestores(MinecraftServer server) {
		WorldVirginLedger ledger = get(server);
		if (ledger.pendingByChunk.isEmpty()) {
			return;
		}

		runWithBypass(() -> {
			boolean changed = false;
			Iterator<Map.Entry<String, Long2ObjectOpenHashMap<List<VirginEntityRecord>>>> dimIt = ledger.pendingByChunk.entrySet().iterator();
			while (dimIt.hasNext()) {
				Map.Entry<String, Long2ObjectOpenHashMap<List<VirginEntityRecord>>> dimEntry = dimIt.next();
				RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimEntry.getKey()));
				ServerWorld sw = server.getWorld(worldKey);
				if (sw == null) {
					continue;
				}

				Long2ObjectOpenHashMap<List<VirginEntityRecord>> chunkMap = dimEntry.getValue();
				var chunkIt = chunkMap.long2ObjectEntrySet().iterator();
				while (chunkIt.hasNext()) {
					var ce = chunkIt.next();
					List<VirginEntityRecord> records = ce.getValue();
					if (records.isEmpty()) {
						chunkIt.remove();
						continue;
					}
					VirginEntityRecord sample = records.get(0);
					BlockPos checkPos = BlockPos.ofFloored(sample.x, sample.y, sample.z);
					if (!sw.shouldTickEntity(checkPos)) {
						continue;
					}

					// Удаляем все возможные старые сущности прошлой эпохи в этом чанке перед заселением эталона
					for (VirginEntityRecord rec : records) {
						spawnFromVirginRecord(sw, rec, ledger.rebootEpoch);
						rec.pendingChunkRestore = false;
					}
					chunkIt.remove();
					changed = true;
				}

				if (chunkMap.isEmpty()) {
					dimIt.remove();
				}
			}
			if (changed) {
				ledger.markDirty();
			}
		});
	}

	/**
	 * Полный Перезапуск Мира в Девственно Чистое Изначальное Состояние!
	 * Возвращает статистику: int[]{restoredBlocks, restoredVirginEntities, purgedEntities}.
	 */
	public int[] restoreEntireWorldToVirginState(MinecraftServer server) {
		final int[] stats = new int[]{0, 0, 0};

		this.rebootEpoch++;
		this.pendingByChunk.clear();

		runWithBypass(() -> {
			// 0. Очищаем пойманных мобов в Покеболах у игроков и в Сокровищнице Вавилона,
			//    чтобы спасённая в Покебол Кошкодевочка честно вернулась в Лабораторию без дюпа!
			clearCapturedMobsFromInventories(server);

			// 1. Полная зачистка загруженных миров от всех текущих существ (искусственных, ночных, размноженных,
			//    приручённых, уведённых домой) и выброшенного мусора!
			for (ServerWorld sw : server.getWorlds()) {
				if (!isTrackableDimension(sw)) {
					continue;
				}
				List<Entity> toDiscard = new ArrayList<>();
				for (Entity e : sw.iterateEntities()) {
					if (e instanceof PlayerEntity) {
						continue;
					}
					if (isVirginTrackableEntity(e)
							|| e instanceof ItemEntity
							|| e instanceof ExperienceOrbEntity
							|| e instanceof ProjectileEntity
							|| e instanceof TntEntity
							|| e instanceof FallingBlockEntity
							|| e instanceof AreaEffectCloudEntity
							|| e.getCommandTags().contains(TAG_TEMP_SUMMON)) {
						toDiscard.add(e);
					}
				}
				for (Entity entity : toDiscard) {
					if (entity instanceof LivingEntity) {
						stats[2]++;
					}
					entity.discard();
				}
			}

			// 2. Восстанавливаем ВСЕ когда-либо изменённые блоки и сундуки мира (включая закрытую клетку Лаборатории!)
			int restoreFlags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
			for (Map.Entry<String, Long2IntLinkedOpenHashMap> entry : originalBlocksByDim.entrySet()) {
				String dimId = entry.getKey();
				RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimId));
				ServerWorld sw = server.getWorld(worldKey);
				if (sw == null) {
					continue;
				}
				Long2IntLinkedOpenHashMap dimBlocks = entry.getValue();
				Long2ObjectOpenHashMap<NbtCompound> dimBe = originalBlockEntitiesByDim.get(dimId);

				long[] keys = dimBlocks.keySet().toLongArray();
				for (int i = keys.length - 1; i >= 0; i--) {
					long packed = keys[i];
					BlockPos pos = BlockPos.fromLong(packed);
					BlockState virginState = Block.getStateFromRawId(dimBlocks.get(packed));
					if (virginState == null) {
						virginState = Blocks.AIR.getDefaultState();
					}
					sw.setBlockState(pos, virginState, restoreFlags);
					if (dimBe != null && dimBe.containsKey(packed)) {
						BlockEntity be = sw.getBlockEntity(pos);
						if (be != null) {
							be.read(dimBe.get(packed), sw.getRegistryManager());
							be.markDirty();
						}
					}
					stats[0]++;
				}
			}

			// 3. Возвращаем ТОЛЬКО изначальных существ мира и строений в их нетронутом девственном состоянии!
			for (VirginEntityRecord rec : virginRoster.values()) {
				RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(rec.dimId));
				ServerWorld sw = server.getWorld(worldKey);
				if (sw == null) {
					continue;
				}
				BlockPos targetPos = BlockPos.ofFloored(rec.x, rec.y, rec.z);
				if (sw.shouldTickEntity(targetPos)) {
					if (spawnFromVirginRecord(sw, rec, this.rebootEpoch)) {
						stats[1]++;
					}
					rec.pendingChunkRestore = false;
				} else {
					// Чанк сейчас выгружен — откладываем мгновенный спавн до подгрузки этого чанка без фризов!
					rec.pendingChunkRestore = true;
					queuePendingRecord(rec);
					stats[1]++;
				}
			}

			// 4. Блоки мира снова девственно чисты — очищаем журнал правок блоков до следующего цикла истории
			//    (а эталонный реестр изначальных существ `virginRoster` сохраняется навсегда для будущих перезапусков!)
			originalBlocksByDim.clear();
			originalBlockEntitiesByDim.clear();
			totalRecordedBlocks = 0;
			markDirty();
		});

		return stats;
	}

	private void queuePendingRecord(VirginEntityRecord rec) {
		Long2ObjectOpenHashMap<List<VirginEntityRecord>> chunkMap = pendingByChunk
				.computeIfAbsent(rec.dimId, k -> new Long2ObjectOpenHashMap<>());
		chunkMap.computeIfAbsent(rec.packedChunkPos(), k -> new ArrayList<>()).add(rec);
	}

	private static boolean spawnFromVirginRecord(ServerWorld sw, VirginEntityRecord rec, int epoch) {
		NbtCompound copy = rec.virginNbt.copy();
		copy.putUuid("UUID", UUID.randomUUID());
		Entity restored = EntityType.loadEntityWithPassengers(copy, sw, e -> {
			e.refreshPositionAndAngles(rec.x, rec.y, rec.z, rec.yaw, rec.pitch);
			e.setVelocity(Vec3d.ZERO);
			e.removeCommandTag(TAG_NON_VIRGIN);
			e.addCommandTag(TAG_VIRGIN_NATIVE);
			e.addCommandTag(TAG_VIRGIN_RECORDED);
			stampEntityEpochDirect(e, epoch);
			return e;
		});
		if (restored == null) {
			return false;
		}
		if (restored instanceof LivingEntity le) {
			le.setHealth(le.getMaxHealth());
			le.setFireTicks(0);
			le.clearStatusEffects();
		}
		if (restored instanceof MobEntity mob) {
			mob.setTarget(null);
			mob.setAttacking(false);
		}
		return sw.spawnEntity(restored);
	}

	private static void clearCapturedMobsFromInventories(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);
		for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
			scrubMobPokeballsInInventory(sp.getInventory());
			scrubMobPokeballsInInventory(sp.getEnderChestInventory());
			DefaultedList<ItemStack> vault = state.getBabylonVault(sp.getUuid());
			boolean vaultChanged = false;
			for (ItemStack stack : vault) {
				if (!stack.isEmpty() && stack.getItem() instanceof PokeballItem
						&& "mob".equals(PokeballItem.getCaptureType(stack))) {
					PokeballItem.clearPokeballData(stack);
					vaultChanged = true;
				}
			}
			if (vaultChanged) {
				state.markDirty();
			}
		}
	}

	private static boolean scrubMobPokeballsInInventory(Inventory inv) {
		boolean changed = false;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack stack = inv.getStack(i);
			if (!stack.isEmpty() && stack.getItem() instanceof PokeballItem
					&& "mob".equals(PokeballItem.getCaptureType(stack))) {
				PokeballItem.clearPokeballData(stack);
				changed = true;
			}
		}
		return changed;
	}

	public static BlockPos findSafeWorldSpawnPos(ServerWorld overworld) {
		BlockPos spawn = overworld.getSpawnPos();
		overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
		int topY = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		return new BlockPos(spawn.getX(), Math.max(overworld.getBottomY() + 2, topY), spawn.getZ());
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		nbt.putInt("SchemaVersion", SCHEMA_VERSION);
		nbt.putInt("RebootEpoch", this.rebootEpoch);

		NbtCompound dimsTag = new NbtCompound();
		for (Map.Entry<String, Long2IntLinkedOpenHashMap> e : originalBlocksByDim.entrySet()) {
			Long2IntLinkedOpenHashMap map = e.getValue();
			long[] posArr = new long[map.size()];
			int[] stateArr = new int[map.size()];
			int idx = 0;
			for (Long2IntMap.Entry me : map.long2IntEntrySet()) {
				posArr[idx] = me.getLongKey();
				stateArr[idx] = me.getIntValue();
				idx++;
			}
			NbtCompound dimEntry = new NbtCompound();
			dimEntry.putLongArray("Positions", posArr);
			dimEntry.putIntArray("States", stateArr);

			Long2ObjectOpenHashMap<NbtCompound> beMap = originalBlockEntitiesByDim.get(e.getKey());
			if (beMap != null && !beMap.isEmpty()) {
				NbtList beList = new NbtList();
				for (var beEntry : beMap.long2ObjectEntrySet()) {
					NbtCompound item = new NbtCompound();
					item.putLong("Pos", beEntry.getLongKey());
					item.put("Data", beEntry.getValue());
					beList.add(item);
				}
				dimEntry.put("BlockEntities", beList);
			}
			dimsTag.put(e.getKey(), dimEntry);
		}
		nbt.put("VirginBlocks", dimsTag);

		NbtList rosterList = new NbtList();
		for (VirginEntityRecord rec : virginRoster.values()) {
			NbtCompound r = new NbtCompound();
			r.putUuid("Id", rec.recordId);
			r.putString("Dim", rec.dimId);
			r.putDouble("X", rec.x);
			r.putDouble("Y", rec.y);
			r.putDouble("Z", rec.z);
			r.putFloat("Yaw", rec.yaw);
			r.putFloat("Pitch", rec.pitch);
			r.putBoolean("Struct", rec.structureEntity);
			r.putBoolean("Pending", rec.pendingChunkRestore);
			r.put("Nbt", rec.virginNbt);
			rosterList.add(r);
		}
		nbt.put("VirginRoster", rosterList);
		return nbt;
	}

	public static WorldVirginLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		WorldVirginLedger ledger = new WorldVirginLedger();
		ledger.rebootEpoch = nbt.getInt("RebootEpoch");

		if (nbt.contains("VirginBlocks", NbtElement.COMPOUND_TYPE)) {
			NbtCompound dimsTag = nbt.getCompound("VirginBlocks");
			for (String dimKey : dimsTag.getKeys()) {
				NbtCompound dimEntry = dimsTag.getCompound(dimKey);
				long[] posArr = dimEntry.getLongArray("Positions");
				int[] stateArr = dimEntry.getIntArray("States");
				int len = Math.min(posArr.length, stateArr.length);
				Long2IntLinkedOpenHashMap map = new Long2IntLinkedOpenHashMap(len);
				for (int i = 0; i < len; i++) {
					map.put(posArr[i], stateArr[i]);
				}
				ledger.originalBlocksByDim.put(dimKey, map);
				ledger.totalRecordedBlocks += len;

				if (dimEntry.contains("BlockEntities", NbtElement.LIST_TYPE)) {
					NbtList beList = dimEntry.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
					Long2ObjectOpenHashMap<NbtCompound> beMap = new Long2ObjectOpenHashMap<>(beList.size());
					for (int i = 0; i < beList.size(); i++) {
						NbtCompound item = beList.getCompound(i);
						beMap.put(item.getLong("Pos"), item.getCompound("Data"));
					}
					ledger.originalBlockEntitiesByDim.put(dimKey, beMap);
				}
			}
		}

		// Загружаем только новый чистый реестр Schema v2 (старый перегруженный DeadVirginMobs из v1 намеренно игнорируется!)
		if (nbt.getInt("SchemaVersion") >= SCHEMA_VERSION && nbt.contains("VirginRoster", NbtElement.LIST_TYPE)) {
			NbtList rl = nbt.getList("VirginRoster", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < rl.size(); i++) {
				NbtCompound r = rl.getCompound(i);
				if (!r.containsUuid("Id")) {
					continue;
				}
				UUID id = r.getUuid("Id");
				boolean struct = r.getBoolean("Struct");
				boolean pending = r.getBoolean("Pending");
				VirginEntityRecord rec = new VirginEntityRecord(
						id,
						r.getString("Dim"),
						r.getDouble("X"),
						r.getDouble("Y"),
						r.getDouble("Z"),
						r.getFloat("Yaw"),
						r.getFloat("Pitch"),
						struct,
						r.getCompound("Nbt"),
						pending
				);
				ledger.virginRoster.put(id, rec);
				if (struct) {
					ledger.structureRosterCount++;
				} else {
					ledger.passiveRosterCount++;
				}
				if (pending) {
					ledger.queuePendingRecord(rec);
				}
			}
		}
		return ledger;
	}

	public static WorldVirginLedger get(MinecraftServer server) {
		ServerWorld overworld = server.getWorld(World.OVERWORLD);
		if (overworld == null) {
			overworld = server.getOverworld();
		}
		PersistentState.Type<WorldVirginLedger> type = new PersistentState.Type<>(
				WorldVirginLedger::new,
				WorldVirginLedger::fromNbt,
				null
		);
		return overworld.getPersistentStateManager().getOrCreate(type, STATE_ID);
	}
}
