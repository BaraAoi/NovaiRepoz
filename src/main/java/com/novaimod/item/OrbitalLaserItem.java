package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.OrbitalLaserEntity;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * КРЕАТИВНЫЙ АРТЕФАКТ: «Маяк Орбитального Лазера» (v3.6.0).
 * - ПКМ (без кулдауна!): обрушивает с орбиты исполинский 3D-столб плазмы высотой 180 блоков,
 *   который сам плавно преследует и испепеляет ближайших живых врагов и боссов!
 * - Shift + ПКМ: переключает режим («Стирание материи» — прожигает блоки / «Чистая плазма» — щадит постройки).
 */
public class OrbitalLaserItem extends Item {
	private static final String NBT_CARVE_TERRAIN = "novai_laser_carve";

	public OrbitalLaserItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static boolean shouldCarveTerrain(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return true;
		}
		NbtCompound nbt = comp.copyNbt();
		return !nbt.contains(NBT_CARVE_TERRAIN) || nbt.getBoolean(NBT_CARVE_TERRAIN);
	}

	public static void setCarveTerrain(ItemStack stack, boolean carve) {
		NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
		nbt.putBoolean(NBT_CARVE_TERRAIN, carve);
		stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (user.isSneaking()) {
				boolean next = !shouldCarveTerrain(stack);
				setCarveTerrain(stack, next);
				sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, next ? 0.85F : 1.4F);
				sp.sendMessage(
						Text.literal(next
								? "☢ Режим Орбитального Лазера: СТИРАНИЕ МАТЕРИИ (испаряет породу!)"
								: "✦ Режим Орбитального Лазера: ЧИСТАЯ ПЛАЗМА (без разрушения блоков)")
								.formatted(next ? Formatting.GOLD : Formatting.AQUA, Formatting.BOLD),
						true
				);
				return TypedActionResult.success(stack, false);
			}

			Vec3d eye = sp.getEyePos();
			Vec3d look = sp.getRotationVec(1.0F);
			Vec3d maxEnd = eye.add(look.multiply(128.0D));
			BlockHitResult hit = sw.raycast(new RaycastContext(
					eye,
					maxEnd,
					RaycastContext.ShapeType.OUTLINE,
					RaycastContext.FluidHandling.NONE,
					sp
			));

			Vec3d targetPos;
			if (hit.getType() == HitResult.Type.BLOCK) {
				targetPos = Vec3d.ofBottomCenter(hit.getBlockPos().up());
			} else {
				Vec3d ahead = sp.getPos().add(look.x * 28.0D, 0.0D, look.z * 28.0D);
				int topY = sw.getTopY(Heightmap.Type.MOTION_BLOCKING, BlockPos.ofFloored(ahead.x, sp.getY(), ahead.z).getX(), BlockPos.ofFloored(ahead.x, sp.getY(), ahead.z).getZ());
				targetPos = new Vec3d(ahead.x, Math.max(sw.getBottomY() + 1, topY), ahead.z);
			}

			OrbitalLaserEntity laser = new OrbitalLaserEntity(sw, targetPos.x, targetPos.y, targetPos.z, sp.getUuid(), shouldCarveTerrain(stack));
			sw.spawnEntity(laser);

			sw.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.0F, 1.55F);
			sp.sendMessage(
					Text.literal("☄ ОРБИТАЛЬНЫЙ СПУТНИК НАВЕДЁН: сведение плазменного луча! ☄")
							.formatted(Formatting.AQUA, Formatting.BOLD),
					true
			);
			user.swingHand(hand, true);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.translatable("item.novaimod.orbital_laser.desc1").formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("item.novaimod.orbital_laser.desc2").formatted(Formatting.GOLD));
		boolean carve = shouldCarveTerrain(stack);
		tooltip.add(Text.literal(carve
				? "Активный режим: Стирание материи (прожигает породу)"
				: "Активный режим: Чистая плазма (без поломки блоков)")
				.formatted(carve ? Formatting.RED : Formatting.GREEN));
	}
}
