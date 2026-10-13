package io.github.bosschoco.skapi.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.bosschoco.skapi.SkApiSettings;
import io.github.bosschoco.skapi.json.Json;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 로컬 테스트 HTTP 서버를 띄워 실제로 통신해본다. */
class HttpServiceTest {

	private static HttpServer server;
	private static String base;

	private static final SkApiSettings SETTINGS = new SkApiSettings(
		Duration.ofSeconds(5), Duration.ofSeconds(2), true, "skAPI-test", true, Duration.ofSeconds(1), false);

	@BeforeAll
	static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.createContext("/echo", HttpServiceTest::echo);
		server.createContext("/slow", exchange -> {
			sleep(700);
			respond(exchange, 200, "{\"slow\":true}");
		});
		server.createContext("/missing", exchange -> respond(exchange, 404, "{\"error\":\"not found\"}"));
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	private static void echo(HttpExchange exchange) throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("method", exchange.getRequestMethod());
		json.addProperty("query", exchange.getRequestURI().getRawQuery());
		json.addProperty("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		json.addProperty("contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
		json.addProperty("userAgent", exchange.getRequestHeaders().getFirst("User-Agent"));
		json.addProperty("apiKey", exchange.getRequestHeaders().getFirst("X-Api-Key"));
		exchange.getResponseHeaders().add("X-Test", "yes");
		respond(exchange, 200, json.toString());
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static HttpService service(boolean mainThread) {
		return new HttpService(Logger.getLogger("skAPI-test"), SETTINGS, () -> mainThread);
	}

	@Test
	void asyncPostSendsJsonBodyHeadersAndQuery() throws Exception {
		HttpService http = service(false);
		ApiRequest request = new ApiRequest("POST", base + "/echo");
		request.setQuery("q", "한글 검색");
		request.setHeader("X-Api-Key", "secret");
		request.setHeader("Content-Length", "999"); // 자동 관리 헤더 → 무시되어야 함 (예외 X)
		request.setBody("{\"text\":\"안녕\"}");

		ApiResponse response = http.send(request).get(5, TimeUnit.SECONDS);

		assertTrue(response.isSuccess(), () -> "error=" + response.error());
		assertEquals(200, response.status());
		assertEquals("POST", Json.toSkript(Json.select(response.json(), "method")));
		assertEquals("{\"text\":\"안녕\"}", Json.toSkript(Json.select(response.json(), "body")));
		assertEquals("application/json; charset=utf-8", Json.toSkript(Json.select(response.json(), "contentType")));
		assertEquals("skAPI-test", Json.toSkript(Json.select(response.json(), "userAgent")));
		assertEquals("secret", Json.toSkript(Json.select(response.json(), "apiKey")));
		assertEquals("q=%ED%95%9C%EA%B8%80+%EA%B2%80%EC%83%89", Json.toSkript(Json.select(response.json(), "query")));
		assertEquals("yes", response.header("x-test"));
		http.shutdown();
	}

	@Test
	void serverErrorStatusIsNotANetworkError() throws Exception {
		HttpService http = service(false);
		ApiResponse response = http.send(new ApiRequest("GET", base + "/missing")).get(5, TimeUnit.SECONDS);
		assertEquals(404, response.status());
		assertNull(response.error());
		assertFalse(response.isSuccess());
		assertEquals("not found", Json.toSkript(Json.select(response.json(), "error")));
		http.shutdown();
	}

	@Test
	void failuresBecomeResponsesInsteadOfExceptions() throws Exception {
		HttpService http = service(false);

		ApiRequest slow = new ApiRequest("GET", base + "/slow");
		slow.setTimeout(Duration.ofMillis(200));
		ApiResponse timedOut = http.send(slow).get(5, TimeUnit.SECONDS);
		assertEquals(0, timedOut.status());
		assertEquals("응답 시간 초과 (timeout)", timedOut.error());

		ApiResponse refused = http.send(new ApiRequest("GET", "http://127.0.0.1:1/")).get(5, TimeUnit.SECONDS);
		assertNotNull(refused.error());
		assertTrue(refused.error().startsWith("연결 실패"), refused.error());

		ApiResponse badUrl = http.send(new ApiRequest("GET", "그냥 글자")).get(5, TimeUnit.SECONDS);
		assertNotNull(badUrl.error());
		assertTrue(badUrl.error().contains("URL"), badUrl.error());

		ApiResponse badScheme = http.send(new ApiRequest("GET", "ftp://example.com")).get(5, TimeUnit.SECONDS);
		assertTrue(badScheme.error().contains("http://"), badScheme.error());
		http.shutdown();
	}

	@Test
	void asyncRequestsRunInParallel() throws Exception {
		HttpService http = service(false);
		long start = System.nanoTime();
		List<CompletableFuture<ApiResponse>> futures = new ArrayList<>();
		for (int i = 0; i < 30; i++)
			futures.add(http.send(new ApiRequest("GET", base + "/slow")));
		for (CompletableFuture<ApiResponse> future : futures)
			assertEquals(200, future.get(10, TimeUnit.SECONDS).status());
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;
		// 하나당 0.7초 → 순차였다면 21초. 병렬이므로 몇 초 안에 끝나야 한다.
		assertTrue(elapsedMs < 5_000, "30개 요청에 " + elapsedMs + "ms 걸림");
		http.shutdown();
	}

	@Test
	void blockingRequestReturnsResponse() {
		HttpService http = service(false);
		ApiResponse response = http.sendBlocking(new ApiRequest("DELETE", base + "/echo"));
		assertEquals(200, response.status());
		assertEquals("DELETE", Json.toSkript(Json.select(response.json(), "method")));
		http.shutdown();
	}

	@Test
	void blockingRequestOnMainThreadIsCappedByMaxMainThreadTimeout() {
		HttpService http = service(true); // 메인 스레드라고 가정, 상한 1초
		ApiRequest request = new ApiRequest("GET", base + "/slow");
		request.setTimeout(Duration.ofMillis(300)); // 상한보다 짧으면 그대로
		assertEquals("응답 시간 초과 (timeout)", http.sendBlocking(request).error());

		ApiRequest generous = new ApiRequest("GET", base + "/slow");
		generous.setTimeout(Duration.ofSeconds(30)); // 상한(1초)으로 줄어들지만 0.7초 응답이라 성공
		assertEquals(200, http.sendBlocking(generous).status());
		http.shutdown();
	}
}
