package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.SupernovaStarEntity;

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
 * Кинематографичный 3D-рендерер «Рождение Сверхновой» (Supernova Genesis):
 * 1) Раздувающийся Красный Гипергигант с солнечной короной и протуберанцами;
 * 2) Гравитационный коллапс в крошечную ослепительную нейтронную звезду;
 * 3) Взрыв Сверхновой: три гигантских 44-блочных ударных кольца и многослойная вращающаяся
 *    цветная 3D-Туманность с полярными джетами пульсара!
 */
public class SupernovaStarEntityRenderer extends EntityRenderer<SupernovaStarEntity> {
	public SupernovaStarEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(SupernovaStarEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 512.0D * 512.0D;
	}

	@Override
	public void render(
			SupernovaStarEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

		matrices.push();

		if (exactAge < SupernovaStarEntity.SWELL_END_TICK) {
			// ФАЗА 1: Красный Гипергигант раздувается в небе
			float p = MathHelper.clamp(exactAge / SupernovaStarEntity.SWELL_END_TICK, 0.0F, 1.0F);
			float beat = 1.0F + 0.08F * MathHelper.sin(realSec * (6.0F + p * 18.0F));
			float radius = MathHelper.lerp(p * p, 0.9F, 5.8F) * beat;

			drawSphere(matrices.peek().getPositionMatrix(), vc, radius * 0.58F, 255, 255, 235, 250);
			drawSphere(matrices.peek().getPositionMatrix(), vc, radius * 0.85F, 255, 185, 55, 195);
			drawSphere(matrices.peek().getPositionMatrix(), vc, radius * 1.15F, 255, 72, 32, 115);

			// Солнечные протуберанцы
			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(0.55F, realSec * 1.9F, 0.35F));
			drawRing(matrices.peek().getPositionMatrix(), vc, radius * 1.12F, radius * 1.55F, 32, 255, 125, 40, 165);
			matrices.pop();

			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(-0.65F, -realSec * 2.3F, 0.45F));
			drawRing(matrices.peek().getPositionMatrix(), vc, radius * 1.05F, radius * 1.42F, 32, 255, 215, 90, 155);
			matrices.pop();

		} else if (exactAge < SupernovaStarEntity.COLLAPSE_END_TICK) {
			// ФАЗА 2: Гравитационное схлопывание звезды в ослепительную нейтронную точку!
			float c = MathHelper.clamp(
					(exactAge - SupernovaStarEntity.SWELL_END_TICK)
							/ (float) (SupernovaStarEntity.COLLAPSE_END_TICK - SupernovaStarEntity.SWELL_END_TICK),
					0.0F, 1.0F
			);
			float implode = 1.0F - c * c * (3.0F - 2.0F * c);
			float r = MathHelper.lerp(1.0F - implode, 5.8F, 0.22F);

			drawSphere(matrices.peek().getPositionMatrix(), vc, r, 235, 250, 255, 255);
			drawSphere(matrices.peek().getPositionMatrix(), vc, r * 1.6F + 0.35F, 110, 220, 255, (int) (180 * implode + 65));

			// Стягивающиеся внутрь световые нити аккреции
			float rayOuter = 1.5F + implode * 12.0F;
			for (int k = 0; k < 16; k++) {
				matrices.push();
				matrices.multiply(new Quaternionf().rotationXYZ(k * 1.13F + realSec * 2.0F, k * 0.77F, k * 0.49F));
				drawRaySpike(matrices.peek().getPositionMatrix(), vc, 0.16F, rayOuter, 165, 240, 255, 210);
				matrices.pop();
			}

		} else {
			// ФАЗА 3: ВЗРЫВ СВЕРХНОВОЙ И РОЖДЕНИЕ ЦВЕТНОЙ 3D-ТУМАННОСТИ С ПУЛЬСАРОМ!
			float after = exactAge - SupernovaStarEntity.COLLAPSE_END_TICK;
			float totalAfter = SupernovaStarEntity.TOTAL_LIFE_TICKS - SupernovaStarEntity.COLLAPSE_END_TICK;
			float nebulaProg = MathHelper.clamp(after / totalAfter, 0.0F, 1.0F);
			float fadeOut = MathHelper.clamp((1.0F - nebulaProg) * 3.2F, 0.0F, 1.0F);

			// Тройные сверхзвуковые 3D-ударные кольца (разлетаются до 44 блоков!)
			float shockT = MathHelper.clamp(after / 42.0F, 0.0F, 1.0F);
			if (shockT < 1.0F) {
				float shockEase = 1.0F - (1.0F - shockT) * (1.0F - shockT);
				float shockR = 1.5F + shockEase * 42.0F;
				int shockAlpha = (int) ((1.0F - shockT) * 235.0F);

				matrices.push();
				matrices.multiply(new Quaternionf().rotationXYZ(0.22F, realSec * 0.8F, 0.14F));
				drawRing(matrices.peek().getPositionMatrix(), vc, shockR * 0.86F, shockR, 44, 95, 245, 255, shockAlpha);
				matrices.pop();

				matrices.push();
				matrices.multiply(new Quaternionf().rotationXYZ(-0.48F, -realSec * 1.1F, 0.35F));
				drawRing(matrices.peek().getPositionMatrix(), vc, shockR * 0.68F, shockR * 0.80F, 40, 255, 95, 220, shockAlpha);
				matrices.pop();

				matrices.push();
				matrices.multiply(new Quaternionf().rotationXYZ(0.72F, realSec * 1.3F, -0.32F));
				drawRing(matrices.peek().getPositionMatrix(), vc, shockR * 0.50F, shockR * 0.60F, 36, 255, 225, 110, shockAlpha);
				matrices.pop();
			}

			// Многослойная вращающаяся 3D-Туманность (бирюзовая, фиолетовая, пурпурно-розовая и золотая оболочки!)
			float bloom = MathHelper.clamp(after / 22.0F, 0.0F, 1.0F);
			float nebScale = (0.35F + 0.65F * (1.0F - (1.0F - bloom) * (1.0F - bloom)));

			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(0.3F, realSec * 0.45F, 0.2F));
			matrices.scale(1.35F, 0.72F, 1.15F);
			drawSphere(matrices.peek().getPositionMatrix(), vc, 15.5F * nebScale, 55, 225, 255, (int) (58 * fadeOut));
			matrices.pop();

			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(-0.55F, -realSec * 0.62F, 0.42F));
			matrices.scale(1.1F, 0.82F, 1.4F);
			drawSphere(matrices.peek().getPositionMatrix(), vc, 11.8F * nebScale, 190, 80, 255, (int) (68 * fadeOut));
			matrices.pop();

			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(0.75F, realSec * 0.85F, -0.35F));
			matrices.scale(1.25F, 0.65F, 1.0F);
			drawSphere(matrices.peek().getPositionMatrix(), vc, 8.4F * nebScale, 255, 95, 185, (int) (78 * fadeOut));
			matrices.pop();

			// Наклонный звёздный диск вокруг молодого Пульсара + два релятивистских джета!
			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(0.28F, realSec * 3.4F, 0.18F));
			drawRing(matrices.peek().getPositionMatrix(), vc, 2.2F * nebScale, 6.8F * nebScale, 36, 255, 225, 130, (int) (150 * fadeOut));
			// Северный и Южный полярные джеты пульсара
			drawPolarJet(matrices.peek().getPositionMatrix(), vc, 0.48F, 24.0F * nebScale, 155, 245, 255, (int) (195 * fadeOut));
			drawPolarJet(matrices.peek().getPositionMatrix(), vc, 0.48F, -24.0F * nebScale, 155, 245, 255, (int) (195 * fadeOut));
			matrices.pop();

			// Сияющее белое ядро Пульсара
			drawSphere(matrices.peek().getPositionMatrix(), vc, 1.15F, 255, 255, 255, (int) (245 * fadeOut));
		}

		matrices.pop();
	}

	private void drawSphere(Matrix4f m, VertexConsumer vc, float radius, int r, int g, int b, int a) {
		if (a <= 2) {
			return;
		}
		int lat = 8;
		int lon = 14;
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

	private void drawRing(Matrix4f m, VertexConsumer vc, float innerR, float outerR, int segs, int r, int g, int b, int a) {
		if (a <= 2) {
			return;
		}
		for (int i = 0; i < segs; i++) {
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			float c0 = MathHelper.cos(a0);
			float s0 = MathHelper.sin(a0);
			float c1 = MathHelper.cos(a1);
			float s1 = MathHelper.sin(a1);
			vc.vertex(m, c0 * innerR, 0.0F, s0 * innerR).color(r, g, b, a);
			vc.vertex(m, c1 * innerR, 0.0F, s1 * innerR).color(r, g, b, a);
			vc.vertex(m, c1 * outerR, 0.0F, s1 * outerR).color(r, g, b, a / 4);
			vc.vertex(m, c0 * outerR, 0.0F, s0 * outerR).color(r, g, b, a / 4);
		}
	}

	private void drawRaySpike(Matrix4f m, VertexConsumer vc, float baseR, float length, int r, int g, int b, int a) {
		vc.vertex(m, -baseR, 0.0F, 0.0F).color(255, 255, 255, a);
		vc.vertex(m, baseR, 0.0F, 0.0F).color(255, 255, 255, a);
		vc.vertex(m, 0.0F, length, 0.0F).color(r, g, b, 0);
		vc.vertex(m, 0.0F, length, 0.0F).color(r, g, b, 0);
	}

	private void drawPolarJet(Matrix4f m, VertexConsumer vc, float baseR, float height, int r, int g, int b, int a) {
		int sides = 8;
		for (int i = 0; i < sides; i++) {
			float a0 = MathHelper.TAU * i / sides;
			float a1 = MathHelper.TAU * (i + 1) / sides;
			vc.vertex(m, MathHelper.cos(a0) * baseR, 0.0F, MathHelper.sin(a0) * baseR).color(255, 255, 255, a);
			vc.vertex(m, MathHelper.cos(a1) * baseR, 0.0F, MathHelper.sin(a1) * baseR).color(255, 255, 255, a);
			vc.vertex(m, MathHelper.cos(a1) * baseR * 2.4F, height, MathHelper.sin(a1) * baseR * 2.4F).color(r, g, b, 0);
			vc.vertex(m, MathHelper.cos(a0) * baseR * 2.4F, height, MathHelper.sin(a0) * baseR * 2.4F).color(r, g, b, 0);
		}
	}

	@Override
	public Identifier getTexture(SupernovaStarEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
