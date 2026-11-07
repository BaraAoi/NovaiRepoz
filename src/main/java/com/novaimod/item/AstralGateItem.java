package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.PartyDollEntity;
import com.novaimod.entity.TerraCataclysmEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * КРЕАТИВНЫЙ АРТЕФАКТ: «Зеркало Повелителя Миров» (v3.6.0).
 * 4-в-1 пульт абсолютной власти над реальностью (переключение Shift + ПКМ, применение ПКМ без кулдауна!):
 *  0) Гравитационный Инвертор — поднимает всех врагов в радиусе 64 блоков в стратосферу и с грохотом бьёт о землю!
 *  1) Звёздный Дождь Энда — обрушивает с небес 36 самонаводящихся астральных драконьих комет!
 *  2) Сверхновая Зачистка — переключает день/ночь, убирает шторм, исцеляет владельца и испепеляет тьму в радиусе 90 блоков!
 *  3) Квантовый Скачок Взгляда — мгновенно переносит владельца на 220 блоков вперёд со звуковым ударом Sonic Boom!
 */
public class AstralGateItem extends Item {
	private static final String NBT_DECREE_MODE = "novai_astral_decree";
	public static final int MODE_GRAVITY_SLAM = 0;
	public static final int MODE_STARFALL = 1;
	public static final int MODE_SUPERNOVA_PURGE = 2;
	public static final int MODE_SONIC_BLINK = 3;

	public AstralGateItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return MODE_GRAVITY_SLAM;
		}
		return Math.floorMod(comp.copyNbt().getInt(NBT_DECREE_MODE), 4);
	}

	public static void setMode(ItemStack stack, int mode) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putInt(NBT_DECREE_MODE, Math.floorMod(mode, 4));
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	public static String getModeTitle(int mode) {
		return switch (mode) {
			case MODE_STARFALL -> "Звёздный Дождь Энда (36 комет)";
			case MODE_SUPERNOVA_PURGE -> "Сверхновая Зачистка (90 блоков + смена светил)";
			case MODE_SONIC_BLINK -> "Квантовый Скачок Взгляда (220 блоков)";
			default -> "Гравитационный Инвертор (64 блока)";
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (user.isSneaking()) {
				int next = (getMode(stack) + 1) % 4;
				setMode(stack, next);
				sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.6F, 1.15F + next * 0.15F);
				sp.sendMessage(
						Text.literal("❖ Указ Зеркала Миров: " + getModeTitle(next))
								.formatted(Formatting.AQUA, Formatting.BOLD),
						true
				);
				return TypedActionResult.success(stack, false);
			}

			int mode = getMode(stack);
			switch (mode) {
				case MODE_GRAVITY_SLAM -> executeGravityInversion(sw, sp);
				case MODE_STARFALL -> executeAstralStarfall(sw, sp);
				case MODE_SUPERNOVA_PURGE -> executeSupernovaPurge(sw, sp);
				case MODE_SONIC_BLINK -> executeSonicBlink(sw, sp);
			}
			user.swingHand(hand, true);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private void executeGravityInversion(ServerWorld sw, ServerPlayerEntity sp) {
		TerraCataclysmEntity inv = TerraCataclysmEntity.startGravityInversion(sw, sp);
		sw.spawnEntity(inv);
		sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.0F, 0.65F);
		sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY() + 1.0D, sp.getZ(), 80, 3.0D, 1.2D, 3.0D, 0.25D);
		sp.sendMessage(
				Text.literal("🌌 ГРАВИТАЦИЯ ОТМЕНЕНА: все враги вознесены перед ударом о твердь! 🌌")
						.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
				true
		);
	}

	private void executeAstralStarfall(ServerWorld sw, ServerPlayerEntity sp) {
		Box box = new Box(
				sp.getX() - 72.0D, sp.getY() - 28.0D, sp.getZ() - 72.0D,
				sp.getX() + 72.0D, sp.getY() + 45.0D, sp.getZ() + 72.0D
		);
		List<LivingEntity> enemies = sw.getEntitiesByClass(
				LivingEntity.class,
				box,
				e -> e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof ModNpcEntity)
						&& !(e instanceof PartyDollEntity)
						&& !(e instanceof TameableEntity tame && tame.isTamed())
		);

		Vec3d baseAim = sp.getPos().add(sp.getRotationVec(1.0F).multiply(26.0D));
		int comets = 36;
		for (int i = 0; i < comets; i++) {
			Vec3d dest;
			if (!enemies.isEmpty()) {
				LivingEntity victim = enemies.get(i % enemies.size());
				dest = victim.getPos().add((sw.random.nextDouble() - 0.5D) * 2.5D, 0.5D, (sw.random.nextDouble() - 0.5D) * 2.5D);
			} else {
				double ang = (Math.PI * 2.0D * i) / comets;
				double rad = 3.0D + (i % 6) * 3.8D;
				dest = baseAim.add(Math.cos(ang) * rad, 0.0D, Math.sin(ang) * rad);
			}

			Vec3d skyStart = dest.add((sw.random.nextDouble() - 0.5D) * 14.0D, 34.0D + (i % 5) * 4.0D, (sw.random.nextDouble() - 0.5D) * 14.0D);
			Vec3d vel = dest.subtract(skyStart).normalize().multiply(2.2D);
			DragonFireballEntity comet = new DragonFireballEntity(sw, sp, vel);
			comet.setPosition(skyStart.x, skyStart.y, skyStart.z);
			comet.addCommandTag(TerraCataclysmEntity.TAG_CREATIVE_BOULDER);
			sw.spawnEntity(comet);
			sw.spawnParticles(ParticleTypes.END_ROD, skyStart.x, skyStart.y, skyStart.z, 5, 0.5D, 0.5D, 0.5D, 0.06D);
		}

		// И прямое поражение звёздным светом всех врагов под звездопадом!
		for (LivingEntity victim : enemies) {
			victim.hurtTime = 0;
			victim.timeUntilRegen = 0;
			float starDmg = Math.max(180.0F, victim.getMaxHealth() * 0.40F);
			if (victim instanceof EnderDragonEntity dragon) {
				dragon.damagePart(dragon.head, sw.getDamageSources().magic(), starDmg);
			} else {
				victim.damage(sw.getDamageSources().magic(), starDmg);
			}
		}

		sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.8F, 1.35F);
		sp.sendMessage(
				Text.literal("☄ ЗВЁЗДНЫЙ ДОЖДЬ ЭНДА: 36 астральных комет обрушились с небес! ☄")
						.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD),
				true
		);
	}

	private void executeSupernovaPurge(ServerWorld sw, ServerPlayerEntity sp) {
		long timeOfDay = sw.getTimeOfDay() % 24000L;
		boolean wasNight = timeOfDay >= 12500L && timeOfDay <= 23500L;
		sw.setTimeOfDay(wasNight ? 6000L : 18000L);
		sw.setWeather(12000, 0, false, false);

		sp.setHealth(sp.getMaxHealth());
		sp.getHungerManager().setFoodLevel(20);
		sp.getHungerManager().setSaturationLevel(20.0F);
		sp.clearStatusEffects();

		Box purgeBox = new Box(
				sp.getX() - 90.0D, sp.getY() - 45.0D, sp.getZ() - 90.0D,
				sp.getX() + 90.0D, sp.getY() + 65.0D, sp.getZ() + 90.0D
		);
		List<LivingEntity> enemies = sw.getEntitiesByClass(
				LivingEntity.class,
				purgeBox,
				e -> e.isAlive()
						&& !(e instanceof PlayerEntity)
						&& !(e instanceof ModNpcEntity)
						&& !(e instanceof PartyDollEntity)
						&& !(e instanceof TameableEntity tame && tame.isTamed())
		);

		int smited = 0;
		for (LivingEntity enemy : enemies) {
			if (smited < 16) {
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
				if (bolt != null) {
					bolt.refreshPositionAfterTeleport(enemy.getX(), enemy.getY(), enemy.getZ());
					bolt.setCosmetic(true);
					sw.spawnEntity(bolt);
				}
			}
			sw.spawnParticles(ParticleTypes.FLASH, enemy.getX(), enemy.getBodyY(0.5D), enemy.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, enemy.getX(), enemy.getBodyY(0.5D), enemy.getZ(), 18, 0.5D, 0.8D, 0.5D, 0.14D);
			enemy.hurtTime = 0;
			enemy.timeUntilRegen = 0;
			if (enemy instanceof EnderDragonEntity dragon) {
				dragon.damagePart(dragon.head, sw.getDamageSources().outOfWorld(), 5000.0F);
			} else {
				enemy.damage(sw.getDamageSources().outOfWorld(), 5000.0F);
			}
			smited++;
		}

		sw.spawnParticles(ParticleTypes.FLASH, sp.getX(), sp.getY() + 1.5D, sp.getZ(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
		sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.8F, 1.15F);
		sp.sendMessage(
				Text.literal("☀ СВЕРХНОВАЯ ЗАЧИСТКА: испепелено врагов — " + smited + ", светила обращены! ☀")
						.formatted(Formatting.GOLD, Formatting.BOLD),
				true
		);
	}

	private void executeSonicBlink(ServerWorld sw, ServerPlayerEntity sp) {
		Vec3d startEye = sp.getEyePos();
		Vec3d look = sp.getRotationVec(1.0F).normalize();
		Vec3d maxDest = startEye.add(look.multiply(220.0D));

		BlockHitResult hit = sw.raycast(new RaycastContext(
				startEye, maxDest, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp
		));
		Vec3d dest = hit.getType() == HitResult.Type.BLOCK
				? hit.getPos().subtract(look.multiply(1.2D))
				: maxDest;

		double dist = Math.min(220.0D, startEye.distanceTo(dest));
		for (double d = 2.0D; d < dist; d += 4.0D) {
			Vec3d p = startEye.add(look.multiply(d));
			sw.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			Box beamBox = new Box(p.x - 3.0D, p.y - 3.0D, p.z - 3.0D, p.x + 3.0D, p.y + 3.0D, p.z + 3.0D);
			for (LivingEntity victim : sw.getEntitiesByClass(
					LivingEntity.class,
					beamBox,
					e -> e.isAlive() && e != sp && !(e instanceof PlayerEntity) && !(e instanceof ModNpcEntity) && !(e instanceof PartyDollEntity)
			)) {
				victim.hurtTime = 0;
				victim.timeUntilRegen = 0;
				victim.damage(sw.getDamageSources().sonicBoom(sp), 350.0F);
			}
		}

		sp.fallDistance = 0.0F;
		sp.teleport(sw, dest.x, dest.y, dest.z, sp.getYaw(), sp.getPitch());
		sw.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.8F, 1.25F);
		sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1.0D, dest.z, 45, 0.6D, 1.0D, 0.6D, 0.18D);
		sp.sendMessage(
				Text.literal("⚡ КВАНТОВЫЙ СКАЧОК: мгновенный прорыв пространства! ⚡")
						.formatted(Formatting.AQUA, Formatting.BOLD),
				true
		);
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.novaimod.astral_gate.desc1").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.novaimod.astral_gate.desc2").formatted(Formatting.LIGHT_PURPLE));
		tooltip.add(Text.literal("Текущий указ: " + getModeTitle(getMode(stack))).formatted(Formatting.GOLD));
	}
}
