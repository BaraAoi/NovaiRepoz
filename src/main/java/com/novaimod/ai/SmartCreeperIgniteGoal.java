package com.novaimod.ai;

import java.util.EnumSet;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Улучшенная логика подрыва крипера:
 * 1) Поджигается вблизи цели ДАЖЕ ЧЕРЕЗ СПЛОШНЫЕ БЛОКИ (без проверки линии видимости).
 * 2) Не останавливается на месте во время шипения, а продолжает бежать к цели.
 * 3) Если крипер преследует игрока и упирается в стену/баррикаду (застревает),
 *    он намеренно взрывает преграду, пробивая проход сквозь блоки!
 *    При этом радиус/сила взрыва остаётся ванильной.
 */
public class SmartCreeperIgniteGoal extends Goal {
	private static final double IGNITE_DISTANCE_SQ = 4.2D * 4.2D;
	private static final double DEFUSE_DISTANCE_SQ = 7.2D * 7.2D;
	private static final double BREACH_MAX_DIST_SQ = 16.0D * 16.0D;

	private final CreeperEntity creeper;
	private LivingEntity target;
	private Vec3d lastCheckedPos;
	private int stuckCheckTimer;
	private int stuckCounter;
	private boolean breachingWall;

	public SmartCreeperIgniteGoal(CreeperEntity creeper) {
		this.creeper = creeper;
		this.setControls(EnumSet.of(Goal.Control.MOVE));
	}

	@Override
	public boolean canStart() {
		LivingEntity candidate = this.creeper.getTarget();
		if (candidate == null || !candidate.isAlive()) {
			return this.creeper.getFuseSpeed() > 0;
		}
		double distSq = this.creeper.squaredDistanceTo(candidate);
		if (this.creeper.getFuseSpeed() > 0 || distSq <= IGNITE_DISTANCE_SQ) {
			this.breachingWall = false;
			return true;
		}
		// Проверка на пробитие стены, если путь к игроку преграждён блоками
		if (distSq <= BREACH_MAX_DIST_SQ && checkWallStuck(candidate)) {
			this.breachingWall = true;
			return true;
		}
		return false;
	}

	private boolean checkWallStuck(LivingEntity candidate) {
		this.stuckCheckTimer++;
		if (this.stuckCheckTimer < 10) {
			return false;
		}
		this.stuckCheckTimer = 0;
		Vec3d now = this.creeper.getPos();
		if (this.lastCheckedPos != null) {
			double movedSq = now.squaredDistanceTo(this.lastCheckedPos);
			boolean navIdleOrBlocked = this.creeper.getNavigation().isIdle() || this.creeper.horizontalCollision;
			if (movedSq < 0.09D && navIdleOrBlocked && this.creeper.squaredDistanceTo(candidate) > IGNITE_DISTANCE_SQ) {
				this.stuckCounter++;
			} else {
				this.stuckCounter = Math.max(0, this.stuckCounter - 1);
			}
		}
		this.lastCheckedPos = now;
		return this.stuckCounter >= 3;
	}

	@Override
	public void start() {
		this.target = this.creeper.getTarget();
		this.creeper.setFuseSpeed(1);
	}

	@Override
	public void stop() {
		this.target = null;
		this.breachingWall = false;
		this.stuckCounter = 0;
		this.creeper.setFuseSpeed(-1);
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public boolean shouldContinue() {
		if (this.target == null || !this.target.isAlive()) {
			return false;
		}
		double distSq = this.creeper.squaredDistanceTo(this.target);
		if (this.breachingWall) {
			return distSq <= BREACH_MAX_DIST_SQ;
		}
		return distSq <= DEFUSE_DISTANCE_SQ;
	}

	@Override
	public void tick() {
		if (this.target == null || !this.target.isAlive()) {
			this.creeper.setFuseSpeed(-1);
			return;
		}

		// Продолжает активно двигаться к игроку даже во время шипения!
		this.creeper.getLookControl().lookAt(this.target, 30.0F, 30.0F);
		this.creeper.getNavigation().startMovingTo(this.target, 1.25D);

		double distSq = this.creeper.squaredDistanceTo(this.target);
		if (this.breachingWall) {
			if (distSq > BREACH_MAX_DIST_SQ) {
				this.creeper.setFuseSpeed(-1);
				this.breachingWall = false;
			} else {
				this.creeper.setFuseSpeed(1);
			}
			return;
		}

		// Взрывается сквозь любые блоки без проверки canSee!
		if (distSq > DEFUSE_DISTANCE_SQ) {
			this.creeper.setFuseSpeed(-1);
		} else {
			this.creeper.setFuseSpeed(1);
		}
	}
}
