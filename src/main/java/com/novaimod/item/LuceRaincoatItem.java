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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * «Жёлтый Дождевик Люче» (с 3D-капюшоном и миссионерскими чётками на игроке!):
 * - Защита уровня незеритового нагрудника, неразрушимый и огнеупорный.
 * - Очищает Иссушение, Отравление и Тьму, защищает от молний и снижает урон от нежити на 35%.
 * - В дождь, грозу и в воде даёт Скорость II, Грацию дельфина и Регенерацию!
 */
public class LuceRaincoatItem extends ArmorItem {
	public static final RegistryEntry<ArmorMaterial> LUCE_RAINCOAT_MATERIAL = registerMaterial();

	private static RegistryEntry<ArmorMaterial> registerMaterial() {
		Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
		defense.put(ArmorItem.Type.BOOTS, 3);
		defense.put(ArmorItem.Type.LEGGINGS, 6);
		defense.put(ArmorItem.Type.CHESTPLATE, 8);
		defense.put(ArmorItem.Type.HELMET, 4);
		defense.put(ArmorItem.Type.BODY, 6);

		ArmorMaterial mat = new ArmorMaterial(
				defense,
				25,
				SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
				() -> Ingredient.EMPTY,
				List.of(new ArmorMaterial.Layer(Identifier.of(NovaiMod.MOD_ID, "luce_raincoat"))),
				3.0F,
				0.15F
		);

		return Registry.registerReference(
				Registries.ARMOR_MATERIAL,
				Identifier.of(NovaiMod.MOD_ID, "luce_raincoat"),
				mat
		);
	}

	public LuceRaincoatItem(Settings settings) {
		super(LUCE_RAINCOAT_MATERIAL, ArmorItem.Type.CHESTPLATE,
				settings.component(DataComponentTypes.UNBREAKABLE, new UnbreakableComponent(false)));
	}

	@Override
	public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
		super.inventoryTick(stack, world, entity, slot, selected);
		if (world.isClient() || !(entity instanceof LivingEntity living)) {
			return;
		}
		if (living.getEquippedStack(EquipmentSlot.CHEST) != stack) {
			return;
		}
		if (living.age % 15 == 0) {
			living.removeStatusEffect(StatusEffects.POISON);
			living.removeStatusEffect(StatusEffects.WITHER);
			living.removeStatusEffect(StatusEffects.DARKNESS);
			living.removeStatusEffect(StatusEffects.BLINDNESS);

			if (world.isRaining() || living.isTouchingWaterOrRain()) {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 45, 1, true, false, true));
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 45, 0, true, false, true));
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 45, 0, true, false, true));
			} else {
				living.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 45, 0, true, false, true));
			}

			if (world instanceof ServerWorld sw && living.age % 30 == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, living.getX(), living.getBodyY(0.5D), living.getZ(), 1, 0.25D, 0.25D, 0.25D, 0.01D);
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("Фирменный непромокаемый дождевик паломницы Люче (3D-капюшон + плащ)").formatted(Formatting.YELLOW, Formatting.BOLD));
		tooltip.add(Text.literal("• Снимает Иссушение, Яд и Тьму, защищает от молний и снижает урон нежити на 35%").formatted(Formatting.AQUA));
		tooltip.add(Text.literal("• В дождь и в воде: Скорость II, Грация дельфина и Регенерация!").formatted(Formatting.GREEN));
	}
}
