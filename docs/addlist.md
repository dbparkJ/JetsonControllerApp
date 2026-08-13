# Add List

1. [x] 작은 파일이 많은 폴더의 업로드를 빠르게 처리한다.
   - `/home/jm/26_camera_record/depthai_refactored_ver2/image_records/2026-06-26_09-51-36_raw`의 10,042개 파일(29,127,864,491바이트) 분포를 기준으로 분석했다.
   - 최대 32 MiB 또는 256개 파일을 한 요청으로 보내는 batch protocol과 일괄 offset 조회를 구현했다.
   - 이전 receiver에는 기존 resumable upload로 자동 fallback한다.
   - receiver, reverse proxy, 저장 경로 설정과 재설치 절차는 `UPLOAD_RECEIVER_AGENT_GUIDE.md`에 기록했다.
   - 공인 receiver의 readiness는 확인했지만 원격 SSH 장비 키가 만료되어 이 작업 환경에서 라이브 receiver 재설치는 하지 못했다. 서버에서 최신 `main`을 받은 뒤 문서의 재설치 명령을 실행해야 batch 전송이 활성화된다.
2. [x] 설명용 문서만 남기도록 문서를 정리한다.
   - 재사용 가능한 설치·운영 가이드와 이 목록은 유지하고, 시점 의존적인 `CURRENT_SYSTEM_AUDIT.md`와 임시 명령 파일은 삭제했다.
3. [x] 자동 실행 작업의 HTTP 405 인증 오류를 수정하고 실제 휴대폰에서 디버깅한다.
   - Jetson backend `1.3.0`을 설치하고 HTTPS API와 systemd 서비스를 확인했다.
   - 기존 휴대폰 앱에서 `DepthAI Capture` 자동 실행 작업이 오류 없이 표시되는 것을 확인했다.
   - 앱 `1.6.0`을 빌드하고 별도 임시 패키지로 설치해 기기 계측 시험 8개를 통과했다.
   - 휴대폰에 설치된 `1.5.0`은 다른 서명 키로 빌드되어 있어 저장된 장비 인증 정보를 보존하기 위해 강제 교체하지 않았다.
4. [x] Jetson이 이미 연결한 Wi-Fi를 목록에서 구분하고 재연결하지 않는다.
   - 실제 Jetson과 연결된 휴대폰이 모두 `Geon` SSID를 사용하는 상태에서 확인했다.
   - 현재 SSID에는 체크 표시와 `현재 Jetson이 연결됨` 상태를 보여 주고 선택을 비활성화했다.
   - ViewModel 단위 시험과 실제 Android 기기 Compose 시험으로 재연결 폼이 열리지 않는 것을 확인했다.
5. [x] receiver 저장소를 `/data` 아래의 지정 경로로 복구한다.
   - 기본 저장 경로와 설치 문서를 `/data/server_storage/jetson-upload-receiver`로 통일하고 `/data/server_storage` mount 검사를 유지했다.
