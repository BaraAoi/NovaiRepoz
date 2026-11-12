package com.novaimod.client.render;

import com.novaimod.entity.LuceEntity;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * 3D-модель Люче (Luce — аниме-пилигрим Ватикана):
 * - Синие волосы с боковыми прядями + объёмный жёлтый капюшон дождевика.
 * - Расклёшенный жёлтый плащ-дождевик + 3D-крестик пятицветных миссионерских чёток на груди.
 * - Зелёные резиновые сапожки со следами дорожной грязи паломника.
 * - 3D раздвоенный посох пилигрима («бордоне») в правой руке.
 * - В режиме Божественного Гнева за головой загорается вращающийся святой нимб, а поза меняется на боевую!
 */
public class LuceEntityModel extends SinglePartEntityModel<LuceEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart haloRing;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart pilgrimStaff;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public LuceEntityModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.haloRing = this.head.getChild("halo_ring");
		this.rightArm = root.getChild("right_arm");
		this.pilgrimStaff = this.rightArm.getChild("pilgrim_staff");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		ModelPartData head = root.addChild(
				"head",
				ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
						.uv(32, 8).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.48F))
						.uv(32, 0).cuboid(2.2F, -1.6F, -4.25F, 2.0F, 6.0F, 2.0F)
						.uv(40, 0).cuboid(-4.2F, -1.6F, -4.25F, 2.0F, 6.0F, 2.0F),
				ModelTransform.pivot(0.0F, 2.0F, 0.0F)
		);

		head.addChild(
				"halo_ring",
				ModelPartBuilder.create().uv(40, 42).cuboid(-4.0F, -4.0F, -0.5F, 8.0F, 8.0F, 1.0F),
				ModelTransform.pivot(0.0F, -6.8F, 4.4F)
		);

		root.addChild(
				"body",
				ModelPartBuilder.create()
						.uv(0, 16).cuboid(-4.0F, 2.0F, -2.0F, 8.0F, 10.0F, 4.0F)
						.uv(48, 0).cuboid(-1.0F, 4.4F, -2.65F, 2.0F, 3.0F, 1.0F)
						.uv(0, 30).cuboid(-4.5F, 11.2F, -2.5F, 9.0F, 6.0F, 5.0F),
				ModelTransform.NONE
		);

		ModelPartData rightArm = root.addChild(
				"right_arm",
				ModelPartBuilder.create().uv(28, 24).cuboid(-2.5F, -1.5F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(-4.5F, 3.8F, 0.0F)
		);

		// Раздвоенный посох паломника («бордоне») в правой ладони Люче
		ModelPartData staff = rightArm.addChild(
				"pilgrim_staff",
				ModelPartBuilder.create().uv(28, 42).cuboid(-0.5F, -11.0F, -0.5F, 1.0F, 18.0F, 1.0F),
				ModelTransform.of(-1.0F, 8.0F, -1.5F, 1.15F, 0.0F, 0.0F)
		);
		staff.addChild(
				"staff_fork_l",
				ModelPartBuilder.create().uv(32, 42).cuboid(-0.5F, -3.8F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(0.3F, -10.6F, 0.0F, 0.0F, 0.0F, 0.38F)
		);
		staff.addChild(
				"staff_fork_r",
				ModelPartBuilder.create().uv(36, 42).cuboid(-0.5F, -3.8F, -0.5F, 1.0F, 4.0F, 1.0F),
				ModelTransform.of(-0.3F, -10.6F, 0.0F, 0.0F, 0.0F, -0.38F)
		);

		root.addChild(
				"left_arm",
				ModelPartBuilder.create().uv(42, 24).cuboid(-0.5F, -1.5F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(4.5F, 3.8F, 0.0F)
		);

		root.addChild(
				"right_leg",
				ModelPartBuilder.create().uv(0, 42).cuboid(-1.6F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
				ModelTransform.pivot(-2.0F, 13.0F, 0.0F)
		);
		root.addChild(
				"left_leg",
				ModelPartBuilder.create().uv(14, 42).cuboid(-1.4F, 0.0F, -2.0F, 3.0F, 11.0F, 4.0F),
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
			LuceEntity entity,
			float limbAngle,
			float limbDistance,
			float animationProgress,
			float headYaw,
			float headPitch
	) {
		this.head.yaw = headYaw * ((float) Math.PI / 180.0F);
		this.head.pitch = headPitch * ((float) Math.PI / 180.0F);

		boolean wrath = entity.isWrathMode();
		boolean praying = entity.isPrayingPose();

		this.haloRing.visible = wrath;
		if (wrath) {
			this.haloRing.roll = animationProgress * 0.09F;
		}

		if (praying && !wrath) {
			// Коленопреклонённая молитва паломницы со сложенными руками
			this.rightLeg.pitch = -1.25F;
			this.rightLeg.yaw = 0.12F;
			this.leftLeg.pitch = -1.25F;
			this.leftLeg.yaw = -0.12F;

			this.rightArm.pitch = -0.95F + MathHelper.sin(animationProgress * 0.07F) * 0.04F;
			this.rightArm.yaw = -0.35F;
			this.rightArm.roll = 0.08F;

			this.leftArm.pitch = -0.95F + MathHelper.sin(animationProgress * 0.07F) * 0.04F;
			this.leftArm.yaw = 0.35F;
			this.leftArm.roll = -0.08F;
			this.pilgrimStaff.pitch = 0.65F;
			return;
		}

		this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662F) * 1.1F * limbDistance;
		this.rightLeg.yaw = 0.0F;
		this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 1.1F * limbDistance;
		this.leftLeg.yaw = 0.0F;

		this.rightArm.yaw = 0.0F;
		this.leftArm.yaw = 0.0F;

		int castTicks = entity.getCastAnimTicks();
		if (castTicks > 0) {
			// Воздевание святого посоха к небу во время призыва луча или благословения!
			float lift = Math.min(1.0F, castTicks / 10.0F);
			this.rightArm.pitch = -2.15F * lift - 0.52F * (1.0F - lift);
			this.rightArm.roll = 0.14F;
			this.pilgrimStaff.pitch = 1.10F * lift + 0.52F * (1.0F - lift);
			this.leftArm.pitch = -0.85F * lift;
			this.leftArm.roll = -0.15F;
			return;
		}

		if (wrath) {
			// Боевая стойка «Сила Бога и Аниме!»: посох поднят для небесной кары!
			this.rightArm.pitch = -1.35F + MathHelper.sin(animationProgress * 0.22F) * 0.22F;
			this.rightArm.roll = 0.16F;
			this.pilgrimStaff.pitch = 0.85F;

			this.leftArm.pitch = -0.55F + MathHelper.cos(limbAngle * 0.6662F) * 0.6F * limbDistance;
			this.leftArm.roll = -0.18F;
		} else {
			// Мирная поступь пилигрима с опорой на посох в правой руке
			this.rightArm.pitch = -0.52F + MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 0.38F * limbDistance;
			this.rightArm.roll = 0.06F;
			this.pilgrimStaff.pitch = 0.52F;

			this.leftArm.pitch = MathHelper.cos(limbAngle * 0.6662F) * 0.85F * limbDistance;
			this.leftArm.roll = -0.08F;
		}
	}
}
