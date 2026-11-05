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
 * - Продуктовый (2 продавца, у каждого Обычный и Элитный раздел)
 * - Шахтёрская лавка (кирки, материалы без незерита + ремонт)
 * - Элитные продавцы в МФО (втридорога + строгий остаток по штукам)
 * - Бармен казино (зелья под конкретный биом/шахту + коктейль «Пьяное забвение»)
 */
public final class ShopCatalogs {
	public record OfferEntry(
			String offerId,
			ItemStack stack,
			long price,
			String tierLabel,
			int stock // -1 = безлимит, >=0 = ограниченный запас
	) {
	}

	private ShopCatalogs() {
	}

	public static List<OfferEntry> buildCatalog(MinecraftServer server, String shopKey) {
		List<OfferEntry> list = new ArrayList<>();
		RegistryWrapper.Impl<Enchantment> enchRegistry = server.getRegistryManager().getWrapperOrThrow(RegistryKeys.ENCHANTMENT);

		if ("grocery_1".equals(shopKey)) {
			// Продавец №1: Обычные и Элитные продукты (пекарня и ферма)
			list.add(new OfferEntry("g1_bread", new ItemStack(Items.BREAD, 2), 18, "Обычный", -1));
			list.add(new OfferEntry("g1_potato", new ItemStack(Items.BAKED_POTATO, 3), 21, "Обычный", -1));
			list.add(new OfferEntry("g1_apple", new ItemStack(Items.APPLE, 4), 24, "Обычный", -1));
			list.add(new OfferEntry("g1_carrot", new ItemStack(Items.CARROT, 4), 18, "Обычный", -1));
			list.add(new OfferEntry("g1_cookie", new ItemStack(Items.COOKIE, 6), 20, "Обычный", -1));
			list.add(new OfferEntry("g1_pie", new ItemStack(Items.PUMPKIN_PIE, 2), 30, "Обычный", -1));

			list.add(new OfferEntry("g1_gcarrot", new ItemStack(Items.GOLDEN_CARROT, 2), 140, "Элитный", -1));
			list.add(new OfferEntry("g1_gapple", new ItemStack(Items.GOLDEN_APPLE, 1), 390, "Элитный", -1));
			list.add(new OfferEntry("g1_cake", new ItemStack(Items.CAKE, 1), 110, "Элитный", -1));
			list.add(new OfferEntry("g1_glow", named(new ItemStack(Items.GLOW_BERRIES, 8), "Отборные светящиеся ягоды", Formatting.GOLD), 95, "Элитный", -1));
			return list;
		}

		if ("grocery_2".equals(shopKey)) {
			// Продавец №2: Обычные и Элитные мясные деликатесы
			list.add(new OfferEntry("g2_beef", new ItemStack(Items.COOKED_BEEF, 2), 36, "Обычный", -1));
			list.add(new OfferEntry("g2_pork", new ItemStack(Items.COOKED_PORKCHOP, 2), 36, "Обычный", -1));
			list.add(new OfferEntry("g2_chicken", new ItemStack(Items.COOKED_CHICKEN, 3), 32, "Обычный", -1));
			list.add(new OfferEntry("g2_mutton", new ItemStack(Items.COOKED_MUTTON, 3), 34, "Обычный", -1));
			list.add(new OfferEntry("g2_salmon", new ItemStack(Items.COOKED_SALMON, 3), 34, "Обычный", -1));
			list.add(new OfferEntry("g2_stew", new ItemStack(Items.MUSHROOM_STEW, 1), 26, "Обычный", -1));

			list.add(new OfferEntry("g2_steak_vip", named(new ItemStack(Items.COOKED_BEEF, 8), "Мраморный рибай шефа", Formatting.GOLD), 190, "Элитный", -1));
			list.add(new OfferEntry("g2_rabbit", named(new ItemStack(Items.RABBIT_STEW, 1), "Царское жаркое", Formatting.GOLD), 120, "Элитный", -1));
			list.add(new OfferEntry("g2_honey", named(new ItemStack(Items.HONEY_BOTTLE, 3), "Горный элитный мёд", Formatting.GOLD), 135, "Элитный", -1));
			list.add(new OfferEntry("g2_egapple", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1), 2400, "Элитный", -1));
			return list;
		}

		if ("miner".equals(shopKey)) {
			// Шахтёрская лавка: кирки и материалы (железо, уголь, алмазы, медь, булыжник, золото и т.д. — БЕЗ незерита!)
			list.add(new OfferEntry("m_wood_pick", new ItemStack(Items.WOODEN_PICKAXE), 12, "Кирки", -1));
			list.add(new OfferEntry("m_stone_pick", new ItemStack(Items.STONE_PICKAXE), 28, "Кирки", -1));
			list.add(new OfferEntry("m_iron_pick", new ItemStack(Items.IRON_PICKAXE), 115, "Кирки", -1));
			list.add(new OfferEntry("m_gold_pick", new ItemStack(Items.GOLDEN_PICKAXE), 135, "Кирки", -1));
			list.add(new OfferEntry("m_dia_pick", new ItemStack(Items.DIAMOND_PICKAXE), 640, "Кирки", -1));

			ItemStack drillPick = named(new ItemStack(Items.DIAMOND_PICKAXE), "Кирка бригадира шахты", Formatting.AQUA);
			enchant(drillPick, enchRegistry, Enchantments.EFFICIENCY, 3);
			enchant(drillPick, enchRegistry, Enchantments.UNBREAKING, 2);
			list.add(new OfferEntry("m_drill_pick", drillPick, 1150, "Кирки", -1));

			list.add(new OfferEntry("m_cobble", new ItemStack(Items.COBBLESTONE, 32), 64, "Материалы", -1));
			list.add(new OfferEntry("m_coal", new ItemStack(Items.COAL, 16), 145, "Материалы", -1));
			list.add(new OfferEntry("m_copper", new ItemStack(Items.COPPER_INGOT, 8), 95, "Материалы", -1));
			list.add(new OfferEntry("m_iron", new ItemStack(Items.IRON_INGOT, 8), 235, "Материалы", -1));
			list.add(new OfferEntry("m_gold", new ItemStack(Items.GOLD_INGOT, 8), 410, "Материалы", -1));
			list.add(new OfferEntry("m_redstone", new ItemStack(Items.REDSTONE, 16), 130, "Материалы", -1));
			list.add(new OfferEntry("m_lapis", new ItemStack(Items.LAPIS_LAZULI, 16), 165, "Материалы", -1));
			list.add(new OfferEntry("m_diamond", new ItemStack(Items.DIAMOND, 1), 250, "Материалы", -1));
			list.add(new OfferEntry("m_torch", new ItemStack(Items.TORCH, 32), 45, "Материалы", -1));
			return list;
		}

		if ("elite_mfo".equals(shopKey)) {
			// Элитные продавцы прямо в МФО: втридорога + строго ограниченный остаток!
			ModPersistentState state = ModPersistentState.get(server);

			ItemStack vipSword = named(new ItemStack(Items.DIAMOND_SWORD), "Клинок Магната", Formatting.LIGHT_PURPLE);
			enchant(vipSword, enchRegistry, Enchantments.SHARPNESS, 4);
			enchant(vipSword, enchRegistry, Enchantments.UNBREAKING, 3);
			list.add(new OfferEntry("elite_sword", vipSword, 4500, "VIP ЛИМИТ", state.getEliteStock("elite_sword", 2)));

			ItemStack vipPick = named(new ItemStack(Items.DIAMOND_PICKAXE), "Кирка Олигарха", Formatting.LIGHT_PURPLE);
			enchant(vipPick, enchRegistry, Enchantments.EFFICIENCY, 4);
			enchant(vipPick, enchRegistry, Enchantments.FORTUNE, 3);
			list.add(new OfferEntry("elite_pickaxe", vipPick, 5200, "VIP ЛИМИТ", state.getEliteStock("elite_pickaxe", 2)));

			ItemStack vipChest = named(new ItemStack(Items.DIAMOND_CHESTPLATE), "Бриллиантовый китель", Formatting.LIGHT_PURPLE);
			enchant(vipChest, enchRegistry, Enchantments.PROTECTION, 4);
			enchant(vipChest, enchRegistry, Enchantments.UNBREAKING, 3);
			list.add(new OfferEntry("elite_chestplate", vipChest, 5800, "VIP ЛИМИТ", state.getEliteStock("elite_chestplate", 2)));

			list.add(new OfferEntry("elite_elytra", named(new ItemStack(Items.ELYTRA), "Люксовые Элитры", Formatting.GOLD), 9500, "VIP ЛИМИТ", state.getEliteStock("elite_elytra", 1)));
			list.add(new OfferEntry("elite_totem", new ItemStack(Items.TOTEM_OF_UNDYING), 3600, "VIP ЛИМИТ", state.getEliteStock("elite_totem", 3)));
			list.add(new OfferEntry("elite_egapple", new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), 4800, "VIP ЛИМИТ", state.getEliteStock("elite_egapple", 3)));
			list.add(new OfferEntry("elite_netherite", new ItemStack(Items.NETHERITE_INGOT), 4200, "VIP ЛИМИТ", state.getEliteStock("elite_netherite", 2)));
			return list;
		}

		if (shopKey.startsWith("bartender")) {
			String style = shopKey.contains(":") ? shopKey.substring(shopKey.indexOf(':') + 1) : "plains";
			// Коктейль «Пьяное забвение» есть у каждого бармена казино
			list.add(new OfferEntry("bar_oblivion", new ItemStack(ModItems.DRUNKEN_OBLIVION), 350, "Особый коктейль", -1));

			switch (style) {
				case "cave" -> {
					list.add(new OfferEntry("bar_nv", potion(Potions.LONG_NIGHT_VISION), 90, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_swift", potion(Potions.STRONG_SWIFTNESS), 110, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_leap", potion(Potions.STRONG_LEAPING), 110, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_haste", customEffectPotion("Шот «Горняцкий драйв»", new StatusEffectInstance(StatusEffects.HASTE, 4800, 1)), 150, "Шахтный бар", -1));
					list.add(new OfferEntry("bar_fire", potion(Potions.LONG_FIRE_RESISTANCE), 125, "Шахтный бар", -1));
				}
				case "desert" -> {
					list.add(new OfferEntry("bar_fire", potion(Potions.LONG_FIRE_RESISTANCE), 100, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_swift", potion(Potions.STRONG_SWIFTNESS), 110, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_regen", potion(Potions.STRONG_REGENERATION), 140, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_heal", potion(Potions.STRONG_HEALING), 95, "Оазис-бар", -1));
					list.add(new OfferEntry("bar_nv", potion(Potions.LONG_NIGHT_VISION), 95, "Оазис-бар", -1));
				}
				case "snowy" -> {
					list.add(new OfferEntry("bar_str", potion(Potions.STRONG_STRENGTH), 145, "Северный бар", -1));
					list.add(new OfferEntry("bar_slowfall", potion(Potions.LONG_SLOW_FALLING), 115, "Северный бар", -1));
					list.add(new OfferEntry("bar_regen", potion(Potions.STRONG_REGENERATION), 140, "Северный бар", -1));
					list.add(new OfferEntry("bar_leap", potion(Potions.STRONG_LEAPING), 110, "Северный бар", -1));
					list.add(new OfferEntry("bar_swift", potion(Potions.STRONG_SWIFTNESS), 110, "Северный бар", -1));
				}
				case "jungle" -> {
					list.add(new OfferEntry("bar_leap", potion(Potions.STRONG_LEAPING), 105, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_water", potion(Potions.LONG_WATER_BREATHING), 110, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_regen", potion(Potions.STRONG_REGENERATION), 140, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_swift", potion(Potions.STRONG_SWIFTNESS), 110, "Тропик-бар", -1));
					list.add(new OfferEntry("bar_invis", potion(Potions.LONG_INVISIBILITY), 150, "Тропик-бар", -1));
				}
				default -> {
					list.add(new OfferEntry("bar_luck", potion(Potions.LUCK), 130, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_swift", potion(Potions.STRONG_SWIFTNESS), 110, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_str", potion(Potions.STRONG_STRENGTH), 145, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_leap", potion(Potions.STRONG_LEAPING), 110, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_invis", potion(Potions.LONG_INVISIBILITY), 150, "Гранд-бар", -1));
					list.add(new OfferEntry("bar_water", potion(Potions.LONG_WATER_BREATHING), 115, "Гранд-бар", -1));
				}
			}
			return list;
		}

		return list;
	}

	private static ItemStack potion(RegistryEntry<Potion> potion) {
		return PotionContentsComponent.createStack(Items.POTION, potion);
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
