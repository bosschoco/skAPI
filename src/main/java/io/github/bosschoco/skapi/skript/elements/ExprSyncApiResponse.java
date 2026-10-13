package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import io.github.bosschoco.skapi.SkApiPlugin;
import io.github.bosschoco.skapi.http.ApiRequest;
import io.github.bosschoco.skapi.http.ApiResponse;
import io.github.bosschoco.skapi.skript.LastResponses;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Sync API Response")
@Description({
	"요청을 동기로 보내고 응답을 바로 돌려줍니다. 이 표현식을 쓸 때마다 요청이 새로 나갑니다.",
	"응답이 올 때까지 서버(메인 스레드)가 멈추므로, 보통은 비동기 'send {_req} and store the response in {_res}' 를 권장합니다."
})
@Example("set {_res} to sync api response of {_req}")
@Since("0.1.0")
public class ExprSyncApiResponse extends SimpleExpression<ApiResponse> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprSyncApiResponse.class, ApiResponse.class)
			.supplier(ExprSyncApiResponse::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns("[the] sync[hronous] api response (of|for|from) %apirequest%")
			.build());
	}

	private Expression<ApiRequest> request;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		request = (Expression<ApiRequest>) exprs[0];
		return true;
	}

	@Override
	protected ApiResponse @Nullable [] get(Event event) {
		ApiRequest target = request.getSingle(event);
		if (target == null)
			return new ApiResponse[0];
		ApiResponse response = SkApiPlugin.http().sendBlocking(target.snapshot());
		LastResponses.set(event, response);
		return new ApiResponse[] {response};
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
		return new SyntaxStringBuilder(event, debug).append("sync api response of", request).toString();
	}
}
