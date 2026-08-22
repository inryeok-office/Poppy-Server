"use strict";

const fs = require("node:fs");
const { createGitHubClient } = require("./github-api");
const { parseLinkedIssues } = require("./linked-issues");
const { closeLinkedIssue } = require("./close-linked-issues");
const { applyPrMetadata, fetchChangedFilePaths } = require("./pr-metadata");
const { isBotLogin, computeDesiredAutoLabels, reconcileLabels, ALL_LABEL_NAMES } = require("./pr-rules");

const DEFAULT_CLOSED_PR_NUMBERS = [2, 4, 6, 8, 10, 12, 14];
const DEFAULT_OPEN_PR_NUMBERS = [15];
const DEFAULT_EXPECTED_ISSUE_NUMBERS = [1, 3, 5, 7, 9, 11, 13];

function parseNumberList(value, fallback) {
  if (!value || String(value).trim() === "") return fallback;
  return String(value)
    .split(",")
    .map((part) => Number(part.trim()))
    .filter((num) => Number.isInteger(num) && num > 0);
}

async function applyIssueMetadata({ client, issueNumber, sourcePr, summary }) {
  let issue;
  try {
    issue = await client.get(`/repos/${client.repository}/issues/${issueNumber}`);
  } catch (error) {
    summary.push(`  - Issue #${issueNumber} 조회 실패: ${error.message}`);
    return;
  }

  const author = issue.user.login;
  if (isBotLogin(author)) {
    summary.push(`  - Issue #${issueNumber} Assignee: 건너뜀 (Bot 작성)`);
  } else {
    const alreadyAssigned = (issue.assignees || []).some((assignee) => assignee.login === author);
    if (alreadyAssigned) {
      summary.push(`  - Issue #${issueNumber} Assignee: 이미 지정됨 (${author})`);
    } else {
      try {
        await client.post(`/repos/${client.repository}/issues/${issueNumber}/assignees`, {
          assignees: [author],
        });
        summary.push(`  - Issue #${issueNumber} Assignee: ${author} 지정 완료`);
      } catch (error) {
        summary.push(`  - Issue #${issueNumber} Assignee 지정 실패: ${error.message}`);
      }
    }
  }

  if (!sourcePr) return;

  const files = await fetchChangedFilePaths(client, sourcePr.number);
  const desired = computeDesiredAutoLabels({
    baseRef: sourcePr.base.ref,
    headRef: sourcePr.head.ref,
    files,
    title: sourcePr.title,
    body: sourcePr.body,
  });
  const currentLabelNames = (issue.labels || []).map((label) => label.name);
  const { toAdd } = reconcileLabels(currentLabelNames, desired, ALL_LABEL_NAMES);

  if (toAdd.length === 0) {
    summary.push(`  - Issue #${issueNumber} 라벨: 변경 없음`);
    return;
  }

  try {
    await client.post(`/repos/${client.repository}/issues/${issueNumber}/labels`, { labels: toAdd });
    summary.push(`  - Issue #${issueNumber} 라벨 추가: ${toAdd.join(", ")}`);
  } catch (error) {
    summary.push(`  - Issue #${issueNumber} 라벨 추가 실패: ${error.message}`);
  }
}

async function processClosedPr({ client, number, summary, handledIssueNumbers }) {
  let pr;
  try {
    pr = await client.get(`/repos/${client.repository}/pulls/${number}`);
  } catch (error) {
    summary.push(`- PR #${number}: 조회 실패 - ${error.message}`);
    return;
  }

  if (pr.merged !== true || pr.base.ref !== "develop") {
    summary.push(
      `- PR #${number}: develop 병합 검증 실패(merged=${pr.merged}, base=${pr.base.ref}) - 건너뜀`,
    );
    return;
  }

  summary.push(`- PR #${number} "${pr.title}" (작성자: ${pr.user.login})`);
  await applyPrMetadata({ client, pr, summary, requestReviewers: false });

  const issueNumbers = parseLinkedIssues(pr.body);
  if (issueNumbers.length === 0) {
    summary.push("  - 본문에서 연결된 Issue를 찾지 못함");
    return;
  }

  for (const issueNumber of issueNumbers) {
    await closeLinkedIssue({ client, issueNumber, pr, summary: wrapIndent(summary) });
    await applyIssueMetadata({ client, issueNumber, sourcePr: pr, summary });
    handledIssueNumbers.add(issueNumber);
  }
}

function wrapIndent(summary) {
  return {
    push: (...lines) => summary.push(...lines.map((line) => `  ${line}`)),
  };
}

async function processOpenPr({ client, number, summary }) {
  let pr;
  try {
    pr = await client.get(`/repos/${client.repository}/pulls/${number}`);
  } catch (error) {
    summary.push(`- PR #${number}: 조회 실패 - ${error.message}`);
    return;
  }

  if (pr.state !== "open") {
    summary.push(`- PR #${number}: 현재 상태가 open이 아님(${pr.state}) - 건너뜀(닫거나 병합하지 않음)`);
    return;
  }

  summary.push(`- PR #${number} "${pr.title}" (작성자: ${pr.user.login}, base: ${pr.base.ref})`);
  await applyPrMetadata({ client, pr, summary, requestReviewers: true });
}

async function run({
  token,
  repository,
  closedPrNumbers = DEFAULT_CLOSED_PR_NUMBERS,
  openPrNumbers = DEFAULT_OPEN_PR_NUMBERS,
  expectedIssueNumbers = DEFAULT_EXPECTED_ISSUE_NUMBERS,
}) {
  const client = createGitHubClient({ token, repository });
  const summary = ["## 소급 적용 결과", ""];
  const handledIssueNumbers = new Set();

  summary.push("### 기존 완료 PR (Assignee/라벨 소급, Reviewer 요청 없음, 연결 Issue 종료)");
  for (const number of closedPrNumbers) {
    await processClosedPr({ client, number, summary, handledIssueNumbers });
  }

  summary.push("", "### 예상 Issue 커버리지");
  for (const expected of expectedIssueNumbers) {
    if (handledIssueNumbers.has(expected)) {
      summary.push(`- Issue #${expected}: 처리됨`);
    } else {
      summary.push(`- ⚠️ Issue #${expected}: 이번 실행에서 연결 PR을 통해 처리되지 않음 (수동 확인 필요)`);
    }
  }

  summary.push("", "### 열린 PR (Assignee/Reviewer/라벨 적용, 종료·병합 없음)");
  for (const number of openPrNumbers) {
    await processOpenPr({ client, number, summary });
  }

  const summaryText = summary.join("\n") + "\n";
  console.log(summaryText);
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summaryText);
  }
}

if (require.main === module) {
  run({
    token: process.env.GITHUB_TOKEN,
    repository: process.env.GITHUB_REPOSITORY,
    closedPrNumbers: parseNumberList(process.env.CLOSED_PR_NUMBERS, DEFAULT_CLOSED_PR_NUMBERS),
    openPrNumbers: parseNumberList(process.env.OPEN_PR_NUMBERS, DEFAULT_OPEN_PR_NUMBERS),
    expectedIssueNumbers: parseNumberList(process.env.EXPECTED_ISSUE_NUMBERS, DEFAULT_EXPECTED_ISSUE_NUMBERS),
  }).catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = { run, parseNumberList };
