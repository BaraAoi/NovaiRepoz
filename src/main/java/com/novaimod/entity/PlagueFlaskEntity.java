package com.novaimod.entity;

import java.util.List;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * Чумная Колба Очищения Клана Белых Колпаков:
 * - Взрывается облаком изумрудно-бирюзового алхимического реагента.
 * - Исцеляет бойцов Клана Белых Колпаков (и игрока в Колпаке Чумного Доктора / владельца броска).
 * - Наносит сокрушительный урон ненавистной нежити, эндерменам (блокируя им телепорт!), всполохам и драконам!
 */
public class PlagueFlaskEntity extends ThrownItemEntity {
	public PlagueFlaskEntity(EntityType<? extends PlagueFlaskEntity> entityType, World world) {
		super(entityType, world);
	}

	public PlagueFlaskEntity(World world, LivingEntity owner) {
		super(ModEntities.PLAGUE_FLASK_ENTITY, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.WHITE_CAP_FLASK;
	}

	@Override
	protected double getGravity() {
		return 0.045D;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY(), this.getZ(), 2, 0.08D, 0.08D, 0.08D, 0.01D);
			sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY(), this.getZ(), 1, 0.05D, 0.05D, 0.05D, 0.005D);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		Entity owner = this.getOwner();
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.HOSTILE, 1.1F, 0.92F + sw.random.nextFloat() * 0.2F);
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 0.75F, 1.35F);

		sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 0.2D, this.getZ(), 32, 1.6D, 0.65D, 1.6D, 0.08D);
		sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.25D, this.getZ(), 22, 1.3D, 0.55D, 1.3D, 0.05D);
		sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.15D, this.getZ(), 14, 1.2D, 0.35D, 1.2D, 0.03D);

		Box splashBox = this.getBoundingBox().expand(4.4D, 2.8D, 4.4D);
		List<LivingEntity> targets = sw.getEntitiesByClass(LivingEntity.class, splashBox, LivingEntity::isAlive);
		for (LivingEntity target : targets) {
			if (target instanceof WhiteCapEntity ally) {
				ally.heal(8.0F);
				ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0, false, false));
				continue;
			}

			if (target == owner) {
				target.heal(5.0F);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 80, 1, false, true));
				continue;
			}

			if (owner instanceof WhiteCapEntity clanThrower && target instanceof PlayerEntity p
					&& WhiteCapEntity.isWearingClanHood(p) && !clanThrower.isPlayerMarkedEnemy(p)) {
				p.heal(4.0F);
				continue;
			}

			boolean abomination = WhiteCapEntity.isHatedAbomination(target);
			float dmg = abomination ? 38.0F : 14.0F;

			if (target instanceof EnderDragonEntity dragon) {
				WhiteCapEntity.damageEnderDragon(sw, dragon, owner instanceof LivingEntity le ? le : null, 42.0F);
				continue;
			}

			WhiteCapEntity.applyAntiEnderSealIfNeeded(target);
			if (owner instanceof LivingEntity livingOwner) {
				target.damage(sw.getDamageSources().indirectMagic(this, livingOwner), dmg);
			} else {
				target.damage(sw.getDamageSources().magic(), dmg);
			}
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 90, abomination ? 2 : 1));
			if (abomination) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
			}
		}

		// Также проверим Эндер-дракона на чуть большем радиусе (у него огромный хитбокс частей тела)
		List<EnderDragonEntity> dragons = sw.getEntitiesByClass(EnderDragonEntity.class, this.getBoundingBox().expand(8.0D), LivingEntity::isAlive);
		for (EnderDragonEntity dragon : dragons) {
			WhiteCapEntity.damageEnderDragon(sw, dragon, owner instanceof LivingEntity le ? le : null, 42.0F);
		}

		this.discard();
	}
}
