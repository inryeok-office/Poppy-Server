$ErrorActionPreference = "Stop"

Set-Location (Join-Path $PSScriptRoot "..")

$fail = $false

function Require-File($path) {
    if (-not (Test-Path $path -PathType Leaf)) {
        Write-Output "MISSING: $path"
        $script:fail = $true
    }
}

function Require-Lf($path) {
    if (Test-Path $path -PathType Leaf) {
        $content = Get-Content -Raw -Path $path
        if ($content -match "`r`n") {
            Write-Output "NOT LF-ONLY: $path"
            $script:fail = $true
        }
    }
}

Require-File "CLAUDE.md"
Require-File "AGENTS.md"

if ((Test-Path "CLAUDE.md" -PathType Leaf) -and (Test-Path "AGENTS.md" -PathType Leaf)) {
    $claude = Get-Content -Raw -Path "CLAUDE.md"
    $agents = Get-Content -Raw -Path "AGENTS.md"
    if ($claude -ne $agents) {
        Write-Output "MISMATCH: CLAUDE.md and AGENTS.md are not byte-identical"
        $fail = $true
    } else {
        Write-Output "OK: CLAUDE.md and AGENTS.md are byte-identical"
    }
    Require-Lf "CLAUDE.md"
    Require-Lf "AGENTS.md"
}

$requiredDocs = @(
    "README.md",
    "CONTRIBUTING.md",
    "SECURITY.md",
    "docs/git-workflow.md",
    "docs/commit-convention.md",
    "docs/ai-workflow.md",
    "docs/architecture.md",
    "docs/configuration.md",
    "docs/api-convention.md",
    "docs/testing.md",
    "docs/ci.md",
    "docs/pull-request-convention.md"
)

foreach ($doc in $requiredDocs) {
    Require-File $doc
}

if ($fail) {
    Write-Output "harness-check FAILED"
    exit 1
}

Write-Output "harness-check PASSED"
