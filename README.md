# skAPI

**skAPI** 는 Skript 에서 **외부 API(HTTP) 를 쉽게 호출**하게 해주는 애드온 플러그인입니다.

DB 서버에 데이터 넣기·조회·삭제, AI 에게 질문하고 답변 받기, 날씨·검색·공공데이터 가져오기, 디스코드 웹훅 보내기처럼 **HTTP 로 할 수 있는 일은 전부** 스크립트 몇 줄로 할 수 있습니다.
특정 서비스 전용이 아니라 범용 HTTP 클라이언트라서, API 문서에 나온 주소·헤더·본문만 그대로 옮기면 어떤 API 든 쓸 수 있습니다.

```applescript
command /hello:
	trigger:
		set {_req} to new api request to "https://httpbin.org/get"
		send {_req} and store the response in {_res}      # 서버를 멈추지 않고 응답을 기다림
		send "상태 코드: %api status of {_res}%" to player   # 응답이 온 뒤 실행됨
```

---

## 목차

- [주요 기능](#주요-기능)
- [요구 사항](#요구-사항)
- [설치](#설치)
- [빠른 시작](#빠른-시작)
- [기본 개념](#기본-개념)
- [요청 만들기](#요청-만들기)
- [요청 설정하기](#요청-설정하기)
- [요청 보내기](#요청-보내기)
- [응답 다루기](#응답-다루기)
- [JSON 다루기](#json-다루기)
- [실전 예제](#실전-예제)
- [설정 파일](#설정-파일)
- [명령어와 권한](#명령어와-권한)
- [보안 주의사항](#보안-주의사항)
- [성능과 동작 방식](#성능과-동작-방식)
- [문제 해결](#문제-해결)
- [지원하지 않는 기능](#지원하지-않는-기능)
- [전체 문법 목록](#전체-문법-목록)
- [개발자용](#개발자용)
- [버전 기록](#버전-기록)
- [라이선스](#라이선스)

---

## 주요 기능

- **비동기(권장) / 동기** 두 가지 방식을 모두 지원합니다.
- 비동기로 보내도 **로컬 변수(`{_player}` 등)와 이벤트 값(`player` 등)이 그대로 유지**됩니다. 응답이 몇 초 뒤에 와도 요청한 유저에게 정확히 답을 돌려줄 수 있습니다.
- GET / POST / PUT / PATCH / DELETE 등 모든 HTTP 메서드, 헤더, 쿼리 파라미터, 본문, 타임아웃을 설정할 수 있습니다.
- **JSON 을 쉽게 다룹니다.** 경로로 값 꺼내기, 목록 변수로 펼치기, 목록 변수로 JSON 만들기, 경로로 값 수정하기를 지원합니다.
- 연결 실패·시간 초과 같은 네트워크 오류도 스크립트 오류 대신 **응답 객체**로 돌려주므로, `if` 하나로 처리할 수 있습니다.
- 요청이 아무리 많아도 서버(메인 스레드)를 멈추지 않습니다. HTTP 통신은 가상 스레드에서 처리합니다.

## 요구 사항

| 항목 | 버전 |
| --- | --- |
| 서버 | **Paper 26.2** |
| Java | **25** 이상 (Paper 26.x 의 요구 사항) |
| Skript | **2.16.2** 이상 |

> Spigot / Bukkit 은 지원하지 않습니다. Paper 를 사용하세요.

## 설치

1. [Releases](https://github.com/bosschoco/skAPI/releases) 에서 `skAPI-1.0.0.jar` 를 받거나, [직접 빌드](#개발자용)합니다.
2. 서버의 `plugins/` 폴더에 넣습니다. **Skript 도 함께 설치되어 있어야 합니다.**
3. 서버를 시작합니다. 콘솔에 `skAPI 1.0.0 활성화 완료` 가 보이면 성공입니다.
4. `plugins/skAPI/config.yml` 이 만들어집니다. 필요하면 수정한 뒤 `/skapi reload` 로 다시 불러옵니다.

> **업데이트할 때**: 서버를 끄고 jar 를 교체한 뒤 다시 켜세요. `/reload` 나 플러그인 매니저로 다시 불러오는 방식은 지원하지 않습니다. Skript 애드온은 서버가 켜질 때만 문법을 등록할 수 있기 때문입니다.

---

## 빠른 시작

`plugins/Skript/scripts/` 에 `api-test.sk` 파일을 만들고 아래 내용을 넣은 뒤 `/sk reload api-test` 를 입력하세요.

```applescript
command /apitest:
	trigger:
		# 1. 요청 만들기
		set {_req} to new api request to "https://httpbin.org/get"
		set api query "name" of {_req} to name of player

		# 2. 보내기 (비동기 - 권장)
		send {_req} and store the response in {_res}

		# 3. 응답 확인
		if {_res} is successful:
			send "&a성공! 보낸 이름: %json value "args.name" of {_res}%" to player
		else if api error of {_res} is set:
			send "&c연결 실패: %api error of {_res}%" to player
		else:
			send "&c서버 오류 (상태 코드 %api status of {_res}%)" to player
```

게임에서 `/apitest` 를 입력하면 `성공! 보낸 이름: (내 닉네임)` 이 출력됩니다.

---

## 기본 개념

skAPI 는 **요청(apirequest) → 보내기 → 응답(apiresponse)** 순서로 동작합니다.

```
 ┌────────────────────┐   send   ┌──────────────┐   응답 도착   ┌─────────────────────┐
 │ 요청 (apirequest)   │ ───────▶ │  외부 API 서버 │ ───────────▶ │ 응답 (apiresponse)   │
 │ 주소/메서드/헤더/본문 │          └──────────────┘              │ 상태코드/본문/헤더/오류 │
 └────────────────────┘                                       └─────────────────────┘
```

| 타입 | 설명 | 만드는 방법 |
| --- | --- | --- |
| `apirequest` | 보낼 요청. 주소, 메서드, 헤더, 쿼리, 본문, 타임아웃, 사용자 데이터를 담습니다. 보내기 전까지 얼마든지 수정할 수 있습니다. | `new api request to "주소"` |
| `apiresponse` | 받은 응답. 상태 코드, 본문, 헤더, 오류 메시지, 걸린 시간을 담습니다. 만들어진 뒤에는 바뀌지 않습니다. | `send` 로 요청을 보내면 생깁니다 |

- 요청은 **보내는 순간의 내용이 복사되어** 나갑니다. 보낸 뒤 원래 요청을 수정해도 이미 나간 요청에는 영향이 없습니다. 같은 요청 객체를 여러 번 보내도 됩니다.
- 두 타입은 함수 매개변수나 반환값으로도 쓸 수 있습니다. 예: `function f(r: apiresponse)`, `function g() :: apirequest`

---

## 요청 만들기

```applescript
set {_req} to new api request to "https://api.example.com/users"            # GET (기본)
set {_req} to new post api request to "https://api.example.com/users"       # POST
set {_req} to new delete api request to "https://api.example.com/users/1"   # DELETE
```

메서드 자리에는 `get`, `post`, `put`, `patch`, `delete`, `head`, `options` 를 쓸 수 있습니다. 생략하면 GET 입니다.

본문을 바로 넣을 수도 있습니다.

```applescript
set {_req} to new post api request to "https://api.example.com/users" with body {_json}
```

메서드를 변수로 정하고 싶으면 `with method` 를 씁니다.

```applescript
set {_req} to new api request to "https://api.example.com/users/1" with method "PATCH"
set {_req} to new api request to "https://api.example.com/users/1" with method {_method} and body {_json}
```

> `a`, `new`, `the` 는 생략해도 됩니다. `api request to ...`, `post api request for ...` 처럼 써도 같습니다.

---

## 요청 설정하기

요청을 만든 뒤 보내기 전에 아래 항목들을 설정할 수 있습니다.

### 헤더

인증 토큰이나 API 키는 보통 헤더로 보냅니다. 헤더 이름은 대소문자를 구분하지 않습니다.

```applescript
set api header "Authorization" of {_req} to "Bearer 토큰"
set api header "x-api-key" of {_req} to "키"
set api header "Accept" of {_req} to "application/json"
delete api header "Accept" of {_req}          # 헤더 지우기
```

- **User-Agent** 를 넣지 않으면 `config.yml` 의 `http.user-agent` 값(기본 `skAPI`)이 들어갑니다.
- **Content-Type** 을 넣지 않으면 본문을 보고 자동으로 정합니다. ([본문](#본문) 참고)
- `Connection`, `Content-Length`, `Expect`, `Host`, `Upgrade` 헤더는 자바 HTTP 클라이언트가 직접 관리하므로, 설정해도 무시됩니다.

### 쿼리 파라미터

주소 뒤의 `?이름=값&...` 부분입니다. **값이 자동으로 URL 인코딩**되므로 한글이나 공백, `&` 같은 특수문자를 그대로 넣으면 됩니다.

```applescript
set {_req} to new api request to "https://api.example.com/search"
set api query "q" of {_req} to "마인크래프트 서버"
set api query "page" of {_req} to 2               # 숫자도 됩니다
delete api query "page" of {_req}
# 실제 주소: https://api.example.com/search?q=%EB%A7%88...+%EC%84%9C%EB%B2%84
```

- 주소에 이미 `?lang=ko` 처럼 쿼리가 있으면 그 뒤에 `&` 로 이어 붙습니다.
- 주소 문자열에 직접 쓰고 싶으면 `url encoded` 를 사용하세요. ([도우미](#직접-조립할-때-쓰는-도우미) 참고)

### 본문

POST / PUT / PATCH 요청에서 서버로 보낼 내용입니다.

```applescript
set api body of {_req} to json of {_data::*}        # JSON (가장 많이 씀)
set api body of {_req} to "name=steve&level=3"      # 폼 형식
set api body of {_req} to "그냥 글자"                 # 텍스트
delete api body of {_req}                           # 본문 지우기
```

Content-Type 헤더를 직접 넣지 않았다면 본문을 보고 이렇게 정합니다.

| 본문 모양 | 자동 Content-Type |
| --- | --- |
| `{` 또는 `[` 로 시작 | `application/json; charset=utf-8` |
| `a=b&c=d` 처럼 공백·줄바꿈 없이 `=` 포함 | `application/x-www-form-urlencoded; charset=utf-8` |
| 그 외 | `text/plain; charset=utf-8` |

본문은 항상 UTF-8 로 보냅니다. JSON 을 만드는 방법은 [JSON 다루기](#json-다루기)에 자세히 있습니다.

### 메서드와 URL

```applescript
set api method of {_req} to "PUT"
set api url of {_req} to "https://api.example.com/v2/users"
send "%api method of {_req}% %api url of {_req}%" to player
```

> 요청의 `api url` 은 `api query` 로 붙인 쿼리를 **뺀** 주소입니다. 실제로 나간 최종 주소는 응답의 `api url` 로 확인하세요.

### 타임아웃

응답을 기다리는 최대 시간입니다. 이 시간이 지나면 `응답 시간 초과 (timeout)` 오류가 담긴 응답이 돌아옵니다.

```applescript
set api timeout of {_req} to 60 seconds      # AI 처럼 오래 걸리는 API 는 넉넉하게
reset api timeout of {_req}                  # config.yml 의 기본값(15초)으로 되돌리기
send "타임아웃: %api timeout of {_req}%" to player
```

- 설정하지 않으면 `config.yml` 의 `http.default-timeout` (기본 15초)이 적용됩니다.
- 서버에 **연결**되기까지 기다리는 시간은 따로 `http.connect-timeout` (기본 10초)으로 정합니다.

### api data

요청에 **아무 값이나 이름을 붙여 저장**해 둘 수 있습니다. 서버로 전송되지는 않고, 그 요청의 **응답에서 같은 이름으로 다시 꺼낼 수 있습니다.**

```applescript
set api data "asker" of {_req} to player
set api data "question" of {_req} to arg-1
send {_req} and store the response in {_res}
send "'%api data "question" of {_res}%' 의 답변이 왔어요" to (api data "asker" of {_res})
```

- 이름은 대소문자를 구분하지 않습니다.
- 플레이어, 숫자, 문자열, 위치 등 어떤 값이든 넣을 수 있습니다.
- skAPI 는 로컬 변수를 자동으로 보존하므로 보통은 필요 없습니다. **응답 객체 하나만 함수나 전역 변수로 넘길 때** 함께 들고 다닐 값을 넣어두면 편합니다.

---

## 요청 보내기

### 네 가지 방식 한눈에 보기

| 방식 | 문법 | 서버가 멈추나요 | 아래 줄은 언제 실행되나요 | 응답 받기 |
| --- | --- | --- | --- | --- |
| **대기형 비동기 (기본·권장)** | `send {_req} and store the response in {_res}` | 아니요 | 응답이 온 **뒤** | O |
| 콜백형 비동기 | `send {_req} async:` + 들여쓴 코드 | 아니요 | **바로** (섹션 안만 응답 뒤 실행) | O |
| 백그라운드 | `send {_req} async` | 아니요 | **바로** | X |
| 동기 | `send {_req} sync and store the response in {_res}` | **예** | 응답이 온 뒤 | O |

- `async` / `sync` 를 생략하면 **비동기**입니다. `asynchronously` / `synchronously` 로 길게 써도 됩니다.
- `store` 대신 `save`, `put` 을, `in` 대신 `into`, `to` 를 써도 됩니다. 예: `send {_req} and save the response to {_res}`

### 대기형 비동기 (권장)

가장 많이 쓰는 방식입니다. 요청은 다른 스레드에서 보내고, **응답이 오면 다음 줄부터 이어서** 실행합니다.
기다리는 동안 이 트리거만 잠시 멈출 뿐, 서버와 다른 스크립트는 정상적으로 돌아갑니다. Skript 의 `wait 1 second` 와 비슷하다고 생각하면 됩니다.

```applescript
command /myinfo:
	trigger:
		set {_name} to name of player
		set {_req} to new api request to "https://api.example.com/players/%uuid of player%"
		send {_req} and store the response in {_res}
		# ↓ 여기부터는 응답이 도착한 뒤 실행. {_name} 과 player 가 그대로 남아있음
		send "%{_name}% 님의 레벨: %json value "level" of {_res}%" to player
```

응답을 변수에 넣지 않고 기다리기만 할 수도 있습니다. 응답은 `last api response` 로 꺼냅니다.

```applescript
send {_req} and wait
send api body of last api response to player
```

### 콜백형 비동기

`send ... async` 뒤에 `:` 를 붙이고 들여쓴 코드를 적습니다. **섹션 안의 코드만 응답이 왔을 때 실행**되고, 섹션 **아래** 코드는 기다리지 않고 바로 실행됩니다.

```applescript
command /weather <text>:
	trigger:
		set {_city} to arg-1
		set {_req} to new api request to "https://wttr.in/%url encoded {_city}%"
		set api query "format" of {_req} to "3"
		send {_req} async:
			# 응답이 왔을 때 실행. 밖에서 만든 {_city} 와 player 를 그대로 사용 가능
			send "&b%{_city}% 날씨: &f%api body of last api response%" to player
		send "&7날씨를 조회하는 중..." to player     # ← 응답을 기다리지 않고 먼저 출력됨
```

섹션 안에서 응답을 변수로 받고 싶으면 `and store the response in` 과 함께 씁니다.

```applescript
send {_req} and store the response in {_res}:
	send "응답 코드: %api status of {_res}%" to player
```

### 백그라운드

응답이 필요 없을 때 씁니다. 보내기만 하고 바로 다음 줄로 넘어갑니다. 로그 전송, 웹훅 알림 등에 좋습니다.

```applescript
send {_req} asynchronously
```

### 동기

**응답이 올 때까지 서버 전체가 멈춥니다.** 응답이 1초 걸리면 그동안 모든 플레이어가 렉을 느낍니다.
같은 PC 에 있는 아주 빠른 API 처럼, 꼭 그 자리에서 결과가 필요할 때만 쓰세요.

```applescript
send {_req} synchronously and store the response in {_res}

# 표현식 형태 (쓸 때마다 요청이 새로 나감)
set {_res} to sync api response of {_req}
```

서버가 오래 멈추지 않도록 다음 장치가 있습니다.

- 메인 스레드에서 실행되면, 요청 타임아웃이 길게 잡혀 있어도 `sync.max-main-thread-timeout` (기본 5초)까지만 기다립니다.
- 메인 스레드에서 실행되면 콘솔에 경고가 출력됩니다. (1분에 최대 1번, `sync.warn-on-main-thread` 로 끌 수 있음)

> 동기 방식에는 섹션(콜백)을 붙일 수 없습니다. 붙이면 스크립트를 불러올 때 오류가 납니다.

### 어떤 방식을 써야 하나요

- **고민되면 대기형 비동기**를 쓰세요. 코드가 위에서 아래로 읽히고, 서버도 멈추지 않습니다.
- 요청을 보내 놓고 **다른 일을 먼저 해야 하면** 콜백형을 쓰세요. 예: "조회 중..." 메시지를 먼저 띄우기, 여러 API 를 동시에 호출하기.
- 결과가 필요 없으면 백그라운드를 쓰세요.
- 동기는 웬만하면 쓰지 마세요.

여러 API 를 **동시에** 호출하고 싶을 때는 콜백형을 여러 개 쓰면 됩니다.

```applescript
send {_reqA} async:
	send "A 결과: %api status of last api response%" to player
send {_reqB} async:
	send "B 결과: %api status of last api response%" to player
# A 와 B 가 동시에 나가고, 먼저 온 응답부터 처리됨
```

### 비동기에서 변수가 유지되는 원리

Skript 는 트리거 실행이 끝나면 그 이벤트의 **로컬 변수를 지웁니다.** 그래서 보통의 비동기 처리에서는 응답이 왔을 때 `{_player}` 같은 값이 이미 사라져 있어서, 요청한 유저에게 답을 돌려줄 수 없습니다.

skAPI 는 Skript 의 `wait` 와 같은 방식으로 이 문제를 해결합니다.

1. 요청을 보내는 순간, 현재 로컬 변수 묶음을 이벤트에서 **떼어내 따로 보관**합니다. (콜백형은 **복사본**을 보관)
2. 응답이 오면 **메인 스레드**에서 보관한 변수를 다시 붙입니다.
3. 다음 줄(또는 콜백 섹션)부터 이어서 실행합니다.

그래서 별도 저장 없이도 `{_player}`, 목록 변수 `{_list::*}`, 이벤트 값 `player` / `event-block` 등을 응답 뒤에 그대로 쓸 수 있습니다.
응답 뒤의 코드는 **항상 메인 스레드**에서 실행되므로, 블록 설치·아이템 지급 같은 작업도 안전하게 할 수 있습니다.

### 콜백 섹션의 변수 규칙

콜백 섹션 안에서는 **요청을 보낸 시점의 로컬 변수 복사본**을 씁니다.

| 상황 | 결과 |
| --- | --- |
| `send` 위에서 만든 로컬 변수를 섹션 안에서 사용 | ✅ 사용 가능 |
| `send` 아래에서 바꾼 값 | ❌ 섹션 안에는 반영되지 않음 (보낼 때 값이 고정됨) |
| `send` 아래에서 처음 만든 변수 | ❌ 섹션 안에서는 비어 있음 |
| 섹션 안에서 바꾼 로컬 변수 | ❌ 섹션 밖에는 반영되지 않음 |
| 전역 변수 (`{coins::%uuid of player%}` 등) | ✅ 복사가 아니라 항상 같은 값을 공유 |
| 섹션 안에서 `wait` 사용 | ✅ 가능 |

섹션 안팎이 값을 주고받아야 하면 전역 변수를 쓰거나 대기형 비동기를 쓰세요.

### 반복문과 함수 안에서 쓰기

**반복문 안**에서 대기형을 쓰면 요청이 **하나씩 차례대로** 나갑니다.

```applescript
loop 3 times:
	set {_req} to new api request to "https://api.example.com/items/%loop-value%"
	send {_req} and store the response in {_r}
	send "%loop-value%번: %json value "name" of {_r}%" to player
```

**함수 안**에서 대기형을 쓰면 Skript 의 `wait` 처럼, 함수를 부른 쪽은 기다리지 않고 바로 다음 줄로 넘어갑니다. 함수 안의 나머지 코드는 응답이 온 뒤 실행됩니다.

```applescript
function saveScore(p: player, score: number):
	set {_req} to new post api request to "https://api.example.com/scores" with body "{""score"": %{_score}%}"
	send {_req} and store the response in {_res}
	if {_res} is successful:
		send "&a점수가 저장되었습니다." to {_p}
```

> 값을 **반환(return)하는 함수**에서는 대기형을 쓸 수 없습니다. 함수가 이미 끝난 뒤라 돌려줄 수 없기 때문에 Skript 가 오류를 띄웁니다. 결과를 꼭 돌려받아야 하면 함수 밖에서 요청을 보내거나, 결과를 전역 변수에 저장하세요.

> 대기형 비동기 뒤에서는 `cancel event` 를 쓸 수 없습니다. 이벤트가 이미 지나갔기 때문입니다. (Skript 의 `wait` 뒤와 같음) 이벤트를 취소해야 하면 `send` 보다 위에서 취소하세요.

---

## 응답 다루기

### 성공 여부 확인

```applescript
if {_res} is successful:          # 네트워크 오류 없이 200~299 상태 코드를 받았을 때
	send "성공!" to player
if {_res} is not successful:      # 네트워크 오류이거나, 200~299 가 아닌 상태 코드
	send "실패..." to player
```

### 응답 정보

| 문법 | 결과 | 설명 |
| --- | --- | --- |
| `api status of {_res}` | 숫자 | HTTP 상태 코드 (200, 404, 500 ...). 네트워크 오류로 응답을 못 받았으면 `0` |
| `api body of {_res}` | 문자열 | 응답 본문 그대로 |
| `api error of {_res}` | 문자열 | 네트워크 오류 메시지. **서버가 응답했다면(404, 500 포함) none** |
| `api header "이름" of {_res}` | 문자열 | 응답 헤더 (대소문자 구분 없음, 같은 이름이 여러 개면 `, ` 로 합침) |
| `api header names of {_res}` | 문자열 목록 | 모든 응답 헤더 이름 |
| `api response time of {_res}` | 시간 | 요청부터 응답까지 걸린 시간 (`api duration of` 도 같음) |
| `api url of {_res}` | 문자열 | 실제로 요청한 최종 주소 (쿼리 포함, 리다이렉트가 있었다면 마지막 주소) |
| `api data "이름" of {_res}` | 아무 값 | 요청에 붙여둔 [api data](#api-data) |
| `last api response` | 응답 | 현재 트리거에서 마지막으로 받은 응답 |

`api status of {_res}` 대신 `{_res}'s api status` 처럼 써도 됩니다.

### 추천 오류 처리 패턴

응답은 세 가지 경우로 나뉩니다. 순서대로 확인하면 됩니다.

```applescript
send {_req} and store the response in {_res}

if api error of {_res} is set:
	# 1. 서버에 닿지도 못함 (인터넷 문제, 주소 오타, 시간 초과 등)
	send "&c연결 실패: %api error of {_res}%" to player
	stop
if {_res} is not successful:
	# 2. 서버가 거절함 (401 인증 실패, 404 없음, 429 너무 많은 요청, 500 서버 오류 등)
	send "&c요청 실패 (%api status of {_res}%): %json value "error.message" of {_res}%" to player
	stop

# 3. 성공
send "&a성공!" to player
```

> 서버가 보내는 오류 내용의 위치(`error.message` 등)는 API 마다 다릅니다. 해당 API 문서를 확인하세요.

### 네트워크 오류 메시지 목록

`api error of {_res}` 로 나올 수 있는 메시지입니다.

| 메시지 | 원인 |
| --- | --- |
| `응답 시간 초과 (timeout)` | `api timeout` 안에 응답이 오지 않음 |
| `연결 시간 초과 (connect timeout)` | `http.connect-timeout` 안에 서버에 연결되지 않음 |
| `연결 실패: ...` | 서버가 꺼져 있거나 포트가 막혀 있음 |
| `알 수 없는 호스트: ...` | 도메인 주소 오타, DNS 문제 |
| `잘못된 URL 입니다: ...` | 주소에 공백이나 허용되지 않는 문자가 있음 |
| `URL 은 http:// 또는 https:// 로 시작해야 합니다: ...` | 주소의 앞부분이 잘못됨 |
| `요청이 중단되었습니다` | 서버 종료 등으로 요청이 취소됨 |
| `SSLHandshakeException: ...` 등 | 그 밖의 오류. 오류 이름과 원래 메시지가 그대로 나옴 |

---

## JSON 다루기

대부분의 API 는 JSON 으로 데이터를 주고받습니다. skAPI 는 JSON 을 위한 기능을 기본으로 갖추고 있습니다.

### 경로 문법

JSON 안의 값 위치를 **경로**로 가리킵니다.

```json
{
  "user": { "name": "Steve", "level": 7 },
  "tags": ["a", "b", "c"],
  "items": [ { "id": 1, "name": "sword" }, { "id": 2, "name": "shield" } ],
  "content": [ { "type": "thinking" }, { "type": "text", "text": "안녕!" } ]
}
```

| 경로 | 결과 | 설명 |
| --- | --- | --- |
| `user.name` | `Steve` | `.` 으로 안쪽으로 들어감 |
| `tags[0]` | `a` | 배열 인덱스는 **0부터** |
| `tags.1` | `b` | 점 뒤에 숫자를 써도 인덱스 |
| `tags[-1]` | `c` | 음수는 뒤에서부터 (`-1` 은 마지막) |
| `items[1].name` | `shield` | 섞어서 사용 |
| `items[id=2].name` | `shield` | **조건 필터**: 배열에서 `id` 가 `2` 인 첫 요소 |
| `content[type=text].text` | `안녕!` | 필터 값에 따옴표를 써도 됨: `[type="text"]` |
| `['키.점있음']` | | 키에 `.` 같은 특수문자가 있을 때 따옴표로 감싸기 |
| `""` 또는 `$` | 전체 | 최상위 자체 |

### 값 꺼내기

```applescript
set {_name} to json value "user.name" of {_res}            # 응답에서 바로
set {_name} to json value "user.name" of {_jsonText}       # JSON 문자열에서도 가능
set {_tags::*} to json values "tags" of {_res}             # 배열의 각 요소를 목록으로
```

꺼낸 값의 종류는 이렇게 바뀝니다.

| JSON 값 | Skript 값 |
| --- | --- |
| `"글자"` | 문자열 |
| `7`, `1.0` | 정수 (`1.0` 처럼 소수점 아래가 0이면 정수) |
| `0.5` | 실수 |
| `true` / `false` | boolean |
| `null`, 없는 경로 | none |
| 객체 `{...}` / 배열 `[...]` | 그 부분의 **JSON 문자열** (다시 `json value` 로 꺼낼 수 있음) |

`json values` (복수형)는 배열이면 각 요소를, 객체면 각 값을 목록으로 줍니다. 배열 안이 객체라면 각각 JSON 문자열이 되므로, 반복문에서 다시 꺼내 쓰면 됩니다.

```applescript
loop json values "items" of {_res}:
	send "%json value "id" of loop-value%: %json value "name" of loop-value%" to player
```

> `json values "items[*]"` 처럼 끝에 `[*]` 를 붙여도 같은 결과입니다.

### 목록 변수로 펼치기

JSON 전체를 Skript 목록 변수에 한 번에 펼쳐 넣습니다. 값이 많을 때 편리합니다.

```applescript
load json {_res} into {_data::*}

send "%{_data::user::name}%"       # Steve
send "%{_data::user::level}%"      # 7
send "%{_data::tags::1}%"          # a   ← 목록 변수 인덱스는 1부터!
send "%{_data::items::2::name}%"   # shield
loop {_data::tags::*}:
	send "태그: %loop-value%"
```

- 객체의 키는 인덱스가 되고, **배열은 1, 2, 3...** 인덱스가 됩니다. (Skript 목록 관례)
- 중첩은 `{_data::user::name}` 처럼 `::` 로 이어집니다.
- `null` 값은 넣지 않습니다.
- 넣기 전에 **기존 목록 내용은 지워집니다.**
- 대상은 반드시 목록 변수(`{_이름::*}`)여야 합니다.
- `load` 대신 `parse` / `convert`, `into` 대신 `in` / `to` 를 써도 됩니다.

### 목록 변수로 JSON 만들기

`json of {_목록::*}` 는 목록 변수의 구조를 그대로 JSON 으로 바꿉니다.

```applescript
set {_body::model} to "my-model"
set {_body::max_tokens} to 1000
set {_body::stream} to false
set {_body::messages::1::role} to "user"
set {_body::messages::1::content} to arg-1
set {_json} to json of {_body::*}
# {"max_tokens":1000,"messages":[{"content":"...","role":"user"}],"model":"my-model","stream":false}
```

규칙은 다음과 같습니다.

- 인덱스가 **1, 2, 3... 빠짐없이 이어지면 배열**, 그 외에는 객체가 됩니다.
- `::` 로 중첩하면 중첩된 JSON 이 됩니다.
- 숫자는 숫자로, `true`/`false` 는 boolean 으로, 플레이어·위치 같은 값은 Skript 가 보여주는 글자로 들어갑니다.
- `5.0` 처럼 소수점 아래가 0인 실수는 `5` 로 보냅니다. (정수만 받는 API 가 많기 때문)
- 따옴표·줄바꿈 같은 특수문자는 **자동으로 이스케이프**되므로, 플레이어 채팅을 그대로 넣어도 JSON 이 깨지지 않습니다.
- 빈 목록은 `{}` 가 됩니다.
- `pretty json of {_body::*}` 는 사람이 보기 좋게 줄바꿈된 JSON 을 만듭니다.

목록 변수가 아닌 값을 넣으면 그 값 하나를 JSON 으로 바꿉니다. 예: `json of "안녕"` → `"안녕"`, `json of 1, 2 and 3` → `[1,2,3]`

### JSON 경로로 수정하기

JSON 문자열이 들어있는 변수나 요청 본문을, 경로로 지정해서 `set` / `add` / `delete` 할 수 있습니다.
중간 경로가 없으면 **자동으로 만들어집니다.** (`[숫자]` 앞이면 배열, 아니면 객체)

```applescript
set {_json} to "{}"
set json value "generationConfig.maxOutputTokens" of {_json} to 200
set json value "contents[0].parts[0].text" of {_json} to arg-1
add "태그1" to json values "tags" of {_json}          # 배열 끝에 추가 (배열이 없으면 만듦)
delete json value "generationConfig" of {_json}       # 지우기
# {"contents":[{"parts":[{"text":"..."}]}],"tags":["태그1"]}

# 요청 본문을 직접 수정
set json value "model" of api body of {_req} to "other-model"

# 필터로 찾은 요소 수정
set json value "items[id=2].name" of {_json} to "new name"
```

- 문자열 값은 **항상 JSON 문자열로** 들어갑니다. 플레이어 입력에 `{` 나 `[` 가 있어도 JSON 구조로 해석되지 않으므로 안전합니다.
- 여러 값을 `set` 하면 배열이 됩니다. 예: `set json value "ids" of {_json} to 1, 2 and 3` → `[1,2,3]`
- 배열 인덱스를 건너뛰어 설정하면 빈 칸은 `null` 로 채워집니다.
- 응답(`{_res}`)은 바꿀 수 없습니다. 바꾸려면 `set {_text} to api body of {_res}` 로 꺼낸 뒤 수정하세요.

### 대소문자 주의

Skript 는 기본 설정(`case-insensitive variables: true`)에서 **변수 이름을 소문자로 저장**합니다.
그래서 `json of` 로 만든 JSON 의 키는 모두 소문자가 됩니다.

```applescript
set {_body::maxTokens} to 100
send json of {_body::*}            # {"maxtokens":100}   ← 소문자가 됨!
```

| API 의 키 모양 | 추천 방법 |
| --- | --- |
| `max_tokens`, `model` 처럼 **소문자 / 밑줄** | `json of {_목록::*}` 를 그대로 사용 |
| `maxTokens`, `generationConfig` 처럼 **대문자 포함** | `set json value "maxTokens" of {_json} to ...` 사용 (대소문자 그대로 유지) |

두 방법을 섞어도 됩니다. `json of` 로 대부분 만들고, 대문자 키만 `set json value` 로 추가하세요.

### 직접 조립할 때 쓰는 도우미

| 문법 | 설명 | 예 |
| --- | --- | --- |
| `json escaped %문자열%` | JSON 문자열 안에 넣을 수 있게 `"`, `\`, 줄바꿈을 이스케이프 (앞뒤 따옴표는 안 붙음) | `a"b` → `a\"b` |
| `url encoded %문자열%` | URL 에 넣을 수 있게 한글·공백·특수문자를 인코딩 | `한글 a&b` → `%ED%95%9C%EA%B8%80+a%26b` |

```applescript
set {_json} to "{""text"": ""%json escaped message%""}"
set {_req} to new api request to "https://api.example.com/search?q=%url encoded {_keyword}%"
```

`json-escaped`, `url-encoded` 처럼 하이픈을 넣어 써도 됩니다. 보통은 `json of` 와 `api query` 를 쓰는 편이 더 쉽고 안전합니다.

---

## 실전 예제

아래 예제는 [examples/](examples/) 폴더에도 있습니다.

> **options 를 쓸 때 주의**: Skript 의 options 는 글자를 그대로 붙여넣습니다. 아래 예제처럼 값은 **따옴표 없이** 적고, 쓸 때 `"{@이름}"` 처럼 따옴표 안에 넣으세요.

### AI 에게 질문하기: Claude

[examples/02-ai-chat.sk](examples/02-ai-chat.sk) 에 전체 코드가 있습니다.

```applescript
options:
	claude-key: 여기에-API-키
	claude-model: claude-opus-5-5

command /ai <text>:
	cooldown: 5 seconds
	trigger:
		set {_player} to player
		send "&7[AI] 생각 중..." to {_player}

		set {_body::model} to "{@claude-model}"
		set {_body::max_tokens} to 4096
		set {_body::system} to "너는 마인크래프트 서버의 도우미야. 한국어로 3문장 이내로 짧게 답해."
		set {_body::messages::1::role} to "user"
		set {_body::messages::1::content} to arg-1
		set {_body::output_config::effort} to "low"
		set {_body::fallbacks} to "default"

		set {_req} to new post api request to "https://api.anthropic.com/v1/messages" with body json of {_body::*}
		set api header "x-api-key" of {_req} to "{@claude-key}"
		set api header "anthropic-version" of {_req} to "2023-06-01"
		set api header "anthropic-beta" of {_req} to "server-side-fallback-2026-07-01"
		set api timeout of {_req} to 90 seconds

		send {_req} and store the response in {_res}

		if api error of {_res} is set:
			send "&c[AI] 연결 실패: %api error of {_res}%" to {_player}
			stop
		if {_res} is not successful:
			send "&c[AI] 오류 %api status of {_res}%: %json value "error.message" of {_res}%" to {_player}
			stop
		if json value "stop_reason" of {_res} is "refusal":
			send "&c[AI] 이 질문에는 답변할 수 없어요." to {_player}
			stop
		send "&b[AI] &f%json value "content[type=text].text" of {_res}%" to {_player}
```

- 답변 블록이 첫 번째가 아닐 수 있으므로 `content[0].text` 대신 **`content[type=text].text`** 로 꺼냅니다.
- `fallbacks` 와 `anthropic-beta` 헤더는 안전 필터에 걸려 거절되면 다른 모델로 자동 재시도하게 하는 설정입니다.
- `effort` 를 `low` 로 두면 답변이 빨라집니다. 채팅처럼 짧은 답에 적합합니다.

### AI 에게 질문하기: OpenAI 호환 API

OpenAI 형식(`/v1/chat/completions`)을 따르는 서비스는 주소와 키만 바꿔서 같은 코드로 쓸 수 있습니다.

```applescript
options:
	gpt-url: https://api.openai.com/v1/chat/completions
	gpt-key: 여기에-API-키
	gpt-model: 사용할-모델-이름

command /gpt <text>:
	trigger:
		set {_player} to player
		set {_body::model} to "{@gpt-model}"
		set {_body::messages::1::role} to "system"
		set {_body::messages::1::content} to "한국어로 짧게 답해."
		set {_body::messages::2::role} to "user"
		set {_body::messages::2::content} to arg-1

		set {_req} to new post api request to "{@gpt-url}" with body json of {_body::*}
		set api header "Authorization" of {_req} to "Bearer {@gpt-key}"
		set api timeout of {_req} to 90 seconds
		send {_req} and store the response in {_res}

		if {_res} is successful:
			send "&b[GPT] &f%json value "choices[0].message.content" of {_res}%" to {_player}
		else:
			send "&c[GPT] 실패 (%api status of {_res}%)" to {_player}
```

### AI 에게 질문하기: Gemini (대문자 키 예시)

Gemini 는 `generationConfig`, `maxOutputTokens` 처럼 대문자가 섞인 키를 쓰므로 `set json value` 로 본문을 만듭니다.

```applescript
options:
	gemini-key: 여기에-API-키
	gemini-model: 사용할-모델-이름

command /gemini <text>:
	trigger:
		set {_player} to player
		set {_json} to "{}"
		set json value "contents[0].parts[0].text" of {_json} to arg-1
		set json value "generationConfig.maxOutputTokens" of {_json} to 500

		set {_req} to new post api request to "https://generativelanguage.googleapis.com/v1beta/models/{@gemini-model}:generateContent" with body {_json}
		set api header "x-goog-api-key" of {_req} to "{@gemini-key}"
		set api timeout of {_req} to 90 seconds
		send {_req} and store the response in {_res}

		if {_res} is successful:
			send "&b[Gemini] &f%json value "candidates[0].content.parts[0].text" of {_res}%" to {_player}
		else:
			send "&c[Gemini] 실패 (%api status of {_res}%)" to {_player}
```

### DB REST API: 입력, 조회, 삭제

자체 DB 서버나 REST API 를 제공하는 DB 서비스와 연동하는 예제입니다. 전체 코드는 [examples/03-database-rest.sk](examples/03-database-rest.sk) 에 있습니다.

```applescript
options:
	db: https://db.example.com/api
	db-token: 여기에-토큰

# 공통 설정을 함수로 묶어두면 편합니다
function dbRequest(method: text, path: text) :: apirequest:
	set {_req} to new api request to "{@db}%{_path}%" with method {_method}
	set api header "Authorization" of {_req} to "Bearer {@db-token}"
	return {_req}

# 입력 (POST) - 결과가 필요 없으면 백그라운드로
on join:
	set {_user::uuid} to uuid of player
	set {_user::name} to name of player
	set {_req} to dbRequest("POST", "/players")
	set api body of {_req} to json of {_user::*}
	send {_req} asynchronously

# 조회 (GET)
command /profile:
	trigger:
		set {_req} to dbRequest("GET", "/players/%uuid of player%")
		send {_req} and store the response in {_res}
		if api status of {_res} is 404:
			send "&c등록되지 않은 플레이어입니다." to player
			stop
		load json {_res} into {_data::*}
		send "&e%{_data::name}% &7레벨 %{_data::level}%" to player

# 삭제 (DELETE)
command /resetprofile:
	permission: op
	trigger:
		set {_req} to dbRequest("DELETE", "/players/%uuid of player%")
		send {_req} and store the response in {_res}
		if {_res} is successful:
			send "&a삭제되었습니다." to player
```

### 디스코드 웹훅

서버 이벤트를 디스코드 채널로 알립니다. 디스코드 채널 설정 → 연동 → 웹후크에서 주소를 만들 수 있습니다.

```applescript
options:
	webhook: https://discord.com/api/webhooks/여기에-웹훅-주소

on join:
	set {_msg::content} to "**%player%** 님이 서버에 접속했습니다."
	set {_req} to new post api request to "{@webhook}" with body json of {_msg::*}
	send {_req} asynchronously
```

### 공공데이터 API

공공데이터포털(data.go.kr) 같은 API 는 인증키를 쿼리 파라미터로 보냅니다.

```applescript
options:
	data-key: 여기에-Decoding-인증키

command /publicdata:
	trigger:
		set {_req} to new api request to "https://apis.data.go.kr/여기에-API-경로"
		set api query "serviceKey" of {_req} to "{@data-key}"
		set api query "pageNo" of {_req} to 1
		set api query "numOfRows" of {_req} to 10
		send {_req} and store the response in {_res}
		send api body of {_res} to player
```

> 공공데이터포털은 인증키를 **Encoding** / **Decoding** 두 가지로 줍니다. skAPI 는 쿼리 값을 자동으로 인코딩하므로 **Decoding 키**를 넣으세요. Encoding 키를 넣으면 두 번 인코딩되어 인증 오류가 납니다.
> 응답 형식(JSON / XML)을 정하는 파라미터 이름은 API 마다 다릅니다. skAPI 는 XML 을 경로로 꺼낼 수 없으므로, 가능하면 JSON 으로 받으세요.

---

## 설정 파일

`plugins/skAPI/config.yml`

| 키 | 기본값 | 설명 |
| --- | --- | --- |
| `http.default-timeout` | `15` | 요청 하나가 응답을 기다리는 기본 시간(초). 요청마다 `api timeout` 으로 바꿀 수 있습니다. |
| `http.connect-timeout` | `10` | 서버에 연결될 때까지 기다리는 시간(초) |
| `http.follow-redirects` | `true` | 3xx 리다이렉트를 자동으로 따라갈지 여부. https 에서 http 로 내려가는 리다이렉트는 따라가지 않습니다. |
| `http.user-agent` | `skAPI` | 요청에 User-Agent 헤더가 없을 때 넣을 값 |
| `sync.warn-on-main-thread` | `true` | 메인 스레드에서 동기 요청이 실행되면 콘솔에 경고 (1분에 최대 1번) |
| `sync.max-main-thread-timeout` | `5` | 메인 스레드 동기 요청의 최대 대기 시간(초). 서버가 오래 멈추는 것을 막습니다. |
| `debug` | `false` | 모든 요청과 응답(주소, 상태 코드, 걸린 시간)을 콘솔에 출력합니다. 문제를 찾을 때 켜세요. |

시간 값은 `0.5` 처럼 소수도 쓸 수 있고, 1초보다 작으면 1초로 맞춰집니다. 수정한 뒤 `/skapi reload` 를 입력하면 바로 적용됩니다. 이미 보낸 요청은 이전 설정대로 끝까지 처리됩니다.

## 명령어와 권한

| 명령어 | 설명 |
| --- | --- |
| `/skapi info` | 버전과 현재 설정 값 확인 |
| `/skapi reload` | `config.yml` 다시 불러오기 |

| 권한 | 설명 | 기본 |
| --- | --- | --- |
| `skapi.admin` | `/skapi` 명령어 사용 | OP |

---

## 보안 주의사항

- **API 키를 플레이어에게 보이지 마세요.** 키를 채팅으로 보내거나, 오류 메시지에 요청 헤더를 그대로 출력하지 마세요.
- 스크립트 파일에 적은 키는 서버 파일에 접근할 수 있는 사람이면 누구나 볼 수 있습니다. 서버 파일 접근 권한을 관리하세요. 키가 유출되면 해당 서비스에서 바로 폐기하고 새로 발급받으세요.
- **플레이어가 입력한 값으로 주소 전체를 만들지 마세요.** 플레이어가 서버 내부 주소(`http://127.0.0.1:...`, 공유기 관리 페이지 등)를 호출하게 만들 수 있습니다. 주소는 스크립트에 고정하고, 플레이어 입력은 `api query` 나 `url encoded` 로 **값 자리에만** 넣으세요.
- 플레이어 입력을 JSON 에 넣을 때는 `json of` 또는 `set json value` 를 쓰세요. 문자열을 직접 이어 붙이면 따옴표 때문에 JSON 이 깨지거나 원하지 않는 값이 끼어들 수 있습니다.
- AI 처럼 **유료 API** 는 `cooldown` 이나 사용 횟수 제한을 꼭 거세요. 플레이어가 명령어를 반복 입력하면 그만큼 비용이 나갑니다.

## 성능과 동작 방식

- HTTP 통신은 자바 내장 `HttpClient` 와 **가상 스레드**에서 처리합니다. 요청을 동시에 수십 개 보내도 서버 스레드를 막지 않습니다.
- 응답 뒤에 실행되는 스크립트 코드는 **다음 틱에 메인 스레드**에서 실행됩니다. 그래서 응답이 아무리 빨라도 최소 1틱(0.05초) 뒤에 이어집니다.
- https 주소는 서버가 지원하면 HTTP/2 를 쓰고, http 주소는 HTTP/1.1 을 씁니다.
- 응답 본문은 텍스트(UTF-8)로 받아 메모리에 보관합니다. 응답 JSON 은 처음 꺼낼 때 한 번만 해석하고 저장해두므로, 같은 응답에서 `json value` 를 여러 번 써도 빠릅니다.
- `apirequest` / `apiresponse` 는 디스크에 저장되지 않는 타입입니다. 전역 변수에 넣어도 서버를 재시작하면 사라집니다. 오래 보관할 값은 `api body`, `json value` 등으로 **필요한 값만 꺼내서** 저장하세요.
- 서버가 꺼지는 중에 도착한 응답은 처리하지 않고 버립니다.

---

## 문제 해결

<details>
<summary><b>Can't understand this expression 오류가 나요</b></summary>

- **options 따옴표 문제**가 가장 흔합니다. `url: "https://..."` 로 적고 `"{@url}/users"` 처럼 쓰면 따옴표가 겹칩니다. options 값은 따옴표 없이 적으세요.
- 문자열 안에서 따옴표를 쓰려면 `""` 두 번 적어야 합니다. 예: `"{""key"": ""value""}"`
- `%...%` 안에서는 따옴표를 그대로 써도 됩니다. 예: `"%json value "a.b" of {_res}%"`
</details>

<details>
<summary><b>"보낼 api 요청이 없습니다(none)" 오류가 나요</b></summary>

`send` 에 넣은 변수가 비어 있습니다. 바로 위 `set {_req} to new api request ...` 줄에 오류가 있지 않은지 콘솔의 스크립트 로드 메시지를 확인하세요.
</details>

<details>
<summary><b>send {_req} 를 했는데 요청이 안 나가고 채팅에 "api request GET ..." 이 떠요</b></summary>

`send {_req}` 만 쓰면 Skript 기본 기능인 **메시지 보내기**로 해석됩니다. 요청을 보내려면 반드시 방식을 적어주세요.

- `send {_req} async` (백그라운드)
- `send {_req} and store the response in {_res}` (대기형)
- `send {_req} async:` (콜백형)
</details>

<details>
<summary><b>응답 값이 none 으로 나와요</b></summary>

1. 먼저 `send api body of {_res} to player` 로 응답 본문 전체를 확인하세요.
2. 경로가 맞는지 확인하세요. 배열 인덱스는 **0부터**입니다. (`load json` 으로 펼친 목록 변수는 1부터)
3. `api error of {_res}` 에 오류가 있지 않은지 확인하세요.
4. `config.yml` 에서 `debug: true` 로 바꾸고 `/skapi reload` 하면 모든 요청과 응답이 콘솔에 출력됩니다.
</details>

<details>
<summary><b>JSON 키가 소문자로 바뀌어서 API 가 인식을 못 해요</b></summary>

Skript 가 변수 이름을 소문자로 저장하기 때문입니다. 대문자가 들어간 키는 `set json value "키이름" of {_json} to 값` 으로 넣으세요. → [대소문자 주의](#대소문자-주의)
</details>

<details>
<summary><b>AI 답변이 오기 전에 "응답 시간 초과" 가 떠요</b></summary>

`set api timeout of {_req} to 90 seconds` 처럼 타임아웃을 늘리세요. 기본값은 15초입니다. 답변이 길수록 오래 걸리므로, 최대 길이(`max_tokens` 등)를 줄이는 것도 방법입니다.
</details>

<details>
<summary><b>401 / 403 이 나와요</b></summary>

인증 문제입니다. 키가 맞는지, 헤더 이름(`Authorization`, `x-api-key` 등)과 형식(`Bearer 키`)이 API 문서와 같은지 확인하세요. 공공데이터포털이라면 Decoding 키를 쓰고 있는지 확인하세요.
</details>

<details>
<summary><b>429 가 나와요</b></summary>

요청을 너무 자주 보냈습니다. `cooldown` 을 걸거나 요청 횟수를 줄이세요. `api header "Retry-After" of {_res}` 에 다시 시도해도 되는 시간이 들어 있는 경우가 많습니다.
</details>

<details>
<summary><b>한글이 깨져요</b></summary>

skAPI 는 본문을 UTF-8 로 보내고, 응답은 서버가 알려준 문자셋(없으면 UTF-8)으로 읽습니다. 스크립트 파일(.sk)이 UTF-8 로 저장되어 있는지 확인하세요.
</details>

<details>
<summary><b>cancel event 를 쓰면 오류가 나요</b></summary>

대기형 비동기(`send ... and store the response in ...`) 뒤에서는 이벤트가 이미 지나간 상태라 취소할 수 없습니다. `cancel event` 를 `send` 보다 위로 옮기세요.
</details>

<details>
<summary><b>/reload 를 했더니 skAPI 가 안 돼요</b></summary>

Skript 애드온은 서버 시작 때만 문법을 등록할 수 있습니다. 서버를 완전히 재시작하세요. 스크립트만 다시 불러오는 `/sk reload` 는 괜찮습니다.
</details>

---

## 지원하지 않는 기능

현재 버전(1.0.0)에서 지원하지 않는 기능입니다.

| 기능 | 대안 |
| --- | --- |
| 스트리밍 응답 (SSE, AI 답변을 한 글자씩 받기) | 스트리밍 옵션을 끄고 완성된 답변을 한 번에 받으세요 |
| 바이너리 응답 (이미지, 파일 다운로드) | - |
| 파일 업로드 (multipart/form-data) | - |
| WebSocket, gRPC, TCP 통신 | - |
| Basic 인증 (Base64 변환) | API 키 / Bearer 토큰 방식을 쓰세요 |
| 쿠키 자동 저장 | `api header "Set-Cookie" of {_res}` 로 꺼내서 다음 요청의 `Cookie` 헤더에 넣으세요 |
| XML 경로 조회 | `api body` 로 텍스트는 받을 수 있습니다. 가능하면 JSON 으로 받으세요 |

---

## 전체 문법 목록

`[ ]` 는 생략 가능, `( | )` 는 그중 하나, `%타입%` 은 값이 들어가는 자리입니다.

### 타입

| 타입 | 설명 |
| --- | --- |
| `apirequest` | API 요청 |
| `apiresponse` | API 응답 |

### 요청 보내기 (효과 / 섹션)

```
send %apirequest% (async|sync)[hronously]
send %apirequest% [(async|sync)[hronously]] and (store|save|put) [the] [api] response (in|into|to) %변수%
send %apirequest% [(async|sync)[hronously]] and wait [for [the] [api] response]
```

- 끝에 `:` 를 붙이고 들여쓴 코드를 적으면 콜백형이 됩니다. (`sync` 와는 함께 쓸 수 없음)
- 섹션이 없을 때: `async` 만 쓰면 백그라운드, `store` / `wait` 가 있으면 대기형, `sync` 면 동기입니다.

### 요청 만들기 / 설정 (표현식)

```
[a] [new] [(get|post|put|patch|delete|head|options)] api request (to|for) %string% [with [the] body %string%]
[a] [new] api request (to|for) %string% (with|using) [the] method %string% [and [the] body %string%]

[the] api method of %apirequests%                       # set
[the] api url of %apirequests/apiresponses%             # set (요청만)
[the] api body of %apirequests/apiresponses%            # set, delete, reset (요청만)
[the] api timeout of %apirequests%                      # set, delete, reset
[the] api header %string% of %apirequest/apiresponse%   # set, delete, reset (요청만)
[the] api header names of %apirequests/apiresponses%
[the] api query [param[eter]] %string% of %apirequest%  # set, delete, reset
[the] api data %string% of %apirequest/apiresponse%     # set, delete, reset
```

모든 속성은 `%apirequest%'s api method` 처럼 소유격 형태로도 쓸 수 있습니다.

### 응답 확인 (표현식 / 조건)

```
[the] api status [code] of %apiresponses%
[the] api error [message] of %apiresponses%
[the] api (duration|response time) of %apiresponses%
[the] [last] api response
[the] sync[hronous] api response (of|for|from) %apirequest%

%apiresponses% (is|are) success[ful]
%apiresponses% (isn't|is not|aren't|are not) success[ful]
```

### JSON (표현식 / 효과)

```
[the] json (value|values) %string% (of|from|in) %string/apiresponse%   # set, add, delete, reset (문자열을 담은 곳일 때)
[pretty] json [string|text] (of|from) %objects%
(load|parse|convert) json [from] %string/apiresponse% (in|into|to) %목록 변수%
(json escaped|json-escaped) %string%
(url encoded|url-encoded) %string%
```

---

## 개발자용

### 빌드

```bash
./gradlew build
```

- 결과물: `build/libs/skAPI-1.0.0.jar`
- JDK 25 가 없으면 Gradle 이 자동으로 내려받습니다. (Gradle 실행 자체는 JDK 17 이상이면 됩니다)

### 테스트

- **단위 테스트** `./gradlew test`: JSON 변환·경로·수정, URL 조립, 로컬 HTTP 서버와의 실제 통신(타임아웃, 연결 실패, 동시 요청 병렬 처리)
- **인게임 테스트** [ingame-test/](ingame-test/README.md): 실제 Paper 서버에서 문법 전체를 확인하는 자동 테스트 82개

### 구조

```
src/main/java/io/github/bosschoco/skapi/
├── SkApiPlugin.java            플러그인 시작, Skript 애드온 등록
├── SkApiSettings.java          config.yml 값
├── SkApiCommand.java           /skapi 명령어
├── http/                       HTTP 통신 (Skript 와 무관)
│   ├── ApiRequest.java         요청 객체
│   ├── ApiResponse.java        응답 객체
│   └── HttpService.java        동기/비동기 전송
├── json/Json.java              JSON 경로 조회·수정·변환
└── skript/                     Skript 문법
    ├── SkApiModule.java        문법 등록 목록
    ├── Types.java              apirequest / apiresponse 타입
    ├── LastResponses.java      last api response 저장소
    └── elements/               각 문법 구현 (EffSecSendApiRequest 가 핵심)
```

---

## 버전 기록

### 1.0.0

첫 정식 버전입니다.

- Paper 26.2, Skript 2.16.2, Java 25 지원
- 요청 보내기: 대기형 비동기, 콜백형 비동기, 백그라운드, 동기
- 비동기에서 로컬 변수와 이벤트 값 보존
- 요청 설정: 메서드, URL, 헤더, 쿼리, 본문, 타임아웃, api data
- 응답 확인: 성공 여부, 상태 코드, 본문, 헤더, 오류 메시지, 걸린 시간
- JSON: 경로 조회(필터·음수 인덱스), 목록 변수로 펼치기, 목록 변수로 만들기, 경로로 수정하기
- 도우미: json escaped, url encoded
- config.yml, /skapi reload | info

---

## 라이선스

[MIT License](LICENSE)
