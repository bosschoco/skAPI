package io.github.bosschoco.skapi.json;

import ch.njol.skript.registrations.Classes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON ↔ Skript 값 변환 도우미.
 * <ul>
 *     <li>JSON 숫자 → 정수면 Long, 아니면 Double</li>
 *     <li>JSON 객체/배열 → (값 하나로 꺼낼 때) JSON 문자열</li>
 *     <li>Skript 목록 변수 → 인덱스가 1,2,3... 이면 배열, 아니면 객체</li>
 * </ul>
 */
public final class Json {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
	private static final Gson PRETTY_GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

	private Json() {
	}

	public static @Nullable JsonElement parse(@Nullable String text) {
		if (text == null || text.isBlank())
			return null;
		try {
			return JsonParser.parseString(text);
		} catch (JsonParseException | IllegalStateException ex) {
			return null;
		}
	}

	public static String stringify(JsonElement element, boolean pretty) {
		return (pretty ? PRETTY_GSON : GSON).toJson(element);
	}

	/** 문자열을 JSON 문자열 안에 넣을 수 있게 이스케이프한다. (앞뒤 따옴표는 붙이지 않음) */
	public static String escape(String text) {
		String quoted = GSON.toJson(text);
		return quoted.substring(1, quoted.length() - 1);
	}

	// ── 경로 탐색 ─────────────────────────────────

	/** 경로의 {@code [key=value]} 조각: 배열에서 key 필드가 value 인 첫 요소. */
	private record Filter(String key, String value) {
	}

	/**
	 * {@code choices[0].message.content}, {@code data.items.2.name}, {@code [0]}, {@code items[-1]} 같은 경로로 값을 찾는다.
	 * {@code content[type=text].text} 처럼 쓰면 배열에서 조건에 맞는 첫 요소를 고른다.
	 * 빈 문자열이나 {@code $} 는 루트 자체.
	 */
	public static @Nullable JsonElement select(@Nullable JsonElement root, String path) {
		JsonElement current = root;
		for (Object segment : parsePath(path)) {
			if (current == null || current.isJsonNull())
				return null;
			if (segment instanceof Filter filter) {
				current = current.isJsonArray() ? findFirst(current.getAsJsonArray(), filter) : null;
			} else if (current.isJsonObject()) {
				current = current.getAsJsonObject().get(segment.toString());
			} else if (current.isJsonArray()) {
				Integer index = segment instanceof Integer i ? i : parseIndex(segment.toString());
				if (index == null)
					return null;
				JsonArray array = current.getAsJsonArray();
				int resolved = index < 0 ? array.size() + index : index;
				if (resolved < 0 || resolved >= array.size())
					return null;
				current = array.get(resolved);
			} else {
				return null;
			}
		}
		return current;
	}

	private static @Nullable JsonElement findFirst(JsonArray array, Filter filter) {
		int index = indexOfFirst(array, filter);
		return index < 0 ? null : array.get(index);
	}

	private static int indexOfFirst(JsonArray array, Filter filter) {
		for (int i = 0; i < array.size(); i++) {
			JsonElement element = array.get(i);
			if (!element.isJsonObject())
				continue;
			JsonElement field = element.getAsJsonObject().get(filter.key());
			if (field != null && field.isJsonPrimitive() && field.getAsString().equals(filter.value()))
				return i;
		}
		return -1;
	}

	// ── 경로로 수정 ───────────────────────────────

	public enum Change {
		/** 경로의 값을 바꾼다 (중간 객체/배열은 자동 생성) */
		SET,
		/** 경로의 배열에 값을 추가한다 (배열이 없으면 만든다) */
		ADD,
		/** 경로의 값을 지운다 */
		REMOVE
	}

	/**
	 * 경로 위치의 값을 바꾼 새 루트를 돌려준다. (root 가 객체/배열이면 그 자리에서 수정된다)
	 * 중간 경로가 없으면 다음 조각이 {@code [숫자]} 일 때는 배열, 아니면 객체를 만든다.
	 */
	public static JsonElement modify(@Nullable JsonElement root, String path, @Nullable JsonElement value, Change change) {
		JsonElement newValue = value == null ? JsonNull.INSTANCE : value;
		List<Object> segments = parsePath(path);
		if (segments.isEmpty()) {
			return switch (change) {
				case SET -> newValue;
				case REMOVE -> new JsonObject();
				case ADD -> {
					JsonArray array = root != null && root.isJsonArray() ? root.getAsJsonArray() : new JsonArray();
					array.add(newValue);
					yield array;
				}
			};
		}

		if (root == null || !(root.isJsonObject() || root.isJsonArray()))
			root = containerFor(segments.getFirst());
		JsonElement parent = root;
		for (int i = 0; i < segments.size() - 1; i++) {
			JsonElement child = child(parent, segments.get(i));
			if (child == null || !(child.isJsonObject() || child.isJsonArray())) {
				if (change == Change.REMOVE)
					return root; // 지울 대상이 없음
				child = containerFor(segments.get(i + 1));
				if (!putChild(parent, segments.get(i), child))
					return root;
			}
			parent = child;
		}

		Object last = segments.getLast();
		switch (change) {
			case SET -> putChild(parent, last, newValue);
			case REMOVE -> removeChild(parent, last);
			case ADD -> {
				JsonElement existing = child(parent, last);
				if (existing != null && existing.isJsonArray()) {
					existing.getAsJsonArray().add(newValue);
				} else {
					JsonArray array = new JsonArray();
					if (existing != null && !existing.isJsonNull())
						array.add(existing);
					array.add(newValue);
					putChild(parent, last, array);
				}
			}
		}
		return root;
	}

	private static JsonElement containerFor(Object segment) {
		return segment instanceof Integer ? new JsonArray() : new JsonObject();
	}

	private static @Nullable JsonElement child(JsonElement parent, Object segment) {
		if (parent.isJsonObject())
			return segment instanceof Filter ? null : parent.getAsJsonObject().get(segment.toString());
		if (!parent.isJsonArray())
			return null;
		JsonArray array = parent.getAsJsonArray();
		int index = resolveIndex(array, segment);
		return index >= 0 && index < array.size() ? array.get(index) : null;
	}

	/** 배열 위치. 필터면 찾은 위치, 음수 인덱스는 뒤에서부터. 해석 불가면 -1. */
	private static int resolveIndex(JsonArray array, Object segment) {
		if (segment instanceof Filter filter)
			return indexOfFirst(array, filter);
		Integer index = segment instanceof Integer i ? i : parseIndex(segment.toString());
		if (index == null)
			return -1;
		return index < 0 ? array.size() + index : index;
	}

	private static boolean putChild(JsonElement parent, Object segment, JsonElement value) {
		if (parent.isJsonObject()) {
			if (segment instanceof Filter)
				return false;
			parent.getAsJsonObject().add(segment.toString(), value);
			return true;
		}
		JsonArray array = parent.getAsJsonArray();
		int index = resolveIndex(array, segment);
		if (index < 0)
			return false;
		if (index < array.size()) {
			array.set(index, value);
			return true;
		}
		if (segment instanceof Filter)
			return false;
		while (array.size() < index) // 중간 빈 칸은 null 로 채움
			array.add(JsonNull.INSTANCE);
		array.add(value);
		return true;
	}

	private static void removeChild(JsonElement parent, Object segment) {
		if (parent.isJsonObject()) {
			if (!(segment instanceof Filter))
				parent.getAsJsonObject().remove(segment.toString());
			return;
		}
		JsonArray array = parent.getAsJsonArray();
		int index = resolveIndex(array, segment);
		if (index >= 0 && index < array.size())
			array.remove(index);
	}

	private static List<Object> parsePath(String path) {
		List<Object> segments = new ArrayList<>();
		String trimmed = path.trim();
		if (trimmed.startsWith("$"))
			trimmed = trimmed.substring(1);
		StringBuilder key = new StringBuilder();
		int i = 0;
		while (i < trimmed.length()) {
			char c = trimmed.charAt(i);
			if (c == '.') {
				flush(key, segments);
				i++;
			} else if (c == '[') {
				flush(key, segments);
				int end = trimmed.indexOf(']', i);
				if (end < 0) { // 닫는 괄호가 없으면 나머지를 키로 취급
					key.append(trimmed.substring(i));
					break;
				}
				String inside = trimmed.substring(i + 1, end).trim();
				int equals = inside.indexOf('=');
				if (isQuoted(inside)) {
					segments.add(inside.substring(1, inside.length() - 1));
				} else if (equals > 0) {
					String value = inside.substring(equals + 1).trim();
					segments.add(new Filter(inside.substring(0, equals).trim(), isQuoted(value) ? value.substring(1, value.length() - 1) : value));
				} else {
					Integer index = parseIndex(inside);
					segments.add(index != null ? index : inside);
				}
				i = end + 1;
			} else {
				key.append(c);
				i++;
			}
		}
		flush(key, segments);
		return segments;
	}

	private static boolean isQuoted(String text) {
		return text.length() >= 2
			&& (text.startsWith("\"") && text.endsWith("\"") || text.startsWith("'") && text.endsWith("'"));
	}

	private static void flush(StringBuilder key, List<Object> segments) {
		if (!key.isEmpty()) {
			segments.add(key.toString());
			key.setLength(0);
		}
	}

	private static @Nullable Integer parseIndex(String text) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	// ── JSON → Skript ─────────────────────────────

	/** JSON 값 하나를 Skript 값으로. 객체/배열은 JSON 문자열로 돌려준다. null 은 none. */
	public static @Nullable Object toSkript(@Nullable JsonElement element) {
		if (element == null || element.isJsonNull())
			return null;
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isBoolean())
				return primitive.getAsBoolean();
			if (primitive.isNumber())
				return toNumber(primitive.getAsBigDecimal());
			return primitive.getAsString();
		}
		return GSON.toJson(element);
	}

	/** 배열이면 각 요소를, 객체면 각 값을, 그 외에는 자기 자신 하나를 Skript 값으로. */
	public static List<Object> toSkriptList(@Nullable JsonElement element) {
		List<Object> values = new ArrayList<>();
		if (element == null || element.isJsonNull())
			return values;
		Iterable<JsonElement> children;
		if (element.isJsonArray()) {
			children = element.getAsJsonArray();
		} else if (element.isJsonObject()) {
			children = element.getAsJsonObject().asMap().values();
		} else {
			children = List.of(element);
		}
		for (JsonElement child : children) {
			Object value = toSkript(child);
			if (value != null)
				values.add(value);
		}
		return values;
	}

	private static Number toNumber(BigDecimal decimal) {
		try {
			BigDecimal stripped = decimal.stripTrailingZeros();
			if (stripped.scale() <= 0)
				return stripped.longValueExact();
		} catch (ArithmeticException ignored) {
			// long 범위를 넘으면 double 로
		}
		return decimal.doubleValue();
	}

	/**
	 * JSON 을 Skript 목록 변수에 펼쳐 넣을 (이름, 값) 목록을 만든다.
	 * 예) {"user":{"name":"a"},"tags":["x","y"]} → user::name=a, tags::1=x, tags::2=y
	 */
	public static void flatten(JsonElement element, String prefix, Map<String, Object> out) {
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
				flattenChild(entry.getValue(), prefix + entry.getKey(), out);
		} else if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < array.size(); i++)
				flattenChild(array.get(i), prefix + (i + 1), out);
		} else {
			flattenChild(element, prefix + "1", out);
		}
	}

	private static void flattenChild(JsonElement value, String name, Map<String, Object> out) {
		if (value.isJsonObject() || value.isJsonArray()) {
			flatten(value, name + "::", out);
			return;
		}
		Object converted = toSkript(value);
		if (converted != null)
			out.put(name, converted);
	}

	// ── Skript → JSON ─────────────────────────────

	public static JsonElement fromSkript(@Nullable Object value) {
		if (value == null)
			return JsonNull.INSTANCE;
		if (value instanceof JsonElement element)
			return element;
		if (value instanceof Boolean bool)
			return new JsonPrimitive(bool);
		if (value instanceof Number number)
			return fromNumber(number);
		if (value instanceof CharSequence text)
			return new JsonPrimitive(text.toString());
		if (value instanceof Map<?, ?> map)
			return fromSkriptMap(map);
		if (value instanceof Object[] array)
			return fromCollection(Arrays.asList(array));
		if (value instanceof Collection<?> collection)
			return fromCollection(collection);
		// 플레이어, 위치 등 그 밖의 Skript 값은 Skript 가 보여주는 문자열로
		return new JsonPrimitive(Classes.toString(value));
	}

	private static JsonElement fromNumber(Number number) {
		if (number instanceof Double || number instanceof Float) {
			double d = number.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d))
				return JsonNull.INSTANCE;
			// 5.0 처럼 정수인 실수는 5 로 보낸다 (정수만 받는 API 가 많음)
			if (d == Math.rint(d) && Math.abs(d) < 9.007199254740992E15)
				return new JsonPrimitive((long) d);
		}
		return new JsonPrimitive(number);
	}

	private static JsonElement fromCollection(Collection<?> values) {
		JsonArray array = new JsonArray();
		for (Object value : values)
			array.add(fromSkript(value));
		return array;
	}

	/**
	 * Skript 목록 변수의 원본 구조(Map)를 JSON 으로.
	 * 키가 1..n 으로 빠짐없이 이어지면 배열, 아니면 객체. 키가 null 인 항목은 "그 노드 자체의 값"이다.
	 */
	public static JsonElement fromSkriptMap(Map<?, ?> map) {
		Map<String, Object> children = new TreeMap<>();
		Object ownValue = null;
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() == null) {
				ownValue = entry.getValue();
			} else {
				children.put(entry.getKey().toString(), entry.getValue());
			}
		}
		if (children.isEmpty())
			return fromSkript(ownValue);

		if (isSequential(children.keySet())) {
			JsonArray array = new JsonArray();
			for (int i = 1; i <= children.size(); i++)
				array.add(fromSkript(children.get(String.valueOf(i))));
			return array;
		}
		JsonObject object = new JsonObject();
		// TreeMap 정렬 순서가 아니라 원래 Skript 순서를 쓰기 위해 원본 map 을 다시 순회
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() != null)
				object.add(entry.getKey().toString(), fromSkript(entry.getValue()));
		}
		return object;
	}

	private static boolean isSequential(Collection<String> keys) {
		int size = keys.size();
		for (String key : keys) {
			Integer index = parseIndex(key);
			if (index == null || index < 1 || index > size || !String.valueOf(index).equals(key))
				return false;
		}
		return true; // 중복 없는 키 n 개가 모두 1..n 범위 → 1..n 전부 존재
	}
}
