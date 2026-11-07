package com.novaimod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.novaimod.entity.CelestialOrbEntity;
import com.novaimod.entity.InfinityEyeEntity;
import com.novaimod.entity.SingularityOrbEntity;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

/**
 * Замораживает все частицы в мире на время вечной остановки времени («Хроно-Скипетр»),
 * но позволяет частицам креативных сфер («Жезл Небесной Кары», «Сфера Сингулярности», «Око Бескрайности»)
 * эффектно бурлить в небе даже посреди застывшего мира!
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	@Shadow
	protected ClientWorld world;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void novai$freezeParticlesUnlessCreativeOrbActive(CallbackInfo ci) {
		if (this.world == null || !this.world.getTickManager().isFrozen()) {
			return;
		}
		for (Entity e : this.world.getEntities()) {
			if (e instanceof CelestialOrbEntity || e instanceof SingularityOrbEntity || e instanceof InfinityEyeEntity) {
				return;
			}
		}
		ci.cancel();
	}
}
