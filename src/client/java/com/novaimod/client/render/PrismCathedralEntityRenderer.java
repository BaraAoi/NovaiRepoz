package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.PrismCathedralEntity;

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
 * 3D-рендерер «Призмы Зеркального Измерения» (Mirror Dimension Prism):
 * - Вращающиеся шестиконечные и двенадцатиконечные кристальные мандалы на земле и в зените;
 * - 24 парящих 3D алмазных призматических осколка по периметру купола;
 * - Переливающиеся радужные лазерные хорды и ослепительное схождение лучей в финале!
 */
public class PrismCathedralEntityRenderer extends EntityRenderer<PrismCathedralEntity> {
	public PrismCathedralEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(PrismCathedralEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 320.0D * 320.0D;
	}

	@Override
	public void render(
			PrismCathedralEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		float fadeIn = MathHelper.clamp(exactAge / 14.0F, 0.0F, 1.0F);
		float fadeOut = MathHelper.clamp((PrismCathedralEntity.TOTAL_TICKS - exactAge) / 18.0F, 0.0F, 1.0F);
		float env = fadeIn * fadeOut;
		if (env <= 0.01F) {
			return;
		}

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
		float radius = (float) PrismCathedralEntity.DOME_RADIUS * fadeIn;

		matrices.push();

		// 1. Вращающиеся священные гексаграммы на уровне земли и под сводом купола
		matrices.push();
		matrices.translate(0.0F, 0.14F, 0.0F);
		matrices.multiply(new Quaternionf().rotationY(realSec * 0.65F));
		drawStarMandala(matrices.peek().getPositionMatrix(), vc, radius, 6, 125, 245, 255, (int) (185 * env));
		matrices.pop();

		matrices.push();
		matrices.translate(0.0F, 0.20F, 0.0F);
		matrices.multiply(new Quaternionf().rotationY(-realSec * 0.95F));
		drawStarMandala(matrices.peek().getPositionMatrix(), vc, radius * 0.72F, 6, 225, 130, 255, (int) (175 * env));
		matrices.pop();

		matrices.push();
		matrices.translate(0.0F, 10.5F * fadeIn, 0.0F);
		matrices.multiply(new Quaternionf().rotationY(-realSec * 0.8F));
		drawStarMandala(matrices.peek().getPositionMatrix(), vc, radius * 0.68F, 6, 255, 225, 135, (int) (170 * env));
		matrices.pop();

		// 2. 24 парящих 3D призматических кристалла и преломлённые лазерные лучи между ними!
		int shards = 24;
		boolean converging = exactAge >= PrismCathedralEntity.CONVERGENCE_TICK - 6;
		Matrix4f rootM = matrices.peek().getPositionMatrix();

		for (int i = 0; i < shards; i++) {
			float a = MathHelper.TAU * i / shards + realSec * 0.55F;
			float sx = MathHelper.cos(a) * radius;
			float sz = MathHelper.sin(a) * radius;
			float sy = 3.2F + 2.2F * MathHelper.sin(realSec * 2.1F + i * 0.85F);

			int rgb = MathHelper.hsvToRgb(((i / (float) shards) + realSec * 0.18F) % 1.0F, 0.62F, 1.0F);
			int cr = (rgb >> 16) & 255;
			int cg = (rgb >> 8) & 255;
			int cb = rgb & 255;

			// Сам 3D-осколок (алмазный октаэдр)
			matrices.push();
			matrices.translate(sx, sy, sz);
			matrices.multiply(new Quaternionf().rotationXYZ(0.35F, realSec * 2.4F + i, 0.22F));
			drawOctahedronShard(matrices.peek().getPositionMatrix(), vc, 0.62F, 1.35F, cr, cg, cb, (int) (210 * env));
			matrices.pop();

			// Перекрещивающиеся лазерные хорды к противоположным осколкам (калейдоскоп!)
			if (i % 2 == 0) {
				int nextIdx = (i + 7) % shards;
				float a2 = MathHelper.TAU * nextIdx / shards + realSec * 0.55F;
				float tx = MathHelper.cos(a2) * radius;
				float tz = MathHelper.sin(a2) * radius;
				float ty = 3.2F + 2.2F * MathHelper.sin(realSec * 2.1F + nextIdx * 0.85F);
				drawLaserBeam(rootM, vc, sx, sy, sz, tx, ty, tz, 0.08F, cr, cg, cb, (int) (135 * env));
			}

			// В момент финального схождения все 24 осколка бьют радужными лучами прямо в центр арены!
			if (converging) {
				drawLaserBeam(rootM, vc, sx, sy, sz, 0.0F, 2.5F, 0.0F, 0.16F, cr, cg, cb, (int) (235 * env));
			}
		}

		matrices.pop();
	}

	private void drawStarMandala(Matrix4f m, VertexConsumer vc, float r, int points, int cr, int cg, int cb, int ca) {
		// Внешнее кольцо + звёздные хорды через 2 вершины
		int segs = points * 4;
		for (int i = 0; i < segs; i++) {
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			vc.vertex(m, MathHelper.cos(a0) * (r - 0.18F), 0.0F, MathHelper.sin(a0) * (r - 0.18F)).color(cr, cg, cb, ca);
			vc.vertex(m, MathHelper.cos(a1) * (r - 0.18F), 0.0F, MathHelper.sin(a1) * (r - 0.18F)).color(cr, cg, cb, ca);
			vc.vertex(m, MathHelper.cos(a1) * r, 0.0F, MathHelper.sin(a1) * r).color(cr, cg, cb, ca / 2);
			vc.vertex(m, MathHelper.cos(a0) * r, 0.0F, MathHelper.sin(a0) * r).color(cr, cg, cb, ca / 2);
		}
		for (int p = 0; p < points; p++) {
			float a0 = MathHelper.TAU * p / points;
			float a1 = MathHelper.TAU * ((p + 2) % points) / points;
			drawLaserBeam(m, vc, MathHelper.cos(a0) * r, 0.0F, MathHelper.sin(a0) * r,
					MathHelper.cos(a1) * r, 0.0F, MathHelper.sin(a1) * r, 0.11F, cr, cg, cb, ca);
		}
	}

	private void drawOctahedronShard(Matrix4f m, VertexConsumer vc, float rad, float halfH, int r, int g, int b, int a) {
		for (int s = 0; s < 4; s++) {
			float a0 = MathHelper.TAU * s / 4.0F;
			float a1 = MathHelper.TAU * (s + 1) / 4.0F;
			float x0 = MathHelper.cos(a0) * rad;
			float z0 = MathHelper.sin(a0) * rad;
			float x1 = MathHelper.cos(a1) * rad;
			float z1 = MathHelper.sin(a1) * rad;
			// Верхняя грань
			vc.vertex(m, x0, 0.0F, z0).color(r, g, b, a);
			vc.vertex(m, x1, 0.0F, z1).color(r, g, b, a);
			vc.vertex(m, 0.0F, halfH, 0.0F).color(255, 255, 255, a);
			vc.vertex(m, 0.0F, halfH, 0.0F).color(255, 255, 255, a);
			// Нижняя грань
			vc.vertex(m, x1, 0.0F, z1).color(r, g, b, a);
			vc.vertex(m, x0, 0.0F, z0).color(r, g, b, a);
			vc.vertex(m, 0.0F, -halfH, 0.0F).color(255, 255, 255, a);
			vc.vertex(m, 0.0F, -halfH, 0.0F).color(255, 255, 255, a);
		}
	}

	private void drawLaserBeam(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float w, int r, int g, int b, int a) {
		// Два перпендикулярных ленточных квада для объёмности с любого ракурса
		vc.vertex(m, x0, y0 - w, z0).color(r, g, b, a);
		vc.vertex(m, x0, y0 + w, z0).color(255, 255, 255, a);
		vc.vertex(m, x1, y1 + w, z1).color(255, 255, 255, a);
		vc.vertex(m, x1, y1 - w, z1).color(r, g, b, a);

		vc.vertex(m, x0 - w, y0, z0).color(r, g, b, a);
		vc.vertex(m, x0 + w, y0, z0).color(255, 255, 255, a);
		vc.vertex(m, x1 + w, y1, z1).color(255, 255, 255, a);
		vc.vertex(m, x1 - w, y1, z1).color(r, g, b, a);
	}

	@Override
	public Identifier getTexture(PrismCathedralEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
