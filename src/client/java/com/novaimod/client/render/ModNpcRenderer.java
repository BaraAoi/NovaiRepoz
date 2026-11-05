package com.novaimod.client.render;

import java.util.EnumMap;
import java.util.Map;

import com.novaimod.NovaiMod;
import com.novaimod.entity.ModNpcEntity;
import com.novaimod.entity.NpcRole;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/**
 * Рендерер кастомных NPC мода.
 * Намеренно НЕ добавляет ArmorFeatureRenderer и HeldItemFeatureRenderer:
 * ни броня, ни меч никогда визуально не показываются на моделях NPC!
 */
public class ModNpcRenderer extends MobEntityRenderer<ModNpcEntity, PlayerEntityModel<ModNpcEntity>> {
	public static final EntityModelLayer NPC_MODEL_LAYER =
			new EntityModelLayer(Identifier.of(NovaiMod.MOD_ID, "mod_npc"), "main");

	private static final Map<NpcRole, Identifier> SKIN_CACHE = new EnumMap<>(NpcRole.class);

	static {
		for (NpcRole role : NpcRole.values()) {
			SKIN_CACHE.put(role, Identifier.of(NovaiMod.MOD_ID, "textures/entity/npc/" + role.getId() + ".png"));
		}
	}

	public ModNpcRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(NPC_MODEL_LAYER), false), 0.5F);
	}

	public static TexturedModelData getTexturedModelData() {
		return TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false), 64, 64);
	}

	@Override
	public Identifier getTexture(ModNpcEntity entity) {
		return SKIN_CACHE.getOrDefault(entity.getNpcRole(), SKIN_CACHE.get(NpcRole.GROCERY_SELLER_1));
	}
}
