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

@Name("API Header")
@Description({
	"요청 또는 응답의 헤더 값 (이름은 대소문자 구분 없음).",
	"요청의 헤더는 설정/삭제할 수 있습니다. 인증 토큰(Authorization) 등을 넣을 때 사용합니다."
})
@Example("set api header \"Authorization\" of {_req} to \"Bearer %{_token}%\"")
@Example("send \"남은 요청 수: %api header \"X-RateLimit-Remaining\" of {_res}%\" to player")
@Since("1.0.0")
public class ExprApiHeader extends SimpleExpression<String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprApiHeader.class, String.class)
			.supplier(ExprApiHeader::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns(
				"[the] api header %string% of %apirequest/apiresponse%",
				"%apirequest/apiresponse%'[s] api header %string%")
			.build());
	}

	private Expression<String> name;
	private Expression<?> owner;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		name = (Expression<String>) exprs[matchedPattern == 0 ? 0 : 1];
		owner = exprs[matchedPattern == 0 ? 1 : 0];
		return true;
	}

	@Override
	protected String @Nullable [] get(Event event) {
		String header = name.getSingle(event);
		Object target = owner.getSingle(event);
		if (header == null || target == null)
			return new String[0];
		String value = null;
		if (target instanceof ApiRequest request) {
			value = request.header(header);
		} else if (target instanceof ApiResponse response) {
			value = response.header(header);
		}
		return value == null ? new String[0] : new String[] {value};
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		return switch (mode) {
			case SET -> new Class[] {String.class};
			case DELETE, RESET -> new Class[0];
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		String header = name.getSingle(event);
		if (header == null || !(owner.getSingle(event) instanceof ApiRequest request))
			return;
		String value = mode == ChangeMode.SET && delta != null && delta.length > 0 ? (String) delta[0] : null;
		request.setHeader(header, value);
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
		return new SyntaxStringBuilder(event, debug).append("api header", name, "of", owner).toString();
	}
}
