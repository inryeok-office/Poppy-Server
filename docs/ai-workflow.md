# AI 작업 방식 (Claude Code / Codex)

이 저장소는 Claude Code와 Codex만 지원한다. 다른 AI 도구용 설정 파일은 만들지 않는다.

## 공통 하네스

`CLAUDE.md`와 `AGENTS.md`는 루트에 위치하며 byte 단위로 완전히 동일해야 한다. 두 파일 중 하나만 수정하는 것은 금지되며, `scripts/harness-check.sh`(Bash)와 `scripts/harness-check.ps1`(PowerShell)로 로컬에서 동일성을 검증할 수 있다.

```bash
bash scripts/harness-check.sh
```

```powershell
./scripts/harness-check.ps1
```

두 스크립트는 같은 기준으로 검사하며, CI에도 동일한 검사가 포함된다.

## 작업 시작 전 확인 사항

AI는 코드를 변경하기 전 다음을 모두 확인한다.

1. 현재 작업에 대응하는 GitHub Issue가 존재하는가
2. Issue의 완료 조건을 읽었는가
3. 현재 브랜치가 해당 Issue를 위한 올바른 작업 브랜치인가
4. `git status`로 기존 변경사항과 충돌하지 않는가
5. 관련 문서(`docs/`, README, 이전 PR)를 읽었는가
6. 영향 범위를 파악했는가
7. 기존 테스트를 확인했는가

## 규칙 우선순위

1. 사용자의 현재 명시적 지시
2. 승인된 GitHub Issue
3. `CLAUDE.md` / `AGENTS.md`
4. 저장소의 공식 문서
5. 기존 코드 관례

상위 규칙과 하위 규칙이 충돌하면 상위 규칙을 따르되, 충돌로 인해 명세나 계약 자체가 바뀌어야 한다면 자동으로 판단하지 않고 작업을 중단한 뒤 사용자에게 보고한다.

## 하나의 Issue, 하나의 PR

각 Issue는 develop에서 분기한 하나의 작업 브랜치와 하나의 PR로 처리한다. 작업 도중 범위가 커지면 현재 Issue를 확장하지 않고 후속 Issue로 분리한다.

자세한 코드/테스트/Git 규칙은 `CLAUDE.md`(`AGENTS.md`와 동일)를 따른다.
