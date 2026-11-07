package com.novaimod.client.render;

import com.novaimod.entity.WhiteCapEntity;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * 3D-модель бойца «Клана Белых Колпаков»:
 * - Обычный человеческий рост тела (32px = 2 блока) в длинной белой мантии чумного алхимика с пелериной на плечах.
 * - Объёмная 3D-маска чумного доктора: изогнутый вороний клюв из 3 сегментов и светящиеся окуляры.
 * - ОГРОМНЫЙ белый гномий колпак высотой 33 пикселя (> 2 блоков НАД головой, выше самого бойца!)
 *   с мягким загнутым назад верхом и помпоном, покачивающимся при ходьбе!
 */
public class WhiteCapEntityModel extends SinglePartEntityModel<WhiteCapEntity> {
	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart giantCap;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart bladeWeapon;
	private final ModelPart crossbowWeapon;
	private final ModelPart lanternWeapon;
	private final ModelPart caneWeapon;

	public WhiteCapEntityModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.giantCap = this.head.getChild("giant_gnome_cap");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.bladeWeapon = this.rightArm.getChild("blade_weapon");
		this.crossbowWeapon = this.rightArm.getChild("crossbow_weapon");
		this.lanternWeapon = this.rightArm.getChild("lantern_weapon");
		this.caneWeapon = this.rightArm.getChild("cane_weapon");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = new ModelData();
		ModelPartData root = modelData.getRoot();

		// 1. Голова + очки и клюв чумного доктора
		ModelPartData head = root.addChild(
				"head",
				ModelPartBuilder.create()
						.uv(0, 0).cuboid(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F)
						.uv(36, 0).cuboid(-3.6F, -5.8F, -4.7F, 3.0F, 3.0F, 1.0F)
						.uv(46, 0).cuboid(0.6F, -5.8F, -4.7F, 3.0F, 3.0F, 1.0F),
				ModelTransform.pivot(0.0F, 0.0F, 0.0F)
		);

		head.addChild(
				"beak_base",
				ModelPartBuilder.create().uv(36, 6).cuboid(-2.0F, -4.0F, -7.0F, 4.0F, 3.0F, 3.0F),
				ModelTransform.NONE
		);
		head.addChild(
				"beak_mid",
				ModelPartBuilder.create().uv(52, 6).cuboid(-1.5F, -0.5F, -3.0F, 3.0F, 2.0F, 3.0F),
				ModelTransform.of(0.0F, -3.6F, -6.7F, 0.18F, 0.0F, 0.0F)
		);
		head.addChild(
				"beak_tip",
				ModelPartBuilder.create().uv(68, 6).cuboid(-1.0F, -0.4F, -2.8F, 2.0F, 2.0F, 3.0F),
				ModelTransform.of(0.0F, -3.0F, -9.4F, 0.38F, 0.0F, 0.0F)
		);

		// ОГРОМНЫЙ БЕЛЫЙ КОЛПАК КАК У ГНОМОВ (высота 33px = 2.06 блока поверх головы!)
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
				ModelTransform.pivot(0.0F, -7.5F, 0.0F)
		);

		// 2. Торс + белая мантия до щиколоток + наплечная пелерина чумного доктора + подсумок с колбами
		root.addChild(
				"body",
				ModelPartBuilder.create()
						.uv(0, 16).cuboid(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F)
						.uv(32, 16).cuboid(-5.0F, -0.3F, -3.0F, 10.0F, 5.0F, 6.0F)
						.uv(68, 16).cuboid(3.8F, 10.5F, -2.0F, 2.0F, 4.0F, 4.0F)
						.uv(0, 32).cuboid(-4.5F, 11.5F, -2.5F, 9.0F, 10.0F, 5.0F),
				ModelTransform.NONE
		);

		// 3. Руки + 3D оружие ранга в правой руке
		ModelPartData rightArm = root.addChild(
				"right_arm",
				ModelPartBuilder.create().uv(32, 32).cuboid(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				ModelTransform.pivot(-5.0F, 2.0F, 0.0F)
		);
		rightArm.addChild(
				"blade_weapon",
				ModelPartBuilder.create()
						.uv(84, 0).cuboid(-1.5F, 7.5F, -13.0F, 1.0F, 2.0F, 12.0F)
						.uv(112, 0).cuboid(-2.0F, 6.5F, -2.5F, 2.0F, 4.0F, 1.0F),
				ModelTransform.NONE
		);
		rightArm.addChild(
				"crossbow_weapon",
				ModelPartBuilder.create()
						.uv(84, 16).cuboid(-2.0F, 7.0F, -10.0F, 2.0F, 2.0F, 9.0F)
						.uv(84, 27).cuboid(-5.0F, 7.5F, -10.5F, 8.0F, 1.0F, 2.0F),
				ModelTransform.NONE
		);
		rightArm.addChild(
				"lantern_weapon",
				ModelPartBuilder.create()
						.uv(84, 31).cuboid(-2.5F, 8.2F, -4.5F, 3.0F, 4.0F, 3.0F),
				ModelTransform.NONE
		);
		rightArm.addChild(
				"cane_weapon",
				ModelPartBuilder.create()
						.uv(84, 40).cuboid(-1.5F, 7.5F, -15.0F, 1.0F, 1.0F, 16.0F)
						.uv(84, 58).cuboid(-2.5F, 6.5F, -17.5F, 3.0F, 3.0F, 3.0F),
				ModelTransform.NONE
		);

		root.addChild(
				"left_arm",
				ModelPartBuilder.create().uv(48, 32).cuboid(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				ModelTransform.pivot(5.0F, 2.0F, 0.0F)
		);

		// 4. Ноги в тёмных сапогах
		root.addChild(
				"right_leg",
				ModelPartBuilder.create().uv(0, 48).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				ModelTransform.pivot(-2.0F, 12.0F, 0.0F)
		);
		root.addChild(
				"left_leg",
				ModelPartBuilder.create().uv(16, 48).cuboid(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
				ModelTransform.pivot(2.0F, 12.0F, 0.0F)
		);

		return TexturedModelData.of(modelData, 128, 128);
	}

	@Override
	public ModelPart getPart() {
		return this.root;
	}

	@Override
	public void setAngles(
			WhiteCapEntity entity,
			float limbAngle,
			float limbDistance,
			float animationProgress,
			float headYaw,
			float headPitch
	) {
		this.head.yaw = headYaw * ((float) Math.PI / 180.0F);
		this.head.pitch = headPitch * ((float) Math.PI / 180.0F);

		// Мягкое живое покачивание огромного гномьего колпака при шаге и на ветру!
		this.giantCap.roll = MathHelper.cos(limbAngle * 0.6662F) * 0.08F * limbDistance
				+ MathHelper.sin(animationProgress * 0.06F) * 0.025F;
		this.giantCap.pitch = MathHelper.abs(MathHelper.sin(limbAngle * 0.6662F)) * 0.06F * limbDistance;

		this.rightLeg.pitch = MathHelper.cos(limbAngle * 0.6662F) * 1.15F * limbDistance;
		this.leftLeg.pitch = MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 1.15F * limbDistance;

		int rank = entity.getRank();
		this.bladeWeapon.visible = (rank == WhiteCapEntity.RANK_HUNTER);
		this.crossbowWeapon.visible = (rank == WhiteCapEntity.RANK_ARBALIST);
		this.lanternWeapon.visible = (rank == WhiteCapEntity.RANK_APOTHECARY);
		this.caneWeapon.visible = (rank == WhiteCapEntity.RANK_GRAND_MASTER);

		float walkArmR = MathHelper.cos(limbAngle * 0.6662F + (float) Math.PI) * 1.0F * limbDistance;
		float walkArmL = MathHelper.cos(limbAngle * 0.6662F) * 1.0F * limbDistance;

		int anim = entity.getActionAnimTicks();
		if (rank == WhiteCapEntity.RANK_ARBALIST) {
			// Стойка прицеливания из арбалета
			this.rightArm.pitch = -1.35F + (anim > 0 ? -0.18F : 0.0F);
			this.rightArm.yaw = -0.12F;
			this.leftArm.pitch = -1.25F;
			this.leftArm.yaw = 0.48F;
		} else if (anim > 0) {
			float prog = anim / 12.0F;
			this.rightArm.pitch = -1.85F * MathHelper.sin(prog * (float) Math.PI);
			this.rightArm.yaw = 0.0F;
			this.leftArm.pitch = walkArmL;
			this.leftArm.yaw = 0.0F;
		} else {
			this.rightArm.pitch = walkArmR - 0.18F;
			this.rightArm.yaw = 0.0F;
			this.leftArm.pitch = walkArmL;
			this.leftArm.yaw = 0.0F;
		}
	}
}
