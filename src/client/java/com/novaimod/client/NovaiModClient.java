package com.novaimod.client;

import org.lwjgl.glfw.GLFW;

import com.novaimod.client.hud.AccountHudOverlay;
import com.novaimod.client.render.CushionEntityRenderer;
import com.novaimod.client.render.ModNpcRenderer;
import com.novaimod.client.render.PartyDollEntityRenderer;
import com.novaimod.client.screen.AccountShopScreen;
import com.novaimod.client.screen.CoinflipScreen;
import com.novaimod.client.screen.LoanScreen;
import com.novaimod.client.screen.OddsConfigScreen;
import com.novaimod.client.screen.RevivalSelectScreen;
import com.novaimod.client.screen.RouletteScreen;
import com.novaimod.client.screen.TeleporterScreen;
import com.novaimod.client.screen.VendingScreen;
import com.novaimod.item.UberGunItem;
import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.registry.ModScreenHandlers;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class NovaiModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Регистрация модели и рендерера NPC (без рендера брони и оружия в руках)
		EntityModelLayerRegistry.registerModelLayer(
				ModNpcRenderer.NPC_MODEL_LAYER,
				ModNpcRenderer::getTexturedModelData
		);
		EntityModelLayerRegistry.registerModelLayer(
				CushionEntityRenderer.CUSHION_MODEL_LAYER,
				CushionEntityRenderer::getTexturedModelData
		);
		EntityModelLayerRegistry.registerModelLayer(
				PartyDollEntityRenderer.DOLL_MODEL_LAYER,
				PartyDollEntityRenderer::getTexturedModelData
		);
		EntityRendererRegistry.register(ModEntities.MOD_NPC, ModNpcRenderer::new);
		EntityRendererRegistry.register(ModEntities.SEAT_ENTITY, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.CUSHION_ENTITY, CushionEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.PARTY_DOLL_ENTITY, PartyDollEntityRenderer::new);

		// Экран Торгового аппарата
		HandledScreens.register(ModScreenHandlers.VENDING_SCREEN_HANDLER, VendingScreen::new);

		// Отрисовка виртуального Счёта в верхнем углу экрана
		HudRenderCallback.EVENT.register(new AccountHudOverlay());

		// Анимация натяга УБЕРГАНА (с учётом всех 3 режимов: Пулемёт, Снайперский 45 тиков, Аннигиляция 20 тиков)
		ModelPredicateProviderRegistry.register(ModItems.UBER_GUN, Identifier.ofVanilla("pull"), (stack, world, entity, seed) -> {
			if (entity == null || entity.getActiveItem() != stack) {
				return 0.0F;
			}
			int used = stack.getMaxUseTime(entity) - entity.getItemUseTimeLeft();
			int mode = UberGunItem.getMode(stack);
			if (mode == UberGunItem.MODE_MACHINE_GUN) {
				return (used % 4) / 3.0F;
			}
			if (mode == UberGunItem.MODE_SNIPER) {
				return MathHelper.clamp((float) used / (float) UberGunItem.SNIPER_FULL_DRAW_TICKS, 0.0F, 1.0F);
			}
			return MathHelper.clamp((float) used / 20.0F, 0.0F, 1.0F);
		});
		ModelPredicateProviderRegistry.register(
				ModItems.UBER_GUN,
				Identifier.ofVanilla("pulling"),
				(stack, world, entity, seed) -> entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
		);

		// Клавиша смены режима стрельбы Убергана (по умолчанию R)
		KeyBinding cycleUberModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.novaimod.uber_gun_mode",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				KeyBinding.GAMEPLAY_CATEGORY
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (cycleUberModeKey.wasPressed()) {
				if (client.player != null && (client.player.getMainHandStack().isOf(ModItems.UBER_GUN)
						|| client.player.getOffHandStack().isOf(ModItems.UBER_GUN))) {
					ClientPlayNetworking.send(new ModPayloads.CycleUberGunModePayload());
				}
			}
		});

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

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenTeleporterScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new TeleporterScreen(payload.balance(), payload.targets()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenCoinflipScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new CoinflipScreen(payload.balance()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.CoinflipResultPayload.ID, (payload, context) -> {
			if (context.client().currentScreen instanceof CoinflipScreen coinflipScreen) {
				coinflipScreen.onSpinResult(payload.landedHeads(), payload.won(), payload.payout(), payload.animTicks());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenRouletteScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new RouletteScreen(payload.balance()));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.RouletteResultPayload.ID, (payload, context) -> {
			if (context.client().currentScreen instanceof RouletteScreen rouletteScreen) {
				rouletteScreen.onSpinResult(payload.winningNumber(), payload.won(), payload.payout(), payload.animTicks());
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.OpenOddsConfigScreenPayload.ID, (payload, context) -> {
			context.client().setScreen(new OddsConfigScreen(
					payload.fateTokenChance(),
					payload.fateNothingChance(),
					payload.fateDeathChance(),
					payload.coinHeadsChance(),
					payload.rouletteGreenChance(),
					payload.rouletteRedChance(),
					payload.rouletteBlackChance()
			));
		});

		ClientPlayNetworking.registerGlobalReceiver(ModPayloads.ShadowCuePayload.ID, (payload, context) -> {
			ClientAccountData.triggerShadowCue(payload.leftSide(), payload.intensity());
		});
	}
}
