#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

KEYS = ("SPRING_PROFILES_ACTIVE", "DB_NAME", "DB_USERNAME", "DB_PASSWORD",
        "POPPY_AGENT_TOKEN", "POPPY_ADMIN_USERNAME", "POPPY_ADMIN_PASSWORD_HASH")


def valid_env(path):
    try:
        with open(path, encoding="utf-8-sig") as source:
            lines = source.read().splitlines()
        counts = dict.fromkeys(KEYS, 0)
        for line in lines:
            match = re.match(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*[:=](.*)$", line)
            if match and match.group(1) in counts:
                counts[match.group(1)] += 1
                if match.group(2).lstrip().startswith("#"):
                    return False
        if any(count != 1 for count in counts.values()):
            return False

        compose = {"services": {"check": {"image": "poppy-env-precheck",
                   "environment": {key: "${" + key + "}" for key in KEYS}}}}
        env = os.environ.copy()
        for key in (*KEYS, "COMPOSE_FILE", "COMPOSE_ENV_FILES", "COMPOSE_PROFILES"):
            env.pop(key, None)
        result = subprocess.run(
            ["docker", "compose", "--project-directory", os.path.dirname(os.path.abspath(path)),
             "--env-file", os.path.abspath(path), "-f", "-", "config", "--format", "json"],
            input=json.dumps(compose), capture_output=True, text=True, env=env, check=False,
        )
        if result.returncode != 0:
            return False
        values = json.loads(result.stdout)["services"]["check"]["environment"]
        return all(isinstance(values.get(key), str) and values[key].strip() for key in KEYS)
    except (OSError, ValueError, KeyError, TypeError):
        return False


if __name__ == "__main__":
    if len(sys.argv) != 2 or not valid_env(sys.argv[1]):
        print("ERROR: required environment keys are missing, duplicated, empty, or invalid. Aborting deployment.")
        sys.exit(1)
    print("Required environment keys validated.")
