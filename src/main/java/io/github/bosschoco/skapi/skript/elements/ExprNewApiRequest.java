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
import io.github.bosschoco.skapi.http.ApiRequest;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Locale;
import java.util.Set;

@Name("New API Request")
@Description("새 API 요청을 만듭니다. 메서드를 생략하면 GET 입니다. 본문이 JSON 이면 Content-Type 이 자동으로 application/json 이 됩니다.")
@Example("set {_req} to new api request to \"https://api.example.com/users\"")
@Example("set {_req} to new post api request to \"https://api.example.com/users\" with body json of {_user::*}")
@Example("set {_req} to new api request to \"https://api.example.com/users/1\" with method \"DELETE\"")
@Since("0.1.0")
public class ExprNewApiRequest extends SimpleExpression<ApiRequest> {

	private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprNewApiRequest.class, ApiRequest.class)
			.supplier(ExprNewApiRequest::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns(
				"[a] [new] [(:get|:post|:put|:patch|:delete|:head|:options)] api request (to|for) %string% [with [the] body %-string%]",
				"[a] [new] api request (to|for) %string% (with|using) [the] method %string% [and [the] body %-string%]")
			.build());
	}

	private Expression<String> url;
	private @Nullable Expression<String> method;
	private @Nullable Expression<String> body;
	private String fixedMethod = "GET";

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		url = (Expression<String>) exprs[0];
		if (matchedPattern == 0) {
			for (String tag : parseResult.tags) {
				if (METHODS.contains(tag)) {
					fixedMethod = tag.toUpperCase(Locale.ROOT);
					break;
				}
			}
			body = (Expression<String>) exprs[1];
		} else {
			method = (Expression<String>) exprs[1];
			body = (Expression<String>) exprs[2];
		}
		return true;
	}

	@Override
	protected ApiRequest @Nullable [] get(Event event) {
		String target = url.getSingle(event);
		if (target == null)
			return new ApiRequest[0];
		String requestMethod = method == null ? fixedMethod : method.getSingle(event);
		ApiRequest request = new ApiRequest(requestMethod == null ? "GET" : requestMethod, target);
		if (body != null)
			request.setBody(body.getSingle(event));
		return new ApiRequest[] {request};
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends ApiRequest> getReturnType() {
		return ApiRequest.class;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		SyntaxStringBuilder builder = new SyntaxStringBuilder(event, debug).append("new api request to", url);
		if (method != null) {
			builder.append("with method", method);
		} else {
			builder.append("(" + fixedMethod + ")");
		}
		if (body != null)
			builder.append("with body", body);
		return builder.toString();
	}
}
