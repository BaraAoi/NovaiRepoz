package com.novaimod.item;

import java.util.List;

import com.novaimod.world.TimeOfTheWorldManager;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * «Time of the World (Made in Heaven)»:
 * - Режим 0 (по умолчанию): «Полный Перезапуск в Девственный Мир» — плавно разгоняет смену Дня и Ночи
 *   всё быстрее и быстрее, а в Сингулярности возвращает ВЕСЬ МИР в девственно чистое изначальное состояние
 *   (все когда-либо сломанные/взорванные/поставленные блоки и все убитые мобы возвращаются на свои места!).
 * - Режим 1 (Shift+ПКМ вне разгона): «Прокрутка Эпох (Урожай и Плавка без сброса)» — ускоряет время,
 *   выращивает все фермы/деревья и ускоряет плавку в печах вокруг без отката построек!
 */
public class TimeOfTheWorldItem extends Item {
	private static final String KEY_MODE = "ChronoMode";

	public TimeOfTheWorldItem(Settings settings) {
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
		return comp.copyNbt().getInt(KEY_MODE) == 1 ? 1 : 0;
	}

	public static String modeTitle(int mode) {
		return mode == 1
				? "Прокрутка Эпох (Рост урожая и плавка печей без сброса мира)"
				: "Перезапуск в Девственно Чистый Мир (Эталон при создании мира)";
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
			if (sp.isSneaking() && !TimeOfTheWorldManager.isActive()) {
				int next = getMode(stack) == 0 ? 1 : 0;
				NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
				nbt.putInt(KEY_MODE, next);
				stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
				sp.getServerWorld().playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 1.35F);
				sp.sendMessage(Text.literal("⏳ Режим Time of the World: ").formatted(Formatting.GOLD)
						.append(Text.literal(modeTitle(next)).formatted(Formatting.AQUA, Formatting.BOLD)), true);
				return TypedActionResult.success(stack, false);
			}

			boolean virginReset = (getMode(stack) == 0);
			TimeOfTheWorldManager.startOrFinish(sp, virginReset);
			sp.getItemCooldownManager().set(this, 10);
		}
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
		int mode = getMode(stack);
		tooltip.add(Text.literal("✦ КРЕАТИВНЫЙ АРТЕФАКТ ВСЕЛЕННОЙ («MADE IN HEAVEN») ✦").formatted(Formatting.GOLD, Formatting.BOLD));
		tooltip.add(Text.literal("Задача: ").formatted(Formatting.GRAY)
				.append(Text.literal(modeTitle(mode)).formatted(Formatting.AQUA)));
		tooltip.add(Text.literal("• ПКМ: Плавный разгон Дня и Ночи (1x -> 2400x) и Перезапуск Мира!").formatted(Formatting.YELLOW));
		tooltip.add(Text.literal("  Восстанавливает ВСЕ блоки и ТОЛЬКО изначальных существ мира").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("  (коренных животных без размножения и NPC/мобов всех строений —").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("  приручённые и Кошкодевочки возвращаются обратно в свои клетки/места!).").formatted(Formatting.GREEN));
		tooltip.add(Text.literal("• Повторный ПКМ: Мгновенный финал | Shift+ПКМ: Сменить режим").formatted(Formatting.GRAY));
	}
}
