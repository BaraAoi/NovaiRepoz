package com.novaimod.item;

import java.util.List;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.StormVortexEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * «Stormbreaker Sovereign — Повелитель Гроз»:
 * - Режим 0: Сверхзвуковой Грозовой Полёт (бросает вас по небу в шлейфе молний в любом режиме игры без урона от падения!);
 * - Режим 1: 3D Вихрь Тысячи Молний (зачистка врагов + Заряженные Криперы + очистка всей Меди от окисления!);
 * - Режим 2: Электромагнитный Пылесос Лута (стягивает ВЕСЬ дроп и опыт с радиуса 80 блоков прямо вам в инвентарь!).
 */
public class StormSovereignItem extends Item {
	private static final String KEY_MODE = "StormMode";

	public StormSovereignItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return 0;
		}
		return MathHelper.clamp(comp.copyNbt().getInt(KEY_MODE), 0, 2);
	}

	public static String modeTitle(int mode) {
		return switch (mode) {
			case 1 -> "3D Вихрь Тысячи Молний (Кара врагов + Очистка Меди + Заряд Криперов)";
			case 2 -> "Электромагнитный Пылесос Лута (Сбор всех предметов и опыта с 80 бл.!)";
			default -> "Грозовой Полёт Громовержца (Сверхзвуковой рывок по небу без урона)";
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking()) {
				int next = (getMode(stack) + 1) % 3;
				NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				nbt.putInt(KEY_MODE, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.4F);
				sp.sendMessage(Text.literal("⚡ Режим Молот Гроз: ").formatted(Formatting.AQUA)
						.append(Text.literal(modeTitle(next)).formatted(Formatting.YELLOW, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			int mode = getMode(stack);
			if (mode == 0) {
				// Грозовой Полёт!
				Vec3d look = sp.getRotationVec(1.0F);
				sp.setVelocity(look.multiply(2.85D).add(0.0D, 0.25D, 0.0D));
				sp.velocityModified = true;
				sp.fallDistance = 0.0F;
				sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 110, 0, true, false, true));
				sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getBodyY(0.5D), sp.getZ(), 42, 0.6D, 0.6D, 0.6D, 0.35D);
				sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.5D), sp.getZ(), 20, 0.4D, 0.4D, 0.4D, 0.12D);
				sw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.5F, 1.15F);
				sp.getItemCooldownManager().set(this, 6);
			} else if (mode == 1) {
				// 3D Вихрь Тысячи Молний!
				Vec3d eye = sp.getCameraPosVec(1.0F);
				Vec3d look = sp.getRotationVec(1.0F);
				HitResult hit = sw.raycast(new RaycastContext(
						eye, eye.add(look.multiply(68.0D)),
						RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp
				));
				Vec3d pos = hit.getType() != HitResult.Type.MISS ? hit.getPos() : eye.add(look.multiply(20.0D));
				StormVortexEntity vortex = ModEntities.STORM_VORTEX_ENTITY.create(sw);
				if (vortex != null) {
					vortex.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
					vortex.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
					sw.spawnEntity(vortex);
				}
				sw.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.MASTER, 1.8F, 0.9F);
				sp.sendMessage(Text.literal("⚡ Над целью бушует 3D Вихрь Тысячи Молний!").formatted(Formatting.AQUA, Formatting.BOLD), true);
				sp.getItemCooldownManager().set(this, 24);
			} else {
				// Электромагнитный Пылесос Лута и Опыта с 80 блоков!
				Box magnetBox = sp.getBoundingBox().expand(80.0D);
				int pulledItems = 0;
				for (ItemEntity ie : sw.getEntitiesByClass(ItemEntity.class, magnetBox, Entity::isAlive)) {
					sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, ie.getX(), ie.getY() + 0.2D, ie.getZ(), 6, 0.15D, 0.15D, 0.15D, 0.08D);
					ie.setPickupDelay(0);
					ie.refreshPositionAndAngles(sp.getX(), sp.getY() + 0.5D, sp.getZ(), 0.0F, 0.0F);
					pulledItems += ie.getStack().getCount();
				}
				for (ExperienceOrbEntity xp : sw.getEntitiesByClass(ExperienceOrbEntity.class, magnetBox, Entity::isAlive)) {
					xp.refreshPositionAndAngles(sp.getX(), sp.getY() + 0.5D, sp.getZ(), 0.0F, 0.0F);
				}
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 1.65F);
				sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, sp.getX(), sp.getBodyY(0.5D), sp.getZ(), 35, 0.8D, 0.8D, 0.8D, 0.2D);
				sp.sendMessage(Text.literal("⚡ Электромагнитный импульс притянул к вам предметов: " + pulledItems + "!").formatted(Formatting.AQUA, Formatting.BOLD), true);
				sp.getItemCooldownManager().set(this, 15);
			}
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int mode = getMode(stack);
		tooltip.add(Text.literal("⚡ ПОВЕЛИТЕЛЬ ГРОЗ И МАГНЕТИЗМА ⚡").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("Задача: ").formatted(Formatting.GRAY)
				.append(Text.literal(modeTitle(mode)).formatted(Formatting.YELLOW)));
		tooltip.add(Text.literal("• Режим 1: Сверхзвуковой полёт в молниях куда смотрите").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("• Режим 2: 3D Грозовой Вихрь (очистка Меди + кара врагов)").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Режим 3: Мгновенный сбор ВСЕГО лута и опыта в радиусе 80 бл.!").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Shift + ПКМ: Сменить режим").formatted(Formatting.GRAY));
	}
}
