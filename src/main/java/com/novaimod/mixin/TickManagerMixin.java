package com.novaimod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.novaimod.entity.CelestialMeteorEntity;
import com.novaimod.entity.CelestialOrbEntity;
import com.novaimod.entity.InfinityEyeEntity;
import com.novaimod.entity.InfinityPearlEntity;
import com.novaimod.entity.OrbitalLaserEntity;
import com.novaimod.entity.RealitySlashEntity;
import com.novaimod.entity.SingularityOrbEntity;
import com.novaimod.entity.TerraCataclysmEntity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.world.tick.TickManager;

/**
 * Позволяет всем креативным сущностям (включая новинки v3.6.0: Орбитальный Лазер, Разрез Реальности,
 * Катаклизм Геоманта и Зеркало Повелителя Миров) свободно действовать даже во время вечной остановки времени!
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
				|| entity instanceof OrbitalLaserEntity
				|| entity instanceof RealitySlashEntity
				|| entity instanceof TerraCataclysmEntity
				|| entity instanceof EvokerFangsEntity
				|| entity instanceof LightningEntity
				|| entity.getCommandTags().contains(TerraCataclysmEntity.TAG_CREATIVE_BOULDER)
				|| entity.getCommandTags().contains(TerraCataclysmEntity.TAG_ASTRAL_LIFTED)) {
			cir.setReturnValue(false);
			return;
		}
		// Если игрок стреляет из лука/арбалета во время остановленного времени — стрела вылетает перед игроком на 4 тика и замирает в воздухе!
		if (entity instanceof ProjectileEntity proj && proj.getOwner() instanceof PlayerEntity && entity.age < 4) {
			cir.setReturnValue(false);
		}
	}
}
