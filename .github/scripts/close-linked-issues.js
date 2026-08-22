"use strict";

const fs = require("node:fs");
const { createGitHubClient } = require("./github-api");
const { parseLinkedIssues } = require("./linked-issues");

async function closeLinkedIssue({ client, issueNumber, pr, summary }) {
  let issue;
  try {
    issue = await client.get(`/repos/${client.repository}/issues/${issueNumber}`);
  } catch (error) {
    summary.push(`- #${issueNumber}: 조회 실패(존재하지 않거나 접근 불가) - ${error.message}`);
    return;
  }

  if (issue.pull_request) {
    summary.push(`- #${issueNumber}: Pull Request이므로 종료하지 않음`);
    return;
  }

  if (issue.state === "closed") {
    summary.push(`- #${issueNumber}: 이미 닫혀 있음 (변경 없음)`);
    return;
  }

  try {
    await client.patch(`/repos/${client.repository}/issues/${issueNumber}`, {
      state: "closed",
      state_reason: "completed",
    });
    await client.post(`/repos/${client.repository}/issues/${issueNumber}/comments`, {
      body: `연결된 PR #${pr.number}이(가) develop에 병합되어 이 이슈를 종료합니다.\n${pr.html_url}`,
    });
    summary.push(`- #${issueNumber}: 종료 완료 (PR #${pr.number})`);
  } catch (error) {
    summary.push(`- #${issueNumber}: 종료 실패 - ${error.message}`);
  }
}

async function run({ token, repository, eventPayload }) {
  const pr = eventPayload.pull_request;
  const summary = [];

  if (!pr || pr.merged !== true) {
    summary.push("PR이 병합되지 않아 이슈 종료를 건너뜁니다.");
    writeSummary(summary);
    return;
  }

  if (pr.base.ref !== "develop") {
    summary.push(`base 브랜치가 develop이 아니므로(${pr.base.ref}) 이슈 종료를 건너뜁니다.`);
    writeSummary(summary);
    return;
  }

  const issueNumbers = parseLinkedIssues(pr.body);
  summary.push(`## 연결 Issue 종료 결과 (PR #${pr.number})`, "");

  if (issueNumbers.length === 0) {
    summary.push("본문에서 연결된 Issue를 찾지 못했습니다.");
    writeSummary(summary);
    return;
  }

  const client = createGitHubClient({ token, repository });
  for (const issueNumber of issueNumbers) {
    await closeLinkedIssue({ client, issueNumber, pr, summary });
  }

  writeSummary(summary);
}

function writeSummary(lines) {
  const text = lines.join("\n") + "\n";
  console.log(text);
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, text);
  }
}

if (require.main === module) {
  const eventPath = process.env.GITHUB_EVENT_PATH;
  const eventPayload = JSON.parse(fs.readFileSync(eventPath, "utf8"));
  run({
    token: process.env.GITHUB_TOKEN,
    repository: process.env.GITHUB_REPOSITORY,
    eventPayload,
  }).catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = { run, closeLinkedIssue };
