package com.novaimod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.novaimod.data.ModPersistentState;
import com.novaimod.network.ModPayloads;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModBlocks;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.registry.ModScreenHandlers;
import com.novaimod.world.DetentionManager;
import com.novaimod.world.ModStructureGenerator;
import com.novaimod.world.MysterySkyManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class NovaiMod implements ModInitializer {
	public static final String MOD_ID = "novaimod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int structureCheckTicker = 0;

	@Override
	public void onInitialize() {
		ModBlocks.init();
		ModItems.init();
		ModScreenHandlers.init();
		ModEntities.init();

		ModPayloads.registerPayloadTypes();
		ModServerNetworking.registerServerReceivers();

		registerEvents();
		registerCommands();

		LOGGER.info("Novai Mod initialized for Minecraft 1.21.1!");
	}

	private void registerEvents() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ModServerNetworking.syncAccount(player);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ModPersistentState state = ModPersistentState.get(server);
			ModPersistentState.SkyArenaSession removed = state.removeSkyArena(player.getUuid());
			if (removed != null) {
				ServerWorld overworld = server.getOverworld();
				MysterySkyManager.eraseSkyPlatform(overworld, removed);
				ServerWorld returnWorld = MysterySkyManager.resolveWorld(server, removed.returnDim);
				player.teleport(returnWorld, removed.returnX, removed.returnY, removed.returnZ, removed.returnYaw, removed.returnPitch);
			}
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ModServerNetworking.syncAccount(newPlayer);
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient() && DetentionManager.isPlayerRestrictedFromBreaking(player)) {
				if (player instanceof ServerPlayerEntity sp) {
					sp.sendMessage(Text.literal("Здесь нельзя ломать блоки!").formatted(Formatting.RED), true);
				}
				return false;
			}
			return true;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MysterySkyManager.tickSkyArenas(server);
			DetentionManager.tickDebtAndDetentions(server);

			structureCheckTicker++;
			if (structureCheckTicker >= 40) {
				structureCheckTicker = 0;
				ModStructureGenerator.tickWorldGeneration(server);
			}
		});
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(
					CommandManager.literal("novai")
							.requires(src -> src.hasPermissionLevel(2))
							.then(CommandManager.literal("locate").executes(ctx -> {
								ModPersistentState state = ModPersistentState.get(ctx.getSource().getServer());
								if (state.getGeneratedStructures().isEmpty()) {
									ctx.getSource().sendFeedback(() -> Text.literal("Структуры ещё генерируются по мере исследования мира.").formatted(Formatting.YELLOW), false);
									return 1;
								}
								ctx.getSource().sendFeedback(() -> Text.literal("=== Сгенерированные заведения ===").formatted(Formatting.GOLD, Formatting.BOLD), false);
								int start = Math.max(0, state.getGeneratedStructures().size() - 12);
								for (int i = start; i < state.getGeneratedStructures().size(); i++) {
									ModPersistentState.StructureEntry e = state.getGeneratedStructures().get(i);
									ctx.getSource().sendFeedback(
											() -> Text.literal("• " + e.type + " [" + e.dimension + "] -> X=" + e.pos.getX() + " Y=" + e.pos.getY() + " Z=" + e.pos.getZ())
													.formatted(Formatting.GREEN),
											false
									);
								}
								return 1;
							}))
							.then(CommandManager.literal("spawn")
									.then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
										ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
										ServerWorld world = player.getServerWorld();
										BlockPos pos = player.getBlockPos().add(10, 0, 10);
										String type = StringArgumentType.getString(ctx, "type");
										switch (type) {
											case "mfo" -> ModStructureGenerator.buildMfoOffice(world, pos);
											case "grocery" -> ModStructureGenerator.buildGroceryStore(world, pos);
											case "miner" -> ModStructureGenerator.buildMinerShop(world, pos);
											case "resort" -> ModStructureGenerator.buildLavaResort(world, pos);
											case "casino_cave" -> ModStructureGenerator.buildBiomeCasino(world, pos, "cave");
											case "casino_desert" -> ModStructureGenerator.buildBiomeCasino(world, pos, "desert");
											case "casino_snowy" -> ModStructureGenerator.buildBiomeCasino(world, pos, "snowy");
											case "casino_jungle" -> ModStructureGenerator.buildBiomeCasino(world, pos, "jungle");
											default -> ModStructureGenerator.buildBiomeCasino(world, pos, "plains");
										}
										ctx.getSource().sendFeedback(() -> Text.literal("Построено заведение: " + type + " на " + pos.toShortString()).formatted(Formatting.GREEN), true);
										return 1;
									})))
							.then(CommandManager.literal("balance")
									.then(CommandManager.argument("amount", LongArgumentType.longArg(0)).executes(ctx -> {
										ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
										long amt = LongArgumentType.getLong(ctx, "amount");
										ModPersistentState state = ModPersistentState.get(player.getServer());
										state.getAccount(player.getUuid()).balance = amt;
										state.markDirty();
										ModServerNetworking.syncAccount(player);
										ctx.getSource().sendFeedback(() -> Text.literal("Баланс установлен: " + amt + " ₽").formatted(Formatting.GREEN), false);
										return 1;
									})))
			);
		});
	}
}
