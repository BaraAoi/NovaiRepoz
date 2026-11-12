package com.novaimod.entity;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * 3D-массив золотых порталов «Gate of Babylon»:
 * - Сковывает сильнейшего врага/босса перед игроком золотыми «Цепями Небес (Энкиду)»!
 * - Наносит бронебойный урон, растущий от числа реликвий в вашей 54-слотовой Сокровищнице Вавилона!
 * - В режиме Короны 360° обращает поверженных монстров в золотую дань (золотые слитки и золотые яблоки)!
 */
public class BabylonTreasuryEntity extends Entity {
	public static final int OPEN_WARMUP_TICKS = 16;
	public static final int TOTAL_TICKS = 130;

	private static final TrackedData<Optional<UUID>> OWNER_UUID = DataTracker.registerData(
			BabylonTreasuryEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID
	);
	private static final TrackedData<Boolean> CROWN_MODE = DataTracker.registerData(
			BabylonTreasuryEntity.class, TrackedDataHandlerRegistry.BOOLEAN
	);
	private static final TrackedData<Float> OWNER_YAW = DataTracker.registerData(
			BabylonTreasuryEntity.class, TrackedDataHandlerRegistry.FLOAT
	);

	private int vaultRelicBonus = 0;

	public BabylonTreasuryEntity(EntityType<? extends BabylonTreasuryEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(OWNER_UUID, Optional.empty());
		builder.add(CROWN_MODE, false);
		builder.add(OWNER_YAW, 0.0F);
	}

	public void setOwner(PlayerEntity player) {
		this.dataTracker.set(OWNER_UUID, Optional.of(player.getUuid()));
		this.dataTracker.set(OWNER_YAW, player.getYaw());
	}

	public void setCrownMode(boolean crown) {
		this.dataTracker.set(CROWN_MODE, crown);
	}

	public void setVaultRelicBonus(int count) {
		this.vaultRelicBonus = Math.max(0, count);
	}

	public boolean isCrownMode() {
		return this.dataTracker.get(CROWN_MODE);
	}

	public float getOwnerYaw() {
		return this.dataTracker.get(OWNER_YAW);
	}

	public int getPortalCount() {
		return isCrownMode() ? 32 : 18;
	}

	public Vec3d computePortalOffset(int index, float yawDeg) {
		if (isCrownMode()) {
			int ring = index < 18 ? 0 : 1;
			int idxInRing = ring == 0 ? index : (index - 18);
			int countInRing = ring == 0 ? 18 : 14;
			double angle = (Math.PI * 2.0D * idxInRing) / countInRing + ring * 0.22D;
			double radius = ring == 0 ? 6.2D : 4.2D;
			double y = ring == 0 ? 4.2D : 6.6D;
			return new Vec3d(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
		} else {
			int row = index / 6;
			int col = index % 6;
			double norm = (col - 2.5D) / 2.5D;
			double spanX = norm * (3.6D + row * 1.15D);
			double y = 1.6D + row * 1.45D + (1.0D - norm * norm) * 0.65D;
			double backZ = -1.65D - row * 0.45D;

			float rad = yawDeg * MathHelper.RADIANS_PER_DEGREE;
			double forwardX = -MathHelper.sin(rad);
			double forwardZ = MathHelper.cos(rad);
			double rightX = forwardZ;
			double rightZ = -forwardX;

			double wx = rightX * spanX + forwardX * backZ;
			double wz = rightZ * spanX + forwardZ * backZ;
			return new Vec3d(wx, y, wz);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		ServerPlayerEntity owner = this.dataTracker.get(OWNER_UUID)
				.map(id -> sw.getServer().getPlayerManager().getPlayer(id))
				.orElse(null);

		if (owner != null && owner.getServerWorld() == sw) {
			this.setPosition(
					MathHelper.lerp(0.45D, this.getX(), owner.getX()),
					MathHelper.lerp(0.45D, this.getY(), owner.getY()),
					MathHelper.lerp(0.45D, this.getZ(), owner.getZ())
			);
			this.dataTracker.set(OWNER_YAW, owner.getYaw());
		}

		// «Цепи Энкиду»: сковываем сильнейшего врага/босса по направлению взгляда золотыми цепями!
		if (!isCrownMode() && owner != null && this.age % 5 == 0 && this.age < TOTAL_TICKS - 10) {
			bindStrongestTargetWithEnkiduChains(sw, owner);
		}

		if (this.age < OPEN_WARMUP_TICKS && this.age % 3 == 0) {
			float pitch = 0.9F + (this.age / (float) OPEN_WARMUP_TICKS) * 0.6F;
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.4F, pitch);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 0.55F, 1.5F + pitch * 0.25F);
		}

		if (this.age >= OPEN_WARMUP_TICKS && this.age < TOTAL_TICKS - 10 && this.age % 2 == 0) {
			int portalCount = getPortalCount();
			int shotIdx = ((this.age - OPEN_WARMUP_TICKS) / 2) % portalCount;
			Vec3d origin = this.getPos().add(computePortalOffset(shotIdx, getOwnerYaw()));
			Vec3d targetPos = pickTargetPoint(sw, owner, shotIdx);
			fireGoldenNobleWeapon(sw, owner, origin, targetPos);
		}

		if (this.age >= TOTAL_TICKS) {
			this.discard();
		}
	}

	private void bindStrongestTargetWithEnkiduChains(ServerWorld sw, ServerPlayerEntity owner) {
		Vec3d center = owner.getEyePos().add(owner.getRotationVec(1.0F).multiply(14.0D));
		List<LivingEntity> candidates = sw.getEntitiesByClass(
				LivingEntity.class,
				new Box(center, center).expand(16.0D),
				e -> e.isAlive() && e != owner && !(e instanceof PlayerEntity)
						&& !(e instanceof LuceEntity) && !(e instanceof HolyOrderEntity) && !(e instanceof CatgirlEntity)
						&& !(e instanceof TameableEntity t && t.isTamed())
						&& (e instanceof Monster || e instanceof WhiteCapEntity || e.getMaxHealth() >= 50.0F)
		);
		if (candidates.isEmpty()) {
			return;
		}
		candidates.sort(Comparator.comparingDouble(LivingEntity::getMaxHealth).reversed());
		LivingEntity bound = candidates.get(0);
		bound.setVelocity(Vec3d.ZERO);
		bound.velocityModified = true;
		bound.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 9, true, false, false));
		bound.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 25, 0, true, false, false));
		// Крестовые золотые цепи вокруг скованной цели
		for (int c = 0; c < 8; c++) {
			double a = c * (Math.PI / 4.0D) + this.age * 0.15D;
			sw.spawnParticles(ParticleTypes.WAX_ON,
					bound.getX() + Math.cos(a) * 1.4D,
					bound.getBodyY(0.5D),
					bound.getZ() + Math.sin(a) * 1.4D,
					2, 0.05D, 0.3D, 0.05D, 0.01D);
		}
		if (this.age == 10) {
			sw.playSound(null, bound.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.MASTER, 1.8F, 0.85F);
		}
	}

	private Vec3d pickTargetPoint(ServerWorld sw, ServerPlayerEntity owner, int shotIdx) {
		if (isCrownMode()) {
			List<LivingEntity> hostiles = sw.getEntitiesByClass(
					LivingEntity.class,
					this.getBoundingBox().expand(36.0D),
					e -> e.isAlive() && (e instanceof Monster || e instanceof WhiteCapEntity)
			);
			if (!hostiles.isEmpty()) {
				LivingEntity victim = hostiles.get(shotIdx % hostiles.size());
				return victim.getPos().add(0.0D, victim.getHeight() * 0.5D, 0.0D);
			}
			double a = (shotIdx * 0.55D) + this.age * 0.09D;
			double r = 7.0D + (shotIdx % 5) * 3.2D;
			return this.getPos().add(Math.cos(a) * r, 0.2D, Math.sin(a) * r);
		}

		if (owner != null) {
			// Автонаведение на живую враждебную цель в конусе взгляда, если есть!
			Vec3d eye = owner.getCameraPosVec(1.0F);
			Vec3d look = owner.getRotationVec(1.0F);
			Vec3d reach = eye.add(look.multiply(54.0D));
			HitResult hit = sw.raycast(new RaycastContext(
					eye, reach, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, owner
			));
			Vec3d base = hit.getType() != HitResult.Type.MISS ? hit.getPos() : eye.add(look.multiply(28.0D));
			List<LivingEntity> nearCrosshair = sw.getEntitiesByClass(
					LivingEntity.class,
					new Box(base, base).expand(5.5D),
					e -> e.isAlive() && (e instanceof Monster || e instanceof WhiteCapEntity)
			);
			if (!nearCrosshair.isEmpty()) {
				LivingEntity locked = nearCrosshair.get(shotIdx % nearCrosshair.size());
				return locked.getPos().add(0.0D, locked.getHeight() * 0.55D, 0.0D);
			}
			double spreadX = (sw.random.nextDouble() - 0.5D) * 2.4D;
			double spreadZ = (sw.random.nextDouble() - 0.5D) * 2.4D;
			return base.add(spreadX, 0.15D, spreadZ);
		}
		return this.getPos().add(0.0D, 0.0D, 12.0D);
	}

	private void fireGoldenNobleWeapon(ServerWorld sw, ServerPlayerEntity owner, Vec3d from, Vec3d to) {
		sw.playSound(null, BlockPos.ofFloored(from.x, from.y, from.z), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.MASTER, 0.85F, 1.45F + sw.random.nextFloat() * 0.35F);

		Vec3d diff = to.subtract(from);
		double dist = Math.max(0.5D, diff.length());
		Vec3d dir = diff.multiply(1.0D / dist);

		int steps = Math.min(36, (int) (dist * 1.5D));
		for (int s = 0; s <= steps; s++) {
			double f = (double) s / (double) Math.max(1, steps);
			Vec3d p = from.add(diff.multiply(f));
			sw.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
			if (s % 2 == 0) {
				sw.spawnParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 1, 0.03D, 0.03D, 0.03D, 0.0D);
			}
		}

		sw.spawnParticles(ParticleTypes.FLASH, to.x, to.y + 0.3D, to.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		sw.spawnParticles(ParticleTypes.END_ROD, to.x, to.y + 0.3D, to.z, 8, 0.4D, 0.6D, 0.4D, 0.10D);

		float spearDamage = 55.0F + vaultRelicBonus * 1.6F;
		Box hitBox = new Box(to.x - 2.8D, to.y - 2.4D, to.z - 2.8D, to.x + 2.8D, to.y + 3.0D, to.z + 2.8D);
		for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, hitBox, e -> e.isAlive() && e != owner)) {
			if (target instanceof PlayerEntity || target instanceof LuceEntity || target instanceof HolyOrderEntity
					|| target instanceof CatgirlEntity || (target instanceof TameableEntity t && t.isTamed())) {
				continue;
			}
			boolean wasAlive = target.isAlive();
			target.damage(sw.getDamageSources().magic(), spearDamage);
			target.addVelocity(dir.x * 0.35D, 0.2D, dir.z * 0.35D);

			// В режиме Короны поверженные монстры обращаются в Золотую Дань Сокровищницы!
			if (wasAlive && !target.isAlive() && isCrownMode()) {
				ItemStack goldDrop = sw.random.nextInt(12) == 0
						? new ItemStack(Items.GOLDEN_APPLE, 1)
						: new ItemStack(Items.GOLD_INGOT, 1 + sw.random.nextInt(3));
				ItemEntity drop = new ItemEntity(sw, target.getX(), target.getY() + 0.4D, target.getZ(), goldDrop);
				drop.setGlowing(true);
				sw.spawnEntity(drop);
			}
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 320.0D * 320.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.containsUuid("Owner")) {
			this.dataTracker.set(OWNER_UUID, Optional.of(nbt.getUuid("Owner")));
		}
		setCrownMode(nbt.getBoolean("Crown"));
		this.dataTracker.set(OWNER_YAW, nbt.getFloat("OwnerYaw"));
		this.vaultRelicBonus = nbt.getInt("RelicBonus");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		this.dataTracker.get(OWNER_UUID).ifPresent(id -> nbt.putUuid("Owner", id));
		nbt.putBoolean("Crown", isCrownMode());
		nbt.putFloat("OwnerYaw", getOwnerYaw());
		nbt.putInt("RelicBonus", vaultRelicBonus);
	}
}
