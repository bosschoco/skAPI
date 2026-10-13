# 인게임 자동 테스트

실제 Paper 서버 + Skript 에서 skAPI 문법이 제대로 동작하는지 확인하는 테스트입니다. (총 82개 항목)

## 준비

1. 테스트용 Paper 26.2 서버 폴더를 만들고 `paper-26.2-*.jar`, `plugins/Skript-2.16.2.jar`, `plugins/skAPI-*.jar` 를 넣습니다.
2. `scripts/` 안의 파일들을 서버의 `plugins/Skript/scripts/` 에 복사합니다.
3. 가짜 API 서버를 켭니다. (127.0.0.1:18080)

```bash
python test_api_server.py
```

## 실행

서버를 켜면 스크립트가 로드되고 5초 뒤 `/skapitest` 가 자동 실행되며, 끝나면 서버가 스스로 종료됩니다.
콘솔에서 `[SKAPI-TEST]` 로 시작하는 줄을 확인하세요.

```
[SKAPI-TEST] PASS T1 status
...
[SKAPI-TEST] SUMMARY pass=82 fail=0
```

- 서버가 이미 켜져 있다면 콘솔에서 `skapitest` 를 입력해도 됩니다. (끝나면 서버가 종료되니 주의)
- 테스트가 멈추더라도 180초 뒤에는 `SAFETY-STOP` 과 함께 서버가 꺼집니다.

## 로드 오류가 나와야 정상인 파일

일부러 틀린 문법을 넣은 파일입니다. 로드할 때 아래 오류가 나와야 정상입니다.

| 파일 | 나와야 하는 오류 |
| --- | --- |
| `skapi-parse-errors.sk` | skAPI 의 한국어 오류 2개 (동기 요청에 섹션, 목록 변수가 아닌 곳에 load json) |
| `skapi-negative.sk` | Skript 오류 2개 (대기형 뒤 `return`, 대기형 뒤 `cancel event`) |

## 테스트 항목

| 번호 | 내용 |
| --- | --- |
| T1 | 대기형 비동기: 로컬 변수 / 목록 변수 / 이벤트 값 유지 |
| T2 | 한글 쿼리, 요청·응답 헤더 |
| T3 | POST + `json of` 목록 변수 (따옴표 이스케이프) |
| T4, T5 | `load json`, `json values`, `[필드=값]` 필터, 음수 인덱스 |
| T6 ~ T8 | 404, 연결 실패, 타임아웃 |
| T9, T10 | 동기 방식(효과/표현식), `and wait` + `last api response` |
| T11, T12 | 콜백형: 밖의 변수 사용, 즉시 진행, 섹션 안팎 격리, 콜백 안 `wait` |
| T13 ~ T16 | 백그라운드, 반복문/함수 안 대기형, `api data` |
| T17 | JSON 경로 수정 (camelCase 유지), `api body` 직접 수정 |
| T18 ~ T21 | 리다이렉트, 텍스트 응답, Skript 기본 `send` 메시지와 충돌 없음, AI 응답 형태 |
| T22 | 콜백 안에서 대기형 중첩 |
| T23 | none 요청은 오류만 내고 계속 진행 |
| T24 | `url encoded`, `json escaped` |
| T25 | 소유격 형태(`{_res}'s api status`), `api duration`, `api header names`, 최종 URL |
| T26 | 헤더·쿼리·본문 삭제, 타임아웃 설정·초기화 |
| T27 | `json of` 글자 값 / boolean / 정수 실수 / pretty / 빈 목록 |
| T28 | JSON 경로 수정: 여러 값, 필터로 찾은 요소 |
| T29 | `send {_req}` 만 쓰면 요청 대신 메시지로 처리됨 |

README 의 코드 예제가 모두 오류 없이 로드되는지도 함께 확인했습니다. (README 의 `applescript` 코드 블록을 스크립트로 만들어 로드)
