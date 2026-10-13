package io.github.bosschoco.skapi.skript.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.classes.Changer.ChangerUtils;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.EffectSection;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.util.SectionUtils;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import io.github.bosschoco.skapi.SkApiPlugin;
import io.github.bosschoco.skapi.http.ApiRequest;
import io.github.bosschoco.skapi.http.ApiResponse;
import io.github.bosschoco.skapi.http.HttpService;
import io.github.bosschoco.skapi.skript.LastResponses;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;

/**
 * API 요청 전송. 이 애드온의 핵심.
 *
 * <h2>처리 방식</h2>
 * <ul>
 *     <li><b>대기형 비동기 (기본/권장)</b> — {@code send {_req} and store response in {_res}}<br>
 *         요청은 다른 스레드에서 보내고, 응답이 오면 <b>다음 줄부터 메인 스레드에서 이어서</b> 실행한다.
 *         서버는 멈추지 않고, 로컬 변수({@code {_player}} 등)와 이벤트 값({@code player})이 그대로 유지된다.</li>
 *     <li><b>콜백형 비동기</b> — {@code send {_req} async:} + 섹션<br>
 *         섹션 아래 코드는 바로 계속 실행되고, 섹션 안의 코드만 응답이 왔을 때 실행된다.
 *         섹션 안에서는 <b>요청을 보낸 시점의 로컬 변수 복사본</b>을 그대로 쓸 수 있다.</li>
 *     <li><b>백그라운드</b> — {@code send {_req} async}<br>
 *         보내기만 하고 응답은 신경 쓰지 않는다.</li>
 *     <li><b>동기</b> — {@code send {_req} sync and store response in {_res}}<br>
 *         응답이 올 때까지 현재 스레드를 멈춘다. 메인 스레드라면 서버 전체가 멈추니 주의.</li>
 * </ul>
 *
 * <h2>로컬 변수 보존 원리</h2>
 * Skript 는 트리거 실행이 끝나면 그 이벤트의 로컬 변수를 지워버린다.
 * 그래서 평범하게 비동기 콜백을 만들면, 응답이 왔을 때 {@code {_player}} 같은 값이 이미 사라져 있다.
 * 여기서는 Skript 의 {@code wait} 효과와 같은 방식으로,
 * 전송 직전에 로컬 변수 묶음을 떼어서(또는 복사해서) 보관하고, 응답이 오면 메인 스레드에서 다시 붙인 뒤 이어서 실행한다.
 */
@Name("Send API Request")
@Description({
	"API 요청을 보냅니다. 기본(권장)은 비동기이며, 응답이 오면 다음 줄부터 이어서 실행됩니다. 로컬 변수와 이벤트 값이 그대로 유지됩니다.",
	"섹션(콜백)을 붙이면 섹션 아래 코드는 바로 실행되고, 섹션 안의 코드만 응답이 왔을 때 실행됩니다.",
	"'sync' 를 붙이면 동기로 보냅니다. 응답이 올 때까지 서버가 멈추므로 꼭 필요할 때만 사용하세요."
})
@Example("""
	# 대기형 비동기 (권장)
	set {_req} to new api request to "https://api.example.com/hello"
	send {_req} and store the response in {_res}
	send "결과: %api body of {_res}%" to player
	""")
@Example("""
	# 콜백형 비동기
	send {_req} asynchronously:
		send "결과: %api body of last api response%" to player
	send "요청을 보냈어요!" to player
	""")
@Example("""
	# 동기 (서버가 멈춤 - 주의)
	send {_req} synchronously and store the response in {_res}
	""")
@Since("1.0.0")
public class EffSecSendApiRequest extends EffectSection {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.SECTION, SyntaxInfo.builder(EffSecSendApiRequest.class)
			.supplier(EffSecSendApiRequest::new)
			.addPatterns(
				"send %apirequest% (:async|:sync)[hronously]",
				"send %apirequest% [(:async|:sync)[hronously]] and (store|save|put) [the] [api] response (in|into|to) %~object%",
				"send %apirequest% [(:async|:sync)[hronously]] and wait [for [the] [api] response]")
			.build());
	}

	private enum Mode {
		/** 동기: 현재 스레드에서 응답까지 기다림 */
		SYNC,
		/** 비동기 대기형: 트리거를 멈췄다가 응답 후 다음 줄부터 재개 */
		WAIT,
		/** 비동기 콜백형: 섹션만 응답 후 실행, 아래 코드는 즉시 진행 */
		CALLBACK,
		/** 비동기 백그라운드: 응답 무시 */
		BACKGROUND
	}

	private Expression<ApiRequest> request;
	private @Nullable Expression<?> target;
	private Mode mode;
	private @Nullable Trigger callback;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult,
						@Nullable SectionNode sectionNode, @Nullable List<TriggerItem> triggerItems) {
		request = (Expression<ApiRequest>) exprs[0];
		boolean sync = parseResult.hasTag("sync");

		if (matchedPattern == 1) {
			target = exprs[1];
			if (!ChangerUtils.acceptsChange(target, ChangeMode.SET, ApiResponse.class)) {
				Skript.error("'" + target.toString(null, false) + "' 에는 api 응답을 저장할 수 없습니다. {_res} 같은 변수를 사용하세요.");
				return false;
			}
		}

		if (sync) {
			if (hasSection()) {
				Skript.error("동기(sync) 요청에는 섹션(콜백)을 붙일 수 없습니다. 'send ... sync and store the response in {_res}' 처럼 쓰거나, 비동기로 바꾸세요.");
				return false;
			}
			mode = Mode.SYNC;
			return true;
		}

		if (hasSection()) {
			assert sectionNode != null;
			mode = Mode.CALLBACK;
			Class<? extends Event>[] events = getParser().getCurrentEvents();
			// 같은 이벤트 타입으로 섹션을 따로 로드한다 → 섹션 안에서도 player 같은 이벤트 값을 쓸 수 있음.
			// 섹션 안은 "나중에" 실행되므로 지연(delay) 이후로 표시한다.
			callback = SectionUtils.loadDelayableLinkedCode("api response", (beforeLoading, afterLoading) ->
				loadCode(sectionNode, "api response", () -> {
					beforeLoading.run();
					getParser().setHasDelayBefore(Kleenean.TRUE);
				}, afterLoading, events));
			return callback != null;
		}

		if (matchedPattern == 0) {
			mode = Mode.BACKGROUND;
			return true;
		}

		mode = Mode.WAIT;
		// 이 줄 이후는 응답을 기다린 뒤 실행되므로 Skript 에 "지연 이후" 라고 알린다.
		// (예: 이후의 'cancel event' 는 의미가 없다는 경고를 Skript 가 띄워줌)
		getParser().setHasDelayBefore(Kleenean.TRUE);
		return true;
	}

	@Override
	protected @Nullable TriggerItem walk(Event event) {
		debug(event, true);

		ApiRequest original = request.getSingle(event);
		if (original == null) {
			error("보낼 api 요청이 없습니다(none). 요청을 담은 변수가 비어있지 않은지 확인하세요.");
			return getNext();
		}
		ApiRequest snapshot = original.snapshot();
		HttpService http = SkApiPlugin.http();

		return switch (mode) {
			case SYNC -> {
				deliver(event, http.sendBlocking(snapshot));
				yield getNext();
			}
			case BACKGROUND -> {
				http.send(snapshot);
				yield getNext();
			}
			case WAIT -> {
				sendAndResume(event, snapshot, http);
				yield null; // 여기서 트리거를 멈춘다. 나머지는 응답이 오면 이어서 실행.
			}
			case CALLBACK -> {
				sendWithCallback(event, snapshot, http);
				yield getNext();
			}
		};
	}

	/**
	 * 대기형: 로컬 변수 묶음을 이벤트에서 떼어 보관 → 응답 후 메인 스레드에서 다시 붙이고 다음 줄부터 재개.
	 * (Skript 의 wait 효과와 같은 원리)
	 */
	private void sendAndResume(Event event, ApiRequest snapshot, HttpService http) {
		TriggerItem next = getNext();
		Delay.addDelayedEvent(event);
		Object locals = Variables.removeLocals(event);

		http.send(snapshot).thenAccept(response -> SkApiPlugin.runOnMainThread(() -> {
			Variables.setLocalVariables(event, locals);
			try {
				deliver(event, response);
				if (next != null)
					TriggerItem.walk(next, event);
			} finally {
				// 이어서 실행한 코드가 오류로 멈춰도 로컬 변수는 반드시 정리한다
				Variables.removeLocals(event);
			}
		}));
	}

	/**
	 * 콜백형: 로컬 변수를 "복사"해서 보관(원래 트리거는 그대로 계속 진행) → 응답 후 복사본을 붙여서 섹션만 실행.
	 */
	private void sendWithCallback(Event event, ApiRequest snapshot, HttpService http) {
		Trigger trigger = callback;
		assert trigger != null;
		Object locals = Variables.copyLocalVariables(event);

		http.send(snapshot).thenAccept(response -> SkApiPlugin.runOnMainThread(() -> {
			// 혹시 같은 이벤트가 아직 다른 곳에서 로컬 변수를 쓰고 있다면 잠시 치워뒀다가 되돌린다
			Object previous = Variables.removeLocals(event);
			Variables.setLocalVariables(event, locals);
			try {
				Delay.addDelayedEvent(event);
				deliver(event, response);
				TriggerItem.walk(trigger, event);
			} finally {
				Variables.removeLocals(event);
				if (previous != null)
					Variables.setLocalVariables(event, previous);
			}
		}));
	}

	private void deliver(Event event, ApiResponse response) {
		LastResponses.set(event, response);
		if (target != null)
			target.change(event, new Object[] {response}, ChangeMode.SET);
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		SyntaxStringBuilder builder = new SyntaxStringBuilder(event, debug)
			.append("send", request)
			.append(mode == Mode.SYNC ? "synchronously" : "asynchronously");
		if (target != null) {
			builder.append("and store the response in", target);
		} else if (mode == Mode.WAIT) {
			builder.append("and wait for the response");
		}
		return builder.toString();
	}
}
