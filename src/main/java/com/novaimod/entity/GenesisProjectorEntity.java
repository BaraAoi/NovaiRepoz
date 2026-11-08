package com.novaimod.entity;

import com.novaimod.data.WorldVirginLedger;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * 3D Голографический Проектор «Architect's Genesis Cube»:
 * возводит за 45 тиков выбранное архитектурное чудо послойным лазерным сканированием:
 * - 0: Хрустальный Биокупол (осушает воду/лаву внутри — идеальная подводная или небесная база!);
 * - 1: Парящий Небесный Остров с каменным шпилем, травой, цветущей вишней и водопадом;
 * - 2: Сияющий Радужный Мост Биврёст длиной 44 блока через любую пропасть/океан/Энд;
 * - 3: Идеальное Лазерное Разравнивание строительного плато 21x21!
 */
public class GenesisProjectorEntity extends Entity {
	public static final int BUILD_TICKS = 44;
	public static final int TOTAL_TICKS = 75;

	private static final TrackedData<Integer> BLUEPRINT = DataTracker.registerData(
			GenesisProjectorEntity.class, TrackedDataHandlerRegistry.INTEGER
	);
	private static final TrackedData<Integer> FACING_HORIZ = DataTracker.registerData(
			GenesisProjectorEntity.class, TrackedDataHandlerRegistry.INTEGER
	);

	private int customBlockRawId = -1;

	private static final Block[] BIFROST_GLASS = new Block[]{
			Blocks.RED_STAINED_GLASS,
			Blocks.ORANGE_STAINED_GLASS,
			Blocks.YELLOW_STAINED_GLASS,
			Blocks.LIME_STAINED_GLASS,
			Blocks.CYAN_STAINED_GLASS,
			Blocks.LIGHT_BLUE_STAINED_GLASS,
			Blocks.PURPLE_STAINED_GLASS,
			Blocks.MAGENTA_STAINED_GLASS
	};

	public GenesisProjectorEntity(EntityType<? extends GenesisProjectorEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(BLUEPRINT, 0);
		builder.add(FACING_HORIZ, Direction.SOUTH.getHorizontal());
	}

	public void configure(int blueprint, Direction horizDir, BlockState customMaterial) {
		this.dataTracker.set(BLUEPRINT, MathHelper.clamp(blueprint, 0, 3));
		this.dataTracker.set(FACING_HORIZ, horizDir.getHorizontal());
		this.customBlockRawId = customMaterial != null ? Block.getRawIdFromState(customMaterial) : -1;
	}

	public int getBlueprint() {
		return this.dataTracker.get(BLUEPRINT);
	}

	public Direction getHorizDirection() {
		return Direction.fromHorizontal(this.dataTracker.get(FACING_HORIZ));
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		if (this.age <= BUILD_TICKS) {
			if (this.age % 4 == 0) {
				float pitch = 0.85F + (this.age / (float) BUILD_TICKS) * 0.75F;
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.3F, pitch);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 0.7F, 1.5F);
			}
			buildProgressiveStep(sw, this.age);
		} else if (this.age == BUILD_TICKS + 1) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, 1.8F, 1.35F);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 3.0D, this.getZ(), 50, 3.0D, 2.0D, 3.0D, 0.08D);
		} else if (this.age >= TOTAL_TICKS) {
			this.discard();
		}
	}

	private BlockState pickPrimaryMaterial(BlockState fallback) {
		if (customBlockRawId > 0) {
			BlockState st = Block.getStateFromRawId(customBlockRawId);
			if (st != null && !st.isAir()) {
				return st;
			}
		}
		return fallback;
	}

	private void buildProgressiveStep(ServerWorld sw, int step) {
		int bp = getBlueprint();
		BlockPos origin = this.getBlockPos();
		int flags = Block.NOTIFY_ALL;

		switch (bp) {
			case 0 -> {
				// Хрустальный Биокупол радиусом 10 блоков (послойно снизу вверх!)
				int r = 10;
				int dy = -2 + (step * (r + 3)) / BUILD_TICKS;
				int prevDy = -2 + ((step - 1) * (r + 3)) / BUILD_TICKS;
				for (int y = prevDy; y <= Math.min(r, dy); y++) {
					for (int x = -r; x <= r; x++) {
						for (int z = -r; z <= r; z++) {
							int d2 = x * x + y * y + z * z;
							if (d2 > r * r) {
								continue;
							}
							BlockPos p = origin.add(x, y, z);
							if (sw.getBlockState(p).getHardness(sw, p) < 0.0F) {
								continue;
							}
							if (y == -1) {
								boolean lamp = (Math.abs(x) % 4 == 0 && Math.abs(z) % 4 == 0);
								sw.setBlockState(p, lamp ? Blocks.SEA_LANTERN.getDefaultState() : pickPrimaryMaterial(Blocks.SMOOTH_QUARTZ.getDefaultState()), flags);
							} else if (y >= 0) {
								if (d2 >= (r - 1) * (r - 1)) {
									boolean rib = (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) && y % 4 == 0;
									sw.setBlockState(p, rib ? Blocks.SEA_LANTERN.getDefaultState() : pickPrimaryMaterial(Blocks.CYAN_STAINED_GLASS.getDefaultState()), flags);
								} else {
									// Осушаем воду/лаву и очищаем внутренний воздух биокупола!
									sw.setBlockState(p, Blocks.AIR.getDefaultState(), flags | Block.SKIP_DROPS);
								}
							}
						}
					}
				}
			}
			case 1 -> {
				// Парящий Небесный Остров радиусом 9 блоков
				int r = 9;
				if (step <= 36) {
					int zSlice = -r + ((step - 1) * (2 * r + 1)) / 36;
					int zEnd = -r + (step * (2 * r + 1)) / 36;
					for (int z = zSlice; z <= Math.min(r, zEnd); z++) {
						for (int x = -r; x <= r; x++) {
							double dist = Math.sqrt(x * x + z * z);
							if (dist > r) {
								continue;
							}
							int depth = Math.max(1, (int) Math.round((1.0D - dist / r) * 8.0D));
							for (int y = -depth; y <= 0; y++) {
								BlockPos p = origin.add(x, y, z);
								if (sw.getBlockState(p).getHardness(sw, p) < 0.0F) {
									continue;
								}
								BlockState mat = (y == 0)
										? Blocks.GRASS_BLOCK.getDefaultState()
										: (y >= -2 ? Blocks.DIRT.getDefaultState() : pickPrimaryMaterial(Blocks.STONE.getDefaultState()));
								sw.setBlockState(p, mat, flags);
							}
						}
					}
				} else if (step == 40) {
					// Дерево Сакуры, фонари и небесный родник на вершине острова!
					for (int ty = 1; ty <= 4; ty++) {
						sw.setBlockState(origin.add(0, ty, 0), Blocks.CHERRY_LOG.getDefaultState(), flags);
					}
					for (int lx = -2; lx <= 2; lx++) {
						for (int lz = -2; lz <= 2; lz++) {
							for (int ly = 4; ly <= 6; ly++) {
								if (Math.abs(lx) + Math.abs(lz) + (ly - 4) <= 4) {
									BlockPos lp = origin.add(lx, ly, lz);
									if (sw.getBlockState(lp).isAir()) {
										sw.setBlockState(lp, Blocks.CHERRY_LEAVES.getDefaultState(), flags);
									}
								}
							}
						}
					}
					sw.setBlockState(origin.add(3, 0, 0), Blocks.WATER.getDefaultState(), flags);
					sw.setBlockState(origin.add(-3, 1, 0), Blocks.SEA_LANTERN.getDefaultState(), flags);
					sw.setBlockState(origin.add(0, 1, 3), Blocks.SEA_LANTERN.getDefaultState(), flags);
					sw.setBlockState(origin.add(0, 1, -3), Blocks.SEA_LANTERN.getDefaultState(), flags);
				}
			}
			case 2 -> {
				// Радужный Мост Биврёст (1 сегмент за тик = 44 блока в длину!)
				Direction fwd = getHorizDirection();
				Direction right = fwd.rotateYClockwise();
				BlockPos segCenter = origin.down().offset(fwd, step);
				Block glass = BIFROST_GLASS[step % BIFROST_GLASS.length];
				for (int w = -2; w <= 2; w++) {
					BlockPos floorPos = segCenter.offset(right, w);
					if (sw.getBlockState(floorPos).getHardness(sw, floorPos) >= 0.0F) {
						BlockState floorState = (w == 0 && step % 4 == 0)
								? Blocks.SEA_LANTERN.getDefaultState()
								: pickPrimaryMaterial(glass.getDefaultState());
						sw.setBlockState(floorPos, floorState, flags);
					}
					// Очищаем проход над мостом (высотой 3 блока) и ставим перила по краям
					for (int h = 1; h <= 3; h++) {
						BlockPos airPos = floorPos.up(h);
						if (sw.getBlockState(airPos).getHardness(sw, airPos) >= 0.0F) {
							if (h == 1 && Math.abs(w) == 2) {
								sw.setBlockState(airPos, (step % 6 == 0) ? Blocks.END_ROD.getDefaultState() : Blocks.QUARTZ_SLAB.getDefaultState(), flags);
							} else if (!sw.getBlockState(airPos).isAir()) {
								sw.setBlockState(airPos, Blocks.AIR.getDefaultState(), flags | Block.SKIP_DROPS);
							}
						}
					}
				}
			}
			default -> {
				// Лазерное Разравнивание Плато 21x21!
				int r = 10;
				int xSlice = -r + ((step - 1) * (2 * r + 1)) / BUILD_TICKS;
				int xEnd = -r + (step * (2 * r + 1)) / BUILD_TICKS;
				BlockState floorMat = pickPrimaryMaterial(Blocks.GRASS_BLOCK.getDefaultState());
				for (int x = xSlice; x <= Math.min(r, xEnd); x++) {
					for (int z = -r; z <= r; z++) {
						BlockPos base = origin.add(x, -1, z);
						if (sw.getBlockState(base).getHardness(sw, base) >= 0.0F) {
							sw.setBlockState(base, floorMat, flags);
						}
						// Засыпаем ямы на 4 блока вниз
						for (int d = -5; d <= -2; d++) {
							BlockPos sub = origin.add(x, d, z);
							if (sw.getBlockState(sub).isAir() || !sw.getFluidState(sub).isEmpty()) {
								sw.setBlockState(sub, Blocks.DIRT.getDefaultState(), flags);
							}
						}
						// Срезаем холмы и заросли на 8 блоков вверх
						for (int u = 0; u <= 8; u++) {
							BlockPos up = origin.add(x, u, z);
							if (!sw.getBlockState(up).isAir() && sw.getBlockState(up).getHardness(sw, up) >= 0.0F) {
								sw.setBlockState(up, Blocks.AIR.getDefaultState(), flags | Block.SKIP_DROPS);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 320.0D * 320.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		this.dataTracker.set(BLUEPRINT, nbt.getInt("Blueprint"));
		this.dataTracker.set(FACING_HORIZ, nbt.getInt("Facing"));
		this.customBlockRawId = nbt.getInt("CustomBlock");
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Blueprint", getBlueprint());
		nbt.putInt("Facing", this.dataTracker.get(FACING_HORIZ));
		nbt.putInt("CustomBlock", customBlockRawId);
	}
}
