# Link Radar

Link Radar는 HTTP 응답 본문에서 endpoint URL을 추출하고, 빠르게 분석/재현할 수 있도록 도와주는 Burp Suite 익스텐션입니다.

## 주요 기능

- Proxy HTTP history에서 선택한 항목 기준으로 endpoint 수집
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

- Burp Suite (Montoya API 기반 익스텐션)
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

출력 산출물:

- `build/libs/link-radar-1.0.jar`

## Burp 로드 방법

1. Burp Suite를 실행합니다.
2. `Extensions` -> `Installed`로 이동합니다.
3. `Add`를 클릭합니다.
4. Extension type에서 `Java`를 선택합니다.
5. `build/libs/link-radar-1.0.jar`를 선택합니다.

   <img width="700" alt="image" src="https://github.com/user-attachments/assets/0033d1aa-d252-40ca-b1a6-dbafe32b789f" />

6. 정상 로드 후 `Link Radar` 탭을 확인합니다.

## 사용 방법

1. `Proxy` -> `HTTP history`에서 메시지 1개 이상을 선택합니다.
2. 우클릭 후 `Send to Link Radar`를 실행합니다 (`Ctrl+G`가 가능하면 단축키 사용).

   <img width="800" alt="image" src="https://github.com/user-attachments/assets/1b96b7e8-d1b4-4c30-84dc-df3b3cd07149" /><br/>
   OR
   <br/><img width="800" alt="image" src="https://github.com/user-attachments/assets/9f218fa9-628b-4d8d-b358-80405ad7ec7d" />

3. `Link Radar` 탭에서 수집된 endpoint를 확인합니다.
4. 필요하면 검색창으로 필터링합니다.
5. 행을 1개 이상 선택하고 `Send to Repeater`를 클릭합니다 (탭 내부 `Ctrl+R` 가능).

   <img width="800" alt="image" src="https://github.com/user-attachments/assets/2b722524-af05-4d0c-a49d-22ca88855be8" />

6. 필요하면 `Export CSV`로 내보냅니다.

## 프로젝트 명령어

- jar 빌드: `./gradlew clean jar`
- 회귀 테스트 실행: `./gradlew runTaskM05Regression`
