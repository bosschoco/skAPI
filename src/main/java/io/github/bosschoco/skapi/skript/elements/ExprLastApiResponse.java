package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import io.github.bosschoco.skapi.http.ApiResponse;
import io.github.bosschoco.skapi.skript.LastResponses;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Last API Response")
@Description({
	"현재 트리거에서 마지막으로 받은 응답.",
	"콜백 섹션('send {_req} async:') 안이나 'send {_req} and wait' 다음 줄에서 응답을 꺼낼 때 사용합니다."
})
@Example("""
	send {_req} asynchronously:
		send api body of last api response to player
	""")
@Since("1.0.0")
public class ExprLastApiResponse extends SimpleExpression<ApiResponse> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprLastApiResponse.class, ApiResponse.class)
			.supplier(ExprLastApiResponse::new)
			.priority(SyntaxInfo.SIMPLE)
			.addPatterns("[the] [last] api response")
			.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		return true;
	}

	@Override
	protected ApiResponse @Nullable [] get(Event event) {
		ApiResponse response = LastResponses.get(event);
		return response == null ? new ApiResponse[0] : new ApiResponse[] {response};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends ApiResponse> getReturnType() {
		return ApiResponse.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "the last api response";
	}
}
