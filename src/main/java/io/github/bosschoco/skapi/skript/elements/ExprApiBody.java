package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.expressions.base.SimplePropertyExpression;
import io.github.bosschoco.skapi.http.ApiRequest;
import io.github.bosschoco.skapi.http.ApiResponse;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("API Body")
@Description({
	"요청에 담아 보낼 본문, 또는 응답으로 받은 본문(문자열).",
	"요청의 본문은 설정/삭제할 수 있습니다. JSON 은 'json of {_목록::*}' 로 쉽게 만들 수 있습니다."
})
@Example("set api body of {_req} to json of {_data::*}")
@Example("send \"응답: %api body of {_res}%\" to player")
@Since("0.1.0")
public class ExprApiBody extends SimplePropertyExpression<Object, String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ExprApiBody.class, String.class, "api body", "apirequests/apiresponses", false)
				.supplier(ExprApiBody::new)
				.build());
	}

	@Override
	public @Nullable String convert(Object from) {
		if (from instanceof ApiRequest request)
			return request.body();
		if (from instanceof ApiResponse response)
			return response.body();
		return null;
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
		String body = mode == ChangeMode.SET && delta != null && delta.length > 0 ? (String) delta[0] : null;
		for (Object owner : getExpr().getArray(event)) {
			if (owner instanceof ApiRequest request)
				request.setBody(body);
		}
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	protected String getPropertyName() {
		return "api body";
	}
}
