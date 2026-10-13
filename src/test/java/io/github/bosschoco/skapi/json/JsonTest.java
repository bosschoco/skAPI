package io.github.bosschoco.skapi.json;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonTest {

	private static final String AI_RESPONSE = """
		{"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"안녕하세요!"}}],
		 "usage":{"total_tokens":42,"cost":0.5,"exact":1.0},"ok":true,"list":[1,2,3],"weird.key":"w"}
		""";

	@Test
	void selectsByPath() {
		JsonElement root = Json.parse(AI_RESPONSE);
		assertEquals("안녕하세요!", Json.toSkript(Json.select(root, "choices[0].message.content")));
		assertEquals("안녕하세요!", Json.toSkript(Json.select(root, "choices.0.message.content")));
		assertEquals("안녕하세요!", Json.toSkript(Json.select(root, "$.choices[0].message.content")));
		assertEquals(3L, Json.toSkript(Json.select(root, "list[-1]")));
		assertEquals("w", Json.toSkript(Json.select(root, "['weird.key']")));
		assertNull(Json.select(root, "choices[5].message"));
		assertNull(Json.select(root, "nope.deeper"));
		assertEquals(root, Json.select(root, ""));
	}

	@Test
	void filtersArrayElementsByField() {
		// AI 응답처럼 텍스트 블록이 첫 번째가 아닐 수 있는 경우
		JsonElement root = Json.parse("""
			{"content":[{"type":"thinking","thinking":""},{"type":"text","text":"답변"}],
			 "rows":[{"id":1,"name":"a"},{"id":2,"name":"b"}]}
			""");
		assertEquals("답변", Json.toSkript(Json.select(root, "content[type=text].text")));
		assertEquals("답변", Json.toSkript(Json.select(root, "content[type=\"text\"].text")));
		assertEquals("b", Json.toSkript(Json.select(root, "rows[id=2].name")));
		assertNull(Json.select(root, "rows[id=9].name"));
		assertNull(Json.select(root, "content[0][type=text]")); // 배열이 아닌 곳의 필터
	}

	@Test
	void convertsNumbersToSkriptTypes() {
		JsonElement root = Json.parse(AI_RESPONSE);
		assertEquals(42L, Json.toSkript(Json.select(root, "usage.total_tokens")));
		assertEquals(0.5, Json.toSkript(Json.select(root, "usage.cost")));
		assertEquals(1L, Json.toSkript(Json.select(root, "usage.exact")));
		assertEquals(true, Json.toSkript(Json.select(root, "ok")));
		assertEquals(1.0E30, Json.toSkript(Json.parse("1e30")));
		// 객체는 JSON 문자열로
		assertEquals("{\"role\":\"assistant\",\"content\":\"안녕하세요!\"}", Json.toSkript(Json.select(root, "choices[0].message")));
	}

	@Test
	void listsArrayElementsAndObjectValues() {
		JsonElement root = Json.parse(AI_RESPONSE);
		assertEquals(List.of(1L, 2L, 3L), Json.toSkriptList(Json.select(root, "list")));
		assertEquals(List.of(42L, 0.5, 1L), Json.toSkriptList(Json.select(root, "usage")));
		assertEquals(List.of(), Json.toSkriptList(Json.select(root, "missing")));
	}

	@Test
	void invalidJsonParsesToNull() {
		assertNull(Json.parse(""));
		assertNull(Json.parse("{broken"));
		assertNull(Json.parse("Internal Server Error"));
	}

	@Test
	void buildsJsonFromSkriptListStructure() {
		// Skript 목록 변수의 내부 구조: {_body::model}, {_body::messages::1::role}, {_body::messages::1::content}
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "user");
		message.put("content", "따옴표\" 와 줄바꿈\n 포함");
		Map<String, Object> messages = new TreeMap<>();
		messages.put("1", message);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", "test-model");
		body.put("max_tokens", 100L);
		body.put("temperature", 0.7);
		body.put("messages", messages);

		assertEquals(
			"{\"model\":\"test-model\",\"max_tokens\":100,\"temperature\":0.7,\"messages\":[{\"role\":\"user\",\"content\":\"따옴표\\\" 와 줄바꿈\\n 포함\"}]}",
			Json.stringify(Json.fromSkriptMap(body), false));
	}

	@Test
	void sequentialKeysBecomeArraysOthersObjects() {
		assertEquals("[\"a\",\"b\"]", Json.stringify(Json.fromSkriptMap(new TreeMap<>(Map.of("1", "a", "2", "b"))), false));
		assertEquals("{\"1\":\"a\",\"3\":\"b\"}", Json.stringify(Json.fromSkriptMap(new TreeMap<>(Map.of("1", "a", "3", "b"))), false));
		assertEquals("{\"0\":\"a\"}", Json.stringify(Json.fromSkriptMap(Map.of("0", "a")), false));
		assertEquals("{\"01\":\"a\"}", Json.stringify(Json.fromSkriptMap(Map.of("01", "a")), false));
	}

	@Test
	void normalizesWholeDoublesToIntegers() {
		assertEquals("5", Json.stringify(Json.fromSkript(5.0), false));
		assertEquals("5.5", Json.stringify(Json.fromSkript(5.5), false));
		assertEquals("null", Json.stringify(Json.fromSkript(Double.NaN), false));
		assertEquals("[1,\"x\",true]", Json.stringify(Json.fromSkript(new Object[] {1L, "x", true}), false));
	}

	@Test
	void flattensJsonIntoListVariableEntries() {
		Map<String, Object> out = new LinkedHashMap<>();
		Json.flatten(Json.parse("{\"user\":{\"name\":\"a\",\"age\":3},\"tags\":[\"x\",\"y\"],\"none\":null}"), "_data::", out);
		Map<String, Object> expected = new LinkedHashMap<>();
		expected.put("_data::user::name", "a");
		expected.put("_data::user::age", 3L);
		expected.put("_data::tags::1", "x");
		expected.put("_data::tags::2", "y");
		assertEquals(expected, out);
	}

	@Test
	void modifiesByPathKeepingKeyCase() {
		JsonElement json = Json.modify(null, "generationConfig.maxOutputTokens", Json.fromSkript(200L), Json.Change.SET);
		json = Json.modify(json, "contents[0].parts[0].text", Json.fromSkript("{\"not\":\"json\"}"), Json.Change.SET);
		json = Json.modify(json, "contents[0].role", Json.fromSkript("user"), Json.Change.SET);
		assertEquals("{\"generationConfig\":{\"maxOutputTokens\":200},\"contents\":[{\"parts\":[{\"text\":\"{\\\"not\\\":\\\"json\\\"}\"}],\"role\":\"user\"}]}",
			Json.stringify(json, false));

		// 기존 값 덮어쓰기 / 배열 추가 / 삭제
		json = Json.modify(json, "generationConfig.maxOutputTokens", Json.fromSkript(50L), Json.Change.SET);
		json = Json.modify(json, "tags", Json.fromSkript("a"), Json.Change.ADD);
		json = Json.modify(json, "tags", Json.fromSkript("b"), Json.Change.ADD);
		json = Json.modify(json, "contents", null, Json.Change.REMOVE);
		assertEquals("{\"generationConfig\":{\"maxOutputTokens\":50},\"tags\":[\"a\",\"b\"]}", Json.stringify(json, false));

		// 필터로 찾은 요소 수정, 없는 경로 삭제는 무시
		JsonElement rows = Json.parse("{\"rows\":[{\"id\":1,\"v\":0},{\"id\":2,\"v\":0}]}");
		rows = Json.modify(rows, "rows[id=2].v", Json.fromSkript(9L), Json.Change.SET);
		rows = Json.modify(rows, "missing.deep", null, Json.Change.REMOVE);
		assertEquals("{\"rows\":[{\"id\":1,\"v\":0},{\"id\":2,\"v\":9}]}", Json.stringify(rows, false));

		// 빈 경로 = 루트 교체, 배열 인덱스 건너뛰면 null 로 채움
		assertEquals("[1,2]", Json.stringify(Json.modify(Json.parse("{}"), "", Json.fromSkript(new Object[] {1L, 2L}), Json.Change.SET), false));
		assertEquals("{\"list\":[null,null,\"x\"]}", Json.stringify(Json.modify(null, "list[2]", Json.fromSkript("x"), Json.Change.SET), false));
	}

	@Test
	void escapesForEmbeddingInJsonStrings() {
		assertEquals("a\\\"b\\\\c\\nd", Json.escape("a\"b\\c\nd"));
		assertEquals("<html>", Json.escape("<html>"));
	}
}
