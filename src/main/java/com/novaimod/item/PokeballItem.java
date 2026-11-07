package com.novaimod.item;

import java.util.List;
import java.util.UUID;

import com.novaimod.entity.PokeballProjectileEntity;
import com.novaimod.world.PokeballDimensionManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * «Покебол» (`novaimod:pokeball`):
 * - ПКМ (пустой): бросить Покебол!
 *   • Попадание в любое живое существо (Кошкодевочку, жителя, животное, моба) — помещает его внутрь Покебола для переноса!
 *   • Попадание в игрока — затягивает игрока ВНУТРЬ Покебола в личное обустроенное жилое мини-пространство!
 * - ПКМ (занятый): бросить Покебол на блок, чтобы выпустить существо или игрока наружу!
 * - Shift + ПКМ себе под ноги (пустой Покебол): залезть внутрь собственного Покебола самому (удобно даже в одиночной игре!).
 */
public class PokeballItem extends Item {
	private static final String KEY_TYPE = "CaptureType";
	private static final String KEY_NAME = "CapturedName";
	private static final String KEY_HP = "MobHp";
	private static final String KEY_MAX_HP = "MobMaxHp";
	private static final String KEY_MOB_NBT = "MobNbt";
	private static final String KEY_PLAYER_UUID = "CapturedPlayerUuid";
	private static final String KEY_POCKET_ID = "PocketId";

	public PokeballItem(Settings settings) {
		super(settings);
	}

	public static boolean isFilled(ItemStack stack) {
		return !getCaptureType(stack).isEmpty();
	}

	public static String getCaptureType(ItemStack stack) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (custom == null) {
			return "";
		}
		return custom.copyNbt().getString(KEY_TYPE);
	}

	public static String getCapturedName(ItemStack stack) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		return custom != null ? custom.copyNbt().getString(KEY_NAME) : "";
	}

	public static int getPocketId(ItemStack stack) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		return custom != null ? custom.copyNbt().getInt(KEY_POCKET_ID) : 0;
	}

	public static NbtCompound getMobNbt(ItemStack stack) {
		NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (custom == null) {
			return null;
		}
		NbtCompound root = custom.copyNbt();
		return root.contains(KEY_MOB_NBT, NbtElement.COMPOUND_TYPE) ? root.getCompound(KEY_MOB_NBT) : null;
	}

	public static void storeMobCapture(ItemStack stack, String name, int hp, int maxHp, NbtCompound mobNbt) {
		NbtCompound root = new NbtCompound();
		root.putString(KEY_TYPE, "mob");
		root.putString(KEY_NAME, name);
		root.putInt(KEY_HP, hp);
		root.putInt(KEY_MAX_HP, maxHp);
		root.put(KEY_MOB_NBT, mobNbt);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
		stack.set(DataComponentTypes.CUSTOM_NAME,
				Text.literal("Покебол [").formatted(Formatting.RED)
						.append(Text.literal(name).formatted(Formatting.AQUA, Formatting.BOLD))
						.append(Text.literal("]").formatted(Formatting.RED)));
	}

	public static void storePlayerCapture(ItemStack stack, UUID playerUuid, String playerName, int pocketId) {
		NbtCompound root = new NbtCompound();
		root.putString(KEY_TYPE, "player");
		root.putString(KEY_NAME, playerName);
		root.putUuid(KEY_PLAYER_UUID, playerUuid);
		root.putInt(KEY_POCKET_ID, pocketId);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
		stack.set(DataComponentTypes.CUSTOM_NAME,
				Text.literal("Покебол [Игрок: ").formatted(Formatting.GOLD)
						.append(Text.literal(playerName).formatted(Formatting.GREEN, Formatting.BOLD))
						.append(Text.literal("]").formatted(Formatting.GOLD)));
	}

	public static void clearPokeballData(ItemStack stack) {
		stack.remove(DataComponentTypes.CUSTOM_DATA);
		stack.remove(DataComponentTypes.CUSTOM_NAME);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return isFilled(stack);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack held = user.getStackInHand(hand);
		if (world.isClient()) {
			return TypedActionResult.success(held, true);
		}
		if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)) {
			return TypedActionResult.pass(held);
		}

		// Shift + ПКМ себе под ноги с пустым Покеболом: залезть внутрь своего Покебола самому!
		if (sp.isSneaking() && sp.getPitch() > 65.0F && !isFilled(held) && !PokeballDimensionManager.isPlayerInsideAnyPokeball(sp)) {
			ItemStack selfBall = held.copyWithCount(1);
			if (!sp.getAbilities().creativeMode) {
				held.decrement(1);
			}
			var dropPos = sp.getPos();
			int pocketId = PokeballDimensionManager.capturePlayerIntoPokeball(sp, 0);
			if (pocketId > 0) {
				storePlayerCapture(selfBall, sp.getUuid(), sp.getName().getString(), pocketId);
				PokeballProjectileEntity.spawnIndestructibleBallDrop(sw, dropPos, selfBall);
			}
			return TypedActionResult.success(held, false);
		}

		ItemStack thrownCopy = held.copyWithCount(1);
		if (!sp.getAbilities().creativeMode || isFilled(held)) {
			held.decrement(1);
		}

		sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.9F, 1.15F);
		PokeballProjectileEntity proj = new PokeballProjectileEntity(sw, sp, thrownCopy);
		proj.setPosition(sp.getX(), sp.getEyeY() - 0.1D, sp.getZ());
		proj.setVelocity(sp, sp.getPitch(), sp.getYaw(), 0.0F, 1.35F, 0.5F);
		sw.spawnEntity(proj);
		sp.getItemCooldownManager().set(this, 8);

		return TypedActionResult.success(held, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		String capType = getCaptureType(stack);
		if ("mob".equals(capType)) {
			NbtComponent c = stack.get(DataComponentTypes.CUSTOM_DATA);
			int hp = c != null ? c.copyNbt().getInt(KEY_HP) : 0;
			int maxHp = c != null ? c.copyNbt().getInt(KEY_MAX_HP) : 0;
			tooltip.add(Text.literal("Внутри существо: ").formatted(Formatting.GRAY)
					.append(Text.literal(getCapturedName(stack)).formatted(Formatting.AQUA, Formatting.BOLD)));
			tooltip.add(Text.literal("Здоровье: " + hp + " / " + maxHp + " ХП").formatted(Formatting.GREEN));
			tooltip.add(Text.literal("ПКМ — бросить Покебол, чтобы выпустить существо!").formatted(Formatting.YELLOW));
		} else if ("player".equals(capType)) {
			tooltip.add(Text.literal("Внутри живёт игрок: ").formatted(Formatting.GRAY)
					.append(Text.literal(getCapturedName(stack)).formatted(Formatting.GREEN, Formatting.BOLD)));
			tooltip.add(Text.literal("Сейчас он отдыхает в уютной комнате внутри Покебола!").formatted(Formatting.LIGHT_PURPLE));
			tooltip.add(Text.literal("ПКМ — бросить на блок, чтобы выпустить игрока сюда!").formatted(Formatting.YELLOW));
		} else {
			tooltip.add(Text.literal("Карманная сфера переноса и жилья").formatted(Formatting.RED, Formatting.BOLD));
			tooltip.add(Text.literal("• Бросок в существо — ловит и переносит кого угодно (кошкодевочек, жителей, мобов)!").formatted(Formatting.WHITE));
			tooltip.add(Text.literal("• Бросок в игрока — отправляет его внутрь уютного дома в Покеболе!").formatted(Formatting.AQUA));
			tooltip.add(Text.literal("• Shift + ПКМ себе под ноги — залезть внутрь Покебола самому!").formatted(Formatting.GOLD));
			tooltip.add(Text.literal("Крафт: 3 красной шерсти, 3 железа, 2 белой шерсти и 1 жемчуг Края").formatted(Formatting.DARK_GRAY));
		}
	}
}
