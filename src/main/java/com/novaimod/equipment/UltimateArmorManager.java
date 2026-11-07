package com.novaimod.equipment;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.novaimod.ai.evolution.MobEvolutionManager;
import com.novaimod.data.ModPersistentState;
import com.novaimod.item.RealityRiftBladeItem;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEffects;
import com.novaimod.registry.ModItems;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

/**
 * Управляет единым невидимым предметом Альтимейт-брони — «Поджопником» (v3.7.0):
 * 1) Надевается в отдельный скрытый слот инвентаря (виден только если Поджопник есть в инвентаре / на курсоре / надет).
 * 2) Никак не отображается на модели и скине персонажа (и не пускает никаких частиц вокруг тела).
 * 3) Поглощает 100% ВСЕГО урона в игре, кроме чистого Процентного урона!
 * 4) Даёт свободный полёт в любом режиме игры.
 */
public final class UltimateArmorManager {
	private static final Set<UUID> FLIGHT_GRANTED_BY_ARMOR = new HashSet<>();

	private UltimateArmorManager() {
	}

	public static boolean isWearingFullUltimateArmor(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp && sp.getServer() != null) {
			return ModPersistentState.get(sp.getServer()).getAccount(sp.getUuid()).seatPadEquipped;
		}
		return false;
	}

	public static void setSeatPadEquipped(ServerPlayerEntity player, boolean equipped) {
		if (player == null || player.getServer() == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());
		acc.seatPadEquipped = equipped;
		state.markDirty();
		ServerPlayNetworking.send(player, new ModPayloads.SeatPadSyncPayload(equipped));
	}

	/**
	 * Обрабатывает клики по секретному слоту Поджопника в инвентаре:
	 * action == 0: обычный клик мышкой (надеть с курсора или снять на курсор, если не Креатив)
	 * action == 1: Shift + клик по слоту Поджопника (снять сразу в инвентарь)
	 * action == 2: Shift + клик по Поджопнику в обычном слоте инвентаря invSlot (быстро надеть)
	 * action == 3: надеть в режиме Креатив (клиент локально убрал предмет с креативного курсора)
	 * action == 4: снять в режиме Креатив (клиент локально взял предмет на креативный курсор)
	 */
	public static void handleSeatPadSlotAction(ServerPlayerEntity player, int action, int invSlot) {
		if (player == null || player.getServer() == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(player.getServer());
		ModPersistentState.PlayerFinanceData acc = state.getAccount(player.getUuid());

		if (action == 3) {
			if (player.isCreative() && !acc.seatPadEquipped) {
				setSeatPadEquipped(player, true);
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 1.15F);
			}
			return;
		}
		if (action == 4) {
			if (player.isCreative() && acc.seatPadEquipped) {
				setSeatPadEquipped(player, false);
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 0.9F);
			}
			return;
		}

		if (action == 0) {
			ItemStack cursor = player.currentScreenHandler.getCursorStack();
			if (!acc.seatPadEquipped && cursor.isOf(ModItems.ULTIMATE_SEAT_PAD)) {
				cursor.decrement(1);
				player.currentScreenHandler.setCursorStack(cursor.isEmpty() ? ItemStack.EMPTY : cursor);
				player.currentScreenHandler.syncState();
				setSeatPadEquipped(player, true);
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 1.15F);
			} else if (acc.seatPadEquipped && cursor.isEmpty()) {
				setSeatPadEquipped(player, false);
				player.currentScreenHandler.setCursorStack(new ItemStack(ModItems.ULTIMATE_SEAT_PAD, 1));
				player.currentScreenHandler.syncState();
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 0.9F);
			}
			return;
		}

		if (action == 1) {
			if (acc.seatPadEquipped) {
				setSeatPadEquipped(player, false);
				ItemStack unequipped = new ItemStack(ModItems.ULTIMATE_SEAT_PAD, 1);
				if (!player.getInventory().insertStack(unequipped)) {
					player.dropItem(unequipped, false);
				}
				player.playerScreenHandler.sendContentUpdates();
				player.currentScreenHandler.syncState();
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 0.9F);
			}
			return;
		}

		if (action == 2 && !acc.seatPadEquipped) {
			PlayerInventory inv = player.getInventory();
			if (invSlot >= 0 && invSlot < inv.size() && inv.getStack(invSlot).isOf(ModItems.ULTIMATE_SEAT_PAD)) {
				inv.getStack(invSlot).decrement(1);
				inv.markDirty();
				player.playerScreenHandler.sendContentUpdates();
				player.currentScreenHandler.syncState();
				setSeatPadEquipped(player, true);
				player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 1.15F);
				return;
			}
			for (int i = 0; i < inv.size(); i++) {
				if (inv.getStack(i).isOf(ModItems.ULTIMATE_SEAT_PAD)) {
					inv.getStack(i).decrement(1);
					inv.markDirty();
					player.playerScreenHandler.sendContentUpdates();
					player.currentScreenHandler.syncState();
					setSeatPadEquipped(player, true);
					player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 0.9F, 1.15F);
					return;
				}
			}
		}
	}

	/**
	 * Проверяет, содержит ли инвентарь игрока (или курсор экрана) предмет «Поджопник».
	 */
	public static boolean playerHasSeatPadItem(PlayerEntity player, ItemStack cursorStack) {
		if (cursorStack != null && cursorStack.isOf(ModItems.ULTIMATE_SEAT_PAD)) {
			return true;
		}
		PlayerInventory inv = player.getInventory();
		for (ItemStack st : inv.main) {
			if (st.isOf(ModItems.ULTIMATE_SEAT_PAD)) {
				return true;
			}
		}
		for (ItemStack st : inv.offHand) {
			if (st.isOf(ModItems.ULTIMATE_SEAT_PAD)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Проверяет, несёт ли данный удар эффект процентного урона (единственный тип урона, пробивающий Поджопник!).
	 */
	public static boolean isPercentageDamageHit(DamageSource source) {
		Entity attacker = source.getAttacker();
		if (attacker == null || source.getSource() != attacker || source.isIn(DamageTypeTags.IS_PROJECTILE)) {
			return false;
		}
		if (attacker instanceof MobEntity mob) {
			if (mob.getCommandTags().contains(MobEvolutionManager.TAG_PERCENT_MELEE)
					&& MobEvolutionManager.isMeleeHostile(mob)) {
				return true;
			}
		}
		if (attacker instanceof LivingEntity living && living.getMainHandStack().getItem() instanceof RealityRiftBladeItem) {
			return true;
		}
		return false;
	}

	/**
	 * Возвращает ТОЛЬКО чистый процентный урон (1..4 ХП = 5%..20% от макс. здоровья), полностью поглощая весь остальной физический урон!
	 */
	public static float computePurePercentageDamageOnly(ServerPlayerEntity victim, DamageSource source) {
		Entity attacker = source.getAttacker();
		int step = (attacker instanceof LivingEntity le && le.getMainHandStack().getItem() instanceof RealityRiftBladeItem)
				? 4
				: (1 + victim.getRandom().nextInt(4));
		float pct = step * 0.05F;
		float purePercentHp = MathHelper.clamp((float) Math.round(victim.getMaxHealth() * pct), 1.0F, Math.max(4.0F, victim.getMaxHealth() * 0.20F));

		victim.addStatusEffect(new StatusEffectInstance(ModEffects.PERCENT_EROSION, 50, step - 1, false, true, true), attacker);
		victim.getServerWorld().spawnParticles(
				ParticleTypes.DAMAGE_INDICATOR,
				victim.getX(),
				victim.getBodyY(0.6D),
				victim.getZ(),
				step + 1,
				0.25D,
				0.25D,
				0.25D,
				0.08D
		);
		return purePercentHp;
	}

	public static void tickPlayers(MinecraftServer server) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive()) {
				FLIGHT_GRANTED_BY_ARMOR.remove(player.getUuid());
				continue;
			}

			// Авто-миграция старых частей Альтимейт Брони из v3.6.0 в единый невидимый «Поджопник»!
			migrateLegacyUltimatePieces(player);

			boolean wearingPad = isWearingFullUltimateArmor(player);
			if (wearingPad) {
				player.fallDistance = 0.0F;
				if (player.isOnFire()) {
					player.extinguish();
				}
				if (player.getAir() < player.getMaxAir()) {
					player.setAir(player.getMaxAir());
				}

				if (!player.getAbilities().allowFlying) {
					player.getAbilities().allowFlying = true;
					player.sendAbilitiesUpdate();
				}
				FLIGHT_GRANTED_BY_ARMOR.add(player.getUuid());
			} else if (FLIGHT_GRANTED_BY_ARMOR.remove(player.getUuid())) {
				if (!player.isCreative() && !player.isSpectator()) {
					player.getAbilities().allowFlying = false;
					player.getAbilities().flying = false;
					player.sendAbilitiesUpdate();
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false, false));
				}
			}
		}
	}

	private static void migrateLegacyUltimatePieces(ServerPlayerEntity player) {
		boolean removedAny = false;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack eq = player.getEquippedStack(slot);
			if (!eq.isEmpty() && eq.getItem() instanceof UltimateArmorItem) {
				player.equipStack(slot, ItemStack.EMPTY);
				removedAny = true;
			}
		}
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			ItemStack st = player.getInventory().main.get(i);
			if (!st.isEmpty() && st.getItem() instanceof UltimateArmorItem) {
				player.getInventory().main.set(i, ItemStack.EMPTY);
				removedAny = true;
			}
		}
		if (removedAny) {
			if (!isWearingFullUltimateArmor(player)) {
				setSeatPadEquipped(player, true);
			} else {
				ItemStack pad = new ItemStack(ModItems.ULTIMATE_SEAT_PAD, 1);
				if (!player.getInventory().insertStack(pad)) {
					player.dropItem(pad, false);
				}
			}
		}
	}
}
