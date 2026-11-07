package com.novaimod.client.render;

import com.novaimod.NovaiMod;
import com.novaimod.entity.CatgirlEntity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class CatgirlEntityRenderer extends MobEntityRenderer<CatgirlEntity, CatgirlEntityModel> {
	public static final EntityModelLayer CATGIRL_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "catgirl"), "main");

	private static final Identifier CAPTIVE_TEXTURE =
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/catgirl/captive.png");
	private static final Identifier[] RESCUED_TEXTURES = new Identifier[]{
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/catgirl/shiro.png"),
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/catgirl/kuro.png"),
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/catgirl/miko.png"),
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/catgirl/yuki.png")
	};

	public CatgirlEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new CatgirlEntityModel(context.getPart(CATGIRL_MODEL_LAYER)), 0.42F);
	}

	@Override
	public void render(
			CatgirlEntity entity,
			float f,
			float g,
			MatrixStack matrixStack,
			VertexConsumerProvider vertexConsumerProvider,
			int i
	) {
		matrixStack.push();
		if (entity.isSittingPose()) {
			matrixStack.translate(0.0F, -0.45F, 0.0F);
		}
		super.render(entity, f, g, matrixStack, vertexConsumerProvider, i);
		matrixStack.pop();
	}

	@Override
	public Identifier getTexture(CatgirlEntity entity) {
		if (!entity.isRescued()) {
			return CAPTIVE_TEXTURE;
		}
		int v = Math.floorMod(entity.getVariantId(), RESCUED_TEXTURES.length);
		return RESCUED_TEXTURES[v];
	}
}
