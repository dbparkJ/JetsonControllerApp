# Rollback·진단 절차

장애 대응은 먼저 실제 target과 현재 revision을 읽기 전용으로 확인하고, 증거를 보존한 뒤, 승인된 target에서만 변경한다. rollback은 수집 데이터·receiver object·credential을 삭제하는 절차가 아니다.

## 1. 공통 진단 Bundle

기록할 항목:

- incident 시작 시각/timezone, operator, release commit, dirty 여부
- Android version/APK hash와 비식별 device reference
- Jetson model/OS/kernel/backend revision과 service 상태
- pipeline ID/run ID/log ID/source revision/config SHA-256
- receiver environment/revision/session ID/receipt, HTTP status와 request reference
- 실제 transport, Wi-Fi chipset/driver, P2P group 존재 여부

민감정보를 수집하지 않는다. QR URI, HMAC secret, bearer token, TLS private key, NTRIP credential, 원문 employee identifier는 bundle에 넣지 않는다.

읽기 전용 시작점:

```bash
git status --short --branch
adb devices -l
systemctl status jetson-control-api.service jetson-wifi-direct.service --no-pager
journalctl -u jetson-control-api.service -u jetson-wifi-direct.service --since '<incident-start>' --no-pager
```

`adb`와 Jetson 명령은 연결 target을 먼저 식별한다. 실제 장치가 없으면 실행하지 않는다. 앱/Jetson 연결 상관관계는 `docs/DIAGNOSTICS.md`의 bounded export 절차를 사용한다.

## 2. 빠른 분류

| 증상 | 먼저 확인 | 금지할 추정 |
|---|---|---|
| 앱 offline | transport state, deviceRef, 마지막 관찰, Jetson service | 수집이 중단됐다고 추정하지 않음 |
| start/stop timeout | target GET pipeline, activeRunId/execution, request reference | mutation 자동 재전송 안 함 |
| Wi-Fi Direct 장애 | group 존재, GO address, manager/driver 상태, LAN 가능 여부 | API 실패만으로 group loss 단정 안 함 |
| upload 실패 | job ID/state/offset, Jetson 원본, receiver session/receipt | 검증 전 원본 삭제 안 함 |
| server 조회 거절 | expected environment, employee status/role/project grant | cache로 401/403/TLS mismatch 우회 안 함 |
| storage 문제 | configured root, probe evidence, available/required bytes | `not_configured`를 pass로 표시 안 함 |
| RTK/센서 저하 | raw sample state, timing, problem intervals | 승인 없는 threshold로 fail/pass 생성 안 함 |

## 3. Android Rollback

사전 조건은 동일 application ID와 서명으로 만든 이전 승인 APK, 그 SHA-256, 명시적으로 선택한 Android serial, Android package manager가 허용하는 versionCode rollback 계획이다.

1. 현재 APK/version과 앱 진단 bundle을 보존한다.
2. 진행 중 mutation과 실제 Jetson pipeline 상태를 확인한다.
3. 승인된 배포 도구로 사용자 data를 보존하는 in-place 설치를 수행한다. 같은 versionCode 또는 플랫폼이 허용하는 rollback artifact인지 먼저 확인한다.
4. 등록 credential, 선택 장비, server profile/cache migration을 읽어 확인한다.
5. 연결과 read-only status부터 검증하고 mutation은 별도 승인된 smoke plan에서 수행한다.

낮은 versionCode, 서명 불일치, schema 비호환으로 package manager가 downgrade를 허용하지 않으면 설치를 중단하고 forward fix 또는 승인된 migration을 결정한다. 우회 목적으로 app uninstall, app data clear, Keystore/DataStore 삭제를 하지 않는다.

## 4. Jetson Backend Rollback

1. current backend revision, unit state, 실행 중 pipeline/upload/relay를 읽어 기록한다.
2. 실행 중 수집과 upload가 있으면 rollback 영향과 중단 권한을 운영 owner에게 확인한다.
3. 실제 배포 manifest와 backup이 current release에서 변경·배포한 package module, runner/registrar, route/quality recorder, scripts, systemd unit, protected config를 모두 포함하는지 대조한다.
4. 전체 scope backup이면 승인된 이전 checkout/package와 보존된 config/unit backup을 사용해 표준 `backend/scripts/install.sh` 경로로 일관되게 복원한다. 실행 중 pipeline, service restart, device access는 별도 운영 승인이 필요하다.
5. backup이 일부 component만 포함하면 전체 release rollback에 사용하지 않는다. 완전한 backup을 확보할 수 없으면 partial rollback을 중단하고 forward fix를 선택한다.
6. unit 상태, public TLS hello, 인증된 status, pipeline process/output를 다시 확인한다.

`scripts/diagnosis/deploy_backend.py --rollback`은 그 도구가 실제로 백업한 connection-evidence 파일 범위에만 사용할 수 있다. backup manifest가 current 변경 전체와 정확히 일치할 때만 해당 범위 복구에 사용한다. `scripts/diagnosis/backend-deployment-manifest.json`은 과거 6-file 배포 기록이므로 current release 전체 package manifest나 새 배포 승인으로 사용하지 않고 자동 재생성하지 않는다. 일반 설치는 `backend/scripts/install.sh`가 package module 전체를 복사하므로 특정 Python 파일 목록을 수동 구성하지 않는다.

## 5. Receiver Rollback

1. receiver environment, service revision, DB path, object root, free space를 기록한다.
2. service account가 읽을 수 있는 일관된 DB backup과 object/config inventory를 만든다.
3. 진행 중 upload와 trash transition을 확인한다. transition recovery가 끝나기 전 DB와 object를 따로 되돌리지 않는다.
4. 이전 binary가 현재 DB schema와 호환되는지 확인한다. 불명확하면 binary-only downgrade를 금지한다.
5. 승인된 coordinated DB/object/config backup을 staging path에 복원해 검증한 뒤 전환한다.
6. capabilities, employee/project deny/allow, session list, receipt object verification을 확인한다.

receiver object directory를 비우거나 DB 파일만 덮어쓰는 명령은 rollback으로 사용하지 않는다. employee/device token 원문을 terminal/log에 출력하지 않는다.

## 6. Rollback 합격 조건

- 선택한 component revision과 configuration이 기대 값이다.
- 기존 run output과 미검증 upload source가 보존됐다.
- 다른 device/project/environment data가 노출되지 않는다.
- 실제 target GET과 receipt가 관찰되며 cached 상태를 current로 사용하지 않는다.
- rollback 중 발생한 mutation과 service restart가 incident record에 남는다.
- forward recovery 방법과 책임자가 정해졌다.

실제 restart, rollback, device install은 known target과 해당 권한이 있을 때만 수행한다. 이 작업 세트에서는 실행하지 않았다.
