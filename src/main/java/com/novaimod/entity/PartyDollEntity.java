package com.novaimod.entity;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.registry.ModSounds;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Женская кукла высотой ровно в 1 блок («Танцующая кукла»):
 * - По нажатию ПКМ включается / выключается.
 * - Играет трек мема с прыгающей Toyota Yaris ("Why's this dealer?" — UK Speed Garage 135 BPM).
 * - Пока кукла активна:
 *   1) Все мобы и NPC в радиусе 20 блоков становятся полностью дружелюбными, сбегаются к кукле,
 *      ВСТАЮТ НА МЕСТЕ И НЕ ДВИГАЮТСЯ (кроме визуальных эффектов сплющивания и вращения!).
 *   2) Игроки и мобы в зоне куклы не наносят друг другу урон.
 *   3) ВСЕ сущности в радиусе 20 блоков (мобы, NPC, игроки и сама кукла) синхронно под 135 BPM
 *      сплющиваются вниз-вширь и возвращаются в нормальный размер + крутятся на 360° чисто визуально
 *      (игроки спокойно ходят, на камеру не влияет!).
 */
public class PartyDollEntity extends Entity {
	public static final double AURA_RADIUS = 20.0D;
	public static final double AURA_RADIUS_SQ = AURA_RADIUS * AURA_RADIUS;
	// 135 BPM = 2.25 ударов/сек = 0.1125 ударов/тик -> 2 * PI * 0.1125 = 0.70685835F рад/тик!
	public static final float YARIS_BEAT_RAD_PER_TICK = 0.70685835F;
	private static final int SOUND_LOOP_TICKS = 289; // 14.466 сек — точная длина загруженного трека Toyota Yaris meme edit!

	private static final TrackedData<Boolean> ACTIVE = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> DOLL_YAW = DataTracker.registerData(PartyDollEntity.class, TrackedDataHandlerRegistry.FLOAT);

	/**
	 * Храним активные куклы раздельно для ServerWorld и ClientWorld через IdentityHashMap<World, ...>,
	 * чтобы в одиночной игре серверная и клиентская сущности с одинаковым entityId никогда не перекрывали друг друга!
	 */
	private static final Map<World, Map<Integer, PartyDollEntity>> ACTIVE_BY_WORLD =
			Collections.synchronizedMap(new IdentityHashMap<>());

	private int musicLoopTicker = 0;

	public PartyDollEntity(EntityType<?> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(ACTIVE, false);
		builder.add(DOLL_YAW, 0.0F);
	}

	public boolean isActive() {
		return this.dataTracker.get(ACTIVE);
	}

	public void setActive(boolean active) {
		this.dataTracker.set(ACTIVE, active);
		updateWorldRegistry(active && !this.isRemoved());
		if (active) {
			this.musicLoopTicker = 0;
		}
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

		// Подстраховка на самом первом кадре клиента до первого tick() куклы
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

		boolean nextState = !this.isActive();
		this.setActive(nextState);

		if (nextState) {
			if (this.getWorld() instanceof ServerWorld sw) {
				playYarisLoop(sw);
				this.musicLoopTicker = 1;
				sw.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.05D, this.getZ(), 8, 0.45D, 0.25D, 0.45D, 0.02D);
				sw.spawnParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.15D, this.getZ(), 10, 0.55D, 0.3D, 0.55D, 0.35D);
			}
			player.sendMessage(
					Text.literal("♫ Кукла включена! Все вокруг замерли на месте и плющатся под бит Toyota Yaris! (ПКМ — выключить)")
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
				d -> d != this && !d.isRemoved() && d.isActive()
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
		if (nbt.contains("DollYaw")) {
			this.setDollYaw(nbt.getFloat("DollYaw"));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putBoolean("Active", this.isActive());
		nbt.putFloat("DollYaw", this.getDollYaw());
	}

	public static PartyDollEntity placeDoll(World world, BlockPos supportPos, float yaw) {
		BlockState below = world.getBlockState(supportPos);
		double y = supportPos.getY() + (below.isOf(Blocks.AIR) ? 0.0D : 1.0D);
		PartyDollEntity doll = ModEntities.PARTY_DOLL_ENTITY.create(world);
		if (doll == null) {
			return null;
		}
		doll.refreshPositionAndAngles(supportPos.getX() + 0.5D, y, supportPos.getZ() + 0.5D, yaw, 0.0F);
		doll.setDollYaw(yaw);
		world.spawnEntity(doll);
		return doll;
	}
}
