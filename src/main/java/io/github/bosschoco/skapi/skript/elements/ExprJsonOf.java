package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.bosschoco.skapi.json.Json;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Map;

@Name("JSON Of")
@Description({
	"Skript 값을 JSON 문자열로 바꿉니다. 목록 변수를 넣으면 구조 그대로 JSON 객체/배열이 됩니다.",
	"인덱스가 1, 2, 3... 처럼 이어지면 배열, 그 외에는 객체가 됩니다. {_a::b::c} 처럼 중첩하면 중첩 JSON 이 됩니다.",
	"따옴표/줄바꿈 이스케이프를 자동으로 처리하므로, 채팅 내용 같은 사용자 입력을 넣어도 안전합니다. 'pretty json of' 는 보기 좋게 줄바꿈합니다."
})
@Example("""
	set {_body::model} to "gpt-model"
	set {_body::messages::1::role} to "user"
	set {_body::messages::1::content} to message
	set {_json} to json of {_body::*}
	# → {"model":"gpt-model","messages":[{"role":"user","content":"..."}]}
	""")
@Since("0.1.0")
public class ExprJsonOf extends SimpleExpression<String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprJsonOf.class, String.class)
			.supplier(ExprJsonOf::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns("[:pretty] json [string|text] (of|from) %objects%")
			.build());
	}

	private Expression<?> values;
	private boolean pretty;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		values = exprs[0];
		pretty = parseResult.hasTag("pretty");
		return true;
	}

	@Override
	protected String @Nullable [] get(Event event) {
		return new String[] {Json.stringify(toJson(event), pretty)};
	}

	private JsonElement toJson(Event event) {
		if (values instanceof Variable<?> variable && variable.isList()) {
			// 목록 변수는 인덱스(키)까지 살려야 하므로 원본 구조를 그대로 읽는다
			Object raw = variable.getRaw(event);
			if (raw instanceof Map<?, ?> map && !map.isEmpty())
				return Json.fromSkriptMap(map);
			return new JsonObject();
		}
		Object[] all = values.getArray(event);
		if (values.isSingle())
			return Json.fromSkript(all.length == 0 ? null : all[0]);
		JsonArray array = new JsonArray();
		for (Object value : all)
			array.add(Json.fromSkript(value));
		return array;
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return new SyntaxStringBuilder(event, debug).append(pretty ? "pretty json of" : "json of", values).toString();
	}
}
