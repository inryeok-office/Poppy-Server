"use strict";

const path = require("node:path");
const approvedLabels = require("../labels.json");

const REVIEW_CANDIDATES = ["s26059-maker", "bongbonggoo", "hej090224"];

const ALL_LABEL_NAMES = approvedLabels.map((label) => label.name);

const DOMAIN_NAMES = ["session", "mission", "simulation", "execution", "robot", "admin", "agent"];

function isBotLogin(login) {
  return typeof login === "string" && login.toLowerCase().endsWith("[bot]");
}

function resolveReviewers(author, candidates = REVIEW_CANDIDATES) {
  if (!author || isBotLogin(author)) {
    return [];
  }
  const normalizedAuthor = author.toLowerCase();
  const isKnownDeveloper = candidates.some((name) => name.toLowerCase() === normalizedAuthor);
  if (!isKnownDeveloper) {
    return [...candidates];
  }
  return candidates.filter((name) => name.toLowerCase() !== normalizedAuthor);
}

function matchBranchLabel(branchName) {
  if (!branchName) return null;
  if (/^feature\//.test(branchName)) return "feature";
  if (/^fix\//.test(branchName)) return "bug";
  if (/^hotfix\//.test(branchName)) return "bug";
  if (/^refactor\//.test(branchName)) return "refactor";
  if (/^chore\//.test(branchName)) return "chore";
  if (/^docs\//.test(branchName)) return "documentation";
  if (/^release\//.test(branchName)) return "release";
  return null;
}

function isReleasePr(baseRef, headRef) {
  return baseRef === "main" && headRef === "develop";
}

const CI_SCRIPT_KEYWORDS = ["harness", "discord", "sync-labels", "pr-metadata", "close-linked-issues", "label"];

function matchPathLabel(filePath) {
  const normalized = filePath.replace(/\\/g, "/");
  const basename = path.posix.basename(normalized);

  if (normalized.startsWith(".github/")) return "ci";

  if (normalized.startsWith("scripts/")) {
    const lowerBasename = basename.toLowerCase();
    if (CI_SCRIPT_KEYWORDS.some((keyword) => lowerBasename.includes(keyword))) {
      return "ci";
    }
  }

  if (normalized.startsWith("src/test/")) return "test";
  if (/Test\.kt$/.test(basename) || /Tests\.kt$/.test(basename)) return "test";

  if (normalized === "build.gradle.kts") return "dependencies";
  if (normalized === "settings.gradle.kts") return "dependencies";
  if (normalized === "gradle.properties") return "dependencies";
  if (normalized === "gradle/libs.versions.toml") return "dependencies";
  if (normalized.startsWith("gradle/wrapper/")) return "dependencies";

  for (const domain of DOMAIN_NAMES) {
    const marker = `/domain/${domain}/`;
    if (normalized.startsWith("src/main/") && normalized.includes(marker)) {
      return `domain: ${domain}`;
    }
  }

  return null;
}

function detectBreakingChange(title, body) {
  const normalizedTitle = (title || "").trim();
  const normalizedBody = (body || "").replace(/\r\n/g, "\n");
  if (/^[a-z]+(\([^)]*\))?!:/i.test(normalizedTitle)) {
    return true;
  }
  if (/breaking[ -]change/i.test(normalizedBody)) {
    return true;
  }
  return false;
}

function computeDesiredAutoLabels({ baseRef, headRef, files, title, body }) {
  const labels = new Set();

  const branchLabel = matchBranchLabel(headRef);
  if (branchLabel) labels.add(branchLabel);

  if (isReleasePr(baseRef, headRef)) {
    labels.add("release");
  }

  for (const filePath of files || []) {
    const label = matchPathLabel(filePath);
    if (label) labels.add(label);
  }

  if (detectBreakingChange(title, body)) {
    labels.add("breaking-change");
  }

  return labels;
}

function reconcileLabels(currentLabelNames, desiredAutoLabels, autoManagedLabels = ALL_LABEL_NAMES) {
  const current = new Set(currentLabelNames);
  const autoManaged = new Set(autoManagedLabels);

  const toAdd = [...desiredAutoLabels].filter((name) => !current.has(name));
  const toRemove = [...current].filter((name) => autoManaged.has(name) && !desiredAutoLabels.has(name));

  return { toAdd, toRemove };
}

module.exports = {
  REVIEW_CANDIDATES,
  ALL_LABEL_NAMES,
  DOMAIN_NAMES,
  isBotLogin,
  resolveReviewers,
  matchBranchLabel,
  isReleasePr,
  matchPathLabel,
  detectBreakingChange,
  computeDesiredAutoLabels,
  reconcileLabels,
};
