# 작업 목록

- [x] 일반 Wi-Fi 연결 중 늦게 도착한 Wi-Fi Direct 응답이 연결 표시를 덮어쓰는 문제 수정
- [x] 데이터 수집 종료 후 센서 상태가 멈추는 문제 수정
- [x] 센서 상태 카메라 프리뷰를 캡처와 같은 최대 RGB 후보 해상도로 제공
- [x] 새 데이터 작업 폴더를 `/data/collections/yyyy-mm-dd-hh-mm-ss_raw` 형식으로 통일
- [x] 데이터 탭에서 폴더의 재귀 용량 표시
- [x] 메인 대시보드에서 닫은 동일 알람이 재접속 후 다시 나타나는 문제 수정
- [x] Fan 속도 탭의 무한 로딩 수정 및 Jetson `pwm_tach` RPM 지원
- [x] 업로드 큐에서 완료·실패·취소 기록 삭제 지원

## 적용 상태

- Android 앱 `1.13.0` (`versionCode 16`) 빌드 및 연결된 휴대폰 설치 완료
- Jetson backend와 `26_camera_record` 소스 구현 및 테스트 완료
- Jetson 시스템 서비스 반영은 관리자 권한 설치가 필요해 배포 대기
- 기존 `/data/collections/image_records` 데이터는 이동하거나 삭제하지 않으며, 새 수집부터 통일된 경로와 이름을 사용
