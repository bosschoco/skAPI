package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.expressions.base.SimplePropertyExpression;
import io.github.bosschoco.skapi.http.ApiRequest;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("API Method")
@Description("요청의 HTTP 메서드(GET, POST, PUT, PATCH, DELETE ...). 바꿀 수 있습니다.")
@Example("set api method of {_req} to \"PATCH\"")
@Since("1.0.0")
public class ExprApiMethod extends SimplePropertyExpression<ApiRequest, String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ExprApiMethod.class, String.class, "api method", "apirequests", false)
				.supplier(ExprApiMethod::new)
				.build());
	}

	@Override
	public String convert(ApiRequest request) {
		return request.method();
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		return mode == ChangeMode.SET ? new Class[] {String.class} : null;
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		if (delta == null || delta.length == 0)
			return;
		String method = (String) delta[0];
		for (ApiRequest request : getExpr().getArray(event))
			request.setMethod(method);
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	protected String getPropertyName() {
		return "api method";
	}
}
