package com.novaimod.entity;

import java.util.ArrayList;
import java.util.List;

import com.novaimod.data.WorldVirginLedger;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Мощнейший 3D-феномен «Supernova Genesis»:
 * - Режим 0 (КАТАКЛИЗМ И ЗВЁЗДНЫЙ КРАТЕР):
 *     1) Красный Гипергигант испаряет воду/снег/лёд в пар и плавит песок в стекло;
 *     2) Гравитационный коллапс ФИЗИЧЕСКИ вырывает блоки земли/камня из почвы в небо прямо в ядро звезды
 *        и затягивает всех врагов и боссов;
 *     3) ВЗРЫВ СВЕРХНОВОЙ выжигает гигантский 18-блочный метеоритный кратер, оплавленный обсидианом,
 *        плачущим обсидианом, аметистами и жилами Алмазов, Золота и Древних Обломков (Незерита) + ваншотит боссов!
 * - Режим 1 (ЗВЁЗДНЫЙ РЕНТГЕН-КАРЬЕР И КУЗНИЦА НУКЛЕОСИНТЕЗА):
 *     Испаряет всю пустую породу (камень, глубинный сланец, землю, гравий, туф) в сфере 16 блоков,
 *     оставляя ВСЕ РУДНЫЕ ЖИЛЫ, сундуки и постройки парить нетронутыми в воздухе при свете звёзд,
 *     а также переплавляет брошенный уголь в Алмазы, руду — в целые блоки металлов и чинит снаряжение!
 */
public class SupernovaStarEntity extends Entity {
	public static final int SWELL_END_TICK = 65;
	public static final int COLLAPSE_END_TICK = 92;
	public static final int TOTAL_LIFE_TICKS = 260;

	private static final TrackedData<Integer> MODE = DataTracker.registerData(
			SupernovaStarEntity.class, TrackedDataHandlerRegistry.INTEGER
	);

	private final List<FallingBlockEntity> suckedBlocks = new ArrayList<>();

	public SupernovaStarEntity(EntityType<? extends SupernovaStarEntity> type, World world) {
		super(type, world);
		this.noClip = true;
		this.ignoreCameraFrustum = true;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(MODE, 0);
	}

	public void setForgeMode(int mode) {
		this.dataTracker.set(MODE, mode);
	}

	public int getForgeMode() {
		return this.dataTracker.get(MODE);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld sw)) {
			return;
		}

		int mode = getForgeMode();

		if (this.age < SWELL_END_TICK) {
			// ФАЗА 1: Раскалённый Гипергигант — испаряет воду и плавит песок под звездой!
			int beatPeriod = Math.max(4, 16 - this.age / 5);
			if (this.age % beatPeriod == 0) {
				float pitch = 0.55F + (this.age / (float) SWELL_END_TICK) * 0.85F;
				sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.MASTER, 2.2F, pitch);
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.MASTER, 1.0F, pitch);
			}
			sw.spawnParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 10, 2.0D, 2.0D, 2.0D, 0.05D);

			if (mode == 0 && this.age % 3 == 0) {
				scorchUnderHypergiant(sw);
			}
		} else if (this.age == SWELL_END_TICK) {
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 2.4F, 1.75F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.MASTER, 2.0F, 0.85F);
		} else if (this.age < COLLAPSE_END_TICK) {
			// ФАЗА 2: Гравитационное схлопывание — вырывает куски породы с земли прямо в звезду и стягивает врагов!
			Box pullBox = this.getBoundingBox().expand(34.0D);
			for (LivingEntity mob : sw.getEntitiesByClass(LivingEntity.class, pullBox, e -> e.isAlive() && isHostileOrValidTarget(e))) {
				Vec3d pull = this.getPos().subtract(mob.getPos());
				double d = Math.max(1.0D, pull.length());
				Vec3d vel = pull.multiply(0.42D / d);
				mob.addVelocity(vel.x, vel.y + 0.10D, vel.z);
				mob.velocityModified = true;
			}

			if (this.age % 2 == 0) {
				ripSurfaceBlocksIntoCore(sw, mode == 0 ? 5 : 3);
			}
			// Направляем вырванные блоки по спирали в центр умирающей звезды!
			for (FallingBlockEntity fb : suckedBlocks) {
				if (fb != null && fb.isAlive()) {
					Vec3d toCore = this.getPos().subtract(fb.getPos());
					if (toCore.lengthSquared() < 2.2D) {
						fb.discard();
					} else {
						fb.setVelocity(toCore.normalize().multiply(0.85D));
						fb.velocityModified = true;
					}
				}
			}
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 14, 4.0D, 4.0D, 4.0D, -0.30D);
		} else if (this.age == COLLAPSE_END_TICK) {
			// Удаляем все втянутые в сингулярность летящие глыбы в миг детонации
			for (FallingBlockEntity fb : suckedBlocks) {
				if (fb != null && fb.isAlive()) {
					fb.discard();
				}
			}
			suckedBlocks.clear();

			// ВЗРЫВ СВЕРХНОВОЙ!
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.MASTER, 2.8F, 0.60F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.MASTER, 2.8F, 0.50F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 1.8F, 1.25F);
			sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.5F, 0.75F);

			sw.spawnParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 8, 2.0D, 2.0D, 2.0D, 0.0D);
			sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 200, 3.0D, 3.0D, 3.0D, 0.75D);
			sw.spawnParticles(ParticleTypes.FIREWORK, this.getX(), this.getY(), this.getZ(), 150, 3.5D, 3.5D, 3.5D, 0.65D);

			if (mode == 0) {
				detonateCataclysmStarCrater(sw);
			} else {
				detonateStellarQuarryAndForge(sw);
			}
		} else if (this.age < TOTAL_LIFE_TICKS) {
			if (this.age % 3 == 0) {
				sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 6, 9.0D, 6.0D, 9.0D, 0.02D);
			}
			if (this.age % 28 == 0) {
				sw.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.2F, 1.15F);
			}
		} else {
			this.discard();
		}
	}

	private boolean isHostileOrValidTarget(LivingEntity e) {
		if (e instanceof PlayerEntity || e instanceof LuceEntity || e instanceof HolyOrderEntity || e instanceof CatgirlEntity) {
			return false;
		}
		if (e instanceof TameableEntity t && t.isTamed()) {
			return false;
		}
		return e instanceof Monster || e instanceof WhiteCapEntity;
	}

	private void scorchUnderHypergiant(ServerWorld sw) {
		BlockPos center = this.getBlockPos();
		for (int i = 0; i < 18; i++) {
			BlockPos bp = center.add(sw.random.nextBetween(-13, 13), sw.random.nextBetween(-12, 4), sw.random.nextBetween(-13, 13));
			BlockState bs = sw.getBlockState(bp);
			if (bs.isOf(Blocks.WATER) || bs.isOf(Blocks.ICE) || bs.isOf(Blocks.SNOW) || bs.isOf(Blocks.PACKED_ICE)) {
				sw.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				sw.spawnParticles(ParticleTypes.CLOUD, bp.getX() + 0.5D, bp.getY() + 0.6D, bp.getZ() + 0.5D, 2, 0.2D, 0.2D, 0.2D, 0.04D);
			} else if (bs.isOf(Blocks.SAND) || bs.isOf(Blocks.RED_SAND)) {
				sw.setBlockState(bp, Blocks.GLASS.getDefaultState(), Block.NOTIFY_ALL);
			}
		}
	}

	private void ripSurfaceBlocksIntoCore(ServerWorld sw, int count) {
		BlockPos center = this.getBlockPos();
		for (int i = 0; i < count; i++) {
			BlockPos bp = center.add(sw.random.nextBetween(-12, 12), sw.random.nextBetween(-10, -2), sw.random.nextBetween(-12, 12));
			BlockState bs = sw.getBlockState(bp);
			if (bs.isAir() || bs.getHardness(sw, bp) < 0.0F || bs.hasBlockEntity() || isValuableOre(bs)) {
				continue;
			}
			FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(sw, bp, bs);
			fb.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
			fb.dropItem = false;
			fb.setNoGravity(true);
			fb.setHurtEntities(0.0F, 0);
			Vec3d toCore = this.getPos().subtract(fb.getPos()).normalize().multiply(0.75D);
			fb.setVelocity(toCore);
			fb.velocityModified = true;
			suckedBlocks.add(fb);
		}
	}

	/**
	 * Режим 0: Выжигает исполинский 18-блочный оплавленный звёздный кратер, богатый метеоритными рудами и аметистами,
	 * и наносит сокрушительный термоядерный урон всем врагам и боссам в радиусе 38 блоков!
	 */
	private void detonateCataclysmStarCrater(ServerWorld sw) {
		BlockPos epic = this.getBlockPos().down(4);
		int radius = 17;
		int rSq = radius * radius;
		int crustInnerSq = (radius - 2) * (radius - 2);

		for (int dy = -radius; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					int d2 = dx * dx + dy * dy + dz * dz;
					if (d2 > rSq) {
						continue;
					}
					BlockPos bp = epic.add(dx, dy, dz);
					BlockState cur = sw.getBlockState(bp);
					if (cur.getHardness(sw, bp) < 0.0F) {
						continue; // Бедрок и барьеры не ломаем
					}
					if (d2 < crustInnerSq || dy > 2) {
						if (!cur.isAir()) {
							sw.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
						}
					} else if (!cur.isAir() && dy <= 1) {
						// Оплавленная звёздным нуклеосинтезом метеоритная корка с драгоценными жилами!
						int roll = sw.random.nextInt(100);
						BlockState fused;
						if (roll < 3) {
							fused = Blocks.ANCIENT_DEBRIS.getDefaultState();
						} else if (roll < 9) {
							fused = Blocks.DIAMOND_ORE.getDefaultState();
						} else if (roll < 15) {
							fused = Blocks.GOLD_BLOCK.getDefaultState();
						} else if (roll < 22) {
							fused = Blocks.EMERALD_ORE.getDefaultState();
						} else if (roll < 34) {
							fused = Blocks.CRYING_OBSIDIAN.getDefaultState();
						} else if (roll < 48) {
							fused = Blocks.AMETHYST_BLOCK.getDefaultState();
						} else if (roll < 64) {
							fused = Blocks.MAGMA_BLOCK.getDefaultState();
						} else {
							fused = Blocks.OBSIDIAN.getDefaultState();
						}
						sw.setBlockState(bp, fused, Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
					}
				}
			}
		}

		// Сокрушительный термоядерный удар по всем враждебным существам и боссам (до 650 урона!)
		Box blastBox = this.getBoundingBox().expand(40.0D);
		for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, blastBox, e -> e.isAlive() && !(e instanceof PlayerEntity))) {
			if (target instanceof LuceEntity || target instanceof HolyOrderEntity || target instanceof CatgirlEntity
					|| (target instanceof TameableEntity t && t.isTamed())) {
				continue;
			}
			Vec3d push = target.getPos().subtract(this.getPos()).normalize().multiply(2.8D);
			target.damage(sw.getDamageSources().outOfWorld(), 650.0F);
			target.addVelocity(push.x, 1.1D, push.z);
			target.setOnFireFor(15);
		}
	}

	/**
	 * Режим 1: Звёздный Рентген-Карьер и Кузница Нуклеосинтеза!
	 * Испаряет всю пустую породу в сфере 16 блоков, оставляя ВСЕ руды и постройки парить в воздухе,
	 * и превращает брошенные предметы в драгоценности!
	 */
	private void detonateStellarQuarryAndForge(ServerWorld sw) {
		BlockPos epic = this.getBlockPos().down(3);
		int radius = 16;
		int rSq = radius * radius;

		for (int dy = -radius; dy <= radius; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (dx * dx + dy * dy + dz * dz > rSq) {
						continue;
					}
					BlockPos bp = epic.add(dx, dy, dz);
					BlockState bs = sw.getBlockState(bp);
					if (isBarrenRockOrSoil(bs)) {
						sw.setBlockState(bp, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.SKIP_DROPS);
					}
				}
			}
		}

		// Звёздный синтез брошенных предметов + 100% починка любого снаряжения!
		Box forgeBox = this.getBoundingBox().expand(24.0D);
		for (ItemEntity ie : sw.getEntitiesByClass(ItemEntity.class, forgeBox, Entity::isAlive)) {
			ItemStack st = ie.getStack();
			int cnt = st.getCount();
			if (st.isOf(Items.COAL) || st.isOf(Items.CHARCOAL)) {
				ie.setStack(new ItemStack(Items.DIAMOND, cnt));
			} else if (st.isOf(Items.COAL_BLOCK)) {
				ie.setStack(new ItemStack(Items.DIAMOND_BLOCK, cnt));
			} else if (st.isOf(Items.RAW_IRON) || st.isOf(Items.IRON_ORE) || st.isOf(Items.DEEPSLATE_IRON_ORE)) {
				ie.setStack(new ItemStack(Items.IRON_BLOCK, cnt));
			} else if (st.isOf(Items.RAW_GOLD) || st.isOf(Items.GOLD_ORE) || st.isOf(Items.DEEPSLATE_GOLD_ORE)) {
				ie.setStack(new ItemStack(Items.GOLD_BLOCK, cnt));
			} else if (st.isOf(Items.COPPER_INGOT) || st.isOf(Items.RAW_COPPER)) {
				ie.setStack(new ItemStack(Items.EMERALD, cnt));
			} else if (st.isOf(Items.OBSIDIAN)) {
				ie.setStack(new ItemStack(Items.NETHERITE_INGOT, cnt));
			} else if (st.isDamageable() && st.isDamaged()) {
				st.setDamage(0);
			}
			ie.setGlowing(true);
			ie.setInvulnerable(true);
		}

		for (LivingEntity target : sw.getEntitiesByClass(LivingEntity.class, forgeBox.expand(12.0D), e -> e.isAlive() && isHostileOrValidTarget(e))) {
			target.damage(sw.getDamageSources().magic(), 300.0F);
		}
	}

	private static boolean isValuableOre(BlockState bs) {
		return bs.isIn(BlockTags.COAL_ORES)
				|| bs.isIn(BlockTags.IRON_ORES)
				|| bs.isIn(BlockTags.GOLD_ORES)
				|| bs.isIn(BlockTags.DIAMOND_ORES)
				|| bs.isIn(BlockTags.EMERALD_ORES)
				|| bs.isIn(BlockTags.LAPIS_ORES)
				|| bs.isIn(BlockTags.REDSTONE_ORES)
				|| bs.isIn(BlockTags.COPPER_ORES)
				|| bs.isOf(Blocks.ANCIENT_DEBRIS)
				|| bs.isOf(Blocks.NETHER_QUARTZ_ORE)
				|| bs.isOf(Blocks.NETHER_GOLD_ORE);
	}

	private static boolean isBarrenRockOrSoil(BlockState bs) {
		if (isValuableOre(bs) || bs.hasBlockEntity()) {
			return false;
		}
		return bs.isIn(BlockTags.BASE_STONE_OVERWORLD)
				|| bs.isIn(BlockTags.BASE_STONE_NETHER)
				|| bs.isIn(BlockTags.DIRT)
				|| bs.isOf(Blocks.GRAVEL)
				|| bs.isOf(Blocks.SAND)
				|| bs.isOf(Blocks.SANDSTONE)
				|| bs.isOf(Blocks.RED_SAND)
				|| bs.isOf(Blocks.CLAY)
				|| bs.isOf(Blocks.CALCITE)
				|| bs.isOf(Blocks.DRIPSTONE_BLOCK);
	}

	@Override
	public boolean shouldRender(double distance) {
		return distance < 512.0D * 512.0D;
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		setForgeMode(nbt.getInt("Mode"));
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		nbt.putInt("Mode", getForgeMode());
	}
}
