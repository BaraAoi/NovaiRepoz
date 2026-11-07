package com.novaimod.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.novaimod.entity.InfinityEyeEntity;
import com.novaimod.entity.InfinityPearlEntity;
import com.novaimod.entity.SingularityOrbEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Управляет куполами остановки времени от креативного «Хроно-Скипетра»:
 * замораживает в воздухе стрелы, снаряды и всех мобов в радиусе 28 блоков на 8 секунд,
 * сохраняя их точный вектор скорости и возвращая движение после снятия купола!
 */
public final class TimeStopManager {
	public static final double DOME_RADIUS = 28.0D;
	public static final int DOME_DURATION_TICKS = 160; // 8 секунд

	private static final List<TimeDome> ACTIVE_DOMES = new ArrayList<>();
	private static final Map<UUID, FrozenProjectileData> FROZEN_PROJECTILES = new HashMap<>();

	private record FrozenProjectileData(
			RegistryKey<World> worldKey,
			Vec3d frozenPos,
			Vec3d savedVelocity,
			float yaw,
			float pitch,
			boolean wasNoGravity
	) {
	}

	private static final class TimeDome {
		final UUID ownerUuid;
		final RegistryKey<World> worldKey;
		final Vec3d center;
		int ticksLeft;

		TimeDome(UUID ownerUuid, RegistryKey<World> worldKey, Vec3d center, int ticksLeft) {
			this.ownerUuid = ownerUuid;
			this.worldKey = worldKey;
			this.center = center;
			this.ticksLeft = ticksLeft;
		}
	}

	private TimeStopManager() {
	}

	public static boolean hasActiveDomeOwnedBy(UUID ownerUuid) {
		for (TimeDome dome : ACTIVE_DOMES) {
			if (dome.ownerUuid.equals(ownerUuid) && dome.ticksLeft > 0) {
				return true;
			}
		}
		return false;
	}

	public static void activateDome(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		Vec3d center = player.getPos();

		ACTIVE_DOMES.removeIf(d -> d.ownerUuid.equals(player.getUuid()));
		ACTIVE_DOMES.add(new TimeDome(player.getUuid(), world.getRegistryKey(), center, DOME_DURATION_TICKS));

		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.3F, 0.55F);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.4F, 0.7F);
		spawnChronoRing(world, center, DOME_RADIUS, 72);

		player.sendMessage(
				Text.literal("⏳ ВРЕМЯ ОСТАНОВЛЕНО НА 8 СЕКУНД! (Shift + ПКМ — запустить ход времени)")
						.formatted(Formatting.AQUA, Formatting.BOLD),
				true
		);
	}

	public static void releaseOwnedDomesEarly(ServerPlayerEntity player) {
		Iterator<TimeDome> it = ACTIVE_DOMES.iterator();
		boolean released = false;
		while (it.hasNext()) {
			TimeDome d = it.next();
			if (d.ownerUuid.equals(player.getUuid())) {
				ServerWorld w = player.getServer().getWorld(d.worldKey);
				if (w != null) {
					unfreezeEntitiesInDome(w, d);
					w.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.55F);
				}
				it.remove();
				released = true;
			}
		}
		if (released) {
			player.sendMessage(Text.literal("⏳ Ход времени восстановлен!").formatted(Formatting.GOLD, Formatting.BOLD), true);
		}
	}

	public static boolean isEntityFrozen(Entity entity) {
		if (ACTIVE_DOMES.isEmpty() || entity.getWorld().isClient()) {
			return false;
		}
		if (entity instanceof InfinityEyeEntity
				|| entity instanceof InfinityPearlEntity
				|| entity instanceof SingularityOrbEntity) {
			return false;
		}
		RegistryKey<World> wk = entity.getWorld().getRegistryKey();
		for (TimeDome dome : ACTIVE_DOMES) {
			if (dome.ticksLeft > 0 && dome.worldKey == wk) {
				if (entity.getPos().squaredDistanceTo(dome.center) <= DOME_RADIUS * DOME_RADIUS) {
					return true;
				}
			}
		}
		return false;
	}

	public static void tickServer(MinecraftServer server) {
		if (ACTIVE_DOMES.isEmpty()) {
			return;
		}

		Iterator<TimeDome> it = ACTIVE_DOMES.iterator();
		while (it.hasNext()) {
			TimeDome dome = it.next();
			ServerWorld world = server.getWorld(dome.worldKey);
			if (world == null) {
				it.remove();
				continue;
			}

			dome.ticksLeft--;
			if (dome.ticksLeft <= 0) {
				unfreezeEntitiesInDome(world, dome);
				world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(dome.center), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.25F, 1.45F);
				spawnChronoRing(world, dome.center, DOME_RADIUS * 0.6D, 48);
				it.remove();
				continue;
			}

			// Атмосферное тиканье часов и мерцание границы хроно-купола
			if (dome.ticksLeft % 20 == 0) {
				float pitch = (dome.ticksLeft / 20) % 2 == 0 ? 1.65F : 1.35F;
				world.playSound(null, net.minecraft.util.math.BlockPos.ofFloored(dome.center), SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.55F, pitch);
				spawnChronoRing(world, dome.center, DOME_RADIUS, 36);
			}

			Box domeBox = Box.of(dome.center, DOME_RADIUS * 2.0D, DOME_RADIUS * 2.0D, DOME_RADIUS * 2.0D);

			// 1. Удерживаем мобов в неподвижном стазисе
			for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, domeBox, Entity::isAlive)) {
				if (mob.getPos().squaredDistanceTo(dome.center) > DOME_RADIUS * DOME_RADIUS) {
					continue;
				}
				mob.getNavigation().stop();
				mob.setVelocity(Vec3d.ZERO);
				mob.velocityModified = true;
				if (dome.ticksLeft % 15 == 0) {
					world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getBodyY(0.6D), mob.getZ(), 2, 0.2D, 0.3D, 0.2D, 0.0D);
				}
			}

			// 2. Удерживаем летящие снаряды застывшими прямо в воздухе с сохранением их исходного импульса!
			for (ProjectileEntity proj : world.getEntitiesByClass(ProjectileEntity.class, domeBox, Entity::isAlive)) {
				if (proj instanceof InfinityEyeEntity || proj instanceof InfinityPearlEntity || proj instanceof SingularityOrbEntity) {
					continue;
				}
				if (proj.getPos().squaredDistanceTo(dome.center) > DOME_RADIUS * DOME_RADIUS) {
					continue;
				}
				FrozenProjectileData saved = FROZEN_PROJECTILES.get(proj.getUuid());
				if (saved == null) {
					Vec3d curVel = proj.getVelocity();
					if (curVel.lengthSquared() < 1.0E-4D) {
						continue;
					}
					saved = new FrozenProjectileData(
							dome.worldKey,
							proj.getPos(),
							curVel,
							proj.getYaw(),
							proj.getPitch(),
							proj.hasNoGravity()
					);
					FROZEN_PROJECTILES.put(proj.getUuid(), saved);
				}
				proj.setNoGravity(true);
				proj.setVelocity(Vec3d.ZERO);
				proj.setPosition(saved.frozenPos);
				proj.setYaw(saved.yaw);
				proj.setPitch(saved.pitch);
				proj.velocityModified = true;
			}
		}
	}

	private static void unfreezeEntitiesInDome(ServerWorld world, TimeDome dome) {
		Box domeBox = Box.of(dome.center, DOME_RADIUS * 2.2D, DOME_RADIUS * 2.2D, DOME_RADIUS * 2.2D);
		for (ProjectileEntity proj : world.getEntitiesByClass(ProjectileEntity.class, domeBox, Entity::isAlive)) {
			FrozenProjectileData saved = FROZEN_PROJECTILES.remove(proj.getUuid());
			if (saved != null) {
				proj.setNoGravity(saved.wasNoGravity);
				proj.setVelocity(saved.savedVelocity);
				proj.velocityModified = true;
			}
		}
	}

	private static void spawnChronoRing(ServerWorld world, Vec3d center, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double ang = (Math.PI * 2.0D * i) / points;
			double px = center.x + Math.cos(ang) * radius;
			double pz = center.z + Math.sin(ang) * radius;
			world.spawnParticles(ParticleTypes.END_ROD, px, center.y + 1.1D, pz, 1, 0.05D, 0.25D, 0.05D, 0.01D);
		}
	}
}
