package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.entity.TerraCataclysmEntity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * КРЕАТИВНЫЙ АРТЕФАКТ: «Катаклизм Геоманта» (v3.6.0).
 * - ПКМ (без кулдауна!): вырывает из земли 28 реальных глыб породы и магмы, поднимает их кружащимся
 *   огненным кольцом в небе и швыряет всю гору камней прямо во врагов или в точку взгляда!
 * - Shift + ПКМ: «Тектонический Разлом» — сейсмическая волна лавовых гейзеров на 45 блоков вперёд,
 *   подбрасывающая всех врагов на 20 блоков в воздух!
 */
public class TerraHeaverItem extends Item {
	public TerraHeaverItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (user.isSneaking()) {
				triggerTectonicFaultLine(sw, sp);
			} else {
				Vec3d eye = sp.getEyePos();
				Vec3d end = eye.add(sp.getRotationVec(1.0F).multiply(90.0D));
				BlockHitResult hit = sw.raycast(new RaycastContext(
						eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, sp
				));
				Vec3d aim = hit.getType() == HitResult.Type.BLOCK
						? hit.getPos()
						: eye.add(sp.getRotationVec(1.0F).multiply(38.0D));

				TerraCataclysmEntity swarm = TerraCataclysmEntity.startBoulderSwarm(sw, sp, aim);
				sw.spawnEntity(swarm);
				sp.sendMessage(
						Text.literal("⛰ КАТАКЛИЗМ ГЕОМАНТА: недра земли подняты в небо! ⛰")
								.formatted(Formatting.GOLD, Formatting.BOLD),
						true
				);
			}
			user.swingHand(hand, true);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private void triggerTectonicFaultLine(ServerWorld sw, ServerPlayerEntity sp) {
		Vec3d dir = new Vec3d(sp.getRotationVec(1.0F).x, 0.0D, sp.getRotationVec(1.0F).z);
		if (dir.lengthSquared() < 1.0E-4D) {
			dir = new Vec3d(0.0D, 0.0D, 1.0D);
		} else {
			dir = dir.normalize();
		}

		sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0F, 0.55F);
		sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 2.0F, 0.75F);

		for (int step = 3; step <= 45; step += 2) {
			double px = sp.getX() + dir.x * step;
			double pz = sp.getZ() + dir.z * step;
			int topY = sw.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(px, sp.getY(), pz).getX(), BlockPos.ofFloored(px, sp.getY(), pz).getZ());
			double py = Math.abs(topY - sp.getY()) <= 18.0D ? topY : sp.getY();

			EvokerFangsEntity fangs = new EvokerFangsEntity(sw, px, py, pz, sp.getYaw(), step / 2, sp);
			sw.spawnEntity(fangs);

			sw.spawnParticles(ParticleTypes.LAVA, px, py + 0.4D, pz, 8, 1.1D, 0.6D, 1.1D, 0.1D);
			sw.spawnParticles(ParticleTypes.FLAME, px, py + 0.5D, pz, 14, 1.2D, 1.2D, 1.2D, 0.08D);
			if (step % 6 == 3) {
				sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, px, py + 0.8D, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}

			Box faultBox = new Box(px - 3.8D, py - 4.0D, pz - 3.8D, px + 3.8D, py + 7.0D, pz + 3.8D);
			List<LivingEntity> victims = sw.getEntitiesByClass(
					LivingEntity.class,
					faultBox,
					e -> e.isAlive()
							&& !(e instanceof PlayerEntity)
							&& !(e instanceof ModNpcEntity)
							&& !(e instanceof PartyDollEntity)
							&& !(e instanceof TameableEntity tame && tame.isTamed())
			);
			for (LivingEntity victim : victims) {
				victim.hurtTime = 0;
				victim.timeUntilRegen = 0;
				float dmg = Math.max(220.0F, victim.getMaxHealth() * 0.55F);
				if (victim instanceof EnderDragonEntity dragon) {
					dragon.damagePart(dragon.head, sw.getDamageSources().playerAttack(sp), dmg);
				} else {
					victim.damage(sw.getDamageSources().playerAttack(sp), dmg);
				}
				victim.setVelocity(dir.x * 0.65D, 1.95D, dir.z * 0.65D);
				victim.velocityModified = true;
				victim.setOnFireFor(8.0F);
			}
		}

		sp.sendMessage(
				Text.literal("🌋 ТЕКТОНИЧЕСКИЙ РАЗЛОМ: линия лавовых гейзеров сокрушила врагов! 🌋")
						.formatted(Formatting.GOLD, Formatting.BOLD),
				true
		);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.novaimod.terra_heaver.desc1").formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("item.novaimod.terra_heaver.desc2").formatted(Formatting.RED));
	}
}
