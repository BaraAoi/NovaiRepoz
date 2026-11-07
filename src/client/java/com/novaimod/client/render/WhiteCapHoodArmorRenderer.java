package com.novaimod.client.render;

import com.novaimod.NovaiMod;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.Dilation;
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
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Кастомный 3D-рендерер «Колпака Чумного Доктора» на голове ИГРОКА (или стойки для брони):
 * рисует 3D-маску чумного доктора с клювом, светящимися окулярами и огромным гномьим колпаком выше роста игрока!
 */
public class WhiteCapHoodArmorRenderer implements ArmorRenderer {
	public static final EntityModelLayer HOOD_ARMOR_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "white_cap_hood_armor"), "main");

	private ModelPart hoodHead;

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		ModelPartData head = root.addChild(
				"hood_head",
				ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.55F))
						.uv(36, 0).cuboid(-3.7F, -5.9F, -5.2F, 3.0F, 3.0F, 1.0F)
						.uv(46, 0).cuboid(0.7F, -5.9F, -5.2F, 3.0F, 3.0F, 1.0F),
				ModelTransform.NONE
		);

		head.addChild(
				"beak_base",
				ModelPartBuilder.create().uv(36, 6).cuboid(-2.0F, -4.0F, -7.4F, 4.0F, 3.0F, 3.0F),
				ModelTransform.NONE
		);
		head.addChild(
				"beak_mid",
				ModelPartBuilder.create().uv(52, 6).cuboid(-1.5F, -0.5F, -3.0F, 3.0F, 2.0F, 3.0F),
				ModelTransform.of(0.0F, -3.6F, -7.1F, 0.18F, 0.0F, 0.0F)
		);
		head.addChild(
				"beak_tip",
				ModelPartBuilder.create().uv(68, 6).cuboid(-1.0F, -0.4F, -2.8F, 2.0F, 2.0F, 3.0F),
				ModelTransform.of(0.0F, -3.0F, -9.8F, 0.38F, 0.0F, 0.0F)
		);

		head.addChild(
				"giant_gnome_cap",
				ModelPartBuilder.create()
						.uv(0, 64).cuboid(-5.0F, -2.2F, -5.0F, 10.0F, 3.0F, 10.0F)
						.uv(0, 78).cuboid(-4.5F, -8.0F, -4.3F, 9.0F, 6.0F, 9.0F)
						.uv(40, 64).cuboid(-4.0F, -13.8F, -3.6F, 8.0F, 6.0F, 8.0F)
						.uv(40, 80).cuboid(-3.5F, -19.5F, -2.5F, 7.0F, 6.0F, 7.0F)
						.uv(76, 64).cuboid(-3.0F, -25.0F, -1.0F, 6.0F, 6.0F, 6.0F)
						.uv(76, 80).cuboid(-2.0F, -29.6F, 0.8F, 4.0F, 5.0F, 4.0F)
						.uv(76, 92).cuboid(-1.5F, -33.0F, 2.6F, 3.0F, 4.0F, 4.0F)
						.uv(96, 92).cuboid(-1.5F, -31.8F, 6.2F, 3.0F, 3.0F, 3.0F),
				ModelTransform.pivot(0.0F, -7.8F, 0.0F)
		);

		return TexturedModelData.of(modelData, 128, 128);
	}

	private ModelPart ensureLoaded() {
		if (this.hoodHead == null) {
			this.hoodHead = getTexturedModelData().createModel().getChild("hood_head");
		}
		return this.hoodHead;
	}

	@Override
	public void render(
			MatrixStack matrices,
			VertexConsumerProvider vertexConsumers,
			ItemStack stack,
			LivingEntity entity,
			EquipmentSlot slot,
			int light,
			BipedEntityModel<LivingEntity> contextModel
	) {
		if (slot != EquipmentSlot.HEAD) {
			return;
		}
		ModelPart part = ensureLoaded();
		contextModel.head.copyTransform(part);
		VertexConsumer vc = ItemRenderer.getArmorGlintConsumer(
				vertexConsumers,
				RenderLayer.getArmorCutoutNoCull(WhiteCapEntityRenderer.MEMBER_TEXTURE),
				stack.hasGlint()
		);
		part.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
	}
}
