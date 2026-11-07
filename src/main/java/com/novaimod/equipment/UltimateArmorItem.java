package com.novaimod.equipment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.novaimod.NovaiMod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Креативный сет «Альтимейт Броня» (4/4):
 * - Неразрушимая божественная броня.
 * - Поглощает 100% абсолютно любого урона (взрывы, лава, пустота, боссы, стрелы, магия), КРОМЕ процентного урона!
 * - Даёт полноценный креативный полёт даже в режиме Выживания!
 */
public class UltimateArmorItem extends ArmorItem {
	public static final RegistryEntry<ArmorMaterial> ULTIMATE_MATERIAL = registerUltimateMaterial();

	private static RegistryEntry<ArmorMaterial> registerUltimateMaterial() {
		Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.BOOTS, 8);
		defense.put(ArmorItem.Type.LEGGINGS, 12);
		defense.put(ArmorItem.Type.CHESTPLATE, 16);
		defense.put(ArmorItem.Type.HELMET, 8);
		defense.put(ArmorItem.Type.BODY, 16);

		ArmorMaterial mat = new ArmorMaterial(
				defense,
				30,
				SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
				() -> Ingredient.EMPTY,
				List.of(new ArmorMaterial.Layer(Identifier.of(NovaiMod.MOD_ID, "ultimate"))),
				20.0F,
				1.0F
		);

		return Registry.registerReference(
				Registries.ARMOR_MATERIAL,
				Identifier.of(NovaiMod.MOD_ID, "ultimate"),
				mat
		);
	}

	public UltimateArmorItem(ArmorItem.Type type, Settings settings) {
		super(ULTIMATE_MATERIAL, type, settings.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Креативный сет: Альтимейт Броня (4/4)").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("• Поглощает 100% ВСЕГО урона в игре").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("  (кроме эффекта «Процентный урон»!)").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("• Даёт свободный полёт в любом режиме!").formatted(Formatting.GREEN));
	}
}
