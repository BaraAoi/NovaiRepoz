package com.novaimod.registry;

import com.novaimod.NovaiMod;
import com.novaimod.screen.VendingScreenHandler;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModScreenHandlers {
	public static final ScreenHandlerType<VendingScreenHandler> VENDING_SCREEN_HANDLER = Registry.register(
			Registries.SCREEN_HANDLER,
			Identifier.of(NovaiMod.MOD_ID, "vending_machine"),
			new ScreenHandlerType<>(VendingScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
	);

	private ModScreenHandlers() {
	}

	public static void init() {
	}
}
