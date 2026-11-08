package com.novaimod.client.render;

import com.novaimod.entity.HolyOrderEntity;

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
 * 3D-модель бойцов и духовенства «Святого Ордена Паломников Люче»:
 * - Паладин: великий шлем с Т-крестом, белая табарда Ватикана, 3D меч и рыцарский каплевидный щит!
 * - Арбалетчик Инквизиции: берет со знаком ракушки, сине-жёлтый плащ и освящённый арбалет!
 * - Священник Собора: бело-золотая сутана с епитрахилью, малая митра и золотое кадило-посох!
 * - Архиепископ Собора (Босс): багряно-золотое облачение, высокая Папская Митра и крестовый посох!
 */
public class HolyOrderEntityModel extends SinglePartEntityModel<HolyOrderEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart mitreLower;
	private final ModelPart mitrePeak;
	private final ModelPart rightArm;
	private final ModelPart weapon;
	private final ModelPart leftArm;
	private final ModelPart kiteShield;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public HolyOrderEntityModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.mitreLower = this.head.getChild("mitre_lower");
		this.mitrePeak = this.mitreLower.getChild("mitre_peak");
		this.rightArm = root.getChild("right_arm");
		this.weapon = this.rightArm.getChild("weapon");
		this.leftArm = root.getChild("left_arm");
		this.kiteShield = this.leftArm.getChild("kite_shield");
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
						.uv(32, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new Dilation(0.45F)),
				ModelTransform.pivot(0.0F, 2.0F, 0.0F)
		);

		ModelPartData mitreLower = head.addChild(
				"mitre_lower",
				ModelPartBuilder.create().uv(34, 49).cuboid(-3.0F, -13.2F, -2.5F, 6.0F, 6.0F, 5.0F),
				ModelTransform.NONE
		);
		mitreLower.addChild(
				"mitre_peak",
				ModelPartBuilder.create().uv(34, 38).cuboid(-2.0F, -17.8F, -1.0F, 4.0F, 5.0F, 2.0F),
				ModelTransform.NONE
		);

		root.addChild(
				"body",
				ModelPartBuilder.create()
						.uv(0, 16).cuboid(-4.0F, 2.0F, -2.0F, 8.0F, 11.0F, 4.0F)
						.uv(0, 32).cuboid(-4.5F, 11.8F, -2.5F, 9.0F, 7.0F, 5.0F),
				ModelTransform.NONE
		);

		ModelPartData rightArm = root.addChild(
				"right_arm",
				ModelPartBuilder.create().uv(28, 16).cuboid(-3.0F, -1.5F, -2.0F, 4.0F, 11.0F, 4.0F),
				ModelTransform.pivot(-5.0F, 3.8F, 0.0F)
		);
		rightArm.addChild(
				"weapon",
				ModelPartBuilder.create()
						.uv(28, 32).cuboid(-0.5F, -11.5F, -1.0F, 1.0F, 15.0F, 2.0F)
						.uv(34, 32).cuboid(-2.5F, -9.5F, -1.0F, 5.0F, 3.0F, 2.0F),
				ModelTransform.of(-1.0F, 8.0F, -1.5F, 0.95F, 0.0F, 0.0F)
		);

		ModelPartData leftArm = root.addChild(
				"left_arm",
				ModelPartBuilder.create().uv(44, 16).cuboid(-1.0F, -1.5F, -2.0F, 4.0F, 11.0F, 4.0F),
				ModelTransform.pivot(5.0F, 3.8F, 0.0F)
		);
		leftArm.addChild(
				"kite_shield",
				ModelPartBuilder.create().uv(48, 32).cuboid(2.2F, 0.5F, -3.0F, 1.0F, 9.0F, 6.0F),
				ModelTransform.NONE
		);

		root.addChild(
				"right_leg",
				ModelPartBuilder.create().uv(0, 45).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F),
				ModelTransform.pivot(-2.0F, 13.0F, 0.0F)
		);
		root.addChild(
				"left_leg",
				ModelPartBuilder.create().uv(16, 45).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 11.0F, 4.0F),
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
			HolyOrderEntity entity,
			float limbAngle,
			float limbDistance,
			float animationProgress,
			float headYaw,
			float headPitch
	) {
		this.head.yaw = headYaw * ((float) Math.PI / 180.0F);
		this.head.pitch = headPitch * ((float) Math.PI / 180.0F);

		int rank = entity.getRank();
		int act = entity.getActionAnimTicks();

		this.kiteShield.visible = (rank == HolyOrderEntity.RANK_CRUSADER);
		this.mitreLower.visible = (rank == HolyOrderEntity.RANK_PRIEST || rank == HolyOrderEntity.RANK_CARDINAL);
		this.mitrePeak.visible = (rank == HolyOrderEntity.RANK_CARDINAL);

		this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662F) * 1.15F * limbDistance;
		this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 1.15F * limbDistance;

		if (rank == HolyOrderEntity.RANK_INQUISITOR) {
			// Арбалетная стойка стрелка Инквизиции
			this.rightArm.pitch = -1.35F + (act > 0 ? -0.25F : 0.0F);
			this.rightArm.yaw = -0.18F;
			this.rightArm.roll = 0.0F;
			this.weapon.pitch = 1.48F;

			this.leftArm.pitch = -1.25F;
			this.leftArm.yaw = 0.42F;
			this.leftArm.roll = 0.0F;
			return;
		}

		this.rightArm.yaw = 0.0F;
		this.leftArm.yaw = 0.0F;

		if (act > 0) {
			float lift = Math.min(1.0F, act / 8.0F);
			this.rightArm.pitch = -1.85F * lift;
			this.rightArm.roll = 0.12F;
			this.weapon.pitch = 0.85F;
			this.leftArm.pitch = (rank == HolyOrderEntity.RANK_CRUSADER) ? -0.75F : -0.55F * lift;
			this.leftArm.roll = -0.12F;
			return;
		}

		this.rightArm.pitch = -0.45F + MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 0.55F * limbDistance;
		this.rightArm.roll = 0.06F;
		this.weapon.pitch = (rank == HolyOrderEntity.RANK_CRUSADER) ? 0.92F : 0.55F;

		if (rank == HolyOrderEntity.RANK_CRUSADER) {
			// Щит прикрывает корпус рыцаря!
			this.leftArm.pitch = -0.52F + MathHelper.cos(limbAngle * 0.6662F) * 0.35F * limbDistance;
			this.leftArm.yaw = 0.22F;
			this.leftArm.roll = -0.06F;
		} else {
			this.leftArm.pitch = MathHelper.cos(limbAngle * 0.6662F) * 0.75F * limbDistance;
			this.leftArm.roll = -0.06F;
		}
	}
}
