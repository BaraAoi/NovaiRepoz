package com.novaimod.economy;

import java.util.ArrayList;
import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.registry.ModItems;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Каталоги товаров всех заведений:
 * - Инструменты удорожены на +50% с красивым ценником.
 * - Зелья удорожены на +25%, снабжены авторскими красивыми названиями и красивыми округлёнными ценами (119, 139, 149, 189, 449, 1990, 1999 и т.д.).
 */
public final class ShopCatalogs {
	public record OfferEntry(
			String offerId,
			ItemStack stack,
			long price,
			String tierLabel,
			int stock
	) {
	}

	private ShopCatalogs() {
	}

	public static List<OfferEntry> buildCatalog(MinecraftServer server, String shopKey) {
		List<OfferEntry> list = new ArrayList<>();
		RegistryWrapper.Impl<Enchantment> enchRegistry = server.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);

		if ("grocery_1".equals(shopKey)) {
			// Продавец №1: Обычные и Элитные продукты (пекарня и ферма)
			list.add(new OfferEntry("g1_bread", new ItemStack(Items.BREAD, 2), 19, "Обычный", -1));
			list.add(new OfferEntry("g1_potato", new ItemStack(Items.BAKED_POTATO, 3), 25, "Обычный", -1));
			list.add(new OfferEntry("g1_apple", new ItemStack(Items.APPLE, 4), 25, "Обычный", -1));
			list.add(new OfferEntry("g1_carrot", new ItemStack(Items.CARROT, 4), 19, "Обычный", -1));
			list.add(new OfferEntry("g1_cookie", new ItemStack(Items.COOKIE, 6), 19, "Обычный", -1));
			list.add(new OfferEntry("g1_pie", new ItemStack(Items.PUMPKIN_PIE, 2), 29, "Обычный", -1));

			list.add(new OfferEntry("g1_gcarrot", new ItemStack(Items.GOLDEN_CARROT, 2), 149, "Элитный", -1));
			list.add(new OfferEntry("g1_gapple", new ItemStack(Items.GOLDEN_APPLE, 1), 399, "Элитный", -1));
			list.add(new OfferEntry("g1_cake", new ItemStack(Items.CAKE, 1), 119, "Элитный", -1));
			list.add(new OfferEntry("g1_glow", named(new ItemStack(Items.GLOW_BERRIES, 8), "Отборные светящиеся ягоды", Formatting.GOLD), 99, "Элитный", -1));
			return list;
		}

		if ("grocery_2".equals(shopKey)) {
			// Продавец №2: Обычные и Элитные мясные деликатесы
			list.add(new OfferEntry("g2_beef", new ItemStack(Items.COOKED_BEEF, 2), 39, "Обычный", -1));
			list.add(new OfferEntry("g2_pork", new ItemStack(Items.COOKED_PORKCHOP, 2), 39, "Обычный", -1));
			list.add(new OfferEntry("g2_chicken", new ItemStack(Items.COOKED_CHICKEN, 3), 35, "Обычный", -1));
			list.add(new OfferEntry("g2_mutton", new ItemStack(Items.COOKED_MUTTON, 3), 35, "Обычный", -1));
			list.add(new OfferEntry("g2_salmon", new ItemStack(Items.COOKED_SALMON, 3), 35, "Обычный", -1));
			list.add(new OfferEntry("g2_stew", new ItemStack(Items.MUSHROOM_STEW, 1), 29, "Обычный", -1));

			list.add(new OfferEntry("g2_steak_vip", named(new ItemStack(Items.COOKED_BEEF, 8), "Мраморный рибай шефа", Formatting.GOLD), 199, "Элитный", -1));
			list.add(new OfferEntry("g2_rabbit", named(new ItemStack(Items.RABBIT_STEW, 1), "Царское жаркое", Formatting.GOLD), 129, "Элитный", -1));
			list.add(new OfferEntry("g2_honey", named(new ItemStack(Items.HONEY_BOTTLE, 3), "Горный элитный мёд", Formatting.GOLD), 139, "Элитный", -1));
			list.add(new OfferEntry("g2_egapple", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), 2490, "Элитный", -1));
			return list;
		}

		if ("miner".equals(shopKey)) {
			// Шахтёрская лавка: все инструменты дороже на +50% с красивыми ценами + материалы (без незерита!)
			list.add(new OfferEntry("m_wood_pick", new ItemStack(Items.WOODEN_PICKAXE), 19, "Кирки", -1));
			list.add(new OfferEntry("m_stone_pick", new ItemStack(Items.STONE_PICKAXE), 45, "Кирки", -1));
			list.add(new OfferEntry("m_iron_pick", new ItemStack(Items.IRON_PICKAXE), 179, "Кирки", -1));
			list.add(new OfferEntry("m_gold_pick", new ItemStack(Items.GOLDEN_PICKAXE), 199, "Кирки", -1));
			list.add(new OfferEntry("m_dia_pick", new ItemStack(Items.DIAMOND_PICKAXE), 990, "Кирки", -1));

			ItemStack drillPick = named(new ItemStack(Items.DIAMOND_PICKAXE), "Кирка бригадира шахты", Formatting.AQUA);
			enchant(drillPick, enchRegistry, Enchantments.EFFICIENCY, 3);
			enchant(drillPick, enchRegistry, Enchantments.UNBREAKING, 2);
			list.add(new OfferEntry("m_drill_pick", drillPick, 1790, "Кирки", -1));

			list.add(new OfferEntry("m_cobble", new ItemStack(Items.COBBLESTONE, 32), 69, "Материалы", -1));
			list.add(new OfferEntry("m_coal", new ItemStack(Items.COAL, 16), 149, "Материалы", -1));
			list.add(new OfferEntry("m_copper", new ItemStack(Items.COPPER_INGOT, 8), 99, "Материалы", -1));
			list.add(new OfferEntry("m_iron", new ItemStack(Items.IRON_INGOT, 8), 239, "Материалы", -1));
			list.add(new OfferEntry("m_gold", new ItemStack(Items.GOLD_INGOT, 8), 419, "Материалы", -1));
			list.add(new OfferEntry("m_redstone", new ItemStack(Items.REDSTONE, 16), 129, "Материалы", -1));
			list.add(new OfferEntry("m_lapis", new ItemStack(Items.LAPIS_LAZULI, 16), 169, "Материалы", -1));
			list.add(new OfferEntry("m_diamond", new ItemStack(Items.DIAMOND, 1), 249, "Материалы", -1));
			list.add(new OfferEntry("m_torch", new ItemStack(Items.TORCH, 32), 49, "Материалы", -1));
			return list;
		}

		if ("elite_mfo".equals(shopKey) || "elite_mfo_1".equals(shopKey)) {
			// Торговец №1 в МФО (Элитный оружейник): эксклюзивное оружие, инструменты и броня с повышенным ценником
			ModPersistentState state = ModPersistentState.get(server);

			ItemStack vipSword = named(new ItemStack(Items.DIAMOND_SWORD), "Клинок Магната", Formatting.LIGHT_PURPLE);
			enchant(vipSword, enchRegistry, Enchantments.SHARPNESS, 4);
			enchant(vipSword, enchRegistry, Enchantments.UNBREAKING, 3);
			list.add(new OfferEntry("elite_sword", vipSword, 8290, "VIP ЛИМИТ", state.getEliteStock("elite_sword", 2)));

			ItemStack vipPick = named(new ItemStack(Items.DIAMOND_PICKAXE), "Кирка Олигарха", Formatting.LIGHT_PURPLE);
			enchant(vipPick, enchRegistry, Enchantments.EFFICIENCY, 4);
			enchant(vipPick, enchRegistry, Enchantments.FORTUNE, 3);
			list.add(new OfferEntry("elite_pickaxe", vipPick, 9490, "VIP ЛИМИТ", state.getEliteStock("elite_pickaxe", 2)));

			ItemStack vipChest = named(new ItemStack(Items.DIAMOND_CHESTPLATE), "Бриллиантовый китель", Formatting.LIGHT_PURPLE);
			enchant(vipChest, enchRegistry, Enchantments.PROTECTION, 4);
			enchant(vipChest, enchRegistry, Enchantments.UNBREAKING, 3);
			list.add(new OfferEntry("elite_chestplate", vipChest, 7190, "VIP ЛИМИТ", state.getEliteStock("elite_chestplate", 2)));

			ItemStack vipLegs = named(new ItemStack(Items.DIAMOND_LEGGINGS), "Брюки Банкира", Formatting.LIGHT_PURPLE);
			enchant(vipLegs, enchRegistry, Enchantments.PROTECTION, 4);
			enchant(vipLegs, enchRegistry, Enchantments.UNBREAKING, 3);
			list.add(new OfferEntry("elite_leggings", vipLegs, 6490, "VIP ЛИМИТ", state.getEliteStock("elite_leggings", 2)));

			ItemStack vipBow = named(new ItemStack(Items.BOW), "Лук Взыскателя", Formatting.GOLD);
			enchant(vipBow, enchRegistry, Enchantments.POWER, 4);
			enchant(vipBow, enchRegistry, Enchantments.INFINITY, 1);
			enchant(vipBow, enchRegistry, Enchantments.FLAME, 1);
			enchant(vipBow, enchRegistry, Enchantments.UNBREAKING, 3);
			list.add(new OfferEntry("elite_bow", vipBow, 8990, "VIP ЛИМИТ", state.getEliteStock("elite_bow", 1)));
			return list;
		}

		if ("elite_mfo_2".equals(shopKey)) {
			// Торговец №2 в МФО (Элитный антиквар): единственный в мире Карманный телепорт и редчайшие артефакты с повышенным ценником
			ModPersistentState state = ModPersistentState.get(server);

			list.add(new OfferEntry(
					"elite_teleporter",
					new ItemStack(ModItems.PLAYER_TELEPORTER),
					14990,
					"1 В МИРЕ",
					state.getEliteStock("elite_teleporter", 1)
			));
			list.add(new OfferEntry("elite_elytra", named(new ItemStack(Items.ELYTRA), "Люксовые Элитры", Formatting.GOLD), 11990, "VIP ЛИМИТ", state.getEliteStock("elite_elytra", 1)));
			list.add(new OfferEntry("elite_egapple", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 5790, "VIP ЛИМИТ", state.getEliteStock("elite_egapple", 3)));
			list.add(new OfferEntry("elite_netherite", new ItemStack(Items.NETHERITE_INGOT), 4990, "VIP ЛИМИТ", state.getEliteStock("elite_netherite", 2)));
			list.add(new OfferEntry("elite_totem", new ItemStack(Items.TOTEM_OF_UNDYING), 4390, "VIP ЛИМИТ", state.getEliteStock("elite_totem", 3)));
			return list;
		}

		if (shopKey.startsWith("bartender")) {
			String style = shopKey.contains(":") ? shopKey.substring(shopKey.indexOf(':') + 1) : "plains";
			// Все напитки/зелья на +25% дороже, с красивыми названиями и красивыми округлёнными ценами!
			list.add(new OfferEntry("bar_oblivion", new ItemStack(ModItems.DRUNKEN_OBLIVION), 1990, "Фирменный напиток", -1));

			switch (style) {
				case "cave" -> {
					list.add(new OfferEntry("bar_nv", namedPotion(Potions.LONG_NIGHT_VISION, "Эликсир «Кошачий взор бездны»", Formatting.AQUA), 119, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_swift", namedPotion(Potions.STRONG_SWIFTNESS, "Настойка «Ртутный шаг»", Formatting.AQUA), 139, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_leap", namedPotion(Potions.STRONG_LEAPING, "Коктейль «Прыжок сквозь своды»", Formatting.GREEN), 139, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_haste", customEffectPotion("Шот «Горняцкий кураж»", new StatusEffectInstance(StatusEffects.HASTE, 4800, 1)), 189, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_fire", namedPotion(Potions.LONG_FIRE_RESISTANCE, "Бальзам «Холодная магма»", Formatting.GOLD), 159, "Шахтный бар", -1));
				}
				case "desert" -> {
					list.add(new OfferEntry("bar_fire", namedPotion(Potions.LONG_FIRE_RESISTANCE, "Нектар «Дыхание барханов»", Formatting.GOLD), 129, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_swift", namedPotion(Potions.STRONG_SWIFTNESS, "Мираж «Ветер самума»", Formatting.YELLOW), 139, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_regen", namedPotion(Potions.STRONG_REGENERATION, "Роса «Сердце оазиса»", Formatting.LIGHT_PURPLE), 179, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_heal", namedPotion(Potions.STRONG_HEALING, "Глоток «Живая амброзия»", Formatting.RED), 119, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_nv", namedPotion(Potions.LONG_NIGHT_VISION, "Тоник «Звёздная пустыня»", Formatting.AQUA), 119, "Оазис-бар", -1));
				}
				case "snowy" -> {
					list.add(new OfferEntry("bar_str", namedPotion(Potions.STRONG_STRENGTH, "Грог «Ярость северного медведя»", Formatting.RED), 189, "Северный бар", -1));
					list.add(new OfferEntry("bar_slowfall", namedPotion(Potions.LONG_SLOW_FALLING, "Ликёр «Снежное перо»", Formatting.WHITE), 149, "Северный бар", -1));
					list.add(new OfferEntry("bar_regen", namedPotion(Potions.STRONG_REGENERATION, "Бальзам «Полярное сияние»", Formatting.LIGHT_PURPLE), 179, "Северный бар", -1));
					list.add(new OfferEntry("bar_leap", namedPotion(Potions.STRONG_LEAPING, "Настой «Горный барс»", Formatting.GREEN), 139, "Северный бар", -1));
					list.add(new OfferEntry("bar_swift", namedPotion(Potions.STRONG_SWIFTNESS, "Пунш «Ледяной вихрь»", Formatting.AQUA), 139, "Северный бар", -1));
				}
				case "jungle" -> {
					list.add(new OfferEntry("bar_leap", namedPotion(Potions.STRONG_LEAPING, "Коктейль «Лиановый акробат»", Formatting.GREEN), 129, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_water", namedPotion(Potions.LONG_WATER_BREATHING, "Эликсир «Глубокий риф»", Formatting.BLUE), 139, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_regen", namedPotion(Potions.STRONG_REGENERATION, "Нектар «Дикая орхидея»", Formatting.LIGHT_PURPLE), 179, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_swift", namedPotion(Potions.STRONG_SWIFTNESS, "Шот «Тень ягуара»", Formatting.YELLOW), 139, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_invis", namedPotion(Potions.LONG_INVISIBILITY, "Абсент «Призрак сельвы»", Formatting.GRAY), 189, "Тропик-бар", -1));
				}
				default -> {
					list.add(new OfferEntry("bar_luck", namedPotion(Potions.LUCK, "Шампанское «Фортуна крупье»", Formatting.GOLD), 169, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_swift", namedPotion(Potions.STRONG_SWIFTNESS, "Коктейль «Золотой галоп»", Formatting.AQUA), 139, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_str", namedPotion(Potions.STRONG_STRENGTH, "Виски «Стальной кулак»", Formatting.RED), 189, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_leap", namedPotion(Potions.STRONG_LEAPING, "Тоник «Небесный скачок»", Formatting.GREEN), 139, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_invis", namedPotion(Potions.LONG_INVISIBILITY, "Ликёр «Бархатная тень»", Formatting.GRAY), 189, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_water", namedPotion(Potions.LONG_WATER_BREATHING, "Бриз «Жемчужная лагуна»", Formatting.BLUE), 149, "Гранд-бар", -1));
				}
			}
			return list;
		}

		return list;
	}

	private static ItemStack namedPotion(RegistryEntry<Potion> potion, String customTitle, Formatting color) {
		ItemStack stack = PotionContentsComponent.createStack(Items.POTION, potion);
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(customTitle).formatted(color));
		return stack;
	}

	private static ItemStack customEffectPotion(String name, StatusEffectInstance effect) {
		ItemStack stack = new ItemStack(Items.POTION);
		stack.set(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT.with(effect));
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.GOLD));
		return stack;
	}

	private static ItemStack named(ItemStack stack, String title, Formatting color) {
		stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title).formatted(color));
		return stack;
	}

	private static void enchant(ItemStack stack, RegistryWrapper.Impl<Enchantment> registry, RegistryKey<Enchantment> key, int level) {
		registry.getOptional(key).ifPresent(entry -> stack.addEnchantment(entry, level));
	}
}
