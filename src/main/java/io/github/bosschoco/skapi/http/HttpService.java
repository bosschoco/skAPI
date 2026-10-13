package io.github.bosschoco.skapi.http;

import io.github.bosschoco.skapi.SkApiSettings;
import org.bukkit.Bukkit;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * 실제 HTTP 통신 담당.
 * <ul>
 *     <li>{@link #send(ApiRequest)} — 비동기. 메인 스레드를 막지 않는다. (권장)</li>
 *     <li>{@link #sendBlocking(ApiRequest)} — 동기. 응답이 올 때까지 호출한 스레드를 막는다.</li>
 * </ul>
 * 두 방식 모두 예외를 던지지 않고, 실패는 {@link ApiResponse#error()} 로 돌려준다.
 */
public final class HttpService {

	/** Java HttpClient 가 직접 관리해서 사용자가 설정하면 예외가 나는 헤더들. */
	private static final Set<String> RESTRICTED_HEADERS = Set.of("connection", "content-length", "expect", "host", "upgrade");
	private static final long SYNC_WARNING_INTERVAL_MS = 60_000;

	private final Logger logger;
	private final BooleanSupplier isMainThread;
	/** 가상 스레드: 요청이 많아도 OS 스레드를 거의 쓰지 않는다. */
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final AtomicLong lastSyncWarning = new AtomicLong();

	private volatile HttpClient client;
	private volatile SkApiSettings settings;

	public HttpService(Logger logger, SkApiSettings settings) {
		this(logger, settings, Bukkit::isPrimaryThread);
	}

	/** 테스트용: 서버 없이도 "메인 스레드 여부"를 정할 수 있게 한다. */
	HttpService(Logger logger, SkApiSettings settings, BooleanSupplier isMainThread) {
		this.logger = logger;
		this.isMainThread = isMainThread;
		this.settings = settings;
		this.client = createClient(settings);
	}

	/** 설정 변경 시 클라이언트를 교체한다. 이미 진행 중인 요청은 기존 클라이언트로 끝까지 처리된다. */
	public void configure(SkApiSettings settings) {
		HttpClient old = this.client;
		this.settings = settings;
		this.client = createClient(settings);
		old.shutdown();
	}

	public void shutdown() {
		client.shutdownNow();
		executor.shutdownNow();
	}

	private HttpClient createClient(SkApiSettings settings) {
		return HttpClient.newBuilder()
			.connectTimeout(settings.connectTimeout())
			.followRedirects(settings.followRedirects() ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
			.executor(executor)
			.build();
	}

	// ── 비동기 ────────────────────────────────────

	/**
	 * 비동기 전송. 반환된 future 는 절대 예외로 끝나지 않는다.
	 * 완료 콜백은 HTTP 스레드에서 실행되므로, Skript 코드는 반드시 메인 스레드로 넘겨서 실행해야 한다.
	 */
	public CompletableFuture<ApiResponse> send(ApiRequest request) {
		long start = System.nanoTime();
		SkApiSettings current = settings;
		HttpRequest httpRequest;
		try {
			httpRequest = build(request, timeoutOf(request, current), current);
		} catch (RuntimeException ex) {
			return CompletableFuture.completedFuture(fail(request, ex, start));
		}
		debug(current, "→ " + request.method() + " " + httpRequest.uri() + " (비동기)");
		return client.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
			.handle((response, ex) -> {
				ApiResponse result = ex == null
					? ApiResponse.of(request, response, elapsed(start))
					: fail(request, ex, start);
				debug(current, "← " + describe(result));
				return result;
			});
	}

	// ── 동기 ──────────────────────────────────────

	/**
	 * 동기 전송. 응답이 올 때까지 현재 스레드가 멈춘다.
	 * 메인 스레드에서 호출되면 타임아웃을 {@code sync.max-main-thread-timeout} 이하로 줄이고 경고를 남긴다.
	 */
	public ApiResponse sendBlocking(ApiRequest request) {
		long start = System.nanoTime();
		SkApiSettings current = settings;
		Duration timeout = timeoutOf(request, current);
		if (isMainThread.getAsBoolean()) {
			if (timeout.compareTo(current.maxMainThreadTimeout()) > 0)
				timeout = current.maxMainThreadTimeout();
			warnSyncOnMainThread(request, current);
		}
		try {
			HttpRequest httpRequest = build(request, timeout, current);
			debug(current, "→ " + request.method() + " " + httpRequest.uri() + " (동기)");
			HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			ApiResponse result = ApiResponse.of(request, response, elapsed(start));
			debug(current, "← " + describe(result));
			return result;
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return fail(request, ex, start);
		} catch (IOException | RuntimeException ex) {
			ApiResponse result = fail(request, ex, start);
			debug(current, "← " + describe(result));
			return result;
		}
	}

	// ── 내부 ──────────────────────────────────────

	private HttpRequest build(ApiRequest request, Duration timeout, SkApiSettings settings) {
		URI uri;
		try {
			uri = URI.create(request.resolvedUrl());
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("잘못된 URL 입니다: " + request.resolvedUrl()
				+ " (공백이나 특수문자는 'url encoded' 또는 'api query' 를 사용하세요)", ex);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https"))
			throw new IllegalArgumentException("URL 은 http:// 또는 https:// 로 시작해야 합니다: " + request.resolvedUrl());

		HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);
		if (scheme.equals("http")) // 평문 HTTP 에서 h2c 업그레이드를 시도하면 일부 서버가 오작동한다
			builder.version(HttpClient.Version.HTTP_1_1);

		boolean hasUserAgent = false;
		boolean hasContentType = false;
		for (Map.Entry<String, String> header : request.headers().entrySet()) {
			String name = header.getKey().toLowerCase(Locale.ROOT);
			if (RESTRICTED_HEADERS.contains(name)) {
				debug(settings, "'" + header.getKey() + "' 헤더는 자동으로 관리되므로 무시합니다.");
				continue;
			}
			hasUserAgent |= name.equals("user-agent");
			hasContentType |= name.equals("content-type");
			builder.header(header.getKey(), header.getValue());
		}
		if (!hasUserAgent)
			builder.header("User-Agent", settings.userAgent());

		String body = request.body();
		if (body != null && !hasContentType)
			builder.header("Content-Type", guessContentType(body));
		HttpRequest.BodyPublisher publisher = body == null
			? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
		builder.method(request.method(), publisher);
		return builder.build();
	}

	private static Duration timeoutOf(ApiRequest request, SkApiSettings settings) {
		Duration timeout = request.timeout();
		return timeout == null || timeout.isZero() || timeout.isNegative() ? settings.defaultTimeout() : timeout;
	}

	private static String guessContentType(String body) {
		String trimmed = body.stripLeading();
		if (trimmed.startsWith("{") || trimmed.startsWith("["))
			return "application/json; charset=utf-8";
		if (trimmed.contains("=") && !trimmed.contains(" ") && !trimmed.contains("\n"))
			return "application/x-www-form-urlencoded; charset=utf-8";
		return "text/plain; charset=utf-8";
	}

	private static ApiResponse fail(ApiRequest request, Throwable ex, long start) {
		return ApiResponse.failure(request, describe(ex), elapsed(start));
	}

	/** 스크립트 작성자가 이해하기 쉬운 오류 메시지로 바꾼다. */
	static String describe(Throwable ex) {
		Throwable cause = ex;
		while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null)
			cause = cause.getCause();
		String message = cause.getMessage() == null ? "" : cause.getMessage();
		if (cause instanceof HttpConnectTimeoutException)
			return "연결 시간 초과 (connect timeout)";
		if (cause instanceof HttpTimeoutException)
			return "응답 시간 초과 (timeout)";
		if (cause instanceof UnknownHostException)
			return "알 수 없는 호스트: " + message;
		if (cause instanceof ConnectException)
			return "연결 실패" + (message.isEmpty() ? "" : ": " + message);
		if (cause instanceof InterruptedException)
			return "요청이 중단되었습니다";
		if (cause instanceof IllegalArgumentException && !message.isEmpty())
			return message;
		return cause.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
	}

	private static String describe(ApiResponse response) {
		String head = response.error() != null ? "오류(" + response.error() + ")" : String.valueOf(response.status());
		return head + " " + response.url() + " [" + response.durationMillis() + "ms]";
	}

	private static long elapsed(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000L;
	}

	private void warnSyncOnMainThread(ApiRequest request, SkApiSettings settings) {
		if (!settings.warnSyncOnMainThread())
			return;
		long now = System.currentTimeMillis();
		long last = lastSyncWarning.get();
		if (now - last < SYNC_WARNING_INTERVAL_MS || !lastSyncWarning.compareAndSet(last, now))
			return;
		logger.warning("메인 스레드에서 동기(sync) 요청을 실행했습니다: " + request.method() + " " + request.url()
			+ " — 응답을 기다리는 동안 서버가 멈춥니다. 가능하면 비동기 방식을 사용하세요. (이 경고는 1분에 한 번만 표시됩니다)");
	}

	private void debug(SkApiSettings settings, @Nullable String message) {
		if (settings.debug() && message != null)
			logger.info("[debug] " + message);
	}
}
