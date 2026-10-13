package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.expressions.base.SimplePropertyExpression;
import ch.njol.skript.util.Timespan;
import io.github.bosschoco.skapi.http.ApiResponse;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.time.Duration;

/** 응답의 읽기 전용 정보들: 상태 코드, 오류 메시지, 걸린 시간. */
public final class ExprApiResponseInfo {

	private ExprApiResponseInfo() {
	}

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(Status.class, Long.class, "api status [code]", "apiresponses", false)
				.supplier(Status::new)
				.build());
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ErrorMessage.class, String.class, "api error [message]", "apiresponses", false)
				.supplier(ErrorMessage::new)
				.build());
		registry.register(SyntaxRegistry.EXPRESSION,
			PropertyExpression.infoBuilder(ResponseTime.class, Timespan.class, "api (duration|response time)", "apiresponses", false)
				.supplier(ResponseTime::new)
				.build());
	}

	@Name("API Status Code")
	@Description("응답의 HTTP 상태 코드 (200, 404, 500 ...). 네트워크 오류로 응답을 못 받았으면 0 입니다.")
	@Example("if api status of {_res} is 404:")
	@Since("1.0.0")
	public static class Status extends SimplePropertyExpression<ApiResponse, Long> {

		@Override
		public Long convert(ApiResponse response) {
			return (long) response.status();
		}

		@Override
		public Class<? extends Long> getReturnType() {
			return Long.class;
		}

		@Override
		protected String getPropertyName() {
			return "api status";
		}
	}

	@Name("API Error")
	@Description({
		"네트워크 오류 메시지 (연결 실패, 시간 초과, 잘못된 URL 등).",
		"서버가 응답했다면(404, 500 포함) none 입니다. 상태 코드까지 확인하려면 '{_res} is successful' 을 쓰세요."
	})
	@Example("if api error of {_res} is set:\n\tsend \"요청 실패: %api error of {_res}%\" to player")
	@Since("1.0.0")
	public static class ErrorMessage extends SimplePropertyExpression<ApiResponse, String> {

		@Override
		public @Nullable String convert(ApiResponse response) {
			return response.error();
		}

		@Override
		public Class<? extends String> getReturnType() {
			return String.class;
		}

		@Override
		protected String getPropertyName() {
			return "api error";
		}
	}

	@Name("API Response Time")
	@Description("요청을 보내고 응답을 받기까지 걸린 시간.")
	@Example("send \"%api response time of {_res}% 걸림\" to player")
	@Since("1.0.0")
	public static class ResponseTime extends SimplePropertyExpression<ApiResponse, Timespan> {

		@Override
		public Timespan convert(ApiResponse response) {
			return Timespan.fromDuration(Duration.ofMillis(response.durationMillis()));
		}

		@Override
		public Class<? extends Timespan> getReturnType() {
			return Timespan.class;
		}

		@Override
		protected String getPropertyName() {
			return "api response time";
		}
	}
}
