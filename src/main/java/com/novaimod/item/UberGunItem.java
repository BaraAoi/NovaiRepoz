package com.novaimod.item;

import java.util.List;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * УБЕРГАН — легендарное трансформируемое оружие полного Сета Лучника МФО (4/4).
 * Имеет 3 режима стрельбы (переключение: клавиша R или Shift + ПКМ):
 * 0 — ПУЛЕМЁТ: зажатие ПКМ выпускает стрелу каждые 0.2 сек (4 тика), стрелы чуть слабее обычных, без кд неуязвимости цели.
 * 1 — СНАЙПЕРСКИЙ: долгий натяг (2.25 сек = 45 тиков), но урон выше в несколько раз и прямая траектория.
 * 2 — АННИГИЛЯЦИЯ: обычный натяг лука, но при попадании куда угодно взрывается местность!
 */
public class UberGunItem extends BowItem {
	public static final String NBT_MODE_KEY = "novai_uber_mode";
	public static final int MODE_MACHINE_GUN = 0;
	public static final int MODE_SNIPER = 1;
	public static final int MODE_ANNIHILATION = 2;

	public static final int SNIPER_FULL_DRAW_TICKS = 45; // 2.25 сек долгого натяга

	public UberGunItem(Settings settings) {
		super(settings);
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		if (!comp.contains(NBT_MODE_KEY)) {
			return MODE_MACHINE_GUN;
		}
		return MathHelper.clamp(comp.copyNbt().getInt(NBT_MODE_KEY), 0, 2);
	}

	public static void setMode(ItemStack stack, int mode) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putInt(NBT_MODE_KEY, MathHelper.clamp(mode, 0, 2));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static void cycleMode(ServerPlayerEntity player, ItemStack stack) {
		int next = (getMode(stack) + 1) % 3;
		setMode(stack, next);
		player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DISPENSER_DISPENSE, SoundCategory.PLAYERS, 0.9F, 1.35F);
		player.sendMessage(getModeBanner(next), true);
	}

	public static Text getModeBanner(int mode) {
		return switch (mode) {
			case MODE_SNIPER -> Text.literal("🎯 Режим Убергана: СНАЙПЕРСКИЙ (долгий натяг, урон x4)").formatted(Formatting.GOLD, Formatting.BOLD);
			case MODE_ANNIHILATION -> Text.literal("💥 Режим Убергана: АННИГИЛЯЦИЯ (взрывает мобов и местность)").formatted(Formatting.RED, Formatting.BOLD);
			default -> Text.literal("⚡ Режим Убергана: ПУЛЕМЁТ (очередь раз в 0.2 сек, усиленный урон)").formatted(Formatting.AQUA, Formatting.BOLD);
		};
	}

	@Override
	public Text getName(ItemStack stack) {
		return switch (getMode(stack)) {
			case MODE_SNIPER -> Text.literal("Уберган [Снайперский]").formatted(Formatting.GOLD, Formatting.BOLD);
			case MODE_ANNIHILATION -> Text.literal("Уберган [Аннигиляция]").formatted(Formatting.RED, Formatting.BOLD);
			default -> Text.literal("Уберган [Пулемёт]").formatted(Formatting.AQUA, Formatting.BOLD);
		};
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BOW;
	}

	@Override
	public int getMaxUseTime(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.isSneaking()) {
			if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
				cycleMode(sp, stack);
			}
			return TypedActionResult.success(stack, world.isClient());
		}

		user.setCurrentHand(hand);
		return TypedActionResult.consume(stack);
	}

	@Override
	public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		int usedTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;
		int mode = getMode(stack);

		if (mode == MODE_MACHINE_GUN) {
			// Пулемёт: выстрел строго каждые 4 тика (0.2 секунды) с увеличенным уроном!
			if (usedTicks >= 2 && usedTicks % 4 == 0 && !world.isClient()) {
				ArrowEntity arrow = new ArrowEntity(world, user, new ItemStack(Items.ARROW), stack);
				arrow.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 3.05F, 0.95F);
				arrow.setDamage(1.90D); // Усиленный урон пулемётной очереди (~6 ед. чистого урона за каждую стрелу каждые 0.2 сек!)
				arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
				arrow.addCommandTag("novai_uber_mg");
				world.spawnEntity(arrow);

				world.playSound(
						null,
						user.getX(),
						user.getY(),
						user.getZ(),
						SoundEvents.ENTITY_ARROW_SHOOT,
						SoundCategory.PLAYERS,
						0.75F,
						1.35F + world.getRandom().nextFloat() * 0.15F
				);
			}
			return;
		}

		if (mode == MODE_SNIPER && !world.isClient() && user instanceof ServerPlayerEntity sp) {
			if (usedTicks == 15 || usedTicks == 30) {
				world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.6F, 1.0F + usedTicks * 0.015F);
			} else if (usedTicks == SNIPER_FULL_DRAW_TICKS) {
				world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.9F, 1.6F);
				sp.sendMessage(Text.literal("🎯 СНАЙПЕРСКИЙ ЗАРЯД 100% — ГОТОВ К ВЫСТРЕЛУ!").formatted(Formatting.GOLD, Formatting.BOLD), true);
			}
		}
	}

	@Override
	public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
		int mode = getMode(stack);
		if (mode == MODE_MACHINE_GUN) {
			// В режиме пулемёта выстрелы идут непрерывно пока зажата ПКМ
			return;
		}

		int usedTicks = this.getMaxUseTime(stack, user) - remainingUseTicks;

		if (mode == MODE_SNIPER) {
			float charge = MathHelper.clamp((float) usedTicks / (float) SNIPER_FULL_DRAW_TICKS, 0.0F, 1.0F);
			if (charge < 0.35F) {
				return;
			}
			if (!world.isClient()) {
				ArrowEntity arrow = new ArrowEntity(world, user, new ItemStack(Items.ARROW), stack);
				float speed = 2.4F + charge * 2.8F; // До 5.2F на полном натяге!
				arrow.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, speed, 0.0F);
				// На полном натяге урон стрелы в несколько раз выше обычного лука (~38-42 урона)!
				arrow.setDamage(2.5D + charge * 5.5D);
				if (charge >= 0.95F) {
					arrow.setCritical(true);
				}
				arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
				arrow.addCommandTag("novai_uber_sniper");
				world.spawnEntity(arrow);

				world.playSound(
						null,
						user.getX(),
						user.getY(),
						user.getZ(),
						SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
						SoundCategory.PLAYERS,
						0.55F,
						1.75F
				);
				world.playSound(
						null,
						user.getX(),
						user.getY(),
						user.getZ(),
						SoundEvents.ENTITY_ARROW_SHOOT,
						SoundCategory.PLAYERS,
						1.0F,
						0.75F
				);
			}
			return;
		}

		if (mode == MODE_ANNIHILATION) {
			float pull = BowItem.getPullProgress(usedTicks);
			if (pull < 0.45F) {
				return;
			}
			if (!world.isClient()) {
				ArrowEntity arrow = new ArrowEntity(world, user, new ItemStack(Items.ARROW), stack);
				arrow.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, pull * 3.1F, 0.6F);
				arrow.setDamage(2.2D);
				if (pull >= 1.0F) {
					arrow.setCritical(true);
				}
				arrow.setOnFireFor(100);
				arrow.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
				arrow.addCommandTag("novai_uber_annihilate");
				world.spawnEntity(arrow);

				world.playSound(
						null,
						user.getX(),
						user.getY(),
						user.getZ(),
						SoundEvents.ENTITY_BLAZE_SHOOT,
						SoundCategory.PLAYERS,
						0.9F,
						0.95F
				);
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int mode = getMode(stack);
		String modeName = switch (mode) {
			case MODE_SNIPER -> "Снайперский (долгий натяг, урон x4)";
			case MODE_ANNIHILATION -> "Аннигиляция (взрывает мобов и местность)";
			default -> "Пулемёт (выстрел раз в 0.2 сек)";
		};
		tooltip.add(Text.literal("Активный режим: " + modeName).formatted(Formatting.GOLD));
		tooltip.add(Text.literal("Смена режима: клавиша [R] или [Shift + ПКМ]").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("Работает только при полном Сете Лучника МФО (4/4).").formatted(Formatting.LIGHT_PURPLE));
	}
}
