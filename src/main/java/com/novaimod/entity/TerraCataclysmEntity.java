package com.novaimod.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.novaimod.registry.ModEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * Многостадийный режиссёр катаклизмов для креативных предметов v3.6.0:
 * - Режим 0 («Катаклизм Геоманта»): вырывает 28 реальных глыб породы/магмы из земли, поднимает их кружащимся
 *   кольцом в небе на 2 секунды и обрушивает метеоритным шквалом прямо во врагов или в точку прицела игрока!
 * - Режим 1 («Гравитационный Инвертор» Зеркала Повелителя Миров): поднимает всех врагов в радиусе 64 блоков
 *   в стратосферу на 2.5 секунды и сокрушительно впечатывает обратно в землю!
 */
public class TerraCataclysmEntity extends Entity {
	public static final String TAG_CREATIVE_BOULDER = "novai_creative_boulder";
	public static final String TAG_ASTRAL_LIFTED = "novai_astral_lifted";

	private UUID ownerUuid;
	private int cataclysmType = 0; // 0 = Terra Boulder Swarm, 1 = Astral Gravity Inversion
	private final List<UUID> trackedEntities = new ArrayList<>();
	private Vec3d fallbackAimPos = Vec3d.ZERO;

	public TerraCataclysmEntity(EntityType<? extends TerraCataclysmEntity> type, World world) {
		super(type, world);
		this.noClip = true;
	}

	public static TerraCataclysmEntity startBoulderSwarm(ServerWorld sw, PlayerEntity owner, Vec3d aimTarget) {
		TerraCataclysmEntity ctrl = new TerraCataclysmEntity(ModEntities.TERRA_CATACLYSM_ENTITY, sw);
		ctrl.setPosition(owner.getX(), owner.getY() + 2.0D, owner.getZ());
		ctrl.ownerUuid = owner.getUuid();
		ctrl.cataclysmType = 0;
		ctrl.fallbackAimPos = aimTarget;
		ctrl.spawnTerrainBoulders(sw, owner);
		return ctrl;
	}

	public static TerraCataclysmEntity startGravityInversion(ServerWorld sw, PlayerEntity owner) {
		TerraCataclysmEntity ctrl = new TerraCataclysmEntity(ModEntities.TERRA_CATACLYSM_ENTITY, sw);
		ctrl.setPosition(owner.getX(), owner.getY() + 1.0D, owner.getZ());
		ctrl.ownerUuid = owner.getUuid();
		ctrl.cataclysmType = 1;
		ctrl.captureEnemiesForInversion(sw, owner);
		return ctrl;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
	}

	private void spawnTerrainBoulders(ServerWorld sw, PlayerEntity owner) {
		BlockPos origin = owner.getBlockPos();
		BlockState[] fallbackPalette = new BlockState[] {
				Blocks.MAGMA_BLOCK.getDefaultState(),
				Blocks.CRYING_OBSIDIAN.getDefaultState(),
				Blocks.GILDED_BLACKSTONE.getDefaultState(),
				Blocks.DEEPSLATE_GOLD_ORE.getDefaultState(),
				Blocks.BASALT.getDefaultState(),
				Blocks.TUFF.getDefaultState()
		};

		int count = 28;
		for (int i = 0; i < count; i++) {
			double angle = (Math.PI * 2.0D * i) / count;
			double dist = 4.5D + (i % 3) * 2.2D;
			int bx = MathHelper.floor(owner.getX() + Math.cos(angle) * dist);
			int bz = MathHelper.floor(owner.getZ() + Math.sin(angle) * dist);
			int topY = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz) - 1;
			BlockPos samplePos = new BlockPos(bx, Math.max(sw.getBottomY() + 1, topY), bz);

			BlockState chosenState = sw.getBlockState(samplePos);
			if (chosenState.isAir()
					|| chosenState.getHardness(sw, samplePos) < 0.0F
					|| sw.getBlockEntity(samplePos) != null
					|| samplePos.getSquaredDistance(origin) < 9.0D) {
				chosenState = fallbackPalette[i % fallbackPalette.length];
			} else if (i % 4 == 0) {
				// Каждую 4-ю глыбу превращаем в раскалённую магму/золочёный чернит для эпичности!
				chosenState = fallbackPalette[i % 3];
			}

			BlockPos spawnBlockPos = new BlockPos(bx, Math.max(sw.getBottomY() + 2, samplePos.getY() + 1), bz);
			FallingBlockEntity boulder = FallingBlockEntity.spawnFromBlock(sw, spawnBlockPos, chosenState);
			boulder.dropItem = false;
			boulder.setDestroyedOnLanding();
			boulder.setHurtEntities(120.0F, 600);
			boulder.setNoGravity(true);
			boulder.addCommandTag(TAG_CREATIVE_BOULDER);
			boulder.setVelocity(0.0D, 0.75D, 0.0D);

			sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, bx + 0.5D, samplePos.getY() + 1.0D, bz + 0.5D, 5, 0.35D, 0.2D, 0.35D, 0.02D);
			sw.spawnParticles(ParticleTypes.LAVA, bx + 0.5D, samplePos.getY() + 1.0D, bz + 0.5D, 3, 0.3D, 0.2D, 0.3D, 0.02D);
			this.trackedEntities.add(boulder.getUuid());
		}

		sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_WARDEN_DIG, SoundCategory.PLAYERS, 2.0F, 0.75F);
		sw.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.3F, 0.55F);
	}

	private void captureEnemiesForInversion(ServerWorld sw, PlayerEntity owner) {
		Box box = new Box(
				owner.getX() - 64.0D, owner.getY() - 32.0D, owner.getZ() - 64.0D,
				owner.getX() + 64.0D, owner.getY() + 48.0D, owner.getZ() + 64.0D
		);
		List<LivingEntity> targets = sw.getEntitiesByClass(
				LivingEntity.class,
				box,
				e -> e.isAlive()
						&& !e.isSpectator()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof ModNpcEntity)
						&& !(e instanceof TameableEntity tame && tame.isTamed())
		);
		for (LivingEntity target : targets) {
			target.setNoGravity(true);
			target.addCommandTag(TAG_ASTRAL_LIFTED);
			target.setVelocity(0.0D, 0.68D, 0.0D);
			target.velocityModified = true;
			this.trackedEntities.add(target.getUuid());
			sw.spawnParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 0.5D, target.getZ(), 10, 0.4D, 0.4D, 0.4D, 0.05D);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		if (this.cataclysmType == 0) {
			tickBoulderSwarm(sw);
		} else {
			tickGravityInversion(sw);
		}
	}

	private void tickBoulderSwarm(ServerWorld sw) {
		Entity ownerEntity = this.ownerUuid != null ? sw.getEntity(this.ownerUuid) : null;
		Vec3d center = ownerEntity != null ? ownerEntity.getPos() : this.getPos();
		if (ownerEntity != null) {
			this.setPosition(center.x, center.y + 2.0D, center.z);
		}

		if (this.age <= 36) {
			// Фаза 1 (0..36 тиков): глыбы поднимаются в небо и вращаются грозным огненным кольцом над игроком!
			double spinBase = this.age * 0.16D;
			for (int i = 0; i < this.trackedEntities.size(); i++) {
				Entity e = sw.getEntity(this.trackedEntities.get(i));
				if (!(e instanceof FallingBlockEntity boulder) || !boulder.isAlive()) {
					continue;
				}
				boulder.timeFalling = 1;
				double angle = spinBase + (Math.PI * 2.0D * i) / Math.max(1, this.trackedEntities.size());
				double radius = 6.0D + (i % 3) * 2.0D;
				double height = 9.0D + (i % 4) * 2.1D + Math.sin(this.age * 0.2D + i) * 0.8D;
				Vec3d desired = new Vec3d(center.x + Math.cos(angle) * radius, center.y + height, center.z + Math.sin(angle) * radius);
				Vec3d delta = desired.subtract(boulder.getPos()).multiply(0.32D);
				boulder.setVelocity(delta);
				boulder.velocityModified = true;
				if ((this.age + i) % 4 == 0) {
					sw.spawnParticles(ParticleTypes.FLAME, boulder.getX(), boulder.getY() + 0.5D, boulder.getZ(), 2, 0.25D, 0.25D, 0.25D, 0.02D);
				}
			}
			if (this.age == 36) {
				launchBouldersAtTargets(sw, ownerEntity);
			}
		} else if (this.age <= 105) {
			// Фаза 2: глыбы летят в цели и при столкновении вызывают сейсмические взрывы!
			boolean anyAlive = false;
			for (int i = 0; i < this.trackedEntities.size(); i++) {
				UUID id = this.trackedEntities.get(i);
				if (id == null) {
					continue;
				}
				Entity e = sw.getEntity(id);
				if (!(e instanceof FallingBlockEntity boulder) || !boulder.isAlive()) {
					this.trackedEntities.set(i, null);
					continue;
				}
				anyAlive = true;
				boulder.timeFalling = 2;
				sw.spawnParticles(ParticleTypes.LARGE_SMOKE, boulder.getX(), boulder.getY(), boulder.getZ(), 1, 0.15D, 0.15D, 0.15D, 0.01D);

				boolean hitGround = boulder.isOnGround() || boulder.horizontalCollision || boulder.verticalCollision;
				Box hitBox = boulder.getBoundingBox().expand(1.6D);
				List<LivingEntity> hitMobs = sw.getEntitiesByClass(
						LivingEntity.class,
						hitBox,
						m -> m.isAlive() && !(m instanceof PlayerEntity) && !(m instanceof ModNpcEntity)
				);
				if (hitGround || !hitMobs.isEmpty() || this.age == 105) {
					detonateBoulderImpact(sw, ownerEntity, boulder.getPos());
					boulder.discard();
					this.trackedEntities.set(i, null);
				}
			}
			if (!anyAlive || this.age >= 105) {
				this.discard();
			}
		} else {
			this.discard();
		}
	}

	private void launchBouldersAtTargets(ServerWorld sw, Entity ownerEntity) {
		Vec3d mainTarget = this.fallbackAimPos;
		if (ownerEntity instanceof PlayerEntity p) {
			Vec3d eye = p.getEyePos();
			Vec3d end = eye.add(p.getRotationVec(1.0F).multiply(95.0D));
			BlockHitResult hit = sw.raycast(new RaycastContext(
					eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p
			));
			if (hit.getType() == HitResult.Type.BLOCK) {
				mainTarget = hit.getPos();
			} else {
				mainTarget = eye.add(p.getRotationVec(1.0F).multiply(42.0D));
			}
		}

		List<LivingEntity> nearbyEnemies = sw.getEntitiesByClass(
				LivingEntity.class,
				new Box(mainTarget.x - 28.0D, mainTarget.y - 18.0D, mainTarget.z - 28.0D,
						mainTarget.x + 28.0D, mainTarget.y + 24.0D, mainTarget.z + 28.0D),
				e -> e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof ModNpcEntity)
						&& !(e instanceof TameableEntity tame && tame.isTamed())
		);

		sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 2.0F, 0.65F);
		sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.PLAYERS, 1.8F, 0.8F);

		for (int i = 0; i < this.trackedEntities.size(); i++) {
			Entity e = sw.getEntity(this.trackedEntities.get(i));
			if (!(e instanceof FallingBlockEntity boulder) || !boulder.isAlive()) {
				continue;
			}
			boulder.setNoGravity(false);
			Vec3d dest;
			if (!nearbyEnemies.isEmpty()) {
				LivingEntity victim = nearbyEnemies.get(i % nearbyEnemies.size());
				dest = victim.getPos().add(0.0D, victim.getHeight() * 0.5D, 0.0D);
			} else {
				double spreadAngle = (Math.PI * 2.0D * i) / Math.max(1, this.trackedEntities.size());
				double spreadR = (i % 5) * 1.9D;
				dest = mainTarget.add(Math.cos(spreadAngle) * spreadR, 0.0D, Math.sin(spreadAngle) * spreadR);
			}
			Vec3d dir = dest.subtract(boulder.getPos());
			double len = Math.max(0.001D, dir.length());
			boulder.setVelocity(dir.multiply(2.85D / len));
			boulder.velocityModified = true;
		}
	}

	private void detonateBoulderImpact(ServerWorld sw, Entity ownerEntity, Vec3d pos) {
		sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		sw.spawnParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.3D, pos.z, 8, 0.8D, 0.4D, 0.8D, 0.08D);
		sw.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.4F, 0.85F);

		Box blastBox = new Box(pos.x - 6.0D, pos.y - 5.0D, pos.z - 6.0D, pos.x + 6.0D, pos.y + 6.0D, pos.z + 6.0D);
		List<LivingEntity> victims = sw.getEntitiesByClass(
				LivingEntity.class,
				blastBox,
				e -> e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof ModNpcEntity)
						&& !(e instanceof TameableEntity tame && tame.isTamed())
		);
		for (LivingEntity victim : victims) {
			victim.hurtTime = 0;
			victim.timeUntilRegen = 0;
			float dmg = Math.max(160.0F, victim.getMaxHealth() * 0.45F);
			if (victim instanceof EnderDragonEntity dragon) {
				dragon.damagePart(dragon.head, sw.getDamageSources().explosion(this, ownerEntity), dmg);
			} else {
				victim.damage(sw.getDamageSources().explosion(this, ownerEntity), dmg);
			}
		}
	}

	private void tickGravityInversion(ServerWorld sw) {
		Entity ownerEntity = this.ownerUuid != null ? sw.getEntity(this.ownerUuid) : null;
		if (this.trackedEntities.isEmpty()) {
			this.discard();
			return;
		}

		if (this.age <= 50) {
			// 2.5 секунды: все враги плавно взмывают вверх в астральной невесомости!
			for (UUID id : this.trackedEntities) {
				Entity e = sw.getEntity(id);
				if (!(e instanceof LivingEntity liv) || !liv.isAlive()) {
					continue;
				}
				liv.setNoGravity(true);
				double vy = this.age < 36 ? 0.52D : 0.04D * Math.sin(this.age * 0.4D);
				liv.setVelocity(liv.getVelocity().x * 0.8D, vy, liv.getVelocity().z * 0.8D);
				liv.velocityModified = true;
				if (this.age % 4 == 0) {
					sw.spawnParticles(ParticleTypes.END_ROD, liv.getX(), liv.getY() + 0.3D, liv.getZ(), 2, 0.3D, 0.3D, 0.3D, 0.02D);
					sw.spawnParticles(ParticleTypes.PORTAL, liv.getX(), liv.getBodyY(0.5D), liv.getZ(), 4, 0.35D, 0.4D, 0.35D, 0.15D);
				}
			}
			if (this.age == 50) {
				// РЕЗКИЙ ГРАВИТАЦИОННЫЙ УДАР ВНИЗ!
				sw.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.5F, 0.7F);
				for (UUID id : this.trackedEntities) {
					Entity e = sw.getEntity(id);
					if (e instanceof LivingEntity liv && liv.isAlive()) {
						liv.setNoGravity(false);
						liv.setVelocity(0.0D, -4.5D, 0.0D);
						liv.velocityModified = true;
					}
				}
			}
		} else if (this.age <= 72) {
			boolean anyAirborne = false;
			for (int i = 0; i < this.trackedEntities.size(); i++) {
				UUID id = this.trackedEntities.get(i);
				if (id == null) {
					continue;
				}
				Entity e = sw.getEntity(id);
				if (!(e instanceof LivingEntity liv) || !liv.isAlive()) {
					this.trackedEntities.set(i, null);
					continue;
				}
				if (liv.isOnGround() || this.age == 72) {
					liv.setNoGravity(false);
					liv.removeCommandTag(TAG_ASTRAL_LIFTED);
					liv.fallDistance = 0.0F;
					liv.hurtTime = 0;
					liv.timeUntilRegen = 0;
					float slamDmg = Math.max(400.0F, liv.getMaxHealth() * 0.85F);
					if (liv instanceof EnderDragonEntity dragon) {
						dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), slamDmg);
					} else {
						liv.damage(sw.getDamageSources().outOfWorld(), slamDmg);
					}
					sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, liv.getX(), liv.getY(), liv.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
					sw.spawnParticles(ParticleTypes.SONIC_BOOM, liv.getX(), liv.getY() + 0.6D, liv.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
					sw.playSound(null, liv.getX(), liv.getY(), liv.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5F, 0.75F);
					this.trackedEntities.set(i, null);
				} else {
					anyAirborne = true;
					liv.setVelocity(0.0D, -4.5D, 0.0D);
					liv.velocityModified = true;
				}
			}
			if (!anyAirborne || this.age >= 72) {
				this.discard();
			}
		} else {
			cleanupTags(sw);
			this.discard();
		}
	}

	private void cleanupTags(ServerWorld sw) {
		for (UUID id : this.trackedEntities) {
			if (id == null) {
				continue;
			}
			Entity e = sw.getEntity(id);
			if (e != null) {
				e.setNoGravity(false);
				e.removeCommandTag(TAG_ASTRAL_LIFTED);
				e.removeCommandTag(TAG_CREATIVE_BOULDER);
			}
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.cataclysmType = nbt.getInt("CataclysmType");
		if (nbt.containsUuid("Owner")) {
			this.ownerUuid = nbt.getUuid("Owner");
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("CataclysmType", this.cataclysmType);
		if (this.ownerUuid != null) {
			nbt.putUuid("Owner", this.ownerUuid);
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return false;
	}
}
