package com.novaimod.ai.evolution;

import java.util.UUID;

import net.minecraft.util.math.BlockPos;

/**
 * Интерфейс доступа к эволюционным и тактическим данным моба (внедряется через MobEntityMixin).
 * Согласно принципу «не делать всё сразу», каждый моб получает не более 1 индивидуальной черты,
 * а возможности открываются плавно по мере развития игроков и мира.
 */
public interface EvolvedMobAccess {
	enum MobTrait {
		NONE(""),
		// Зомби: одни быстрые, другие живучие, третьи чутко реагируют на звук
		SPRINTER("sprinter"),
		TOUGH("tough"),
		KEEN_EAR("keen_ear"),
		// Скелеты: меткий снайпер, подвижный застрельщик, обходчик с фланга
		SNIPER("sniper"),
		SKIRMISHER("skirmisher"),
		FLANKER("flanker"),
		// Пауки: теневой ловец, юркий скакун
		STALKER("stalker"),
		SKITTERER("skitterer");

		private final String id;

		MobTrait(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public static MobTrait fromId(String id) {
			if (id == null || id.isEmpty()) {
				return NONE;
			}
			for (MobTrait t : values()) {
				if (t.id.equals(id)) {
					return t;
				}
			}
			return NONE;
		}
	}

	enum RareMobVariant {
		NONE(""),
		MELEE_SKELETON("melee_skeleton"),
		FAST_CLIMBER_SPIDER("fast_climber_spider");

		private final String id;

		RareMobVariant(String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public static RareMobVariant fromId(String id) {
			if (id == null || id.isEmpty()) {
				return NONE;
			}
			for (RareMobVariant v : values()) {
				if (v.id.equals(id)) {
					return v;
				}
			}
			return NONE;
		}
	}

	boolean novai$isInitialized();

	void novai$setInitialized(boolean initialized);

	float novai$getEvolutionLevel();

	void novai$setEvolutionLevel(float level);

	MobTrait novai$getTrait();

	void novai$setTrait(MobTrait trait);

	RareMobVariant novai$getRareVariant();

	void novai$setRareVariant(RareMobVariant variant);

	String novai$getBiomeTag();

	void novai$setBiomeTag(String biomeTag);

	boolean novai$isPackLeader();

	void novai$setPackLeader(boolean packLeader);

	UUID novai$getLeaderUuid();

	void novai$setLeaderUuid(UUID leaderUuid);

	int novai$getDisorganizedTicks();

	void novai$setDisorganizedTicks(int ticks);

	int novai$getPackSlot();

	void novai$setPackSlot(int slot);

	int novai$getPackSize();

	void novai$setPackSize(int size);

	BlockPos novai$getLastKnownTargetPos();

	void novai$setLastKnownTargetPos(BlockPos pos);

	int novai$getTrailTicksLeft();

	void novai$setTrailTicksLeft(int ticks);

	boolean novai$isRetreating();

	void novai$setRetreating(boolean retreating);

	int novai$getRetreatCooldown();

	void novai$setRetreatCooldown(int ticks);

	boolean novai$consumeShotReposition();

	void novai$markShotFired();

	boolean novai$isNearTorchLight();

	void novai$setNearTorchLight(boolean nearTorch);
}
