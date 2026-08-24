# Multi-Jetson and Python Pipeline Deployment

이 문서는 Controller backend와 Python 수집 pipeline을 여러 Jetson에 동일하게 설치하는 절차다. 현재 기준 pipeline은 다음 작업 트리다.

```text
/home/jm/26_camera_record
```

## 1. 현재 확인값

| 항목 | 확인 결과 |
|---|---|
| Git branch | `main` |
| Git worktree와 실행 source | `/home/jm/26_camera_record` |
| Git remote | `origin` (`geonLabs/geonova-depthai-mapper`) |
| 메인 entrypoint | `main.py` |
| 실행 config | `config.yaml` |
| virtualenv | `/home/jm/26_camera_record/.venv` |
| Python | 장비에서 만든 위 venv의 Python; 등록 manifest에 실제 버전 기록 |
| 기본 출력 | preset이 `--output-dir /data/collections`로 고정 |

원본 작업 트리는 source version history와 개발용으로 유지한다. 자동 실행 서비스는 이 디렉터리의 Python source를 직접 실행하지 않고, 등록 시점의 working tree를 시스템 release로 복사해 실행한다. 따라서 개발 중 파일 변경이 실행 중 프로세스에 섞이지 않는다.

## 2. 새 Jetson 자동 설치

ControllerApp 저장소, DepthAI Git 작업 트리, 해당 장비에서 생성한 virtualenv를 새
장비에 준비한 뒤 한 번 실행한다. `<user>`는 카메라, GPS, IMU 장치에 접근할 Linux
사용자다. DepthAI 저장소는 승인된 `origin`에서 `/home/<user>/26_camera_record`로
clone한다. virtualenv는 장비와 Python ABI에 종속되므로 다른 Jetson에서 그대로
복사하지 않고 각 장비의 repository root에 `.venv`로 생성한다.

```bash
sudo backend/scripts/bootstrap-jetson.sh \
  --device-name MMS-JETSON-02 \
  --pipeline-user <user> \
  --enable-power \
  --depthai-repo /home/<user>/26_camera_record \
  --depthai-venv /home/<user>/26_camera_record/.venv
```

이 명령은 다음 작업을 idempotent하게 수행한다.

1. backend와 BlueZ build/runtime package 설치
2. BlueZ 5.55 binary 검증 또는 공식 source build
3. 새 장비 UUID/secret과 QR 생성; 기존 identity는 보존
4. BLE GATT와 pinned HTTPS API 설치 및 부팅 자동 시작
5. Wi-Fi Direct 검색 service와 요청 시 NetworkManager Group Owner/DHCP 자동 구성
6. storage root와 전원 명령 구성
7. DepthAI working tree의 실행 snapshot 생성
8. `jetson-pipeline@depthai-capture.service` 부팅 자동 시작 활성화

등록 직후 카메라를 바로 실행하지는 않는다. 현재 session에서도 즉시 시작하려면 마지막에 `--start-depthai-now`를 추가한다. 다음 재부팅부터는 `enable`된 pipeline이 자동으로 실행된다.

현재 장비처럼 BlueZ 5.55 binary가 이미 있으면 `/usr/local/libexec/bluetooth/bluetoothd-5.55`를 검증해 재사용한다. 별도 빌드 binary를 배포할 때는 다음 옵션을 쓴다.

```bash
sudo backend/scripts/bootstrap-jetson.sh \
  --bluez-binary /path/to/bluez-5.55/src/bluetoothd \
  ...
```

package와 BlueZ를 장비 이미지에서 이미 관리한다면 각각 `--skip-packages`, `--skip-bluez`를 사용할 수 있다. 건너뛰기 전 `bluetoothd-5.55 -v`, systemd ExecStart, BLE 광고를 별도로 확인한다. Wi-Fi Direct는 기본 활성화되며 adapter가 P2P-GO를 지원하지 않는 장비만 `--disable-wifi-direct`를 사용한다. 자세한 검증은 [WIFI_DIRECT_SETUP.md](WIFI_DIRECT_SETUP.md)에 있다.

## 3. QR와 BLE 재등록

신규 장비의 QR은 다음 root 전용 경로에 생성된다.

```text
/var/lib/jetson-control/jetson-pairing-uri.txt
/var/lib/jetson-control/jetson-pairing-qr.png
```

화면이 연결된 장비에서는 승인된 운영 절차로 QR 이미지를 열고 앱의 `QR 스캔`을 사용한다. QR에는 장비 secret이 포함되므로 메신저나 일반 파일 서버에 올리지 않는다. 설치를 다시 실행해도 `/etc/jetson-control/device.json`을 보존하므로 기존 앱 등록은 유지된다.

QR 등록 뒤 앱은 BLE challenge-response로 장비를 인증한다. Wi-Fi 비밀번호는 challenge에서 파생한 AES-256-GCM key로 암호화되어 GATT로 전달된다. LAN API 연결에서는 같은 QR secret으로 TLS 인증서 proof와 HTTP HMAC을 검증한다.

## 4. Pipeline 작성 계약

자동 관리 대상은 pipeline 안에서 실제 수집을 시작하고 종료 signal을 처리하는 하나의
메인 Python 파일이다. 보조 script와 library를 각각 서비스로 등록할 필요는 없다.
현재 DepthAI pipeline의 표준 메인은 repository root의 `main.py`다.

새 pipeline은 레포 root에 `config.yaml`을 두고, 메인 Python을 인자 없이 실행해도 같은 레포의 `config.yaml`을 기본으로 읽도록 구성하는 것을 표준으로 한다.

```python
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent
parser.add_argument(
    "--config",
    type=Path,
    default=REPO_ROOT / "config.yaml",
)
```

상대 current working directory에 의존해 `Path("config.yaml")`만 사용하지 않는다. systemd, test, 개발 shell의 working directory가 달라도 같은 설정을 읽어야 하기 때문이다. 메인 파일은 `SIGINT`와 `SIGTERM`에서 장치와 파일 writer를 정상 종료해야 한다.

관리자는 실행 시 항상 snapshot 안의 config 절대 경로를 다음처럼 넘긴다.

```text
<venv>/bin/python -u <release>/<main.py> --config <release>/<config.yaml>
```

현재 DepthAI 저장소는 root `.venv`, `main.py`, `config.yaml` 계약을 따른다. preset은
YAML의 상대 출력 경로에 의존하지 않고 `--output-dir /data/collections`와
`--controller-bridge-dir /var/lib/jetson-sensors`를 `main.py`의 명시적 CLI 인수로
넘긴다. systemd 작업 디렉터리는 source repository root이며 위 두 절대경로만
`ReadWritePaths`에 추가된다. 따라서 YAML에 남아 있는 상대경로가 sandbox 밖의 홈
디렉터리로 쓰기 경로를 바꾸지 못한다.

## 5. 현재 DepthAI 작업 등록

backend 설치 후 preset script를 실행한다.

```bash
sudo /opt/jetson-control/install-depthai-pipeline.sh
```

다른 경로라면 명시한다.

```bash
sudo /opt/jetson-control/install-depthai-pipeline.sh \
  --repo /home/<user>/26_camera_record \
  --venv /home/<user>/26_camera_record/.venv
```

generic pipeline은 다음처럼 등록한다.

```bash
sudo /opt/jetson-control/register-pipeline.sh \
  --id capture-main \
  --label "Capture Main" \
  --repo /home/<user>/path/to/repo \
  --venv /home/<user>/path/to/.venv \
  --entry main.py \
  --config config.yaml \
  --working-dir /home/<user>/path/to/repo \
  --write-path /home/<user>/path/to/repo/records \
  --user <user> \
  --autostart
```

`--autostart`는 다음 부팅을 활성화하고 현재 process는 시작하지 않는다. 즉시 실행은 `--start-now`, 실행 중인 작업을 새 snapshot으로 바꿀 때는 `--restart-running`을 추가한다.

## 6. Source snapshot과 버전 이력

등록기는 선택한 source 디렉터리에서 `git ls-files --cached --others --exclude-standard -- .` 결과만 복사한다. source가 Git worktree의 하위 프로젝트여도 선택 디렉터리 밖의 파일은 포함하지 않는다. 따라서 현재 commit의 tracked 파일뿐 아니라 ignore되지 않은 미커밋 새 파일도 정확히 snapshot에 포함되며, 원본 `.git`, ignored image dataset, cache, 기존 venv는 복사하지 않는다.

```text
/opt/jetson-pipelines/depthai-capture/
  pipeline.json
  current -> releases/<timestamp>-<commit>[-dirty]
  releases/
    <timestamp>-<commit>[-dirty]/
```

manifest에는 source repo, branch, commit, dirty 여부, Python/venv, entrypoint, config, 생성 시각을 기록한다. 앱의 작업 목록에서도 branch, dirty 여부, Python 버전을 확인할 수 있다.

source 변경을 배포할 때는 pipeline을 중지한 뒤 같은 ID로 다시 등록하거나 CLI에서 `--restart-running`을 쓴다. 이전 release는 그대로 남는다. 등록 해제는 실행 release를 삭제하지 않고 `/opt/jetson-pipelines/.archive/`로 이동한다.

## 7. 변경분 시스템 반영

Controller backend 코드, systemd unit, helper script가 바뀌면 Git 작업 트리를 최신 commit으로 맞춘 뒤 설치 script를 다시 실행한다. `install.sh`는 `/opt/jetson-control/jetson_control`을 새 복사본으로 교체하고, systemd unit과 실행 script를 덮어쓰며, 기존 `/etc/jetson-control/device.json`, QR identity, storage/upload 설정은 보존한다.

```bash
git pull --ff-only
sudo backend/scripts/install.sh \
  --pipeline-user <user> \
  --enable-power
sudo systemctl restart jetson-control.service jetson-control-api.service
sudo systemctl restart jetson-wifi-direct.service
sudo /opt/jetson-control/doctor.sh
```

Python pipeline source가 바뀐 경우에는 backend 설치만으로 실행 snapshot이 바뀌지 않는다. 앱의 `자동 실행 작업`에서 해당 작업을 다시 등록하거나 CLI에서 같은 ID로 `--restart-running`을 사용해 새 release를 만든다.

```bash
sudo /opt/jetson-control/register-pipeline.sh \
  --id depthai-capture \
  --label "DepthAI Capture" \
  --repo /home/<user>/26_camera_record \
  --venv /home/<user>/26_camera_record/.venv \
  --entry main.py \
  --config config.yaml \
  --working-dir /home/<user>/26_camera_record \
  --write-path /data/collections \
  --write-path /var/lib/jetson-sensors \
  --argument=--output-dir \
  --argument /data/collections \
  --argument=--controller-bridge-dir \
  --argument /var/lib/jetson-sensors \
  --user <user> \
  --autostart \
  --restart-running
```

명시적 `--output-dir`이 YAML 기본값보다 우선하므로 수집물은
`/data/collections` 아래에 기록된다. `--argument=--output-dir`처럼 option 이름은
등호 형식으로 등록해야 registrar가 다음 registrar option으로 오해하지 않는다.
기존 `~/26_camera_record`의 수집물은 backend 재설치 후 앱에
`Previous collected data` root로 계속 노출된다.

운영 순서는 `Git 최신화 -> backend install 재실행 -> 필요한 pipeline 재등록 -> doctor와 앱 연결 확인`으로 고정한다. 설정 파일을 직접 복사해 덮어쓰지 말고, 설치 script와 등록기를 통해 원자적으로 반영한다.

## 8. 앱에서 작업 추가

Jetson에 LAN 또는 Wi-Fi Direct로 연결한 뒤 `대시보드 > 자동 실행 작업 > 작업 추가`로 이동한다. 앱에서 다음 순서로 선택한다.

1. 표시 이름 입력
2. 표준 작업 폴더 선택
3. 부팅 자동 실행 toggle 확인

앱의 실행 소스 선택기는 `pipeline_user`의 `~/` 아래만 탐색한다. 폴더 이름은 소문자나
숫자로 시작하고 소문자, 숫자, 점, 밑줄, 하이픈만 사용할 수 있으므로
`26_camera_record`도 유효한 작업 ID다. 선택한 폴더 root에는 `.venv/bin/python`,
`main.py`, `config.yaml` 또는 `config.yml` 중 하나가 있어야 한다. backend는 Git root,
venv Python, Python syntax, config 파일 유형을 다시 검사하고 `results/`를 유일한 표준
출력 폴더로 등록한다. 임의 shell command는 앱에서 등록할 수 없다. `/data/collections`와
Controller sensor bridge가 필요한 현재 DepthAI 운영 preset은 앞 절의
`install-depthai-pipeline.sh`로 명시적 등록한다.

등록된 작업 카드에서는 실시간 실행 로그, 현재 release의 YAML 설정, 첫 출력 폴더를 각각 별도 화면으로 연다. 로그는 자동 재시작마다 `/var/log/jetson-pipelines/<id>/run-*.log`로 분리되어 앱에서 이전 실행까지 선택할 수 있다. YAML 저장은 실행 snapshot에 원자 반영되며 작업 재시작 후 적용된다. 소스 레포를 다시 등록하면 새 snapshot의 YAML이 기준이 된다.

## 9. 운영 명령

```bash
systemctl status jetson-pipeline@depthai-capture.service
journalctl -u jetson-pipeline@depthai-capture.service -f
ls -lh /var/log/jetson-pipelines/depthai-capture/

sudo systemctl start jetson-pipeline@depthai-capture.service
sudo systemctl stop jetson-pipeline@depthai-capture.service
sudo systemctl restart jetson-pipeline@depthai-capture.service

sudo systemctl enable jetson-pipeline@depthai-capture.service
sudo systemctl disable jetson-pipeline@depthai-capture.service
```

파일 로그는 pipeline별 최근 20개, 합계 1 GiB, 실행당 128 MiB까지 자동 보관한다. 실행 파일 한도를 넘긴 출력은 journald의 기존 회전 정책으로 계속 확인할 수 있다.

pipeline 사용자에게 필요한 장치 group을 장비 정책에 맞게 부여한다. 일반적인 후보는 `video`, `dialout`, `plugdev`지만 실제 `/dev` node의 owner/group을 먼저 확인한다. group 변경 후에는 사용자 session 재로그인 또는 재부팅이 필요하다.

## 10. 검증

```bash
/usr/local/libexec/bluetooth/bluetoothd-5.55 -v
systemctl show bluetooth.service --property=ExecStart
systemctl is-enabled jetson-control.service jetson-control-api.service jetson-wifi-direct.service
systemctl is-enabled jetson-pipeline@depthai-capture.service
sudo /opt/jetson-control/doctor.sh
```

재부팅 검증:

```bash
sudo reboot
```

부팅 후 API, BLE 광고, pipeline 상태, 새 dataset 생성 위치, GPS/IMU serial 접근, 종료 시 파일 무결성을 확인한다. 운영 데이터가 연결된 상태에서는 앱의 reboot/shutdown 버튼을 누르기 전에 pipeline을 정상 중지한다.
