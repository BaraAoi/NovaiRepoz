package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.RealitySlashEntity;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * КРЕАТИВНЫЙ АРТЕФАКТ: «Клинок Разлома Реальности» (v3.6.0).
 * - ЛКМ (Прямой удар): рассекает само бытие и наносит чистый ПРОЦЕНТНЫЙ УРОН (пробивает даже Альтимейт Броню!)
 * - ПКМ (без кулдауна!): выпускает 3D-волну Пространственного Разреза шириной 8 блоков сквозь стены на 85 блоков!
 * - Shift + ПКМ: «Приговор Пространства» (Judgment Cut End) — замораживает врагов в радиусе 48 блоков в куполе
 *   3D-трещин пространства и через 1.3 секунды раскалывает реальность, аннигилируя всех врагов разом!
 */
public class RealityRiftBladeItem extends Item {
	public RealityRiftBladeItem(Settings settings) {
		super(settings
				.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false))
				.attributeModifiers(createAttributeModifiers()));
	}

	public static AttributeModifiersComponent createAttributeModifiers() {
		return AttributeModifiersComponent.builder()
				.add(
						EntityAttributes.GENERIC_ATTACK_DAMAGE,
						new EntityAttributeModifier(
								BASE_ATTACK_DAMAGE_MODIFIER_ID,
								49.0D,
								EntityAttributeModifier.Operation.ADD_VALUE
						),
						AttributeModifierSlot.MAINHAND
				)
				.add(
						EntityAttributes.GENERIC_ATTACK_SPEED,
						new EntityAttributeModifier(
								BASE_ATTACK_SPEED_MODIFIER_ID,
								-1.6D,
								EntityAttributeModifier.Operation.ADD_VALUE
						),
						AttributeModifierSlot.MAINHAND
				)
				.build();
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
		return !miner.isCreative();
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (!attacker.getWorld().isClient() && attacker.getWorld() instanceof ServerWorld sw && attacker instanceof PlayerEntity player) {
			float bonusPure = Math.max(250.0F, target.getMaxHealth() * 0.35F);
			if (!(target instanceof PlayerEntity)) {
				target.hurtTime = 0;
				target.timeUntilRegen = 0;
				if (target instanceof EnderDragonEntity dragon) {
					dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), bonusPure);
				} else {
					target.damage(sw.getDamageSources().outOfWorld(), bonusPure);
				}
			}
			sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5D), target.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.0D);
			sw.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getBodyY(0.5D), target.getZ(), 28, 0.55D, 0.7D, 0.55D, 0.4D);
			sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getBodyY(0.5D), target.getZ(), 18, 0.45D, 0.6D, 0.45D, 0.08D);
			sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.2F, 0.65F);
			sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 1.35F);
		}
		stack.damage(0, attacker, EquipmentSlot.MAINHAND);
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (user.isSneaking()) {
				// Shift + ПКМ: «Приговор Пространства» (Judgment Cut End) в радиусе 48 блоков!
				RealitySlashEntity dome = new RealitySlashEntity(
						sw,
						sp.getX(), sp.getY(), sp.getZ(),
						Vec3d.ZERO,
						sp.getYaw(), 0.0F,
						sp.getUuid(),
						RealitySlashEntity.MODE_JUDGMENT_CUT_END
				);
				sw.spawnEntity(dome);
				sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 1.8F, 1.45F);
				sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.0F, 0.85F);
				sp.sendMessage(
						Text.literal("✦ ПРИГОВОР ПРОСТРАНСТВА: реальность застыла перед расколом! ✦")
								.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
						true
				);
			} else {
				// ПКМ: Пространственный Разрез (3D-серп, летящий сквозь стены на 85 блоков!)
				Vec3d look = sp.getRotationVec(1.0F).normalize();
				Vec3d spawnPos = sp.getEyePos().add(look.multiply(1.35D)).subtract(0.0D, 0.35D, 0.0D);
				RealitySlashEntity wave = new RealitySlashEntity(
						sw,
						spawnPos.x, spawnPos.y, spawnPos.z,
						look.multiply(2.65D),
						sp.getYaw(), sp.getPitch(),
						sp.getUuid(),
						RealitySlashEntity.MODE_CRESCENT_WAVE
				);
				sw.spawnEntity(wave);
				sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4F, 0.65F);
				sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.2F, 1.4F);
			}
			user.swingHand(hand, true);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.novaimod.reality_rift_blade.desc1").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.novaimod.reality_rift_blade.desc2").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.novaimod.reality_rift_blade.desc3").formatted(Formatting.GOLD));
	}
}
