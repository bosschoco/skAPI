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

@Name("API URL")
@Description({
	"요청의 URL('api query' 로 붙인 쿼리는 제외), 또는 응답의 최종 URL(쿼리 포함, 리다이렉트가 있었다면 마지막 주소).",
	"요청의 URL 은 바꿀 수 있습니다. 쿼리 파라미터는 'api query' 로 따로 붙이는 것을 권장합니다."
})
@Example("set api url of {_req} to \"https://api.example.com/v2/users\"")
@Since("0.1.0")
public class ExprApiUrl extends SimplePropertyExpression<Object, String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ExprApiUrl.class, String.class, "api url", "apirequests/apiresponses", false)
				.supplier(ExprApiUrl::new)
				.build());
	}

	@Override
	public @Nullable String convert(Object from) {
		if (from instanceof ApiRequest request)
			return request.url();
		if (from instanceof ApiResponse response)
			return response.url();
		return null;
	}

	@Override
	public Class<?> @Nullable [] acceptChange(ChangeMode mode) {
		return mode == ChangeMode.SET ? new Class[] {String.class} : null;
	}

	@Override
	public void change(Event event, Object @Nullable [] delta, ChangeMode mode) {
		if (delta == null || delta.length == 0)
			return;
		String url = (String) delta[0];
		for (Object owner : getExpr().getArray(event)) {
			if (owner instanceof ApiRequest request)
				request.setUrl(url);
		}
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	protected String getPropertyName() {
		return "api url";
	}
}
