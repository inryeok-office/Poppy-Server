#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NOTIFY_SCRIPT="$SCRIPT_DIR/discord-notify.sh"

fail=0

assert_eq() {
  local description="$1"
  local expected="$2"
  local actual="$3"
  if [ "$expected" != "$actual" ]; then
    echo "FAIL: $description (expected [$expected], got [$actual])"
    fail=1
  else
    echo "OK: $description"
  fi
}

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

git -C "$WORK_DIR" init -q
git -C "$WORK_DIR" config user.email "test@example.com"
git -C "$WORK_DIR" config user.name "test"

LONG_MESSAGE="$(printf 'a%.0s' $(seq 1 250))"
COMMIT_SUBJECT='fix(test): "quote" and back`tick handling'

git -C "$WORK_DIR" commit --allow-empty -q -m "$COMMIT_SUBJECT"

run_notify() {
  (cd "$WORK_DIR" && CI_STATUS="$1" REPOSITORY="org/repo" WORKFLOW_NAME="CI" EVENT_NAME="push" \
    BRANCH="develop" COMMIT_SHA="0123456789abcdef" ACTOR="tester" RUN_NUMBER="42" RUN_ID="99" \
    SERVER_URL="https://github.com" bash "$NOTIFY_SCRIPT" print)
}

run_cd_notify() {
  (cd "$WORK_DIR" && NOTIFICATION_TYPE="cd" CD_STATUS="$1" REPOSITORY="org/repo" \
    WORKFLOW_NAME="CD" EVENT_NAME="push" ENVIRONMENT="develop" BRANCH="develop" \
    COMMIT_SHA="0123456789abcdef" ACTOR="tester" RUN_NUMBER="42" RUN_ID="99" \
    SERVER_URL="https://github.com" DEPLOYMENT_STAGE="$2" HEALTH_STATUS="$3" \
    FAILURE_REASON="$4" CD_DURATION_SECONDS="$5" bash "$NOTIFY_SCRIPT" print)
}

assert_valid_json() {
  local description="$1"
  local payload="$2"
  if echo "$payload" | jq empty >/dev/null 2>&1; then
    echo "OK: $description"
  else
    echo "FAIL: $description"
    fail=1
  fi
}

SUCCESS_PAYLOAD="$(run_notify success)"
assert_valid_json "성공 payload는 유효한 JSON이다" "$SUCCESS_PAYLOAD"
assert_eq "성공 title" "✅ CI 성공" "$(echo "$SUCCESS_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "성공 color" "3066993" "$(echo "$SUCCESS_PAYLOAD" | jq -r '.embeds[0].color')"

FAILURE_PAYLOAD="$(run_notify failure)"
assert_valid_json "실패 payload는 유효한 JSON이다" "$FAILURE_PAYLOAD"
assert_eq "실패 title" "❌ CI 실패" "$(echo "$FAILURE_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "실패 color" "15158332" "$(echo "$FAILURE_PAYLOAD" | jq -r '.embeds[0].color')"

CANCELLED_PAYLOAD="$(run_notify cancelled)"
assert_valid_json "취소 payload는 유효한 JSON이다" "$CANCELLED_PAYLOAD"
assert_eq "취소 title" "⚠️ CI 취소" "$(echo "$CANCELLED_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "취소 color" "15105570" "$(echo "$CANCELLED_PAYLOAD" | jq -r '.embeds[0].color')"

assert_eq "allowed_mentions.parse는 비어 있다" "[]" "$(echo "$SUCCESS_PAYLOAD" | jq -c '.allowed_mentions.parse')"

FIELD_NAMES="$(echo "$SUCCESS_PAYLOAD" | jq -r '[.embeds[0].fields[].name] | join(",")')"
for required_field in Repository Workflow Status Event Branch Commit "Commit Message" Actor "Pull Request" Run Actions; do
  case ",$FIELD_NAMES," in
    *",$required_field,"*)
      echo "OK: 필수 필드 존재 - $required_field"
      ;;
    *)
      echo "FAIL: 필수 필드 누락 - $required_field"
      fail=1
      ;;
  esac
done

COMMIT_MESSAGE_FIELD="$(echo "$SUCCESS_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Commit Message") | .value')"
assert_eq "커밋 메시지 escape 처리" "$COMMIT_SUBJECT" "$COMMIT_MESSAGE_FIELD"

PR_NONE_FIELD="$(echo "$SUCCESS_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Pull Request") | .value')"
assert_eq "PR 없는 이벤트는 해당 없음으로 표시된다" "해당 없음" "$PR_NONE_FIELD"

PR_PAYLOAD="$(cd "$WORK_DIR" && CI_STATUS="success" REPOSITORY="org/repo" WORKFLOW_NAME="CI" EVENT_NAME="pull_request" \
  BRANCH="feature/1-test" COMMIT_SHA="0123456789abcdef" ACTOR="tester" RUN_NUMBER="42" RUN_ID="99" \
  SERVER_URL="https://github.com" PR_NUMBER="12" PR_TITLE="$LONG_MESSAGE" bash "$NOTIFY_SCRIPT" print)"
PR_FIELD="$(echo "$PR_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Pull Request") | .value')"
case "$PR_FIELD" in
  "#12 "*"..."*)
    echo "OK: PR 제목이 길이 제한에 맞게 잘린다"
    ;;
  *)
    echo "FAIL: PR 제목 길이 제한 동작이 예상과 다르다 ($PR_FIELD)"
    fail=1
    ;;
esac

git -C "$WORK_DIR" commit --allow-empty -q -m "$LONG_MESSAGE"
LONG_PAYLOAD="$(run_notify success)"
LONG_FIELD="$(echo "$LONG_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Commit Message") | .value')"
case "$LONG_FIELD" in
  *"...")
    echo "OK: 커밋 메시지가 길이 제한에 맞게 잘린다"
    ;;
  *)
    echo "FAIL: 커밋 메시지 길이 제한 동작이 예상과 다르다"
    fail=1
    ;;
  esac

CD_START_PAYLOAD="$(run_cd_notify start INITIALIZING "해당 없음" "해당 없음" "")"
assert_valid_json "CD 시작 payload는 유효한 JSON이다" "$CD_START_PAYLOAD"
assert_eq "CD 시작 title" "🚀 Poppy-Server 배포 시작" "$(echo "$CD_START_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "CD 시작 color" "3447003" "$(echo "$CD_START_PAYLOAD" | jq -r '.embeds[0].color')"
assert_eq "CD 환경" "develop" "$(echo "$CD_START_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Environment") | .value')"
assert_eq "CD 시작 stage" "INITIALIZING" "$(echo "$CD_START_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Deployment Stage") | .value')"

CD_REPLACEMENT_PAYLOAD="$(run_cd_notify replacement REPLACEMENT "해당 없음" "해당 없음" "")"
assert_valid_json "CD replacement payload는 유효한 JSON이다" "$CD_REPLACEMENT_PAYLOAD"
assert_eq "CD replacement title" "⏸️ 기존 애플리케이션 교체 시작" "$(echo "$CD_REPLACEMENT_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "CD replacement color" "15105570" "$(echo "$CD_REPLACEMENT_PAYLOAD" | jq -r '.embeds[0].color')"

CD_SUCCESS_PAYLOAD="$(run_cd_notify success HEALTH_CHECK UP "해당 없음" "102")"
assert_valid_json "CD 성공 payload는 유효한 JSON이다" "$CD_SUCCESS_PAYLOAD"
assert_eq "CD 성공 title" "✅ Poppy-Server 배포 완료" "$(echo "$CD_SUCCESS_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "CD 성공 status" "HEALTHY" "$(echo "$CD_SUCCESS_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Status") | .value')"
assert_eq "CD 성공 health" "UP" "$(echo "$CD_SUCCESS_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Health") | .value')"
assert_eq "CD duration" "1m 42s" "$(echo "$CD_SUCCESS_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Duration") | .value')"
assert_eq "CD Actions URL" "https://github.com/org/repo/actions/runs/99" "$(echo "$CD_SUCCESS_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Actions") | .value')"
assert_eq "CD allowed_mentions.parse는 비어 있다" "[]" "$(echo "$CD_SUCCESS_PAYLOAD" | jq -c '.allowed_mentions.parse')"

LONG_REASON="$(printf 'r%.0s' {1..600})"
CD_FAILURE_PAYLOAD="$(run_cd_notify failure HEALTH_CHECK "DOWN" "$LONG_REASON" "120")"
assert_valid_json "CD 실패 payload는 유효한 JSON이다" "$CD_FAILURE_PAYLOAD"
assert_eq "CD 실패 title" "❌ Poppy-Server 배포 실패" "$(echo "$CD_FAILURE_PAYLOAD" | jq -r '.embeds[0].title')"
assert_eq "CD 실패 color" "15158332" "$(echo "$CD_FAILURE_PAYLOAD" | jq -r '.embeds[0].color')"
FAILURE_REASON_FIELD="$(echo "$CD_FAILURE_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Failure Reason") | .value')"
case "$FAILURE_REASON_FIELD" in
  *"..." )
    echo "OK: 실패 원인이 길이 제한에 맞게 잘린다"
    ;;
  *)
    echo "FAIL: 실패 원인 길이 제한 동작이 예상과 다르다"
    fail=1
    ;;
esac

UNKNOWN_STAGE_PAYLOAD="$(run_cd_notify unexpected unexpected-stage "해당 없음" "해당 없음" "")"
assert_valid_json "알 수 없는 CD 상태 payload는 유효한 JSON이다" "$UNKNOWN_STAGE_PAYLOAD"
assert_eq "알 수 없는 stage가 안전하게 표시된다" "unexpected-stage" "$(echo "$UNKNOWN_STAGE_PAYLOAD" | jq -r '.embeds[0].fields[] | select(.name == "Deployment Stage") | .value')"

SECRET_MARKER="https://discord.example/secret-marker"
SECRET_PAYLOAD="$(cd "$WORK_DIR" && NOTIFICATION_TYPE=cd CD_STATUS=start REPOSITORY="org/repo" \
  WORKFLOW_NAME="CD" EVENT_NAME="push" ENVIRONMENT="develop" BRANCH="develop" \
  COMMIT_SHA="0123456789abcdef" ACTOR="tester" RUN_NUMBER="42" RUN_ID="99" \
  SERVER_URL="https://github.com" DEPLOYMENT_STAGE=INITIALIZING DISCORD_WEBHOOK_URL="$SECRET_MARKER" \
  bash "$NOTIFY_SCRIPT" print)"
case "$SECRET_PAYLOAD" in
  *"$SECRET_MARKER"*)
    echo "FAIL: Webhook 값이 payload에 포함되었다"
    fail=1
    ;;
  *)
    echo "OK: Webhook 값이 payload에 포함되지 않는다"
    ;;
esac

SKIP_OUTPUT="$(cd "$WORK_DIR" && CI_STATUS="success" REPOSITORY="org/repo" WORKFLOW_NAME="CI" EVENT_NAME="push" \
  BRANCH="develop" COMMIT_SHA="0123456789abcdef" ACTOR="tester" RUN_NUMBER="42" RUN_ID="99" \
  SERVER_URL="https://github.com" bash "$NOTIFY_SCRIPT" send)"
SKIP_EXIT_CODE=$?
assert_eq "Secret 미설정 시 정상 종료(exit 0)" "0" "$SKIP_EXIT_CODE"
case "$SKIP_OUTPUT" in
  *"DISCORD_WEBHOOK_URL is not set"*)
    echo "OK: Secret 미설정 시 skip 메시지를 출력한다"
    ;;
  *)
    echo "FAIL: Secret 미설정 시 skip 메시지가 출력되지 않았다"
    fail=1
    ;;
  esac

FAKE_CURL_DIR="$WORK_DIR/fake-bin"
mkdir -p "$FAKE_CURL_DIR"
printf '#!/usr/bin/env bash\nexit 7\n' > "$FAKE_CURL_DIR/curl"
chmod +x "$FAKE_CURL_DIR/curl"
CURL_FAILURE_OUTPUT="$(cd "$WORK_DIR" && PATH="$FAKE_CURL_DIR:$PATH" \
  NOTIFICATION_TYPE=cd CD_STATUS=success REPOSITORY="org/repo" WORKFLOW_NAME="CD" EVENT_NAME="push" \
  ENVIRONMENT="develop" BRANCH="develop" COMMIT_SHA="0123456789abcdef" ACTOR="tester" \
  RUN_NUMBER="42" RUN_ID="99" SERVER_URL="https://github.com" DEPLOYMENT_STAGE="HEALTH_CHECK" \
  DISCORD_WEBHOOK_URL="$SECRET_MARKER" bash "$NOTIFY_SCRIPT" send)"
CURL_FAILURE_EXIT=$?
assert_eq "Webhook curl 실패 시 알림 스크립트는 정상 종료한다" "0" "$CURL_FAILURE_EXIT"
case "$CURL_FAILURE_OUTPUT" in
  *"Discord notification failed"*)
    echo "OK: Webhook curl 실패가 로그에 남는다"
    ;;
  *)
    echo "FAIL: Webhook curl 실패 로그가 없다"
    fail=1
    ;;
esac

if [ "$fail" -ne 0 ]; then
  echo "test-discord-notify FAILED"
  exit 1
fi

echo "test-discord-notify PASSED"
