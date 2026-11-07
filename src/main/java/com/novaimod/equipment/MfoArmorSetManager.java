package com.novaimod.equipment;

import java.util.List;

import com.novaimod.NovaiMod;
import com.novaimod.item.UberGunItem;
import com.novaimod.registry.ModItems;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Управляет двумя эксклюзивными сетами брони МФО (Мечник и Лучник) и правилами скрытых проклятий на броне:
 * 1) Сет Мечника (алмазный/незеритовый): при полном сете (4/4) даёт +2 ХП (+2 Макс. здоровья).
 * 2) Сет Лучника (алмазный чуть слабее по защите: 15 брони / 4 твёрдости вместо 20 / 8):
 *    при полном сете (4/4) купленный в МФО лук автоматически трансформируется в Уберган с 3 режимами!
 * 3) На броне стоят Проклятие несъёмности и Проклятие утраты (исчезает после смерти), убрана Прочность (Unbreaking),
 *    отдельно зачаровать броню нельзя, а ВСЕ зачарования и проклятия полностью скрыты из описания!
 * 4) При улучшении любой части брони до незеритовой на кузнечном столе ВСЕ бонусы, скрытые чары и проклятия сохраняются!
 */
public final class MfoArmorSetManager {
	public static final String NBT_SET_KEY = "novai_mfo_set";
	public static final String NBT_MFO_BOW_KEY = "novai_mfo_bow";
	public static final String NBT_ENCHANT_LOCKED = "novai_enchant_locked";
	public static final String NBT_V32_CURSED = "novai_v32_cursed";
	public static final String SET_SWORDSMAN = "swordsman";
	public static final String SET_ARCHER = "archer";

	private static final Identifier SWORDSMAN_HP_MOD_ID = Identifier.of(NovaiMod.MOD_ID, "swordsman_full_set_hp");

	private MfoArmorSetManager() {
	}

	public static ItemStack createSwordsmanArmorPiece(
			Item diamondArmorItem,
			String customTitle,
			RegistryWrapper.Impl<Enchantment> enchRegistry
	) {
		ItemStack stack = new ItemStack(diamondArmorItem);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customTitle).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		addEnchant(stack, enchRegistry, Enchantments.PROTECTION, 4);

		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putString(NBT_SET_KEY, SET_SWORDSMAN);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

		applyHiddenCursedArmorRules(stack, enchRegistry);

		stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.literal("Сет Мечника МФО").formatted(Formatting.GOLD),
				Text.literal("Бонус полного сета (4/4): +2 ХП").formatted(Formatting.GREEN),
				Text.literal("Бонус сохраняется при улучшении до незерита!").formatted(Formatting.GRAY)
		)));
		return stack;
	}

	public static ItemStack createArcherArmorPiece(
			ArmorItem diamondArmorItem,
			String customTitle,
			double reducedArmor,
			RegistryWrapper.Impl<Enchantment> enchRegistry
	) {
		ItemStack stack = new ItemStack(diamondArmorItem);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customTitle).formatted(Formatting.AQUA, Formatting.BOLD));
		addEnchant(stack, enchRegistry, Enchantments.PROTECTION, 3);

		// Делаем алмазную броню лучника чуть слабее обычной алмазной брони (суммарно 15 брони и 4.0 твёрдости вместо 20 и 8.0)
		EquipmentSlot slot = diamondArmorItem.getSlotType();
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, buildArcherModifiers(slot, reducedArmor, 1.0D, 0.0D));

		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putString(NBT_SET_KEY, SET_ARCHER);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

		applyHiddenCursedArmorRules(stack, enchRegistry);

		stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.literal("Сет Лучника МФО (облегчённый алмаз)").formatted(Formatting.AQUA),
				Text.literal("Бонус полного сета (4/4): превращает лук МФО в УБЕРГАН!").formatted(Formatting.GOLD),
				Text.literal("Бонус сохраняется при улучшении до незерита!").formatted(Formatting.GRAY)
		)));
		return stack;
	}

	/**
	 * Добавляет на броню Проклятие несъёмности + Проклятие утраты, убирает Прочность (Unbreaking),
	 * блокирует отдельное зачарование и полностью скрывает ВСЕ зачарования и проклятия в подсказке!
	 */
	public static void applyHiddenCursedArmorRules(ItemStack stack, RegistryWrapper.Impl<Enchantment> enchRegistry) {
		ItemEnchantmentsComponent existing = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(existing);

		// Убираем зачарование «Прочность» (minecraft:unbreaking)
		Identifier unbreakingId = Identifier.ofVanilla("unbreaking");
		builder.remove(entry -> entry.getKey().map(k -> k.getValue().equals(unbreakingId)).orElse(false));

		// Добавляем Проклятие несъёмности (binding_curse) и Проклятие утраты (vanishing_curse)
		enchRegistry.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("binding_curse")))
				.ifPresent(entry -> builder.set(entry, 1));
		enchRegistry.getOptional(RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.ofVanilla("vanishing_curse")))
				.ifPresent(entry -> builder.set(entry, 1));

		// Полностью скрываем ВСЕ зачарования (включая проклятия!) из описания предмета
		stack.set(DataComponentTypes.ENCHANTMENTS, builder.build().withShowInTooltip(false));

		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_ENCHANT_LOCKED, true);
		nbt.putBoolean(NBT_V32_CURSED, true);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static boolean isEnchantLocked(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (!getArmorSetId(stack).isEmpty()) {
			return true;
		}
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return comp.contains(NBT_ENCHANT_LOCKED) && comp.copyNbt().getBoolean(NBT_ENCHANT_LOCKED);
	}

	public static ItemStack createMfoBow(RegistryWrapper.Impl<Enchantment> enchRegistry) {
		ItemStack bow = new ItemStack(Items.BOW);
		bow.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Лук Взыскателя").formatted(Formatting.GOLD, Formatting.BOLD));
		addEnchant(bow, enchRegistry, Enchantments.POWER, 4);
		addEnchant(bow, enchRegistry, Enchantments.INFINITY, 1);
		addEnchant(bow, enchRegistry, Enchantments.FLAME, 1);
		addEnchant(bow, enchRegistry, Enchantments.UNBREAKING, 3);

		NbtCompound nbt = bow.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_MFO_BOW_KEY, true);
		bow.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

		bow.set(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.literal("В комплекте при покупке: 64 стрелы + Бесконечность").formatted(Formatting.YELLOW),
				Text.literal("В полном Сете Лучника (4/4) превращается в УБЕРГАН!").formatted(Formatting.AQUA)
		)));
		return bow;
	}

	private static AttributeModifiersComponent buildArcherModifiers(
			EquipmentSlot slot,
			double armorVal,
			double toughnessVal,
			double knockbackRes
	) {
		Identifier modId = Identifier.of(NovaiMod.MOD_ID, "archer_armor_" + slot.getName());
		AttributeModifierSlot modSlot = AttributeModifierSlot.forEquipmentSlot(slot);
		AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder()
				.add(
						EntityAttributes.GENERIC_ARMOR,
						new EntityAttributeModifier(modId, armorVal, EntityAttributeModifier.Operation.ADD_VALUE),
						modSlot
				)
				.add(
						EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
						new EntityAttributeModifier(modId, toughnessVal, EntityAttributeModifier.Operation.ADD_VALUE),
						modSlot
				);
		if (knockbackRes > 0.0D) {
			builder.add(
					EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
					new EntityAttributeModifier(modId, knockbackRes, EntityAttributeModifier.Operation.ADD_VALUE),
					modSlot
			);
		}
		return builder.build();
	}

	/**
	 * Вызывается из SmithingTransformRecipeMixin при улучшении брони на кузнечном столе до незеритовой:
	 * гарантирует, что теги сета, проклятия, скрытость зачарований и характеристики незерита корректно сохраняются!
	 */
	public static void onSmithingUpgrade(ItemStack resultStack) {
		if (resultStack == null || resultStack.isEmpty() || !(resultStack.getItem() instanceof ArmorItem armorItem)) {
			return;
		}
		String setId = getArmorSetId(resultStack);
		if (setId.isEmpty()) {
			return;
		}

		// Гарантируем сохранение скрытости всех зачарований и блокировки отдельного зачарования после кузнечного стола
		ItemEnchantmentsComponent ench = resultStack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		resultStack.set(DataComponentTypes.ENCHANTMENTS, ench.withShowInTooltip(false));
		NbtCompound nbt = resultStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_ENCHANT_LOCKED, true);
		resultStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

		EquipmentSlot slot = armorItem.getSlotType();
		if (SET_ARCHER.equals(setId)) {
			double nethArcherArmor = switch (slot) {
				case HEAD -> 2.5D;
				case CHEST -> 7.0D;
				case LEGS -> 5.5D;
				case FEET -> 2.0D;
				default -> 2.0D;
			};
			resultStack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, buildArcherModifiers(slot, nethArcherArmor, 2.0D, 0.1D));
			String pieceTitle = switch (slot) {
				case HEAD -> "Незеритовый шлем Лучника МФО";
				case CHEST -> "Незеритовая кираса Лучника МФО";
				case LEGS -> "Незеритовые поножи Лучника МФО";
				default -> "Незеритовые сапоги Лучника МФО";
			};
			resultStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(pieceTitle).formatted(Formatting.AQUA, Formatting.BOLD));
			resultStack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
					Text.literal("Сет Лучника МФО (улучшен до незерита)").formatted(Formatting.AQUA),
					Text.literal("Бонус полного сета (4/4): превращает лук МФО в УБЕРГАН!").formatted(Formatting.GOLD)
			)));
		} else if (SET_SWORDSMAN.equals(setId)) {
			String pieceTitle = switch (slot) {
				case HEAD -> "Незеритовый шлем Мечника МФО";
				case CHEST -> "Незеритовый китель Мечника МФО";
				case LEGS -> "Незеритовые поножи Мечника МФО";
				default -> "Незеритовые сапоги Мечника МФО";
			};
			resultStack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(pieceTitle).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
			resultStack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
					Text.literal("Сет Мечника МФО (улучшен до незерита)").formatted(Formatting.GOLD),
					Text.literal("Бонус полного сета (4/4): +2 ХП").formatted(Formatting.GREEN)
			)));
		}
	}

	public static String getArmorSetId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		if (!comp.contains(NBT_SET_KEY)) {
			return "";
		}
		return comp.copyNbt().getString(NBT_SET_KEY);
	}

	public static boolean isMfoBow(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BowItem) || stack.isOf(ModItems.UBER_GUN)) {
			return false;
		}
		NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return comp.contains(NBT_MFO_BOW_KEY) && comp.copyNbt().getBoolean(NBT_MFO_BOW_KEY);
	}

	public static boolean isWearingFullSet(ServerPlayerEntity player, String targetSetId) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack eq = player.getEquippedStack(slot);
			if (!targetSetId.equals(getArmorSetId(eq))) {
				return false;
			}
		}
		return true;
	}

	public static void tickPlayers(MinecraftServer server) {
		RegistryWrapper.Impl<Enchantment> enchRegistry = server.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);

		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive()) {
				continue;
			}

			// 0. Авто-обновление уже купленной брони МФО из старых версий мира под новые правила проклятий и скрытых чар
			migrateLegacyMfoArmor(player, enchRegistry);

			// 1. Проверка полного Сета Мечника (4/4): +2 ХП (+2.0 Max Health)
			boolean fullSwordsman = isWearingFullSet(player, SET_SWORDSMAN);
			EntityAttributeInstance maxHpAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (maxHpAttr != null) {
				boolean hasBonus = maxHpAttr.getModifier(SWORDSMAN_HP_MOD_ID) != null;
				if (fullSwordsman && !hasBonus) {
					maxHpAttr.addPersistentModifier(new EntityAttributeModifier(
							SWORDSMAN_HP_MOD_ID,
							2.0D,
							EntityAttributeModifier.Operation.ADD_VALUE
					));
				} else if (!fullSwordsman && hasBonus) {
					maxHpAttr.removeModifier(SWORDSMAN_HP_MOD_ID);
					if (player.getHealth() > player.getMaxHealth()) {
						player.setHealth(player.getMaxHealth());
					}
				}
			}

			// 2. Проверка полного Сета Лучника (4/4): купленный лук МФО <-> УБЕРГАН!
			boolean fullArcher = isWearingFullSet(player, SET_ARCHER);
			if (fullArcher) {
				transformBowsToUberGun(player);
			} else {
				revertUberGunsToBow(player, enchRegistry);
			}
		}
	}

	private static void migrateLegacyMfoArmor(ServerPlayerEntity player, RegistryWrapper.Impl<Enchantment> enchRegistry) {
		for (ItemStack armor : player.getInventory().armor) {
			if (!getArmorSetId(armor).isEmpty()) {
				NbtComponent comp = armor.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
				if (!comp.contains(NBT_V32_CURSED)) {
					applyHiddenCursedArmorRules(armor, enchRegistry);
				}
			}
		}
		for (ItemStack main : player.getInventory().main) {
			if (!getArmorSetId(main).isEmpty()) {
				NbtComponent comp = main.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
				if (!comp.contains(NBT_V32_CURSED)) {
					applyHiddenCursedArmorRules(main, enchRegistry);
				}
			}
		}
	}

	private static boolean shouldRevertUberGun(ServerPlayerEntity player, ItemStack st) {
		if (!st.isOf(ModItems.UBER_GUN)) {
			return false;
		}
		if (!player.isCreative()) {
			return true;
		}
		NbtComponent comp = st.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
		return comp.contains(NBT_MFO_BOW_KEY) && comp.copyNbt().getBoolean(NBT_MFO_BOW_KEY);
	}

	private static void transformBowsToUberGun(ServerPlayerEntity player) {
		boolean transformedAny = false;
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			ItemStack st = player.getInventory().main.get(i);
			if (isMfoBow(st)) {
				player.getInventory().main.set(i, convertBowToUberGun(st));
				transformedAny = true;
			}
		}
		for (int i = 0; i < player.getInventory().offHand.size(); i++) {
			ItemStack st = player.getInventory().offHand.get(i);
			if (isMfoBow(st)) {
				player.getInventory().offHand.set(i, convertBowToUberGun(st));
				transformedAny = true;
			}
		}
		if (transformedAny) {
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.85F, 1.4F);
			player.sendMessage(
					Text.literal("⚡ Сет Лучника (4/4): Ваш Лук Взыскателя трансформировался в УБЕРГАН! (Клавиша R / Shift+ПКМ — смена режима)")
							.formatted(Formatting.AQUA, Formatting.BOLD),
					true
			);
		}
	}

	private static void revertUberGunsToBow(ServerPlayerEntity player, RegistryWrapper.Impl<Enchantment> enchRegistry) {
		boolean revertedAny = false;
		for (int i = 0; i < player.getInventory().main.size(); i++) {
			ItemStack st = player.getInventory().main.get(i);
			if (shouldRevertUberGun(player, st)) {
				player.getInventory().main.set(i, convertUberGunToBow(st, enchRegistry));
				revertedAny = true;
			}
		}
		for (int i = 0; i < player.getInventory().offHand.size(); i++) {
			ItemStack st = player.getInventory().offHand.get(i);
			if (shouldRevertUberGun(player, st)) {
				player.getInventory().offHand.set(i, convertUberGunToBow(st, enchRegistry));
				revertedAny = true;
			}
		}
		if (revertedAny) {
			player.stopUsingItem();
			player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.2F);
			player.sendMessage(
					Text.literal("Сет Лучника не полон — Уберган вернулся в форму Лука Взыскателя.")
							.formatted(Formatting.YELLOW),
					true
			);
		}
	}

	private static ItemStack convertBowToUberGun(ItemStack bow) {
		ItemStack uber = new ItemStack(ModItems.UBER_GUN, 1);
		uber.setDamage(Math.min(bow.getDamage(), uber.getMaxDamage() - 1));
		NbtCompound oldNbt = bow.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		int savedMode = oldNbt.contains(UberGunItem.NBT_MODE_KEY) ? oldNbt.getInt(UberGunItem.NBT_MODE_KEY) : UberGunItem.MODE_MACHINE_GUN;
		oldNbt.putBoolean(NBT_MFO_BOW_KEY, true);
		oldNbt.putInt(UberGunItem.NBT_MODE_KEY, savedMode);
		uber.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(oldNbt));
		return uber;
	}

	private static ItemStack convertUberGunToBow(ItemStack uber, RegistryWrapper.Impl<Enchantment> enchRegistry) {
		ItemStack bow = createMfoBow(enchRegistry);
		bow.setDamage(Math.min(uber.getDamage(), bow.getMaxDamage() - 1));
		NbtCompound nbt = bow.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putInt(UberGunItem.NBT_MODE_KEY, UberGunItem.getMode(uber));
		bow.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
		return bow;
	}

	private static void addEnchant(ItemStack stack, RegistryWrapper.Impl<Enchantment> registry, RegistryKey<Enchantment> key, int level) {
		registry.getOptional(key).ifPresent(entry -> stack.addEnchantment(entry, level));
	}
}
