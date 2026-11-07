package com.novaimod.client.render;

import com.novaimod.entity.CatgirlEntity;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * 3D-модель Кошкодевочки:
 * - Пушистые 3D кошачьи ушки, которые живо подёргиваются (и прижимаются от страха, пока она заперта в клетке!).
 * - Объёмные длинные волосы спереди и сзади, юбочка и колокольчик на шее.
 * - Длинный гибкий 3D кошачий хвостик из 2 сегментов, плавно виляющий в такт настроению!
 * - Полноценная поза сидения (когда ждёт дома/сидит на коврике).
 */
public class CatgirlEntityModel extends SinglePartEntityModel<CatgirlEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart leftEar;
	private final ModelPart rightEar;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart tailBase;
	private final ModelPart tailTip;

	public CatgirlEntityModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.leftEar = this.head.getChild("left_ear");
		this.rightEar = this.head.getChild("right_ear");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		ModelPart body = root.getChild("body");
		this.tailBase = body.getChild("tail_base");
		this.tailTip = this.tailBase.getChild("tail_tip");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// Голова (y = 2.0F как база шеи для роста ~1.82 блока)
		ModelPartData head = root.addChild(
				"head",
				ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
						.uv(0, 48).cuboid(-4.0F, -1.0F, 2.6F, 8.0F, 10.0F, 2.0F)
						.uv(22, 48).cuboid(2.6F, -1.5F, -4.2F, 2.0F, 7.0F, 2.0F)
						.uv(30, 48).cuboid(-4.6F, -1.5F, -4.2F, 2.0F, 7.0F, 2.0F),
				ModelTransform.pivot(0.0F, 2.0F, 0.0F)
		);

		// 3D пушистые кошачьи ушки
		head.addChild(
				"left_ear",
				ModelPartBuilder.create()
						.uv(32, 0).cuboid(-1.5F, -3.0F, -1.0F, 3.0F, 3.0F, 2.0F)
						.uv(32, 6).cuboid(-1.0F, -4.8F, -0.5F, 2.0F, 2.0F, 1.0F),
				ModelTransform.of(2.4F, -7.6F, -0.5F, 0.0F, 0.0F, 0.18F)
		);
		head.addChild(
				"right_ear",
				ModelPartBuilder.create()
						.uv(42, 0).cuboid(-1.5F, -3.0F, -1.0F, 3.0F, 3.0F, 2.0F)
						.uv(42, 6).cuboid(-1.0F, -4.8F, -0.5F, 2.0F, 2.0F, 1.0F),
				ModelTransform.of(-2.4F, -7.6F, -0.5F, 0.0F, 0.0F, -0.18F)
		);

		// Торс + юбочка + золотой колокольчик + 2-сегментный хвост!
		ModelPartData body = root.addChild(
				"body",
				ModelPartBuilder.create()
						.uv(0, 16).cuboid(-4.0F, 2.0F, -2.0F, 8.0F, 11.0F, 4.0F)
						.uv(52, 0).cuboid(-1.0F, 1.8F, -2.8F, 2.0F, 2.0F, 2.0F)
						.uv(0, 32).cuboid(-4.5F, 11.5F, -2.5F, 9.0F, 6.0F, 5.0F),
				ModelTransform.NONE
		);

		ModelPartData tailBase = body.addChild(
				"tail_base",
				ModelPartBuilder.create().uv(38, 48).cuboid(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F),
				ModelTransform.of(0.0F, 12.2F, 2.0F, -0.42F, 0.0F, 0.0F)
		);
		tailBase.addChild(
				"tail_tip",
				ModelPartBuilder.create().uv(38, 56).cuboid(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F),
				ModelTransform.of(0.0F, 0.0F, 5.5F, 0.55F, 0.0F, 0.0F)
		);

		// Изящные 3px ручки и ножки
		root.addChild(
				"right_arm",
				ModelPartBuilder.create().uv(32, 16).cuboid(-2.5F, -1.5F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(-4.5F, 3.8F, 0.0F)
		);
		root.addChild(
				"left_arm",
				ModelPartBuilder.create().uv(46, 16).cuboid(-0.5F, -1.5F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(4.5F, 3.8F, 0.0F)
		);

		root.addChild(
				"right_leg",
				ModelPartBuilder.create().uv(32, 32).cuboid(-1.6F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(-2.0F, 13.0F, 0.0F)
		);
		root.addChild(
				"left_leg",
				ModelPartBuilder.create().uv(46, 32).cuboid(-1.4F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(2.0F, 13.0F, 0.0F)
		);

		return TexturedModelData.of(modelData, 64, 64);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(
			CatgirlEntity entity,
			float limbAngle,
			float limbDistance,
			float animationProgress,
			float headYaw,
			float headPitch
	) {
		this.head.yaw = headYaw * ((float) Math.PI / 180.0F);
		this.head.pitch = headPitch * ((float) Math.PI / 180.0F);

		boolean rescued = entity.isRescued();
		// Ушки: прижаты в клетке учёных, бодро торчат и подёргиваются на свободе!
		float earTwitch = MathHelper.sin(animationProgress * 0.14F) * 0.06F;
		float baseTilt = rescued ? 0.18F : 0.52F;
		this.leftEar.roll = baseTilt + earTwitch;
		this.rightEar.roll = -baseTilt - earTwitch * 0.7F;

		// Хвостик плавно виляет восьмёркой!
		float wagSpeed = rescued ? 0.11F : 0.05F;
		float wagAmp = rescued ? 0.34F : 0.12F;
		this.tailBase.yaw = MathHelper.sin(animationProgress * wagSpeed) * wagAmp;
		this.tailBase.pitch = rescued ? -0.35F : -0.65F;
		this.tailTip.yaw = MathHelper.sin(animationProgress * wagSpeed - 0.8F) * (wagAmp * 0.85F);

		if (entity.isSittingPose()) {
			this.rightLeg.pitch = -1.38F;
			this.rightLeg.yaw = 0.16F;
			this.leftLeg.pitch = -1.38F;
			this.leftLeg.yaw = -0.16F;
			this.rightArm.pitch = -0.45F;
			this.rightArm.roll = 0.08F;
			this.leftArm.pitch = -0.45F;
			this.leftArm.roll = -0.08F;
		} else {
			this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662F) * 1.15F * limbDistance;
			this.rightLeg.yaw = 0.0F;
			this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 1.15F * limbDistance;
			this.leftLeg.yaw = 0.0F;

			this.rightArm.pitch = MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 0.85F * limbDistance;
			this.rightArm.roll = 0.08F;
			this.leftArm.pitch = MathHelper.cos(limbAngle * 0.6662F) * 0.85F * limbDistance;
			this.leftArm.roll = -0.08F;
		}
	}
}
