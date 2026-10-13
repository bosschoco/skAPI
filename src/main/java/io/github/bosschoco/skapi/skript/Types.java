package io.github.bosschoco.skapi.skript;

import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import io.github.bosschoco.skapi.http.ApiRequest;
import io.github.bosschoco.skapi.http.ApiResponse;
import org.jspecify.annotations.Nullable;

/** Skript 타입 등록: apirequest, apiresponse */
final class Types {

	private Types() {
	}

	static void register() {
		Classes.registerClass(new ClassInfo<>(ApiRequest.class, "apirequest")
			.user("api ?requests?")
			.name("API Request")
			.description("보낼 API(HTTP) 요청. 'new api request to \"url\"' 로 만들고 헤더/본문/쿼리 등을 설정한 뒤 send 로 보냅니다.")
			.since("1.0.0")
			.parser(new NonParser<>() {
				@Override
				public String toString(ApiRequest request, int flags) {
					return request.toString();
				}
			}));

		Classes.registerClass(new ClassInfo<>(ApiResponse.class, "apiresponse")
			.user("api ?responses?")
			.name("API Response")
			.description("API 요청의 응답. 상태 코드, 본문, 헤더, 오류 메시지 등을 담고 있습니다.")
			.since("1.0.0")
			.parser(new NonParser<>() {
				@Override
				public String toString(ApiResponse response, int flags) {
					return response.toString();
				}
			}));
	}

	/** 스크립트에 글자로 직접 쓸 수 없는 타입용 파서 (문자열 변환만 지원). */
	private abstract static class NonParser<T> extends Parser<T> {

		@Override
		public @Nullable T parse(String input, ParseContext context) {
			return null;
		}

		@Override
		public boolean canParse(ParseContext context) {
			return false;
		}

		@Override
		public String toVariableNameString(T object) {
			return toString(object, 0);
		}
	}
}
