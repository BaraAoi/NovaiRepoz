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
 * 3D-рендерер «Жёлтого Дождевика Люче» на игроке (EquipmentSlot.CHEST):
 * Рисует объёмный жёлтый капюшон дождевика вокруг головы (с вырезом для лица),
 * расклёшенный непромокаемый плащ, 3D-крестик миссионерских чёток на груди и жёлтые рукава!
 */
public class LuceRaincoatArmorRenderer implements ArmorRenderer {
	public static final EntityModelLayer RAINCOAT_ARMOR_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "luce_raincoat_armor"), "main");

	private ModelPart hoodPart;
	private ModelPart bodyPart;
	private ModelPart rightArmPart;
	private ModelPart leftArmPart;

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		root.addChild(
				"raincoat_hood",
				ModelPartBuilder.create()
						.uv(32, 8).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.62F)),
				ModelTransform.NONE
		);

		root.addChild(
				"raincoat_body",
				ModelPartBuilder.create()
						.uv(0, 16).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F, new Dilation(0.35F))
						.uv(48, 0).cuboid(-1.0F, 2.4F, -2.75F, 2.0F, 3.0F, 1.0F, new Dilation(0.08F))
						.uv(0, 30).cuboid(-4.5F, 9.2F, -2.5F, 9.0F, 6.0F, 5.0F, new Dilation(0.28F)),
				ModelTransform.NONE
		);

		root.addChild(
				"raincoat_right_arm",
				ModelPartBuilder.create()
						.uv(28, 24).cuboid(-2.5F, -2.0F, -2.0F, 3.0F, 11.0F, 4.0F, new Dilation(0.68F)),
				ModelTransform.NONE
		);

		root.addChild(
				"raincoat_left_arm",
				ModelPartBuilder.create()
						.uv(42, 24).cuboid(-0.5F, -2.0F, -2.0F, 3.0F, 11.0F, 4.0F, new Dilation(0.68F)),
				ModelTransform.NONE
		);

		return TexturedModelData.of(modelData, 64, 64);
	}

	private void ensureLoaded() {
		if (this.bodyPart == null) {
			ModelPart root = getTexturedModelData().createModel();
			this.hoodPart = root.getChild("raincoat_hood");
			this.bodyPart = root.getChild("raincoat_body");
			this.rightArmPart = root.getChild("raincoat_right_arm");
			this.leftArmPart = root.getChild("raincoat_left_arm");
		}
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
		if (slot != EquipmentSlot.CHEST) {
			return;
		}
		ensureLoaded();

		contextModel.head.copyTransform(this.hoodPart);
		contextModel.body.copyTransform(this.bodyPart);
		contextModel.rightArm.copyTransform(this.rightArmPart);
		contextModel.leftArm.copyTransform(this.leftArmPart);

		VertexConsumer vc = ItemRenderer.getArmorGlintConsumer(
				vertexConsumers,
				RenderLayer.getArmorCutoutNoCull(LuceEntityRenderer.PEACEFUL_TEXTURE),
				stack.hasGlint()
		);

		if (entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
			this.hoodPart.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		}
		this.bodyPart.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		this.rightArmPart.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
		this.leftArmPart.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
	}
}
