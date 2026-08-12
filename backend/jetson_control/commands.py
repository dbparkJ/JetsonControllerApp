from __future__ import annotations

import subprocess
import threading
from typing import Callable, Dict, Iterable, Sequence

from .config import DeviceConfig


class CommandError(RuntimeError):
    pass


class CommandDisabled(CommandError):
    pass


class CommandRunner:
    ACTIONS = {
        "start-system",
        "stop-system",
        "restart-services",
        "reboot",
        "shutdown",
    }

    def __init__(
        self,
        config: DeviceConfig,
        run: Callable[..., subprocess.CompletedProcess] = subprocess.run,
        popen: Callable[..., subprocess.Popen] = subprocess.Popen,
    ) -> None:
        self.config = config
        self._run = run
        self._popen = popen

    def execute(self, action: str) -> Dict[str, object]:
        if action not in self.ACTIONS:
            raise KeyError(action)

        if action in {"reboot", "shutdown"}:
            self._schedule_power_action(action)
            return {"accepted": True, "action": action}

        units = self.config.controlled_services
        if not units:
            raise CommandDisabled(
                "No controlled_services are configured on this Jetson"
            )

        verb = {
            "start-system": "start",
            "stop-system": "stop",
            "restart-services": "restart",
        }[action]
        ordered_units: Iterable[str] = reversed(units) if verb == "stop" else units

        try:
            for unit in ordered_units:
                self._run(
                    ["/usr/bin/systemctl", verb, unit],
                    check=True,
                    timeout=30,
                )
        except subprocess.TimeoutExpired as error:
            raise CommandError(f"Timed out while trying to {verb} services") from error
        except subprocess.CalledProcessError as error:
            raise CommandError(f"systemctl {verb} failed") from error
        except OSError as error:
            raise CommandError("systemctl is unavailable") from error

        return {"accepted": True, "action": action, "units": list(units)}

    def _schedule_power_action(self, action: str) -> None:
        if not self.config.allow_power_commands:
            raise CommandDisabled("Power commands are disabled on this Jetson")

        verb = "reboot" if action == "reboot" else "poweroff"

        def invoke() -> None:
            try:
                self._popen(
                    ["/usr/bin/systemctl", verb],
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    start_new_session=True,
                )
            except OSError:
                pass

        timer = threading.Timer(1.0, invoke)
        timer.daemon = True
        timer.start()
