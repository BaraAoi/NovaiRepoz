package com.novaimod.ai.evolution;

import java.util.EnumSet;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Отступление скелетов и пауков:
 * Когда здоровье опускается низко (<= 35%), моб отходит в тень/укрытие на безопасную дистанцию,
 * постепенно восстанавливает силы и снова возвращается к охоте на игрока.
 */
public class RetreatAndRecoverGoal extends Goal {
	private final PathAwareEntity mob;
	private final EvolvedMobAccess access;
	private LivingEntity savedTarget;
	private int retreatTicks = 0;

	public RetreatAndRecoverGoal(PathAwareEntity mob) {
		this.mob = mob;
		this.access = (EvolvedMobAccess) mob;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (this.access.novai$getEvolutionLevel() < 0.35F) {
			return false;
		}
		if (this.access.novai$getRetreatCooldown() > 0) {
			this.access.novai$setRetreatCooldown(this.access.novai$getRetreatCooldown() - 1);
			return false;
		}

		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		float hpRatio = this.mob.getHealth() / this.mob.getMaxHealth();
		return hpRatio <= 0.35F;
	}

	@Override
	public boolean shouldContinue() {
		if (!this.mob.isAlive() || this.retreatTicks >= 200) {
			return false;
		}
		float hpRatio = this.mob.getHealth() / this.mob.getMaxHealth();
		return hpRatio < 0.72F;
	}

	@Override
	public void start() {
		this.savedTarget = this.mob.getTarget();
		this.retreatTicks = 0;
		this.access.novai$setRetreating(true);
		// Прекращаем наступательную атаку на время перегруппировки
		this.mob.stopUsingItem();

		// Звуковая подсказка игроку, чтобы он понимал манёвр отступления моба
		this.mob.getWorld().playSound(
				null,
				this.mob.getBlockPos(),
				(this.mob instanceof SpiderEntity) ? SoundEvents.ENTITY_SPIDER_HURT : SoundEvents.ENTITY_SKELETON_STEP,
				SoundCategory.HOSTILE,
				0.65F,
				0.75F
		);
		moveAwayTowardDarkCover();
	}

	@Override
	public void tick() {
		this.retreatTicks++;
		if (this.retreatTicks % 18 == 0) {
			moveAwayTowardDarkCover();
		}

		double distSq = (this.savedTarget != null && this.savedTarget.isAlive())
				? this.mob.squaredDistanceTo(this.savedTarget)
				: 256.0D;

		// Когда моб отошёл на безопасное расстояние или скрылся из виду — плавно восстанавливает здоровье
		boolean outOfSight = this.savedTarget == null || !this.mob.canSee(this.savedTarget);
		if ((distSq > 9.0D * 9.0D || outOfSight) && this.retreatTicks % 24 == 0) {
			float nextHp = Math.min(this.mob.getMaxHealth() * 0.78F, this.mob.getHealth() + 1.5F);
			this.mob.setHealth(nextHp);
			if (this.mob.getWorld() instanceof ServerWorld sw) {
				sw.spawnParticles(
						ParticleTypes.SMOKE,
						this.mob.getX(),
						this.mob.getBodyY(0.6D),
						this.mob.getZ(),
						3,
						0.2D,
						0.2D,
						0.2D,
						0.01D
				);
			}
		}
	}

	@Override
	public void stop() {
		this.access.novai$setRetreating(false);
		this.access.novai$setRetreatCooldown(360); // Не чаще раза в 18 секунд
		// Восстановившись, возвращаемся в бой!
		if (this.savedTarget != null && this.savedTarget.isAlive() && !this.savedTarget.isSpectator()) {
			this.mob.setTarget(this.savedTarget);
			this.mob.getNavigation().startMovingTo(this.savedTarget, 1.12D);
		}
	}

	private void moveAwayTowardDarkCover() {
		Vec3d away;
		if (this.savedTarget != null) {
			away = this.mob.getPos().subtract(this.savedTarget.getPos());
			away = new Vec3d(away.x, 0.0D, away.z);
			if (away.lengthSquared() < 0.01D) {
				away = new Vec3d(1.0D, 0.0D, 0.0D);
			} else {
				away = away.normalize();
			}
		} else {
			double a = this.mob.getRandom().nextDouble() * Math.PI * 2.0D;
			away = new Vec3d(Math.cos(a), 0.0D, Math.sin(a));
		}

		// Ищем среди 3 веерных направлений самое тёмное место для укрытия
		BlockPos bestPos = null;
		int darkestLight = 16;
		for (double angleOffset : new double[]{-0.55D, 0.0D, 0.55D}) {
			double cos = Math.cos(angleOffset);
			double sin = Math.sin(angleOffset);
			double rx = away.x * cos - away.z * sin;
			double rz = away.x * sin + away.z * cos;
			BlockPos candidate = BlockPos.ofFloored(
					this.mob.getX() + rx * 12.0D,
					this.mob.getY(),
					this.mob.getZ() + rz * 12.0D
			);
			int light = this.mob.getWorld().getLightLevel(candidate);
			if (light < darkestLight) {
				darkestLight = light;
				bestPos = candidate;
			}
		}

		if (bestPos != null) {
			double speed = (this.access.novai$getTrait() == EvolvedMobAccess.MobTrait.SKITTERER) ? 1.38D : 1.22D;
			this.mob.getNavigation().startMovingTo(bestPos.getX() + 0.5D, bestPos.getY(), bestPos.getZ() + 0.5D, speed);
		}
	}
}
