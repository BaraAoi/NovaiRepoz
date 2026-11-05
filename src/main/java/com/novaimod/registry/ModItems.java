package com.novaimod.registry;

import com.novaimod.NovaiMod;
import com.novaimod.item.DrunkenOblivionItem;
import com.novaimod.item.RevivalTokenItem;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
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

	public static final Item REVIVAL_TOKEN = register(
			"revival_token",
			new RevivalTokenItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item DRUNKEN_OBLIVION = register(
			"drunken_oblivion",
			new DrunkenOblivionItem(new Item.Settings().maxCount(4).rarity(Rarity.RARE))
	);

	public static final ItemGroup NOVAI_ITEM_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			Identifier.of(NovaiMod.MOD_ID, "novai_group"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(REVIVAL_TOKEN))
					.displayName(Text.translatable("itemGroup.novaimod.novai_group"))
					.entries((displayContext, entries) -> {
						entries.add(TRADE_MACHINE_ITEM);
						entries.add(REPAIR_STATION_ITEM);
						entries.add(FATE_MACHINE_ITEM);
						entries.add(REVIVAL_TOKEN);
						entries.add(DRUNKEN_OBLIVION);
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
