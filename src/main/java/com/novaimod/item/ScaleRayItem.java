package com.novaimod.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.novaimod.NovaiMod;
import com.novaimod.entity.ModNpcEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * КРЕАТИВНЫЙ АРТЕФАКТ: «Пушка Абсурдного Масштаба» (v3.7.0).
 * 4 безумных режима (Shift + ПКМ переключает, ПКМ применяет без кулдауна!):
 *  0) ГИГАНТИЗАЦИЯ (Кайдзю до x12) — превращает любого моба (или вас при выстреле себе под ноги!) в титана!
 *  1) МИКРО-ЛИЛИПУТ (Муравей x0.12) — сжимает врагов или самого игрока до размера мыши (можно ходить под люками!).
 *  2) ТЕЛЕКИНЕЗ-КАТАПУЛЬТА — хватает любого моба силовым лучом перед прицелом, а повторным ПКМ швыряет его как бомбу!
 *  3) СБРОС МАСШТАБА (Норма x1.0) — возвращает обычный размер игроку и всем существам вокруг!
 */
public class ScaleRayItem extends Item {
	private static final String NBT_RAY_MODE = "novai_scale_ray_mode";
	private static final Identifier SCALE_MOD_ID = Identifier.of(NovaiMod.MOD_ID, "absurd_scale_mod");
	private static final Identifier STEP_MOD_ID = Identifier.of(NovaiMod.MOD_ID, "absurd_step_mod");
	private static final Identifier BLOCK_REACH_MOD_ID = Identifier.of(NovaiMod.MOD_ID, "absurd_block_reach");
	private static final Identifier ENTITY_REACH_MOD_ID = Identifier.of(NovaiMod.MOD_ID, "absurd_entity_reach");
	public static final String TAG_THROWN_BOMB = "novai_telekinesis_bomb";

	public static final int MODE_GIANT = 0;
	public static final int MODE_TINY = 1;
	public static final int MODE_TELEKINESIS = 2;
	public static final int MODE_RESET = 3;

	private static final Map<UUID, UUID> HELD_BY_PLAYER = new HashMap<>();

	public ScaleRayItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return MODE_GIANT;
		}
		return Math.floorMod(comp.copyNbt().getInt(NBT_RAY_MODE), 4);
	}

	public static void setMode(ItemStack stack, int mode) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putInt(NBT_RAY_MODE, Math.floorMod(mode, 4));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static String getModeTitle(int mode) {
		return switch (mode) {
			case MODE_TINY -> "Микро-Лилипут (сжатие до муравья)";
			case MODE_TELEKINESIS -> "Телекинез-Катапульта (захват и бросок)";
			case MODE_RESET -> "Сброс Масштаба (вернуть норму x1.0)";
			default -> "Гигантизация (Кайдзю-титан до x12)";
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (user.isSneaking()) {
				int next = (getMode(stack) + 1) % 4;
				setMode(stack, next);
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.4F, 0.9F + next * 0.2F);
				sp.sendMessage(
						Text.literal("◈ Режим Пушки Масштаба: " + getModeTitle(next))
								.formatted(Formatting.AQUA, Formatting.BOLD),
						true
				);
				return TypedActionResult.success(stack, false);
			}

			int mode = getMode(stack);
			switch (mode) {
				case MODE_GIANT -> fireScaleBeam(sw, sp, true);
				case MODE_TINY -> fireScaleBeam(sw, sp, false);
				case MODE_TELEKINESIS -> triggerTelekinesis(sw, sp);
				case MODE_RESET -> resetAllScales(sw, sp);
			}
			user.swingHand(hand, true);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private void fireScaleBeam(ServerWorld sw, ServerPlayerEntity sp, boolean enlarge) {
		// Если игрок смотрит резко себе под ноги (pitch > 68°) — меняет размер САМОГО СЕБЯ!
		if (sp.getPitch() > 68.0F) {
			double cur = getCurrentScale(sp);
			double targetScale = enlarge
					? MathHelper.clamp(cur < 1.0D ? 1.0D : cur * 2.0D, 1.0D, 10.0D)
					: (cur > 1.0D ? 1.0D : 0.15D);
			applyEntityScale(sp, targetScale);
			sw.spawnParticles(ParticleTypes.END_ROD, sp.getX(), sp.getBodyY(0.5D), sp.getZ(), 28, 0.5D, 0.7D, 0.5D, 0.12D);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.4F, enlarge ? 0.6F : 1.85F);
			return;
		}

		LivingEntity hitTarget = raycastLivingTarget(sw, sp, 80.0D);
		Vec3d start = sp.getEyePos().subtract(0.0D, 0.18D, 0.0D);
		Vec3d end = hitTarget != null
				? hitTarget.getPos().add(0.0D, hitTarget.getHeight() * 0.5D, 0.0D)
				: start.add(sp.getRotationVec(1.0F).multiply(45.0D));

		// Лёгкий и быстрый луч частиц (0 лагов!)
		Vec3d delta = end.subtract(start);
		double len = delta.length();
		Vec3d stepVec = len > 0.001D ? delta.multiply(1.0D / len) : Vec3d.ZERO;
		for (double d = 1.0D; d < Math.min(len, 60.0D); d += 1.5D) {
			Vec3d p = start.add(stepVec.multiply(d));
			sw.spawnParticles(enlarge ? ParticleTypes.WAX_ON : ParticleTypes.PORTAL, p.x, p.y, p.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
		}

		if (hitTarget == null) {
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.9F, 1.5F);
			return;
		}

		double cur = getCurrentScale(hitTarget);
		double nextScale = enlarge
				? MathHelper.clamp(cur < 1.0D ? 1.0D : cur * 2.0D, 1.0D, 12.0D)
				: MathHelper.clamp(cur > 1.0D ? 1.0D : cur * 0.35D, 0.12D, 1.0D);
		applyEntityScale(hitTarget, nextScale);

		sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, hitTarget.getX(), hitTarget.getBodyY(0.5D), hitTarget.getZ(), 24, 0.5D, 0.6D, 0.5D, 0.15D);
		sw.playSound(null, hitTarget.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.4F, enlarge ? 0.55F : 1.85F);
	}

	private void triggerTelekinesis(ServerWorld sw, ServerPlayerEntity sp) {
		UUID heldId = HELD_BY_PLAYER.remove(sp.getUuid());
		if (heldId != null) {
			Entity held = sw.getEntity(heldId);
			if (held instanceof LivingEntity living && living.isAlive()) {
				living.setNoGravity(false);
				Vec3d launch = sp.getRotationVec(1.0F).normalize().multiply(3.6D).add(0.0D, 0.35D, 0.0D);
				living.setVelocity(launch);
				living.velocityModified = true;
				living.addCommandTag(TAG_THROWN_BOMB);
				sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.6F, 1.25F);
				sw.spawnParticles(ParticleTypes.SONIC_BOOM, living.getX(), living.getBodyY(0.5D), living.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
				return;
			}
		}

		LivingEntity target = raycastLivingTarget(sw, sp, 64.0D);
		if (target == null || target instanceof PlayerEntity) {
			return;
		}
		HELD_BY_PLAYER.put(sp.getUuid(), target.getUuid());
		target.setNoGravity(true);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.3F, 1.6F);
	}

	private void resetAllScales(ServerWorld sw, ServerPlayerEntity sp) {
		applyEntityScale(sp, 1.0D);
		Box area = sp.getBoundingBox().expand(64.0D);
		for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, area, LivingEntity::isAlive)) {
			if (Math.abs(getCurrentScale(e) - 1.0D) > 0.01D) {
				applyEntityScale(e, 1.0D);
			}
		}
		UUID rel = HELD_BY_PLAYER.remove(sp.getUuid());
		if (rel != null) {
			Entity held = sw.getEntity(rel);
			if (held != null) {
				held.setNoGravity(false);
			}
		}
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.3F, 1.35F);
	}

	public static double getCurrentScale(LivingEntity entity) {
		EntityAttributeInstance attr = entity.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		return attr != null ? attr.getValue() : 1.0D;
	}

	public static void applyEntityScale(LivingEntity entity, double targetScale) {
		EntityAttributeInstance scaleAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scaleAttr != null) {
			if (Math.abs(targetScale - 1.0D) < 0.01D) {
				scaleAttr.removeModifier(SCALE_MOD_ID);
			} else {
				scaleAttr.overwritePersistentModifier(new EntityAttributeModifier(
						SCALE_MOD_ID,
						targetScale - 1.0D,
						EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
				));
			}
		}

		EntityAttributeInstance stepAttr = entity.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
		if (stepAttr != null) {
			if (targetScale <= 1.05D) {
				stepAttr.removeModifier(STEP_MOD_ID);
			} else {
				stepAttr.overwritePersistentModifier(new EntityAttributeModifier(
						STEP_MOD_ID,
						Math.min(4.0D, (targetScale - 1.0D) * 0.55D),
						EntityAttributeModifier.Operation.ADD_VALUE
				));
			}
		}

		if (entity instanceof PlayerEntity player) {
			EntityAttributeInstance bReach = player.getAttributeInstance(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE);
			EntityAttributeInstance eReach = player.getAttributeInstance(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE);
			if (bReach != null) {
				if (targetScale <= 1.05D) {
					bReach.removeModifier(BLOCK_REACH_MOD_ID);
				} else {
					bReach.overwritePersistentModifier(new EntityAttributeModifier(
							BLOCK_REACH_MOD_ID,
							Math.min(18.0D, (targetScale - 1.0D) * 2.2D),
							EntityAttributeModifier.Operation.ADD_VALUE
					));
				}
			}
			if (eReach != null) {
				if (targetScale <= 1.05D) {
					eReach.removeModifier(ENTITY_REACH_MOD_ID);
				} else {
					eReach.overwritePersistentModifier(new EntityAttributeModifier(
							ENTITY_REACH_MOD_ID,
							Math.min(18.0D, (targetScale - 1.0D) * 2.0D),
							EntityAttributeModifier.Operation.ADD_VALUE
					));
				}
			}
		}
		entity.calculateDimensions();
	}

	private static LivingEntity raycastLivingTarget(ServerWorld sw, ServerPlayerEntity sp, double maxDist) {
		Vec3d start = sp.getEyePos();
		Vec3d look = sp.getRotationVec(1.0F);
		Vec3d end = start.add(look.multiply(maxDist));
		Box searchBox = sp.getBoundingBox().stretch(look.multiply(maxDist)).expand(3.0D);

		LivingEntity best = null;
		double bestDistSq = maxDist * maxDist;
		for (LivingEntity candidate : sw.getEntitiesByClass(LivingEntity.class, searchBox, e -> e != sp && e.isAlive() && !(e instanceof ModNpcEntity))) {
			Box hitBox = candidate.getBoundingBox().expand(0.45D);
			Optional<Vec3d> hit = hitBox.raycast(start, end);
			if (hit.isPresent()) {
				double d2 = start.squaredDistanceTo(hit.get());
				if (d2 < bestDistSq) {
					bestDistSq = d2;
					best = candidate;
				}
			}
		}
		return best;
	}

	public static void tickTelekinesis(MinecraftServer server) {
		if (!HELD_BY_PLAYER.isEmpty()) {
			Iterator<Map.Entry<UUID, UUID>> it = HELD_BY_PLAYER.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, UUID> entry = it.next();
				ServerPlayerEntity holder = server.getPlayerManager().getPlayer(entry.getKey());
				if (holder == null || !holder.isAlive()
						|| (!holder.getMainHandStack().isOf(com.novaimod.registry.ModItems.SCALE_RAY)
						&& !holder.getOffHandStack().isOf(com.novaimod.registry.ModItems.SCALE_RAY))) {
					if (holder != null) {
						Entity e = holder.getServerWorld().getEntity(entry.getValue());
						if (e != null) {
							e.setNoGravity(false);
						}
					}
					it.remove();
					continue;
				}
				ServerWorld sw = holder.getServerWorld();
				Entity target = sw.getEntity(entry.getValue());
				if (!(target instanceof LivingEntity living) || !living.isAlive()) {
					it.remove();
					continue;
				}
				double holdDist = Math.max(5.5D, 4.0D + living.getWidth() * 1.4D);
				Vec3d desired = holder.getEyePos().add(holder.getRotationVec(1.0F).multiply(holdDist)).subtract(0.0D, living.getHeight() * 0.4D, 0.0D);
				Vec3d vel = desired.subtract(living.getPos()).multiply(0.45D);
				living.setNoGravity(true);
				living.setVelocity(vel);
				living.velocityModified = true;
				living.fallDistance = 0.0F;
				if (holder.age % 3 == 0) {
					sw.spawnParticles(ParticleTypes.END_ROD, living.getX(), living.getBodyY(0.5D), living.getZ(), 2, 0.35D, 0.35D, 0.35D, 0.02D);
				}
			}
		}

		// Проверка приземления запущенных телекинезом живых бомб
		for (ServerWorld sw : server.getWorlds()) {
			for (ServerPlayerEntity p : sw.getPlayers()) {
				Box box = p.getBoundingBox().expand(96.0D);
				for (LivingEntity bomb : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.getCommandTags().contains(TAG_THROWN_BOMB))) {
					if (bomb.isOnGround() || bomb.horizontalCollision || bomb.verticalCollision) {
						bomb.removeCommandTag(TAG_THROWN_BOMB);
						bomb.setNoGravity(false);
						sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, bomb.getX(), bomb.getY(), bomb.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
						sw.playSound(null, bomb.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.6F, 0.85F);
						for (LivingEntity nearby : sw.getEntitiesByClass(LivingEntity.class, bomb.getBoundingBox().expand(5.5D), e -> e.isAlive() && !(e instanceof PlayerEntity) && !(e instanceof ModNpcEntity))) {
							nearby.hurtTime = 0;
							if (nearby instanceof EnderDragonEntity dragon) {
								dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), 180.0F);
							} else {
								nearby.damage(sw.getDamageSources().outOfWorld(), 180.0F);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.novaimod.scale_ray.desc1").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.novaimod.scale_ray.desc2").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.translatable("item.novaimod.scale_ray.desc3").formatted(Formatting.GRAY));
		tooltip.add(Text.literal("Активный режим: " + getModeTitle(getMode(stack))).formatted(Formatting.GOLD));
	}
}
