# Link Radar

Link Radar는 HTTP 응답 본문에서 endpoint URL을 추출하고, 빠르게 분석/재현할 수 있도록 도와주는 Burp Suite 익스텐션입니다.

## 주요 기능

- Proxy HTTP history에서 선택한 항목 기준 endpoint 수집
- 수집 트리거:
  - 우클릭 메뉴: `Send to Link Radar`
  - 단축키: `Ctrl+G`
- 다음 패턴에서 endpoint 후보 추출:
  - 절대 URL
  - 루트 상대 경로 (`/api/...`)
  - 상대 경로 (`../`, `../../`, `./`)
  - JavaScript 패턴 (`fetch`, `axios`, `XHR.open`, `new URL`, 프레임워크 라우트 시그니처)
- 정규화/정제:
  - HTML Entity 디코딩 (`&amp;`, `&#39;`, `&#x27;`, `&#47;` 등)
  - 동적 토큰 정규화 (`:id`, `${var}`, `[id]` -> `{...}`)
  - 정규식/노이즈 오탐 억제
- 검색어 기반 필터
- CSV 내보내기
- 선택 endpoint를 Repeater로 전송 (다중 선택 지원, 가능한 경우 원본 method/header 재사용, 탭 내 `Ctrl+R`)

## 호환성

- Burp Suite (Montoya API 기반)
- Java 17+
- 빌드 기준: `montoya-api:2026.2`

참고: 단축키 등록은 Burp 런타임 버전에 따라 달라질 수 있습니다. 단축키 등록이 불가능한 경우 우클릭 메뉴를 사용하세요.

## 빌드

```bash
./gradlew clean jar
```

Windows:

```powershell
.\gradlew.bat clean jar
```

생성 산출물:

- `build/libs/link-radar-0.1.0.jar`

## Burp 로드 방법

1. Burp Suite 실행
2. `Extensions` -> `Installed` 이동
3. `Add` 클릭
4. Extension type을 `Java`로 선택
5. `build/libs/link-radar-0.1.0.jar` 선택
6. 로드 후 `Link Radar` 탭 확인

## 사용 방법

1. `Proxy` -> `HTTP history`에서 메시지 1개 이상 선택
2. 우클릭 후 `Send to Link Radar` 실행 (`Ctrl+G` 가능 시 사용)
3. `Link Radar` 탭에서 수집 결과 확인
4. 필요 시 검색창으로 필터링
5. 행 1개 이상 선택 후 `Send to Repeater` 클릭 (탭 내부 `Ctrl+R` 가능)
6. 필요 시 `Export CSV`로 저장

## 프로젝트 명령어

- jar 빌드: `./gradlew clean jar`
- 회귀 테스트 실행: `./gradlew runTaskM05Regression`

