package com.novaimod.client;

public final class ClientAccountData {
	private static long balance = 0L;
	private static long debt = 0L;
	private static int sizoSecondsLeft = 0;

	private ClientAccountData() {
	}

	public static void update(long newBalance, long newDebt, int newSizoSeconds) {
		balance = newBalance;
		debt = newDebt;
		sizoSecondsLeft = newSizoSeconds;
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
