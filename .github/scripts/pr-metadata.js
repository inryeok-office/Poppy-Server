"use strict";

const fs = require("node:fs");
const { createGitHubClient } = require("./github-api");
const {
  resolveReviewers,
  isBotLogin,
  computeDesiredAutoLabels,
  reconcileLabels,
  ALL_LABEL_NAMES,
} = require("./pr-rules");

async function fetchChangedFilePaths(client, prNumber) {
  const files = await client.paginate(`/repos/${client.repository}/pulls/${prNumber}/files`);
  return files.map((file) => file.filename);
}

async function fetchRequestedReviewerLogins(client, prNumber) {
  const data = await client.get(`/repos/${client.repository}/pulls/${prNumber}/requested_reviewers`);
  return (data.users || []).map((user) => user.login);
}

async function fetchReviewedLogins(client, prNumber) {
  const reviews = await client.paginate(`/repos/${client.repository}/pulls/${prNumber}/reviews`);
  return [...new Set(reviews.map((review) => review.user.login))];
}

async function applyAssignee({ client, pr, summary }) {
  const author = pr.user.login;

  if (isBotLogin(author) || pr.user.type === "Bot") {
    summary.push(`- Assignee: 건너뜀 (작성자가 Bot 계정: ${author})`);
    return;
  }

  const alreadyAssigned = (pr.assignees || []).some((assignee) => assignee.login === author);
  if (alreadyAssigned) {
    summary.push(`- Assignee: 이미 지정됨 (${author})`);
    return;
  }

  try {
    await client.post(`/repos/${client.repository}/issues/${pr.number}/assignees`, {
      assignees: [author],
    });
    summary.push(`- Assignee: ${author} 지정 완료`);
  } catch (error) {
    summary.push(`- Assignee: ${author} 지정 실패 - ${error.message}`);
  }
}

async function applyReviewers({ client, pr, summary }) {
  if (pr.draft) {
    summary.push("- Reviewer: Draft PR이라 요청하지 않음");
    return;
  }

  const candidates = resolveReviewers(pr.user.login);
  if (candidates.length === 0) {
    summary.push("- Reviewer: 요청 대상 없음 (Bot 작성 PR)");
    return;
  }

  const [requested, reviewed] = await Promise.all([
    fetchRequestedReviewerLogins(client, pr.number),
    fetchReviewedLogins(client, pr.number),
  ]);

  const toRequest = candidates.filter(
    (login) => !requested.includes(login) && !reviewed.includes(login) && login !== pr.user.login,
  );

  if (toRequest.length === 0) {
    summary.push("- Reviewer: 추가로 요청할 대상 없음(이미 요청/리뷰됨)");
    return;
  }

  try {
    await client.post(`/repos/${client.repository}/pulls/${pr.number}/requested_reviewers`, {
      reviewers: toRequest,
    });
    summary.push(`- Reviewer: ${toRequest.join(", ")} 요청 완료`);
  } catch (error) {
    const succeeded = [];
    const failed = [];
    for (const login of toRequest) {
      try {
        await client.post(`/repos/${client.repository}/pulls/${pr.number}/requested_reviewers`, {
          reviewers: [login],
        });
        succeeded.push(login);
      } catch (individualError) {
        failed.push(`${login} (${individualError.message})`);
      }
    }
    if (succeeded.length > 0) {
      summary.push(`- Reviewer: ${succeeded.join(", ")} 요청 완료`);
    }
    if (failed.length > 0) {
      summary.push(`- Reviewer 요청 실패: ${failed.join(", ")}`);
    }
  }
}

async function applyLabels({ client, pr, summary }) {
  const files = await fetchChangedFilePaths(client, pr.number);
  const desired = computeDesiredAutoLabels({
    baseRef: pr.base.ref,
    headRef: pr.head.ref,
    files,
    title: pr.title,
    body: pr.body,
  });
  const currentLabelNames = (pr.labels || []).map((label) => label.name);
  const { toAdd, toRemove } = reconcileLabels(currentLabelNames, desired, ALL_LABEL_NAMES);

  if (toAdd.length > 0) {
    try {
      await client.post(`/repos/${client.repository}/issues/${pr.number}/labels`, { labels: toAdd });
      summary.push(`- 라벨 추가: ${toAdd.join(", ")}`);
    } catch (error) {
      summary.push(`- 라벨 추가 실패: ${error.message}`);
    }
  }

  for (const label of toRemove) {
    try {
      await client.del(`/repos/${client.repository}/issues/${pr.number}/labels/${encodeURIComponent(label)}`);
      summary.push(`- 라벨 제거: ${label}`);
    } catch (error) {
      summary.push(`- 라벨 제거 실패(${label}): ${error.message}`);
    }
  }

  if (toAdd.length === 0 && toRemove.length === 0) {
    summary.push("- 라벨: 변경 없음");
  }
}

async function applyPrMetadata({ client, pr, summary, requestReviewers = true }) {
  await applyAssignee({ client, pr, summary });
  if (requestReviewers) {
    await applyReviewers({ client, pr, summary });
  } else {
    summary.push("- Reviewer: 이미 병합/종료된 PR이라 새로 요청하지 않음");
  }
  await applyLabels({ client, pr, summary });
}

async function run({ token, repository, eventPayload }) {
  const pr = eventPayload.pull_request;
  if (!pr) {
    throw new Error("이벤트 페이로드에 pull_request 정보가 없습니다.");
  }

  const client = createGitHubClient({ token, repository });
  const summary = [`## PR 메타데이터 자동화 결과 (#${pr.number})`, ""];

  await applyPrMetadata({ client, pr, summary });

  const summaryText = summary.join("\n") + "\n";
  console.log(summaryText);
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, summaryText);
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

module.exports = { run, applyPrMetadata, applyAssignee, applyReviewers, applyLabels, fetchChangedFilePaths };
