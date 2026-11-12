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
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.item.trim.ArmorTrimMaterial;
import net.minecraft.item.trim.ArmorTrimMaterials;
import net.minecraft.item.trim.ArmorTrimPattern;
import net.minecraft.item.trim.ArmorTrimPatterns;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
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
	public static final String NBT_V33_TRIMMED = "novai_v33_trimmed";
	public static final String NBT_V36_SWORDSMAN_LORE = "novai_v36_sword_lore";
	public static final String SET_SWORDSMAN = "swordsman";
	public static final String SET_ARCHER = "archer";
	public static final double SWORDSMAN_FULL_SET_HP_BONUS = 6.0D; // +6 ХП (+3 сердечка: было +1 сердечко, добавлено ещё +2 сердечка!)

	private static final Identifier SWORDSMAN_HP_MOD_ID = Identifier.of(NovaiMod.MOD_ID, "swordsman_full_set_hp");

	private MfoArmorSetManager() {
	}

	public static ItemStack createSwordsmanArmorPiece(
			Item diamondArmorItem,
			String customTitle,
			RegistryWrapper.WrapperLookup registriesLookup
	) {
		RegistryWrapper.Impl<Enchantment> enchRegistry = registriesLookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
		ItemStack stack = new ItemStack(diamondArmorItem);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customTitle).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
		addEnchant(stack, enchRegistry, Enchantments.PROTECTION, 4);

		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putString(NBT_SET_KEY, SET_SWORDSMAN);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

		applyHiddenCursedArmorRules(stack, enchRegistry);
		applyVanillaSetTrim(stack, SET_SWORDSMAN, registriesLookup);

		stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.literal("Сет Мечника МФО").formatted(Formatting.GOLD),
				Text.literal("Бонус полного сета (4/4): +6 ХП (+3 сердечка)").formatted(Formatting.GREEN),
				Text.literal("Бонус сохраняется при улучшении до незерита!").formatted(Formatting.GRAY)
		)));
		NbtCompound nbtLore = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbtLore.putBoolean(NBT_V36_SWORDSMAN_LORE, true);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtLore));
		return stack;
	}

	public static ItemStack createArcherArmorPiece(
			ArmorItem diamondArmorItem,
			String customTitle,
			double reducedArmor,
			RegistryWrapper.WrapperLookup registriesLookup
	) {
		RegistryWrapper.Impl<Enchantment> enchRegistry = registriesLookup.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
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
		applyVanillaSetTrim(stack, SET_ARCHER, registriesLookup);

		stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
				Text.literal("Сет Лучника МФО (облегчённый алмаз)").formatted(Formatting.AQUA),
				Text.literal("Бонус полного сета (4/4): превращает лук МФО в УБЕРГАН!").formatted(Formatting.GOLD),
				Text.literal("Бонус сохраняется при улучшении до незерита!").formatted(Formatting.GRAY)
		)));
		return stack;
	}

	/**
	 * Меняет внешний вид обоих сетов МФО с помощью ванильных кузнечных шаблонов (ArmorTrim) без добавления новых текстур:
	 * - Сет Мечника: шаблон «Тишина» (Silence) + Золото (Gold) — богатый золотой узор воина.
	 * - Сет Лучника: шаблон «Поток» (Flow) + Аметист (Amethyst) — стремительный фиолетовый вихрь стрелка.
	 */
	public static void applyVanillaSetTrim(ItemStack stack, String setId, RegistryWrapper.WrapperLookup registriesLookup) {
		Item templateItem = SET_SWORDSMAN.equals(setId)
				? Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE
				: Items.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE;
		Item materialItem = SET_SWORDSMAN.equals(setId)
				? Items.GOLD_INGOT
				: Items.AMETHYST_SHARD;

		java.util.Optional<RegistryEntry.Reference<ArmorTrimPattern>> patternOpt =
				ArmorTrimPatterns.get(registriesLookup, new ItemStack(templateItem));
		java.util.Optional<RegistryEntry.Reference<ArmorTrimMaterial>> materialOpt =
				ArmorTrimMaterials.get(registriesLookup, new ItemStack(materialItem));

		if (patternOpt.isPresent() && materialOpt.isPresent()) {
			stack.set(DataComponentTypes.TRIM, new ArmorTrim(materialOpt.get(), patternOpt.get(), false));
		}

		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_V33_TRIMMED, true);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
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

		// Гарантируем сохранение скрытости всех зачарований и ванильной отделки (ArmorTrim) после кузнечного стола
		ItemEnchantmentsComponent ench = resultStack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		resultStack.set(DataComponentTypes.ENCHANTMENTS, ench.withShowInTooltip(false));
		ArmorTrim existingTrim = resultStack.get(DataComponentTypes.TRIM);
		if (existingTrim != null) {
			resultStack.set(DataComponentTypes.TRIM, existingTrim.withShowInTooltip(false));
		}
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
					Text.literal("Бонус полного сета (4/4): +6 ХП (+3 сердечка)").formatted(Formatting.GREEN)
			)));
			NbtCompound nbtUp = resultStack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
			nbtUp.putBoolean(NBT_V36_SWORDSMAN_LORE, true);
			resultStack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtUp));
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

			// 0. Авто-обновление уже купленной брони МФО из старых версий мира под новые правила проклятий, скрытых чар и ванильной отделки (ArmorTrim)
			migrateLegacyMfoArmor(player, server.getRegistryManager(), enchRegistry);

			// 1. Проверка полного Сета Мечника (4/4): +6 ХП (+3 сердечка!)
			boolean fullSwordsman = isWearingFullSet(player, SET_SWORDSMAN);
			EntityAttributeInstance maxHpAttr = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
			if (maxHpAttr != null) {
				EntityAttributeModifier existing = maxHpAttr.getModifier(SWORDSMAN_HP_MOD_ID);
				if (fullSwordsman) {
					if (existing == null || existing.value() != SWORDSMAN_FULL_SET_HP_BONUS) {
						maxHpAttr.overwritePersistentModifier(new EntityAttributeModifier(
								SWORDSMAN_HP_MOD_ID,
								SWORDSMAN_FULL_SET_HP_BONUS,
								EntityAttributeModifier.Operation.ADD_VALUE
						));
					}
				} else if (existing != null) {
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

	private static void migrateLegacyMfoArmor(
			ServerPlayerEntity player,
			RegistryWrapper.WrapperLookup registriesLookup,
			RegistryWrapper.Impl<Enchantment> enchRegistry
	) {
		for (ItemStack armor : player.getInventory().armor) {
			String setId = getArmorSetId(armor);
			if (!setId.isEmpty()) {
				NbtComponent comp = armor.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
				if (!comp.contains(NBT_V32_CURSED)) {
					applyHiddenCursedArmorRules(armor, enchRegistry);
				}
				if (!comp.contains(NBT_V33_TRIMMED)) {
					applyVanillaSetTrim(armor, setId, registriesLookup);
				}
				if (SET_SWORDSMAN.equals(setId) && !comp.contains(NBT_V36_SWORDSMAN_LORE)) {
					refreshSwordsmanLore(armor);
				}
			}
		}
		for (ItemStack main : player.getInventory().main) {
			String setId = getArmorSetId(main);
			if (!setId.isEmpty()) {
				NbtComponent comp = main.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
				if (!comp.contains(NBT_V32_CURSED)) {
					applyHiddenCursedArmorRules(main, enchRegistry);
				}
				if (!comp.contains(NBT_V33_TRIMMED)) {
					applyVanillaSetTrim(main, setId, registriesLookup);
				}
				if (SET_SWORDSMAN.equals(setId) && !comp.contains(NBT_V36_SWORDSMAN_LORE)) {
					refreshSwordsmanLore(main);
				}
			}
		}
	}

	private static void refreshSwordsmanLore(ItemStack stack) {
		boolean isNetherite = stack.isOf(Items.NETHERITE_HELMET)
				|| stack.isOf(Items.NETHERITE_CHESTPLATE)
				|| stack.isOf(Items.NETHERITE_LEGGINGS)
				|| stack.isOf(Items.NETHERITE_BOOTS);
		if (isNetherite) {
			stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
					Text.literal("Сет Мечника МФО (улучшен до незерита)").formatted(Formatting.GOLD),
					Text.literal("Бонус полного сета (4/4): +6 ХП (+3 сердечка)").formatted(Formatting.GREEN)
			)));
		} else {
			stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
					Text.literal("Сет Мечника МФО").formatted(Formatting.GOLD),
					Text.literal("Бонус полного сета (4/4): +6 ХП (+3 сердечка)").formatted(Formatting.GREEN),
					Text.literal("Бонус сохраняется при улучшении до незерита!").formatted(Formatting.GRAY)
			)));
		}
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_V36_SWORDSMAN_LORE, true);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
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
