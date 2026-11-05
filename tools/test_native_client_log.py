"""Negative controls for the NeoForge client log classification."""

import unittest
import json
from pathlib import Path
import shutil
import tempfile
import tomllib
from native_client_log import audit, classify


LOGIN = ("[Server thread/INFO]: TerrainChecks logged in with entity id 21 at (0, 64, 0)\n"
         "[Server thread/INFO]: TerrainChecks joined the game")
START = "[Server thread/INFO] [MinecraftServer/]: Preparing start region for dimension minecraft:overworld"
NOTICE = "[Thread-1/FATAL] [net.minecraftforge.common.ForgeConfig/CORE]: Forge config just got changed on the file system!"
PAYLOAD = "[Client thread/ERROR] [net.minecraftforge.fml.network.simple.IndexedMessageCodec/SIMPLENET]: Received empty payload on channel fml:handshake"


class NativeClientLogTest(unittest.TestCase):
    def test_previous_target_notices_are_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE, START, LOGIN, PAYLOAD)), True)

    def test_clean_native_startup_is_accepted(self):
        receipt = classify("\n".join((START, LOGIN)), True)
        self.assertEqual(receipt["native_config_notices"], 0)
        self.assertEqual(receipt["empty_handshakes"], 0)
        self.assertEqual(receipt["notices"], [])

    def test_actual_config_change_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE, START, LOGIN)), False)

    def test_notice_after_world_start_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, NOTICE, LOGIN)), True)

    def test_other_fatal_message_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE.replace("just got changed", "failed"), START, LOGIN)), True)

    def test_other_logger_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE.replace("ForgeConfig/CORE", "OtherConfig/CORE"), START, LOGIN)), True)

    def test_repeated_config_notifications_are_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((NOTICE, NOTICE, NOTICE, START, LOGIN)), True)

    def test_other_handshake_error_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, LOGIN, PAYLOAD.replace("fml:handshake", "another:channel"))), True)

    def test_handshake_failure_without_login_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, PAYLOAD)), True)

    def test_stderr_is_rejected(self):
        with self.assertRaises(ValueError):
            classify("\n".join((START, LOGIN, "[STDERR/]: java.lang.IllegalStateException")), True)


class NativeClientFilesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        defaults = Path(__file__).resolve().parent.parent / "src/bootstrapTest/defaultconfigs"
        shutil.copytree(defaults / "config", self.directory / "config")
        self.server = self.directory / "config"
        (self.directory / "logs").mkdir()
        (self.directory / "logs/latest.log").write_text("\n".join((
            START, LOGIN,
            "SKY_CONFIG_SCREENS_PASS", "TERRAIN_SMOOTHER_CLIENT_PASS",
            "TERRAIN_SMOOTHER_CLIENT_WORLD_PASS", "TERRAIN_SMOOTHER_CLIENT_DISCONNECT_PASS", "SKY_RECIPE_PREVIEW_MODELS_PASS")), encoding="utf-8")

    def test_native_defaults_with_different_order_and_comments(self):
        client = self.directory / "config/neoforge-client.toml"
        settings = tomllib.loads(client.read_text(encoding="utf-8"))
        client.write_text("# Same settings in a different order\n" + "\n".join(
            name + " = " + json.dumps(value) for name, value in reversed(list(settings.items()))),
            encoding="utf-8")
        receipt = audit(self.directory, "client-fresh")
        self.assertTrue(receipt["unchanged_native_defaults"])
        self.assertEqual(receipt["native_config_notices"], 0)

    def test_changed_default_stops_the_check(self):
        client = self.directory / "config/neoforge-client.toml"
        contents = client.read_text(encoding="utf-8")
        self.assertIn("showLoadWarnings = true", contents)
        client.write_text(contents.replace("showLoadWarnings = true",
                                           "showLoadWarnings = false"), encoding="utf-8")
        with self.assertRaises(ValueError):
            audit(self.directory, "client-fresh")

    def test_missing_server_config_stops_the_check(self):
        (self.server / "neoforge-server.toml").unlink()
        with self.assertRaises(ValueError):
            audit(self.directory, "client-fresh")

    def test_crash_report_stops_the_check(self):
        (self.directory / "crash-reports").mkdir()
        (self.directory / "crash-reports/test.txt").write_text("Simulated failure")
        with self.assertRaises(ValueError):
            audit(self.directory, "client-fresh")


if __name__ == "__main__":
    unittest.main()
