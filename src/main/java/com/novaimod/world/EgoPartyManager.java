package com.novaimod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.novaimod.data.ModPersistentState;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;
import com.novaimod.network.ModServerNetworking;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Управляет интерактивной вечеринкой на танцполе Эго-острова:
 * - Переливает разноцветное стекло танцпола под бит музыки.
 * - Заставляет гостей вечеринки пританцовывать в такт.
 * - Награждает игроков, танцующих (прыгающих) на танцполе под музыку, драйвом и денежным призом за лучший танец!
 */
public final class EgoPartyManager {
	private static final Block[] DANCEFLOOR_GLASS = new Block[]{
			Blocks.MAGENTA_STAINED_GLASS,
			Blocks.CYAN_STAINED_GLASS,
			Blocks.YELLOW_STAINED_GLASS,
			Blocks.LIME_STAINED_GLASS,
			Blocks.PINK_STAINED_GLASS,
			Blocks.PURPLE_STAINED_GLASS,
			Blocks.ORANGE_STAINED_GLASS,
			Blocks.LIGHT_BLUE_STAINED_GLASS
	};

	private static final float[] BASS_MELODY = new float[]{
			0.75F, 1.0F, 1.18F, 1.0F, 1.33F, 1.18F, 1.5F, 1.18F
	};

	private static class ActiveRave {
		final ServerWorld world;
		final BlockPos djPos;
		int ticksLeft;
		int beatStep;

		ActiveRave(ServerWorld world, BlockPos djPos, int ticksLeft) {
			this.world = world;
			this.djPos = djPos;
			this.ticksLeft = ticksLeft;
			this.beatStep = 0;
		}
	}

	private static final List<ActiveRave> ACTIVE_RAVES = new ArrayList<>();
	private static final Map<UUID, Long> LAST_DANCE_PRIZE_TICK = new HashMap<>();

	private EgoPartyManager() {
	}

	public static void startDancefloorRave(ServerWorld world, BlockPos djPos, ServerPlayerEntity starter) {
		for (ActiveRave r : ACTIVE_RAVES) {
			if (r.world == world && r.djPos.getSquaredDistance(djPos) <= 64.0D) {
				r.ticksLeft = 600; // Продлеваем на 30 секунд
				starter.sendMessage(
						Text.literal("♪ Диджей поддал басов! Танцпол гудит ещё громче!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
						true
				);
				return;
			}
		}

		ACTIVE_RAVES.add(new ActiveRave(world, djPos, 600));
		world.playSound(null, djPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.RECORDS, 0.9F, 1.25F);
		for (ServerPlayerEntity p : world.getPlayers()) {
			if (p.squaredDistanceTo(djPos.getX() + 0.5D, djPos.getY(), djPos.getZ() + 0.5D) <= 45.0D * 45.0D) {
				p.sendMessage(
						Text.literal("♫ ВЕЧЕРИНКА НА ЭГО-ОСТРОВЕ! Прыгай на танцполе, чтобы сорвать куш за лучший танец!")
								.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
						false
				);
			}
		}
	}

	public static void tickRaves(MinecraftServer server) {
		if (ACTIVE_RAVES.isEmpty()) {
			return;
		}
		long now = server.getOverworld().getTime();
		Iterator<ActiveRave> it = ACTIVE_RAVES.iterator();
		while (it.hasNext()) {
			ActiveRave rave = it.next();
			rave.ticksLeft--;
			if (rave.ticksLeft <= 0) {
				it.remove();
				continue;
			}

			// Каждые 8 тиков — удар бита, смена цветов танцпола и танец гостей!
			if (rave.ticksLeft % 8 == 0) {
				rave.beatStep++;
				pulseDancefloorTiles(rave.world, rave.djPos, rave.beatStep);

				float pitch = BASS_MELODY[rave.beatStep % BASS_MELODY.length];
				rave.world.playSound(null, rave.djPos, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.RECORDS, 1.1F, pitch);
				rave.world.playSound(null, rave.djPos, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), SoundCategory.RECORDS, 1.0F, 0.9F);

				// Конфетти и ноты над танцполом
				rave.world.spawnParticles(
						ParticleTypes.NOTE,
						rave.djPos.getX() + 0.5D,
						rave.djPos.getY() + 2.4D,
						rave.djPos.getZ() + 4.5D,
						8,
						3.0D,
						0.6D,
						3.0D,
						0.4D
				);
				rave.world.spawnParticles(
						ParticleTypes.END_ROD,
						rave.djPos.getX() + 0.5D,
						rave.djPos.getY() + 2.8D,
						rave.djPos.getZ() + 4.5D,
						6,
						2.8D,
						0.5D,
						2.8D,
						0.03D
				);

				// Тусовщицы и гости острова подпрыгивают в ритм!
				Box danceZone = new Box(rave.djPos).expand(14.0D, 5.0D, 14.0D);
				for (ModNpcEntity npc : rave.world.getEntitiesByClass(ModNpcEntity.class, danceZone, e -> true)) {
					NpcRole role = npc.getNpcRole();
					if (role == NpcRole.PARTY_GIRL || role == NpcRole.SWEETIE_FIK) {
						if (npc.isOnGround()) {
							npc.addVelocity(0.0D, 0.28D, 0.0D);
						}
					}
				}

				// Игроки на танцполе получают «Вайб вечеринки» + премию 300 ₽ за зажигательный танец!
				Box floorBox = new Box(
						rave.djPos.getX() - 4, rave.djPos.getY() - 1, rave.djPos.getZ() + 1,
						rave.djPos.getX() + 5, rave.djPos.getY() + 3, rave.djPos.getZ() + 9
				);
				for (ServerPlayerEntity dancer : rave.world.getEntitiesByClass(ServerPlayerEntity.class, floorBox, p -> p.isAlive() && !p.isSpectator())) {
					dancer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1, true, false, true));
					dancer.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, 1, true, false, true));

					// Если игрок подпрыгивает в такт на танцполе — раз в 2 минуты получает денежный приз от гостей вечеринки!
					if (!dancer.isOnGround()) {
						long lastPrize = LAST_DANCE_PRIZE_TICK.getOrDefault(dancer.getUuid(), -5000L);
						if (now - lastPrize >= 2400L) {
							LAST_DANCE_PRIZE_TICK.put(dancer.getUuid(), now);
							ModPersistentState state = ModPersistentState.get(server);
							ModPersistentState.PlayerFinanceData acc = state.getAccount(dancer.getUuid());
							acc.balance += 300L;
							state.markDirty();
							ModServerNetworking.syncAccount(dancer);
							dancer.sendMessage(
									Text.literal("★ Гости Эго-острова в восторге от вашего танца! Чаевые: +300 ₽!")
											.formatted(Formatting.GOLD, Formatting.BOLD),
									false
							);
						}
					}
				}
			}
		}
	}

	private static void pulseDancefloorTiles(ServerWorld world, BlockPos djPos, int step) {
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				m.set(djPos.getX() + dx, djPos.getY() - 1, djPos.getZ() + dz);
				BlockState cur = world.getBlockState(m);
				if (isStainedGlass(cur.getBlock())) {
					int idx = Math.floorMod(dx + dz + step, DANCEFLOOR_GLASS.length);
					world.setBlockState(m, DANCEFLOOR_GLASS[idx].getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				}
			}
		}
	}

	private static boolean isStainedGlass(Block block) {
		for (Block g : DANCEFLOOR_GLASS) {
			if (block == g) {
				return true;
			}
		}
		return false;
	}
}
