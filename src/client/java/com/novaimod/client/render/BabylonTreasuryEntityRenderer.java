package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.BabylonTreasuryEntity;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 3D-рендерер «Gate of Babylon» (Врата Вавилона):
 * рисует 18 (или 32 в режиме Короны) сияющих золотых порталов с концентрической рябью жидкого золота,
 * из каждого из которых выдвигается светящийся 3D-клинок/копьё перед сверхзвуковым выстрелом!
 */
public class BabylonTreasuryEntityRenderer extends EntityRenderer<BabylonTreasuryEntity> {
	public BabylonTreasuryEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(BabylonTreasuryEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 320.0D * 320.0D;
	}

	@Override
	public void render(
			BabylonTreasuryEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		float fadeOut = MathHelper.clamp((BabylonTreasuryEntity.TOTAL_TICKS - exactAge) / 12.0F, 0.0F, 1.0F);
		if (fadeOut <= 0.01F) {
			return;
		}

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
		int count = entity.getPortalCount();
		float ownerYaw = entity.getOwnerYaw();
		boolean crown = entity.isCrownMode();

		for (int i = 0; i < count; i++) {
			// Плавное поочерёдное раскрытие каждого золотого портала
			float staggeredOpen = MathHelper.clamp((exactAge - i * 0.55F) / 9.0F, 0.0F, 1.0F);
			if (staggeredOpen <= 0.02F) {
				continue;
			}
			float portalScale = staggeredOpen * fadeOut * (0.94F + 0.08F * MathHelper.sin(realSec * 5.0F + i));
			Vec3d off = entity.computePortalOffset(i, ownerYaw);

			matrices.push();
			matrices.translate(off.x, off.y, off.z);

			if (crown) {
				float radialYaw = (float) Math.atan2(off.x, off.z);
				matrices.multiply(new Quaternionf().rotationY(radialYaw));
				matrices.multiply(new Quaternionf().rotationX(0.45F));
			} else {
				matrices.multiply(new Quaternionf().rotationY(-ownerYaw * MathHelper.RADIANS_PER_DEGREE));
				matrices.multiply(new Quaternionf().rotationX(0.16F));
			}

			matrices.scale(portalScale, portalScale, portalScale);
			Matrix4f m = matrices.peek().getPositionMatrix();

			// 1. Внутренний золотой омут и 3 концентрических круга ряби жидкого золота (в плоскости XY, лицо +Z)
			drawPortalDiskXY(m, vc, 0.0F, 0.52F, 18, 255, 245, 170, 225);
			drawPortalDiskXY(m, vc, 0.52F, 0.86F, 20, 255, 195, 45, 175);
			float ripple = 0.88F + 0.24F * ((realSec * 1.8F + i * 0.19F) % 1.0F);
			drawPortalDiskXY(m, vc, ripple, ripple + 0.11F, 20, 255, 232, 120, 155);

			// 2. Выдвигающийся из золотой ряби мифический 3D-клинок (скользит вперёд вдоль +Z и перезаряжается!)
			float cycle = (realSec * 2.2F + i * 0.31F) % 1.0F;
			float emergeZ = MathHelper.lerp(MathHelper.clamp(cycle * 1.35F, 0.0F, 1.0F), -0.25F, 1.25F);
			drawEmergingGoldenBlade(m, vc, emergeZ);

			matrices.pop();
		}
	}

	private void drawPortalDiskXY(Matrix4f m, VertexConsumer vc, float innerR, float outerR, int segs, int r, int g, int b, int a) {
		for (int i = 0; i < segs; i++) {
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			float c0 = MathHelper.cos(a0);
			float s0 = MathHelper.sin(a0);
			float c1 = MathHelper.cos(a1);
			float s1 = MathHelper.sin(a1);
			vc.vertex(m, c0 * innerR, s0 * innerR, 0.0F).color(r, g, b, a);
			vc.vertex(m, c1 * innerR, s1 * innerR, 0.0F).color(r, g, b, a);
			vc.vertex(m, c1 * outerR, s1 * outerR, 0.0F).color(r, g, b, a / 2);
			vc.vertex(m, c0 * outerR, s0 * outerR, 0.0F).color(r, g, b, a / 2);
		}
	}

	private void drawEmergingGoldenBlade(Matrix4f m, VertexConsumer vc, float tipZ) {
		float baseZ = Math.max(0.0F, tipZ - 1.15F);
		if (tipZ <= 0.05F) {
			return;
		}
		float w = 0.11F;
		// Горизонтальная грань клинка
		vc.vertex(m, -w, 0.0F, baseZ).color(255, 215, 75, 235);
		vc.vertex(m, w, 0.0F, baseZ).color(255, 215, 75, 235);
		vc.vertex(m, 0.0F, 0.0F, tipZ).color(255, 255, 255, 250);
		vc.vertex(m, 0.0F, 0.0F, tipZ).color(255, 255, 255, 250);

		// Вертикальная грань клинка (крестовое свечение)
		vc.vertex(m, 0.0F, -w, baseZ).color(185, 248, 255, 225);
		vc.vertex(m, 0.0F, w, baseZ).color(185, 248, 255, 225);
		vc.vertex(m, 0.0F, 0.0F, tipZ).color(255, 255, 255, 250);
		vc.vertex(m, 0.0F, 0.0F, tipZ).color(255, 255, 255, 250);
	}

	@Override
	public Identifier getTexture(BabylonTreasuryEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
