package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import com.google.gson.JsonElement;
import io.github.bosschoco.skapi.http.ApiResponse;
import io.github.bosschoco.skapi.json.Json;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

@Name("Load JSON Into List")
@Description({
	"JSON 을 Skript 목록 변수에 그대로 펼쳐 넣습니다. 기존 목록 내용은 지워집니다.",
	"객체의 키는 인덱스가 되고, 배열은 1, 2, 3... 인덱스가 됩니다. 중첩은 {_data::user::name} 처럼 들어갑니다.",
	"DB API 조회 결과처럼 여러 값을 한꺼번에 다룰 때 편리합니다."
})
@Example("""
	load json {_res} into {_data::*}
	send "이름: %{_data::user::name}%, 첫 태그: %{_data::tags::1}%" to player
	loop {_data::items::*}:
		send "%loop-index%번 항목" to player
	""")
@Since("0.1.0")
public class EffLoadJson extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffLoadJson.class)
			.supplier(EffLoadJson::new)
			.addPatterns("(load|parse|convert) json [from] %string/apiresponse% (in|into|to) %~objects%")
			.build());
	}

	private Expression<?> source;
	private Variable<?> target;

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		source = exprs[0];
		if (!(exprs[1] instanceof Variable<?> variable) || !variable.isList()) {
			Skript.error("JSON 은 목록 변수에만 넣을 수 있습니다. 예: load json {_res} into {_data::*}");
			return false;
		}
		target = variable;
		return true;
	}

	@Override
	protected void execute(Event event) {
		Object from = source.getSingle(event);
		target.change(event, null, ChangeMode.DELETE);
		if (from == null)
			return;
		JsonElement root = from instanceof ApiResponse response ? response.json() : Json.parse(from.toString());
		if (root == null || root.isJsonNull())
			return;

		// {_data::*} → "_data::" 처럼 끝의 * 를 떼고 그 뒤에 키를 붙인다 (Skript 내부의 목록 인덱스 설정과 같은 방식)
		String listName = target.getName().toString(event);
		String prefix = listName.substring(0, listName.length() - 1);
		Map<String, Object> entries = new LinkedHashMap<>();
		Json.flatten(root, prefix, entries);
		boolean local = target.isLocal();
		for (Map.Entry<String, Object> entry : entries.entrySet())
			Variables.setVariable(entry.getKey(), entry.getValue(), event, local);
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return new SyntaxStringBuilder(event, debug).append("load json", source, "into", target).toString();
	}
}
