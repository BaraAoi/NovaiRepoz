package com.novaimod.ai.evolution;

import java.util.EnumSet;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

/**
 * Тактика скелетов:
 * 1) Фланговые атаки — скелеты стараются зайти сбоку или сзади от взгляда игрока, а не стоять напротив щита/прицела.
 * 2) Умная тактика против лучников — скелеты смещаются между выстрелами и особенно активно маневрируют,
 *    когда видят в руках игрока лук или арбалет.
 */
public class SkeletonTacticalCombatGoal extends Goal {
	private final AbstractSkeletonEntity skeleton;
	private final EvolvedMobAccess access;
	private int maneuverTicks = 0;
	private double destX;
	private double destY;
	private double destZ;
	private int cooldown = 0;

	public SkeletonTacticalCombatGoal(AbstractSkeletonEntity skeleton) {
		this.skeleton = skeleton;
		this.access = (EvolvedMobAccess) skeleton;
		// Не блокируем стрельбу (BowAttackGoal использует MOVE + LOOK, поэтому даём короткий импульс смещения между выстрелами)
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		if (this.access.novai$getEvolutionLevel() < 0.30F || this.access.novai$isRetreating()) {
			return false;
		}
		if (this.access.novai$getRareVariant() == EvolvedMobAccess.RareMobVariant.MELEE_SKELETON) {
			return false;
		}
		if (this.cooldown > 0) {
			this.cooldown--;
			return false;
		}

		LivingEntity target = this.skeleton.getTarget();
		if (target == null || !target.isAlive() || !this.skeleton.canSee(target)) {
			return false;
		}

		double distSq = this.skeleton.squaredDistanceTo(target);
		if (distSq < 3.0D * 3.0D || distSq > 20.0D * 20.0D) {
			return false;
		}

		// 1. Сразу после выстрела — перебежка в сторону, чтобы скелета было сложнее поймать
		if (this.access.novai$consumeShotReposition()) {
			return pickFlankOrDodgePoint(target, true);
		}

		// 2. Если игрок целится из лука/арбалета — активный боковой рывок
		if (isHoldingRangedWeapon(target) && target.isUsingItem()) {
			return pickFlankOrDodgePoint(target, true);
		}

		// 3. Фланговый обход: если скелет стоит прямо перед лицом игрока — уходит вбок или за спину
		Vec3d playerLook = target.getRotationVec(1.0F).multiply(1.0D, 0.0D, 1.0D).normalize();
		Vec3d playerToSkel = this.skeleton.getPos().subtract(target.getPos()).multiply(1.0D, 0.0D, 1.0D).normalize();
		double frontDot = playerLook.dotProduct(playerToSkel);
		boolean lovesFlank = this.access.novai$getTrait() == EvolvedMobAccess.MobTrait.FLANKER
				|| this.access.novai$getTrait() == EvolvedMobAccess.MobTrait.SKIRMISHER;

		if (frontDot > (lovesFlank ? 0.35D : 0.62D)) {
			return pickFlankOrDodgePoint(target, false);
		}

		return false;
	}

	private boolean pickFlankOrDodgePoint(LivingEntity target, boolean postShotDodge) {
		Vec3d toSkel = this.skeleton.getPos().subtract(target.getPos());
		double curDist = Math.clamp(Math.hypot(toSkel.x, toSkel.z), 6.5D, 12.5D);
		double curAngle = Math.atan2(toSkel.z, toSkel.x);

		// Выбираем смещение сбоку/сзади (от 40 до 85 градусов по дуге вокруг игрока)
		double sign = this.skeleton.getRandom().nextBoolean() ? 1.0D : -1.0D;
		if (this.access.novai$getPackSlot() > 0) {
			sign = (this.access.novai$getPackSlot() % 2 == 0) ? 1.0D : -1.0D;
		}
		double stepAngle = (postShotDodge ? 0.55D : 0.95D) * sign;
		double newAngle = curAngle + stepAngle;

		this.destX = target.getX() + Math.cos(newAngle) * curDist;
		this.destZ = target.getZ() + Math.sin(newAngle) * curDist;
		this.destY = target.getY();
		this.maneuverTicks = postShotDodge ? 16 : 26;
		return true;
	}

	@Override
	public boolean shouldContinue() {
		return this.maneuverTicks > 0 && !this.access.novai$isRetreating() && this.skeleton.getTarget() != null;
	}

	@Override
	public void tick() {
		this.maneuverTicks--;
		LivingEntity target = this.skeleton.getTarget();
		if (target == null) {
			return;
		}

		// Плавное боковое смещение к фланговой точке, сохраняя взгляд на игроке
		Vec3d delta = new Vec3d(this.destX - this.skeleton.getX(), 0.0D, this.destZ - this.skeleton.getZ());
		if (delta.lengthSquared() > 0.6D) {
			Vec3d dir = delta.normalize();
			double speed = (this.access.novai$getTrait() == EvolvedMobAccess.MobTrait.SKIRMISHER) ? 0.14D : 0.105D;
			Vec3d vel = this.skeleton.getVelocity();
			this.skeleton.setVelocity(
					vel.x * 0.55D + dir.x * speed,
					vel.y,
					vel.z * 0.55D + dir.z * speed
			);
		}

		if (this.maneuverTicks <= 0) {
			this.cooldown = (this.access.novai$getTrait() == EvolvedMobAccess.MobTrait.SKIRMISHER) ? 18 : 32;
		}
	}

	private static boolean isHoldingRangedWeapon(LivingEntity entity) {
		ItemStack main = entity.getMainHandStack();
		ItemStack off = entity.getOffHandStack();
		return main.getItem() instanceof BowItem
				|| main.getItem() instanceof CrossbowItem
				|| off.getItem() instanceof BowItem
				|| off.getItem() instanceof CrossbowItem;
	}
}
