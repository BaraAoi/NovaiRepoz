package com.novaimod.client.render;

import com.novaimod.NovaiMod;
import com.novaimod.entity.HolyOrderEntity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class HolyOrderEntityRenderer extends MobEntityRenderer<HolyOrderEntity, HolyOrderEntityModel> {
	public static final EntityModelLayer HOLY_ORDER_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "holy_order"), "main");

	private static final Identifier[] RANK_TEXTURES = new Identifier[]{
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/holy_order/crusader.png"),
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/holy_order/inquisitor.png"),
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/holy_order/priest.png"),
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/holy_order/cardinal.png")
	};

	public HolyOrderEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new HolyOrderEntityModel(context.getPart(HOLY_ORDER_MODEL_LAYER)), 0.45F);
	}

	@Override
	public void render(
			HolyOrderEntity entity,
			float f,
			float tickDelta,
			MatrixStack matrixStack,
			VertexConsumerProvider vertexConsumerProvider,
			int light
	) {
		matrixStack.push();
		if (entity.getRank() == HolyOrderEntity.RANK_CARDINAL) {
			matrixStack.scale(1.12F, 1.12F, 1.12F);
		}
		super.render(entity, f, tickDelta, matrixStack, vertexConsumerProvider, light);
		matrixStack.pop();
	}

	@Override
	public Identifier getTexture(HolyOrderEntity entity) {
		int r = Math.floorMod(entity.getRank(), RANK_TEXTURES.length);
		return RANK_TEXTURES[r];
	}
}
