package com.novaimod.registry;

import java.util.EnumMap;
import java.util.Map;

import com.novaimod.NovaiMod;
import com.novaimod.item.CushionItem;
import com.novaimod.item.DrunkenOblivionItem;
import com.novaimod.item.OddsConfiguratorItem;
import com.novaimod.item.RevivalTokenItem;
import com.novaimod.item.StrangeMeatItem;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public final class ModItems {
	public static final Item TRADE_MACHINE_ITEM = register(
			"trade_machine",
			new BlockItem(ModBlocks.TRADE_MACHINE, new Item.Settings())
	);

	public static final Item FATE_MACHINE_ITEM = register(
			"fate_machine",
			new BlockItem(ModBlocks.FATE_MACHINE, new Item.Settings().rarity(Rarity.EPIC))
	);

	public static final Item REPAIR_STATION_ITEM = register(
			"repair_station",
			new BlockItem(ModBlocks.REPAIR_STATION, new Item.Settings())
	);

	public static final Item COINFLIP_TABLE_ITEM = register(
			"coinflip_table",
			new BlockItem(ModBlocks.COINFLIP_TABLE, new Item.Settings().rarity(Rarity.UNCOMMON))
	);

	public static final Item ROULETTE_TABLE_ITEM = register(
			"roulette_table",
			new BlockItem(ModBlocks.ROULETTE_TABLE, new Item.Settings().rarity(Rarity.UNCOMMON))
	);

	// Сохраняется в реестре только для совместимости старых чанков
	public static final Item JACUZZI_SEAT_ITEM = register(
			"jacuzzi_seat",
			new BlockItem(ModBlocks.JACUZZI_SEAT, new Item.Settings())
	);

	public static final Item DJ_CONSOLE_ITEM = register(
			"dj_console",
			new BlockItem(ModBlocks.DJ_CONSOLE, new Item.Settings().rarity(Rarity.RARE))
	);

	public static final Item REVIVAL_TOKEN = register(
			"revival_token",
			new RevivalTokenItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item DRUNKEN_OBLIVION = register(
			"drunken_oblivion",
			new DrunkenOblivionItem(new Item.Settings().maxCount(4).rarity(Rarity.RARE))
	);

	public static final Item ODDS_CONFIGURATOR = register(
			"odds_configurator",
			new OddsConfiguratorItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC))
	);

	public static final Item STRANGE_MEAT = register(
			"strange_meat",
			new StrangeMeatItem(new Item.Settings().maxCount(64).rarity(Rarity.UNCOMMON))
	);

	public static final Map<DyeColor, Item> CUSHIONS = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values()) {
			CUSHIONS.put(
					color,
					register(color.asString() + "_cushion", new CushionItem(color, new Item.Settings().maxCount(16)))
			);
		}
	}

	public static Item getCushionItem(DyeColor color) {
		return CUSHIONS.getOrDefault(color, CUSHIONS.get(DyeColor.WHITE));
	}

	public static final ItemGroup NOVAI_ITEM_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			Identifier.of(NovaiMod.MOD_ID, "novai_group"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(REVIVAL_TOKEN))
					.displayName(Text.translatable("itemGroup.novaimod.novai_group"))
					.entries((displayContext, entries) -> {
						entries.add(TRADE_MACHINE_ITEM);
						entries.add(REPAIR_STATION_ITEM);
						entries.add(COINFLIP_TABLE_ITEM);
						entries.add(ROULETTE_TABLE_ITEM);
						entries.add(DJ_CONSOLE_ITEM);
						entries.add(FATE_MACHINE_ITEM);
						entries.add(REVIVAL_TOKEN);
						entries.add(DRUNKEN_OBLIVION);
						entries.add(STRANGE_MEAT);
						entries.add(ODDS_CONFIGURATOR);
						for (DyeColor color : DyeColor.values()) {
							entries.add(CUSHIONS.get(color));
						}
					})
					.build()
	);

	private ModItems() {
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, Identifier.of(NovaiMod.MOD_ID, name), item);
	}

	public static void init() {
	}
}
