package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.classes.Changer.ChangerUtils;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.bosschoco.skapi.http.ApiResponse;
import io.github.bosschoco.skapi.json.Json;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("JSON Value")
@Description({
	"JSON(응답 본문 또는 문자열)에서 경로로 값을 꺼냅니다. 경로 예: \"choices[0].message.content\", \"data.items.1.name\", \"list[-1]\" (마지막 요소)",
	"\"content[type=text].text\" 처럼 [필드=값] 을 쓰면 배열에서 조건에 맞는 첫 요소를 고릅니다.",
	"숫자는 숫자로, true/false 는 boolean 으로, 문자열은 문자열로 나옵니다. 객체/배열을 가리키면 그 부분의 JSON 문자열이 나옵니다.",
	"'json values' 를 쓰면 배열의 각 요소(또는 객체의 각 값)를 목록으로 돌려줍니다. 경로를 \"\" 로 주면 최상위 자체입니다.",
	"JSON 문자열을 담은 변수(또는 api body)에는 set / add / delete 로 경로의 값을 바꿀 수 있습니다. 중간 객체/배열은 자동으로 만들어집니다.",
	"이 방식은 키의 대소문자가 그대로 유지되므로, camelCase 키(maxTokens 등)가 필요한 API 는 'json of' 대신 이 방식을 쓰세요.",
	"문자열 값은 항상 JSON 문자열로 들어갑니다. (사용자 입력이 JSON 구조로 해석되지 않으므로 안전)"
})
@Example("set {_answer} to json value \"choices[0].message.content\" of {_res}")
@Example("set {_names::*} to json values \"users[*]\" of {_res} # 배열 전체 → 각 요소의 JSON")
@Example("""
	set {_json} to "{}"
	set json value "generationConfig.maxOutputTokens" of {_json} to 200
	set json value "contents[0].parts[0].text" of {_json} to message
	add "tag" to json values "tags" of {_json}
	delete json value "generationConfig" of {_json}
	""")
@Since("0.1.0")
public class ExprJsonValue extends SimpleExpression<Object> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprJsonValue.class, Object.class)
			.supplier(ExprJsonValue::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns("[the] json (value|:values) %string% (of|from|in) %string/apiresponse%")
			.build());
	}

	private Expression<String> path;
	private Expression<?> source;
	private boolean plural;
	/** 원본이 문자열을 저장할 수 있는 곳(변수, api body 등)이면 경로로 값을 바꿀 수 있다 */
	private boolean writable;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		path = (Expression<String>) exprs[0];
		source = exprs[1];
		plural = parseResult.hasTag("values");
		writable = ChangerUtils.acceptsChange(source, ChangeMode.SET, String.class);
		return true;
	}

	private static String normalize(String jsonPath) {
		return jsonPath.endsWith("[*]") ? jsonPath.substring(0, jsonPath.length() - 3) : jsonPath;
	}

	@Override
	protected Object @Nullable [] get(Event event) {
		String jsonPath = path.getSingle(event);
		Object from = source.getSingle(event);
		if (jsonPath == null || from == null)
			return new Object[0];

		JsonElement root = from instanceof ApiResponse response ? response.json() : Json.parse(from.toString());
		JsonElement selected = Json.select(root, normalize(jsonPath));
		if (plural)
			return Json.toSkriptList(selected).toArray();
		Object value = Json.toSkript(selected);
		return value == null ? new Object[0] : new Object[] {value};
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		if (!writable)
			return null;
		return switch (mode) {
			case SET, ADD -> new Class[] {Object[].class};
			case DELETE, RESET -> new Class[0];
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		String jsonPath = path.getSingle(event);
		Object current = source.getSingle(event);
		if (jsonPath == null || current instanceof ApiResponse)
			return;
		JsonElement root = current == null ? null : Json.parse(current.toString());
		String normalizedPath = normalize(jsonPath);
		JsonElement updated = root;

		switch (mode) {
			case SET -> {
				JsonElement value = delta == null || delta.length == 0 ? null
					: delta.length == 1 ? Json.fromSkript(delta[0]) : Json.fromSkript(delta);
				updated = Json.modify(root, normalizedPath, value, Json.Change.SET);
			}
			case ADD -> {
				if (delta == null)
					return;
				for (Object value : delta) // 여러 값을 더하면 하나씩 배열 끝에 붙인다
					updated = Json.modify(updated, normalizedPath, Json.fromSkript(value), Json.Change.ADD);
			}
			case DELETE, RESET -> updated = Json.modify(root, normalizedPath, null, Json.Change.REMOVE);
			default -> {
				return;
			}
		}
		source.change(event, new Object[] {Json.stringify(updated == null ? new JsonObject() : updated, false)}, ChangeMode.SET);
	}

	@Override
	public boolean isSingle() {
		return !plural;
	}

	@Override
	public Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return new SyntaxStringBuilder(event, debug)
			.append(plural ? "json values" : "json value", path, "of", source)
			.toString();
	}
}
