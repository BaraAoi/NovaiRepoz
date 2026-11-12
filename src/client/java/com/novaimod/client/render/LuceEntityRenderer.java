package com.novaimod.client.render;

import com.novaimod.NovaiMod;
import com.novaimod.entity.LuceEntity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class LuceEntityRenderer extends MobEntityRenderer<LuceEntity, LuceEntityModel> {
	public static final EntityModelLayer LUCE_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "luce"), "main");

	public static final Identifier PEACEFUL_TEXTURE =
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/luce/luce_peaceful.png");
	public static final Identifier WRATH_TEXTURE =
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/luce/luce_wrath.png");

	public LuceEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new LuceEntityModel(context.getPart(LUCE_MODEL_LAYER)), 0.44F);
	}

	@Override
	public void render(
			LuceEntity entity,
			float f,
			float tickDelta,
			MatrixStack matrixStack,
			VertexConsumerProvider vertexConsumerProvider,
			int light
	) {
		matrixStack.push();
		if (entity.isPrayingPose() && !entity.isWrathMode()) {
			matrixStack.translate(0.0F, -0.38F, 0.0F);
		} else if (entity.isWrathMode() && entity.getBossPhase() >= 3) {
			// В 3-й фазе Божественного Гнева Люче слегка парит в золотых потоках света!
			float hover = 0.14F + MathHelper.sin((entity.age + tickDelta) * 0.16F) * 0.08F;
			matrixStack.translate(0.0F, hover, 0.0F);
		}
		super.render(entity, f, tickDelta, matrixStack, vertexConsumerProvider, light);
		matrixStack.pop();
	}

	@Override
	public Identifier getTexture(LuceEntity entity) {
		return entity.isWrathMode() ? WRATH_TEXTURE : PEACEFUL_TEXTURE;
	}
}
