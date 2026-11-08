package com.novaimod.entity;

import com.novaimod.data.WorldVirginLedger;
import com.novaimod.registry.ModEntities;
import com.novaimod.registry.ModItems;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Подушка из новых обновлений Minecraft (16 цветов):
 * размещается на обычных ванильных ступеньках, плитах и блоках (в том числе под водой в джакузи!),
 * остаётся лежать в мире, позволяет игрокам сидеть на ней по ПКМ и выпадает обратно при ударе ЛКМ.
 */
public class CushionEntity extends Entity {
	private static final TrackedData<Integer> COLOR_ID = DataTracker.registerData(CushionEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> CUSHION_YAW = DataTracker.registerData(CushionEntity.class, TrackedDataHandlerRegistry.FLOAT);

	private BlockPos supportPos;

	public CushionEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(COLOR_ID, DyeColor.WHITE.getId());
		builder.add(CUSHION_YAW, 0.0F);
	}

	public DyeColor getDyeColor() {
		return DyeColor.byId(this.dataTracker.get(COLOR_ID));
	}

	public void setDyeColor(DyeColor color) {
		this.dataTracker.set(COLOR_ID, color.getId());
	}

	public float getCushionYaw() {
		return this.dataTracker.get(CUSHION_YAW);
	}

	public void setCushionYaw(float yaw) {
		this.dataTracker.set(CUSHION_YAW, yaw);
	}

	public BlockPos getSupportPos() {
		return this.supportPos;
	}

	public void setSupportPos(BlockPos pos) {
		this.supportPos = pos == null ? null : pos.toImmutable();
	}

	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	@Override
	public ActionResult interact(PlayerEntity player, Hand hand) {
		if (player.shouldCancelInteraction()) {
			return ActionResult.PASS;
		}
		if (this.getWorld().isClient()) {
			return ActionResult.SUCCESS;
		}
		return sitPlayer(player) ? ActionResult.CONSUME : ActionResult.PASS;
	}

	public boolean sitPlayer(PlayerEntity player) {
		if (this.getWorld().isClient() || player.hasVehicle()) {
			return false;
		}
		if (this.hasPassengers()) {
			player.sendMessage(Text.literal("Эта подушка уже занята!").formatted(Formatting.YELLOW), true);
			return false;
		}
		player.setYaw(this.getCushionYaw());
		player.startRiding(this, true);
		this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_WOOL_STEP, SoundCategory.BLOCKS, 0.85F, 1.05F);
		if (isInJacuzziWater()) {
			this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.BLOCKS, 0.85F, 1.0F);
			player.sendMessage(
					Text.literal("♨ Вы устроились на мягкой подушке в джакузи! (Shift — встать)")
							.formatted(Formatting.AQUA),
					true
			);
		}
		return true;
	}

	private boolean isInJacuzziWater() {
		BlockPos bp = this.getBlockPos();
		return this.isTouchingWater()
				|| this.getWorld().getFluidState(bp).isIn(FluidTags.WATER)
				|| (this.supportPos != null && this.getWorld().getFluidState(this.supportPos).isIn(FluidTags.WATER));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient()) {
			return;
		}

		// Если опорный блок под подушкой сломали — подушка аккуратно выпадает предметом
		if (this.age % 20 == 0 && this.supportPos != null) {
			BlockState support = this.getWorld().getBlockState(this.supportPos);
			if (support.isAir() || support.isOf(Blocks.WATER) || support.isOf(Blocks.LAVA)) {
				dropAndDiscard(true);
				return;
			}
		}

		// Если подушка находится в воде джакузи — мягкие пузырьки и тёплый пар без песка душ!
		if (isInJacuzziWater() && this.getWorld() instanceof ServerWorld sw) {
			if (this.age % 10 == 0) {
				double rad = Math.toRadians(this.getCushionYaw());
				double frontX = this.getX() - Math.sin(rad) * 0.65D;
				double frontZ = this.getZ() + Math.cos(rad) * 0.65D;
				sw.spawnParticles(ParticleTypes.BUBBLE, frontX, this.getY() + 0.15D, frontZ, 4, 0.45D, 0.15D, 0.45D, 0.04D);
				sw.spawnParticles(ParticleTypes.BUBBLE_POP, frontX, this.getY() + 0.45D, frontZ, 3, 0.45D, 0.05D, 0.45D, 0.02D);
			}
			if (this.age % 28 == 0) {
				sw.spawnParticles(
						ParticleTypes.CAMPFIRE_COSY_SMOKE,
						this.getX(),
						this.getY() + 0.65D,
						this.getZ(),
						1,
						0.35D,
						0.04D,
						0.35D,
						0.005D
				);
			}
			if (this.age % 20 == 0 && this.getFirstPassenger() instanceof PlayerEntity player) {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 65, 0, true, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 65, 0, true, false, false));
			}
		}
	}

	@Override
	public boolean damage(DamageSource source, float amount) {
		if (this.isInvulnerableTo(source) || this.getWorld().isClient() || this.isRemoved()) {
			return false;
		}
		boolean dropItem = !(source.getAttacker() instanceof PlayerEntity p && p.isInCreativeMode());
		dropAndDiscard(dropItem);
		return true;
	}

	private void dropAndDiscard(boolean dropItem) {
		this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.BLOCK_WOOL_BREAK, SoundCategory.BLOCKS, 0.9F, 1.0F);
		if (dropItem) {
			this.dropStack(new ItemStack(ModItems.getCushionItem(this.getDyeColor())));
		}
		this.discard();
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(ModItems.getCushionItem(this.getDyeColor()));
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		if (nbt.contains("Color")) {
			this.setDyeColor(DyeColor.byId(nbt.getInt("Color")));
		}
		if (nbt.contains("YawDeg")) {
			this.setCushionYaw(nbt.getFloat("YawDeg"));
		}
		if (nbt.contains("SupportPos")) {
			this.supportPos = BlockPos.fromLong(nbt.getLong("SupportPos"));
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Color", this.getDyeColor().getId());
		nbt.putFloat("YawDeg", this.getCushionYaw());
		if (this.supportPos != null) {
			nbt.putLong("SupportPos", this.supportPos.asLong());
		}
	}

	/**
	 * Устанавливает подушку на ванильную ступеньку, плиту или блок (в генерации структур или рукой игрока).
	 */
	public static CushionEntity placeOnBlock(World world, BlockPos pos, DyeColor color, Direction seatFacing) {
		Box checkBox = new Box(pos).expand(0.15D, 0.65D, 0.15D);
		if (!world.getEntitiesByClass(CushionEntity.class, checkBox, c -> !c.isRemoved()).isEmpty()) {
			return null;
		}

		BlockState state = world.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}

		double px = pos.getX() + 0.5D;
		double py = pos.getY() + 1.0D;
		double pz = pos.getZ() + 0.5D;
		Direction frontDir = seatFacing;

		if (state.getBlock() instanceof StairsBlock) {
			BlockHalf half = state.get(StairsBlock.HALF);
			Direction backFacing = state.get(StairsBlock.FACING);
			frontDir = backFacing.getOpposite();
			if (half == BlockHalf.BOTTOM) {
				py = pos.getY() + 0.5D;
				px += frontDir.getOffsetX() * 0.10D;
				pz += frontDir.getOffsetZ() * 0.10D;
			}
		} else if (state.getBlock() instanceof SlabBlock) {
			SlabType slabType = state.get(SlabBlock.TYPE);
			if (slabType == SlabType.BOTTOM) {
				py = pos.getY() + 0.5D;
			}
		}

		CushionEntity cushion = ModEntities.CUSHION_ENTITY.create(world);
		if (cushion == null) {
			return null;
		}
		if (!WorldVirginLedger.isInStructureGenScope()) {
			cushion.addCommandTag(WorldVirginLedger.TAG_NON_VIRGIN);
		}
		float yaw = frontDir.asRotation();
		cushion.refreshPositionAndAngles(px, py, pz, yaw, 0.0F);
		cushion.setDyeColor(color);
		cushion.setCushionYaw(yaw);
		cushion.setSupportPos(pos);
		world.spawnEntity(cushion);
		return cushion;
	}
}
