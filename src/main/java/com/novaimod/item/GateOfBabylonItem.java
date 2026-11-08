package com.novaimod.item;

import java.util.List;

import com.novaimod.data.ModPersistentState;
import com.novaimod.data.WorldVirginLedger;
import com.novaimod.entity.BabylonTreasuryEntity;
import com.novaimod.registry.ModEntities;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class GateOfBabylonItem extends Item {
	private static final String KEY_MODE = "BabylonMode";

	public GateOfBabylonItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getMode(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return 0;
		}
		return MathHelper.clamp(comp.copyNbt().getInt(KEY_MODE), 0, 2);
	}

	public static String modeTitle(int mode) {
		return switch (mode) {
			case 1 -> "Небесная Корона 360° (Авто-зачистка врагов + Золотая дань)";
			case 2 -> "Золотая Сокровищница (Личный вечный склад на 54 слота)";
			default -> "Веер Царя Героев + Цепи Энкиду (Сковывание Боссов и Залп)";
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking()) {
				int next = (getMode(stack) + 1) % 3;
				NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				nbt.putInt(KEY_MODE, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.3F, 1.35F);
				sp.sendMessage(Text.literal("⚜ Указ Врат Вавилона: ").formatted(Formatting.GOLD)
						.append(Text.literal(modeTitle(next)).formatted(Formatting.AQUA, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			int mode = getMode(stack);
			ModPersistentState state = ModPersistentState.get(sw.getServer());

			// РЕЖИМ 2: Открытие персональной 54-слотовой Золотой Сокровищницы Вавилона!
			if (mode == 2) {
				openBabylonVault(sw, sp, state);
				return TypedActionResult.success(stack, false);
			}

			boolean crown = (mode == 1);
			int storedRelics = state.countBabylonVaultRelics(sp.getUuid());
			BabylonTreasuryEntity treasury = ModEntities.BABYLON_TREASURY_ENTITY.create(sw);
			if (treasury != null) {
				treasury.addCommandTag(WorldVirginLedger.TAG_TEMP_SUMMON);
				treasury.refreshPositionAndAngles(sp.getX(), sp.getY(), sp.getZ(), sp.getYaw(), 0.0F);
				treasury.setOwner(sp);
				treasury.setCrownMode(crown);
				treasury.setVaultRelicBonus(storedRelics);
				sw.spawnEntity(treasury);
			}
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.5F, 1.35F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 1.8F, 1.15F);
			sp.sendMessage(
					Text.literal(crown
							? "⚜ ВРАТА ВАВИЛОНА: 32 золотых портала сомкнулись в Корону! [Реликвий в хранилище: " + storedRelics + "/54]"
							: "⚜ ВРАТА ВАВИЛОНА: 18 врат и Цепи Энкиду активированы! [Реликвий в хранилище: " + storedRelics + "/54]")
							.formatted(Formatting.GOLD, Formatting.BOLD),
					true
			);
			sp.getItemCooldownManager().set(this, 24);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	private static void openBabylonVault(ServerWorld sw, ServerPlayerEntity sp, ModPersistentState state) {
		DefaultedList<ItemStack> backing = state.getBabylonVault(sp.getUuid());
		Inventory vaultInv = new Inventory() {
			@Override
			public int size() {
				return 54;
			}

			@Override
			public boolean isEmpty() {
				for (ItemStack s : backing) {
					if (!s.isEmpty()) {
						return false;
					}
				}
				return true;
			}

			@Override
			public ItemStack getStack(int slot) {
				return backing.get(slot);
			}

			@Override
			public ItemStack removeStack(int slot, int amount) {
				ItemStack res = net.minecraft.inventory.Inventories.splitStack(backing, slot, amount);
				if (!res.isEmpty()) {
					markDirty();
				}
				return res;
			}

			@Override
			public ItemStack removeStack(int slot) {
				ItemStack res = net.minecraft.inventory.Inventories.removeStack(backing, slot);
				markDirty();
				return res;
			}

			@Override
			public void setStack(int slot, ItemStack stack) {
				backing.set(slot, stack);
				if (stack.getCount() > getMaxCountPerStack()) {
					stack.setCount(getMaxCountPerStack());
				}
				markDirty();
			}

			@Override
			public void markDirty() {
				state.markDirty();
			}

			@Override
			public boolean canPlayerUse(PlayerEntity player) {
				return true;
			}

			@Override
			public void clear() {
				backing.clear();
				markDirty();
			}
		};

		Vec3d front = sp.getEyePos().add(sp.getRotationVec(1.0F).multiply(1.6D));
		sw.spawnParticles(ParticleTypes.WAX_ON, front.x, front.y, front.z, 24, 0.45D, 0.45D, 0.45D, 0.05D);
		sw.spawnParticles(ParticleTypes.END_ROD, front.x, front.y, front.z, 16, 0.35D, 0.35D, 0.35D, 0.03D);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_ENDER_CHEST_OPEN, SoundCategory.PLAYERS, 1.0F, 1.35F);
		sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.4F, 1.2F);

		sp.openHandledScreen(new SimpleNamedScreenHandlerFactory(
				(syncId, playerInv, p) -> GenericContainerScreenHandler.createGeneric9x6(syncId, playerInv, vaultInv),
				Text.literal("⚜ Золотая Сокровищница Вавилона (54 слота) ⚜").formatted(Formatting.GOLD, Formatting.BOLD)
		));
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int mode = getMode(stack);
		tooltip.add(Text.literal("⚜ СОКРОВИЩНИЦА И ОРУЖИЕ ЦАРЯ ГЕРОЕВ ⚜").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("Текущая задача: ").formatted(Formatting.GRAY)
				.append(Text.literal(modeTitle(mode)).formatted(Formatting.AQUA)));
		tooltip.add(Text.literal("• Режим 1: 18 врат + Цепи Энкиду (сковывают даже Боссов!)").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Режим 2: 32 врат 360° + обращение врагов в Золотые слитки").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("• Режим 3: Вечный склад на 54 слота (не сбрасывается даже").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("  при Перезапуске Вселенной и усиливает урон копий!)").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Shift + ПКМ: Переключить режим").formatted(Formatting.GRAY));
	}
}
