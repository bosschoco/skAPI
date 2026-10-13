package io.github.bosschoco.skapi.http;

import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 스크립트에서 만들고 수정하는 API 요청.
 * <p>
 * 이 객체는 가변(mutable)이다. 실제로 보낼 때는 {@link #snapshot()} 으로 복사본을 만들어 보내므로,
 * 전송 이후에 스크립트가 원본을 바꿔도 이미 나간 요청에는 영향이 없다.
 */
public final class ApiRequest {

	private String method;
	private String url;
	private final Map<String, String> headers;
	private final Map<String, String> query;
	private @Nullable String body;
	private @Nullable Duration timeout;
	/** 스크립트가 요청에 붙여두는 임의의 값. 응답에서도 그대로 꺼낼 수 있다. (키는 대소문자 무시) */
	private final Map<String, Object> data;

	public ApiRequest(String method, String url) {
		this.method = normalizeMethod(method);
		this.url = url.trim();
		this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		this.query = new LinkedHashMap<>();
		this.data = new ConcurrentHashMap<>();
	}

	private ApiRequest(ApiRequest other) {
		this.method = other.method;
		this.url = other.url;
		this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		this.headers.putAll(other.headers);
		this.query = new LinkedHashMap<>(other.query);
		this.body = other.body;
		this.timeout = other.timeout;
		this.data = new ConcurrentHashMap<>(other.data);
	}

	/** 전송용 복사본. */
	public ApiRequest snapshot() {
		return new ApiRequest(this);
	}

	// ── 메서드 / URL ──────────────────────────────

	public String method() {
		return method;
	}

	public void setMethod(String method) {
		this.method = normalizeMethod(method);
	}

	public String url() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url.trim();
	}

	/** 쿼리 파라미터까지 붙인 최종 URL. */
	public String resolvedUrl() {
		if (query.isEmpty())
			return url;
		StringBuilder builder = new StringBuilder(url);
		if (url.indexOf('?') < 0) {
			builder.append('?');
		} else if (!url.endsWith("?") && !url.endsWith("&")) {
			builder.append('&');
		}
		boolean first = true;
		for (Map.Entry<String, String> entry : query.entrySet()) {
			if (!first)
				builder.append('&');
			first = false;
			builder.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
		}
		return builder.toString();
	}

	// ── 헤더 ──────────────────────────────────────

	public @Nullable String header(String name) {
		return headers.get(name);
	}

	/** value 가 null 이면 헤더를 지운다. */
	public void setHeader(String name, @Nullable String value) {
		if (value == null) {
			headers.remove(name);
		} else {
			headers.put(name.trim(), value);
		}
	}

	public Map<String, String> headers() {
		return Collections.unmodifiableMap(headers);
	}

	// ── 쿼리 파라미터 ─────────────────────────────

	public @Nullable String query(String name) {
		return query.get(name);
	}

	public void setQuery(String name, @Nullable String value) {
		if (value == null) {
			query.remove(name);
		} else {
			query.put(name, value);
		}
	}

	// ── 본문 / 타임아웃 ───────────────────────────

	public @Nullable String body() {
		return body;
	}

	public void setBody(@Nullable String body) {
		this.body = body;
	}

	public @Nullable Duration timeout() {
		return timeout;
	}

	public void setTimeout(@Nullable Duration timeout) {
		this.timeout = timeout;
	}

	// ── 사용자 데이터 ─────────────────────────────

	public @Nullable Object data(String key) {
		return data.get(key.toLowerCase(Locale.ROOT));
	}

	public void setData(String key, @Nullable Object value) {
		String normalized = key.toLowerCase(Locale.ROOT);
		if (value == null) {
			data.remove(normalized);
		} else {
			data.put(normalized, value);
		}
	}

	@Override
	public String toString() {
		return "api request " + method + " " + url;
	}

	private static String normalizeMethod(String method) {
		String trimmed = method.trim().toUpperCase(Locale.ROOT);
		return trimmed.isEmpty() ? "GET" : trimmed;
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
