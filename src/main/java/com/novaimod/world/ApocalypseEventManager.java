package com.novaimod.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.ai.evolution.MobEvolutionManager;
import com.novaimod.data.ModPersistentState;
import com.novaimod.network.ModServerNetworking;
import com.novaimod.registry.ModItems;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;

/**
 * Управляет проклятым событием из ПЕРВОГО открытого сгенерированного сундука в данже:
 * 1) Кладёт в первый открытый не-скрафченный сундук с лут-таблицей Неизвестный предмет (§k).
 * 2) Запускает на 10 минут (12000 тиков) максимальное усложнение всех мобов:
 *    - Эволюция мобов 100% (1.0F) + баффы скорости, силы и живучести.
 *    - Резко увеличенный спавнрейт мобов вокруг игроков днём и ночью.
 *    - Мобы перестают гореть на солнце.
 *    - Криперы копают тоннель прямо к застроившимся игрокам и взрывают обсидиан!
 * 3) Если игроки выжили 10 минут — выдаёт каждому по +30 000 ₽ и по 1 Жетону возрождения,
 *    а также делит между выжившими 64 железа, 32 алмаза и 6 незерита!
 */
public final class ApocalypseEventManager {
	public static final int APOCALYPSE_DURATION_TICKS = 20 * 60 * 10; // 10 минут = 12000 тиков

	private static ServerBossBar bossBar = null;

	private ApocalypseEventManager() {
	}

	public static boolean isActive(MinecraftServer server) {
		if (server == null) {
			return false;
		}
		return ModPersistentState.get(server).isApocalypseActive();
	}

	public static boolean isActiveForWorld(World world) {
		if (world instanceof ServerWorld sw) {
			return isActive(sw.getServer());
		}
		return false;
	}

	/**
	 * Проверяет ПКМ по блоку: если это самый первый сгенерированный миром данж-сундук (имеет LootTable, т.е. не скрафчен игроком),
	 * генерирует его ванильный лут и помещает внутрь Неизвестный предмет с меняющимися символами!
	 */
	public static void checkFirstDungeonChestOpen(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
		MinecraftServer server = world.getServer();
		ModPersistentState state = ModPersistentState.get(server);
		if (state.isFirstDungeonChestOpened()) {
			return;
		}

		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof LootableContainerBlockEntity lootContainer)) {
			return;
		}
		// У любого скрафченного и поставленного игроком сундука getLootTable() == null!
		// Ненулевой getLootTable() бывает ТОЛЬКО у натурально сгенерированных сундуков в данжах/структурах!
		if (lootContainer.getLootTable() == null) {
			return;
		}

		// Сначала генерируем стандартный лут данжа, чтобы ванилла не перезаписала слоты при открытии меню
		lootContainer.generateLoot(player);

		ItemStack artifact = new ItemStack(ModItems.UNKNOWN_ARTIFACT, 1);
		int size = lootContainer.size();
		int centerSlot = Math.min(13, Math.max(0, size / 2));
		if (lootContainer.getStack(centerSlot).isEmpty()) {
			lootContainer.setStack(centerSlot, artifact);
		} else {
			// Освобождаем центральный слот для наглядности, перекладывая старый предмет в свободный слот
			ItemStack displaced = lootContainer.getStack(centerSlot);
			boolean moved = false;
			for (int i = 0; i < size; i++) {
				if (lootContainer.getStack(i).isEmpty()) {
					lootContainer.setStack(i, displaced);
					moved = true;
					break;
				}
			}
			if (moved) {
				lootContainer.setStack(centerSlot, artifact);
			} else {
				player.dropItem(artifact, false);
			}
		}
		lootContainer.markDirty();

		state.setFirstDungeonChestOpened(true);
		// Если игрок не заберёт предмет из сундука сам — через 5 секунд (100 тиков) артефакт пробудится сам!
		state.setPendingArtifactAwakenTicks(100);

		world.playSound(null, pos, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, 1.2F, 0.75F);
		world.spawnParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 25, 0.4D, 0.4D, 0.4D, 0.05D);
	}

	public static void startApocalypse(MinecraftServer server, @Nullable ServerPlayerEntity triggerPlayer) {
		ModPersistentState state = ModPersistentState.get(server);
		if (state.isApocalypseActive()) {
			return;
		}

		state.setPendingArtifactAwakenTicks(0);
		state.setApocalypseActive(true);
		state.setApocalypseTicksLeft(APOCALYPSE_DURATION_TICKS);
		state.getDiedDuringApocalypse().clear();
		state.markDirty();

		ensureBossBar(server, APOCALYPSE_DURATION_TICKS);

		Text alert = Text.literal("✦ ")
				.formatted(Formatting.DARK_PURPLE)
				.append(Text.literal("ПРОКЛЯТИЕ").formatted(Formatting.LIGHT_PURPLE, Formatting.OBFUSCATED))
				.append(Text.literal(" НЕИЗВЕСТНЫЙ АРТЕФАКТ ПРОБУДИЛСЯ! ").formatted(Formatting.RED, Formatting.BOLD))
				.append(Text.literal("ПРОКЛЯТИЕ").formatted(Formatting.LIGHT_PURPLE, Formatting.OBFUSCATED));
		Text subAlert = Text.literal("Все мобы усилены на МАКСИМУМ, не горят на солнце, а криперы роют путь и взрывают обсидиан! Продержитесь 10 минут ради огромной награды!")
				.formatted(Formatting.GOLD, Formatting.BOLD);

		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			p.sendMessage(alert, false);
			p.sendMessage(subAlert, false);
			p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0F, 0.65F);
			p.getServerWorld().spawnParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0D, p.getZ(), 70, 0.8D, 1.0D, 0.8D, 0.3D);
		}
	}

	public static void onPlayerDeath(ServerPlayerEntity deadPlayer) {
		MinecraftServer server = deadPlayer.getServer();
		if (server == null) {
			return;
		}
		ModPersistentState state = ModPersistentState.get(server);
		if (!state.isApocalypseActive()) {
			return;
		}
		state.getDiedDuringApocalypse().add(deadPlayer.getUuid());
		state.markDirty();
		deadPlayer.sendMessage(
				Text.literal("[Проклятие]: Вы пали во время испытания 10 минут и выбываете из дележа награды выживших!")
						.formatted(Formatting.RED, Formatting.BOLD),
				false
		);
	}

	public static void tickServer(MinecraftServer server) {
		ModPersistentState state = ModPersistentState.get(server);

		// Авто-пробуждение артефакта через 5 секунд после открытия первого данж-сундука, если его оставили лежать внутри
		int pending = state.getPendingArtifactAwakenTicks();
		if (pending > 0 && !state.isApocalypseActive()) {
			pending--;
			state.setPendingArtifactAwakenTicks(pending);
			if (pending == 0) {
				startApocalypse(server, null);
			}
		}

		if (!state.isApocalypseActive()) {
			if (bossBar != null) {
				bossBar.clearPlayers();
				bossBar.setVisible(false);
				bossBar = null;
			}
			return;
		}

		int left = state.getApocalypseTicksLeft() - 1;
		state.setApocalypseTicksLeft(left);
		if (left % 100 == 0) {
			state.markDirty();
		}

		ensureBossBar(server, Math.max(0, left));

		// Каждые 20 тиков (1 сек) баффаем всех враждебных мобов рядом с игроками
		if (left % 20 == 0) {
			buffNearbyHostileMobs(server);
		}

		// Каждые 35 тиков (~1.75 сек) спавним дополнительные волны мобов вокруг выживающих игроков
		if (left % 35 == 10) {
			spawnApocalypseMobWaves(server);
		}

		if (left <= 0) {
			finishApocalypse(server, state);
		}
	}

	private static void ensureBossBar(MinecraftServer server, int ticksLeft) {
		int totalSec = (ticksLeft + 19) / 20;
		int mm = totalSec / 60;
		int ss = totalSec % 60;
		String timerStr = String.format("%02d:%02d", mm, ss);

		Text title = Text.literal("§5§kXXX§r ")
				.append(Text.literal("ПРОКЛЯТИЕ МОБОВ — ВЫЖИВИТЕ: " + timerStr).formatted(Formatting.RED, Formatting.BOLD))
				.append(Text.literal(" §5§kXXX"));

		if (bossBar == null) {
			bossBar = new ServerBossBar(title, BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
			bossBar.setVisible(true);
		} else {
			bossBar.setName(title);
		}
		bossBar.setPercent(MathHelper.clamp((float) ticksLeft / (float) APOCALYPSE_DURATION_TICKS, 0.0F, 1.0F));

		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (!bossBar.getPlayers().contains(p)) {
				bossBar.addPlayer(p);
			}
		}
	}

	private static void buffNearbyHostileMobs(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
					continue;
				}
				Box box = player.getBoundingBox().expand(48.0D, 28.0D, 48.0D);
				List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, box, Entity -> Entity.isAlive());
				for (HostileEntity mob : hostiles) {
					if (PartyDollEntitySafeCheck.isFriendly(mob)) {
						continue;
					}
					mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
					mob.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, true, false));
					mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false));
					if (mob.getTarget() == null && mob.squaredDistanceTo(player) <= 48.0D * 48.0D) {
						mob.setTarget(player);
					}
				}
			}
		}
	}

	private static void spawnApocalypseMobWaves(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			Random rng = world.getRandom();
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
					continue;
				}
				Box countBox = player.getBoundingBox().expand(42.0D, 24.0D, 42.0D);
				int nearbyHostiles = world.getEntitiesByClass(HostileEntity.class, countBox, HostileEntity::isAlive).size();
				if (nearbyHostiles >= 24) {
					continue;
				}

				int toSpawn = 2 + rng.nextInt(2); // 2..3 моба каждые 1.75 сек на игрока
				for (int i = 0; i < toSpawn; i++) {
					BlockPos spawnPos = findWaveSpawnPos(world, player, rng);
					if (spawnPos == null) {
						continue;
					}
					HostileEntity mob = createWaveMob(world, rng);
					if (mob == null) {
						continue;
					}
					mob.refreshPositionAndAngles(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, rng.nextFloat() * 360.0F, 0.0F);
					if (MobEvolutionManager.isEvolvableMob(mob)) {
						MobEvolutionManager.initializeMobIfNeeded(mob, world);
					}
					mob.setTarget(player);
					world.spawnEntity(mob);
					world.spawnParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 0.8D, mob.getZ(), 8, 0.25D, 0.4D, 0.25D, 0.02D);
				}
			}
		}
	}

	private static HostileEntity createWaveMob(ServerWorld world, Random rng) {
		int roll = rng.nextInt(10);
		if (roll < 4) {
			return EntityType.ZOMBIE.create(world);
		}
		if (roll < 7) {
			SkeletonEntity skel = EntityType.SKELETON.create(world);
			if (skel != null) {
				skel.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BOW));
				skel.updateAttackType();
			}
			return skel;
		}
		if (roll < 9) {
			return EntityType.CREEPER.create(world);
		}
		return EntityType.SPIDER.create(world);
	}

	@Nullable
	private static BlockPos findWaveSpawnPos(ServerWorld world, ServerPlayerEntity player, Random rng) {
		BlockPos base = player.getBlockPos();
		for (int attempt = 0; attempt < 10; attempt++) {
			double angle = rng.nextDouble() * Math.PI * 2.0D;
			double dist = 16.0D + rng.nextDouble() * 14.0D;
			int x = MathHelper.floor(player.getX() + Math.cos(angle) * dist);
			int z = MathHelper.floor(player.getZ() + Math.sin(angle) * dist);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			// Ищем валидный пол рядом по высоте с игроком (работает и под землёй в пещере/данже, и на поверхности!)
			for (int dy = 6; dy >= -8; dy--) {
				BlockPos p = new BlockPos(x, base.getY() + dy, z);
				if (!world.getBlockState(p.down()).isAir()
						&& world.getBlockState(p.down()).isSolidBlock(world, p.down())
						&& world.getBlockState(p).isAir()
						&& world.getBlockState(p.up()).isAir()) {
					return p;
				}
			}
			int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos surf = new BlockPos(x, topY, z);
			if (Math.abs(topY - base.getY()) <= 24 && world.getBlockState(surf).isAir() && world.getBlockState(surf.up()).isAir()) {
				return surf;
			}
		}
		return null;
	}

	/**
	 * Во время 10-минутного Апокалипсиса криперы активно роют проход к игроку сквозь любые стены/потолки/полы (включая обсидиан!),
	 * даже если игроки полностью застроились в коробку или закопались!
	 */
	public static void tickCreeperTunneling(CreeperEntity creeper, ServerWorld world) {
		if (!isActive(world.getServer()) || creeper.age % 8 != 0) {
			return;
		}
		if (!(creeper.getTarget() instanceof ServerPlayerEntity target) || !target.isAlive() || target.isSpectator()) {
			return;
		}
		if (PartyDollEntitySafeCheck.isFriendly(creeper)) {
			return;
		}

		double distSq = creeper.squaredDistanceTo(target);
		if (distSq > 48.0D * 48.0D) {
			return;
		}

		Vec3d delta = target.getPos().subtract(creeper.getPos());
		Vec3d horiz = new Vec3d(delta.x, 0.0D, delta.z);
		BlockPos creeperFeet = creeper.getBlockPos();

		boolean blockedHoriz = creeper.horizontalCollision || creeper.getNavigation().isIdle();
		if (!blockedHoriz && Math.abs(delta.y) < 2.2D) {
			return;
		}

		int stepX = horiz.lengthSquared() > 0.04D ? MathHelper.clamp((int) Math.round(horiz.normalize().x), -1, 1) : 0;
		int stepZ = horiz.lengthSquared() > 0.04D ? MathHelper.clamp((int) Math.round(horiz.normalize().z), -1, 1) : 0;

		List<BlockPos> digTargets = new ArrayList<>(4);
		if (delta.y < -1.5D && distSq < 9.0D) {
			// Игрок закопался прямо под крипером — роем пол вниз!
			digTargets.add(creeperFeet.down());
			digTargets.add(creeperFeet.add(stepX, -1, stepZ));
		} else if (delta.y > 1.8D && distSq < 16.0D) {
			// Игрок построился/закрылся сверху — пробиваем потолок и диагональ вверх!
			digTargets.add(creeperFeet.up(2));
			digTargets.add(creeperFeet.add(stepX, 1, stepZ));
			digTargets.add(creeperFeet.add(stepX, 2, stepZ));
		} else if (stepX != 0 || stepZ != 0) {
			// Пробиваем тоннель 1x2 прямо сквозь стену к игроку!
			digTargets.add(creeperFeet.add(stepX, 1, stepZ));
			digTargets.add(creeperFeet.add(stepX, 0, stepZ));
		}

		for (BlockPos digPos : digTargets) {
			if (canCreeperMineBlock(world, digPos)) {
				BlockState old = world.getBlockState(digPos);
				world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, digPos, net.minecraft.block.Block.getRawIdFromState(old));
				world.breakBlock(digPos, true, creeper);
				break; // По 1 блоку раз в 0.4 сек — видно и слышно, как крипер прорывает путь!
			}
		}
	}

	/**
	 * Во время Апокалипсиса взрыв крипера разрушает даже обсидиан и плачущий обсидиан!
	 */
	public static void shatterObsidianOnCreeperExplode(CreeperEntity creeper, ServerWorld world) {
		if (!isActive(world.getServer())) {
			return;
		}
		BlockPos center = creeper.getBlockPos();
		BlockPos.Mutable m = new BlockPos.Mutable();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dy = -3; dy <= 4; dy++) {
				for (int dz = -4; dz <= 4; dz++) {
					if (dx * dx + dy * dy + dz * dz > 16) {
						continue;
					}
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState st = world.getBlockState(m);
					if (st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.CRYING_OBSIDIAN) || st.isOf(Blocks.RESPAWN_ANCHOR)) {
						world.breakBlock(m, true, creeper);
					}
				}
			}
		}
	}

	private static boolean canCreeperMineBlock(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.isOf(Blocks.BEDROCK)
				|| state.isOf(Blocks.BARRIER)
				|| state.isOf(Blocks.END_PORTAL_FRAME)
				|| state.isOf(Blocks.END_PORTAL)
				|| state.isOf(Blocks.NETHER_PORTAL)
				|| state.isOf(Blocks.COMMAND_BLOCK)) {
			return false;
		}
		// Во время Апокалипсиса крипер ломает и обсидиан, и обычные строительные блоки (кроме бедрока/рамок портала/блок-энтити сундуков)
		if (world.getBlockEntity(pos) != null) {
			return false;
		}
		return state.getHardness(world, pos) >= 0.0F;
	}

	private static void finishApocalypse(MinecraftServer server, ModPersistentState state) {
		state.setApocalypseActive(false);
		state.setApocalypseTicksLeft(0);
		if (bossBar != null) {
			bossBar.clearPlayers();
			bossBar.setVisible(false);
			bossBar = null;
		}

		List<ServerPlayerEntity> survivors = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			if (p.isAlive() && !p.isSpectator() && !state.getDiedDuringApocalypse().contains(p.getUuid())) {
				survivors.add(p);
			}
		}
		state.getDiedDuringApocalypse().clear();
		state.markDirty();

		if (survivors.isEmpty()) {
			server.getPlayerManager().broadcast(
					Text.literal("☠ 10 минут проклятия истекли, но никто не выжил без смертей — награда сгорела во тьме!")
							.formatted(Formatting.RED, Formatting.BOLD),
					false
			);
			return;
		}

		int n = survivors.size();
		for (int i = 0; i < n; i++) {
			ServerPlayerEntity survivor = survivors.get(i);
			ModPersistentState.PlayerFinanceData acc = state.getAccount(survivor.getUuid());
			acc.balance += 30_000L;
			state.markDirty();
			ModServerNetworking.syncAccount(survivor);

			// Делим ровно между всеми выжившими: 64 железа (стак), 32 алмаза (полстака), 6 незерита
			int ironShare = (64 / n) + (i < (64 % n) ? 1 : 0);
			int diamondShare = (32 / n) + (i < (32 % n) ? 1 : 0);
			int netheriteShare = (6 / n) + (i < (6 % n) ? 1 : 0);

			giveOrDrop(survivor, Items.IRON_INGOT, ironShare);
			giveOrDrop(survivor, Items.DIAMOND, diamondShare);
			giveOrDrop(survivor, Items.NETHERITE_INGOT, netheriteShare);
			giveOrDrop(survivor, ModItems.REVIVAL_TOKEN, 1);

			survivor.getServerWorld().playSound(null, survivor.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
			survivor.getServerWorld().spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, survivor.getX(), survivor.getY() + 1.0D, survivor.getZ(), 60, 0.5D, 0.8D, 0.5D, 0.25D);
			survivor.sendMessage(
					Text.literal("✦ ВЫ ВЫЖИЛИ 10 МИНУТ! Награда: +30 000 ₽ на счёт, "
							+ ironShare + " железа, " + diamondShare + " алмазов, "
							+ netheriteShare + " незерита и 1 Жетон возрождения!")
							.formatted(Formatting.GREEN, Formatting.BOLD),
					false
			);
		}
	}

	private static void giveOrDrop(ServerPlayerEntity player, Item item, int count) {
		if (count <= 0) {
			return;
		}
		ItemStack stack = new ItemStack(item, count);
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	private static final class PartyDollEntitySafeCheck {
		static boolean isFriendly(MobEntity mob) {
			return com.novaimod.entity.PartyDollEntity.isUnderActiveDollAura(mob);
		}
	}
}
