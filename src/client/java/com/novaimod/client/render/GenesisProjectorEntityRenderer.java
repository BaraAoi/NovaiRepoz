package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.GenesisProjectorEntity;

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
 * 3D-рендерер Голографического Тессеракта («Architect's Genesis Cube»):
 * рисует парящий двойной 4D-гиперкуб из светящихся лазерных рёбер и сканирующие кольца сборки!
 */
public class GenesisProjectorEntityRenderer extends EntityRenderer<GenesisProjectorEntity> {
	private static final float[][] CUBE_VERTS = new float[][]{
			{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1},
			{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}
	};
	private static final int[][] CUBE_EDGES = new int[][]{
			{0, 1}, {1, 2}, {2, 3}, {3, 0},
			{4, 5}, {5, 6}, {6, 7}, {7, 4},
			{0, 4}, {1, 5}, {2, 6}, {3, 7}
	};

	public GenesisProjectorEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(GenesisProjectorEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 320.0D * 320.0D;
	}

	@Override
	public void render(
			GenesisProjectorEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		float fadeOut = MathHelper.clamp((GenesisProjectorEntity.TOTAL_TICKS - exactAge) / 15.0F, 0.0F, 1.0F);
		if (fadeOut <= 0.01F) {
			return;
		}

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
		int alpha = (int) (225.0F * fadeOut);

		matrices.push();
		matrices.translate(0.0F, 4.5F + 0.35F * MathHelper.sin(realSec * 3.2F), 0.0F);

		// Внешний и внутренний 3D-кубы тессеракта + соединяющие диагонали 4-го измерения
		matrices.push();
		matrices.multiply(new Quaternionf().rotationXYZ(realSec * 0.9F, realSec * 1.35F, realSec * 0.55F));
		Matrix4f m = matrices.peek().getPositionMatrix();
		float outerS = 1.9F;
		float innerS = 0.85F;

		for (int[] e : CUBE_EDGES) {
			float[] a = CUBE_VERTS[e[0]];
			float[] b = CUBE_VERTS[e[1]];
			drawBeam(m, vc, a[0] * outerS, a[1] * outerS, a[2] * outerS,
					b[0] * outerS, b[1] * outerS, b[2] * outerS, 0.075F, 75, 240, 255, alpha);
			drawBeam(m, vc, a[0] * innerS, a[1] * innerS, a[2] * innerS,
					b[0] * innerS, b[1] * innerS, b[2] * innerS, 0.065F, 255, 225, 110, alpha);
		}
		for (float[] v : CUBE_VERTS) {
			drawBeam(m, vc, v[0] * innerS, v[1] * innerS, v[2] * innerS,
					v[0] * outerS, v[1] * outerS, v[2] * outerS, 0.045F, 185, 250, 255, alpha * 3 / 4);
		}
		matrices.pop();

		// Горизонтальное сканирующее кольцо постройки
		float scanProg = MathHelper.clamp(exactAge / GenesisProjectorEntity.BUILD_TICKS, 0.0F, 1.0F);
		float scanR = 2.0F + scanProg * 9.5F;
		matrices.push();
		matrices.translate(0.0F, -3.5F + scanProg * 7.0F, 0.0F);
		matrices.multiply(new Quaternionf().rotationY(realSec * 3.0F));
		drawScanRing(matrices.peek().getPositionMatrix(), vc, scanR - 0.25F, scanR, 36, 85, 245, 255, (int) (185 * fadeOut));
		matrices.pop();

		matrices.pop();
	}

	private void drawBeam(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float w, int r, int g, int b, int a) {
		vc.vertex(m, x0, y0 - w, z0).color(r, g, b, a);
		vc.vertex(m, x0, y0 + w, z0).color(255, 255, 255, a);
		vc.vertex(m, x1, y1 + w, z1).color(255, 255, 255, a);
		vc.vertex(m, x1, y1 - w, z1).color(r, g, b, a);

		vc.vertex(m, x0 - w, y0, z0).color(r, g, b, a);
		vc.vertex(m, x0 + w, y0, z0).color(255, 255, 255, a);
		vc.vertex(m, x1 + w, y1, z1).color(255, 255, 255, a);
		vc.vertex(m, x1 - w, y1, z1).color(r, g, b, a);
	}

	private void drawScanRing(Matrix4f m, VertexConsumer vc, float r0, float r1, int segs, int cr, int cg, int cb, int ca) {
		for (int i = 0; i < segs; i++) {
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			vc.vertex(m, MathHelper.cos(a0) * r0, 0.0F, MathHelper.sin(a0) * r0).color(cr, cg, cb, ca);
			vc.vertex(m, MathHelper.cos(a1) * r0, 0.0F, MathHelper.sin(a1) * r0).color(cr, cg, cb, ca);
			vc.vertex(m, MathHelper.cos(a1) * r1, 0.0F, MathHelper.sin(a1) * r1).color(cr, cg, cb, ca / 3);
			vc.vertex(m, MathHelper.cos(a0) * r1, 0.0F, MathHelper.sin(a0) * r1).color(cr, cg, cb, ca / 3);
		}
	}

	@Override
	public Identifier getTexture(GenesisProjectorEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
