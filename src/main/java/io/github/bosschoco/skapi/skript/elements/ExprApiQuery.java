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
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("API Query Parameter")
@Description({
	"요청 URL 뒤에 붙는 쿼리 파라미터 (?key=value). 값은 자동으로 URL 인코딩되므로 한글/공백도 그대로 넣으면 됩니다.",
	"숫자 등 문자열이 아닌 값도 넣을 수 있습니다."
})
@Example("set api query \"q\" of {_req} to \"마인크래프트 서버\"")
@Example("set api query \"page\" of {_req} to 2")
@Since("0.1.0")
public class ExprApiQuery extends SimpleExpression<String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprApiQuery.class, String.class)
			.supplier(ExprApiQuery::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns(
				"[the] api query [param[eter]] %string% of %apirequest%",
				"%apirequest%'[s] api query [param[eter]] %string%")
			.build());
	}

	private Expression<String> name;
	private Expression<ApiRequest> request;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		name = (Expression<String>) exprs[matchedPattern == 0 ? 0 : 1];
		request = (Expression<ApiRequest>) exprs[matchedPattern == 0 ? 1 : 0];
		return true;
	}

	@Override
	protected String @Nullable [] get(Event event) {
		String key = name.getSingle(event);
		ApiRequest target = request.getSingle(event);
		if (key == null || target == null)
			return new String[0];
		String value = target.query(key);
		return value == null ? new String[0] : new String[] {value};
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
		String key = name.getSingle(event);
		ApiRequest target = request.getSingle(event);
		if (key == null || target == null)
			return;
		String value = mode == ChangeMode.SET && delta != null && delta.length > 0 ? Stringify.of(delta[0]) : null;
		target.setQuery(key, value);
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
		return new SyntaxStringBuilder(event, debug).append("api query", name, "of", request).toString();
	}
}
