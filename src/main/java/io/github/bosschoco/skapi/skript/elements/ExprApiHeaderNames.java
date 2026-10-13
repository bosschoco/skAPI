package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import io.github.bosschoco.skapi.http.ApiRequest;
import io.github.bosschoco.skapi.http.ApiResponse;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("API Header Names")
@Description("요청 또는 응답에 들어있는 모든 헤더 이름.")
@Example("""
	loop api header names of {_res}:
		send "%loop-value%: %api header loop-value of {_res}%" to player
	""")
@Since("0.1.0")
public class ExprApiHeaderNames extends PropertyExpression<Object, String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ExprApiHeaderNames.class, String.class, "api header names", "apirequests/apiresponses", false)
				.supplier(ExprApiHeaderNames::new)
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		setExpr(exprs[0]);
		return true;
	}

	@Override
	protected String[] get(Event event, Object[] source) {
		List<String> names = new ArrayList<>();
		for (Object owner : source) {
			if (owner instanceof ApiRequest request) {
				names.addAll(request.headers().keySet());
			} else if (owner instanceof ApiResponse response) {
				names.addAll(response.headerNames());
			}
		}
		return names.toArray(new String[0]);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public Class<? extends String> getReturnType() {
		return String.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "api header names of " + getExpr().toString(event, debug);
	}
}
