package com.novaimod.registry;

import java.util.EnumMap;
import java.util.Map;

import com.novaimod.NovaiMod;
import com.novaimod.equipment.UltimateArmorItem;
import com.novaimod.item.AstralGateItem;
import com.novaimod.item.AuroraWeaverItem;
import com.novaimod.item.BureaucracyKeyItem;
import com.novaimod.item.BureaucracySealItem;
import com.novaimod.item.CatgirlSpawnEggItem;
import com.novaimod.item.CelestialWrathItem;
import com.novaimod.item.ChronoScepterItem;
import com.novaimod.item.CrusadeBannerItem;
import com.novaimod.item.CushionItem;
import com.novaimod.item.DrunkenOblivionItem;
import com.novaimod.item.GateOfBabylonItem;
import com.novaimod.item.HolyOrderSpawnEggItem;
import com.novaimod.item.HolyWaterItem;
import com.novaimod.item.InfinityEyeItem;
import com.novaimod.item.LuceRaincoatItem;
import com.novaimod.item.LuceRosaryItem;
import com.novaimod.item.LuceSpawnEggItem;
import com.novaimod.item.LuceStaffItem;
import com.novaimod.item.MirrorPrismItem;
import com.novaimod.item.OddsConfiguratorItem;
import com.novaimod.item.OrbitalLaserItem;
import com.novaimod.item.PartyDollItem;
import com.novaimod.item.PlagueCaneItem;
import com.novaimod.item.PlayerTeleporterItem;
import com.novaimod.item.PokeballItem;
import com.novaimod.item.RealityRiftBladeItem;
import com.novaimod.item.RevivalTokenItem;
import com.novaimod.item.ScaleRayItem;
import com.novaimod.item.SingularityOrbItem;
import com.novaimod.item.StrangeMeatItem;
import com.novaimod.item.SupernovaGenesisItem;
import com.novaimod.item.TerraHeaverItem;
import com.novaimod.item.TimeOfTheWorldItem;
import com.novaimod.item.UberGunItem;
import com.novaimod.item.UltimateSeatPadItem;
import com.novaimod.item.UnknownArtifactItem;
import com.novaimod.item.WhiteCapCompassItem;
import com.novaimod.item.WhiteCapFlaskItem;
import com.novaimod.item.WhiteCapHoodItem;
import com.novaimod.item.WhiteCapSpawnEggItem;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ArmorItem;
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

	public static final Item PARTY_DOLL = register(
			"party_doll",
			new PartyDollItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE))
	);

	public static final Item PLAYER_TELEPORTER = register(
			"player_teleporter",
			new PlayerTeleporterItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item UNKNOWN_ARTIFACT = register(
			"unknown_artifact",
			new UnknownArtifactItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item UBER_GUN = register(
			"uber_gun",
			new UberGunItem(new Item.Settings().maxDamage(750).rarity(Rarity.EPIC).fireproof())
	);

	// === КРЕАТИВНЫЕ АРТЕФАКТЫ (v3.4.0) ===
	public static final Item INFINITY_EYE = register(
			"infinity_eye",
			new InfinityEyeItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC).fireproof())
	);

	// Внутренний предмет-иконка для рендера летящих жемчугов Ока Бескрайности
	public static final Item INFINITY_PEARL = register(
			"infinity_pearl",
			new Item(new Item.Settings().maxCount(16).rarity(Rarity.RARE))
	);

	public static final Item SINGULARITY_ORB = register(
			"singularity_orb",
			new SingularityOrbItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item CELESTIAL_WRATH = register(
			"celestial_wrath",
			new CelestialWrathItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item CHRONO_SCEPTER = register(
			"chrono_scepter",
			new ChronoScepterItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	// === АЛЬТИМЕЙТ БРОНЯ И НОВЫЕ КРЕАТИВНЫЕ АРТЕФАКТЫ (v3.6.0) ===
	public static final Item ULTIMATE_HELMET = register(
			"ultimate_helmet",
			new UltimateArmorItem(ArmorItem.Type.HELMET, new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item ULTIMATE_CHESTPLATE = register(
			"ultimate_chestplate",
			new UltimateArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item ULTIMATE_LEGGINGS = register(
			"ultimate_leggings",
			new UltimateArmorItem(ArmorItem.Type.LEGGINGS, new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item ULTIMATE_BOOTS = register(
			"ultimate_boots",
			new UltimateArmorItem(ArmorItem.Type.BOOTS, new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item REALITY_RIFT_BLADE = register(
			"reality_rift_blade",
			new RealityRiftBladeItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item ORBITAL_LASER = register(
			"orbital_laser",
			new OrbitalLaserItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item TERRA_HEAVER = register(
			"terra_heaver",
			new TerraHeaverItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item ASTRAL_GATE = register(
			"astral_gate",
			new AstralGateItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	// === v3.7.0: ЕДИНЫЙ НЕВИДИМЫЙ «ПОДЖОПНИК» И «ПУШКА АБСУРДНОГО МАСШТАБА» ===
	public static final Item ULTIMATE_SEAT_PAD = register(
			"ultimate_seat_pad",
			new UltimateSeatPadItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item SCALE_RAY = register(
			"scale_ray",
			new ScaleRayItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	// === v3.8.0: ФРАКЦИЯ «КЛАН БЕЛЫХ КОЛПАКОВ» (ЧУМНЫЕ АЛХИМИКИ В ОГРОМНЫХ ГНОМЬИХ КОЛПАКАХ) ===
	public static final Item WHITE_CAP_HOOD = register(
			"white_cap_hood",
			new WhiteCapHoodItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item PLAGUE_CANE = register(
			"plague_cane",
			new PlagueCaneItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item WHITE_CAP_FLASK = register(
			"white_cap_flask",
			new WhiteCapFlaskItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE))
	);

	public static final Item WHITE_CAP_COMPASS = register(
			"white_cap_compass",
			new WhiteCapCompassItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE))
	);

	public static final Item WHITE_CAP_SPAWN_EGG = register(
			"white_cap_spawn_egg",
			new WhiteCapSpawnEggItem(false, new Item.Settings().maxCount(64).rarity(Rarity.RARE))
	);

	public static final Item WHITE_CAP_BOSS_SPAWN_EGG = register(
			"white_cap_boss_spawn_egg",
			new WhiteCapSpawnEggItem(true, new Item.Settings().maxCount(64).rarity(Rarity.EPIC))
	);

	// === v3.9.0: ПОКЕБОЛЫ (С ЖИЛЫМ МИНИ-ПРОСТРАНСТВОМ ДЛЯ ИГРОКОВ) И КОШКОДЕВОЧКИ ===
	public static final Item POKEBALL = register(
			"pokeball",
			new PokeballItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE).fireproof())
	);

	public static final Item CATGIRL_SPAWN_EGG = register(
			"catgirl_spawn_egg",
			new CatgirlSpawnEggItem(new Item.Settings().maxCount(64).rarity(Rarity.EPIC))
	);

	// === v4.0.0: ИЗМЕРЕНИЕ «БЮРОКРАТИЧЕСКИЙ АД» ===
	public static final Item BUREAUCRACY_KEY = register(
			"bureaucracy_key",
			new BureaucracyKeyItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item BUREAUCRACY_SEAL = register(
			"bureaucracy_seal",
			new BureaucracySealItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	// === v4.1.0: ПАЛОМНИЦА ЛЮЧЕ (СИЛА БОГА И АНИМЕ!) ===
	public static final Item LUCE_RAINCOAT = register(
			"luce_raincoat",
			new LuceRaincoatItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item LUCE_STAFF = register(
			"luce_staff",
			new LuceStaffItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item LUCE_ROSARY = register(
			"luce_rosary",
			new LuceRosaryItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item LUCE_SPAWN_EGG = register(
			"luce_spawn_egg",
			new LuceSpawnEggItem(new Item.Settings().maxCount(64).rarity(Rarity.EPIC).fireproof())
	);

	// === v4.2.0: ФРАКЦИЯ «ОРДЕН СВЕТА И ЦЕРКОВЬ СВЯТОЙ ЛЮЧЕ» (ВОЙНА ПРОТИВ КБК!) ===
	public static final Item CRUSADE_BANNER = register(
			"crusade_banner",
			new CrusadeBannerItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item HOLY_WATER = register(
			"holy_water",
			new HolyWaterItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE))
	);

	public static final Item HOLY_ORDER_SPAWN_EGG = register(
			"holy_order_spawn_egg",
			new HolyOrderSpawnEggItem(new Item.Settings().maxCount(64).rarity(Rarity.RARE))
	);

	// === v4.3.0: КОЛЛЕКЦИЯ КРЕАТИВНЫХ АРТЕФАКТОВ С ВАУ-ЭФФЕКТАМИ И «MADE IN HEAVEN: TIME OF THE WORLD» ===
	public static final Item TIME_OF_THE_WORLD = register(
			"time_of_the_world",
			new TimeOfTheWorldItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item SUPERNOVA_GENESIS = register(
			"supernova_genesis",
			new SupernovaGenesisItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item GATE_OF_BABYLON = register(
			"gate_of_babylon",
			new GateOfBabylonItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item AURORA_WEAVER = register(
			"aurora_weaver",
			new AuroraWeaverItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
	);

	public static final Item MIRROR_PRISM = register(
			"mirror_prism",
			new MirrorPrismItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC).fireproof())
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
						entries.add(PARTY_DOLL);
						entries.add(PLAYER_TELEPORTER);
						entries.add(UBER_GUN);
						entries.add(UNKNOWN_ARTIFACT);
						entries.add(INFINITY_EYE);
						entries.add(SINGULARITY_ORB);
						entries.add(CELESTIAL_WRATH);
						entries.add(CHRONO_SCEPTER);
						entries.add(REALITY_RIFT_BLADE);
						entries.add(ORBITAL_LASER);
						entries.add(TERRA_HEAVER);
						entries.add(ASTRAL_GATE);
						entries.add(SCALE_RAY);
						entries.add(ULTIMATE_SEAT_PAD);
						entries.add(WHITE_CAP_HOOD);
						entries.add(PLAGUE_CANE);
						entries.add(WHITE_CAP_FLASK);
						entries.add(WHITE_CAP_COMPASS);
						entries.add(POKEBALL);
						entries.add(BUREAUCRACY_KEY);
						entries.add(BUREAUCRACY_SEAL);
						entries.add(TIME_OF_THE_WORLD);
						entries.add(SUPERNOVA_GENESIS);
						entries.add(GATE_OF_BABYLON);
						entries.add(AURORA_WEAVER);
						entries.add(MIRROR_PRISM);
						entries.add(LUCE_RAINCOAT);
						entries.add(LUCE_STAFF);
						entries.add(LUCE_ROSARY);
						entries.add(CRUSADE_BANNER);
						entries.add(HOLY_WATER);
						entries.add(WHITE_CAP_SPAWN_EGG);
						entries.add(WHITE_CAP_BOSS_SPAWN_EGG);
						entries.add(CATGIRL_SPAWN_EGG);
						entries.add(LUCE_SPAWN_EGG);
						entries.add(HOLY_ORDER_SPAWN_EGG);
						entries.add(ODDS_CONFIGURATOR);
						for (DyeColor color : DyeColor.values()) {
							entries.add(CUSHIONS.get(color));
						}
					})
					.build()
	);

	public static final ItemGroup NOVAI_CREATIVE_GROUP = Registry.register(
			Registries.ITEM_GROUP,
			Identifier.of(NovaiMod.MOD_ID, "novai_creative_group"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(TIME_OF_THE_WORLD))
					.displayName(Text.translatable("itemGroup.novaimod.novai_creative_group"))
					.entries((displayContext, entries) -> {
						entries.add(TIME_OF_THE_WORLD);
						entries.add(SUPERNOVA_GENESIS);
						entries.add(GATE_OF_BABYLON);
						entries.add(AURORA_WEAVER);
						entries.add(MIRROR_PRISM);
						entries.add(ULTIMATE_SEAT_PAD);
						entries.add(SCALE_RAY);
						entries.add(WHITE_CAP_HOOD);
						entries.add(PLAGUE_CANE);
						entries.add(WHITE_CAP_COMPASS);
						entries.add(POKEBALL);
						entries.add(BUREAUCRACY_KEY);
						entries.add(BUREAUCRACY_SEAL);
						entries.add(LUCE_RAINCOAT);
						entries.add(LUCE_STAFF);
						entries.add(LUCE_ROSARY);
						entries.add(CRUSADE_BANNER);
						entries.add(HOLY_WATER);
						entries.add(WHITE_CAP_SPAWN_EGG);
						entries.add(WHITE_CAP_BOSS_SPAWN_EGG);
						entries.add(CATGIRL_SPAWN_EGG);
						entries.add(LUCE_SPAWN_EGG);
						entries.add(HOLY_ORDER_SPAWN_EGG);
						entries.add(REALITY_RIFT_BLADE);
						entries.add(ORBITAL_LASER);
						entries.add(TERRA_HEAVER);
						entries.add(ASTRAL_GATE);
						entries.add(INFINITY_EYE);
						entries.add(SINGULARITY_ORB);
						entries.add(CELESTIAL_WRATH);
						entries.add(CHRONO_SCEPTER);
						entries.add(UBER_GUN);
						entries.add(UNKNOWN_ARTIFACT);
						entries.add(ODDS_CONFIGURATOR);
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
