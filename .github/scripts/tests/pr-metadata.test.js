"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");
const { run, shouldRunPrMetadata, applyReviewers } = require("../pr-metadata");

function pullRequestEvent({ state = "open", action = "edited", merged = false } = {}) {
  return {
    action,
    pull_request: {
      state,
      merged,
      number: 31,
      user: { login: "outside-contributor", type: "User" },
    },
  };
}

test("open PR metadata policy is preserved for supported events", () => {
  for (const action of ["opened", "reopened", "synchronize", "edited", "ready_for_review"]) {
    assert.equal(shouldRunPrMetadata(pullRequestEvent({ action })), true);
  }
});

test("closed and merged PR metadata policy skips edited events", () => {
  assert.equal(shouldRunPrMetadata(pullRequestEvent({ state: "closed" })), false);
  assert.equal(
    shouldRunPrMetadata(pullRequestEvent({ state: "closed", merged: true })),
    false,
  );
});

test("closed PR exits before creating the GitHub client", async () => {
  await assert.doesNotReject(() =>
    run({ eventPayload: pullRequestEvent({ state: "closed", merged: true }) }),
  );
});

test("draft PR does not request reviewers", async () => {
  const calls = [];
  const summary = [];
  const client = {
    post: async (...args) => calls.push(args),
    get: async () => {
      throw new Error("draft PR should not query reviewers");
    },
    paginate: async () => {
      throw new Error("draft PR should not query reviews");
    },
  };

  await applyReviewers({
    client,
    pr: {
      draft: true,
      number: 31,
      user: { login: "outside-contributor" },
    },
    summary,
  });

  assert.deepEqual(calls, []);
  assert.match(summary[0], /Draft/);
});

test("workflow runs only for open PRs and checks out the trusted base branch", () => {
  const workflowPath = path.resolve(__dirname, "../../workflows/pr-metadata.yml");
  const workflow = fs.readFileSync(workflowPath, "utf8");

  assert.match(
    workflow,
    /if: \$\{\{ github\.event\.pull_request\.state == 'open' \}\}/,
  );
  assert.match(
    workflow,
    /ref: \$\{\{ github\.event\.pull_request\.base\.ref \}\}/,
  );
  assert.doesNotMatch(workflow, /pull_request\.head\.(sha|ref)/);
});
