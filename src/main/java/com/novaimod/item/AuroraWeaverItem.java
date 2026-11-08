package com.novaimod.item;

import java.util.List;

import com.novaimod.entity.AuroraPhenomenonEntity;
import com.novaimod.registry.ModEntities;

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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class AuroraWeaverItem extends Item {
	private static final String KEY_PALETTE = "AuroraPalette";

	public AuroraWeaverItem(Settings settings) {
		super(settings);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	public static int getPalette(ItemStack stack) {
		NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (comp == null) {
			return 0;
		}
		return MathHelper.clamp(comp.copyNbt().getInt(KEY_PALETTE), 0, 3);
	}

	public static String paletteTitle(int p) {
		return switch (p) {
			case 1 -> "Космическая Туманность Андромеды (Пурпур / Розовое Золото)";
			case 2 -> "Золотое Сияние Вальгаллы (Солнечное Золото / Алый Закат)";
			case 3 -> "Призматическая Радуга Мироздания (Полный Спектр)";
			default -> "Полярный Изумруд и Аметист (Классическое Сияние)";
		};
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && world instanceof ServerWorld sw && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking()) {
				int next = (getPalette(stack) + 1) % 4;
				NbtComponent comp = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
				NbtCompound nbt = comp.copyNbt();
				nbt.putInt(KEY_PALETTE, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 1.3F);
				sp.sendMessage(Text.literal("🌌 Палитра Северного Сияния: ").formatted(Formatting.AQUA)
						.append(Text.literal(paletteTitle(next)).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			int palette = getPalette(stack);
			AuroraPhenomenonEntity aurora = ModEntities.AURORA_PHENOMENON_ENTITY.create(sw);
			if (aurora != null) {
				aurora.refreshPositionAndAngles(sp.getX(), sp.getY() + 28.0D, sp.getZ(), 0.0F, 0.0F);
				aurora.setPalette(palette);
				sw.spawnEntity(aurora);
			}
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.MASTER, 2.0F, 0.85F);
			sw.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 1.2F, 1.45F);
			sp.sendMessage(Text.literal("🌌 Над миром раскинулись занавеси Северного Сияния и Звездопада!").formatted(Formatting.AQUA, Formatting.BOLD), true);
			sp.getItemCooldownManager().set(this, 24);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		tooltip.add(Text.literal("🌌 КРЕАТИВНЫЙ АРТЕФАКТ НЕБЕСНОЙ КРАСОТЫ 🌌").formatted(Formatting.AQUA, Formatting.BOLD));
		tooltip.add(Text.literal("ПКМ: Соткать в небе 95-блочные колышущиеся 3D-занавеси").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Северного Сияния и живой поток падающих звёзд!").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("Shift + ПКМ: Сменить палитру (" + paletteTitle(getPalette(stack)) + ")").formatted(Formatting.LIGHT_PURPLE));
	}
}
