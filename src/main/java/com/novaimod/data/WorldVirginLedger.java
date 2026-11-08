package com.novaimod.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Постоянная летопись «Девственного Мира» (WorldVirginLedger):
 * с самой первой секунды жизни мира запоминает:
 * 1) Изначальное (девственное) состояние каждого впервые сломанного, поставленного, взорванного,
 *    сгоревшего или изменённого блока (включая NBT сундуков и контейнеров!) с сохранением на диск.
 * 2) Изначальные точки появления всех естественных мобов мира и полные снимки ВСЕХ убитых существ,
 *    чтобы при перезапуске Вселенной «Time of the World (Made in Heaven)» вернуть мир в абсолютно
 *    девственно чистое изначальное состояние — со всеми целыми блоками и всеми живыми существами!
 */
public class WorldVirginLedger extends PersistentState {
	public static final String STATE_ID = "novaimod_virgin_ledger";
	public static final String TAG_TEMP_SUMMON = "novai_temp_summon";
	private static final int MAX_TRACKED_BLOCKS = 350_000;
	private static final int MAX_DEAD_MOBS = 4_000;
	private static final int MAX_ORIGINS = 10_000;

	private static final ThreadLocal<Integer> BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);

	public record MobVirginOrigin(String dimId, double x, double y, double z, float yaw, float pitch) {
	}

	public record DeadVirginMob(
			UUID uuid,
			String dimId,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			float maxHealth,
			NbtCompound cleanNbt
	) {
	}

	private final Map<String, Long2IntLinkedOpenHashMap> originalBlocksByDim = new HashMap<>();
	private final Map<String, Long2ObjectOpenHashMap<NbtCompound>> originalBlockEntitiesByDim = new HashMap<>();
	private final Map<UUID, MobVirginOrigin> virginOrigins = new LinkedHashMap<>();
	private final Map<UUID, DeadVirginMob> deadVirginMobs = new LinkedHashMap<>();
	private int totalRecordedBlocks = 0;

	public static boolean isBypassActive() {
		return BYPASS_DEPTH.get() > 0;
	}

	public static void runWithBypass(Runnable action) {
		BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);
		try {
			action.run();
		} finally {
			BYPASS_DEPTH.set(Math.max(0, BYPASS_DEPTH.get() - 1));
		}
	}

	public static <T> T supplyWithBypass(Supplier<T> action) {
		BYPASS_DEPTH.set(BYPASS_DEPTH.get() + 1);
		try {
			return action.get();
		} finally {
			BYPASS_DEPTH.set(Math.max(0, BYPASS_DEPTH.get() - 1));
		}
	}

	private static boolean isTrackableDimension(ServerWorld sw) {
		RegistryKey<World> key = sw.getRegistryKey();
		return key == World.OVERWORLD || key == World.NETHER || key == World.END;
	}

	public static void recordBlockBeforeFirstChange(ServerWorld sw, BlockPos pos, BlockState newState) {
		if (isBypassActive() || !isTrackableDimension(sw)) {
			return;
		}
		// Игнорируем заоблачную временную арену Пьяного Забвения
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
			return; // Уже сохранено самое ПЕРВОЕ (девственное) состояние этого блока!
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

	public static void onEntityLoaded(Entity entity, ServerWorld sw) {
		if (isBypassActive() || !isTrackableDimension(sw)) {
			return;
		}
		if (!(entity instanceof LivingEntity living) || entity instanceof PlayerEntity) {
			return;
		}
		if (entity.getCommandTags().contains(TAG_TEMP_SUMMON)) {
			return;
		}

		WorldVirginLedger ledger = get(sw.getServer());
		if (!ledger.virginOrigins.containsKey(living.getUuid()) && ledger.virginOrigins.size() < MAX_ORIGINS) {
			ledger.virginOrigins.put(living.getUuid(), new MobVirginOrigin(
					sw.getRegistryKey().getValue().toString(),
					living.getX(),
					living.getY(),
					living.getZ(),
					living.getYaw(),
					living.getPitch()
			));
			ledger.markDirty();
		}
	}

	/**
	 * Вызывается ПЕРЕД смертью любого живого существа в мире, чтобы при Перезапуске Вселенной
	 * все убитые за историю мира мобы вернулись живыми и невредимыми на свои изначальные места!
	 */
	public static void recordMobBeforeDeath(LivingEntity entity) {
		if (isBypassActive() || entity instanceof PlayerEntity) {
			return;
		}
		if (!(entity.getWorld() instanceof ServerWorld sw) || !isTrackableDimension(sw)) {
			return;
		}
		if (entity.getCommandTags().contains(TAG_TEMP_SUMMON)) {
			return;
		}

		WorldVirginLedger ledger = get(sw.getServer());
		if (ledger.deadVirginMobs.containsKey(entity.getUuid()) || ledger.deadVirginMobs.size() >= MAX_DEAD_MOBS) {
			return;
		}

		NbtCompound nbt = new NbtCompound();
		if (!entity.saveSelfNbt(nbt)) {
			return;
		}

		float maxHp = Math.max(1.0F, entity.getMaxHealth());
		nbt.putFloat("Health", maxHp);
		nbt.putShort("HurtTime", (short) 0);
		nbt.putShort("DeathTime", (short) 0);
		nbt.putShort("Fire", (short) -20);
		nbt.remove("ActiveEffects");
		nbt.remove("AngryAt");
		nbt.putInt("AngerTime", 0);

		MobVirginOrigin origin = ledger.virginOrigins.get(entity.getUuid());
		String dimId = origin != null ? origin.dimId() : sw.getRegistryKey().getValue().toString();
		double ox = origin != null ? origin.x() : entity.getX();
		double oy = origin != null ? origin.y() : entity.getY();
		double oz = origin != null ? origin.z() : entity.getZ();
		float oyaw = origin != null ? origin.yaw() : entity.getYaw();
		float opitch = origin != null ? origin.pitch() : entity.getPitch();

		ledger.deadVirginMobs.put(entity.getUuid(), new DeadVirginMob(
				entity.getUuid(), dimId, ox, oy, oz, oyaw, opitch, maxHp, nbt
		));
		ledger.markDirty();
	}

	public int getTotalRecordedBlocks() {
		return totalRecordedBlocks;
	}

	public int getDeadMobsCount() {
		return deadVirginMobs.size();
	}

	/**
	 * Полный Перезапуск Мира в Девственно Чистое Изначальное Состояние!
	 * Возвращает количество восстановленных блоков и воскрешённых существ: int[]{restoredBlocks, revivedMobs}.
	 */
	public int[] restoreEntireWorldToVirginState(MinecraftServer server) {
		final int[] stats = new int[]{0, 0};

		runWithBypass(() -> {
			// 1. Очищаем все миры от выброшенного мусора, стрел, динамита, падающих блоков и временных суммонов
			for (ServerWorld sw : server.getWorlds()) {
				if (!isTrackableDimension(sw)) {
					continue;
				}
				List<Entity> toDiscard = new ArrayList<>();
				List<LivingEntity> livingToHeal = new ArrayList<>();
				for (Entity e : sw.iterateEntities()) {
					if (e instanceof PlayerEntity) {
						continue;
					}
					if (e instanceof ItemEntity
							|| e instanceof ExperienceOrbEntity
							|| e instanceof ProjectileEntity
							|| e instanceof TntEntity
							|| e instanceof FallingBlockEntity
							|| e instanceof AreaEffectCloudEntity
							|| e.getCommandTags().contains(TAG_TEMP_SUMMON)) {
						toDiscard.add(e);
					} else if (e instanceof LivingEntity le && le.isAlive()) {
						livingToHeal.add(le);
					}
				}
				for (Entity trash : toDiscard) {
					trash.discard();
				}

				// 2. Возвращаем всех живых мобов на их изначальные координаты, полностью лечим и снимаем агрессию!
				for (LivingEntity le : livingToHeal) {
					le.setHealth(le.getMaxHealth());
					le.setFireTicks(0);
					le.clearStatusEffects();
					if (le instanceof MobEntity mob) {
						mob.setTarget(null);
						mob.setAttacking(false);
					}
					if (le instanceof Angerable ang) {
						ang.stopAnger();
					}
					MobVirginOrigin origin = virginOrigins.get(le.getUuid());
					if (origin != null && origin.dimId().equals(sw.getRegistryKey().getValue().toString())) {
						le.refreshPositionAndAngles(origin.x(), origin.y(), origin.z(), origin.yaw(), origin.pitch());
						le.setVelocity(Vec3d.ZERO);
						le.velocityModified = true;
					}
				}
			}

			// 3. Восстанавливаем ВСЕ когда-либо сломанные, взорванные, сгоревшие и поставленные блоки мира!
			// Используем NOTIFY_LISTENERS | FORCE_STATE | SKIP_DROPS, чтобы факелы/двери/посевы не выпадали при откате!
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
				// Восстанавливаем в обратном порядке изменений
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

			// 4. Воскрешаем ВСЕХ убитых за историю мира мобов на их девственных местах с полным здоровьем!
			for (DeadVirginMob dm : deadVirginMobs.values()) {
				RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dm.dimId()));
				ServerWorld sw = server.getWorld(worldKey);
				if (sw == null) {
					continue;
				}
				// Пропускаем, если существо с этим UUID каким-то образом уже живо
				if (sw.getEntity(dm.uuid()) != null) {
					continue;
				}
				NbtCompound copy = dm.cleanNbt().copy();
				copy.putFloat("Health", dm.maxHealth());
				Entity revived = EntityType.loadEntityWithPassengers(copy, sw, e -> {
					e.refreshPositionAndAngles(dm.x(), dm.y(), dm.z(), dm.yaw(), dm.pitch());
					e.setVelocity(Vec3d.ZERO);
					return e;
				});
				if (revived != null) {
					if (revived instanceof LivingEntity le) {
						le.setHealth(le.getMaxHealth());
						le.setFireTicks(0);
					}
					if (revived instanceof MobEntity mob) {
						mob.setTarget(null);
					}
					if (sw.spawnEntity(revived)) {
						stats[1]++;
					}
				}
			}

			// 5. Очищаем летопись изменений — мир снова девственно чист и готов к новому циклу истории!
			originalBlocksByDim.clear();
			originalBlockEntitiesByDim.clear();
			deadVirginMobs.clear();
			totalRecordedBlocks = 0;
			markDirty();
		});

		return stats;
	}

	public static BlockPos findSafeWorldSpawnPos(ServerWorld overworld) {
		BlockPos spawn = overworld.getSpawnPos();
		overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
		int topY = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
		return new BlockPos(spawn.getX(), Math.max(overworld.getBottomY() + 2, topY), spawn.getZ());
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
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

		NbtList originsList = new NbtList();
		for (Map.Entry<UUID, MobVirginOrigin> oe : virginOrigins.entrySet()) {
			NbtCompound o = new NbtCompound();
			o.putUuid("Id", oe.getKey());
			MobVirginOrigin val = oe.getValue();
			o.putString("Dim", val.dimId());
			o.putDouble("X", val.x());
			o.putDouble("Y", val.y());
			o.putDouble("Z", val.z());
			o.putFloat("Yaw", val.yaw());
			o.putFloat("Pitch", val.pitch());
			originsList.add(o);
		}
		nbt.put("VirginOrigins", originsList);

		NbtList deadList = new NbtList();
		for (DeadVirginMob dm : deadVirginMobs.values()) {
			NbtCompound d = new NbtCompound();
			d.putUuid("Id", dm.uuid());
			d.putString("Dim", dm.dimId());
			d.putDouble("X", dm.x());
			d.putDouble("Y", dm.y());
			d.putDouble("Z", dm.z());
			d.putFloat("Yaw", dm.yaw());
			d.putFloat("Pitch", dm.pitch());
			d.putFloat("MaxHp", dm.maxHealth());
			d.put("Nbt", dm.cleanNbt());
			deadList.add(d);
		}
		nbt.put("DeadVirginMobs", deadList);
		return nbt;
	}

	public static WorldVirginLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		WorldVirginLedger ledger = new WorldVirginLedger();
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

		if (nbt.contains("VirginOrigins", NbtElement.LIST_TYPE)) {
			NbtList ol = nbt.getList("VirginOrigins", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < ol.size(); i++) {
				NbtCompound o = ol.getCompound(i);
				if (o.containsUuid("Id")) {
					ledger.virginOrigins.put(o.getUuid("Id"), new MobVirginOrigin(
							o.getString("Dim"),
							o.getDouble("X"),
							o.getDouble("Y"),
							o.getDouble("Z"),
							o.getFloat("Yaw"),
							o.getFloat("Pitch")
					));
				}
			}
		}

		if (nbt.contains("DeadVirginMobs", NbtElement.LIST_TYPE)) {
			NbtList dl = nbt.getList("DeadVirginMobs", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < dl.size(); i++) {
				NbtCompound d = dl.getCompound(i);
				if (d.containsUuid("Id")) {
					UUID id = d.getUuid("Id");
					ledger.deadVirginMobs.put(id, new DeadVirginMob(
							id,
							d.getString("Dim"),
							d.getDouble("X"),
							d.getDouble("Y"),
							d.getDouble("Z"),
							d.getFloat("Yaw"),
							d.getFloat("Pitch"),
							d.getFloat("MaxHp"),
							d.getCompound("Nbt")
					));
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
