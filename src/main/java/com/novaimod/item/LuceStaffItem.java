package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.CatgirlEntity;
import com.novaimod.entity.LuceEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * «Святой Посох Паломника (Бордоне)» — раздвоенный деревянный посох Люче!
 * - Ближний бой: повышенный святой урон + дополнительное испепеление нежити и демонов (+14 святого урона!).
 * - ПКМ: обрушивает «Небесный Луч Экзорцизма» в точку прицела (до 38 блоков)!
 * - Shift + ПКМ: «Благословение Колоколов» — снимает проклятия, лечит союзников и отталкивает нечисть!
 */
public class LuceStaffItem extends SwordItem {
	public LuceStaffItem(Settings settings) {
		super(
				ToolMaterials.NETHERITE,
				settings.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 5, -2.1F))
						.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false))
		);
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!target.getWorld().isClient() && target.getWorld() instanceof ServerWorld sw) {
			if (target.getType().isIn(EntityTypeTags.UNDEAD) || target instanceof Monster) {
				target.damage(sw.getDamageSources().magic(), 14.0F);
				target.setOnFireFor(5);
				sw.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getBodyY(0.6D), target.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.06D);
				sw.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_HIT, SoundCategory.PLAYERS, 0.9F, 1.35F);
			}
		}
		return super.postHit(stack, target, attacker);
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

		if (sp.isSneaking()) {
			// Shift + ПКМ: Благословение Юбилейного Колокола
			user.getItemCooldownManager().set(this, 140);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.3F, 1.15F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.0F, 1.25F);

			for (int i = 0; i < 36; i++) {
				double a = (Math.PI * 2.0D * i) / 36.0D;
				sw.spawnParticles(ParticleTypes.END_ROD, sp.getX() + Math.cos(a) * 5.0D, sp.getY() + 0.4D, sp.getZ() + Math.sin(a) * 5.0D, 2, 0.05D, 0.2D, 0.05D, 0.02D);
			}

			Box blessBox = sp.getBoundingBox().expand(10.0D, 5.0D, 10.0D);
			for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, blessBox, LivingEntity::isAlive)) {
				if (e instanceof PlayerEntity || e instanceof LuceEntity || e instanceof CatgirlEntity) {
					e.removeStatusEffect(StatusEffects.POISON);
					e.removeStatusEffect(StatusEffects.WITHER);
					e.removeStatusEffect(StatusEffects.SLOWNESS);
					e.removeStatusEffect(StatusEffects.WEAKNESS);
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 45, 1));
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 12, 1));
					e.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 20, 0));
				} else if (e instanceof HostileEntity) {
					Vec3d push = e.getPos().subtract(sp.getPos()).normalize().multiply(1.1D);
					e.addVelocity(push.x, 0.35D, push.z);
					e.velocityModified = true;
					e.setOnFireFor(4);
				}
			}
			return TypedActionResult.success(stack, false);
		}

		// Обычный ПКМ: Небесный Столб Экзорцизма по прицелу!
		user.getItemCooldownManager().set(this, 42);
		HitResult hit = sp.raycast(38.0D, 0.0F, false);
		Vec3d impact = hit.getPos();

		sw.playSound(null, BlockPos.ofFloored(impact), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.1F);
		for (double y = 0.0D; y <= 14.0D; y += 0.6D) {
			sw.spawnParticles(ParticleTypes.END_ROD, impact.x, impact.y + y, impact.z, 4, 0.3D, 0.15D, 0.3D, 0.02D);
		}
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(impact.x, impact.y, impact.z);
			bolt.setCosmetic(true);
			sw.spawnEntity(bolt);
		}

		Box smiteBox = new Box(impact.x - 3.5D, impact.y - 2.0D, impact.z - 3.5D, impact.x + 3.5D, impact.y + 5.0D, impact.z + 3.5D);
		for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, smiteBox, e -> e != sp && e.isAlive())) {
			if (target instanceof LuceEntity || target instanceof CatgirlEntity) {
				target.heal(6.0F);
				continue;
			}
			if (target instanceof Monster || target instanceof HostileEntity) {
				target.damage(sw.getDamageSources().playerAttack(sp), 18.0F);
				target.setOnFireFor(6);
			}
		}
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Раздвоенный посох Люче («Сила Бога и Аниме!»)").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("• Удар вблизи: +14 святого урона по нежити и монстрам").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• ПКМ: Небесный Столб Света в точку прицела").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("• Shift + ПКМ: Благословение Колоколов (исцеление и щит света)").formatted(Formatting.GREEN));
	}
}
