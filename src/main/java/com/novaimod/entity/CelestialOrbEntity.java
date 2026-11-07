package com.novaimod.entity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.network.ModPayloads;
import com.novaimod.registry.ModEntities;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Сущность креативного «Жезла Небесной Кары» (v3.5.0):
 * - Фаза 0 (Шар Света): взмывает в небо, испуская объёмные чисто-БЕЛЫЕ лучи света (как при гибели Дракона Края, но ослепительно белые!).
 * - Фаза 1 (Небесные Шары Частиц): в зените рождается созвездие из 8 бурлящих в небе шаров белых частиц,
 *   из которых во все стороны (преимущественно прицельно в мобов!) бьют ветвистые 3D-молнии,
 *   а в каждой точке удара происходит настоящий мощный взрыв!
 */
public class CelestialOrbEntity extends Entity {
	public static final int PHASE_ASCENDING_LIGHT = 0;
	public static final int PHASE_SKY_PARTICLE_SPHERE = 1;

	public static final int ASCEND_DURATION_TICKS = 56;
	public static final int SKY_SPHERE_DURATION_TICKS = 165;

	private static final TrackedData<Integer> PHASE =
			DataTracker.registerData(CelestialOrbEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> BREAK_BLOCKS =
			DataTracker.registerData(CelestialOrbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

	@Nullable
	private UUID casterUuid;
	private int strikeOffset = 0;

	public CelestialOrbEntity(EntityType<? extends CelestialOrbEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	public CelestialOrbEntity(World world, double x, double y, double z, @Nullable UUID casterUuid, int phase, boolean breakBlocks, int strikeOffset) {
		this(ModEntities.CELESTIAL_ORB_ENTITY, world);
		this.setPosition(x, y, z);
		this.casterUuid = casterUuid;
		this.strikeOffset = strikeOffset;
		this.dataTracker.set(PHASE, phase);
		this.dataTracker.set(BREAK_BLOCKS, breakBlocks);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(PHASE, PHASE_ASCENDING_LIGHT);
		builder.add(BREAK_BLOCKS, true);
	}

	public int getPhase() {
		return this.dataTracker.get(PHASE);
	}

	public boolean isBreakBlocks() {
		return this.dataTracker.get(BREAK_BLOCKS);
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0D * 256.0D;
	}

	@Override
	public void tick() {
		this.noClip = true;
		this.prevX = this.getX();
		this.prevY = this.getY();
		this.prevZ = this.getZ();
		super.tick();

		int phase = getPhase();
		if (phase == PHASE_ASCENDING_LIGHT) {
			tickAscendingLightOrb();
		} else {
			tickSkyParticleSphere();
		}
	}

	private void tickAscendingLightOrb() {
		// Взлетаем в небо первые 40 тиков (~33 блока вверх), затем 16 тиков сияем в зените перед расколом на небесные сферы!
		if (this.age <= 40) {
			double vy = 0.88D * (1.0D - (this.age / 52.0D) * 0.45D);
			this.setPosition(this.getX(), this.getY() + vy, this.getZ());
		}

		if (this.getWorld().isClient()) {
			return;
		}
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		// Белый шлейф вознесения
		sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 8, 0.45D, 0.45D, 0.45D, 0.08D);
		sw.spawnParticles(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.05D);

		if (this.age % 12 == 1) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 1.8F, 1.25F + this.age * 0.01F);
		}

		if (this.age >= ASCEND_DURATION_TICKS) {
			// Вспышка сверхсвета в небе и рождение 8 бурлящих небесных шаров частиц!
			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 4, 1.5D, 1.0D, 1.5D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 160, 1.2D, 1.2D, 1.2D, 0.55D);
			sw.spawnParticles(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), 120, 1.5D, 1.5D, 1.5D, 0.65D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.MASTER, 2.5F, 0.85F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 2.0F, 0.7F);

			boolean breakBlocks = isBreakBlocks();
			Random rng = sw.getRandom();

			// 1 центральный небесный шар частиц + 7 шаров по широкому кольцу в небесах!
			CelestialOrbEntity centerNode = new CelestialOrbEntity(
					sw, this.getX(), this.getY() + 2.0D, this.getZ(), this.casterUuid, PHASE_SKY_PARTICLE_SPHERE, breakBlocks, 0
			);
			sw.spawnEntity(centerNode);

			int outerCount = 7;
			for (int i = 0; i < outerCount; i++) {
				double ang = (Math.PI * 2.0D * i) / outerCount + rng.nextDouble() * 0.35D;
				double dist = 16.0D + rng.nextDouble() * 11.0D;
				double nx = this.getX() + Math.cos(ang) * dist;
				double ny = this.getY() - 2.0D + rng.nextDouble() * 8.0D;
				double nz = this.getZ() + Math.sin(ang) * dist;
				CelestialOrbEntity skyNode = new CelestialOrbEntity(
						sw, nx, ny, nz, this.casterUuid, PHASE_SKY_PARTICLE_SPHERE, breakBlocks, i + 1
				);
				sw.spawnEntity(skyNode);
			}

			this.discard();
		}
	}

	private void tickSkyParticleSphere() {
		// Лёгкое парение небесного шара
		double bob = Math.sin((this.age + this.strikeOffset * 9) * 0.12D) * 0.03D;
		this.setPosition(this.getX(), this.getY() + bob, this.getZ());

		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		Random rng = sw.getRandom();

		// Формируем в небе плотный бурлящий ШАР ЧАСТИЦ (сферическая оболочка радиусом ~2.3 блока из белых и электрических искр!)
		for (int i = 0; i < 14; i++) {
			double theta = rng.nextDouble() * Math.PI * 2.0D;
			double phi = Math.acos(2.0D * rng.nextDouble() - 1.0D);
			double r = 1.6D + rng.nextDouble() * 1.1D;
			double px = this.getX() + r * Math.sin(phi) * Math.cos(theta);
			double py = this.getY() + r * Math.cos(phi);
			double pz = this.getZ() + r * Math.sin(phi) * Math.sin(theta);
			sw.spawnParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, px, py, pz, 1, 0.06D, 0.06D, 0.06D, 0.015D);
		}
		sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(), 6, 1.3D, 1.3D, 1.3D, 0.12D);
		if (this.age % 3 == 0) {
			sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY(), this.getZ(), 3, 0.9D, 0.9D, 0.9D, 0.02D);
		}

		// Каждые 6 тиков каждый небесный шар выпускает мощную молнию со взрывом в точке удара!
		if (this.age >= 5 && (this.age + this.strikeOffset) % 6 == 0) {
			fireLightningAndExplode(sw, rng);
		}

		if (this.age >= SKY_SPHERE_DURATION_TICKS) {
			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 35, 0.8D, 0.8D, 0.8D, 0.25D);
			this.discard();
		}
	}

	private void fireLightningAndExplode(ServerWorld sw, Random rng) {
		Vec3d origin = this.getPos();
		Box mobScanBox = Box.of(origin, 170.0D, 140.0D, 170.0D);
		List<MobEntity> nearbyMobs = sw.getEntitiesByClass(
				MobEntity.class,
				mobScanBox,
				m -> m.isAlive() && !(m instanceof ModNpcEntity)
		);

		Vec3d impactPos = null;
		MobEntity chosenMob = null;

		// 78% ударов бьют ПРИЦЕЛЬНО В МОБОВ (включая летающих и боссов вроде Дракона Края!), а остальные летят ВО ВСЕ СТОРОНЫ!
		if (!nearbyMobs.isEmpty() && rng.nextFloat() < 0.78F) {
			chosenMob = nearbyMobs.get(rng.nextInt(nearbyMobs.size()));
			impactPos = chosenMob.getPos().add(
					(rng.nextDouble() - 0.5D) * 0.8D,
					chosenMob.getHeight() * 0.35D,
					(rng.nextDouble() - 0.5D) * 0.8D
			);
		} else {
			// Выстрел молнией в произвольном направлении (во все стороны от небесного шара!)
			double azimuth = rng.nextDouble() * Math.PI * 2.0D;
			double horizDist = 6.0D + rng.nextDouble() * 62.0D;
			double tx = origin.x + Math.cos(azimuth) * horizDist;
			double tz = origin.z + Math.sin(azimuth) * horizDist;

			// 15% — эффектные атмосферные разряды прямо в небе во все стороны, 85% — в землю со взрывом!
			if (rng.nextFloat() < 0.15F) {
				double ty = origin.y + (rng.nextDouble() - 0.65D) * 26.0D;
				impactPos = new Vec3d(tx, ty, tz);
			} else {
				Vec3d farBelow = new Vec3d(tx, sw.getBottomY() + 1, tz);
				BlockHitResult ray = sw.raycast(new RaycastContext(
						origin,
						farBelow,
						RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.ANY,
						this
				));
				if (ray.getType() != HitResult.Type.MISS) {
					impactPos = ray.getPos();
				} else {
					int topY = sw.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(tx), MathHelper.floor(tz));
					impactPos = new Vec3d(tx, topY, tz);
				}
			}
		}

		// 1. Отправляем клиентам 3D-луч молнии напрямую ИЗ НЕБЕСНОГО ШАРА ЧАСТИЦ в точку удара!
		ModPayloads.CelestialLightningBeamPayload beamPacket =
				new ModPayloads.CelestialLightningBeamPayload(this.getId(), impactPos.x, impactPos.y, impactPos.z);
		for (ServerPlayerEntity tracking : PlayerLookup.tracking(this)) {
			ServerPlayNetworking.send(tracking, beamPacket);
		}

		// 2. Спавним ванильную молнию в месте попадания
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
		if (bolt != null) {
			bolt.refreshPositionAfterTeleport(impactPos.x, impactPos.y, impactPos.z);
			bolt.setCosmetic(true);
			sw.spawnEntity(bolt);
		}

		// 3. В МЕСТЕ УДАРА ОБРАЗУЕТСЯ МОЩНЫЙ ВЗРЫВ!
		boolean breakBlocks = isBreakBlocks();
		Entity caster = this.casterUuid != null ? sw.getPlayerByUuid(this.casterUuid) : null;
		sw.createExplosion(
				this,
				impactPos.x,
				impactPos.y,
				impactPos.z,
				4.2F,
				false,
				breakBlocks ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE
		);
		sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impactPos.x, impactPos.y + 0.2D, impactPos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		sw.spawnParticles(ParticleTypes.END_ROD, impactPos.x, impactPos.y + 0.4D, impactPos.z, 22, 0.6D, 0.5D, 0.6D, 0.35D);
		sw.playSound(null, BlockPos.ofFloored(impactPos), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 1.4F, 0.9F + rng.nextFloat() * 0.25F);

		// 4. Сокрушительный божественный урон всем мобам и боссам (включая Дракона Края!), не задевая самого владельца жезла!
		Box hitBox = Box.of(impactPos, 11.0D, 11.0D, 11.0D);
		for (LivingEntity victim : sw.getEntitiesByClass(LivingEntity.class, hitBox, LivingEntity::isAlive)) {
			if (victim instanceof ModNpcEntity) {
				continue;
			}
			if (this.casterUuid != null && this.casterUuid.equals(victim.getUuid())) {
				continue;
			}
			victim.timeUntilRegen = 0;
			if (victim instanceof EnderDragonEntity dragon) {
				dragon.damagePart(dragon.body, sw.getDamageSources().explosion(this, caster), 36.0F);
			} else {
				victim.damage(sw.getDamageSources().explosion(this, caster), 45.0F);
			}
		}

		if (chosenMob instanceof EnderDragonEntity directDragon && directDragon.isAlive()) {
			directDragon.damagePart(directDragon.head, sw.getDamageSources().explosion(this, caster), 36.0F);
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(PHASE, nbt.getInt("OrbPhase"));
		if (nbt.contains("BreakBlocks")) {
			this.dataTracker.set(BREAK_BLOCKS, nbt.getBoolean("BreakBlocks"));
		}
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
		this.strikeOffset = nbt.getInt("StrikeOffset");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("OrbPhase", getPhase());
		nbt.putBoolean("BreakBlocks", isBreakBlocks());
		if (this.casterUuid != null) {
			nbt.putUuid("CasterUuid", this.casterUuid);
		}
		nbt.putInt("StrikeOffset", this.strikeOffset);
	}
}
