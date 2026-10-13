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
import io.github.bosschoco.skapi.json.Json;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.DefaultSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Name("JSON Escaped / URL Encoded Text")
@Description({
	"json escaped: JSON 문자열 안에 넣을 수 있도록 따옴표, 역슬래시, 줄바꿈 등을 이스케이프합니다. (앞뒤 따옴표는 붙이지 않음)",
	"url encoded: URL 에 넣을 수 있도록 한글, 공백, 특수문자를 인코딩합니다.",
	"JSON 을 직접 문자열로 조립하거나 URL 을 직접 만들 때 사용합니다. 보통은 'json of' 와 'api query' 를 쓰는 편이 더 쉽습니다."
})
@Example("set {_json} to \"{\"\"text\"\": \"\"%json escaped message%\"\"}\"")
@Example("set {_req} to new api request to \"https://api.example.com/search?q=%url encoded {_keyword}%\"")
@Since("1.0.0")
public class ExprEncodedString extends SimpleExpression<String> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, DefaultSyntaxInfos.Expression.builder(ExprEncodedString.class, String.class)
			.supplier(ExprEncodedString::new)
			.priority(SyntaxInfo.COMBINED)
			.addPatterns(
				"(json escaped|json-escaped) %string%",
				"(url encoded|url-encoded) %string%")
			.build());
	}

	private Expression<String> text;
	private boolean url;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		text = (Expression<String>) exprs[0];
		url = matchedPattern == 1;
		return true;
	}

	@Override
	protected String @Nullable [] get(Event event) {
		String value = text.getSingle(event);
		if (value == null)
			return new String[0];
		return new String[] {url ? URLEncoder.encode(value, StandardCharsets.UTF_8) : Json.escape(value)};
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
		return new SyntaxStringBuilder(event, debug).append(url ? "url encoded" : "json escaped", text).toString();
	}
}
