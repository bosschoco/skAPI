package io.github.bosschoco.skapi.skript;

import io.github.bosschoco.skapi.http.ApiResponse;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 트리거 실행(이벤트)마다 "마지막으로 받은 응답"을 기억한다. → {@code last api response}
 * 이벤트 객체가 사라지면 같이 정리되도록 WeakHashMap 을 쓴다.
 */
public final class LastResponses {

	private static final Map<Event, ApiResponse> LAST = Collections.synchronizedMap(new WeakHashMap<>());

	private LastResponses() {
	}

	public static void set(Event event, ApiResponse response) {
		LAST.put(event, response);
	}

	public static @Nullable ApiResponse get(Event event) {
		return LAST.get(event);
	}
}
