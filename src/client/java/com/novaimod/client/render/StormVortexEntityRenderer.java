package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.StormVortexEntity;

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
import net.minecraft.util.math.random.Random;

/**
 * 3D-рендерер Вихря Тысячи Молний («Stormbreaker Sovereign»):
 * рисует многоярусную грозовую воронку в небе и живые ломаные 3D-разряды молний,
 * бьющие из небесной воронки в землю на 60–240 FPS!
 */
public class StormVortexEntityRenderer extends EntityRenderer<StormVortexEntity> {
	public StormVortexEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(StormVortexEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 384.0D * 384.0D;
	}

	@Override
	public void render(
			StormVortexEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		long nowMs = Util.getMeasuringTimeMs();
		float realSec = (nowMs % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		float fadeIn = MathHelper.clamp(exactAge / 12.0F, 0.0F, 1.0F);
		float fadeOut = MathHelper.clamp((StormVortexEntity.TOTAL_TICKS - exactAge) / 16.0F, 0.0F, 1.0F);
		float env = fadeIn * fadeOut;
		if (env <= 0.01F) {
			return;
		}

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
		matrices.push();

		// 1. Вращающиеся ярусы грозовой воронки от высоты +4 до +22 блоков
		for (int tier = 0; tier < 6; tier++) {
			float y = 5.0F + tier * 3.1F;
			float r = (3.5F + tier * 2.6F) * fadeIn;
			matrices.push();
			matrices.translate(0.0F, y, 0.0F);
			matrices.multiply(new Quaternionf().rotationY(realSec * (3.8F - tier * 0.35F) + tier));
			int a = (int) ((155 - tier * 14) * env);
			drawStormRing(matrices.peek().getPositionMatrix(), vc, r * 0.78F, r, 28, 75, 215, 255, a);
			matrices.pop();
		}

		// 2. 6 живых ветвистых 3D-молний, мерцающих с частотой кадров!
		Matrix4f m = matrices.peek().getPositionMatrix();
		long frameBucket = nowMs / 55L;
		for (int b = 0; b < 6; b++) {
			Random rng = Random.create(frameBucket * 31337L + b * 7919L + entity.getId());
			float topAngle = rng.nextFloat() * MathHelper.TAU;
			float topR = 4.0F + rng.nextFloat() * 11.0F;
			float x = MathHelper.cos(topAngle) * topR;
			float z = MathHelper.sin(topAngle) * topR;
			float y = 20.0F;

			int segments = 8;
			for (int s = 0; s < segments; s++) {
				float nx = x + (rng.nextFloat() - 0.5F) * 3.4F;
				float nz = z + (rng.nextFloat() - 0.5F) * 3.4F;
				float ny = 20.0F * (1.0F - (s + 1) / (float) segments);
				drawJaggedSegment(m, vc, x, y, z, nx, ny, nz, 0.14F, (int) (235 * env));
				x = nx;
				y = ny;
				z = nz;
			}
		}

		matrices.pop();
	}

	private void drawStormRing(Matrix4f m, VertexConsumer vc, float r0, float r1, int segs, int r, int g, int b, int a) {
		for (int i = 0; i < segs; i++) {
			if (i % 4 == 3) {
				continue;
			}
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			vc.vertex(m, MathHelper.cos(a0) * r0, 0.0F, MathHelper.sin(a0) * r0).color(220, 250, 255, a);
			vc.vertex(m, MathHelper.cos(a1) * r0, 0.0F, MathHelper.sin(a1) * r0).color(220, 250, 255, a);
			vc.vertex(m, MathHelper.cos(a1) * r1, 0.45F, MathHelper.sin(a1) * r1).color(r, g, b, a / 3);
			vc.vertex(m, MathHelper.cos(a0) * r1, 0.45F, MathHelper.sin(a0) * r1).color(r, g, b, a / 3);
		}
	}

	private void drawJaggedSegment(Matrix4f m, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float w, int a) {
		vc.vertex(m, x0 - w, y0, z0).color(130, 240, 255, a);
		vc.vertex(m, x0 + w, y0, z0).color(255, 255, 255, a);
		vc.vertex(m, x1 + w, y1, z1).color(255, 255, 255, a);
		vc.vertex(m, x1 - w, y1, z1).color(130, 240, 255, a);

		vc.vertex(m, x0, y0, z0 - w).color(130, 240, 255, a);
		vc.vertex(m, x0, y0, z0 + w).color(255, 255, 255, a);
		vc.vertex(m, x1, y1, z1 + w).color(255, 255, 255, a);
		vc.vertex(m, x1, y1, z1 - w).color(130, 240, 255, a);
	}

	@Override
	public Identifier getTexture(StormVortexEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
