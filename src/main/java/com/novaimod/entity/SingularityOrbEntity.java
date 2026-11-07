package com.novaimod.entity;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Креативная сущность «Сфера Сингулярности» (объёмная Чёрная дыра без ограничений, v3.7.0):
 * - Затягивает в гравитационную воронку всех мобов, боссов, снаряды и предметы в радиусе 40 блоков.
 * - Вырывает блоки ландшафта вокруг себя, закручивает их в аккреционную воронку и бесследно поглощает в горизонте событий!
 * - В финале схлопывается мощнейшим взрывом сверхновой.
 */
public class SingularityOrbEntity extends ThrownItemEntity {
	public static final String TAG_SINGULARITY_DEBRIS = "novai_singularity_debris";
	private static final double PULL_RADIUS = 40.0D;
	private static final double BLOCK_SUCTION_RADIUS = 13.5D;
	private static final int MAX_ACTIVE_VISUAL_DEBRIS = 34;
	private static final int TOTAL_LIFETIME = 140;

	@Nullable
	private UUID casterUuid;
	private boolean breakBlocks = true;

	public SingularityOrbEntity(EntityType<? extends SingularityOrbEntity> entityType, World world) {
		super(entityType, world);
		this.setNoGravity(true);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	public SingularityOrbEntity(World world, ServerPlayerEntity caster, boolean breakBlocks) {
		super(ModEntities.SINGULARITY_ORB_ENTITY, caster, world);
		this.casterUuid = caster.getUuid();
		this.breakBlocks = breakBlocks;
		this.setNoGravity(true);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 256.0D * 256.0D;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SINGULARITY_ORB;
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		this.setVelocity(this.getVelocity().multiply(0.15D));
	}

	@Override
	public void tick() {
		this.noClip = true;
		super.tick();

		if (this.getWorld().isClient() || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.age > 20) {
			this.setVelocity(this.getVelocity().multiply(0.75D));
			this.velocityModified = true;
		}

		Vec3d center = this.getPos().add(0.0D, 0.5D, 0.0D);

		sw.spawnParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 6, 0.4D, 0.4D, 0.4D, 0.02D);
		for (int i = 0; i < 6; i++) {
			double a = this.age * 0.4D + i * (Math.PI / 3.0D);
			double r = 2.4D + (i % 3) * 1.4D;
			double px = center.x + Math.cos(a) * r;
			double py = center.y + Math.sin(a * 2.0D) * 0.4D;
			double pz = center.z + Math.sin(a) * r;
			sw.spawnParticles(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.FLAME, px, py, pz, 1, 0.08D, 0.08D, 0.08D, 0.02D);
		}

		if (this.age % 14 == 1) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 1.6F, 0.5F);
		}

		// 1. Засасывание реальных блоков ландшафта и поглощение их внутри горизонта событий!
		if (this.breakBlocks && this.age >= 6 && this.age < TOTAL_LIFETIME - 6) {
			suckNearbyBlocksIntoVoid(sw, center);
		}

		// 2. Ведение засасываемых блоков по спирали прямо в ядро Чёрной дыры и их исчезновение в ней!
		Box debrisBox = Box.of(center, 36.0D, 36.0D, 36.0D);
		List<FallingBlockEntity> debrisList = sw.getEntitiesByClass(
				FallingBlockEntity.class,
				debrisBox,
				fb -> fb.isAlive() && fb.getCommandTags().contains(TAG_SINGULARITY_DEBRIS)
		);
		for (FallingBlockEntity fb : debrisList) {
			fb.noClip = true;
			fb.setNoGravity(true);
			fb.timeFalling = 2;
			fb.dropItem = false;

			Vec3d toCore = center.subtract(fb.getPos().add(0.0D, 0.5D, 0.0D));
			double d = toCore.length();
			if (d <= 1.85D) {
				// Блок пересёк горизонт событий и БЕСШОВНО ИСЧЕЗАЕТ внутри Чёрной дыры!
				sw.spawnParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 2, 0.25D, 0.25D, 0.25D, 0.01D);
				fb.discard();
				continue;
			}
			Vec3d rad = toCore.multiply(1.0D / d);
			Vec3d tang = new Vec3d(-rad.z, 0.0D, rad.x);
			double speed = MathHelper.clamp(0.45D + (14.0D - Math.min(14.0D, d)) * 0.085D, 0.45D, 1.65D);
			fb.setVelocity(rad.multiply(speed).add(tang.multiply(0.38D)));
			fb.velocityModified = true;
		}

		// 3. Затягивание мобов, боссов, предметов и снарядов
		Box box = Box.of(center, PULL_RADIUS * 2.0D, PULL_RADIUS * 2.0D, PULL_RADIUS * 2.0D);
		List<Entity> targets = sw.getOtherEntities(this, box, e ->
				e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof SingularityOrbEntity)
						&& !(e instanceof InfinityEyeEntity)
						&& !(e instanceof CelestialOrbEntity)
						&& (e instanceof MobEntity || e instanceof ItemEntity || e instanceof ProjectileEntity)
		);

		for (Entity e : targets) {
			Vec3d toCenter = center.subtract(e.getPos().add(0.0D, e.getHeight() * 0.4D, 0.0D));
			double dist = toCenter.length();
			if (dist > PULL_RADIUS || dist < 1.0E-3D) {
				continue;
			}

			// Выброшенные предметы (кроме неуязвимых наград) тоже исчезают внутри горизонта событий!
			if (e instanceof ItemEntity itemEnt && !itemEnt.isInvulnerable() && dist < 1.85D) {
				itemEnt.discard();
				continue;
			}

			Vec3d radial = toCenter.normalize();
			Vec3d tangent = new Vec3d(-radial.z, 0.0D, radial.x);
			double pullStrength = 0.25D + (1.0D - dist / PULL_RADIUS) * 0.72D;
			Vec3d desiredVel = radial.multiply(pullStrength).add(tangent.multiply(0.24D));

			if (e instanceof EnderDragonEntity dragon) {
				dragon.setPosition(dragon.getX() + desiredVel.x * 0.65D, dragon.getY() + desiredVel.y * 0.65D, dragon.getZ() + desiredVel.z * 0.65D);
			} else {
				e.setVelocity(e.getVelocity().multiply(0.48D).add(desiredVel));
				e.velocityModified = true;
			}
			e.fallDistance = 0.0F;

			if (dist < 3.6D && this.age % 8 == 0 && e instanceof LivingEntity living && !(living instanceof ModNpcEntity)) {
				living.timeUntilRegen = 0;
				if (living instanceof EnderDragonEntity dragon) {
					dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), 20.0F);
				} else {
					living.damage(sw.getDamageSources().outOfWorld(), 20.0F);
				}
			}
		}

		if (this.age >= TOTAL_LIFETIME) {
			// Все блоки, ещё летящие в воронку в момент финального схлопывания, мгновенно исчезают в сингулярности!
			for (FallingBlockEntity fb : debrisList) {
				fb.discard();
			}

			sw.spawnParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0.0D, 0.0D, 0.0D, 0.0D);
			sw.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 5, 0.5D, 0.5D, 0.5D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 120, 1.0D, 1.0D, 1.0D, 0.85D);
			sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 130, 1.2D, 1.2D, 1.2D, 0.9D);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 2.0F, 0.75F);

			sw.createExplosion(
					this,
					center.x,
					center.y,
					center.z,
					8.0F,
					false,
					this.breakBlocks ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE
			);

			for (Entity e : targets) {
				if (e instanceof LivingEntity living && !(living instanceof ModNpcEntity)) {
					living.timeUntilRegen = 0;
					if (living instanceof EnderDragonEntity dragon) {
						dragon.damagePart(dragon.head, sw.getDamageSources().sonicBoom(this), 80.0F);
					} else {
						living.damage(sw.getDamageSources().sonicBoom(this), 120.0F);
					}
					Vec3d away = e.getPos().subtract(center);
					Vec3d push = away.lengthSquared() > 0.01D ? away.normalize().multiply(2.6D) : new Vec3d(0.0D, 1.5D, 0.0D);
					e.setVelocity(push.x, Math.max(0.95D, push.y + 0.75D), push.z);
					e.velocityModified = true;
				}
			}
			this.discard();
		}
	}

	private void suckNearbyBlocksIntoVoid(ServerWorld sw, Vec3d center) {
		Random rng = sw.getRandom();
		int currentVisualDebris = sw.getEntitiesByClass(
				FallingBlockEntity.class,
				Box.of(center, 36.0D, 36.0D, 36.0D),
				fb -> fb.isAlive() && fb.getCommandTags().contains(TAG_SINGULARITY_DEBRIS)
		).size();

		double maxR = Math.min(BLOCK_SUCTION_RADIUS, 3.5D + this.age * 0.11D);
		BlockPos.Mutable mPos = new BlockPos.Mutable();
		int rippedThisTick = 0;

		for (int attempt = 0; attempt < 26 && rippedThisTick < 4; attempt++) {
			double r = 1.5D + rng.nextDouble() * maxR;
			double theta = rng.nextDouble() * Math.PI * 2.0D;
			double phi = (rng.nextDouble() - 0.5D) * Math.PI;
			int bx = MathHelper.floor(center.x + Math.cos(theta) * Math.cos(phi) * r);
			int by = MathHelper.floor(center.y + Math.sin(phi) * r);
			int bz = MathHelper.floor(center.z + Math.sin(theta) * Math.cos(phi) * r);

			if (by <= sw.getBottomY() + 1 || by >= sw.getTopY()) {
				continue;
			}
			mPos.set(bx, by, bz);
			BlockState state = sw.getBlockState(mPos);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				continue;
			}
			if (state.getHardness(sw, mPos) < 0.0F
					|| state.isOf(Blocks.BEDROCK)
					|| state.isOf(Blocks.BARRIER)
					|| state.isOf(Blocks.END_PORTAL_FRAME)
					|| state.isOf(Blocks.END_PORTAL)
					|| state.isOf(Blocks.COMMAND_BLOCK)
					|| sw.getBlockEntity(mPos) != null) {
				continue;
			}

			// Вырываем блок из мира без выпадения дропа (он поглощается Чёрной дырой!)
			sw.setBlockState(mPos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			rippedThisTick++;

			if (currentVisualDebris < MAX_ACTIVE_VISUAL_DEBRIS) {
				BlockPos immutablePos = mPos.toImmutable();
				FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(sw, immutablePos, state);
				fb.dropItem = false;
				fb.setDestroyedOnLanding();
				fb.setNoGravity(true);
				fb.noClip = true;
				fb.addCommandTag(TerraCataclysmEntity.TAG_CREATIVE_BOULDER);
				fb.addCommandTag(TAG_SINGULARITY_DEBRIS);

				Vec3d toCore = center.subtract(Vec3d.ofCenter(immutablePos));
				double len = Math.max(0.01D, toCore.length());
				fb.setVelocity(toCore.multiply(0.55D / len));
				fb.velocityModified = true;
				currentVisualDebris++;
			}
		}
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.casterUuid != null) {
			nbt.putUuid("CasterUuid", this.casterUuid);
		}
		nbt.putBoolean("BreakBlocks", this.breakBlocks);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		if (nbt.containsUuid("CasterUuid")) {
			this.casterUuid = nbt.getUuid("CasterUuid");
		}
		if (nbt.contains("BreakBlocks")) {
			this.breakBlocks = nbt.getBoolean("BreakBlocks");
		}
	}
}
