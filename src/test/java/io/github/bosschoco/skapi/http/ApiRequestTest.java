package io.github.bosschoco.skapi.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiRequestTest {

	@Test
	void appendsEncodedQueryParameters() {
		ApiRequest request = new ApiRequest("get", "https://api.example.com/search");
		request.setQuery("q", "마인크래프트 서버");
		request.setQuery("page", "2");
		assertEquals("https://api.example.com/search?q=%EB%A7%88%EC%9D%B8%ED%81%AC%EB%9E%98%ED%94%84%ED%8A%B8+%EC%84%9C%EB%B2%84&page=2",
			request.resolvedUrl());

		ApiRequest withExisting = new ApiRequest("GET", "https://api.example.com/search?lang=ko");
		withExisting.setQuery("q", "a&b");
		assertEquals("https://api.example.com/search?lang=ko&q=a%26b", withExisting.resolvedUrl());
	}

	@Test
	void snapshotIsIndependentFromOriginal() {
		ApiRequest original = new ApiRequest("post", "https://api.example.com");
		original.setHeader("Authorization", "Bearer 1");
		original.setBody("{}");
		original.setData("player", "steve");
		original.setTimeout(Duration.ofSeconds(30));

		ApiRequest snapshot = original.snapshot();
		original.setHeader("Authorization", "Bearer 2");
		original.setBody("changed");
		original.setData("player", "alex");
		original.setMethod("delete");

		assertEquals("POST", snapshot.method());
		assertEquals("Bearer 1", snapshot.header("authorization"));
		assertEquals("{}", snapshot.body());
		assertEquals("steve", snapshot.data("PLAYER"));
		assertEquals(Duration.ofSeconds(30), snapshot.timeout());
	}

	@Test
	void headersAreCaseInsensitiveAndRemovable() {
		ApiRequest request = new ApiRequest("GET", "https://x");
		request.setHeader("X-Api-Key", "k");
		assertEquals("k", request.header("x-api-key"));
		request.setHeader("x-api-key", null);
		assertNull(request.header("X-Api-Key"));
	}

	@Test
	void emptyMethodFallsBackToGet() {
		assertEquals("GET", new ApiRequest("  ", "https://x").method());
		assertEquals("PATCH", new ApiRequest(" patch ", "https://x").method());
	}
}
