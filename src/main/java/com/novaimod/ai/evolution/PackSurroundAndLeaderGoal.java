package com.novaimod.ai.evolution;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Охота стаей + Координация с Лидером группы:
 * - Вне боя участники группы держатся рядом со своим лидером.
 * - В бою (когда >= 2 моба охотятся на одного игрока) каждый получает свой угловой сектор
 *   и плавно смещается по дуге вбок при сближении, окружая игрока с разных сторон вместо бега гуськом!
 */
public class PackSurroundAndLeaderGoal extends Goal {
	private final PathAwareEntity mob;
	private final EvolvedMobAccess access;

	public PackSurroundAndLeaderGoal(PathAwareEntity mob) {
		this.mob = mob;
		this.access = (EvolvedMobAccess) mob;
		// Не блокируем основную боевую цель (ZombieAttackGoal / BowAttackGoal), работая параллельно с ней
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		if (this.access.novai$getEvolutionLevel() < 0.32F
				|| this.access.novai$isRetreating()
				|| this.access.novai$getDisorganizedTicks() > 0) {
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			UUID leaderId = this.access.novai$getLeaderUuid();
			if (leaderId != null && !this.access.novai$isPackLeader() && this.mob.getWorld() instanceof ServerWorld sw) {
				Entity leader = sw.getEntity(leaderId);
				return leader != null && leader.isAlive() && this.mob.squaredDistanceTo(leader) > 7.5D * 7.5D;
			}
			return false;
		}

		if (this.mob instanceof AbstractSkeletonEntity
				&& this.access.novai$getRareVariant() != EvolvedMobAccess.RareMobVariant.MELEE_SKELETON) {
			return false;
		}

		double dSq = this.mob.squaredDistanceTo(target);
		return this.access.novai$getPackSize() >= 2 && dSq > 2.6D * 2.6D && dSq < 16.0D * 16.0D;
	}

	@Override
	public boolean shouldContinue() {
		return canStart();
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			if (this.mob.age % 20 == 0 && this.mob.getNavigation().isIdle() && this.mob.getWorld() instanceof ServerWorld sw) {
				UUID leaderId = this.access.novai$getLeaderUuid();
				if (leaderId != null) {
					Entity leader = sw.getEntity(leaderId);
					if (leader != null && leader.isAlive()) {
						this.mob.getNavigation().startMovingTo(leader, 1.0D);
					}
				}
			}
			return;
		}

		// Тангенциальное (круговое) смещение для веерного окружения игрока с разных сторон
		int slot = this.access.novai$getPackSlot();
		if (slot == 0) {
			return; // Первый идёт в лоб, остальные обходят слева и справа под своими углами!
		}

		Vec3d toPlayer = target.getPos().subtract(this.mob.getPos()).multiply(1.0D, 0.0D, 1.0D);
		double dist = toPlayer.length();
		if (dist < 2.6D || dist > 16.0D) {
			return;
		}
		Vec3d forward = toPlayer.normalize();
		// Перпендикулярный вектор влево или вправо в зависимости от номера в стае
		double sideSign = (slot % 2 == 1) ? 1.0D : -1.0D;
		double arcStrength = 0.065D * Math.min(1.5D, 1.0D + (slot / 2) * 0.25D);
		Vec3d tangent = new Vec3d(-forward.z * sideSign, 0.0D, forward.x * sideSign);

		if (this.mob.isOnGround()) {
			Vec3d vel = this.mob.getVelocity();
			this.mob.setVelocity(
					vel.x + tangent.x * arcStrength,
					vel.y,
					vel.z + tangent.z * arcStrength
			);
		}
	}
}
