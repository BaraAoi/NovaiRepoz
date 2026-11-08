package com.novaimod.entity;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/**
 * «Святая Вода Собора Люче» — освящённый ответ Церкви на чумные колбы КБК!
 * - При разбитии исцеляет Люче, Сантино, рыцарей Ордена и дружественных игроков (+10 HP),
 *   полностью смывая Яд, Иссушение, Слабость и Замедление от колб КБК!
 * - Наносит сокрушительный святой урон (24 ед.) еретикам Клана Белых Колпаков (КБК) и нежити!
 */
public class HolyWaterEntity extends ThrownItemEntity {
	public HolyWaterEntity(EntityType<? extends HolyWaterEntity> entityType, World world) {
		super(entityType, world);
	}

	public HolyWaterEntity(World world, LivingEntity owner) {
		super(ModEntities.HOLY_WATER_ENTITY, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.HOLY_WATER;
	}

	@Override
	protected double getGravity() {
		return 0.045D;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 2, 0.07D, 0.07D, 0.07D, 0.01D);
			sw.spawnParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 2, 0.06D, 0.06D, 0.06D, 0.01D);
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		Entity owner = this.getOwner();
		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.NEUTRAL, 1.1F, 1.15F);
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1.2F, 1.25F);

		sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.25D, this.getZ(), 36, 1.5D, 0.6D, 1.5D, 0.07D);
		sw.spawnParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.2D, this.getZ(), 28, 1.4D, 0.5D, 1.4D, 0.06D);

		Box splashBox = this.getBoundingBox().expand(4.5D, 2.8D, 4.5D);
		List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, splashBox, LivingEntity::isAlive);
		for (LivingEntity target : list) {
			// 1. Союзники Церкви Света (Орден, Люче, Сантино, бросивший и праведные игроки)
			boolean isSantino = target instanceof WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG);
			if (target instanceof HolyOrderEntity || target instanceof LuceEntity || isSantino || target == owner) {
				cleanseAndBless(target, 10.0F);
				continue;
			}

			if (target instanceof ServerPlayerEntity sp) {
				if (WhiteCapEntity.isWearingClanHood(sp)) {
					// Игрок в маске еретика КБК получает святой ожог!
					sp.damage(sw.getDamageSources().magic(), 14.0F);
					sp.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0));
					continue;
				}
				ModPersistentState.PlayerFinanceData acc = ModPersistentState.get(sw.getServer()).getAccount(sp.getUuid());
				if (acc.holyOrderRep >= -10) {
					cleanseAndBless(sp, 8.0F);
					continue;
				}
			}

			// 2. Главные враги Церкви — Клан Белых Колпаков (КБК) и нежить!
			if (target instanceof WhiteCapEntity kbk) {
				if (owner instanceof LivingEntity le) {
					kbk.damage(sw.getDamageSources().indirectMagic(this, le), 24.0F);
				} else {
					kbk.damage(sw.getDamageSources().magic(), 24.0F);
				}
				kbk.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
				kbk.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 120, 0));
				continue;
			}

			if (target.getType().isIn(EntityTypeTags.UNDEAD) || target instanceof Monster || target instanceof HostileEntity) {
				if (target instanceof ModNpcEntity || target instanceof CatgirlEntity) {
					continue;
				}
				float dmg = target.getType().isIn(EntityTypeTags.UNDEAD) ? 26.0F : 14.0F;
				if (owner instanceof LivingEntity le) {
					target.damage(sw.getDamageSources().indirectMagic(this, le), dmg);
				} else {
					target.damage(sw.getDamageSources().magic(), dmg);
				}
				target.setOnFireFor(5);
			}
		}

		this.discard();
	}

	private static void cleanseAndBless(LivingEntity ally, float healAmt) {
		ally.removeStatusEffect(StatusEffects.POISON);
		ally.removeStatusEffect(StatusEffects.WITHER);
		ally.removeStatusEffect(StatusEffects.SLOWNESS);
		ally.removeStatusEffect(StatusEffects.WEAKNESS);
		ally.removeStatusEffect(StatusEffects.BLINDNESS);
		ally.removeStatusEffect(StatusEffects.DARKNESS);
		ally.heal(healAmt);
		ally.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1, false, true));
	}
}
