
---
name: commit-convention
description: 한국어 본문을 포함한 Conventional Commits 스타일 커밋 메시지(Title + Summary + Changes)를 생성/검수한다.
version: 1.0.0
---

## Goal
- 커밋 제목은 `type: 한글 설명` 형식으로 작성한다. (type은 소문자 영어)
- 본문은 `Summary`, `Changes` 섹션으로 구조화한다.
- 작업 내용이 길거나 섞여 있으면 성격별로 묶고 중복을 제거해 간결하게 만든다.

## Title Rules
- 형식: `type: <한글 설명>`
- type 후보: `feat`, `fix`, `refactor`, `chore`, `docs`, `test`, `style`, `perf`, `ci`, `build`
- 여러 작업이 섞이면 가장 임팩트가 큰 변경을 기준으로 type을 고른다.
- 제목이 과하게 길어지면 핵심 1개로 압축하고 나머지는 본문으로 내린다.

## Body Template
type: 한글 제목

## Summary
- (핵심 변경 2~5개)

## Changes
### (영역/도메인)
- (구체 변경)

## Output Format
- 최종 출력은 커밋 메시지 전체만 출력한다(해설 금지).
