#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-send}"

MAX_MESSAGE_LENGTH=200
MAX_TITLE_LENGTH=200
MAX_REASON_LENGTH=500

truncate_text() {
  local text="$1"
  local max_length="$2"
  if [ "${#text}" -gt "$max_length" ]; then
    echo "${text:0:$max_length}..."
  else
    echo "$text"
  fi
}

NOTIFICATION_TYPE="${NOTIFICATION_TYPE:-ci}"

if [ "$NOTIFICATION_TYPE" = "cd" ]; then
  STATUS="${CD_STATUS:-unknown}"
  ENVIRONMENT="${ENVIRONMENT:-develop}"
  DEPLOYMENT_STAGE="${DEPLOYMENT_STAGE:-UNKNOWN}"
  HEALTH_STATUS="${HEALTH_STATUS:-해당 없음}"
  FAILURE_REASON="$(truncate_text "${FAILURE_REASON:-Deploy step failed — Actions 로그 확인}" "$MAX_REASON_LENGTH")"

  case "$STATUS" in
    start)
      EMBED_TITLE="🚀 Poppy-Server 배포 시작"
      EMBED_COLOR=3447003
      DISPLAY_STATUS="STARTED"
      ;;
    replacement)
      EMBED_TITLE="⏸️ 기존 애플리케이션 교체 시작"
      EMBED_COLOR=15105570
      DISPLAY_STATUS="REPLACING"
      ;;
    success)
      EMBED_TITLE="✅ Poppy-Server 배포 완료"
      EMBED_COLOR=3066993
      DISPLAY_STATUS="HEALTHY"
      ;;
    failure)
      EMBED_TITLE="❌ Poppy-Server 배포 실패"
      EMBED_COLOR=15158332
      DISPLAY_STATUS="FAILED"
      ;;
    *)
      EMBED_TITLE="ℹ️ CD 결과: ${STATUS}"
      EMBED_COLOR=9807270
      DISPLAY_STATUS="$(printf '%s' "$STATUS" | tr '[:lower:]' '[:upper:]')"
      ;;
  esac
else
  STATUS="${CI_STATUS:-unknown}"

  case "$STATUS" in
    success)
      EMBED_TITLE="✅ CI 성공"
      EMBED_COLOR=3066993
      ;;
    failure)
      EMBED_TITLE="❌ CI 실패"
      EMBED_COLOR=15158332
      ;;
    cancelled)
      EMBED_TITLE="⚠️ CI 취소"
      EMBED_COLOR=15105570
      ;;
    *)
      EMBED_TITLE="ℹ️ CI 결과: ${STATUS}"
      EMBED_COLOR=9807270
      ;;
  esac
fi

REPOSITORY="${REPOSITORY:-unknown/unknown}"
WORKFLOW_NAME="${WORKFLOW_NAME:-unknown}"
EVENT_NAME="${EVENT_NAME:-unknown}"
BRANCH="${BRANCH:-unknown}"
COMMIT_SHA="${COMMIT_SHA:-unknown}"
SHORT_SHA="${COMMIT_SHA:0:7}"
ACTOR="${ACTOR:-unknown}"
PR_NUMBER="${PR_NUMBER:-}"
PR_TITLE="${PR_TITLE:-}"
RUN_NUMBER="${RUN_NUMBER:-0}"
RUN_ID="${RUN_ID:-0}"
SERVER_URL="${SERVER_URL:-https://github.com}"
DURATION_SECONDS="${CD_DURATION_SECONDS:-}"

RAW_COMMIT_MESSAGE="$(git log -1 --pretty=%s 2>/dev/null || echo "")"
COMMIT_MESSAGE="$(truncate_text "$RAW_COMMIT_MESSAGE" "$MAX_MESSAGE_LENGTH")"

if [ -n "$PR_NUMBER" ]; then
  PR_FIELD_VALUE="#${PR_NUMBER} $(truncate_text "$PR_TITLE" "$MAX_TITLE_LENGTH")"
else
  PR_FIELD_VALUE="해당 없음"
fi

RUN_URL="${SERVER_URL}/${REPOSITORY}/actions/runs/${RUN_ID}"
TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

format_duration() {
  local seconds="$1"
  if ! [[ "$seconds" =~ ^[0-9]+$ ]]; then
    return 0
  fi

  if [ "$seconds" -ge 3600 ]; then
    printf '%dh %dm %ds' "$((seconds / 3600))" "$(((seconds % 3600) / 60))" "$((seconds % 60))"
  elif [ "$seconds" -ge 60 ]; then
    printf '%dm %ds' "$((seconds / 60))" "$((seconds % 60))"
  else
    printf '%ds' "$seconds"
  fi
}

DURATION="$(format_duration "$DURATION_SECONDS")"

PAYLOAD="$(jq -n \
  --arg notificationType "$NOTIFICATION_TYPE" \
  --arg title "$EMBED_TITLE" \
  --argjson color "$EMBED_COLOR" \
  --arg repository "$REPOSITORY" \
  --arg workflow "$WORKFLOW_NAME" \
  --arg status "$STATUS" \
  --arg event "$EVENT_NAME" \
  --arg branch "$BRANCH" \
  --arg sha "$SHORT_SHA" \
  --arg commitMessage "$COMMIT_MESSAGE" \
  --arg actor "$ACTOR" \
  --arg pr "$PR_FIELD_VALUE" \
  --arg runNumber "$RUN_NUMBER" \
  --arg runUrl "$RUN_URL" \
  --arg timestamp "$TIMESTAMP" \
  --arg environment "${ENVIRONMENT:-}" \
  --arg deploymentStage "${DEPLOYMENT_STAGE:-}" \
  --arg displayStatus "${DISPLAY_STATUS:-}" \
  --arg healthStatus "${HEALTH_STATUS:-}" \
  --arg duration "$DURATION" \
  --arg failureReason "${FAILURE_REASON:-}" \
  '{
    embeds: [
      {
        title: $title,
        color: $color,
        fields: (
          if $notificationType == "cd" then
            [
              { name: "Repository", value: $repository, inline: true },
              { name: "Environment", value: $environment, inline: true },
              { name: "Branch", value: $branch, inline: true },
              { name: "Commit", value: $sha, inline: true },
              { name: "Commit Message", value: $commitMessage, inline: false },
              { name: "Triggered By", value: $actor, inline: true },
              { name: "Workflow Run", value: ("#" + $runNumber), inline: true },
              { name: "Deployment Stage", value: $deploymentStage, inline: true },
              { name: "Status", value: $displayStatus, inline: true },
              { name: "Health", value: $healthStatus, inline: true }
            ]
            + (if $duration == "" then [] else [{ name: "Duration", value: $duration, inline: true }] end)
            + (if $status == "failure" then [{ name: "Failure Reason", value: $failureReason, inline: false }] else [] end)
            + [{ name: "Actions", value: $runUrl, inline: false }]
          else
            [
              { name: "Repository", value: $repository, inline: true },
              { name: "Workflow", value: $workflow, inline: true },
              { name: "Status", value: $status, inline: true },
              { name: "Event", value: $event, inline: true },
              { name: "Branch", value: $branch, inline: true },
              { name: "Commit", value: $sha, inline: true },
              { name: "Commit Message", value: $commitMessage, inline: false },
              { name: "Actor", value: $actor, inline: true },
              { name: "Pull Request", value: $pr, inline: true },
              { name: "Run", value: ("#" + $runNumber), inline: true },
              { name: "Actions", value: $runUrl, inline: false }
            ]
          end
        ),
        timestamp: $timestamp
      }
    ],
    allowed_mentions: { parse: [] }
  }')"

if [ "$MODE" = "print" ]; then
  echo "$PAYLOAD"
  exit 0
fi

if [ -z "${DISCORD_WEBHOOK_URL:-}" ]; then
  echo "DISCORD_WEBHOOK_URL is not set. Skipping Discord notification."
  exit 0
fi

RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE"' EXIT

if HTTP_STATUS="$(curl -sS -o "$RESPONSE_FILE" -w "%{http_code}" \
  --max-time 10 \
  --retry 2 \
  --retry-delay 2 \
  -H "Content-Type: application/json" \
  -X POST \
  -d "$PAYLOAD" \
  "$DISCORD_WEBHOOK_URL")"; then
  :
else
  HTTP_STATUS="000"
fi

if [[ "$HTTP_STATUS" =~ ^2[0-9]{2}$ ]]; then
  echo "Discord notification sent (status $HTTP_STATUS)."
else
  echo "Discord notification failed (status $HTTP_STATUS)."
fi

exit 0
