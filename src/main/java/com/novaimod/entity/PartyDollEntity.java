package com.novaimod.entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.novaimod.data.ModPersistentState;
import com.novaimod.data.WorldVirginLedger;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.registry.ModSounds;
import com.novaimod.world.ApocalypseEventManager;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Женская кукла высотой ровно в 1 блок («Танцующая кукла»):
 * - В обычной игре работает без ограничений.
 * - Во время 5-минутного испытания:
 *   1) Эффект длится ровно 30 секунд (600 тиков).
 *   2) Ровно через 30 секунд музыка обрывается и ВСЕ мобы разом разворачиваются в сторону ближайшего игрока!
 *   3) Ещё через 1 секунду (20 тиков) кукла ломается вдребезги, и всё продолжается как было.
 *   4) При попытке повторного использования до конца испытания кукла не работает («По каким-то причинам она не работает...»).
 */
public class PartyDollEntity extends Entity {
	public static final double AURA_RADIUS = 20.0D;
	public static final double AURA_RADIUS_SQ = AURA_RADIUS * AURA_RADIUS;
	public static final float YARIS_BEAT_RAD_PER_TICK = 0.70685835F;
	private static final int SOUND_LOOP_TICKS = 289;

	private static final int TRIAL_DANCE_TICKS = 20 * 30; // 30 секунд = 600 тиков
	private static final int TRIAL_BREAK_TICK = TRIAL_DANCE_TICKS + 20; // +1 секунда = 620 тиков

	private static final TrackedData<Boolean> ACTIVE = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Boolean> STARE_PHASE = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> DOLL_YAW = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private static final Map<World, Map<Integer, PartyDollEntity>> ACTIVE_BY_WORLD =
			Collections.synchronizedMap(new IdentityHashMap<>());

	private int musicLoopTicker = 0;
	private int trialActiveTicks = 0;

	public PartyDollEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(ACTIVE, false);
		builder.add(STARE_PHASE, false);
		builder.add(DOLL_YAW, 0.0F);
	}

	public boolean isActive() {
		return this.dataTracker.get(ACTIVE);
	}

	public void setActive(boolean active) {
		this.dataTracker.set(ACTIVE, active);
		if (!active) {
			this.dataTracker.set(STARE_PHASE, false);
			this.trialActiveTicks = 0;
		} else {
			this.musicLoopTicker = 0;
		}
		updateWorldRegistry(active && !this.isRemoved());
	}

	public boolean isStarePhase() {
		return this.dataTracker.get(STARE_PHASE);
	}

	public void setStarePhase(boolean stare) {
		this.dataTracker.set(STARE_PHASE, stare);
	}

	public float getDollYaw() {
		return this.dataTracker.get(DOLL_YAW);
	}

	public void setDollYaw(float yaw) {
		this.dataTracker.set(DOLL_YAW, yaw);
	}

	public void updateWorldRegistry(boolean shouldBeRegistered) {
		World w = this.getWorld();
		if (w == null) {
			return;
		}
		if (shouldBeRegistered) {
			Map<Integer, PartyDollEntity> map;
			synchronized (ACTIVE_BY_WORLD) {
				map = ACTIVE_BY_WORLD.computeIfAbsent(w, k -> new ConcurrentHashMap<>());
			}
			map.put(this.getId(), this);
		} else {
			Map<Integer, PartyDollEntity> map = ACTIVE_BY_WORLD.get(w);
			if (map != null) {
				map.remove(this.getId());
			}
		}
	}

	public static PartyDollEntity findNearestActiveDoll(Entity entity) {
		if (entity == null) {
			return null;
		}
		World world = entity.getWorld();
		if (world == null) {
			return null;
		}

		PartyDollEntity best = null;
		double bestDistSq = AURA_RADIUS_SQ;

		Map<Integer, PartyDollEntity> worldDolls = ACTIVE_BY_WORLD.get(world);
		if (worldDolls != null && !worldDolls.isEmpty()) {
			for (PartyDollEntity doll : worldDolls.values()) {
				if (doll == null || doll.isRemoved() || !doll.isActive() || doll.getWorld() != world) {
					continue;
				}
				double dSq = entity.squaredDistanceTo(doll);
				if (dSq <= bestDistSq) {
					bestDistSq = dSq;
					best = doll;
				}
			}
		}

		if (best == null) {
			Box searchBox = entity.getBoundingBox().expand(AURA_RADIUS, 10.0D, AURA_RADIUS);
			List<PartyDollEntity> found = world.getEntitiesByClass(
					PartyDollEntity.class,
					searchBox,
					d -> !d.isRemoved() && d.isActive()
			);
			for (PartyDollEntity doll : found) {
				doll.updateWorldRegistry(true);
				double dSq = entity.squaredDistanceTo(doll);
				if (dSq <= bestDistSq) {
					bestDistSq = dSq;
					best = doll;
				}
			}
		}

		return best;
	}

	public static boolean isUnderActiveDollAura(Entity entity) {
		return findNearestActiveDoll(entity) != null;
	}

	/**
	 * Активен ли именно визуальный танец (сплющивание + вращение 360°):
	 * отключается в финальную 1 секунду испытания, чтобы было чётко видно, как ВСЕ мобы разом развернулись к игроку!
	 */
	public static boolean isUnderDancingDollAura(Entity entity) {
		PartyDollEntity doll = findNearestActiveDoll(entity);
		return doll != null && !doll.isStarePhase();
	}

	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (hand != Hand.MAIN_HAND) {
			return ActionResult.PASS;
		}
		if (this.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}

		if (this.getWorld() instanceof ServerWorld sw && ApocalypseEventManager.isActive(sw.getServer())) {
			ModPersistentState state = ModPersistentState.get(sw.getServer());
			if (!this.isActive()) {
				if (state.isApocalypseDollUsed()) {
					Text msg = Text.literal("По каким-то причинам она не работает...").formatted(Formatting.RED);
					player.sendMessage(msg, false);
					player.sendMessage(msg, true);
					sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.9F, 0.75F);
					return ActionResult.CONSUME;
				}
				state.setApocalypseDollUsed(true);
				this.trialActiveTicks = 1;
				this.setStarePhase(false);
			} else {
				// Во время испытания уже запущенная кукла отсчитывает свои 30 секунд и не выключается вручную
				return ActionResult.CONSUME;
			}
		}

		boolean nextState = !this.isActive();
		this.setActive(nextState);
		if (nextState && this.getWorld() instanceof ServerWorld sw && ApocalypseEventManager.isActive(sw.getServer())) {
			this.trialActiveTicks = 1;
		}

		if (nextState) {
			if (this.getWorld() instanceof ServerWorld sw) {
				playYarisLoop(sw);
				this.musicLoopTicker = 1;
				sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.05D, this.getZ(), 8, 0.45D, 0.25D, 0.45D, 0.02D);
				sw.spawnParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.15D, this.getZ(), 10, 0.55D, 0.3D, 0.55D, 0.35D);
			}
			player.sendMessage(
					Text.literal("♫ Кукла включена! Все вокруг замерли на месте и плющатся под бит Toyota Yaris!")
							.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
					true
			);
		} else {
			if (this.getWorld() instanceof ServerWorld sw) {
				stopYarisLoopIfNoOtherActive(sw);
			}
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.RECORDS, 0.7F, 0.75F);
			player.sendMessage(
					Text.literal("Кукла выключена — все вернулись в обычный режим.").formatted(Formatting.YELLOW),
					true
			);
		}
		return ActionResult.CONSUME;
	}

	private void playYarisLoop(ServerWorld sw) {
		sw.playSound(null, this.getX(), this.getY() + 0.5D, this.getZ(), ModSounds.YARIS_MEME, SoundCategory.RECORDS, 1.6F, 1.0F);
	}

	private void stopYarisLoopIfNoOtherActive(ServerWorld sw) {
		Box nearby = Box.of(this.getPos(), 48.0D, 24.0D, 48.0D);
		boolean anotherPlaying = !sw.getEntitiesByClass(
				PartyDollEntity.class,
				nearby,
				d -> d != this && !d.isRemoved() && d.isActive() && !d.isStarePhase()
		).isEmpty();
		if (!anotherPlaying) {
			StopSoundS2CPacket stopPacket = new StopSoundS2CPacket(ModSounds.YARIS_MEME_ID, SoundCategory.RECORDS);
			for (ServerPlayerEntity sp : sw.getPlayers()) {
				if (sp.squaredDistanceTo(this) <= 64.0D * 64.0D) {
					sp.networkHandler.sendPacket(stopPacket);
				}
			}
		}
	}

	@Override
	public void tick() {
		super.tick();

		updateWorldRegistry(this.isActive() && !this.isRemoved());

		if (!this.isOnGround()) {
			this.setVelocity(this.getVelocity().add(0.0D, -0.06D, 0.0D));
		} else {
			this.setVelocity(Vec3d.ZERO);
		}
		if (this.getVelocity().lengthSquared() > 1.0E-5D) {
			this.move(MovementType.SELF, this.getVelocity());
		}

		if (this.getWorld().isClient() || !this.isActive()) {
			return;
		}
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		// Особое поведение куклы во время 5-минутного испытания!
		if (ApocalypseEventManager.isActive(sw.getServer())) {
			ModPersistentState state = ModPersistentState.get(sw.getServer());
			if (this.trialActiveTicks <= 0) {
				// Если кукла уже стояла включённой до старта испытания
				if (state.isApocalypseDollUsed()) {
					this.setActive(false);
					stopYarisLoopIfNoOtherActive(sw);
					return;
				}
				state.setApocalypseDollUsed(true);
				this.trialActiveTicks = 1;
			} else {
				this.trialActiveTicks++;
			}

			if (this.trialActiveTicks == TRIAL_DANCE_TICKS) {
				// Прошло ровно 30 секунд: музыка резко обрывается, ВСЕ мобы разом разворачиваются к ближайшему игроку!
				this.setStarePhase(true);
				stopYarisLoopIfNoOtherActive(sw);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.HOSTILE, 1.3F, 0.55F);
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 0.85F, 0.65F);
				snapAllNearbyMobsTowardNearestPlayer(sw);
				return;
			}

			if (this.trialActiveTicks > TRIAL_DANCE_TICKS && this.trialActiveTicks < TRIAL_BREAK_TICK) {
				// Секунда гробовой тишины: все мобы смотрят прямо в глаза ближайшему игроку!
				snapAllNearbyMobsTowardNearestPlayer(sw);
				return;
			}

			if (this.trialActiveTicks >= TRIAL_BREAK_TICK) {
				// Через 1 секунду кукла ломается вдребезги, и мобы мгновенно бросаются в атаку как раньше!
				shatterDollAndResumeTrial(sw);
				return;
			}
		} else if (this.trialActiveTicks > 0 || this.isStarePhase()) {
			this.trialActiveTicks = 0;
			this.setStarePhase(false);
		}

		if (this.musicLoopTicker <= 0 || this.musicLoopTicker >= SOUND_LOOP_TICKS) {
			playYarisLoop(sw);
			this.musicLoopTicker = 1;
		} else {
			this.musicLoopTicker++;
		}

		if (this.age % 9 == 0) {
			sw.spawnParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.15D, this.getZ(), 2, 0.35D, 0.15D, 0.35D, 0.3D);
		}
		if (this.age % 22 == 0) {
			sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.05D, this.getZ(), 2, 0.4D, 0.2D, 0.4D, 0.01D);
		}
	}

	private void snapAllNearbyMobsTowardNearestPlayer(ServerWorld sw) {
		Box auraBox = this.getBoundingBox().expand(AURA_RADIUS + 4.0D, 12.0D, AURA_RADIUS + 4.0D);
		List<MobEntity> mobs = sw.getEntitiesByClass(MobEntity.class, auraBox, MobEntity::isAlive);
		for (MobEntity mob : mobs) {
			PlayerEntity nearest = sw.getClosestPlayer(mob, 64.0D);
			if (nearest == null || !nearest.isAlive()) {
				continue;
			}
			snapMobToFace(mob, nearest);
		}
	}

	public static void snapMobToFace(MobEntity mob, Entity target) {
		double dx = target.getX() - mob.getX();
		double dz = target.getZ() - mob.getZ();
		double dy = target.getEyeY() - mob.getEyeY();
		double horiz = Math.hypot(dx, dz);
		float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
		float pitch = (float) -(MathHelper.atan2(dy, horiz) * (180.0D / Math.PI));

		mob.setYaw(yaw);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setPitch(pitch);
		mob.prevYaw = yaw;
		mob.prevHeadYaw = yaw;
		mob.prevBodyYaw = yaw;
		mob.prevPitch = pitch;
		mob.getLookControl().lookAt(target.getX(), target.getEyeY(), target.getZ(), 180.0F, 180.0F);
	}

	private void shatterDollAndResumeTrial(ServerWorld sw) {
		double cx = this.getX();
		double cy = this.getY() + 0.5D;
		double cz = this.getZ();

		this.setActive(false);
		stopYarisLoopIfNoOtherActive(sw);

		sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.HOSTILE, 1.3F, 0.85F);
		sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.2F, 0.8F);
		sw.spawnParticles(
				new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModItems.PARTY_DOLL)),
				cx,
				cy,
				cz,
				28,
				0.25D,
				0.30D,
				0.25D,
				0.12D
		);
		sw.spawnParticles(ParticleTypes.SMOKE, cx, cy, cz, 18, 0.3D, 0.3D, 0.3D, 0.04D);

		this.discard();

		// Возвращаем агрессию всем враждебным мобам вокруг!
		Box auraBox = Box.of(new Vec3d(cx, cy, cz), (AURA_RADIUS + 8.0D) * 2.0D, 24.0D, (AURA_RADIUS + 8.0D) * 2.0D);
		List<HostileEntity> hostiles = sw.getEntitiesByClass(HostileEntity.class, auraBox, HostileEntity::isAlive);
		for (HostileEntity mob : hostiles) {
			PlayerEntity nearest = sw.getClosestPlayer(mob.getX(), mob.getY(), mob.getZ(), 64.0D, true);
			if (nearest != null && nearest.isAlive()) {
				mob.setTarget(nearest);
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		updateWorldRegistry(false);
		super.remove(reason);
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source) || this.getWorld().isClient() || this.isRemoved()) {
			return false;
		}
		boolean wasActive = this.isActive();
		this.setActive(false);
		if (wasActive && this.getWorld() instanceof ServerWorld sw) {
			stopYarisLoopIfNoOtherActive(sw);
		}
		this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.BLOCKS, 0.9F, 1.1F);
		boolean drop = !(source.getAttacker() instanceof PlayerEntity p && p.isInCreativeMode());
		if (drop) {
			this.dropStack(new ItemStack(ModItems.PARTY_DOLL));
		}
		this.discard();
		return true;
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(ModItems.PARTY_DOLL);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Active")) {
			this.setActive(nbt.getBoolean("Active"));
		}
		if (nbt.contains("StarePhase")) {
			this.setStarePhase(nbt.getBoolean("StarePhase"));
		}
		if (nbt.contains("TrialTicks")) {
			this.trialActiveTicks = nbt.getInt("TrialTicks");
		}
		if (nbt.contains("DollYaw")) {
			this.setDollYaw(nbt.getFloat("DollYaw"));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("Active", this.isActive());
		nbt.putBoolean("StarePhase", this.isStarePhase());
		nbt.putInt("TrialTicks", this.trialActiveTicks);
		nbt.putFloat("DollYaw", this.getDollYaw());
	}

	public static PartyDollEntity placeDoll(World world, BlockPos supportPos, float yaw) {
		BlockState below = world.getBlockState(supportPos);
		double y = supportPos.getY() + (below.isOf(Blocks.AIR) ? 0.0D : 1.0D);
		PartyDollEntity doll = ModEntities.PARTY_DOLL_ENTITY.create(world);
		if (doll == null) {
			return null;
		}
		if (!WorldVirginLedger.isInStructureGenScope()) {
			doll.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
		}
		doll.refreshPositionAndAngles(supportPos.getX() + 0.5D, y, supportPos.getZ() + 0.5D, yaw, 0.0F);
		doll.setDollYaw(yaw);
		world.spawnEntity(doll);
		return doll;
	}
}
