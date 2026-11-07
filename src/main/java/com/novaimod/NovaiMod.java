package com.novaimod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.novaimod.ai.evolution.MobEvolutionManager;
import com.novaimod.data.ModPersistentState;
import com.novaimod.economy.CasinoGamesManager;
import com.novaimod.entity.CushionEntity;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.entity.WhiteCapEntity;
import com.novaimod.equipment.MfoArmorSetManager;
import com.novaimod.equipment.UltimateArmorManager;
import com.novaimod.item.CushionItem;
import com.novaimod.item.ScaleRayItem;
import com.novaimod.network.ModPayloads;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModBlocks;
import com.novaimod.registry.ModEffects;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.registry.ModScreenHandlers;
import com.novaimod.registry.ModSounds;
import com.novaimod.world.ApocalypseEventManager;
import com.novaimod.world.DetentionManager;
import com.novaimod.world.EgoPartyManager;
import com.novaimod.world.ModStructureGenerator;
import com.novaimod.world.MysterySkyManager;
import com.novaimod.world.TimeStopManager;
import com.novaimod.world.WhiteCapWorldManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class NovaiMod implements ModInitializer {
	public static final String MOD_ID = "novaimod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private int structureCheckTicker = 0;

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModEffects.register();
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
		// Клик ПКМ по ступеньке/блоку, на котором уже лежит Подушка, плавно сажает игрока на эту подушку;
		// а при открытии ПЕРВОГО натурального сундука в данже помещает внутрь Неизвестный предмет (§k)!
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != Hand.MAIN_HAND || player.isSpectator()) {
				return ActionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			if (!world.isClient() && !player.shouldCancelInteraction() && world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
				ApocalypseEventManager.checkFirstDungeonChestOpen(sw, pos, sp);
			}
			if (player.shouldCancelInteraction()) {
				return ActionResult.PASS;
			}
			if (player.getStackInHand(hand).getItem() instanceof CushionItem) {
				return ActionResult.PASS;
			}
			var cushions = world.getEntitiesByClass(
					CushionEntity.class,
					new Box(pos).expand(0.25D, 0.75D, 0.25D),
					c -> !c.isRemoved() && pos.equals(c.getSupportPos())
			);
			if (!cushions.isEmpty()) {
				if (!world.isClient()) {
					cushions.get(0).sitPlayer(player);
				}
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			ModServerNetworking.syncAccount(player);
			TimeStopManager.onPlayerJoin(player);
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

		// 1) Единый невидимый «Поджопник»: поглощает 100% любого урона в игре КРОМЕ процентного урона (и никак не виден на игроке!)
		// 2) Пока рядом активна женская Танцующая кукла — и мобы, и игроки становятся дружелюбными друг к другу!
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity sp && UltimateArmorManager.isWearingFullUltimateArmor(sp)) {
				if (!UltimateArmorManager.isPercentageDamageHit(source)) {
					return false;
				}
			}
			if (source.getAttacker() instanceof LivingEntity attacker) {
				if (PartyDollEntity.isUnderActiveDollAura(entity) || PartyDollEntity.isUnderActiveDollAura(attacker)) {
					if (entity.getWorld() instanceof ServerWorld sw) {
						sw.spawnParticles(ParticleTypes.HEART, entity.getX(), entity.getBodyY(0.7D), entity.getZ(), 2, 0.25D, 0.2D, 0.25D, 0.01D);
					}
					return false;
				}
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			if (entity instanceof ServerPlayerEntity deadPlayer) {
				MobEvolutionManager.recordPlayerDeath(deadPlayer, damageSource);
				ApocalypseEventManager.onPlayerDeath(deadPlayer);
			} else {
				MobEvolutionManager.onMobDeath(entity);
			}
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient() && DetentionManager.isPlayerRestrictedFromBreaking(player)) {
				if (player instanceof ServerPlayerEntity sp) {
					sp.sendMessage(Text.literal("Здесь нельзя ломать блоки!").formatted(Formatting.RED), true);
				}
				return false;
			}
			if (world instanceof ServerWorld sw && !player.isCreative() && !player.isSpectator()
					&& !WhiteCapEntity.isWearingClanHood(player)) {
				var guards = sw.getEntitiesByClass(WhiteCapEntity.class, new net.minecraft.util.math.Box(pos).expand(14.0D), LivingEntity::isAlive);
				if (!guards.isEmpty()) {
					guards.get(0).markPlayerEnemy(player, 600);
					guards.get(0).alertNearbyClanAgainst(player);
					if (player instanceof ServerPlayerEntity sp) {
						sp.sendMessage(Text.literal("[Белый Колпак] Эй, мародёр! Не смей ломать лагерь Белых Колпаков!!").formatted(Formatting.RED, Formatting.BOLD), true);
					}
				}
			}
			return true;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			MysterySkyManager.tickSkyArenas(server);
			DetentionManager.tickDebtAndDetentions(server);
			CasinoGamesManager.tickPendingResolutions(server);
			MobEvolutionManager.tickServer(server);
			ApocalypseEventManager.tickServer(server);
			TimeStopManager.tickServer(server);
			EgoPartyManager.tickRaves(server);
			UltimateArmorManager.tickPlayers(server);
			ScaleRayItem.tickTelekinesis(server);

			if (structureCheckTicker % 5 == 0) {
				MfoArmorSetManager.tickPlayers(server);
			}

			structureCheckTicker++;
			if (structureCheckTicker >= 40) {
				structureCheckTicker = 0;
				ModStructureGenerator.tickWorldGeneration(server);
				WhiteCapWorldManager.tickClanWorld(server);
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
											case "ego_island" -> ModStructureGenerator.buildEgoIsland(world, pos);
											case "casino_cave" -> ModStructureGenerator.buildBiomeCasino(world, pos, "cave");
											case "casino_desert" -> ModStructureGenerator.buildBiomeCasino(world, pos, "desert");
											case "casino_snowy" -> ModStructureGenerator.buildBiomeCasino(world, pos, "snowy");
											case "casino_jungle" -> ModStructureGenerator.buildBiomeCasino(world, pos, "jungle");
											case "citadel", "white_cap_citadel" -> {
												WhiteCapWorldManager.buildWhiteCapCitadel(world, pos);
												ModPersistentState.get(world.getServer()).recordStructure("white_cap_citadel", world.getRegistryKey().getValue().toString(), pos);
											}
											case "outpost", "white_cap_outpost" -> {
												WhiteCapWorldManager.buildFortifiedOutpost(world, pos);
												ModPersistentState.get(world.getServer()).recordStructure("white_cap_outpost", world.getRegistryKey().getValue().toString(), pos);
											}
											case "camp", "white_cap_camp" -> {
												WhiteCapWorldManager.buildFieldCamp(world, pos);
												ModPersistentState.get(world.getServer()).recordStructure("white_cap_camp", world.getRegistryKey().getValue().toString(), pos);
											}
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
							.then(CommandManager.literal("apocalypse").executes(ctx -> {
								ServerPlayerEntity p = ctx.getSource().getPlayer();
								ApocalypseEventManager.startApocalypse(ctx.getSource().getServer(), p);
								return 1;
							}))
			);
		});
	}
}
