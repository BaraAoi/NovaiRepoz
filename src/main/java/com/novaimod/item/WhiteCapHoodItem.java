package com.novaimod.item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.novaimod.NovaiMod;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.world.World;

/**
 * «Колпак Чумного Доктора» (Клан Белых Колпаков):
 * - Надевается на голову и рендерит полноценную 3D-маску чумного доктора с клювом,
 *   светящимися линзами и ОГРОМНЫМ белым гномьим колпаком прямо на игроке!
 * - Фильтр клюва очищает владельца от Иссушения, Яда, Слепоты, Тьмы и Тошноты.
 * - Бойцы Клана Белых Колпаков принимают игрока за своего брата и не агрятся в лагерях и Цитадели!
 */
public class WhiteCapHoodItem extends ArmorItem {
	public static final RegistryEntry<ArmorMaterial> WHITE_CAP_MATERIAL = registerMaterial();

	private static RegistryEntry<ArmorMaterial> registerMaterial() {
		Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.BOOTS, 3);
		defense.put(ArmorItem.Type.LEGGINGS, 6);
		defense.put(ArmorItem.Type.CHESTPLATE, 8);
		defense.put(ArmorItem.Type.HELMET, 5);
		defense.put(ArmorItem.Type.BODY, 6);

		ArmorMaterial mat = new ArmorMaterial(
				defense,
				22,
				SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
				() -> Ingredient.EMPTY,
				List.of(new ArmorMaterial.Layer(Identifier.of(NovaiMod.MOD_ID, "white_cap"))),
				3.0F,
				0.1F
		);

		return Registry.registerReference(
				Registries.ARMOR_MATERIAL,
				Identifier.of(NovaiMod.MOD_ID, "white_cap"),
				mat
		);
	}

	public WhiteCapHoodItem(Settings settings) {
		super(WHITE_CAP_MATERIAL, ArmorItem.Type.HELMET,
				settings.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClient() || !(entity instanceof LivingEntity living)) {
			return;
		}
		if (living.getEquippedStack(EquipmentSlot.HEAD) == stack && living.age % 10 == 0) {
			living.removeStatusEffect(StatusEffects.POISON);
			living.removeStatusEffect(StatusEffects.WITHER);
			living.removeStatusEffect(StatusEffects.NAUSEA);
			living.removeStatusEffect(StatusEffects.BLINDNESS);
			living.removeStatusEffect(StatusEffects.DARKNESS);
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Реликвия Клана Белых Колпаков").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("• 3D-клюв чумного доктора + ОГРОМНЫЙ белый гномий колпак!").formatted(Formatting.WHITE));
		tooltip.add(Text.literal("• Полный иммунитет к Иссушению, Яду, Слепоте и Тьме").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("• Клан Белых Колпаков считает вас своим братом в лагерях и Цитадели").formatted(Formatting.GOLD));
	}
}
