package com.novaimod.item;

import java.util.List;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.HolyOrderEntity;
import com.novaimod.entity.LuceEntity;
import com.novaimod.entity.WhiteCapEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.WolfEntity;
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
import net.minecraft.world.World;

/**
 * «Святая Хоругвь Ордена Люче» (`novaimod:crusade_banner`):
 * - ПКМ: Трубит сбор Святого Похода — призывает личный отряд из 2 Паладинов и 1 Арбалетчика Инквизиции (на 90 сек)
 *   и дарует ауру Силы II + Сопротивления всем союзникам Церкви вокруг!
 * - Shift + ПКМ: Переключает приказ вашему отряду («Идти в поход за мной» / «Держать оборону здесь»).
 */
public class CrusadeBannerItem extends Item {
	public CrusadeBannerItem(Settings settings) {
		super(settings);
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

		if (WhiteCapEntity.isWearingClanHood(sp)) {
			sp.damage(sw.getDamageSources().magic(), 4.0F);
			sp.sendMessage(Text.literal("✝ Святая Хоругвь обжигает руки того, кто носит проклятый колпак КБК!!").formatted(Formatting.RED, Formatting.BOLD), true);
			return TypedActionResult.fail(stack);
		}

		if (sp.isSneaking()) {
			Box cmdBox = sp.getBoundingBox().expand(28.0D, 12.0D, 28.0D);
			List<HolyOrderEntity> squad = sw.getEntitiesByClass(HolyOrderEntity.class, cmdBox, e -> e.isAlive() && e.isCommandedBy(sp));
			if (squad.isEmpty()) {
				sp.sendMessage(Text.literal("⚔ Рядом нет призванных вами рыцарей — нажмите обычный ПКМ, чтобы поднять Хоругвь и призвать отряд!").formatted(Formatting.YELLOW), true);
				return TypedActionResult.success(stack, false);
			}
			boolean follow = true;
			for (HolyOrderEntity knight : squad) {
				follow = knight.toggleCommanderOrder();
			}
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.3F);
			sp.sendMessage(
					Text.literal(follow
							? "⚔ [Хоругвь Люче]: Отряд Ордена построен в колонну и идёт за вами!"
							: "🛡 [Хоругвь Люче]: Отряд Ордена занял круговую оборону на этой позиции!").formatted(Formatting.GOLD, Formatting.BOLD),
					true
			);
			return TypedActionResult.success(stack, false);
		}

		user.getItemCooldownManager().set(this, 20 * 65);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 1.2F, 1.35F);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.PLAYERS, 1.3F, 1.15F);

		for (int i = 0; i < 36; i++) {
			double a = (Math.PI * 2.0D * i) / 36.0D;
			sw.spawnParticles(ParticleTypes.END_ROD, sp.getX() + Math.cos(a) * 4.2D, sp.getY() + 0.4D, sp.getZ() + Math.sin(a) * 4.2D, 2, 0.05D, 0.25D, 0.05D, 0.02D);
		}

		int[] ranks = {HolyOrderEntity.RANK_CRUSADER, HolyOrderEntity.RANK_CRUSADER, HolyOrderEntity.RANK_INQUISITOR};
		double[][] offsets = {{-1.8D, 1.2D}, {1.8D, 1.2D}, {0.0D, -2.0D}};
		for (int i = 0; i < ranks.length; i++) {
			HolyOrderEntity knight = ModEntities.HOLY_ORDER_ENTITY.create(sw);
			if (knight != null) {
				knight.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
				knight.refreshPositionAndAngles(
						sp.getX() + offsets[i][0],
						sp.getY(),
						sp.getZ() + offsets[i][1],
						sp.getYaw(),
						0.0F
				);
				knight.setRank(ranks[i]);
				knight.configureBannerSummon(sp, 20 * 90);
				sw.spawnEntity(knight);
			}
		}

		// Аура Крестового Похода всем союзникам Света рядом
		Box blessBox = sp.getBoundingBox().expand(14.0D, 6.0D, 14.0D);
		for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, blessBox, LivingEntity::isAlive)) {
			boolean santino = e instanceof WolfEntity w && w.getCommandTags().contains(LuceEntity.TAG_SANTINO_DOG);
			if (e == sp || e instanceof HolyOrderEntity || e instanceof LuceEntity || santino) {
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 45, 1));
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 45, 0));
				e.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 45, 0));
			}
		}

		sp.sendMessage(Text.literal("✝ [Святая Хоругвь]: Рыцари Ордена Люче явились на зов Крестового Похода! Смерть еретикам КБК!!").formatted(Formatting.GOLD, Formatting.BOLD), false);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Знамя Ордена Святой Люче (Война против КБК!)").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("• ПКМ: Призвать отряд Паладинов и Инквизитора + Сила II и Защита").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Shift + ПКМ: Приказ отряду (Следовать за вами / Держать рубеж)").formatted(Formatting.AQUA));
	}
}
