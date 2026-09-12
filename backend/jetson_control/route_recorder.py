"""Record fresh device GNSS independently of mobile connectivity while a run exists."""
import json
import math
import os
import threading
from pathlib import Path
from .sensors import SensorBridgeStore


class RouteRecorder:
    def __init__(self, log_path: Path, bridge_dir: Path):
        self.path = Path(str(log_path) + '.route.jsonl')
        self.bridge = SensorBridgeStore(bridge_dir)
        self.stop_event = threading.Event()
        self.thread = threading.Thread(target=self._record, daemon=True)

    def start(self):
        self.thread.start()

    def close(self):
        self.stop_event.set()
        self.thread.join(timeout=3)

    def _record(self):
        last_stamp = None
        segment = 0
        gap = False
        try:
            fd = os.open(self.path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o640)
            with os.fdopen(fd, 'w', encoding='utf-8') as stream:
                while not self.stop_event.is_set():
                    try:
                        status = self.bridge.status()
                        point = status.gnss
                        lat, lon = point.get('latitude'), point.get('longitude')
                        stamp = point.get('lastSampleAtEpochMillis')
                        valid = (status.fresh and point.get('active') and stamp is not None and
                                 isinstance(lat, (float, int)) and isinstance(lon, (float, int)) and
                                 math.isfinite(lat) and math.isfinite(lon) and -90 <= lat <= 90 and -180 <= lon <= 180)
                        if valid and stamp != last_stamp:
                            if gap or (last_stamp is not None and (stamp < last_stamp or stamp - last_stamp > 15000)):
                                segment += 1
                            gap = False
                            last_stamp = stamp
                            stream.write(json.dumps(dict(latitude=lat, longitude=lon,
                                timestamp=stamp, segment=segment)) + '\n')
                            stream.flush()
                            if stream.tell() >= 8 * 1024 * 1024:
                                break
                        elif not valid:
                            gap = True
                    except (OSError, ValueError, TypeError):
                        gap = True
                    self.stop_event.wait(2)
        except OSError:
            # GNSS storage must never interrupt collection or its exit cleanup.
            return
