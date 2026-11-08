package com.novaimod.client.render;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.client.TimeOfTheWorldClientState;
import com.novaimod.entity.ChronoAstrolabeEntity;

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
 * Грандиозный 3D-рендерер Небесной Астролябии («Made in Heaven: Time of the World»):
 * - 32-блочный золотой циферблат в небе с 12 часовыми делениями и реальными часовой/минутной стрелками,
 *   вращающимися в точном соответствии с ускоряющимся временем суток!
 * - Три наклонных 3D-кольца армиллярной сферы и сияющее ядро Сингулярности.
 */
public class ChronoAstrolabeEntityRenderer extends EntityRenderer<ChronoAstrolabeEntity> {
	public ChronoAstrolabeEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(ChronoAstrolabeEntity entity, Frustum frustum, double x, double y, double z) {
		double dx = entity.getX() - x;
		double dz = entity.getZ() - z;
		return (dx * dx + dz * dz) < 512.0D * 512.0D;
	}

	@Override
	public void render(
			ChronoAstrolabeEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		float exactAge = entity.age + tickDelta;
		float fadeIn = MathHelper.clamp(exactAge / 16.0F, 0.0F, 1.0F);

		long worldTimeOfDay = entity.getWorld() != null ? entity.getWorld().getTimeOfDay() : 0L;
		if (TimeOfTheWorldClientState.isAccelerating()) {
			worldTimeOfDay = TimeOfTheWorldClientState.computeSmoothTimeOfDay();
		}
		float dayFraction = (Math.floorMod(worldTimeOfDay, 24000L)) / 24000.0F;
		float hourAngle = dayFraction * MathHelper.TAU * 2.0F; // 2 оборота в сутки (12-часовой циферблат)
		float minuteAngle = dayFraction * MathHelper.TAU * 24.0F; // 24 оборота в сутки

		float prog = TimeOfTheWorldClientState.isAccelerating()
				? TimeOfTheWorldClientState.getNormalizedProgress()
				: MathHelper.clamp(exactAge / Math.max(1.0F, entity.getTotalTicks()), 0.0F, 1.0F);
		float spinPhase = realSec * (0.8F + prog * 9.5F);

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
		matrices.push();
		matrices.scale(fadeIn, fadeIn, fadeIn);

		// 1. Внешний 16-блочный Небесный Циферблат с 12 часовыми делениями
		drawRing(matrices.peek().getPositionMatrix(), vc, 14.8F, 16.2F, 48, false, 255, 215, 90, 215);
		drawRing(matrices.peek().getPositionMatrix(), vc, 11.8F, 12.3F, 36, true, 120, 240, 255, 175);
		drawHourTicks(matrices.peek().getPositionMatrix(), vc, 12.5F, 14.7F);

		// 2. Часовая и Минутная 3D-стрелки, вращающиеся синхронно со сменой Дня и Ночи!
		matrices.push();
		matrices.multiply(new Quaternionf().rotationY(-hourAngle));
		drawClockHand(matrices.peek().getPositionMatrix(), vc, 0.55F, 9.2F, 255, 232, 130, 240);
		matrices.pop();

		matrices.push();
		matrices.multiply(new Quaternionf().rotationY(-minuteAngle));
		drawClockHand(matrices.peek().getPositionMatrix(), vc, 0.36F, 13.6F, 165, 250, 255, 235);
		matrices.pop();

		// 3. Три наклонных 3D-кольца армиллярной сферы Вселенной
		matrices.push();
		matrices.multiply(new Quaternionf().rotationXYZ(0.58F + spinPhase * 0.7F, spinPhase * 1.1F, 0.25F));
		drawRing(matrices.peek().getPositionMatrix(), vc, 9.2F, 10.0F, 36, false, 255, 205, 75, 195);
		matrices.pop();

		matrices.push();
		matrices.multiply(new Quaternionf().rotationXYZ(-0.72F - spinPhase * 0.9F, -spinPhase * 1.35F, 0.48F));
		drawRing(matrices.peek().getPositionMatrix(), vc, 6.6F, 7.25F, 32, false, 120, 238, 255, 195);
		matrices.pop();

		matrices.push();
		matrices.multiply(new Quaternionf().rotationXYZ(0.35F, spinPhase * 2.1F, -0.85F - spinPhase * 1.2F));
		drawRing(matrices.peek().getPositionMatrix(), vc, 4.1F, 4.65F, 28, false, 255, 250, 210, 215);
		matrices.pop();

		// 4. Центральное бело-золотое ядро Сингулярности
		float coreR = 1.45F + prog * 1.35F + 0.18F * MathHelper.sin(realSec * 14.0F);
		drawSphere(matrices.peek().getPositionMatrix(), vc, coreR, 255, 252, 230, 245);
		drawSphere(matrices.peek().getPositionMatrix(), vc, coreR * 1.65F, 255, 215, 95, 95);

		matrices.pop();
	}

	private void drawRing(Matrix4f m, VertexConsumer vc, float innerR, float outerR, int segs, boolean dashed, int r, int g, int b, int a) {
		for (int i = 0; i < segs; i++) {
			if (dashed && (i % 3 == 2)) {
				continue;
			}
			float a0 = MathHelper.TAU * i / segs;
			float a1 = MathHelper.TAU * (i + 1) / segs;
			float c0 = MathHelper.cos(a0);
			float s0 = MathHelper.sin(a0);
			float c1 = MathHelper.cos(a1);
			float s1 = MathHelper.sin(a1);
			vc.vertex(m, c0 * innerR, 0.0F, s0 * innerR).color(r, g, b, a);
			vc.vertex(m, c1 * innerR, 0.0F, s1 * innerR).color(r, g, b, a);
			vc.vertex(m, c1 * outerR, 0.0F, s1 * outerR).color(r, g, b, a / 2);
			vc.vertex(m, c0 * outerR, 0.0F, s0 * outerR).color(r, g, b, a / 2);
		}
	}

	private void drawHourTicks(Matrix4f m, VertexConsumer vc, float r0, float r1) {
		for (int h = 0; h < 12; h++) {
			float angle = MathHelper.TAU * h / 12.0F;
			float halfW = (h % 3 == 0) ? 0.045F : 0.022F;
			float c0 = MathHelper.cos(angle - halfW);
			float s0 = MathHelper.sin(angle - halfW);
			float c1 = MathHelper.cos(angle + halfW);
			float s1 = MathHelper.sin(angle + halfW);
			int cr = (h % 3 == 0) ? 185 : 255;
			int cg = (h % 3 == 0) ? 250 : 225;
			int cb = (h % 3 == 0) ? 255 : 120;
			vc.vertex(m, c0 * r0, 0.02F, s0 * r0).color(cr, cg, cb, 235);
			vc.vertex(m, c1 * r0, 0.02F, s1 * r0).color(cr, cg, cb, 235);
			vc.vertex(m, c1 * r1, 0.02F, s1 * r1).color(cr, cg, cb, 235);
			vc.vertex(m, c0 * r1, 0.02F, s0 * r1).color(cr, cg, cb, 235);
		}
	}

	private void drawClockHand(Matrix4f m, VertexConsumer vc, float halfWidth, float length, int r, int g, int b, int a) {
		// Ромбовидная часовая стрелка вдоль оси +Z
		vc.vertex(m, 0.0F, 0.05F, -1.2F).color(r, g, b, a);
		vc.vertex(m, halfWidth, 0.05F, length * 0.35F).color(r, g, b, a);
		vc.vertex(m, 0.0F, 0.05F, length).color(255, 255, 255, a);
		vc.vertex(m, -halfWidth, 0.05F, length * 0.35F).color(r, g, b, a);
	}

	private void drawSphere(Matrix4f m, VertexConsumer vc, float radius, int r, int g, int b, int a) {
		int lat = 7;
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

	@Override
	public Identifier getTexture(ChronoAstrolabeEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
