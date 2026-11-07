package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.entity.CelestialMeteorEntity;
import com.novaimod.entity.CelestialOrbEntity;
import com.novaimod.entity.InfinityEyeEntity;
import com.novaimod.entity.InfinityPearlEntity;
import com.novaimod.entity.SingularityOrbEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.world.tick.TickManager;

/**
 * Позволяет креативным сущностям («Жезл Небесной Кары», «Око Бескрайности», «Сфера Сингулярности», молнии)
 * свободно действовать даже во время вечной остановки времени от «Хроно-Скипетра»,
 * а стрелам игрока — отлетать на пару метров от лука и застывать в воздухе перед целью!
 */
@Mixin(TickManager.class)
public abstract class TickManagerMixin {
	@Inject(method = "shouldSkipTick", at = @At("HEAD"), cancellable = true)
	private void novai$allowCreativeArtifactsInFrozenTime(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof CelestialOrbEntity
				|| entity instanceof InfinityEyeEntity
				|| entity instanceof InfinityPearlEntity
				|| entity instanceof SingularityOrbEntity
				|| entity instanceof CelestialMeteorEntity
				|| entity instanceof LightningEntity) {
			cir.setReturnValue(false);
			return;
		}
		// Если игрок стреляет из лука/арбалета во время остановленного времени — стрела вылетает перед игроком на 4 тика и замирает в воздухе!
		if (entity instanceof ProjectileEntity proj && proj.getOwner() instanceof PlayerEntity && entity.age < 4) {
			cir.setReturnValue(false);
		}
	}
}
