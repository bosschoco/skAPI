package io.github.bosschoco.skapi;

import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;

/** config.yml 값을 담는 불변 객체. */
public record SkApiSettings(
	Duration defaultTimeout,
	Duration connectTimeout,
	boolean followRedirects,
	String userAgent,
	boolean warnSyncOnMainThread,
	Duration maxMainThreadTimeout,
	boolean debug
) {

	public static SkApiSettings from(FileConfiguration config) {
		return new SkApiSettings(
			seconds(config.getDouble("http.default-timeout", 15)),
			seconds(config.getDouble("http.connect-timeout", 10)),
			config.getBoolean("http.follow-redirects", true),
			config.getString("http.user-agent", "skAPI"),
			config.getBoolean("sync.warn-on-main-thread", true),
			seconds(config.getDouble("sync.max-main-thread-timeout", 5)),
			config.getBoolean("debug", false)
		);
	}

	private static Duration seconds(double value) {
		// 0 이하나 이상한 값이 들어와도 최소 1초는 보장한다
		long millis = Math.max(1000L, Math.round(value * 1000));
		return Duration.ofMillis(millis);
	}
}
