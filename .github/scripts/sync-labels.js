"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { createGitHubClient } = require("./github-api");
const { computeLabelSync } = require("./labels");

async function findAffectedIssues(client, labelName) {
  try {
    const items = await client.paginate(
      `/repos/${client.repository}/issues?state=all&labels=${encodeURIComponent(labelName)}`,
    );
    return items.map((item) => ({
      number: item.number,
      isPullRequest: Boolean(item.pull_request),
    }));
  } catch (error) {
    return [];
  }
}

async function run({ token, repository, labelsFilePath, summaryFilePath }) {
  const client = createGitHubClient({ token, repository });
  const desiredLabels = JSON.parse(fs.readFileSync(labelsFilePath, "utf8"));
  const currentLabels = await client.paginate(`/repos/${repository}/labels`);

  const { toCreate, toUpdate, toDelete } = computeLabelSync(desiredLabels, currentLabels);

  const created = [];
  const updated = [];
  const deleted = [];
  const failed = [];

  for (const label of toCreate) {
    try {
      await client.post(`/repos/${repository}/labels`, label);
      created.push(label.name);
    } catch (error) {
      failed.push({ action: "create", name: label.name, error: error.message });
    }
  }

  for (const label of toUpdate) {
    try {
      await client.patch(`/repos/${repository}/labels/${encodeURIComponent(label.name)}`, label);
      updated.push(label.name);
    } catch (error) {
      failed.push({ action: "update", name: label.name, error: error.message });
    }
  }

  for (const label of toDelete) {
    const affected = await findAffectedIssues(client, label.name);
    try {
      await client.del(`/repos/${repository}/labels/${encodeURIComponent(label.name)}`);
      deleted.push({ name: label.name, affected });
    } catch (error) {
      failed.push({ action: "delete", name: label.name, error: error.message });
    }
  }

  const summaryLines = [
    "## 라벨 동기화 결과",
    "",
    `- 생성: ${created.length ? created.join(", ") : "없음"}`,
    `- 갱신: ${updated.length ? updated.join(", ") : "없음"}`,
    `- 삭제: ${deleted.length ? deleted.map((item) => item.name).join(", ") : "없음"}`,
  ];

  if (deleted.some((item) => item.affected.length > 0)) {
    summaryLines.push("", "### 삭제된 라벨이 사용 중이던 Issue/PR");
    for (const item of deleted) {
      if (item.affected.length === 0) continue;
      const refs = item.affected
        .map((ref) => `${ref.isPullRequest ? "PR" : "Issue"} #${ref.number}`)
        .join(", ");
      summaryLines.push(`- **${item.name}**: ${refs}`);
    }
  }

  if (failed.length > 0) {
    summaryLines.push("", "### 실패");
    for (const item of failed) {
      summaryLines.push(`- ${item.action} ${item.name}: ${item.error}`);
    }
  }

  const summary = summaryLines.join("\n") + "\n";
  console.log(summary);
  if (summaryFilePath) {
    fs.appendFileSync(summaryFilePath, summary);
  }

  if (failed.length > 0) {
    process.exitCode = 1;
  }
}

if (require.main === module) {
  run({
    token: process.env.GITHUB_TOKEN,
    repository: process.env.GITHUB_REPOSITORY,
    labelsFilePath: path.join(__dirname, "..", "labels.json"),
    summaryFilePath: process.env.GITHUB_STEP_SUMMARY,
  }).catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}

module.exports = { run };
