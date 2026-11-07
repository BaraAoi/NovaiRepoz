package com.novaimod.equipment;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.novaimod.ai.evolution.MobEvolutionManager;
import com.novaimod.item.RealityRiftBladeItem;
import com.novaimod.registry.ModEffects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * Управляет бонусами полного комплекта «Альтимейт Брони» (4/4):
 * 1) Полное поглощение любого урона в игре, кроме процентного урона («Процентный урон» / TAG_PERCENT_MELEE / Клинок Разлома).
 * 2) Полноценный креативный полёт даже в режиме Выживания и Приключения.
 */
public final class UltimateArmorManager {
	private static final Set<UUID> FLIGHT_GRANTED_BY_ARMOR = new HashSet<>();

	private UltimateArmorManager() {
	}

	public static boolean isWearingFullUltimateArmor(PlayerEntity player) {
		if (player == null) {
			return false;
		}
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack eq = player.getEquippedStack(slot);
			if (eq.isEmpty() || !(eq.getItem() instanceof UltimateArmorItem)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Проверяет, несёт ли данный удар эффект процентного урона (единственный тип урона, пробивающий Альтимейт Броню!).
	 */
	public static boolean isPercentageDamageHit(DamageSource source) {
		Entity attacker = source.getAttacker();
		if (attacker == null || source.getSource() != attacker || source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			return false;
		}
		if (attacker instanceof MobEntity mob) {
			if (mob.getCommandTags().contains(MobEvolutionManager.TAG_PERCENT_MELEE)
					&& MobEvolutionManager.isMeleeHostile(mob)) {
				return true;
			}
		}
		if (attacker instanceof LivingEntity living && living.getMainHandStack().getItem() instanceof RealityRiftBladeItem) {
			return true;
		}
		return false;
	}

	/**
	 * Возвращает ТОЛЬКО чистый процентный урон (1..4 ХП = 5%..20% от макс. здоровья), полностью поглощая весь остальной физический урон удара!
	 */
	public static float computePurePercentageDamageOnly(ServerPlayerEntity victim, DamageSource source) {
		Entity attacker = source.getAttacker();
		int step = (attacker instanceof LivingEntity le && le.getMainHandStack().getItem() instanceof RealityRiftBladeItem)
				? 4
				: (1 + victim.getRandom().nextInt(4));
		float pct = step * 0.05F;
		float purePercentHp = MathHelper.clamp((float) Math.round(victim.getMaxHealth() * pct), 1.0F, Math.max(4.0F, victim.getMaxHealth() * 0.20F));

		victim.addStatusEffect(new StatusEffectInstance(ModEffects.PERCENT_EROSION, 50, step - 1, false, true, true), attacker);
		victim.getServerWorld().spawnParticles(
				ParticleTypes.DAMAGE_INDICATOR,
				victim.getX(),
				victim.getBodyY(0.6D),
				victim.getZ(),
				step + 1,
				0.25D,
				0.25D,
				0.25D,
				0.08D
		);
		return purePercentHp;
	}

	public static void tickPlayers(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive()) {
				FLIGHT_GRANTED_BY_ARMOR.remove(player.getUuid());
				continue;
			}

			boolean fullUlt = isWearingFullUltimateArmor(player);
			if (fullUlt) {
				player.fallDistance = 0.0F;
				if (player.isOnFire()) {
					player.extinguish();
				}
				if (player.getAir() < player.getMaxAir()) {
					player.setAir(player.getMaxAir());
				}

				if (!player.getAbilities().allowFlying) {
					player.getAbilities().allowFlying = true;
					player.sendAbilitiesUpdate();
				}
				FLIGHT_GRANTED_BY_ARMOR.add(player.getUuid());

				// Лёгкие звёздные искры за спиной во время полёта в Альтимейт Броне
				if (player.getAbilities().flying && player.age % 4 == 0) {
					player.getServerWorld().spawnParticles(
							ParticleTypes.END_ROD,
							player.getX(),
							player.getY() + 0.2D,
							player.getZ(),
							1,
							0.25D,
							0.1D,
							0.25D,
							0.01D
					);
				}
			} else if (FLIGHT_GRANTED_BY_ARMOR.remove(player.getUuid())) {
				if (!player.isCreative() && !player.isSpectator()) {
					player.getAbilities().allowFlying = false;
					player.getAbilities().flying = false;
					player.sendAbilitiesUpdate();
					// Мягкое приземление на 3 секунды, если игрок снял Альтимейт Броню высоко в воздухе!
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false, false));
				}
			}
		}
	}
}
