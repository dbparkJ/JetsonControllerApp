# Add List

> 2026-08-13 저장소 구현 기준이다. 아래 항목은 모두 소스와 문서 작업을 완료했으며, 실제 장비나 공인 서버에 재설치가 필요한 경우에는 배포 상태를 별도로 적었다.

1. ~~multi part인가 그방법으로 올리는건 별로인가? 그리고 계산도 하면서 업로드를 하는 병렬적인 프로세스로 진행할 순 없는건가? 그렇게 되면 서버단에서 또 바꿔야할 로직은 docs로 정리해둬~~
   - 처리: 단일 대형 요청 대신 재개 가능한 chunk 전송을 유지하고, 작은 파일은 `JETSONBATCH1` 형식으로 최대 32 MiB 또는 256개씩 묶었다. `deferred-v1`에서는 receiver가 수신과 동시에 SHA-256을 계산하므로 Jetson의 전체 사전 hash 단계를 제거하며, 구형 receiver에는 기존 전송으로 자동 fallback한다.
   - 처리: receiver capability, batch 멱등성, 중단 재개, reverse proxy와 배포 절차를 `UPLOAD_RECEIVER_AGENT_GUIDE.md`에 정리했다.
   - 배포 상태: 2026-08-13 공인 receiver를 최신 `main`으로 재설치했다. 공개 HTTPS에서 `deferred-v1` batch의 수신 hash 확정과 멱등 재전송, 기존 required-hash resumable 전송, 완료 처리와 HDD 객체 일치를 확인했다.

2. ~~Jetson에 업로드 하는 루트 위치도 /data 밑으로 잡아둔거 있자나 그쪽으로 사용할 수 있게 해줘~~
   - 처리: 기본 수집 root를 `/data/collections`로 바꾸고 pipeline 사용자 소유로 생성한다. DepthAI 설치 작업의 working directory와 쓰기 허용 경로도 이 root를 사용하므로 상대 `output_dir: image_records`는 `/data/collections/image_records`에 기록된다.
   - 처리: 기존 `~/26_camera_record`의 212 GB 데이터는 설정 전환 시 `Previous collected data` root로 남겨 앱에서 계속 탐색할 수 있게 했다.
   - 배포 상태: 현재 실행 중인 Jetson에는 `sudo backend/scripts/install.sh`와 DepthAI pipeline 재등록이 필요하다. 이 세션에서는 sudo 암호를 사용할 수 없어 실행 중 설정은 변경하지 않았다.

3. ~~자동화 실행중에 yaml 편집은 실제 텍스트 에디터를 이용하는게 아니라 키벨류 값을 가지고 벨류값만 바꿀 수 있게 디자인 해서 보여줘~~
   - 처리: backend가 YAML scalar를 key/value field로 파싱하는 `GET/PATCH /v1/pipelines/{id}/config/fields`를 추가했다. comment와 구조를 보존하고 revision 충돌, 잘못된 타입, alias와 과대 입력을 거절한다.
   - 처리: 앱은 boolean switch와 문자열·정수·실수 입력 필드만 보여 주며 변경된 value만 저장한다. raw YAML API는 기존 클라이언트 호환을 위해 유지하지만 새 화면에서는 사용하지 않는다.

4. ~~수집된 데이터를 앱에서 볼 수 있게 업로드 탭말고 하나 기능 추가해줘 사진은 디스플레이 화면에 맞게 볼 수 있게 해주고, 확대 축소가 가능한 기능정도를 추가해줘~~
   - 처리: 업로드 흐름과 분리된 `데이터` 화면에 `Jetson / 서버` 위치 탭을 추가했다. Jetson root와 폴더를 탐색하고 이미지 또는 UTF-8 파일을 바로 미리볼 수 있다.
   - 처리: 이미지는 화면 비율에 맞춰 표시하고 pinch/pan과 1~6배 확대·축소, 화면 맞춤 버튼을 제공한다. 큰 이미지는 최대 2048 px 미리보기로 sampling해 메모리 사용을 제한한다.

5. ~~시스템 지표는 1초에 한번씩 갱신하게 바꿔줘~~
   - 처리: 개요와 센서 화면이 보이는 동안 status를 1초마다 갱신하고, 화면이 background로 가면 polling을 중지하도록 lifecycle에 연결했다.

6. ~~알림에 업로드 시작과 종료 알림 추가해줘~~
   - 처리: upload job의 `QUEUED/SCANNING/UPLOADING` 진입과 `COMPLETED/FAILED/CANCELLED` 전이를 감지하는 background monitor와 전용 notification channel을 추가했다.
   - 처리: 알림 설정의 `업로드` 탭에서 시작 알림과 종료 알림을 각각 켜고 끌 수 있고, 잠깐 연결이 끊겨도 이전 상태를 유지해 종료 전이를 놓치지 않는다.

7. ~~현재 장치가 어떤 wifi와 연결되어있는지 알 수 있는 탭도 추가해주고 모바일과 같은 wifi가 붙어있다면 LAN으로 연결되게 해줘 그치만 사용자가 Wifi direct기능을 사용하고 싶을때는 다른 버튼을 눌러 direct로 붙일 수 있게해줘~~
   - 처리: 네트워크 설정에 `연결 상태 / Jetson Wi-Fi` 탭을 추가해 모바일 SSID, Jetson SSID, 동일 Wi-Fi 여부와 현재 전송 방식을 표시한다.
   - 처리: 등록된 Jetson이 mDNS로 발견되고 양쪽 SSID가 정확히 같을 때만 LAN을 자동 선택한다. Wi-Fi Direct는 자동 전환하지 않고 사용자가 `Wi-Fi Direct로 연결` 버튼을 눌렀을 때만 시작한다.

8. ~~pdf중에서 확장 및 제품 로드맵에 UX 리셋, A,B,C를 모두 추가해주고, 지금 확장하지 않았던 기능들 모두 추가로 확장해줘~~
   - 처리: `deep-research-report.md`에 UX Reset과 Release A/B/C의 상세 범위, API·저장 구조, 보안 원칙과 release gate를 추가했다. 아직 구현하지 않은 fleet, telemetry, sensor quality, diagnostics, identity/RBAC, relay, audit, 배포, lifecycle, backup/DR도 후속 범위로 명시했다.
   - 처리: 재현 가능한 Chromium renderer를 추가하고 `JetsonControllerApp UI_UX 재설계 및 제품 확장 실행 명세.pdf`를 17쪽 A4 문서로 다시 생성했다. 핵심 페이지를 이미지로 확인해 표 잘림과 겹침이 없음을 검증했다.

9. ~~서버에 업로드 된 사진들과 파일들도 앱에서 확인할 수 있게 해줘 그렇게 하기위해선 또 서버쪽에 구축해야할 docs를 정리해서 주면 서버 agent에게 일을 시킬께~~
   - 처리: receiver에 장비 token 소유권으로 제한된 완료 session, 가상 폴더, 12 MiB 미리보기 API를 추가했다. 폴더는 최대 500개 항목만 반환하고 파일은 regular file과 크기를 다시 검증한다.
   - 처리: Jetson이 server token을 보관한 채 library 요청을 proxy하고, 앱의 `서버 데이터` 화면에서 서버 선택, session pagination, 폴더 이동, 사진 확대와 text preview를 제공한다.
   - 처리: 서버 agent용 설치·검증·rollback 절차와 API 계약을 `UPLOAD_LIBRARY_SERVER_AGENT_GUIDE.md`에 정리했다.
   - 배포 상태: 공인 receiver에 library API를 배포했고 기존 완료 세션과 JPEG·CSV, 새 deferred upload의 폴더·미리보기 byte 및 MIME type을 HDD 원본과 대조했다. 2026-08-14 Jetson backend 1.6.0까지 재설치해 실제 HMAC proxy 응답 서명을 확인했다.

10. ~~로그는 실시간으로 변경되는 걸 볼 수 있게 하고, 자동 재시작 시 기존 로그를 저장한 뒤 새 로그로 기록하며 앱에서도 로그 파일을 확인할 수 있게 해줘~~
   - 처리: pipeline stdout/stderr를 journald와 `/var/log/jetson-pipelines/<id>/run-*.log`에 동시에 기록하고 systemd 실행마다 새 파일을 생성한다. pipeline별 최근 100개·합계 1 GiB·실행당 128 MiB 제한으로 오래된 로그를 자동 정리한다.
   - 처리: backend에 실행 파일 목록과 offset 기반 128 KiB 증분 읽기 API를 추가했다. 앱은 1초마다 최신 크기를 확인해 새 내용만 이어 붙이고, 자동 재시작의 새 파일을 따라가거나 보관된 이전 실행을 직접 선택할 수 있다.
   - 배포 상태: 2026-08-14 Jetson backend 1.6.0을 설치했다. 실행 중 파일 크기가 3초 사이 2511 B에서 2593 B로 증가하는 것과, 정상 재시작 전후 로그 2개가 각각 footer와 종료 코드를 보존하는 것, HMAC API 목록·구간 응답 서명을 실장비에서 확인했다. Samsung SM-S908N에 앱 1.8.0을 기존 자격증명을 보존해 덮어설치하고 실제 로그 파일 선택·본문 표시 화면을 확인했다.

## 검증

- Jetson backend unit/API 시험 70개 통과, 로컬 환경의 `python3-cryptography` 미설치 항목 2개는 skip했다.
- Upload receiver 시험 31개 통과.
- Android 단위 시험 42개와 `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`를 통과했다.
- 설치 shell script 구문 검사와 17쪽 A4 PDF 메타데이터·시각 검사를 통과했다.
