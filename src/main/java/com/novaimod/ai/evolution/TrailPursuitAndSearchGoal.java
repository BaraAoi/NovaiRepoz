package com.novaimod.ai.evolution;

import java.util.EnumSet;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Преследование по следу + Потеря следа + Реакция на слух:
 * - Пока моб видит игрока, он запоминает его последнюю позицию.
 * - Если игрок скрылся из виду (или издал шум за углом), моб идёт к последней известной точке
 *   и осматривается вокруг неё несколько секунд.
 * - Дождь, вода и густая листва сбивают след намного быстрее.
 */
public class TrailPursuitAndSearchGoal extends Goal {
	private final PathAwareEntity mob;
	private final EvolvedMobAccess access;
	private int searchWanderTicker = 0;

	public TrailPursuitAndSearchGoal(PathAwareEntity mob) {
		this.mob = mob;
		this.access = (EvolvedMobAccess) mob;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (this.access.novai$getEvolutionLevel() < 0.10F || this.access.novai$isRetreating()) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target != null && target.isAlive() && !target.isSpectator()) {
			if (this.mob.canSee(target)) {
				// Обновляем свежий след, но не перехватываем управление у обычной боевой атаки
				this.access.novai$setLastKnownTargetPos(target.getBlockPos());
				int baseMemory = 90 + Math.round(this.access.novai$getEvolutionLevel() * 60.0F); // 4.5..7.5 сек
				this.access.novai$setTrailTicksLeft(baseMemory);
				return false;
			}
			// Потеряли цель из виду — идём по последнему следу!
			return this.access.novai$getLastKnownTargetPos() != null && this.access.novai$getTrailTicksLeft() > 0;
		}

		// Либо цели ещё нет, но моб услышал шаги/бой (Слух записал координаты шума!)
		return this.access.novai$getLastKnownTargetPos() != null && this.access.novai$getTrailTicksLeft() > 0;
	}

	@Override
	public boolean shouldContinue() {
		if (this.access.novai$isRetreating()) {
			return false;
		}
		LivingEntity target = this.mob.getTarget();
		if (target != null && target.isAlive() && this.mob.canSee(target)) {
			// Снова увидели игрока напрямую — передаём управление атакующим целям
			this.access.novai$setLastKnownTargetPos(target.getBlockPos());
			return false;
		}
		return this.access.novai$getLastKnownTargetPos() != null && this.access.novai$getTrailTicksLeft() > 0;
	}

	@Override
	public void start() {
		this.searchWanderTicker = 0;
		BlockPos pos = this.access.novai$getLastKnownTargetPos();
		if (pos != null) {
			this.mob.getNavigation().startMovingTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 1.08D);
		}
	}

	@Override
	public void tick() {
		BlockPos trailPos = this.access.novai$getLastKnownTargetPos();
		if (trailPos == null) {
			this.access.novai$setTrailTicksLeft(0);
			return;
		}

		// Потеря следа: дождь, вода или густые листья рядом резко ускоряют потерю следа!
		int decay = 1;
		World world = this.mob.getWorld();
		LivingEntity target = this.mob.getTarget();
		if (world.hasRain(this.mob.getBlockPos()) || world.hasRain(trailPos)) {
			decay += 2;
		}
		if (this.mob.isTouchingWater() || (target != null && target.isTouchingWater())) {
			decay += 3;
		}
		if (isDenseFoliageNearby(world, trailPos)) {
			decay += 2;
		}

		int left = Math.max(0, this.access.novai$getTrailTicksLeft() - decay);
		this.access.novai$setTrailTicksLeft(left);

		double distSq = this.mob.squaredDistanceTo(trailPos.getX() + 0.5D, trailPos.getY(), trailPos.getZ() + 0.5D);
		if (distSq > 2.6D * 2.6D) {
			if (this.mob.getNavigation().isIdle()) {
				this.mob.getNavigation().startMovingTo(trailPos.getX() + 0.5D, trailPos.getY(), trailPos.getZ() + 0.5D, 1.08D);
			}
			this.mob.getLookControl().lookAt(trailPos.getX() + 0.5D, trailPos.getY() + 1.2D, trailPos.getZ() + 0.5D);
		} else {
			// Пришли на последнее место — ищем игрока несколько секунд вокруг точки, вертя головой
			this.searchWanderTicker++;
			if (this.searchWanderTicker % 24 == 1) {
				int ox = this.mob.getRandom().nextBetween(-4, 4);
				int oz = this.mob.getRandom().nextBetween(-4, 4);
				this.mob.getNavigation().startMovingTo(
						trailPos.getX() + ox + 0.5D,
						trailPos.getY(),
						trailPos.getZ() + oz + 0.5D,
						0.92D
				);
			}
			double lookAngle = (this.searchWanderTicker * 0.22D);
			this.mob.getLookControl().lookAt(
					this.mob.getX() + Math.cos(lookAngle) * 4.0D,
					this.mob.getEyeY(),
					this.mob.getZ() + Math.sin(lookAngle) * 4.0D
			);
		}

		if (left <= 0) {
			this.access.novai$setLastKnownTargetPos(null);
			if (target != null && !this.mob.canSee(target)) {
				this.mob.setTarget(null);
			}
		}
	}

	private static boolean isDenseFoliageNearby(World world, BlockPos center) {
		int leaves = 0;
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = 0; dy <= 2; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					if (world.getBlockState(m).isIn(BlockTags.LEAVES)) {
						leaves++;
						if (leaves >= 3) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
