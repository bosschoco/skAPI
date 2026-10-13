package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import io.github.bosschoco.skapi.http.ApiRequest;
import io.github.bosschoco.skapi.http.ApiResponse;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("API Data")
@Description({
	"요청에 붙여두는 임의의 값(플레이어, 숫자, 문자열 등). 서버로 전송되지는 않습니다.",
	"요청을 보내기 전에 넣어두면, 그 요청의 응답에서도 같은 이름으로 꺼낼 수 있습니다.",
	"로컬 변수는 skAPI 가 자동으로 보존하지만, 응답 객체 하나만 다른 곳(함수, 전역 변수 등)으로 넘길 때 함께 들고 다닐 값을 넣어두기 좋습니다."
})
@Example("""
	set api data "asker" of {_req} to player
	send {_req} and store the response in {_res}
	send "답변 도착!" to (api data "asker" of {_res})
	""")
@Since("0.1.0")
public class ExprApiData extends SimpleExpression<Object> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprApiData.class, Object.class)
			.supplier(ExprApiData::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns(
				"[the] api data %string% of %apirequest/apiresponse%",
				"%apirequest/apiresponse%'[s] api data %string%")
			.build());
	}

	private Expression<String> key;
	private Expression<?> owner;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		key = (Expression<String>) exprs[matchedPattern == 0 ? 0 : 1];
		owner = exprs[matchedPattern == 0 ? 1 : 0];
		return true;
	}

	private @Nullable ApiRequest requestOf(Event event) {
		Object target = owner.getSingle(event);
		if (target instanceof ApiRequest request)
			return request;
		if (target instanceof ApiResponse response)
			return response.request();
		return null;
	}

	@Override
	protected Object @Nullable [] get(Event event) {
		String name = key.getSingle(event);
		ApiRequest request = requestOf(event);
		if (name == null || request == null)
			return new Object[0];
		Object value = request.data(name);
		return value == null ? new Object[0] : new Object[] {value};
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		return switch (mode) {
			case SET -> new Class[] {Object.class};
			case DELETE, RESET -> new Class[0];
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		String name = key.getSingle(event);
		ApiRequest request = requestOf(event);
		if (name == null || request == null)
			return;
		request.setData(name, mode == ChangeMode.SET && delta != null && delta.length > 0 ? delta[0] : null);
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<?> getReturnType() {
		return Object.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return new SyntaxStringBuilder(event, debug).append("api data", key, "of", owner).toString();
	}
}
