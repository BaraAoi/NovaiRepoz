package com.novaimod.item;

import java.util.List;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.GenesisProjectorEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GenesisCubeItem extends Item {
	private static final String KEY_BLUEPRINT = "GenesisBlueprint";

	public GenesisCubeItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getBlueprint(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return 0;
		}
		return MathHelper.clamp(comp.copyNbt().getInt(KEY_BLUEPRINT), 0, 3);
	}

	public static String blueprintTitle(int bp) {
		return switch (bp) {
			case 1 -> "Парящий Небесный Остров (С травой, сакурой и источником)";
			case 2 -> "Радужный Мост Биврёст (44 блока через пропасть/океан/лаву)";
			case 3 -> "Лазерное Разравнивание Плато 21x21 (Под застройку базы)";
			default -> "Хрустальный Биокупол (Сфера 21 блок — осушает воду и лаву!)";
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking()) {
				int next = (getBlueprint(stack) + 1) % 4;
				NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				nbt.putInt(KEY_BLUEPRINT, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.35F);
				sp.sendMessage(Text.literal("🏛 Чертёж Куба Творца: ").formatted(Formatting.AQUA)
						.append(Text.literal(blueprintTitle(next)).formatted(Formatting.GOLD, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			int bp = getBlueprint(stack);
			ItemStack offhand = sp.getOffHandStack();
			BlockState customMat = null;
			if (offhand.getItem() instanceof BlockItem bi) {
				BlockState candidate = bi.getBlock().getDefaultState();
				if (candidate.isFullCube(sw, BlockPos.ORIGIN)) {
					customMat = candidate;
				}
			}

			BlockPos targetPos;
			if (bp == 1) {
				Vec3d ahead = sp.getEyePos().add(sp.getRotationVec(1.0F).multiply(18.0D)).add(0.0D, 6.0D, 0.0D);
				targetPos = BlockPos.ofFloored(ahead.x, ahead.y, ahead.z);
			} else {
				targetPos = sp.getBlockPos();
			}

			GenesisProjectorEntity proj = ModEntities.GENESIS_PROJECTOR_ENTITY.create(sw);
			if (proj != null) {
				proj.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
				proj.refreshPositionAndAngles(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D, 0.0F, 0.0F);
				proj.configure(bp, sp.getHorizontalFacing(), customMat);
				sw.spawnEntity(proj);
			}

			sw.playSound(null, targetPos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.5F, 1.35F);
			sp.getItemCooldownManager().set(this, 25);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int bp = getBlueprint(stack);
		tooltip.add(Text.literal("🏛 КУБ ТВОРЦА МИРОВ (3D АРХИТЕКТОР) 🏛").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("Чертёж: ").formatted(Formatting.GRAY)
				.append(Text.literal(blueprintTitle(bp)).formatted(Formatting.GOLD)));
		tooltip.add(Text.literal("• ПКМ: Голографический 3D-тессеракт послойно построит объект!").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Блок во второй руке (F) задаёт свой материал постройки!").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Shift + ПКМ: Сменить чертёж (Биокупол / Остров / Мост / Плато)").formatted(Formatting.GRAY));
	}
}
