"use strict";

const KEYWORD = "close[sd]?|fix(?:es|ed)?|resolve[sd]?";
const REF = "(?:[\\w.-]+/[\\w.-]+#\\d+|#\\d+)";
const SEP = "(?:\\s*,\\s*|\\s+and\\s+)";
const LINKED_ISSUE_PATTERN = new RegExp(`\\b(?:${KEYWORD})\\b\\s*:?\\s*(${REF}(?:${SEP}${REF})*)`, "gi");
const REF_SPLIT_PATTERN = /\s*,\s*|\s+and\s+/i;

function parseLinkedIssues(body) {
  if (!body) return [];

  const normalized = body.replace(/\r\n/g, "\n");
  const numbers = new Set();

  let match;
  LINKED_ISSUE_PATTERN.lastIndex = 0;
  while ((match = LINKED_ISSUE_PATTERN.exec(normalized)) !== null) {
    const refs = match[1].split(REF_SPLIT_PATTERN).map((ref) => ref.trim()).filter(Boolean);
    for (const ref of refs) {
      const sameRepoMatch = ref.match(/^#(\d+)$/);
      if (sameRepoMatch) {
        numbers.add(Number(sameRepoMatch[1]));
      }
      // owner/repo#N 형태는 다른 저장소 참조이므로 무시한다.
    }
  }

  return [...numbers].sort((a, b) => a - b);
}

module.exports = { parseLinkedIssues };
