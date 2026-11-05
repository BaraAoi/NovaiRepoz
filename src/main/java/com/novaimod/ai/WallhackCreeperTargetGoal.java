package com.novaimod.ai;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;

/**
 * Позволяет криперам видеть и преследовать игроков всегда, сквозь любые стены и блоки,
 * в радиусе 64 блоков, а также передавать цель ближайшим сородичам.
 */
public class WallhackCreeperTargetGoal extends Goal {
	private static final double DETECT_RANGE = 64.0D;
	private static final double KEEP_RANGE = 72.0D;
	private static final double ALERT_RANGE = 24.0D;

	private final CreeperEntity creeper;
	private PlayerEntity targetPlayer;
	private int scanCooldown;

	public WallhackCreeperTargetGoal(CreeperEntity creeper) {
		this.creeper = creeper;
		this.setControls(EnumSet.of(Goal.Control.TARGET));
	}

	@Override
	public boolean canStart() {
		if (this.scanCooldown > 0) {
			this.scanCooldown--;
			return false;
		}
		this.scanCooldown = 5;
		this.targetPlayer = findClosestSurvivalPlayer();
		return this.targetPlayer != null;
	}

	@Override
	public boolean shouldContinue() {
		LivingEntity current = this.creeper.getTarget();
		if (current == null) {
			current = this.targetPlayer;
		}
		if (!(current instanceof PlayerEntity player)) {
			return false;
		}
		if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
			return false;
		}
		if (this.creeper.squaredDistanceTo(player) > KEEP_RANGE * KEEP_RANGE) {
			return false;
		}
		this.targetPlayer = player;
		return true;
	}

	@Override
	public void start() {
		this.creeper.setTarget(this.targetPlayer);
		alertNearbyCreepers(this.targetPlayer);
		super.start();
	}

	@Override
	public void tick() {
		if (this.targetPlayer != null && this.targetPlayer.isAlive()) {
			this.creeper.setTarget(this.targetPlayer);
		}
	}

	@Override
	public void stop() {
		this.targetPlayer = null;
		this.creeper.setTarget(null);
	}

	private PlayerEntity findClosestSurvivalPlayer() {
		PlayerEntity closest = null;
		double bestSq = DETECT_RANGE * DETECT_RANGE;
		for (PlayerEntity player : this.creeper.getWorld().getPlayers()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			double distSq = this.creeper.squaredDistanceTo(player);
			if (distSq <= bestSq) {
				bestSq = distSq;
				closest = player;
			}
		}
		return closest;
	}

	private void alertNearbyCreepers(PlayerEntity player) {
		if (player == null) {
			return;
		}
		Box box = this.creeper.getBoundingBox().expand(ALERT_RANGE, 16.0D, ALERT_RANGE);
		List<CreeperEntity> others = this.creeper.getWorld().getEntitiesByClass(
				CreeperEntity.class,
				box,
				other -> other != this.creeper && other.isAlive() && other.getTarget() == null
		);
		for (CreeperEntity other : others) {
			other.setTarget(player);
		}
	}
}
