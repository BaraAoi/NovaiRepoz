package com.novaimod.economy;

import java.util.HashMap;
import java.util.Map;

import com.novaimod.registry.ModItems;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

/**
 * Рассчитывает цену ЛЮБОГО предмета в игре для торгового аппарата и изъятий коллекторами,
 * а также стоимость починки инструментов в шахтёрской лавке.
 */
public final class ItemPricingRegistry {
	private static final Map<Item, Long> EXPLICIT_PRICES = new HashMap<>();
	private static boolean initialized = false;

	private ItemPricingRegistry() {
	}

	private static void ensureInit() {
		if (initialized) {
			return;
		}
		initialized = true;

		// Базовые блоки и ресурсы
		put(Items.DIRT, 1);
		put(Items.COARSE_DIRT, 1);
		put(Items.COBBLESTONE, 2);
		put(Items.COBBLED_DEEPSLATE, 2);
		put(Items.STONE, 3);
		put(Items.DEEPSLATE, 3);
		put(Items.NETHERRACK, 1);
		put(Items.SAND, 2);
		put(Items.RED_SAND, 3);
		put(Items.GRAVEL, 2);
		put(Items.CLAY_BALL, 4);
		put(Items.BRICK, 6);
		put(Items.FLINT, 5);
		put(Items.OBSIDIAN, 35);
		put(Items.CRYING_OBSIDIAN, 55);
		put(ModItems.STRANGE_MEAT, 89);
		put(ModItems.PARTY_DOLL, 350);
		put(ModItems.PLAYER_TELEPORTER, 4500);
		put(ModItems.UBER_GUN, 3500);
		put(ModItems.UNKNOWN_ARTIFACT, 666);

		// Древесина
		put(Items.OAK_LOG, 6);
		put(Items.SPRUCE_LOG, 6);
		put(Items.BIRCH_LOG, 6);
		put(Items.JUNGLE_LOG, 6);
		put(Items.ACACIA_LOG, 6);
		put(Items.DARK_OAK_LOG, 6);
		put(Items.MANGROVE_LOG, 6);
		put(Items.CHERRY_LOG, 7);
		put(Items.CRIMSON_STEM, 8);
		put(Items.WARPED_STEM, 8);
		put(Items.STICK, 1);

		// Руды, слитки и минералы
		put(Items.COAL, 8);
		put(Items.CHARCOAL, 6);
		put(Items.RAW_COPPER, 8);
		put(Items.COPPER_INGOT, 10);
		put(Items.REDSTONE, 7);
		put(Items.LAPIS_LAZULI, 9);
		put(Items.QUARTZ, 12);
		put(Items.AMETHYST_SHARD, 16);
		put(Items.RAW_IRON, 18);
		put(Items.IRON_INGOT, 25);
		put(Items.IRON_NUGGET, 2);
		put(Items.RAW_GOLD, 32);
		put(Items.GOLD_INGOT, 45);
		put(Items.GOLD_NUGGET, 5);
		put(Items.EMERALD, 85);
		put(Items.DIAMOND, 180);
		put(Items.ANCIENT_DEBRIS, 320);
		put(Items.NETHERITE_SCRAP, 360);
		put(Items.NETHERITE_INGOT, 900);

		// Урожай и еда
		put(Items.WHEAT_SEEDS, 1);
		put(Items.WHEAT, 5);
		put(Items.CARROT, 5);
		put(Items.POTATO, 5);
		put(Items.BAKED_POTATO, 9);
		put(Items.BEETROOT, 4);
		put(Items.APPLE, 8);
		put(Items.MELON_SLICE, 3);
		put(Items.SWEET_BERRIES, 4);
		put(Items.GLOW_BERRIES, 8);
		put(Items.BREAD, 12);
		put(Items.COOKIE, 4);
		put(Items.PUMPKIN_PIE, 16);
		put(Items.CAKE, 45);
		put(Items.COOKED_BEEF, 15);
		put(Items.COOKED_PORKCHOP, 15);
		put(Items.COOKED_MUTTON, 13);
		put(Items.COOKED_CHICKEN, 12);
		put(Items.COOKED_COD, 11);
		put(Items.COOKED_SALMON, 13);
		put(Items.GOLDEN_CARROT, 55);
		put(Items.GOLDEN_APPLE, 240);
		put(Items.ENCHANTED_GOLDEN_APPLE, 1500);

		// Лут с мобов и редкие предметы
		put(Items.ROTTEN_FLESH, 2);
		put(Items.BONE, 5);
		put(Items.STRING, 5);
		put(Items.SPIDER_EYE, 6);
		put(Items.GUNPOWDER, 14);
		put(Items.FEATHER, 4);
		put(Items.LEATHER, 12);
		put(Items.SLIME_BALL, 22);
		put(Items.ENDER_PEARL, 45);
		put(Items.BLAZE_ROD, 50);
		put(Items.MAGMA_CREAM, 28);
		put(Items.GHAST_TEAR, 110);
		put(Items.PHANTOM_MEMBRANE, 40);
		put(Items.SHULKER_SHELL, 220);
		put(Items.TOTEM_OF_UNDYING, 750);
		put(Items.ELYTRA, 1800);
		put(Items.NETHER_STAR, 2500);
		put(Items.TRIDENT, 600);
		put(Items.MACE, 1200);
	}

	private static void put(Item item, long price) {
		EXPLICIT_PRICES.put(item, price);
	}

	/**
	 * Цена за 1 штуку конкретного стака (с учётом прочности и чар). Гарантированно >= 1 ₽.
	 */
	public static long getUnitPrice(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0L;
		}
		ensureInit();
		Item item = stack.getItem();
		long base;
		if (EXPLICIT_PRICES.containsKey(item)) {
			base = EXPLICIT_PRICES.get(item);
		} else {
			base = computeFallbackUnitPrice(stack, item);
		}

		// Учёт оставшейся прочности для инструментов/брони
		if (stack.isDamageable() && stack.getMaxDamage() > 0) {
			double ratio = Math.max(0.10D, 1.0D - ((double) stack.getDamage() / (double) stack.getMaxDamage()));
			base = Math.max(1L, Math.round(base * ratio));
		}

		// Надбавка за зачарования
		ItemEnchantmentsComponent enchants = stack.getEnchantments();
		if (enchants != null && !enchants.isEmpty()) {
			for (RegistryEntry<Enchantment> entry : enchants.getEnchantments()) {
				int lvl = enchants.getLevel(entry);
				base += 35L * Math.max(1, lvl);
			}
		}

		return Math.max(1L, base);
	}

	/**
	 * Общая цена всего стака.
	 */
	public static long getStackPrice(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0L;
		}
		return getUnitPrice(stack) * stack.getCount();
	}

	private static long computeFallbackUnitPrice(ItemStack stack, Item item) {
		Identifier id = Registries.ITEM.getId(item);
		String path = id.getPath();

		if (path.contains("netherite")) {
			return 650L;
		}
		if (path.contains("diamond")) {
			return 220L;
		}
		if (path.contains("emerald")) {
			return 90L;
		}
		if (path.contains("gold") || path.contains("golden")) {
			return 55L;
		}
		if (path.contains("iron") || path.contains("chainmail")) {
			return 40L;
		}
		if (path.contains("copper")) {
			return 14L;
		}

		long price = 3L;
		Rarity rarity = stack.getRarity();
		if (rarity == Rarity.EPIC) {
			price = 450L;
		} else if (rarity == Rarity.RARE) {
			price = 140L;
		} else if (rarity == Rarity.UNCOMMON) {
			price = 45L;
		}

		FoodComponent food = stack.get(DataComponentTypes.FOOD);
		if (food != null) {
			price = Math.max(price, 3L + food.nutrition() * 2L);
		}

		if (item instanceof ToolItem || item instanceof ArmorItem) {
			price = Math.max(price, 25L + stack.getMaxDamage() / 12L);
		} else if (stack.getMaxCount() == 1) {
			price = Math.max(price, 20L);
		} else if (item instanceof BlockItem) {
			if (path.contains("planks") || path.contains("slab") || path.contains("stairs")) {
				price = Math.max(price, 2L);
			} else if (path.contains("glass") || path.contains("wool") || path.contains("terracotta") || path.contains("concrete")) {
				price = Math.max(price, 4L);
			} else if (path.contains("lantern") || path.contains("glowstone")) {
				price = Math.max(price, 15L);
			}
		}

		if (stack.contains(DataComponentTypes.POTION_CONTENTS)) {
			price = Math.max(price, 28L);
		}

		return Math.max(1L, price);
	}

	/**
	 * Стоимость полной починки повреждённого инструмента/оружия/брони в шахтёрской лавке.
	 */
	public static long getRepairCost(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.isDamageable() || !stack.isDamaged()) {
			return 0L;
		}
		int damage = stack.getDamage();
		int maxDamage = Math.max(1, stack.getMaxDamage());
		double missingFraction = (double) damage / (double) maxDamage;

		String path = Registries.ITEM.getId(stack.getItem()).getPath();
		long fullRepairBase = 60L;
		if (path.contains("netherite")) {
			fullRepairBase = 690L;
		} else if (path.contains("diamond")) {
			fullRepairBase = 270L;
		} else if (path.contains("iron") || path.contains("chainmail")) {
			fullRepairBase = 115L;
		} else if (path.contains("gold") || path.contains("golden")) {
			fullRepairBase = 90L;
		} else if (path.contains("stone")) {
			fullRepairBase = 39L;
		}

		ItemEnchantmentsComponent enchants = stack.getEnchantments();
		if (enchants != null && !enchants.isEmpty()) {
			fullRepairBase += enchants.getSize() * 45L;
		}

		return Math.max(9L, Math.round(fullRepairBase * missingFraction));
	}
}
