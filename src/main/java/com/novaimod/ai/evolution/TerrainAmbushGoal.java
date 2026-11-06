package com.novaimod.ai.evolution;

import java.util.EnumSet;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ловушки в ландшафте:
 * Мобы умеют использовать лестницы/лианы для подъёма к игроку, спрыгивать с безопасных уступов/обрывов
 * сверху или сбоку, а также выходить из воды по диагонали во фланг, не застревая в потоке.
 */
public class TerrainAmbushGoal extends Goal {
	private final PathAwareEntity mob;
	private final EvolvedMobAccess access;
	private int ledgeJumpCooldown = 0;

	public TerrainAmbushGoal(PathAwareEntity mob) {
		this.mob = mob;
		this.access = (EvolvedMobAccess) mob;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		if (this.access.novai$getEvolutionLevel() < 0.28F || this.access.novai$isRetreating()) {
			return false;
		}
		if (this.ledgeJumpCooldown > 0) {
			this.ledgeJumpCooldown--;
		}
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && !target.isSpectator();
	}

	@Override
	public boolean shouldContinue() {
		LivingEntity target = this.mob.getTarget();
		return target != null && target.isAlive() && !this.access.novai$isRetreating();
	}

	@Override
	public void tick() {
		LivingEntity target = this.mob.getTarget();
		if (target == null) {
			return;
		}
		World world = this.mob.getWorld();
		BlockPos pos = this.mob.getBlockPos();

		// 1. Использование лестниц и лиан, если игрок находится выше!
		double dy = target.getY() - this.mob.getY();
		if (dy > 1.2D) {
			BlockState at = world.getBlockState(pos);
			BlockState up = world.getBlockState(pos.up());
			if (at.isIn(BlockTags.CLIMBABLE) || up.isIn(BlockTags.CLIMBABLE)) {
				Vec3d v = this.mob.getVelocity();
				this.mob.setVelocity(v.x * 0.8D, 0.21D, v.z * 0.8D);
				return;
			}
			// Если рядом в радиусе 2 блоков есть лестница/лиана и путь застрял — подходим к ней
			if (this.mob.age % 20 == 0 && this.mob.getNavigation().isIdle()) {
				BlockPos climbSpot = findNearbyClimbable(world, pos);
				if (climbSpot != null) {
					this.mob.getNavigation().startMovingTo(climbSpot.getX() + 0.5D, climbSpot.getY(), climbSpot.getZ() + 0.5D, 1.1D);
					return;
				}
			}
		}

		// 2. Прыжок с обрыва/уступа сверху на игрока (неожиданная атака сверху с безопасной высоты 2..5 блоков)
		if (this.ledgeJumpCooldown <= 0 && dy <= -2.0D && dy >= -5.5D && this.mob.isOnGround()) {
			double horizSq = this.mob.squaredDistanceTo(target.getX(), this.mob.getY(), target.getZ());
			if (horizSq <= 5.5D * 5.5D && horizSq >= 1.2D * 1.2D) {
				Vec3d dir = new Vec3d(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ()).normalize();
				BlockPos dropCheck = BlockPos.ofFloored(this.mob.getX() + dir.x * 1.4D, target.getY() - 1.0D, this.mob.getZ() + dir.z * 1.4D);
				if (!world.getBlockState(dropCheck).isOf(Blocks.LAVA)) {
					this.mob.setVelocity(dir.x * 0.42D, 0.24D, dir.z * 0.42D);
					this.ledgeJumpCooldown = 90;
					return;
				}
			}
		}

		// 3. Умный выход из воды по диагонали (чтобы моб не барахтался на одном месте, а выходил сбоку)
		if (this.mob.isTouchingWater()) {
			Vec3d toTarget = new Vec3d(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ());
			if (toTarget.lengthSquared() > 0.2D) {
				Vec3d norm = toTarget.normalize();
				// Небольшое боковое смещение для выхода на берег под неожиданным углом
				double sideSign = (this.mob.getId() & 1) == 0 ? 0.35D : -0.35D;
				double sx = norm.x - norm.z * sideSign;
				double sz = norm.z + norm.x * sideSign;
				Vec3d v = this.mob.getVelocity();
				this.mob.setVelocity(
						v.x * 0.7D + sx * 0.06D,
						Math.max(v.y, 0.045D),
						v.z * 0.7D + sz * 0.06D
				);
			}
		}
	}

	private static BlockPos findNearbyClimbable(World world, BlockPos origin) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (world.getBlockState(m).isIn(BlockTags.CLIMBABLE)) {
						return m.toImmutable();
					}
				}
			}
		}
		return null;
	}
}
