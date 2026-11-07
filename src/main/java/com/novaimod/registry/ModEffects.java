package com.novaimod.registry;

import com.novaimod.NovaiMod;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/**
 * Реестр кастомных эффектов мода:
 * - Процентный урон (percent_erosion): эксклюзивный для 10-минутного испытания эффект (и редкий 10% на макс. уровне у милишников),
 *   наносящий от 1 до 4 ХП (5%..20%) сквозь любую броню и любые зачарования.
 */
public final class ModEffects {
	public static final RegistryEntry<StatusEffect> PERCENT_EROSION = Registry.registerReference(
			Registries.STATUS_EFFECT,
			Identifier.of(NovaiMod.MOD_ID, "percent_erosion"),
			new PercentErosionStatusEffect()
	);

	private ModEffects() {
	}

	public static void register() {
		// Инициализация статических полей реестра эффектов
	}

	private static final class PercentErosionStatusEffect extends StatusEffect {
		protected PercentErosionStatusEffect() {
			super(StatusEffectCategory.HARMFUL, 0xD62240);
		}
	}
}
