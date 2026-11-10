import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

CHECK = Path(__file__).with_name("check-cd-env.py")
KEYS = ("SPRING_PROFILES_ACTIVE", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
        "POPPY_AGENT_TOKEN", "POPPY_ADMIN_USERNAME", "POPPY_ADMIN_PASSWORD_HASH")
MARKER = "test-secret-do-not-log-8391"


class EnvCheckTest(unittest.TestCase):
    def check(self, lines, expected):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.env"
            path.write_text("\n".join(lines) + "\n", encoding="utf-8")
            result = subprocess.run([sys.executable, str(CHECK), str(path)],
                                    capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode == 0, expected, result.stdout + result.stderr)
        self.assertNotIn(MARKER, result.stdout + result.stderr)

    def valid(self):
        return [f"{key}={MARKER}" for key in KEYS]

    def test_valid(self):
        self.check(self.valid(), True)

    def test_missing(self):
        self.check(self.valid()[:-1], False)

    def test_duplicate_and_missing(self):
        self.check(self.valid()[:-1] + [self.valid()[0]], False)

    def test_every_key_must_be_unique_and_nonempty(self):
        for index, key in enumerate(KEYS):
            with self.subTest(key=key):
                lines = self.valid()
                lines[index] = f"{key}=  "
                self.check(lines, False)
                self.check(self.valid() + [f"{key}=duplicate"], False)

    def test_empty_values(self):
        for value in ("", "   ", "\"\"", "''", " # comment", "${POPPY_TEST_UNDEFINED}"):
            with self.subTest(value=value):
                self.check(self.valid()[:-1] + [f"{KEYS[-1]}={value}"], False)

    def test_commented_only(self):
        self.check(self.valid()[:-1] + [f"# {KEYS[-1]}={MARKER}"], False)

    def test_compose_syntax(self):
        lines = self.valid()
        lines[0] = " export SPRING_PROFILES_ACTIVE = 'prod' # comment"
        lines[1] = "DB_NAME: poppy # comment"
        lines[-1] = "POPPY_ADMIN_PASSWORD_HASH='$2a$10$example'"
        self.check(lines, True)

    def test_host_environment_does_not_mask_empty_file_value(self):
        os.environ[KEYS[-1]] = MARKER
        try:
            self.check(self.valid()[:-1] + [f"{KEYS[-1]}="], False)
        finally:
            del os.environ[KEYS[-1]]

    def test_malformed_file_does_not_leak(self):
        self.check(self.valid() + [f"BROKEN='unterminated {MARKER}"], False)


if __name__ == "__main__":
    unittest.main()
