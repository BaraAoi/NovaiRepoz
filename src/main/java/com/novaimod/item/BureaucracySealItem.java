package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Легендарный трофей 6-го этажа Бюрократического Ада: «Генеральная Печать Бюрократа».
 * - ПКМ по любой двери: работает как вечный VIP-Ключ в Бюрократический Ад и обратно!
 * - ПКМ в бою: ставит громовой штамп «ОТКАЗАНО!» по всем врагам вокруг (урон, отброс, паралич ИИ и бегство коллекторов!).
 * - Пока лежит в инвентаре: даёт дипломатическую неприкосновенность от задержания ОМОНом в СИЗО!
 */
public class BureaucracySealItem extends Item {
	public BureaucracySealItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
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

		user.getItemCooldownManager().set(this, 70);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 1.1F, 0.65F);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.7F, 1.35F);

		// Кольцо багрово-золотых частиц печати
		for (int i = 0; i < 36; i++) {
			double a = (Math.PI * 2.0D * i) / 36.0D;
			for (double r = 2.0D; r <= 8.5D; r += 3.2D) {
				sw.spawnParticles(ParticleTypes.FLAME, sp.getX() + Math.cos(a) * r, sp.getY() + 0.25D, sp.getZ() + Math.sin(a) * r, 1, 0.05D, 0.05D, 0.05D, 0.01D);
				sw.spawnParticles(ParticleTypes.CRIMSON_SPORE, sp.getX() + Math.cos(a) * r, sp.getY() + 0.8D, sp.getZ() + Math.sin(a) * r, 2, 0.1D, 0.2D, 0.1D, 0.02D);
			}
		}

		Box area = sp.getBoundingBox().expand(9.0D, 4.5D, 9.0D);
		int stampedCount = 0;
		for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, area, e -> e != sp && e.isAlive())) {
			if (target instanceof ModNpcEntity npc) {
				NpcRole role = npc.getNpcRole();
				if (role == NpcRole.VOLODYA || role == NpcRole.COLLECTOR || role == NpcRole.MASTER_OF_SPORTS || role == NpcRole.ARMENIAN_BRIGADE || role == NpcRole.OMON) {
					if (!"bureau_guard".equals(npc.getVariant()) && !"cage".equals(npc.getVariant())) {
						npc.forceFleeFromSeal();
						stampedCount++;
					}
				}
				continue;
			}
			if (target instanceof Monster || (target instanceof PlayerEntity other && other != sp)) {
				target.damage(sw.getDamageSources().playerAttack(sp), 18.0F);
				Vec3d push = target.getPos().subtract(sp.getPos()).normalize().multiply(1.15D);
				target.addVelocity(push.x, 0.42D, push.z);
				target.velocityModified = true;
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4));
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
				stampedCount++;
			}
		}

		sp.sendMessage(
				Text.literal("📕 ШТАМП «ОТКАЗАНО В ТРЁХ ЭКЗЕМПЛЯРАХ!» Поражено целей: " + stampedCount)
						.formatted(Formatting.RED, Formatting.BOLD),
				true
		);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Личная печать Верховного Председателя (6-й этаж)").formatted(Formatting.DARK_RED, Formatting.BOLD));
		tooltip.add(Text.literal("• ПКМ в бою: Удар штампом «ОТКАЗАНО!» (18 урона, стан и разгон коллекторов)").formatted(Formatting.GOLD));
		tooltip.add(Text.literal("• ПКМ по любой двери: VIP-проход в Бюрократический Ад и обратно").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("• В инвентаре: Дипломатическая неприкосновенность от СИЗО ОМОНа").formatted(Formatting.GREEN));
	}
}
