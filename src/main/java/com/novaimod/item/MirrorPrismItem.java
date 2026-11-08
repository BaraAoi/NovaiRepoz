package com.novaimod.item;

import java.util.List;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.PrismCathedralEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * «Mirror Dimension Prism — Призма Калейдоскопа»:
 * - Режим 0: Силовой Купол-Рикошет Базы (отражает вражеские стрелы/фаерболы обратно в стрелков,
 *   гасит взрывы динамита и криперов, даёт Сопротивление IV союзникам и выжигает монстров!).
 * - Режим 1: Зеркальное Клонирование! Наведите на ЛЮБОЕ существо (жителя, кошкодевочку, рыцаря Ордена,
 *   животное, голема или моба) и нажмите ПКМ — призма расщепит свет и создаст его живого двойника!
 *   (Если кликнуть в пустоту — призовёт 2 Призматических Железных Стражей-Хранителей!).
 */
public class MirrorPrismItem extends Item {
	private static final String KEY_MODE = "PrismMode";

	public MirrorPrismItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		return (comp != null && comp.copyNbt().getInt(KEY_MODE) == 1) ? 1 : 0;
	}

	public static String modeTitle(int mode) {
		return mode == 1
				? "Зеркальное Клонирование Существ (Создаёт живого двойника выбранного моба/NPC)"
				: "Силовой Купол-Рикошет Базы (Щит от взрывов + возврат снарядов во врагов)";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking()) {
				int next = getMode(stack) == 0 ? 1 : 0;
				NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				nbt.putInt(KEY_MODE, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.4F);
				sp.sendMessage(Text.literal("💠 Режим Призмы: ").formatted(Formatting.LIGHT_PURPLE)
						.append(Text.literal(modeTitle(next)).formatted(Formatting.AQUA, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			if (getMode(stack) == 1) {
				mirrorCloneTargetOrSummonGuardians(sw, sp);
				sp.getItemCooldownManager().set(this, 25);
				return TypedActionResult.success(stack, false);
			}

			PrismCathedralEntity cathedral = ModEntities.PRISM_CATHEDRAL_ENTITY.create(sw);
			if (cathedral != null) {
				cathedral.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
				cathedral.refreshPositionAndAngles(sp.getX(), sp.getY(), sp.getZ(), 0.0F, 0.0F);
				cathedral.setOwner(sp);
				sw.spawnEntity(cathedral);
			}
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.0F, 1.35F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 1.5F, 1.55F);
			sp.sendMessage(Text.literal("💠 Купол Зеркального Измерения развёрнут на 20 сек.: взрывы и снаряды отражаются!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
			sp.getItemCooldownManager().set(this, 30);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private static void mirrorCloneTargetOrSummonGuardians(ServerWorld sw, ServerPlayerEntity sp) {
		Vec3d start = sp.getCameraPosVec(1.0F);
		Vec3d look = sp.getRotationVec(1.0F);
		Vec3d end = start.add(look.multiply(28.0D));
		Box searchBox = sp.getBoundingBox().stretch(look.multiply(28.0D)).expand(1.5D);
		EntityHitResult hit = ProjectileUtil.raycast(
				sp, start, end, searchBox,
				e -> e instanceof LivingEntity && e.isAlive() && !(e instanceof PlayerEntity)
						&& !(e instanceof EnderDragonEntity) && !(e instanceof WitherEntity),
				28.0D * 28.0D
		);

		if (hit != null && hit.getEntity() instanceof LivingEntity original) {
			NbtCompound mobNbt = new NbtCompound();
			if (original.saveSelfNbt(mobNbt)) {
				mobNbt.remove("UUID");
				double angle = sw.random.nextDouble() * Math.PI * 2.0D;
				double cx = original.getX() + Math.cos(angle) * 1.6D;
				double cz = original.getZ() + Math.sin(angle) * 1.6D;
				Entity clone = EntityType.loadEntityWithPassengers(mobNbt, sw, e -> {
					e.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
					e.refreshPositionAndAngles(cx, original.getY(), cz, original.getYaw(), original.getPitch());
					return e;
				});
				if (clone != null && sw.spawnEntity(clone)) {
					sw.spawnParticles(ParticleTypes.FLASH, cx, original.getBodyY(0.5D), cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
					sw.spawnParticles(ParticleTypes.END_ROD, cx, original.getBodyY(0.5D), cz, 32, 0.5D, 0.7D, 0.5D, 0.08D);
					sw.playSound(null, original.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.MASTER, 1.6F, 1.35F);
					sp.sendMessage(Text.literal("💠 Призма преломила реальность и создала зеркального двойника: ")
							.formatted(Formatting.AQUA)
							.append(original.getDisplayName().copy().formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)), true);
					return;
				}
			}
		}

		// Если в прицеле нет существа — призываем 2 Призматических Стражей!
		for (int i = -1; i <= 1; i += 2) {
			IronGolemEntity golem = EntityType.IRON_GOLEM.create(sw);
			if (golem != null) {
				Vec3d pos = sp.getPos().add(sp.getRotationVec(1.0F).multiply(3.0D)).add(i * 1.8D, 0.0D, 0.0D);
				golem.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
				golem.setPlayerCreated(true);
				golem.setCustomName(Text.literal("Призматический Страж").formatted(Formatting.AQUA, Formatting.BOLD));
				golem.setGlowing(true);
				golem.refreshPositionAndAngles(pos.x, pos.y, pos.z, sp.getYaw(), 0.0F);
				sw.spawnEntity(golem);
				sw.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.2D, pos.z, 24, 0.5D, 0.8D, 0.5D, 0.07D);
			}
		}
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.8F, 1.25F);
		sp.sendMessage(Text.literal("💠 Из зеркальных граней вышли 2 Призматических Стража!").formatted(Formatting.AQUA, Formatting.BOLD), true);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int mode = getMode(stack);
		tooltip.add(Text.literal("💠 ПРИЗМА ЗЕРКАЛЬНОГО ИЗМЕРЕНИЯ 💠").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		tooltip.add(Text.literal("Задача: ").formatted(Formatting.GRAY)
				.append(Text.literal(mode == 0 ? "Силовой Щит-Рикошет Базы" : "Клонирование Моб/NPC").formatted(Formatting.AQUA)));
		tooltip.add(Text.literal("• Режим 1: Отражает все стрелы/фаерболы обратно во врагов,").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("  поглощает взрывы ТНТ/криперов и даёт Сопротивление IV!").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Режим 2: Создаёт живого клона любого существа в прицеле").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("  (или призывает 2 Призматических Стражей, если цель не выбрана)!").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Shift + ПКМ: Переключить режим Призмы").formatted(Formatting.GRAY));
	}
}
