package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.LuceEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * «Миссионерские Чётки Люче» (5 цветов пяти континентов + золотой крестик):
 * - В инвентаре: спасают владельца святой вспышкой при падении здоровья ниже 3 сердец (раз в 45 сек)!
 * - В руке / второй руке: отгоняют криперов и фантомов.
 * - ПКМ: «Молитва Покаяния» — усмиряет разгневанную Люче и ослабляет нежить вокруг!
 */
public class LuceRosaryItem extends Item {
	public LuceRosaryItem(Settings settings) {
		super(settings);
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClient() || !(world instanceof ServerWorld sw) || !(entity instanceof ServerPlayerEntity sp)) {
			return;
		}

		// Спасительная молитва при критическом здоровье
		if (sp.getHealth() > 0.0F && sp.getHealth() <= 6.0F && !sp.getItemCooldownManager().isCoolingDown(this)) {
			sp.getItemCooldownManager().set(this, 20 * 45);
			sp.heal(8.0F);
			sp.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 8, 1));
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 1.3F);
			sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.6D), sp.getZ(), 24, 0.4D, 0.5D, 0.4D, 0.05D);
			sp.sendMessage(Text.literal("📿 Миссионерские Чётки Люче сотворили чудо исцеления! (+4 сердца)").formatted(Formatting.YELLOW, Formatting.BOLD), true);
		}

		// В руках отпугивает фантомов и криперов
		if ((selected || sp.getOffHandStack() == stack) && sp.age % 15 == 0) {
			Box repelBox = sp.getBoundingBox().expand(7.5D);
			for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, repelBox, e -> e instanceof CreeperEntity || e instanceof PhantomEntity)) {
				Vec3d away = mob.getPos().subtract(sp.getPos()).normalize().multiply(0.45D);
				mob.addVelocity(away.x, 0.12D, away.z);
			}
		}
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient()) {
			return TypedActionResult.success(stack, true);
		}
		if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)) {
			return TypedActionResult.pass(stack);
		}

		user.getItemCooldownManager().set(this, 80);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.35F);
		sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.6D), sp.getZ(), 20, 0.5D, 0.5D, 0.5D, 0.04D);

		Box area = sp.getBoundingBox().expand(18.0D);
		int calmed = 0;
		for (LuceEntity luce : sw.getEntitiesByClass(LuceEntity.class, area, LuceEntity::isAlive)) {
			if (luce.isWrathMode()) {
				luce.calmDownWithRosary(sp);
				calmed++;
			}
		}
		for (LivingEntity undead : sw.getEntitiesByClass(LivingEntity.class, sp.getBoundingBox().expand(9.0D), e -> e.isAlive() && e.getType().isIn(EntityTypeTags.UNDEAD))) {
			undead.setOnFireFor(6);
			undead.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1));
		}

		if (calmed == 0) {
			sp.sendMessage(Text.literal("📿 Святая молитва Чёток отогнала тьму и нежить вокруг!").formatted(Formatting.YELLOW), true);
		}
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Пятицветные чётки паломницы Люче").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("• В инвентаре: спасают при < 3 сердец и вдвое смягчают проценты МФО").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("• Во второй руке: отгоняют криперов и фантомов").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("• ПКМ: Молитва Покаяния (успокаивает разгневанную Люче и жжёт нежить)").formatted(Formatting.YELLOW));
	}
}
