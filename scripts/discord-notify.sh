#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-send}"

MAX_MESSAGE_LENGTH=200
MAX_TITLE_LENGTH=200

truncate_text() {
  local text="$1"
  local max_length="$2"
  if [ "${#text}" -gt "$max_length" ]; then
    echo "${text:0:$max_length}..."
  else
    echo "$text"
  fi
}

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

RAW_COMMIT_MESSAGE="$(git log -1 --pretty=%s 2>/dev/null || echo "")"
COMMIT_MESSAGE="$(truncate_text "$RAW_COMMIT_MESSAGE" "$MAX_MESSAGE_LENGTH")"

if [ -n "$PR_NUMBER" ]; then
  PR_FIELD_VALUE="#${PR_NUMBER} $(truncate_text "$PR_TITLE" "$MAX_TITLE_LENGTH")"
else
  PR_FIELD_VALUE="해당 없음"
fi

RUN_URL="${SERVER_URL}/${REPOSITORY}/actions/runs/${RUN_ID}"
TIMESTAMP="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

PAYLOAD="$(jq -n \
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
  '{
    embeds: [
      {
        title: $title,
        color: $color,
        fields: [
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
        ],
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

HTTP_STATUS="$(curl -sS -o /tmp/discord-response.txt -w "%{http_code}" \
  --max-time 10 \
  --retry 2 \
  --retry-delay 2 \
  -H "Content-Type: application/json" \
  -X POST \
  -d "$PAYLOAD" \
  "$DISCORD_WEBHOOK_URL" || echo "000")"

if [ "$HTTP_STATUS" -ge 200 ] && [ "$HTTP_STATUS" -lt 300 ]; then
  echo "Discord notification sent (status $HTTP_STATUS)."
else
  echo "Discord notification failed (status $HTTP_STATUS)."
fi

exit 0
