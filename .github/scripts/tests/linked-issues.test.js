"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parseLinkedIssues } = require("../linked-issues");

test("Closes #N 형식을 인식한다", () => {
  assert.deepEqual(parseLinkedIssues("Closes #12"), [12]);
});

test("Fixes, Resolves 키워드를 인식한다", () => {
  assert.deepEqual(parseLinkedIssues("Fixes #7"), [7]);
  assert.deepEqual(parseLinkedIssues("Resolves #9"), [9]);
});

test("과거형(Closed/Fixed/Resolved) 키워드를 인식한다", () => {
  assert.deepEqual(parseLinkedIssues("Closed #1"), [1]);
  assert.deepEqual(parseLinkedIssues("Fixed #2"), [2]);
  assert.deepEqual(parseLinkedIssues("Resolved #3"), [3]);
});

test("대소문자를 구분하지 않는다", () => {
  assert.deepEqual(parseLinkedIssues("closes #5"), [5]);
  assert.deepEqual(parseLinkedIssues("CLOSES #5"), [5]);
});

test("콤마로 구분된 여러 이슈를 인식한다", () => {
  assert.deepEqual(parseLinkedIssues("Closes #12, #13"), [12, 13]);
});

test("and로 구분된 여러 이슈를 인식한다", () => {
  assert.deepEqual(parseLinkedIssues("Closes #12 and #13"), [12, 13]);
});

test("여러 줄에 작성된 여러 연결 이슈를 모두 인식한다", () => {
  const body = "작업 배경\n\nCloses #1\n\n다른 설명\n\nFixes #2\n";
  assert.deepEqual(parseLinkedIssues(body), [1, 2]);
});

test("CRLF 줄바꿈에서도 동작한다", () => {
  const body = "설명\r\n\r\nCloses #4\r\n";
  assert.deepEqual(parseLinkedIssues(body), [4]);
});

test("키워드 없는 단순 #번호 언급은 인식하지 않는다", () => {
  assert.deepEqual(parseLinkedIssues("See #5 for context"), []);
});

test("다른 저장소의 owner/repo#번호는 인식하지 않는다", () => {
  assert.deepEqual(parseLinkedIssues("Closes octocat/other-repo#12"), []);
});

test("같은 목록 안에서 자기 저장소 참조만 인식한다", () => {
  assert.deepEqual(parseLinkedIssues("Closes #12, octocat/other-repo#99"), [12]);
});

test("중복 번호는 한 번만 반환한다", () => {
  assert.deepEqual(parseLinkedIssues("Closes #12\n\nFixes #12"), [12]);
});

test("연결 키워드가 없으면 빈 배열을 반환한다", () => {
  assert.deepEqual(parseLinkedIssues("관련 없는 일반 설명입니다."), []);
});

test("본문이 없으면 빈 배열을 반환한다", () => {
  assert.deepEqual(parseLinkedIssues(null), []);
  assert.deepEqual(parseLinkedIssues(undefined), []);
  assert.deepEqual(parseLinkedIssues(""), []);
});
