package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.SingularityOrbEntity;

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
 * Объёмный 3D-рендерер Чёрной дыры («Сфера Сингулярности»):
 * рисует абсолютно чёрную сферу горизонта событий, фотонное кольцо и вращающийся 3D аккреционный диск!
 */
public class SingularityOrbEntityRenderer extends EntityRenderer<SingularityOrbEntity> {
	public SingularityOrbEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(SingularityOrbEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 256.0D * 256.0D;
	}

	@Override
	public void render(
			SingularityOrbEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float pulse = 1.0F + 0.08F * MathHelper.sin(realSec * 6.0F);

		matrices.push();
		matrices.translate(0.0F, 0.5F, 0.0F);

		// 1. Чёрный горизонт событий (непрозрачная тёмная 3D-сфера)
		VertexConsumer darkVc = vertexConsumers.getBuffer(RenderLayer.getGui());
		drawSphere(matrices.peek().getPositionMatrix(), darkVc, 1.55F * pulse, 6, 3, 14, 250);

		// 2. Светящееся фотонное гало + наклонённый вращающийся аккреционный диск и полярные джеты
		VertexConsumer glowVc = vertexConsumers.getBuffer(RenderLayer.getLightning());
		drawSphere(matrices.peek().getPositionMatrix(), glowVc, 1.78F * pulse, 185, 90, 255, 95);

		matrices.push();
		matrices.multiply(new Quaternionf().rotationXYZ(0.42F, realSec * 2.8F, 0.22F));
		drawAccretionDisk(matrices.peek().getPositionMatrix(), glowVc, 1.9F, 5.4F);
		matrices.pop();

		matrices.pop();
	}

	private void drawSphere(Matrix4f m, VertexConsumer vc, float radius, int r, int g, int b, int a) {
		int lat = 8;
		int lon = 12;
		for (int i = 0; i < lat; i++) {
			float v0 = (float) Math.PI * i / lat;
			float v1 = (float) Math.PI * (i + 1) / lat;
			float y0 = MathHelper.cos(v0) * radius;
			float r0 = MathHelper.sin(v0) * radius;
			float y1 = MathHelper.cos(v1) * radius;
			float r1 = MathHelper.sin(v1) * radius;
			for (int j = 0; j < lon; j++) {
				float u0 = MathHelper.TAU * j / lon;
				float u1 = MathHelper.TAU * (j + 1) / lon;
				vc.vertex(m, MathHelper.cos(u0) * r0, y0, MathHelper.sin(u0) * r0).color(r, g, b, a);
				vc.vertex(m, MathHelper.cos(u1) * r0, y0, MathHelper.sin(u1) * r0).color(r, g, b, a);
				vc.vertex(m, MathHelper.cos(u1) * r1, y1, MathHelper.sin(u1) * r1).color(r, g, b, a);
				vc.vertex(m, MathHelper.cos(u0) * r1, y1, MathHelper.sin(u0) * r1).color(r, g, b, a);
			}
		}
	}

	private void drawAccretionDisk(Matrix4f m, VertexConsumer vc, float innerR, float outerR) {
		int steps = 24;
		for (int i = 0; i < steps; i++) {
			float a0 = MathHelper.TAU * i / steps;
			float a1 = MathHelper.TAU * (i + 1) / steps;
			float c0 = MathHelper.cos(a0);
			float s0 = MathHelper.sin(a0);
			float c1 = MathHelper.cos(a1);
			float s1 = MathHelper.sin(a1);

			// Верхняя и нижняя стороны раскалённого аккреционного диска
			vc.vertex(m, c0 * innerR, 0.0F, s0 * innerR).color(255, 240, 175, 230);
			vc.vertex(m, c1 * innerR, 0.0F, s1 * innerR).color(255, 240, 175, 230);
			vc.vertex(m, c1 * outerR, 0.0F, s1 * outerR).color(255, 95, 30, 0);
			vc.vertex(m, c0 * outerR, 0.0F, s0 * outerR).color(255, 95, 30, 0);
		}
	}

	@Override
	public Identifier getTexture(SingularityOrbEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
