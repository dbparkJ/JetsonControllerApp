import subprocess
import unittest
from unittest.mock import Mock, patch

from jetson_control.commands import CommandDisabled, CommandRunner
from jetson_control.config import DeviceConfig


class CommandRunnerTest(unittest.TestCase):
    def test_power_commands_are_rejected_when_disabled(self) -> None:
        runner = CommandRunner(self.config(power_enabled=False))

        with self.assertRaises(CommandDisabled):
            runner.execute("reboot")

    def test_reboot_is_scheduled(self) -> None:
        self.assert_power_action("reboot", "reboot")

    def test_shutdown_is_scheduled_as_poweroff(self) -> None:
        self.assert_power_action("shutdown", "poweroff")

    def assert_power_action(self, action: str, systemctl_verb: str) -> None:
        popen = Mock()
        runner = CommandRunner(self.config(power_enabled=True), popen=popen)

        with patch("jetson_control.commands.threading.Timer") as timer:
            result = runner.execute(action)
            callback = timer.call_args.args[1]
            callback()

        self.assertEqual({"accepted": True, "action": action}, result)
        timer.assert_called_once()
        timer.return_value.start.assert_called_once_with()
        popen.assert_called_once_with(
            ["/usr/bin/systemctl", systemctl_verb],
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )

    @staticmethod
    def config(power_enabled: bool) -> DeviceConfig:
        return DeviceConfig(
            device_id="00000000-0000-0000-0000-000000000001",
            device_name="MMS-TEST",
            bootstrap_secret=bytes(range(32)),
            controlled_services=(),
            service_flags={"camera": "", "lidar": "", "gnss": "", "mms": ""},
            allow_power_commands=power_enabled,
            wifi_interface="wlan0",
        )


if __name__ == "__main__":
    unittest.main()
