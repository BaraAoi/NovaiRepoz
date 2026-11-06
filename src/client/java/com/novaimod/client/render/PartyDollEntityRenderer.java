package com.novaimod.client.render;

import com.novaimod.NovaiMod;
import com.novaimod.entity.PartyDollEntity;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * 3D-рендерер женской куклы ровно в 1 блок высотой (0..16 пикселей модели):
 * - Туфельки и ножки, пышное платьице, аккуратный торс, ручки, голова с большими глазами,
 *   длинные хвостики по бокам и бантик на макушке (общая высота = 16px = 1 блок!).
 * - Когда кукла активна, она тоже крутится и ритмично сплющивается вверх-вниз в стиле мема «Приора / Ярис»!
 */
public class PartyDollEntityRenderer extends EntityRenderer<PartyDollEntity> {
	public static final EntityModelLayer DOLL_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "party_doll"), "main");

	private static final Identifier TEXTURE =
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/party_doll.png");

	private final ModelPart root;

	public PartyDollEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.root = context.getPart(DOLL_MODEL_LAYER);
		this.shadowRadius = 0.28F;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Ножки с туфельками (y = 0..5)
		root.addChild(
				"legs",
				ModelPartBuilder.create()
						.uv(0, 26).cuboid(-2.2F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F)
						.uv(8, 26).cuboid(0.2F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F),
				ModelTransform.NONE
		);

		// Пышная юбочка платья (y = 4..8) и лиф (y = 7..10)
		root.addChild(
				"dress",
				ModelPartBuilder.create()
						.uv(0, 17).cuboid(-3.5F, 4.0F, -2.5F, 7.0F, 4.0F, 5.0F)
						.uv(24, 17).cuboid(-2.0F, 7.5F, -1.5F, 4.0F, 3.0F, 3.0F),
				ModelTransform.NONE
		);

		// Ручки по бокам (y = 6.5..10.5) с лёгким изящным наклоном
		root.addChild(
				"left_arm",
				ModelPartBuilder.create()
						.uv(16, 26).cuboid(0.0F, -4.0F, -1.0F, 1.8F, 4.2F, 2.0F),
				ModelTransform.of(2.0F, 10.3F, 0.0F, 0.0F, 0.0F, -0.22F)
		);
		root.addChild(
				"right_arm",
				ModelPartBuilder.create()
						.uv(24, 26).cuboid(-1.8F, -4.0F, -1.0F, 1.8F, 4.2F, 2.0F),
				ModelTransform.of(-2.0F, 10.3F, 0.0F, 0.0F, 0.0F, 0.22F)
		);

		// Голова куклы (5x5x5 на y = 10.2..15.2), два длинных хвостика по бокам и бантик (ровно до y = 16.0 = 1 блок!)
		root.addChild(
				"head_and_hair",
				ModelPartBuilder.create()
						// Личико и причёска (y = 10..15)
						.uv(0, 0).cuboid(-2.5F, 10.2F, -2.5F, 5.0F, 5.0F, 5.0F)
						// Задние длинные волосы
						.uv(20, 0).cuboid(-2.5F, 6.8F, 1.6F, 5.0F, 7.5F, 1.2F)
						// Левый и правый пышные хвостики
						.uv(34, 0).cuboid(2.3F, 7.0F, -1.0F, 1.6F, 7.2F, 2.0F)
						.uv(42, 0).cuboid(-3.9F, 7.0F, -1.0F, 1.6F, 7.2F, 2.0F)
						// Бантик на макушке (до y = 16.0F = ровно 1.0 блок!)
						.uv(0, 11).cuboid(-2.5F, 15.0F, -1.0F, 5.0F, 1.0F, 2.0F),
				ModelTransform.NONE
		);

		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public void render(
			PartyDollEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		matrices.push();

		if (entity.isActive()) {
			float time = entity.age + tickDelta;
			float wave = 0.5F - 0.5F * MathHelper.cos(time * 0.48F);
			float squish = wave * wave * (3.0F - 2.0F * wave);
			float scaleY = MathHelper.lerp(squish, 1.0F, 0.28F);
			float scaleXZ = MathHelper.lerp(squish, 1.0F, 1.36F);
			float spinDeg = (time * 18.0F) % 360.0F;

			matrices.scale(scaleXZ, scaleY, scaleXZ);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getDollYaw() + spinDeg));
		} else {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getDollYaw()));
		}

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
		this.root.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(PartyDollEntity entity) {
		return TEXTURE;
	}
}
