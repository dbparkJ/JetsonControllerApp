# 연결 진단 백엔드의 수동 배포

현재 장비에는 새 백엔드를 배포하지 않았다. 이 스크립트는 확인한 `/opt/jetson-control` 배포의 **6개 Python 파일만** 갱신하는 준비된 절차다. 기본 실행은 읽기 전용 사전 검사이며, `--apply`를 넣어야 설치와 API/P2P 서비스 재시작이 발생한다.

현재 자동 실행을 막는 조건은 다음과 같다.

- 기본 관리 경로가 `wlan0`이며 P2P가 사용하는 무선과 독립된 관리 경로는 확보하지 못했다. 현재 폰 접근은 무선 ADB이며 USB ADB도 확보하지 못했다.
- 현재 세션은 `/opt` 파일 쓰기와 비대화식 `sudo systemctl` 실행 권한이 없다. 일부 `sudo -l` 조회 성공은 비밀번호 없이 실제 실행할 수 있다는 뜻이 아니다.
- API/P2P는 실행 중이다. P2P는 DISCOVERABLE이고 DHCP가 비활성, RTK lease 파일은 없으며 활성 pipeline unit도 발견하지 못했다. 업로드 작업 저장소는 읽기 권한이 없어 운영 중인 업로드 유무가 미확정이다.

지금 터미널에 복사할 **읽기 전용 사전 검사**:

```bash
cd /home/jm/ControllerApp/JetsonControllerApp
sudo /usr/bin/python3 scripts/diagnosis/deploy_backend.py
```

sudo 비밀번호는 사용자가 자신의 터미널에 직접 입력한다. 스크립트나 로그에 저장하지 않는다. 사전 검사 성공은 USB/독립 관리 경로가 확보됐다는 의미가 아니다. 업로드/P2P 상태 저장소가 없거나 읽을 수 없을 때, 알 수 없는 업로드 상태, 유효한 RTK lease, 활성·전이 중 pipeline unit, 실제 P2P 그룹/연결 중 상태가 있으면 중단한다. `CANCELLED` 업로드 기록도 worker 종료를 증명하지 못하므로 중단하며, 검사를 피하려고 기록을 삭제하지 않는다. 별도 수동 수집 프로세스도 사용자가 운영 중인지 확인해야 한다.

**아래 설치 명령은 USB ADB와 실제 작동하는 독립 관리 경로를 확보하고, 장비 작업이 없는 중단 가능 시간을 확인한 뒤에만 사용한다. 지금 확인하지 못한 조건을 플래그로 대신하지 않는다.**

```bash
cd /home/jm/ControllerApp/JetsonControllerApp
sudo /usr/bin/python3 scripts/diagnosis/deploy_backend.py \
  --apply \
  --confirm-maintenance-idle \
  --confirm-usb-adb \
  --confirm-independent-management
```

`backend-deployment-manifest.json`에는 이번에 읽은 배포의 기존 해시와 설치할 저장소 파일 해시가 들어 있다. 이전 배포나 작업 소스가 달라졌다면 자동 중단한다. 해시를 임의로 갱신하거나 검사를 제거하지 말고 차이를 다시 검토한다. 현재 API의 `storage-roots.conf` drop-in은 `ReadWritePaths`만 담고 있음을 읽기 조회로 확인해 해시를 고정했다. 이 파일을 변경하지 않으며, drop-in 추가·변경/별도 환경 파일/사용자 지정 런타임 경로는 새 검토 없이 허용하지 않는다.

설치 대상은 `api.py`, `diagnostics.py`, `mobile_rtk.py`, `pipelines.py`, `status.py`, `wifi_direct.py`다. 새 API는 기존 배포에 없는 `StatusSnapshotService`와 worker thread에서 안전한 변경 작업을 위한 잠금이 필요해, 진단 관련 3개 파일만 덮으면 안 된다. 나머지 Python 파일과 requirements는 현재 저장소와 배포의 해시가 같았다. 기존 파일 5개는 각각 저장소 과거 파일과 정확히 일치했으며, 확인한 범위에서 미확인 현장 수정은 발견하지 못했다.

스크립트는 다음 순서로 실행한다.

1. 소스·기존 파일·변경하지 않는 의존 파일·requirements·unit 해시와 운영 상태를 검사한다.
2. `/opt/jetson-control/backups/connection-evidence-<UTC>-<random>`에 기존 5개 파일, 새 파일의 기존 부재, 파일 권한·소유자·mtime, 서비스 상태를 보관한다. 키·앱 데이터·설정·unit·의존 패키지는 변경하거나 복사하지 않는다.
3. 임시 Python 소스 디렉터리에서 정확한 설치 조합을 import 검사하고 운영 상태를 다시 확인한다.
4. P2P와 API를 중지한 후 6개 파일을 원자적으로 교체하고, 이전에 활성인 API/P2P만 다시 시작한다. NetworkManager, BLE, 센서·수집 서비스는 조작하지 않는다.
5. 새 PID와 검토한 파일 조합의 `buildId`, 원래 인증서를 신뢰하고 SAN을 확인한 TLS `/v1/hello` 응답, 각 프로세스의 새 진단 시작 기록을 확인한다. 시작 확인 전체 시간은 읽기 전용 하위 프로세스의 45초 제한으로 고정하며 결과를 보고 늘리지 않는다. 초과·중단 시 작업이 만든 검증 프로세스 그룹만 정리하며 정리 대기는 최대 4초다. 이는 인증된 폰 세션이나 T1~T9 실기 시험을 통과했다는 뜻이 아니다.
6. 교체 이후 실패하면 모든 백업 파일·메타데이터·원복 대상과 해시를 먼저 검증한 후 기존 파일과 원래 부재 상태를 복원하고 이전 서비스 상태로 복구한다. 배포 뒤 추가로 편집된 파일이나 손상된 백업은 서비스 중지 전에 거부한다. 백업의 `result.json`은 `DEPLOYED`, `FAILED_ROLLED_BACK`, `FAILED_ROLLBACK_INCOMPLETE`를 구분한다. 디스크 쓰기/서비스 명령 자체가 실패한 경우 자동 복구를 보장하지 않는다.

수동 원복이 필요하면 결과에 출력된 **실제 백업 경로**를 마지막 인자로 사용한다. 다음 경로를 예제 그대로 사용하지 않는다.

```bash
cd /home/jm/ControllerApp/JetsonControllerApp
sudo /usr/bin/python3 scripts/diagnosis/deploy_backend.py \
  --confirm-maintenance-idle \
  --confirm-usb-adb \
  --confirm-independent-management \
  --rollback /opt/jetson-control/backups/connection-evidence-ACTUAL_BACKUP_NAME
```

배포 후 장비 진단 파일 내보내기(배포에서 확인한 실제 경로 기준):

```bash
sudo /opt/jetson-control/venv/bin/python -c \
  'import sys; sys.path.insert(0,"/opt/jetson-control"); from jetson_control.diagnostics import main; raise SystemExit(main())' \
  --state-dir /var/lib/jetson-control \
  --output /tmp/jetson-connection-evidence.zip
```

ZIP은 기존 파일을 덮지 않고 0600으로 생성한다. 같은 이름이 있으면 새 이름을 정한다. root 소유 ZIP을 전달하려면 승인된 관리 경로에서 파일 권한을 유지하며 가져온다. 진단 전용 파일만 내보내며 설정·키·전체 journal·센서/파이프라인 내용은 포함하지 않는다.

검증 상태: 로컬 임시 디렉터리와 모의 서비스 명령으로 baseline 변경 차단, 알 수 없는 업로드/RTK 중단, 정확한 6개 파일 교체, 실패 후 파일/부재 복원과 관련 서비스 명령을 테스트했다. 실제 root 설치·서비스 중단/재시작·TLS 재시작 확인은 **NOT RUN**이다. 현재 실제 읽기 전용 사전 검사는 업로드 저장소 권한 부족으로 **BLOCKED**이며, 전체 배포 검증 완료로 표시하지 않는다.
