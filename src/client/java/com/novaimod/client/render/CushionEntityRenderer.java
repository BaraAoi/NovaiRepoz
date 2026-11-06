package com.novaimod.client.render;

import java.util.EnumMap;
import java.util.Map;

import com.novaimod.NovaiMod;
import com.novaimod.entity.CushionEntity;

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
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * 3D-рендерер мягкой стёганой Подушки (16 цветов).
 */
public class CushionEntityRenderer extends EntityRenderer<CushionEntity> {
	public static final EntityModelLayer CUSHION_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "cushion"), "main");

	private static final Map<DyeColor, Identifier> TEXTURES = new EnumMap<>(DyeColor.class);

	static {
		for (DyeColor color : DyeColor.values()) {
			TEXTURES.put(
					color,
					Identifier.of(NovaiMod.MOD_ID, "textures/entity/cushion/" + color.asString() + "_cushion.png")
			);
		}
	}

	private final ModelPart root;

	public CushionEntityRenderer(EntityRendererFactory.Context context) {
		super(context);
		this.root = context.getPart(CUSHION_MODEL_LAYER);
		this.shadowRadius = 0.3F;
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();
		root.addChild(
				"body",
				ModelPartBuilder.create()
						.uv(0, 0)
						.cuboid(-6.0F, 0.0F, -6.0F, 12.0F, 3.0F, 12.0F)
						.uv(0, 16)
						.cuboid(-5.0F, 2.5F, -5.0F, 10.0F, 1.0F, 10.0F),
				ModelTransform.NONE
		);
		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public void render(
			CushionEntity entity,
			float yaw,
			float tickDelta,
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			int light
	) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getCushionYaw()));
		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(getTexture(entity)));
		this.root.render(matrices, vc, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
	}

	@Override
	public Identifier getTexture(CushionEntity entity) {
		return TEXTURES.getOrDefault(entity.getDyeColor(), TEXTURES.get(DyeColor.WHITE));
	}
}
