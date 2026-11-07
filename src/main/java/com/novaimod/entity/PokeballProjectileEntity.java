package com.novaimod.entity;

import com.novaimod.item.PokeballItem;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;
import com.novaimod.world.PokeballDimensionManager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Летящий Покебол:
 * - Пустой Покебол при попадании в ЛЮБОЕ живое существо (кроме боссов Дракона/Визера) сохраняет его внутрь
 *   и возвращает заполненный Покебол!
 * - Пустой Покебол при попадании в ИГРОКА затягивает игрока внутрь Покебола — в уютное сферическое мини-пространство,
 *   обустроенное для жизни!
 * - Заполненный Покебол при приземлении выпускает пойманное существо или игрока в точке падения!
 */
public class PokeballProjectileEntity extends ThrownItemEntity {
	private boolean resolved = false;

	public PokeballProjectileEntity(EntityType<? extends PokeballProjectileEntity> entityType, World world) {
		super(entityType, world);
	}

	public PokeballProjectileEntity(World world, LivingEntity owner, ItemStack ballStack) {
		super(ModEntities.POKEBALL_PROJECTILE_ENTITY, owner, world);
		this.setItem(ballStack.copyWithCount(1));
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.POKEBALL;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld sw) {
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 1, 0.04D, 0.04D, 0.04D, 0.005D);
		}
	}

	@Override
	protected void onEntityHit(EntityHitResult entityHitResult) {
		super.onEntityHit(entityHitResult);
		if (resolved || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		ItemStack ball = this.getStack().copyWithCount(1);
		if (PokeballItem.isFilled(ball)) {
			// Если Покебол уже занят — выпускаем его содержимое рядом с местом попадания!
			resolved = true;
			releaseContentsAt(sw, this.getPos(), ball);
			this.discard();
			return;
		}

		Entity hit = entityHitResult.getEntity();
		if (!(hit instanceof LivingEntity living) || !living.isAlive()) {
			return;
		}

		// 1. ПОПАДАНИЕ В ИГРОКА -> ОТПРАВЛЯЕМ ЕГО В УЮТНОЕ МИНИ-ПРОСТРАНСТВО ВНУТРИ ПОКЕБОЛА!
		if (living instanceof ServerPlayerEntity targetPlayer) {
			if (targetPlayer.isSpectator()) {
				return;
			}
			resolved = true;
			int pocketId = PokeballDimensionManager.capturePlayerIntoPokeball(targetPlayer, 0);
			if (pocketId > 0) {
				PokeballItem.storePlayerCapture(ball, targetPlayer.getUuid(), targetPlayer.getName().getString(), pocketId);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.25F);
				sw.spawnParticles(ParticleTypes.FLASH, hit.getX(), hit.getBodyY(0.5D), hit.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
				sw.spawnParticles(ParticleTypes.END_ROD, hit.getX(), hit.getBodyY(0.5D), hit.getZ(), 26, 0.4D, 0.6D, 0.4D, 0.08D);

				Entity owner = this.getOwner();
				if (owner instanceof ServerPlayerEntity thrower && thrower != targetPlayer) {
					thrower.sendMessage(
							Text.literal("🔴 Покебол поймал игрока ").formatted(Formatting.RED, Formatting.BOLD)
									.append(Text.literal(targetPlayer.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD))
									.append(Text.literal("! Сейчас он живёт внутри вашего Покебола — бросьте Покебол на землю, чтобы выпустить его.").formatted(Formatting.WHITE)),
							false
					);
					giveOrDropBall(sw, thrower, hit.getPos(), ball);
				} else {
					// Если игрок попал сам в себя — оставляем неуязвимый Покебол на земле, чтобы его никто не сжёг!
					spawnIndestructibleBallDrop(sw, hit.getPos(), ball);
				}
			}
			this.discard();
			return;
		}

		// 2. ПОПАДАНИЕ В ЛЮБОЕ ЖИВОЕ СУЩЕСТВО (Кошкодевочка, житель, животное, моб, NPC и т.д.)
		if (living instanceof EnderDragonEntity || living instanceof WitherEntity) {
			if (this.getOwner() instanceof ServerPlayerEntity sp) {
				sp.sendMessage(Text.literal("🔴 Босс слишком огромен для стандартного Покебола!").formatted(Formatting.RED), true);
			}
			resolved = true;
			giveOrDropBall(sw, this.getOwner(), this.getPos(), ball);
			this.discard();
			return;
		}

		NbtCompound mobNbt = new NbtCompound();
		if (living.saveSelfNbt(mobNbt)) {
			resolved = true;
			mobNbt.remove("UUID");
			mobNbt.remove("Pos");
			mobNbt.remove("Motion");
			mobNbt.remove("FallDistance");
			mobNbt.remove("HurtTime");
			mobNbt.remove("DeathTime");

			String displayName = living.getDisplayName().getString();
			int hp = Math.max(1, Math.round(living.getHealth()));
			int maxHp = Math.max(1, Math.round(living.getMaxHealth()));
			PokeballItem.storeMobCapture(ball, displayName, hp, maxHp, mobNbt);

			Vec3d capturePos = living.getPos();
			sw.playSound(null, living.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.25F, 1.35F);
			sw.spawnParticles(ParticleTypes.FLASH, capturePos.x, capturePos.y + living.getHeight() * 0.5D, capturePos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, capturePos.x, capturePos.y + living.getHeight() * 0.5D, capturePos.z, 24, 0.35D, 0.5D, 0.35D, 0.08D);
			sw.spawnParticles(ParticleTypes.POOF, capturePos.x, capturePos.y + 0.4D, capturePos.z, 12, 0.3D, 0.3D, 0.3D, 0.03D);

			living.discard();

			if (this.getOwner() instanceof ServerPlayerEntity sp) {
				sp.sendMessage(
						Text.literal("🔴 Покебол поймал: ").formatted(Formatting.RED, Formatting.BOLD)
								.append(Text.literal(displayName + " (" + hp + "/" + maxHp + " ХП)").formatted(Formatting.GREEN, Formatting.BOLD)),
						true
				);
			}
			giveOrDropBall(sw, this.getOwner(), capturePos, ball);
			this.discard();
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult blockHitResult) {
		super.onBlockHit(blockHitResult);
		if (resolved || !(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}
		resolved = true;

		ItemStack ball = this.getStack().copyWithCount(1);
		BlockPos hitPos = blockHitResult.getBlockPos();
		Direction side = blockHitResult.getSide();
		BlockPos spawnBlock = sw.getBlockState(hitPos).getCollisionShape(sw, hitPos).isEmpty() ? hitPos : hitPos.offset(side);
		Vec3d spawnVec = new Vec3d(spawnBlock.getX() + 0.5D, spawnBlock.getY() + 0.05D, spawnBlock.getZ() + 0.5D);

		if (PokeballItem.isFilled(ball)) {
			releaseContentsAt(sw, spawnVec, ball);
		} else {
			// Пустой промахнувшийся Покебол возвращается владельцу (если не в Креативе)
			Entity owner = this.getOwner();
			if (!(owner instanceof PlayerEntity p && p.getAbilities().creativeMode)) {
				giveOrDropBall(sw, owner, spawnVec, ball);
			}
		}
		this.discard();
	}

	private void releaseContentsAt(ServerWorld sw, Vec3d pos, ItemStack ball) {
		String type = PokeballItem.getCaptureType(ball);
		Entity owner = this.getOwner();

		if ("player".equals(type)) {
			int pocketId = PokeballItem.getPocketId(ball);
			boolean released = PokeballDimensionManager.releasePlayerAt(sw.getServer(), pocketId, sw, pos);
			PokeballItem.clearPokeballData(ball);
			if (!released && owner instanceof ServerPlayerEntity sp) {
				sp.sendMessage(Text.literal("⚪ Игрок уже покинул этот Покебол!").formatted(Formatting.YELLOW), true);
			}
			if (!(owner instanceof PlayerEntity p && p.getAbilities().creativeMode)) {
				giveOrDropBall(sw, owner, pos, ball);
			}
			return;
		}

		if ("mob".equals(type)) {
			NbtCompound mobNbt = PokeballItem.getMobNbt(ball);
			String name = PokeballItem.getCapturedName(ball);
			if (mobNbt != null && !mobNbt.isEmpty()) {
				float yaw = owner != null ? owner.getYaw() + 180.0F : sw.random.nextFloat() * 360.0F;
				Entity restored = EntityType.loadEntityWithPassengers(mobNbt, sw, e -> {
					e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
					return e;
				});
				if (restored != null) {
					sw.spawnNewEntityAndPassengers(restored);
					sw.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.3F);
					sw.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.7D, pos.z, 24, 0.35D, 0.45D, 0.35D, 0.07D);
					if (owner instanceof ServerPlayerEntity sp) {
						sp.sendMessage(
								Text.literal("⚪ Вперёд, ").formatted(Formatting.WHITE, Formatting.BOLD)
										.append(Text.literal(name + "!").formatted(Formatting.AQUA, Formatting.BOLD)),
								true
						);
					}
				}
			}
			PokeballItem.clearPokeballData(ball);
			if (!(owner instanceof PlayerEntity p && p.getAbilities().creativeMode)) {
				giveOrDropBall(sw, owner, pos, ball);
			}
		}
	}

	private static void giveOrDropBall(ServerWorld sw, Entity owner, Vec3d fallbackPos, ItemStack ball) {
		if (owner instanceof ServerPlayerEntity sp && sp.isAlive() && !PokeballDimensionManager.isPlayerInsideAnyPokeball(sp)) {
			if (sp.getInventory().insertStack(ball)) {
				return;
			}
		}
		spawnIndestructibleBallDrop(sw, fallbackPos, ball);
	}

	public static void spawnIndestructibleBallDrop(ServerWorld sw, Vec3d pos, ItemStack ball) {
		ItemEntity drop = new ItemEntity(sw, pos.x, pos.y + 0.25D, pos.z, ball);
		drop.setToDefaultPickupDelay();
		drop.setInvulnerable(true);
		drop.setCovetedItem();
		sw.spawnEntity(drop);
	}
}
