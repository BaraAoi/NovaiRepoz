package com.novaimod.client;

public final class ClientAccountData {
	private static long balance = 0L;
	private static long debt = 0L;
	private static int sizoSecondsLeft = 0;

	// Мимолётный силуэт подкрадывающегося моба на периферии зрения
	private static long shadowCueStartMs = 0L;
	private static boolean shadowCueLeftSide = true;
	private static float shadowCueIntensity = 0.7F;

	private ClientAccountData() {
	}

	public static void update(long newBalance, long newDebt, int newSizoSeconds) {
		balance = newBalance;
		debt = newDebt;
		sizoSecondsLeft = newSizoSeconds;
	}

	public static void triggerShadowCue(boolean leftSide, float intensity) {
		shadowCueStartMs = System.currentTimeMillis();
		shadowCueLeftSide = leftSide;
		shadowCueIntensity = Math.clamp(intensity, 0.25F, 1.0F);
	}

	public static long getShadowCueStartMs() {
		return shadowCueStartMs;
	}

	public static boolean isShadowCueLeftSide() {
		return shadowCueLeftSide;
	}

	public static float getShadowCueIntensity() {
		return shadowCueIntensity;
	}

	public static long getBalance() {
		return balance;
	}

	public static long getDebt() {
		return debt;
	}

	public static int getSizoSecondsLeft() {
		return sizoSecondsLeft;
	}
}
