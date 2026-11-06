package com.novaimod.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.ai.evolution.EvolvedMobAccess;
import com.novaimod.ai.evolution.MobEvolutionManager;
import com.novaimod.ai.evolution.PackSurroundAndLeaderGoal;
import com.novaimod.ai.evolution.RetreatAndRecoverGoal;
import com.novaimod.ai.evolution.SkeletonTacticalCombatGoal;
import com.novaimod.ai.evolution.TerrainAmbushGoal;
import com.novaimod.ai.evolution.TrailPursuitAndSearchGoal;
import com.novaimod.entity.PartyDollEntity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Подключает эволюционные ИИ-цели, индивидуальные черты, биомную адаптацию и быстрый подъём пауков по стенам.
 */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntity implements EvolvedMobAccess {
	@Shadow
	@Final
	protected GoalSelector goalSelector;

	@Unique
	private boolean novai$initialized = false;
	@Unique
	private float novai$evolutionLevel = 0.0F;
	@Unique
	private MobTrait novai$trait = MobTrait.NONE;
	@Unique
	private RareMobVariant novai$rareVariant = RareMobVariant.NONE;
	@Unique
	private String novai$biomeTag = "";
	@Unique
	private boolean novai$packLeader = false;
	@Unique
	private UUID novai$leaderUuid = null;
	@Unique
	private int novai$disorganizedTicks = 0;
	@Unique
	private int novai$packSlot = 0;
	@Unique
	private int novai$packSize = 1;
	@Unique
	private BlockPos novai$lastKnownTargetPos = null;
	@Unique
	private int novai$trailTicksLeft = 0;
	@Unique
	private boolean novai$retreating = false;
	@Unique
	private int novai$retreatCooldown = 0;
	@Unique
	private boolean novai$shotRepositionPending = false;
	@Unique
	private boolean novai$nearTorchLight = false;

	protected MobEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
		super(entityType, world);
	}

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void novai$friendlyWhilePartyDollActive(LivingEntity target, CallbackInfo ci) {
		if (target != null && (PartyDollEntity.isUnderActiveDollAura(this) || PartyDollEntity.isUnderActiveDollAura(target))) {
			ci.cancel();
		}
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void novai$attachEvolutionGoals(EntityType<? extends MobEntity> entityType, World world, CallbackInfo ci) {
		if (world == null || world.isClient()) {
			return;
		}
		Object self = this;
		if (!(self instanceof ZombieEntity) && !(self instanceof AbstractSkeletonEntity) && !(self instanceof SpiderEntity)) {
			return;
		}
		PathAwareEntity pathAware = (PathAwareEntity) self;

		// Приоритет 1: Отступление раненых скелетов и пауков + Преследование по последнему следу вне прямой видимости
		if (self instanceof AbstractSkeletonEntity || self instanceof SpiderEntity) {
			this.goalSelector.add(1, new RetreatAndRecoverGoal(pathAware));
		}
		this.goalSelector.add(1, new TrailPursuitAndSearchGoal(pathAware));

		// Параллельные тактические контроллеры (не конфликтуют с ванильной атакой)
		if (self instanceof AbstractSkeletonEntity skel) {
			this.goalSelector.add(2, new SkeletonTacticalCombatGoal(skel));
		}
		if (self instanceof ZombieEntity || self instanceof AbstractSkeletonEntity) {
			this.goalSelector.add(2, new PackSurroundAndLeaderGoal(pathAware));
		}
		this.goalSelector.add(3, new TerrainAmbushGoal(pathAware));
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void novai$tickEvolution(CallbackInfo ci) {
		Object self = this;
		if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
			if (!this.novai$initialized && MobEvolutionManager.isEvolvableMob(this)) {
				MobEvolutionManager.initializeMobIfNeeded((MobEntity) self, sw);
			}
			if (this.novai$disorganizedTicks > 0) {
				this.novai$disorganizedTicks--;
			}
		}

		// Быстрое лазание по стенам у редкой вариации паука-стенолаза и болотных пауков
		if (self instanceof SpiderEntity spider && spider.isClimbingWall()) {
			if (this.novai$rareVariant == RareMobVariant.FAST_CLIMBER_SPIDER) {
				Vec3d v = spider.getVelocity();
				if (v.y > 0.0D && v.y < 0.35D) {
					spider.setVelocity(v.x, 0.35D, v.z);
				}
			} else if ("swamp_spider".equals(this.novai$biomeTag)) {
				Vec3d v = spider.getVelocity();
				if (v.y > 0.0D && v.y < 0.25D) {
					spider.setVelocity(v.x, 0.25D, v.z);
				}
			}
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void novai$writeEvolutionNbt(NbtCompound nbt, CallbackInfo ci) {
		if (!this.novai$initialized) {
			return;
		}
		nbt.putBoolean("NovaiEvoInit", true);
		nbt.putFloat("NovaiEvoLevel", this.novai$evolutionLevel);
		nbt.putString("NovaiTrait", this.novai$trait.getId());
		nbt.putString("NovaiRareVar", this.novai$rareVariant.getId());
		nbt.putString("NovaiBiomeTag", this.novai$biomeTag);
		nbt.putBoolean("NovaiPackLeader", this.novai$packLeader);
		if (this.novai$leaderUuid != null) {
			nbt.putUuid("NovaiLeaderUuid", this.novai$leaderUuid);
		}
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void novai$readEvolutionNbt(NbtCompound nbt, CallbackInfo ci) {
		if (nbt.contains("NovaiEvoInit")) {
			this.novai$initialized = nbt.getBoolean("NovaiEvoInit");
			this.novai$evolutionLevel = nbt.getFloat("NovaiEvoLevel");
			this.novai$trait = MobTrait.fromId(nbt.getString("NovaiTrait"));
			this.novai$rareVariant = RareMobVariant.fromId(nbt.getString("NovaiRareVar"));
			this.novai$biomeTag = nbt.getString("NovaiBiomeTag");
			this.novai$packLeader = nbt.getBoolean("NovaiPackLeader");
			if (nbt.containsUuid("NovaiLeaderUuid")) {
				this.novai$leaderUuid = nbt.getUuid("NovaiLeaderUuid");
			}
		}
	}

	@Override
	public boolean novai$isInitialized() {
		return this.novai$initialized;
	}

	@Override
	public void novai$setInitialized(boolean initialized) {
		this.novai$initialized = initialized;
	}

	@Override
	public float novai$getEvolutionLevel() {
		return this.novai$evolutionLevel;
	}

	@Override
	public void novai$setEvolutionLevel(float level) {
		this.novai$evolutionLevel = level;
	}

	@Override
	public MobTrait novai$getTrait() {
		return this.novai$trait;
	}

	@Override
	public void novai$setTrait(MobTrait trait) {
		this.novai$trait = trait == null ? MobTrait.NONE : trait;
	}

	@Override
	public RareMobVariant novai$getRareVariant() {
		return this.novai$rareVariant;
	}

	@Override
	public void novai$setRareVariant(RareMobVariant variant) {
		this.novai$rareVariant = variant == null ? RareMobVariant.NONE : variant;
	}

	@Override
	public String novai$getBiomeTag() {
		return this.novai$biomeTag;
	}

	@Override
	public void novai$setBiomeTag(String biomeTag) {
		this.novai$biomeTag = biomeTag == null ? "" : biomeTag;
	}

	@Override
	public boolean novai$isPackLeader() {
		return this.novai$packLeader;
	}

	@Override
	public void novai$setPackLeader(boolean packLeader) {
		this.novai$packLeader = packLeader;
	}

	@Override
	public UUID novai$getLeaderUuid() {
		return this.novai$leaderUuid;
	}

	@Override
	public void novai$setLeaderUuid(UUID leaderUuid) {
		this.novai$leaderUuid = leaderUuid;
	}

	@Override
	public int novai$getDisorganizedTicks() {
		return this.novai$disorganizedTicks;
	}

	@Override
	public void novai$setDisorganizedTicks(int ticks) {
		this.novai$disorganizedTicks = ticks;
	}

	@Override
	public int novai$getPackSlot() {
		return this.novai$packSlot;
	}

	@Override
	public void novai$setPackSlot(int slot) {
		this.novai$packSlot = slot;
	}

	@Override
	public int novai$getPackSize() {
		return this.novai$packSize;
	}

	@Override
	public void novai$setPackSize(int size) {
		this.novai$packSize = size;
	}

	@Override
	public BlockPos novai$getLastKnownTargetPos() {
		return this.novai$lastKnownTargetPos;
	}

	@Override
	public void novai$setLastKnownTargetPos(BlockPos pos) {
		this.novai$lastKnownTargetPos = pos;
	}

	@Override
	public int novai$getTrailTicksLeft() {
		return this.novai$trailTicksLeft;
	}

	@Override
	public void novai$setTrailTicksLeft(int ticks) {
		this.novai$trailTicksLeft = ticks;
	}

	@Override
	public boolean novai$isRetreating() {
		return this.novai$retreating;
	}

	@Override
	public void novai$setRetreating(boolean retreating) {
		this.novai$retreating = retreating;
	}

	@Override
	public int novai$getRetreatCooldown() {
		return this.novai$retreatCooldown;
	}

	@Override
	public void novai$setRetreatCooldown(int ticks) {
		this.novai$retreatCooldown = ticks;
	}

	@Override
	public boolean novai$consumeShotReposition() {
		if (this.novai$shotRepositionPending) {
			this.novai$shotRepositionPending = false;
			return true;
		}
		return false;
	}

	@Override
	public void novai$markShotFired() {
		this.novai$shotRepositionPending = true;
	}

	@Override
	public boolean novai$isNearTorchLight() {
		return this.novai$nearTorchLight;
	}

	@Override
	public void novai$setNearTorchLight(boolean nearTorch) {
		this.novai$nearTorchLight = nearTorch;
	}
}
