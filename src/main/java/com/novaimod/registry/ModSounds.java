package com.novaimod.registry;

import com.novaimod.NovaiMod;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
	public static final Identifier YARIS_MEME_ID = Identifier.of(NovaiMod.MOD_ID, "yaris_meme");
	public static final SoundEvent YARIS_MEME = Registry.register(
			Registries.SOUND_EVENT,
			YARIS_MEME_ID,
			SoundEvent.of(YARIS_MEME_ID)
	);

	public static final Identifier CREEPER_BIRD_ID = Identifier.of(NovaiMod.MOD_ID, "creeper_bird");
	public static final SoundEvent CREEPER_BIRD = Registry.register(
			Registries.SOUND_EVENT,
			CREEPER_BIRD_ID,
			SoundEvent.of(CREEPER_BIRD_ID)
	);

	public static final Identifier CREEPER_AKBAR_ID = Identifier.of(NovaiMod.MOD_ID, "creeper_akbar");
	public static final SoundEvent CREEPER_AKBAR = Registry.register(
			Registries.SOUND_EVENT,
			CREEPER_AKBAR_ID,
			SoundEvent.of(CREEPER_AKBAR_ID)
	);

	private ModSounds() {
	}

	public static void init() {
	}
}
