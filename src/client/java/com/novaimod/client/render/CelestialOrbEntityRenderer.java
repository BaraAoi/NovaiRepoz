package com.novaimod.client.render;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.novaimod.entity.CelestialOrbEntity;

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
import net.minecraft.util.math.random.Random;

/**
 * Кастомный 3D-рендерер «Жезла Небесной Кары»:
 * 1) Для взлетающего Шара Света (Фаза 0): рисует объёмные ослепительно-БЕЛЫЕ лучи света
 *    по геометрии смерти Дракона Края + сияющее сферическое ядро!
 * 2) Для небесных Шаров Частиц (Фаза 1): рисует энергетическое ядро внутри облака частиц
 *    и настоящие ветвистые 3D-молнии, бьющие напрямую из небесного шара во все стороны и в мобов!
 */
public class CelestialOrbEntityRenderer extends EntityRenderer<CelestialOrbEntity> {
	private static final float HALF_SQRT_3 = (float) (Math.sqrt(3.0D) / 2.0D);
	private static final long BEAM_DURATION_MS = 320L;

	private record ActiveLightningBeam(Vec3d targetWorldPos, long createdAtMs, long seed) {
	}

	private static final Map<Integer, List<ActiveLightningBeam>> ACTIVE_BEAMS = new ConcurrentHashMap<>();

	public CelestialOrbEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
	}

	public static void recordLightningBeam(int orbEntityId, double tx, double ty, double tz) {
		List<ActiveLightningBeam> list = ACTIVE_BEAMS.computeIfAbsent(orbEntityId, k -> new ArrayList<>());
		long now = Util.getMeasuringTimeMs();
		synchronized (list) {
			list.add(new ActiveLightningBeam(new Vec3d(tx, ty, tz), now, now ^ ((long) orbEntityId * 341873128712L)));
		}
	}

	@Override
	public boolean shouldRender(CelestialOrbEntity entity, Frustum frustum, double x, double y, double z) {
		return entity.squaredDistanceTo(x, y, z) < 256.0D * 256.0D;
	}

	@Override
	public void render(
			CelestialOrbEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		// Используем реальное время клиента, чтобы божественный свет плавно сиял даже при остановленном времени!
		float realSec = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000.0F;
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

		if (entity.getPhase() == CelestialOrbEntity.PHASE_ASCENDING_LIGHT) {
			float lifeProgress = MathHelper.clamp((entity.age + tickDelta) / (float) CelestialOrbEntity.ASCEND_DURATION_TICKS, 0.0F, 1.0F);
			float intensity = MathHelper.clamp(lifeProgress * 1.35F, 0.18F, 1.0F);
			float coreRadius = 0.85F + intensity * 1.55F;

			matrices.push();
			renderWhiteRadiantSphere(matrices, vc, coreRadius, 245);
			renderWhiteRadiantSphere(matrices, vc, coreRadius * 1.55F, 105);
			renderPureWhiteDragonDeathRays(matrices, vc, realSec, intensity, 40, 18.5F);
			matrices.pop();
		} else {
			// Фаза 1: Небесный шар частиц + ветвистые 3D-молнии во все стороны!
			float pulse = 0.85F + 0.20F * MathHelper.sin(realSec * 7.5F + entity.getId());
			matrices.push();
			renderWhiteRadiantSphere(matrices, vc, 1.05F * pulse, 230);
			renderWhiteRadiantSphere(matrices, vc, 1.85F * pulse, 85);
			renderPureWhiteDragonDeathRays(matrices, vc, realSec * 1.4F + entity.getId(), 0.42F, 14, 5.2F);
			matrices.pop();

			renderOutgoing3dLightningBolts(entity, tickDelta, matrices, vc);
		}
	}

	/**
	 * Геометрия объёмных лучей смерти Дракона Края, но выполненная в ЧИСТО-БЕЛОМ цвете!
	 */
	private void renderPureWhiteDragonDeathRays(
			MatrixStack matrices,
			VertexConsumer vc,
			float animSec,
			float intensity,
			int rayCount,
			float maxLength
	) {
		Random rng = Random.create(432L);
		float spin = animSec * 0.55F;

		for (int i = 0; i < rayCount; i++) {
			matrices.push();
			matrices.multiply(new Quaternionf().rotationXYZ(
					rng.nextFloat() * MathHelper.TAU,
					rng.nextFloat() * MathHelper.TAU,
					rng.nextFloat() * MathHelper.TAU
			));
			matrices.multiply(new Quaternionf().rotationXYZ(
					rng.nextFloat() * MathHelper.TAU,
					rng.nextFloat() * MathHelper.TAU,
					rng.nextFloat() * MathHelper.TAU + spin * (0.6F + rng.nextFloat() * 0.6F)
			));

			float len = (0.45F + rng.nextFloat() * 0.55F) * maxLength * (0.45F + 0.55F * intensity);
			float width = len * (0.14F + rng.nextFloat() * 0.12F);
			int centerAlpha = MathHelper.clamp(Math.round(215.0F * intensity), 45, 245);

			Matrix4f m = matrices.peek().getPositionMatrix();
			float bx1 = -HALF_SQRT_3 * width;
			float bx2 = HALF_SQRT_3 * width;
			float bz = 0.5F * width;

			// 3 грани светового конуса (каждая как квад с двойной вершиной в центре (0,0,0) для DrawMode.QUADS)
			drawConeFace(vc, m, len, bx1, -bz, bx2, -bz, centerAlpha);
			drawConeFace(vc, m, len, bx2, -bz, 0.0F, width, centerAlpha);
			drawConeFace(vc, m, len, 0.0F, width, bx1, -bz, centerAlpha);

			matrices.pop();
		}
	}

	private void drawConeFace(VertexConsumer vc, Matrix4f m, float len, float x1, float z1, float x2, float z2, int centerAlpha) {
		vc.vertex(m, 0.0F, 0.0F, 0.0F).color(255, 255, 255, centerAlpha);
		vc.vertex(m, 0.0F, 0.0F, 0.0F).color(255, 255, 255, centerAlpha);
		vc.vertex(m, x1, len, z1).color(245, 250, 255, 0);
		vc.vertex(m, x2, len, z2).color(245, 250, 255, 0);
	}

	/**
	 * Рисует гладкое объёмное 3D-световое ядро (разбитый октаэдр / сфера).
	 */
	private void renderWhiteRadiantSphere(MatrixStack matrices, VertexConsumer vc, float radius, int alpha) {
		Matrix4f m = matrices.peek().getPositionMatrix();
		int latSteps = 6;
		int lonSteps = 10;
		for (int i = 0; i < latSteps; i++) {
			float v0 = (float) Math.PI * i / latSteps;
			float v1 = (float) Math.PI * (i + 1) / latSteps;
			float y0 = MathHelper.cos(v0) * radius;
			float r0 = MathHelper.sin(v0) * radius;
			float y1 = MathHelper.cos(v1) * radius;
			float r1 = MathHelper.sin(v1) * radius;

			for (int j = 0; j < lonSteps; j++) {
				float u0 = MathHelper.TAU * j / lonSteps;
				float u1 = MathHelper.TAU * (j + 1) / lonSteps;

				float x00 = MathHelper.cos(u0) * r0;
				float z00 = MathHelper.sin(u0) * r0;
				float x01 = MathHelper.cos(u1) * r0;
				float z01 = MathHelper.sin(u1) * r0;
				float x10 = MathHelper.cos(u0) * r1;
				float z10 = MathHelper.sin(u0) * r1;
				float x11 = MathHelper.cos(u1) * r1;
				float z11 = MathHelper.sin(u1) * r1;

				vc.vertex(m, x00, y0, z00).color(255, 255, 255, alpha);
				vc.vertex(m, x01, y0, z01).color(255, 255, 255, alpha);
				vc.vertex(m, x11, y1, z11).color(238, 248, 255, alpha);
				vc.vertex(m, x10, y1, z10).color(238, 248, 255, alpha);
			}
		}
	}

	/**
	 * Рисует настоящие ломаные ветвистые 3D-молнии напрямую из центра Небесного Шара Частиц в точки удара!
	 */
	private void renderOutgoing3dLightningBolts(
			CelestialOrbEntity entity,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumer vc
	) {
		List<ActiveLightningBeam> list = ACTIVE_BEAMS.get(entity.getId());
		if (list == null || list.isEmpty()) {
			return;
		}

		long now = Util.getMeasuringTimeMs();
		double ox = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX());
		double oy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
		double oz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ());

		Matrix4f m = matrices.peek().getPositionMatrix();

		synchronized (list) {
			Iterator<ActiveLightningBeam> it = list.iterator();
			while (it.hasNext()) {
				ActiveLightningBeam beam = it.next();
				long ageMs = now - beam.createdAtMs;
				if (ageMs < 0L || ageMs > BEAM_DURATION_MS) {
					it.remove();
					continue;
				}
				float fade = 1.0F - (float) ageMs / (float) BEAM_DURATION_MS;
				Vec3d relEnd = beam.targetWorldPos.subtract(ox, oy, oz);
				drawJaggedBolt(vc, m, Vec3d.ZERO, relEnd, beam.seed + (ageMs / 45L), fade, 0.22F, true);
			}
		}
	}

	private void drawJaggedBolt(
			VertexConsumer vc,
			Matrix4f m,
			Vec3d start,
			Vec3d end,
			long seed,
			float fade,
			float thickness,
			boolean allowBranches
	) {
		Vec3d diff = end.subtract(start);
		double totalLen = diff.length();
		if (totalLen < 0.2D) {
			return;
		}
		Random rng = Random.create(seed);
		int segments = MathHelper.clamp((int) (totalLen / 3.2D), 5, 18);
		double jitterAmp = Math.min(1.65D, totalLen * 0.065D);

		Vec3d prev = start;
		for (int i = 1; i <= segments; i++) {
			double t = (double) i / segments;
			Vec3d cur = start.lerp(end, t);
			if (i < segments) {
				cur = cur.add(
						(rng.nextDouble() - 0.5D) * 2.0D * jitterAmp,
						(rng.nextDouble() - 0.5D) * 2.0D * jitterAmp,
						(rng.nextDouble() - 0.5D) * 2.0D * jitterAmp
				);
			}

			drawBeamSegment(vc, m, prev, cur, thickness, 255, 255, 255, Math.round(245 * fade));
			drawBeamSegment(vc, m, prev, cur, thickness * 2.4F, 195, 235, 255, Math.round(115 * fade));

			// Боковые ответвления молнии!
			if (allowBranches && i > 1 && i < segments - 1 && rng.nextFloat() < 0.35F) {
				Vec3d branchDir = diff.normalize().multiply(totalLen * 0.22D).add(
						(rng.nextDouble() - 0.5D) * 6.0D,
						(rng.nextDouble() - 0.5D) * 5.0D,
						(rng.nextDouble() - 0.5D) * 6.0D
				);
				drawJaggedBolt(vc, m, cur, cur.add(branchDir), rng.nextLong(), fade * 0.75F, thickness * 0.55F, false);
			}
			prev = cur;
		}
	}

	private void drawBeamSegment(
			VertexConsumer vc,
			Matrix4f m,
			Vec3d a,
			Vec3d b,
			float halfWidth,
			int r,
			int g,
			int bl,
			int alpha
	) {
		if (alpha <= 2) {
			return;
		}
		Vec3d dir = b.subtract(a);
		if (dir.lengthSquared() < 1.0E-5D) {
			return;
		}
		Vec3d n = dir.normalize();
		Vec3d up = Math.abs(n.y) > 0.9D ? new Vec3d(1.0D, 0.0D, 0.0D) : new Vec3d(0.0D, 1.0D, 0.0D);
		Vec3d side1 = n.crossProduct(up).normalize().multiply(halfWidth);
		Vec3d side2 = n.crossProduct(side1).normalize().multiply(halfWidth);

		// Две скрещенные ленты под 90° делают луч молнии объёмным с любого ракурса!
		emitRibbon(vc, m, a, b, side1, r, g, bl, alpha);
		emitRibbon(vc, m, a, b, side2, r, g, bl, alpha);
	}

	private void emitRibbon(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d offset, int r, int g, int bl, int alpha) {
		vc.vertex(m, (float) (a.x - offset.x), (float) (a.y - offset.y), (float) (a.z - offset.z)).color(r, g, bl, alpha);
		vc.vertex(m, (float) (a.x + offset.x), (float) (a.y + offset.y), (float) (a.z + offset.z)).color(r, g, bl, alpha);
		vc.vertex(m, (float) (b.x + offset.x), (float) (b.y + offset.y), (float) (b.z + offset.z)).color(r, g, bl, alpha);
		vc.vertex(m, (float) (b.x - offset.x), (float) (b.y - offset.y), (float) (b.z - offset.z)).color(r, g, bl, alpha);
	}

	@Override
	public Identifier getTexture(CelestialOrbEntity entity) {
		return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
	}
}
