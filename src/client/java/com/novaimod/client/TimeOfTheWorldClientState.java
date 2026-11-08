package com.novaimod.client;

import com.novaimod.world.TimeOfTheWorldManager;

import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Клиентское высокоточное (60–240 FPS) состояние «Made in Heaven: Time of the World»:
 * вычисляет плавное субмиллисекундное время суток на каждом кадре рендера без малейших рывков,
 * а также управляет бело-золотой вспышкой Большого Взрыва в момент Перезапуска Вселенной!
 */
public final class TimeOfTheWorldClientState {
	private static final long REBIRTH_FADE_MS = 2800L;

	private static boolean active = false;
	private static long initialTimeOfDay = 0L;
	private static int totalTicks = TimeOfTheWorldManager.TOTAL_ACCEL_TICKS;
	private static double anchorElapsedTicks = 0.0D;
	private static long anchorTimeMs = 0L;
	private static double lastMonotonicTicks = 0.0D;

	private static long rebirthFlashStartMs = -100_000L;
	private static Long pendingRestoredTime = null;

	private TimeOfTheWorldClientState() {
	}

	public static void onSyncPacket(
			boolean isActive,
			long startTimeOfDay,
			int serverElapsedTicks,
			int serverTotalTicks,
			boolean singularityFlash
	) {
		long now = Util.getMeasuringTimeMs();
		initialTimeOfDay = startTimeOfDay;
		totalTicks = Math.max(40, serverTotalTicks);

		if (isActive) {
			if (!active || serverElapsedTicks <= 2) {
				lastMonotonicTicks = serverElapsedTicks;
			}
			active = true;
			anchorElapsedTicks = serverElapsedTicks;
			anchorTimeMs = now;
			pendingRestoredTime = null;
		} else {
			active = false;
			lastMonotonicTicks = 0.0D;
			pendingRestoredTime = startTimeOfDay;
			if (singularityFlash) {
				rebirthFlashStartMs = now;
			}
		}
	}

	public static boolean isAccelerating() {
		return active;
	}

	public static Long pollPendingRestoredTime() {
		Long val = pendingRestoredTime;
		pendingRestoredTime = null;
		return val;
	}

	public static double getSmoothElapsedTicks() {
		if (!active) {
			return 0.0D;
		}
		long now = Util.getMeasuringTimeMs();
		double dtTicks = (now - anchorTimeMs) / 50.0D;
		double raw = MathHelper.clamp(anchorElapsedTicks + dtTicks, 0.0D, totalTicks);
		// Плавная монотонная подстройка без скачков назад
		if (raw > lastMonotonicTicks) {
			lastMonotonicTicks = raw;
		} else {
			lastMonotonicTicks = MathHelper.lerp(0.08D, lastMonotonicTicks, raw);
		}
		return lastMonotonicTicks;
	}

	public static long computeSmoothTimeOfDay() {
		double exactTicks = getSmoothElapsedTicks();
		return TimeOfTheWorldManager.computeVirtualTimeOfDay(initialTimeOfDay, exactTicks, totalTicks);
	}

	public static float getNormalizedProgress() {
		if (!active) {
			return 0.0F;
		}
		return MathHelper.clamp((float) (getSmoothElapsedTicks() / (double) totalTicks), 0.0F, 1.0F);
	}

	public static double getCurrentSpeedMultiplier() {
		if (!active) {
			return 1.0D;
		}
		return TimeOfTheWorldManager.computeSpeedMultiplier(getSmoothElapsedTicks(), totalTicks);
	}

	/**
	 * Возвращает прозрачность (0..1) бело-золотого свечения Сингулярности / Большого Взрыва:
	 * плавно разгорается на последних 1.6 секундах перед перезапуском и мягко растворяется 2.8 секунды после!
	 */
	public static float getSingularityFlashAlpha() {
		if (active) {
			float prog = getNormalizedProgress();
			if (prog < 0.91F) {
				return 0.0F;
			}
			float t = (prog - 0.91F) / 0.09F;
			return MathHelper.clamp(t * t * 0.96F, 0.0F, 0.96F);
		}
		long elapsed = Util.getMeasuringTimeMs() - rebirthFlashStartMs;
		if (elapsed < 0L || elapsed >= REBIRTH_FADE_MS) {
			return 0.0F;
		}
		if (elapsed < 260L) {
			return 1.0F;
		}
		float fade = 1.0F - (float) (elapsed - 260L) / (float) (REBIRTH_FADE_MS - 260L);
		return MathHelper.clamp(fade * fade, 0.0F, 1.0F);
	}

	public static boolean isInPostResetBannerWindow() {
		long elapsed = Util.getMeasuringTimeMs() - rebirthFlashStartMs;
		return !active && elapsed >= 150L && elapsed <= REBIRTH_FADE_MS + 900L;
	}

	public static void reset() {
		active = false;
		lastMonotonicTicks = 0.0D;
		pendingRestoredTime = null;
	}
}
