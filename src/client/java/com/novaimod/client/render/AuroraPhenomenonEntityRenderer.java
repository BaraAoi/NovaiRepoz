package com.novaimod.client.render;

import org.joml.Matrix4f;

import com.novaimod.entity.AuroraPhenomenonEntity;

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
 * Завораживающий 3D-рендерер «Ткача Северного Сияния и Звёздного Дождя» (Aurora Borealis Weaver):
 * - Рисует в небе 7 гигантских (длиной 92 блока, высотой 22 блока!) плавно колышущихся шёлковых занавесей
 *   полярного сияния с мягким вертикальным градиентом и переливами спектра на 60–240 FPS!
 * - Дополняет купол неба 16 падающими 3D-звёздами с длинными светящимися шлейфами!
 */
public class AuroraPhenomenonEntityRenderer extends EntityRenderer<AuroraPhenomenonEntity> {
	public AuroraPhenomenonEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(AuroraPhenomenonEntity entity, Frustum frustum, double x, double y, double z) {
		double dx = entity.getX() - x;
		double dz = entity.getZ() - z;
		return (dx * dx + dz * dz) < 512.0D * 512.0D;
	}

	@Override
	public void render(
			AuroraPhenomenonEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		float fadeIn = MathHelper.clamp(exactAge / 30.0F, 0.0F, 1.0F);
		float fadeOut = MathHelper.clamp((AuroraPhenomenonEntity.TOTAL_DURATION_TICKS - exactAge) / 40.0F, 0.0F, 1.0F);
		float env = fadeIn * fadeOut;
		if (env <= 0.01F) {
			return;
		}

		int palette = entity.getPalette();
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

		matrices.push();
		Matrix4f m = matrices.peek().getPositionMatrix();

		// 1. 7 гигантских волнообразных шёлковых лент Северного Сияния
		int ribbons = 7;
		int segs = 38;
		float spanX = 92.0F;
		for (int r = 0; r < ribbons; r++) {
			float baseZ = (r - 3.0F) * 11.5F;
			float phase = r * 1.37F;
			float curtainH = 19.0F + (r % 3) * 3.5F;

			for (int s = 0; s < segs; s++) {
				float u0 = (float) s / segs;
				float u1 = (float) (s + 1) / segs;
				float x0 = (u0 - 0.5F) * spanX;
				float x1 = (u1 - 0.5F) * spanX;

				float z0 = baseZ + auroraWaveZ(x0, realSec, phase);
				float z1 = baseZ + auroraWaveZ(x1, realSec, phase);
				float y0 = auroraWaveY(x0, realSec, phase);
				float y1 = auroraWaveY(x1, realSec, phase);

				// Мягкое затухание на левом и правом краях ленты
				float edge0 = MathHelper.sin(u0 * (float) Math.PI);
				float edge1 = MathHelper.sin(u1 * (float) Math.PI);

				int[] lowCol = sampleColor(palette, u0 + realSec * 0.06F + r * 0.14F, true);
				int[] topCol = sampleColor(palette, u0 + realSec * 0.06F + r * 0.14F, false);

				int aLow0 = (int) (135.0F * edge0 * env);
				int aLow1 = (int) (135.0F * edge1 * env);
				int aMid0 = (int) (78.0F * edge0 * env);
				int aMid1 = (int) (78.0F * edge1 * env);

				// Нижняя яркая кайма Северного Сияния
				vc.vertex(m, x0, y0, z0).color(lowCol[0], lowCol[1], lowCol[2], aLow0);
				vc.vertex(m, x1, y1, z1).color(lowCol[0], lowCol[1], lowCol[2], aLow1);
				vc.vertex(m, x1, y1 + curtainH * 0.35F, z1 + 1.2F).color(topCol[0], topCol[1], topCol[2], aMid1);
				vc.vertex(m, x0, y0 + curtainH * 0.35F, z0 + 1.2F).color(topCol[0], topCol[1], topCol[2], aMid0);

				// Верхний растворяющийся в звёздном небе шлейф занавеси
				vc.vertex(m, x0, y0 + curtainH * 0.35F, z0 + 1.2F).color(topCol[0], topCol[1], topCol[2], aMid0);
				vc.vertex(m, x1, y1 + curtainH * 0.35F, z1 + 1.2F).color(topCol[0], topCol[1], topCol[2], aMid1);
				vc.vertex(m, x1, y1 + curtainH, z1 + 2.8F).color(topCol[0], topCol[1], topCol[2], 0);
				vc.vertex(m, x0, y0 + curtainH, z0 + 2.8F).color(topCol[0], topCol[1], topCol[2], 0);
			}
		}

		// 2. 16 падающих 3D-звёзд со светящимися диагональными хвостами!
		for (int i = 0; i < 16; i++) {
			float period = 2.6F + (i % 5) * 0.45F;
			float localT = (realSec + i * 0.83F) % period;
			float life = localT / period; // 0..1
			float starAlpha = MathHelper.sin(life * (float) Math.PI) * env;
			if (starAlpha <= 0.05F) {
				continue;
			}

			float startX = ((i * 37 % 80) - 40.0F);
			float startZ = ((i * 53 % 76) - 38.0F);
			float startY = 18.0F + (i % 4) * 3.5F;

			float dx = 22.0F;
			float dy = -14.0F;
			float dz = 9.0F;

			float headX = startX + dx * life;
			float headY = startY + dy * life;
			float headZ = startZ + dz * life;

			float tailX = headX - dx * 0.22F;
			float tailY = headY - dy * 0.22F;
			float tailZ = headZ - dz * 0.22F;

			int sa = (int) (235.0F * starAlpha);
			vc.vertex(m, headX, headY + 0.14F, headZ).color(255, 255, 255, sa);
			vc.vertex(m, headX, headY - 0.14F, headZ).color(255, 255, 255, sa);
			vc.vertex(m, tailX, tailY - 0.04F, tailZ).color(140, 240, 255, 0);
			vc.vertex(m, tailX, tailY + 0.04F, tailZ).color(140, 240, 255, 0);
		}

		matrices.pop();
	}

	private float auroraWaveZ(float x, float t, float phase) {
		return 6.2F * MathHelper.sin(x * 0.085F + t * 0.95F + phase)
				+ 3.1F * MathHelper.sin(x * 0.19F - t * 0.65F + phase * 1.7F);
	}

	private float auroraWaveY(float x, float t, float phase) {
		return 2.4F * MathHelper.cos(x * 0.07F - t * 0.75F + phase);
	}

	private int[] sampleColor(int palette, float u, boolean lower) {
		float w = 0.5F + 0.5F * MathHelper.sin(u * MathHelper.TAU);
		return switch (palette) {
			case 1 -> lower
					? new int[]{(int) MathHelper.lerp(w, 245, 165), (int) MathHelper.lerp(w, 85, 110), 255}
					: new int[]{135, 55, 235};
			case 2 -> lower
					? new int[]{255, (int) MathHelper.lerp(w, 225, 130), (int) MathHelper.lerp(w, 95, 55)}
					: new int[]{235, 65, 95};
			case 3 -> {
				float h = (u % 1.0F + 1.0F) % 1.0F;
				int rgb = MathHelper.hsvToRgb(h, lower ? 0.72F : 0.88F, 1.0F);
				yield new int[]{(rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255};
			}
			default -> lower
					? new int[]{(int) MathHelper.lerp(w, 55, 75), 255, (int) MathHelper.lerp(w, 165, 245)}
					: new int[]{145, 85, 255};
		};
	}

	@Override
	public Identifier getTexture(AuroraPhenomenonEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
