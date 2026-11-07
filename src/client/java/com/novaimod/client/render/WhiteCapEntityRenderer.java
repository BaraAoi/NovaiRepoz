package com.novaimod.client.render;

import com.novaimod.NovaiMod;
import com.novaimod.entity.WhiteCapEntity;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class WhiteCapEntityRenderer extends MobEntityRenderer<WhiteCapEntity, WhiteCapEntityModel> {
	public static final EntityModelLayer WHITE_CAP_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "white_cap"), "main");

	public static final Identifier MEMBER_TEXTURE =
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/white_cap/member.png");
	public static final Identifier GRAND_MASTER_TEXTURE =
			Identifier.of(NovaiMod.MOD_ID, "textures/entity/white_cap/grand_master.png");

	public WhiteCapEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new WhiteCapEntityModel(context.getPart(WHITE_CAP_MODEL_LAYER)), 0.52F);
	}

	@Override
	protected void scale(WhiteCapEntity entity, MatrixStack matrices, float amount) {
		if (entity.getRank() == WhiteCapEntity.RANK_GRAND_MASTER) {
			// Босс чуть статнее обычных бойцов, но основной вау-эффект — от гигантского колпака!
			matrices.scale(1.12F, 1.12F, 1.12F);
		}
	}

	@Override
	public void render(
			WhiteCapEntity mobEntity,
			float f,
			float g,
			MatrixStack matrixStack,
			VertexConsumerProvider vertexConsumerProvider,
			int i
	) {
		super.render(mobEntity, f, g, matrixStack, vertexConsumerProvider, i);
	}

	@Override
	public Identifier getTexture(WhiteCapEntity entity) {
		return entity.getRank() == WhiteCapEntity.RANK_GRAND_MASTER ? GRAND_MASTER_TEXTURE : MEMBER_TEXTURE;
	}
}
