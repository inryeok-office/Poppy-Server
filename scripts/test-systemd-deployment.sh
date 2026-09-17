#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

service_file="deploy/systemd/poppy-server.service"
env_example="deploy/systemd/poppy-server.env.example"
deployment_doc="docs/deployment.md"
compose_file="docker-compose.yml"
rehearsal_script="scripts/systemd_runtime_rehearsal.py"
rehearsal_doc="docs/systemd-runtime-recovery.md"

fail=0

require_file() {
  if [ ! -f "$1" ]; then
    echo "FAIL: missing file: $1"
    fail=1
  fi
}

require_match() {
  if ! grep -Eq "$2" "$1"; then
    echo "FAIL: missing '$2' in $1"
    fail=1
  fi
}

require_file "$service_file"
require_file "$env_example"
require_file "$deployment_doc"
require_file "$compose_file"
require_file "$rehearsal_script"
require_file "$rehearsal_doc"

if [ -f "$service_file" ]; then
  require_match "$service_file" '^After=.*network-online\.target.*docker\.service'
  require_match "$service_file" '^WorkingDirectory=/opt/poppy-server$'
  require_match "$service_file" '^Group=poppy$'
  require_match "$service_file" '^EnvironmentFile=/etc/poppy-server/poppy-server\.env$'
  require_match "$service_file" '^ExecStart=/usr/bin/docker compose --env-file /etc/poppy-server/poppy-server\.env up --no-build --abort-on-container-exit --exit-code-from app$'
  require_match "$service_file" '^ExecStop=/usr/bin/docker compose --env-file /etc/poppy-server/poppy-server\.env stop --timeout 30$'
  require_match "$service_file" '^Restart=on-failure$'
  require_match "$service_file" '^RestartSec=5s$'
  require_match "$service_file" '^SuccessExitStatus=130 143$'
  require_match "$service_file" '^KillSignal=SIGTERM$'
  require_match "$service_file" '^WantedBy=multi-user\.target$'

  if grep -Eiq 'password\s*=\s*[^[:space:]#]+|token\s*=\s*[^[:space:]#]+|webhook|private.?key|credential' "$service_file"; then
    echo "FAIL: possible hardcoded secret in $service_file"
    fail=1
  fi

  if grep -Eiq 'docker compose .*down|docker volume rm|rm -rf' "$service_file"; then
    echo "FAIL: destructive command in $service_file"
    fail=1
  fi
fi

if [ -f "$env_example" ]; then
  require_match "$env_example" '^DB_PASSWORD=change-me$'
  require_match "$env_example" '^POPPY_AGENT_TOKEN=change-me$'
fi

if [ -f "$deployment_doc" ]; then
  require_match "$deployment_doc" 'systemctl start poppy-server\.service'
  require_match "$deployment_doc" 'systemctl stop poppy-server\.service'
  require_match "$deployment_doc" 'systemctl restart poppy-server\.service'
  require_match "$deployment_doc" 'systemctl status poppy-server\.service'
  require_match "$deployment_doc" 'journalctl -u poppy-server\.service'
  require_match "$deployment_doc" '127\.0\.0\.1:8080/actuator/health'
  require_match "$deployment_doc" 'install -o root -g poppy -m 0640 deploy/systemd/poppy-server\.env\.example /etc/poppy-server/poppy-server\.env'
  require_match "$deployment_doc" 'docker compose down -v'
fi

if [ -f "$compose_file" ]; then
  require_match "$compose_file" 'condition: service_healthy'
  require_match "$compose_file" 'POPPY_AGENT_TOKEN: \$\{POPPY_AGENT_TOKEN:-\}'
fi

if [ -f "$rehearsal_script" ]; then
  require_match "$rehearsal_script" 'poppy-server-rehearsal\.service'
  require_match "$rehearsal_script" 'POPPY_SERVER_SYSTEMD_REHEARSAL'
  require_match "$rehearsal_script" 'ROBOT_MODE'
  require_match "$rehearsal_script" 'docker.*compose.*--project-name'
  require_match "$rehearsal_script" 'docker.*kill'
  if grep -Eiq 'systemctl (start|stop|restart) poppy-server\.service|poppy-postgres-data' "$rehearsal_script"; then
    echo "FAIL: rehearsal script references production service or volume"
    fail=1
  fi
fi

for file in "$service_file" "$env_example" "$deployment_doc"; do
  if [ -f "$file" ] && grep -qU $'\r' "$file"; then
    echo "FAIL: not LF-only: $file"
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo "systemd deployment test FAILED"
  exit 1
fi

echo "systemd deployment test PASSED"
