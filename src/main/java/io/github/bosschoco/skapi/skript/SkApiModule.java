package io.github.bosschoco.skapi.skript;

import io.github.bosschoco.skapi.skript.elements.CondApiSuccess;
import io.github.bosschoco.skapi.skript.elements.EffLoadJson;
import io.github.bosschoco.skapi.skript.elements.EffSecSendApiRequest;
import io.github.bosschoco.skapi.skript.elements.ExprApiBody;
import io.github.bosschoco.skapi.skript.elements.ExprApiData;
import io.github.bosschoco.skapi.skript.elements.ExprApiHeader;
import io.github.bosschoco.skapi.skript.elements.ExprApiHeaderNames;
import io.github.bosschoco.skapi.skript.elements.ExprApiMethod;
import io.github.bosschoco.skapi.skript.elements.ExprApiQuery;
import io.github.bosschoco.skapi.skript.elements.ExprApiResponseInfo;
import io.github.bosschoco.skapi.skript.elements.ExprApiTimeout;
import io.github.bosschoco.skapi.skript.elements.ExprApiUrl;
import io.github.bosschoco.skapi.skript.elements.ExprEncodedString;
import io.github.bosschoco.skapi.skript.elements.ExprJsonOf;
import io.github.bosschoco.skapi.skript.elements.ExprJsonValue;
import io.github.bosschoco.skapi.skript.elements.ExprLastApiResponse;
import io.github.bosschoco.skapi.skript.elements.ExprNewApiRequest;
import io.github.bosschoco.skapi.skript.elements.ExprSyncApiResponse;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;

/** skAPI 의 모든 타입과 문법을 Skript 에 등록한다. */
public final class SkApiModule implements AddonModule {

	@Override
	public void init(SkriptAddon addon) {
		// 문법 패턴이 %apirequest% 같은 타입을 참조하므로 타입을 먼저 등록한다
		Types.register();
	}

	@Override
	public void load(SkriptAddon addon) {
		SyntaxRegistry registry = addon.syntaxRegistry();

		// 요청 보내기 (동기/비동기)
		EffSecSendApiRequest.register(registry);
		ExprSyncApiResponse.register(registry);
		ExprLastApiResponse.register(registry);

		// 요청 만들기/설정
		ExprNewApiRequest.register(registry);
		ExprApiMethod.register(registry);
		ExprApiUrl.register(registry);
		ExprApiBody.register(registry);
		ExprApiTimeout.register(registry);
		ExprApiHeader.register(registry);
		ExprApiHeaderNames.register(registry);
		ExprApiQuery.register(registry);
		ExprApiData.register(registry);

		// 응답 확인
		ExprApiResponseInfo.register(registry);
		CondApiSuccess.register(registry);

		// JSON / 인코딩
		ExprJsonValue.register(registry);
		ExprJsonOf.register(registry);
		EffLoadJson.register(registry);
		ExprEncodedString.register(registry);
	}

	@Override
	public String name() {
		return "skapi";
	}
}
