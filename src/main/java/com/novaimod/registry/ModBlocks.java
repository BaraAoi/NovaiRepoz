package com.novaimod.registry;

import com.novaimod.NovaiMod;
import com.novaimod.block.CoinflipTableBlock;
import com.novaimod.block.DjConsoleBlock;
import com.novaimod.block.FateMachineBlock;
import com.novaimod.block.JacuzziSeatBlock;
import com.novaimod.block.RepairStationBlock;
import com.novaimod.block.RouletteTableBlock;
import com.novaimod.block.TradeMachineBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Block TRADE_MACHINE = register(
			"trade_machine",
			new TradeMachineBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.IRON_GRAY)
							.strength(3.5F, 12.0F)
							.sounds(BlockSoundGroup.METAL)
							.luminance(state -> 7)
			)
	);

	public static final Block FATE_MACHINE = register(
			"fate_machine",
			new FateMachineBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.GOLD)
							.strength(-1.0F, 3600000.0F)
							.dropsNothing()
							.sounds(BlockSoundGroup.AMETHYST_BLOCK)
							.luminance(state -> 14)
			)
	);

	public static final Block REPAIR_STATION = register(
			"repair_station",
			new RepairStationBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.DEEPSLATE_GRAY)
							.strength(4.0F, 12.0F)
							.sounds(BlockSoundGroup.ANVIL)
			)
	);

	public static final Block COINFLIP_TABLE = register(
			"coinflip_table",
			new CoinflipTableBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.GOLD)
							.strength(3.0F, 10.0F)
							.sounds(BlockSoundGroup.WOOD)
							.luminance(state -> 5)
			)
	);

	public static final Block ROULETTE_TABLE = register(
			"roulette_table",
			new RouletteTableBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.EMERALD_GREEN)
							.strength(3.0F, 10.0F)
							.sounds(BlockSoundGroup.WOOD)
							.luminance(state -> 6)
			)
	);

	public static final Block JACUZZI_SEAT = register(
			"jacuzzi_seat",
			new JacuzziSeatBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.OFF_WHITE)
							.strength(2.0F, 8.0F)
							.sounds(BlockSoundGroup.STONE)
							.nonOpaque()
							.luminance(state -> 6)
			)
	);

	public static final Block DJ_CONSOLE = register(
			"dj_console",
			new DjConsoleBlock(
					AbstractBlock.Settings.create()
							.mapColor(MapColor.MAGENTA)
							.strength(3.0F, 10.0F)
							.sounds(BlockSoundGroup.METAL)
							.luminance(state -> 12)
			)
	);

	private ModBlocks() {
	}

	private static Block register(String name, Block block) {
		return Registry.register(Registries.BLOCK, Identifier.of(NovaiMod.MOD_ID, name), block);
	}

	public static void init() {
	}
}
