"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  resolveReviewers,
  matchBranchLabel,
  isReleasePr,
  matchPathLabel,
  detectBreakingChange,
  computeDesiredAutoLabels,
  reconcileLabels,
} = require("../pr-rules");

test("hej090224 작성 PR은 s26059-maker, bongbonggoo를 리뷰어로 요청한다", () => {
  assert.deepEqual(resolveReviewers("hej090224"), ["s26059-maker", "bongbonggoo"]);
});

test("s26059-maker 작성 PR은 bongbonggoo, hej090224를 리뷰어로 요청한다", () => {
  assert.deepEqual(resolveReviewers("s26059-maker"), ["bongbonggoo", "hej090224"]);
});

test("bongbonggoo 작성 PR은 s26059-maker, hej090224를 리뷰어로 요청한다", () => {
  assert.deepEqual(resolveReviewers("bongbonggoo"), ["s26059-maker", "hej090224"]);
});

test("외부 작성자 PR은 3명 모두 리뷰어로 요청한다", () => {
  assert.deepEqual(resolveReviewers("outside-contributor"), ["s26059-maker", "bongbonggoo", "hej090224"]);
});

test("Bot 계정 작성 PR은 리뷰어를 요청하지 않는다", () => {
  assert.deepEqual(resolveReviewers("dependabot[bot]"), []);
});

test("브랜치 prefix에 따라 라벨을 결정한다", () => {
  assert.equal(matchBranchLabel("feature/1-x"), "feature");
  assert.equal(matchBranchLabel("fix/1-x"), "bug");
  assert.equal(matchBranchLabel("hotfix/1-x"), "bug");
  assert.equal(matchBranchLabel("refactor/1-x"), "refactor");
  assert.equal(matchBranchLabel("chore/1-x"), "chore");
  assert.equal(matchBranchLabel("docs/1-x"), "documentation");
  assert.equal(matchBranchLabel("release/1.0.0"), "release");
  assert.equal(matchBranchLabel("random-branch"), null);
});

test("develop -> main PR은 release로 판정한다", () => {
  assert.equal(isReleasePr("main", "develop"), true);
  assert.equal(isReleasePr("develop", "feature/1-x"), false);
});

test(".github 경로 변경은 ci 라벨과 매치된다", () => {
  assert.equal(matchPathLabel(".github/workflows/ci.yml"), "ci");
});

test("CI 관련 scripts 파일은 ci 라벨과 매치된다", () => {
  assert.equal(matchPathLabel("scripts/discord-notify.sh"), "ci");
  assert.equal(matchPathLabel("scripts/harness-check.sh"), "ci");
});

test("CI와 무관한 scripts 파일은 매치되지 않는다", () => {
  assert.equal(matchPathLabel("scripts/local-dev-helper.sh"), null);
});

test("테스트 경로/파일명은 test 라벨과 매치된다", () => {
  assert.equal(matchPathLabel("src/test/kotlin/team/inreok/poppyserver/FooTest.kt"), "test");
  assert.equal(matchPathLabel("src/main/kotlin/team/inreok/poppyserver/FooTests.kt"), "test");
});

test("Gradle 설정 변경은 dependencies 라벨과 매치된다", () => {
  assert.equal(matchPathLabel("build.gradle.kts"), "dependencies");
  assert.equal(matchPathLabel("settings.gradle.kts"), "dependencies");
  assert.equal(matchPathLabel("gradle/wrapper/gradle-wrapper.properties"), "dependencies");
});

test("도메인 경로 변경은 해당 domain 라벨과 매치된다", () => {
  assert.equal(
    matchPathLabel("src/main/kotlin/team/inreok/poppyserver/domain/session/model/Session.kt"),
    "domain: session",
  );
  assert.equal(
    matchPathLabel("src/main/kotlin/team/inreok/poppyserver/domain/robot/application/RobotService.kt"),
    "domain: robot",
  );
});

test("global 경로 변경은 domain 라벨과 매치되지 않는다", () => {
  assert.equal(matchPathLabel("src/main/kotlin/team/inreok/poppyserver/global/error/ErrorCode.kt"), null);
});

test("Conventional Commits ! 표기는 breaking change로 감지한다", () => {
  assert.equal(detectBreakingChange("feat(api)!: 응답 형식을 변경한다", ""), true);
});

test("본문의 BREAKING CHANGE 문구는 대소문자/구분자와 무관하게 감지한다", () => {
  assert.equal(detectBreakingChange("feat: 정상 변경", "설명\r\n\r\nBREAKING-CHANGE: 필드 제거"), true);
  assert.equal(detectBreakingChange("feat: 정상 변경", "breaking change: 필드 제거"), true);
});

test("breaking change 표기가 없으면 감지하지 않는다", () => {
  assert.equal(detectBreakingChange("feat: 정상 변경", "평범한 설명"), false);
});

test("PR 종합 상태에서 자동 라벨 집합을 계산한다", () => {
  const desired = computeDesiredAutoLabels({
    baseRef: "develop",
    headRef: "feature/1-add-session",
    files: ["src/main/kotlin/team/inreok/poppyserver/domain/session/model/Session.kt", "build.gradle.kts"],
    title: "feat(session): 세션 모델을 추가한다",
    body: "Closes #1",
  });
  assert.deepEqual(
    [...desired].sort(),
    ["dependencies", "domain: session", "feature"].sort(),
  );
});

test("자동 관리 라벨만 재계산하며 더 이상 조건에 맞지 않으면 제거한다", () => {
  const { toAdd, toRemove } = reconcileLabels(["feature", "chore"], new Set(["feature", "test"]), [
    "feature",
    "chore",
    "test",
  ]);
  assert.deepEqual(toAdd.sort(), ["test"]);
  assert.deepEqual(toRemove.sort(), ["chore"]);
});

test("자동 관리 대상이 아닌 라벨은 건드리지 않는다", () => {
  const { toAdd, toRemove } = reconcileLabels(["manual-label"], new Set(), ["feature"]);
  assert.deepEqual(toAdd, []);
  assert.deepEqual(toRemove, []);
});
