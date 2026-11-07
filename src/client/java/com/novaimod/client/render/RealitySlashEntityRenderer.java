package com.novaimod.client.render;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.RealitySlashEntity;

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

/**
 * 3D-рендерер «Клинка Разлома Реальности»:
 * - Режим 0: летящий изогнутый 3D-серп рассечённого пространства шириной 8 блоков с чёрным разрывом и неоновым лезвием!
 * - Режим 1 («Приговор Пространства»): купол из десятков сверкающих 3D-нитей расколотой реальности в радиусе 48 блоков!
 */
public class RealitySlashEntityRenderer extends EntityRenderer<RealitySlashEntity> {
	public RealitySlashEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(RealitySlashEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 220.0D * 220.0D;
	}

	@Override
	public void render(
			RealitySlashEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		if (entity.getMode() == RealitySlashEntity.MODE_JUDGMENT_CUT_END) {
			renderJudgmentCutDomain(entity, tickDelta, matrices, vertexConsumers);
		} else {
			renderCrescentWave(entity, matrices, vertexConsumers);
		}
	}

	private void renderCrescentWave(
			RealitySlashEntity entity,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers
	) {
		matrices.push();
		matrices.translate(0.0F, 0.35F, 0.0F);

		float yawRad = -entity.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		float pitchRad = entity.getPitch() * MathHelper.RADIANS_PER_DEGREE;
		float rollDeg = ((entity.getId() * 41) % 90) - 45.0F;
		float rollRad = rollDeg * MathHelper.RADIANS_PER_DEGREE;

		matrices.multiply(new Quaternionf().rotationY(yawRad));
		matrices.multiply(new Quaternionf().rotationX(pitchRad));
		matrices.multiply(new Quaternionf().rotationZ(rollRad));

		Matrix4f m = matrices.peek().getPositionMatrix();
		VertexConsumer glowVc = vertexConsumers.getBuffer(RenderLayer.getLightning());

		int segs = 18;
		float arcSpan = 2.25F; // ~130 градусов дуги серпа
		float outerR = 4.35F;
		float midR = 3.55F;
		float trailR = 2.35F;

		for (int i = 0; i < segs; i++) {
			float t0 = (float) i / segs;
			float t1 = (float) (i + 1) / segs;
			float a0 = -arcSpan * 0.5F + arcSpan * t0;
			float a1 = -arcSpan * 0.5F + arcSpan * t1;

			// Сужение краёв серпа к остриям!
			float taper0 = MathHelper.sin(t0 * (float) Math.PI);
			float taper1 = MathHelper.sin(t1 * (float) Math.PI);

			float xOuter0 = MathHelper.sin(a0) * outerR;
			float zOuter0 = MathHelper.cos(a0) * outerR - 2.6F;
			float xOuter1 = MathHelper.sin(a1) * outerR;
			float zOuter1 = MathHelper.cos(a1) * outerR - 2.6F;

			float rMid0 = MathHelper.lerp(taper0, outerR, midR);
			float rMid1 = MathHelper.lerp(taper1, outerR, midR);
			float xMid0 = MathHelper.sin(a0) * rMid0;
			float zMid0 = MathHelper.cos(a0) * rMid0 - 2.6F;
			float xMid1 = MathHelper.sin(a1) * rMid1;
			float zMid1 = MathHelper.cos(a1) * rMid1 - 2.6F;

			float rInner0 = MathHelper.lerp(taper0, outerR, trailR);
			float rInner1 = MathHelper.lerp(taper1, outerR, trailR);
			float xIn0 = MathHelper.sin(a0) * rInner0;
			float zIn0 = MathHelper.cos(a0) * rInner0 - 2.6F;
			float xIn1 = MathHelper.sin(a1) * rInner1;
			float zIn1 = MathHelper.cos(a1) * rInner1 - 2.6F;

			// Ослепительная бело-бирюзовая режущая кромка
			glowVc.vertex(m, xOuter0, 0.04F, zOuter0).color(235, 255, 255, 245);
			glowVc.vertex(m, xOuter1, 0.04F, zOuter1).color(235, 255, 255, 245);
			glowVc.vertex(m, xMid1, 0.0F, zMid1).color(75, 225, 255, 210);
			glowVc.vertex(m, xMid0, 0.0F, zMid0).color(75, 225, 255, 210);

			// Пурпурно-фиолетовый шлейф разорванной ткани реальности
			glowVc.vertex(m, xMid0, 0.0F, zMid0).color(195, 70, 255, 195);
			glowVc.vertex(m, xMid1, 0.0F, zMid1).color(195, 70, 255, 195);
			glowVc.vertex(m, xIn1, -0.04F, zIn1).color(95, 15, 180, 0);
			glowVc.vertex(m, xIn0, -0.04F, zIn0).color(95, 15, 180, 0);
		}

		matrices.pop();
	}

	private void renderJudgmentCutDomain(
			RealitySlashEntity entity,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		boolean shattered = exactAge >= 25.0F;

		matrices.push();
		matrices.translate(0.0F, 1.2F, 0.0F);
		Matrix4f m = matrices.peek().getPositionMatrix();
		VertexConsumer glowVc = vertexConsumers.getBuffer(RenderLayer.getLightning());

		Random rng = new Random(entity.getId() * 918273L);
		int totalCuts = shattered ? 56 : Math.min(44, (int) (exactAge * 2.2F) + 6);
		float alphaScale = shattered
				? MathHelper.clamp((34.0F - exactAge) / 9.0F, 0.0F, 1.0F)
				: MathHelper.clamp(exactAge / 8.0F, 0.25F, 1.0F);

		for (int i = 0; i < totalCuts; i++) {
			float cx = (rng.nextFloat() - 0.5F) * 42.0F;
			float cy = rng.nextFloat() * 18.0F - 2.0F;
			float cz = (rng.nextFloat() - 0.5F) * 42.0F;

			float dx = (rng.nextFloat() - 0.5F) * 26.0F;
			float dy = (rng.nextFloat() - 0.5F) * 14.0F;
			float dz = (rng.nextFloat() - 0.5F) * 26.0F;

			float thickness = shattered ? 0.28F : (0.09F + 0.04F * MathHelper.sin(realSec * 14.0F + i));
			int r = shattered ? 255 : (i % 2 == 0 ? 120 : 225);
			int g = shattered ? 245 : (i % 2 == 0 ? 240 : 85);
			int b = 255;
			int a = (int) ((shattered ? 245 : 185) * alphaScale);

			drawSpatialThread(m, glowVc, cx - dx, cy - dy, cz - dz, cx + dx, cy + dy, cz + dz, thickness, r, g, b, a);
		}

		matrices.pop();
	}

	private void drawSpatialThread(
			Matrix4f m,
			VertexConsumer vc,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float halfWidth,
			int r, int g, int b, int a
	) {
		vc.vertex(m, x0, y0 - halfWidth, z0).color(r, g, b, a);
		vc.vertex(m, x1, y1 - halfWidth, z1).color(r, g, b, a);
		vc.vertex(m, x1, y1 + halfWidth, z1).color(255, 255, 255, a);
		vc.vertex(m, x0, y0 + halfWidth, z0).color(255, 255, 255, a);

		vc.vertex(m, x0 - halfWidth, y0, z0).color(r, g, b, a);
		vc.vertex(m, x1 - halfWidth, y1, z1).color(r, g, b, a);
		vc.vertex(m, x1 + halfWidth, y1, z1).color(255, 255, 255, a);
		vc.vertex(m, x0 + halfWidth, y0, z0).color(255, 255, 255, a);
	}

	@Override
	public Identifier getTexture(RealitySlashEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
