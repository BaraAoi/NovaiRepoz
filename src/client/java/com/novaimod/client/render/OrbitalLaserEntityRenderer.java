package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.OrbitalLaserEntity;

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
 * 3D-рендерер «Маяка Орбитального Лазера»:
 * - Фаза наведения (0..20 тиков): вращающиеся голографические кольца захвата цели и тонкий луч прицела с орбиты.
 * - Активная фаза (20..180 тиков): исполинский многослойный 3D-столб плазмы высотой 180 блоков до самого космоса!
 */
public class OrbitalLaserEntityRenderer extends EntityRenderer<OrbitalLaserEntity> {
	public OrbitalLaserEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(OrbitalLaserEntity entity, Frustum frustum, double x, double y, double z) {
		double dx = entity.getX() - x;
		double dz = entity.getZ() - z;
		return (dx * dx + dz * dz) < 320.0D * 320.0D;
	}

	@Override
	public void render(
			OrbitalLaserEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;

		matrices.push();
		VertexConsumer glowVc = vertexConsumers.getBuffer(RenderLayer.getLightning());

		if (exactAge < OrbitalLaserEntity.LOCK_ON_TICKS) {
			float prog = MathHelper.clamp(exactAge / OrbitalLaserEntity.LOCK_ON_TICKS, 0.0F, 1.0F);
			float outerR = MathHelper.lerp(prog, 6.5F, 2.2F);

			// Тонкий прицельный луч с орбиты
			drawCylinder(matrices.peek().getPositionMatrix(), glowVc, 0.14F + 0.12F * prog, 180.0F, 90, 240, 255, (int) (130 + 100 * prog));

			// Три сходящихся 3D-кольца орбитального захвата
			matrices.push();
			matrices.translate(0.0F, 0.12F, 0.0F);
			matrices.multiply(new Quaternionf().rotationY(realSec * 3.5F));
			drawFlatRing(matrices.peek().getPositionMatrix(), glowVc, outerR * 0.85F, outerR, 70, 235, 255, 210);
			matrices.pop();

			matrices.push();
			matrices.translate(0.0F, 0.35F, 0.0F);
			matrices.multiply(new Quaternionf().rotationY(-realSec * 5.2F));
			drawFlatRing(matrices.peek().getPositionMatrix(), glowVc, outerR * 0.45F, outerR * 0.56F, 255, 215, 95, 220);
			matrices.pop();
		} else {
			float fireTicks = exactAge - OrbitalLaserEntity.LOCK_ON_TICKS;
			float fadeIn = MathHelper.clamp(fireTicks / 4.0F, 0.0F, 1.0F);
			float fadeOut = MathHelper.clamp((OrbitalLaserEntity.TOTAL_TICKS - exactAge) / 10.0F, 0.0F, 1.0F);
			float scale = fadeIn * fadeOut * (1.0F + 0.07F * MathHelper.sin(realSec * 28.0F));

			// 1. Внутреннее бело-золотое ядро плазмы
			drawCylinder(matrices.peek().getPositionMatrix(), glowVc, 1.05F * scale, 185.0F, 255, 255, 240, 245);
			// 2. Бирюзовая оболочка ионной плазмы
			drawCylinder(matrices.peek().getPositionMatrix(), glowVc, 2.05F * scale, 185.0F, 45, 225, 255, 155);
			// 3. Внешняя фиолетово-лазурная корона
			drawCylinder(matrices.peek().getPositionMatrix(), glowVc, 3.15F * scale, 185.0F, 150, 75, 255, 75);

			// Вращающиеся ударные кольца у земли
			matrices.push();
			matrices.translate(0.0F, 0.18F, 0.0F);
			matrices.multiply(new Quaternionf().rotationY(realSec * 6.5F));
			drawFlatRing(matrices.peek().getPositionMatrix(), glowVc, 2.1F * scale, 4.4F * scale, 95, 245, 255, 195);
			matrices.pop();

			matrices.push();
			matrices.translate(0.0F, 0.45F, 0.0F);
			matrices.multiply(new Quaternionf().rotationY(-realSec * 8.0F));
			drawFlatRing(matrices.peek().getPositionMatrix(), glowVc, 1.2F * scale, 2.6F * scale, 255, 230, 140, 215);
			matrices.pop();
		}

		matrices.pop();
	}

	private void drawCylinder(Matrix4f m, VertexConsumer vc, float radius, float height, int r, int g, int b, int a) {
		int sides = 16;
		for (int i = 0; i < sides; i++) {
			float a0 = MathHelper.TAU * i / sides;
			float a1 = MathHelper.TAU * (i + 1) / sides;
			float x0 = MathHelper.cos(a0) * radius;
			float z0 = MathHelper.sin(a0) * radius;
			float x1 = MathHelper.cos(a1) * radius;
			float z1 = MathHelper.sin(a1) * radius;

			vc.vertex(m, x0, 0.0F, z0).color(r, g, b, a);
			vc.vertex(m, x1, 0.0F, z1).color(r, g, b, a);
			vc.vertex(m, x1, height, z1).color(r, g, b, a);
			vc.vertex(m, x0, height, z0).color(r, g, b, a);
		}
	}

	private void drawFlatRing(Matrix4f m, VertexConsumer vc, float innerR, float outerR, int r, int g, int b, int a) {
		int segs = 24;
		for (int i = 0; i < segs; i++) {
			if (i % 3 == 2) {
				continue; // Пунктирный хай-тек орбитальный паттерн!
			}
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			float c0 = MathHelper.cos(a0);
			float s0 = MathHelper.sin(a0);
			float c1 = MathHelper.cos(a1);
			float s1 = MathHelper.sin(a1);

			vc.vertex(m, c0 * innerR, 0.0F, s0 * innerR).color(r, g, b, a);
			vc.vertex(m, c1 * innerR, 0.0F, s1 * innerR).color(r, g, b, a);
			vc.vertex(m, c1 * outerR, 0.0F, s1 * outerR).color(r, g, b, 30);
			vc.vertex(m, c0 * outerR, 0.0F, s0 * outerR).color(r, g, b, 30);
		}
	}

	@Override
	public Identifier getTexture(OrbitalLaserEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
