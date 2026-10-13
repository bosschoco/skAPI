package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.expressions.base.SimplePropertyExpression;
import ch.njol.skript.util.Timespan;
import io.github.bosschoco.skapi.SkApiPlugin;
import io.github.bosschoco.skapi.http.ApiRequest;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.time.Duration;

@Name("API Timeout")
@Description({
	"요청이 응답을 기다리는 최대 시간. 설정하지 않으면 config.yml 의 http.default-timeout 을 씁니다.",
	"AI API 처럼 답변이 오래 걸리는 요청은 넉넉하게 늘려주세요. reset/delete 하면 기본값으로 돌아갑니다."
})
@Example("set api timeout of {_req} to 60 seconds")
@Since("0.1.0")
public class ExprApiTimeout extends SimplePropertyExpression<ApiRequest, Timespan> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ExprApiTimeout.class, Timespan.class, "api timeout", "apirequests", false)
				.supplier(ExprApiTimeout::new)
				.build());
	}

	@Override
	public Timespan convert(ApiRequest request) {
		Duration timeout = request.timeout();
		return Timespan.fromDuration(timeout != null ? timeout : SkApiPlugin.settings().defaultTimeout());
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		return switch (mode) {
			case SET -> new Class[] {Timespan.class};
			case DELETE, RESET -> new Class[0];
			default -> null;
		};
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		Duration timeout = mode == ChangeMode.SET && delta != null && delta.length > 0
			? ((Timespan) delta[0]).getDuration()
			: null;
		for (ApiRequest request : getExpr().getArray(event))
			request.setTimeout(timeout);
	}

	@Override
	public Class<? extends Timespan> getReturnType() {
		return Timespan.class;
	}

	@Override
	protected String getPropertyName() {
		return "api timeout";
	}
}
