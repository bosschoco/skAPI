package io.github.bosschoco.skapi.http;

import com.google.gson.JsonElement;
import io.github.bosschoco.skapi.json.Json;
import org.jspecify.annotations.Nullable;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * API 응답. 생성된 뒤에는 바뀌지 않는다.
 * <p>
 * 네트워크 오류(연결 실패, 타임아웃 등)도 예외 대신 {@link #error()} 가 채워진 응답으로 돌려준다.
 * 그래서 스크립트는 항상 응답 객체 하나만 확인하면 된다.
 */
public final class ApiResponse {

	private final ApiRequest request;
	private final int status;
	private final String body;
	private final Map<String, List<String>> headers;
	private final String url;
	private final long durationMillis;
	private final @Nullable String error;

	private @Nullable JsonElement json;
	private boolean jsonParsed;

	private ApiResponse(ApiRequest request, int status, String body, Map<String, List<String>> headers,
						String url, long durationMillis, @Nullable String error) {
		this.request = request;
		this.status = status;
		this.body = body;
		this.headers = headers;
		this.url = url;
		this.durationMillis = durationMillis;
		this.error = error;
	}

	static ApiResponse of(ApiRequest request, HttpResponse<String> response, long durationMillis) {
		Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		response.headers().map().forEach((name, values) -> {
			if (!name.startsWith(":")) // HTTP/2 의사 헤더 제외
				headers.put(name, List.copyOf(values));
		});
		String body = response.body() == null ? "" : response.body();
		return new ApiResponse(request, response.statusCode(), body, Collections.unmodifiableMap(headers),
			response.uri().toString(), durationMillis, null);
	}

	static ApiResponse failure(ApiRequest request, String error, long durationMillis) {
		return new ApiResponse(request, 0, "", Map.of(), request.resolvedUrl(), durationMillis, error);
	}

	/** 이 응답을 만든 요청(전송 시점의 복사본). {@code api data} 도 여기서 꺼낸다. */
	public ApiRequest request() {
		return request;
	}

	/** HTTP 상태 코드. 네트워크 오류로 응답을 못 받았으면 0. */
	public int status() {
		return status;
	}

	public String body() {
		return body;
	}

	public String url() {
		return url;
	}

	public long durationMillis() {
		return durationMillis;
	}

	/** 네트워크 오류 메시지. 서버가 응답했다면(4xx, 5xx 포함) null. */
	public @Nullable String error() {
		return error;
	}

	/** 오류 없이 2xx 응답을 받았는지. */
	public boolean isSuccess() {
		return error == null && status >= 200 && status < 300;
	}

	public @Nullable String header(String name) {
		List<String> values = headers.get(name);
		if (values == null || values.isEmpty())
			return null;
		return String.join(", ", values);
	}

	public Set<String> headerNames() {
		return headers.keySet();
	}

	/** 본문을 JSON 으로 해석한 결과 (한 번만 파싱하고 저장해둔다). JSON 이 아니면 null. */
	public synchronized @Nullable JsonElement json() {
		if (!jsonParsed) {
			json = Json.parse(body);
			jsonParsed = true;
		}
		return json;
	}

	@Override
	public String toString() {
		if (error != null)
			return "api response (오류: " + error + ")";
		return "api response " + status;
	}
}
