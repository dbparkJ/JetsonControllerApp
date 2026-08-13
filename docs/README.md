# Documentation Guide

처음에는 아래 순서로 읽는다.

1. [JETSON_BACKEND_SETUP.md](JETSON_BACKEND_SETUP.md): 전체 backend 구조, QR/BLE 인증, API, 저장소, 전원 제어, 설치와 점검
2. [MULTI_JETSON_PIPELINE_DEPLOYMENT.md](MULTI_JETSON_PIPELINE_DEPLOYMENT.md): 여러 Jetson 자동 설치, BlueZ 5.55, Git snapshot, virtualenv/main Python/config.yaml 선택과 부팅 자동 실행
3. [WIFI_DIRECT_SETUP.md](WIFI_DIRECT_SETUP.md): 공유기 없는 Android-Jetson 연결, 상태 확인과 장애 대응
4. [UPLOAD_RECEIVER_AGENT_GUIDE.md](UPLOAD_RECEIVER_AGENT_GUIDE.md): 외부 인터넷 업로드 수신 API 계약과 이 PC의 HDD 저장·공인 HTTPS·장치 target 배포 절차

개발 요청 체크리스트는 [addlist.md](addlist.md)에 기록하며, 구현과 검증이 끝난 항목은 체크 표시와 검증 근거를 함께 남긴다.
