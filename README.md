# skAPI

Skript 에서 **외부 API(HTTP) 호출**을 쉽게 해주는 애드온입니다.
DB API 로 데이터 입력·조회·삭제, AI API 질문/답변, 정보 검색 등 HTTP 로 할 수 있는 것은 전부 스크립트에서 할 수 있습니다.

- **비동기(권장) / 동기** 두 방식 모두 지원
- 비동기여도 **로컬 변수(`{_player}` 등)와 이벤트 값(`player`)이 그대로 유지** → 응답이 오면 요청한 유저에게 바로 메시지를 보낼 수 있음
- JSON 꺼내기 / 만들기 / 목록 변수로 펼치기 내장
- 네트워크 오류도 예외 대신 응답 객체로 돌려줘서 스크립트에서 처리하기 쉬움

## 요구 사항

| 항목 | 버전 |
| --- | --- |
| 서버 | Paper **26.2** |
| Java | **25** 이상 (Paper 26.x 요구 사항) |
| Skript | **2.16.2** 이상 |

## 설치

1. `skAPI-x.x.x.jar` 를 `plugins/` 폴더에 넣습니다. (Skript 도 함께 있어야 합니다)
2. 서버를 시작하면 `plugins/skAPI/config.yml` 이 생성됩니다.
3. 설정을 바꾼 뒤에는 `/skapi reload` 로 다시 불러올 수 있습니다. (권한 `skapi.admin`)

---

## 빠른 시작

```applescript
command /hello:
	trigger:
		set {_req} to new api request to "https://httpbin.org/get"
		send {_req} and store the response in {_res}      # ← 비동기 (권장)
		send "상태: %api status of {_res}%" to player       # ← 응답이 온 뒤 실행됨
```

## 처리 방식 4가지

| 방식 | 문법 | 동작 |
| --- | --- | --- |
| **대기형 비동기 (기본·권장)** | `send {_req} and store the response in {_res}` | 요청은 다른 스레드에서 보내고, 응답이 오면 **다음 줄부터 이어서** 실행. 서버는 멈추지 않음 |
| 콜백형 비동기 | `send {_req} async:` + 들여쓴 코드 | 섹션 **안**의 코드만 응답 후 실행. 섹션 **아래** 코드는 기다리지 않고 바로 실행 |
| 백그라운드 | `send {_req} async` | 보내기만 하고 응답은 무시 |
| 동기 | `send {_req} sync and store the response in {_res}` | 응답이 올 때까지 **서버 전체가 멈춤**. 꼭 필요할 때만 |

- `async` / `sync` 를 생략하면 비동기입니다. (`asynchronously`, `synchronously` 로 써도 됩니다)
- 응답을 변수에 받지 않고 `send {_req} and wait` 로 기다린 뒤 `last api response` 로 꺼낼 수도 있습니다.
- 동기 방식이 메인 스레드에서 실행되면, 타임아웃이 `config.yml` 의 `sync.max-main-thread-timeout` (기본 5초)보다 길게 잡혀 있어도 그 시간까지만 기다립니다.

### 비동기에서 변수가 유지되는 원리

Skript 는 트리거 실행이 끝나면 그 이벤트의 로컬 변수를 지웁니다. 그래서 일반적인 비동기 콜백에서는 응답이 왔을 때 `{_player}` 가 이미 사라져 있습니다.

skAPI 는 Skript 의 `wait` 와 같은 방식으로 동작합니다.

1. 요청을 보내는 순간 로컬 변수 묶음을 이벤트에서 떼어내 따로 보관합니다. (콜백형은 복사본을 보관)
2. 응답이 오면 **메인 스레드**에서 보관한 변수를 다시 붙입니다.
3. 다음 줄(또는 콜백 섹션)부터 이어서 실행합니다.

그래서 별도 저장 없이도 아래 코드가 그대로 동작합니다.

```applescript
command /ai <text>:
	trigger:
		set {_player} to player
		set {_question} to arg-1
		# ... AI API 요청 만들기 ...
		send {_req} and store the response in {_res}         # 몇 초 걸려도 서버는 안 멈춤
		send "Q: %{_question}%" to {_player}                   # {_player}, {_question} 그대로 있음
		send "A: %json value "content[type=text].text" of {_res}%" to {_player}
```

응답 객체만 다른 함수나 전역 변수로 넘겨야 할 때는 `api data` 로 값을 요청에 붙여서 함께 보낼 수 있습니다. ([예제 4](examples/04-callback-and-sync.sk) 참고)

> 함수 안에서 대기형 비동기를 쓰면 `wait` 와 마찬가지로 함수가 그 자리에서 먼저 반환됩니다. 값을 `return` 해야 하는 함수에서는 대기형 대신 콜백형을 쓰거나, 응답 처리를 함수 밖에서 하세요.

---

## 문법 정리

### 요청 만들기

```applescript
set {_req} to new api request to "https://api.example.com/users"                    # GET
set {_req} to new post api request to "https://..." with body {_json}                # POST + 본문
set {_req} to new api request to "https://..." with method "PATCH" and body {_json}  # 메서드 직접 지정
```

`get / post / put / patch / delete / head / options` 를 쓸 수 있습니다.

### 요청 설정

| 문법 | 설명 |
| --- | --- |
| `api header "이름" of {_req}` | 헤더 (set / delete) |
| `api query "이름" of {_req}` | 쿼리 파라미터. 한글·공백 자동 인코딩 (set / delete) |
| `api body of {_req}` | 본문 (set / delete). JSON 이면 `Content-Type` 자동 설정 |
| `api method of {_req}` | HTTP 메서드 (set) |
| `api url of {_req}` | URL (set) |
| `api timeout of {_req}` | 응답 대기 시간 (set / reset). 예: `60 seconds` |
| `api data "이름" of {_req}` | 요청에 붙여둘 임의의 값. 서버로 전송되지 않음. 응답에서도 꺼낼 수 있음 |

### 응답 확인

| 문법 | 설명 |
| --- | --- |
| `{_res} is successful` | 네트워크 오류 없이 2xx 응답을 받았는지 |
| `api status of {_res}` | 상태 코드 (네트워크 오류면 0) |
| `api body of {_res}` | 응답 본문 문자열 |
| `api error of {_res}` | 네트워크 오류 메시지 (연결 실패, 시간 초과 등). 서버가 응답했다면 none |
| `api header "이름" of {_res}` | 응답 헤더 |
| `api header names of {_res}` | 모든 응답 헤더 이름 |
| `api response time of {_res}` | 걸린 시간 |
| `api url of {_res}` | 최종 URL (리다이렉트 반영) |
| `last api response` | 현재 트리거에서 마지막으로 받은 응답 |
| `sync api response of {_req}` | 동기로 보내고 응답을 바로 반환 (사용할 때마다 요청이 나감) |

### JSON

```applescript
# 꺼내기 ─ 경로: a.b.c / list[0] / list[-1](마지막) / list[type=text](조건에 맞는 첫 요소)
set {_answer} to json value "choices[0].message.content" of {_res}
set {_text} to json value "content[type=text].text" of {_res}
set {_items::*} to json values "items[*]" of {_res}          # 배열의 각 요소

# 목록 변수로 펼치기
load json {_res} into {_data::*}                               # {_data::user::name}, {_data::tags::1} ...

# 목록 변수 → JSON 만들기 (1,2,3… 인덱스는 배열, 그 외는 객체)
set {_body::model} to "my-model"
set {_body::messages::1::role} to "user"
set {_body::messages::1::content} to arg-1
set {_json} to json of {_body::*}                              # pretty json of {_body::*} 도 가능

# JSON 문자열을 경로로 수정 (키 대소문자 유지)
set {_json} to "{}"
set json value "generationConfig.maxOutputTokens" of {_json} to 200
add "태그" to json values "tags" of {_json}
delete json value "generationConfig" of {_json}
set json value "model" of api body of {_req} to "other-model"  # 요청 본문도 바로 수정 가능

# 직접 조립할 때 쓰는 도우미
json escaped {_text}      # JSON 문자열 안에 넣을 수 있게 이스케이프
url encoded {_text}       # URL 에 넣을 수 있게 인코딩
```

> ⚠️ **키 대소문자 주의**: Skript 는 기본 설정에서 변수 이름의 대소문자를 구분하지 않아서 `{_body::maxTokens}` 를 `maxtokens` 로 저장합니다. 그래서 `json of {_목록::*}` 로 만든 JSON 의 키는 소문자가 됩니다.
> `max_tokens` 처럼 소문자 키를 쓰는 API 는 `json of` 를 그대로 쓰면 되고, camelCase 키가 필요한 API 는 `set json value "maxTokens" of {_json} to ...` 방식을 쓰세요.

JSON 숫자는 정수면 정수, 아니면 실수로 나옵니다. 객체/배열을 가리키면 그 부분의 JSON 문자열이 나옵니다. 사용자 입력 문자열은 항상 JSON 문자열 값으로 들어가므로, 채팅 내용을 그대로 넣어도 JSON 이 깨지지 않습니다.

---

## 예제

| 파일 | 내용 |
| --- | --- |
| [examples/01-basic.sk](examples/01-basic.sk) | 기본 GET 요청과 성공·실패 처리 |
| [examples/02-ai-chat.sk](examples/02-ai-chat.sk) | `/ai <질문>`: AI(Claude API)에게 묻고 질문한 플레이어에게 답변 |
| [examples/03-database-rest.sk](examples/03-database-rest.sk) | DB REST API 입력(POST)·조회(GET)·목록·삭제(DELETE) |
| [examples/04-callback-and-sync.sk](examples/04-callback-and-sync.sk) | 콜백형 비동기, 동기 방식, `api data` |

## 설정 (config.yml)

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `http.default-timeout` | 15 | 요청 기본 타임아웃(초) |
| `http.connect-timeout` | 10 | 연결 타임아웃(초) |
| `http.follow-redirects` | true | 리다이렉트 자동 추적 |
| `http.user-agent` | skAPI | 기본 User-Agent |
| `sync.warn-on-main-thread` | true | 메인 스레드 동기 요청 시 콘솔 경고 (1분에 1회) |
| `sync.max-main-thread-timeout` | 5 | 메인 스레드 동기 요청 타임아웃 상한(초) |
| `debug` | false | 요청·응답 로그 출력 |

## 빌드

```bash
./gradlew build
```

결과물은 `build/libs/skAPI-<버전>.jar` 입니다. JDK 25 가 없으면 Gradle 이 자동으로 내려받습니다.

## 테스트

- 단위 테스트: `./gradlew test` (JSON 변환, URL 조립, 로컬 HTTP 서버와 실제 통신)
- 인게임 테스트: 실제 Paper 서버에서 문법 전체를 확인하는 자동 테스트 64개 → [ingame-test/](ingame-test/README.md)

## 자주 하는 실수

- **options 값의 따옴표**: Skript 의 options 는 글자를 그대로 붙여넣습니다. `base: "https://..."` 로 적고 `"{@base}/users"` 처럼 문자열 안에서 쓰면 따옴표가 겹쳐 오류가 납니다. 문자열 안에서 쓸 값은 `base: https://...` 처럼 따옴표 없이 적으세요.
