package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.PlagueFlaskEntity;
import com.novaimod.entity.WhiteCapEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * «Трость Чумного Магистра» — трофейное оружие Верховного Магистра Белых Колпаков:
 * - В ближнем бою сокрушает ненавистную нежить, эндерменов, всполохов и драконов (+печать запрета телепорта!).
 * - ПКМ — мечет бесконечные Чумные Колбы Очищения.
 * - Shift + ПКМ — выпускает Кольцо Очищения Скверны вокруг владельца и лечит его!
 */
public class PlagueCaneItem extends Item {
	public PlagueCaneItem(Settings settings) {
		super(settings.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker.getWorld() instanceof ServerWorld sw) {
			boolean abom = WhiteCapEntity.isHatedAbomination(target);
			float bonus = abom ? 28.0F : 10.0F;
			WhiteCapEntity.applyAntiEnderSealIfNeeded(target);
			if (target instanceof EnderDragonEntity dragon) {
				WhiteCapEntity.damageEnderDragon(sw, dragon, attacker, 36.0F);
			} else {
				target.damage(sw.getDamageSources().indirectMagic(attacker, attacker), bonus);
			}
			sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getBodyY(0.5D), target.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.05D);
			sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getBodyY(0.5D), target.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.02D);
		}
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient()) {
			return TypedActionResult.success(stack, true);
		}
		if (!(user instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) {
			return TypedActionResult.pass(stack);
		}

		if (sp.isSneaking()) {
			// Кольцо Очищения Скверны!
			sp.getItemCooldownManager().set(this, 65);
			sp.heal(6.0F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.2F, 0.95F);
			for (int i = 0; i < 36; i++) {
				double rad = (Math.PI * 2.0D * i) / 36.0D;
				sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
						sp.getX() + Math.cos(rad) * 4.5D, sp.getY() + 0.35D, sp.getZ() + Math.sin(rad) * 4.5D,
						2, 0.08D, 0.08D, 0.08D, 0.02D);
			}
			for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, sp.getBoundingBox().expand(5.5D, 3.0D, 5.5D), LivingEntity::isAlive)) {
				if (target == sp || target instanceof WhiteCapEntity) {
					continue;
				}
				float dmg = WhiteCapEntity.isHatedAbomination(target) ? 44.0F : 16.0F;
				WhiteCapEntity.applyAntiEnderSealIfNeeded(target);
				target.damage(sw.getDamageSources().playerAttack(sp), dmg);
				Vec3d push = target.getPos().subtract(sp.getPos()).normalize().multiply(1.15D);
				target.addVelocity(push.x, 0.42D, push.z);
				target.velocityModified = true;
			}
			return TypedActionResult.success(stack, false);
		}

		sp.getItemCooldownManager().set(this, 16);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.PLAYERS, 1.0F, 1.05F);
		PlagueFlaskEntity flask = new PlagueFlaskEntity(sw, sp);
		flask.setPosition(sp.getX(), sp.getEyeY() - 0.1D, sp.getZ());
		flask.setVelocity(sp, sp.getPitch(), sp.getYaw(), -10.0F, 1.15F, 0.8F);
		sw.spawnEntity(flask);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Трофей Верховного Магистра Белых Колпаков").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("• Удар: +28 урона по эндерменам, иссушителям, всполохам и драконам").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("  и блокирует телепортацию эндерменов!").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("• ПКМ: бросить Чумную Колбу Очищения (урон врагам + лечение себе)").formatted(Formatting.WHITE));
		tooltip.add(Text.literal("• Shift + ПКМ: Кольцо Очищения Скверны вокруг себя").formatted(Formatting.YELLOW));
	}
}
