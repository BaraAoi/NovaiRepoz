package com.novaimod.client;

import com.novaimod.client.hud.AccountHudOverlay;
import com.novaimod.client.render.ModNpcRenderer;
import com.novaimod.client.screen.AccountShopScreen;
import com.novaimod.client.screen.LoanScreen;
import com.novaimod.client.screen.RevivalSelectScreen;
import com.novaimod.client.screen.VendingScreen;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModScreenHandlers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class NovaiModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Регистрация модели и рендерера NPC (без рендера брони и оружия в руках)
		EntityModelLayerRegistry.registerModelLayer(
				ModNpcRenderer.NPC_MODEL_LAYER,
				ModNpcRenderer::getTexturedModelData
		);
		EntityRendererRegistry.register(ModEntities.MOD_NPC, ModNpcRenderer::new);

		// Экран Торгового аппарата
		HandledScreens.register(ModScreenHandlers.VENDING_SCREEN_HANDLER, VendingScreen::new);

		// Отрисовка Счёта и Долга сверху слева
		HudRenderCallback.EVENT.register(new AccountHudOverlay());

		// S2C пакеты
		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.AccountSyncPayload.ID, (payload, context) -> {
			ClientAccountData.update(payload.balance(), payload.debt(), payload.sizoSecondsLeft());
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenLoanScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new LoanScreen(payload.balance(), payload.debt()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenShopScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new AccountShopScreen(
					payload.title(),
					payload.shopKey(),
					payload.allowRepair(),
					payload.limitedElite(),
					payload.offers()
			));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenRevivalScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new RevivalSelectScreen(payload.candidates()));
		});
	}
}
