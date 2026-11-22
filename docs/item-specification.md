# 아이템 요구사항 명세서

아이템 모드에 나오는 아이템의 요구사항을 정리한 명세서입니다. 평가팀이 각 아이템의 형태·발동 조건·효과·점수를 확인하고, 요구사항마다 어떤 기준으로 검증했는지 따라갈 수 있게 씁니다. 설계 논의 과정은 [아이템 모드 설계 정리](item-spec.md)에, 결정의 근거는 [미확정 사항](open-questions.md)에 있습니다.

## 1. 문서 정보

| 항목 | 내용 |
| --- | --- |
| 대상 | 1인 아이템 모드, 로컬 대전·P2P 대전의 아이템 모드 |
| 근거 요구사항 | 2차 R2-10~R2-20 (아이템 모드), 3차 R3-04·R3-18 (대전 아이템 모드), U3-04 (팀 아이템 명세서 작성 품질) |
| 범위 | 요구사항 제시 아이템 2종(`L`, 무게추 `@`)과 팀 구상 아이템 3종(`C`, `B`, `$`). 팀 구상 아이템을 중심으로 쓰고, 공통 규칙이 같은 제시 아이템도 같은 형식으로 함께 적음 |
| 기준 버전 | v3.0.0 |

### 1.1 요구사항 ID 규칙

| 접두어 | 뜻 | 예 |
| --- | --- | --- |
| `IT-COM` | 모든 아이템에 공통인 규칙 | `IT-COM-03` |
| `IT-L`, `IT-W` | 요구사항 제시 아이템 (줄 삭제, 무게추) | `IT-L-01` |
| `IT-C`, `IT-B`, `IT-D` | 팀 구상 아이템 (연쇄 삭제, 폭탄, 점수 2배) | `IT-C-02` |
| `IT-BAT` | 대전 모드에서의 아이템 규칙 | `IT-BAT-01` |
| `AC` | 각 요구사항의 수용 기준. `IT-C-02/AC1`처럼 요구사항 ID 뒤에 붙임 | |

### 1.2 우선순위

| 우선순위 | 뜻 |
| --- | --- |
| 필수 | 요구사항 원문이 직접 요구함. 빠지면 요구사항 미충족 |
| 높음 | 원문을 만족하려고 팀이 정한 규칙. 바꾸려면 팀 합의와 이 문서 개정이 필요함 |
| 보통 | 사용 편의나 화면 표시를 위한 규칙. 다른 요구사항에 영향을 주지 않는 범위에서 바꿀 수 있음 |

### 1.3 용어

| 용어 | 뜻 |
| --- | --- |
| 고정 | 블록이 더 이상 내려가지 못해 보드에 놓이는 순간 |
| 줄 단위 삭제 | 한 줄 전체를 지우고 위 줄을 내리는 삭제. 꽉 찬 줄 삭제와 `L` |
| 칸 단위 삭제 | 줄을 내리지 않고 칸만 비우는 삭제. `C`, `B`, 무게추가 내려오며 지우는 칸 |
| 블록에 붙는 아이템 | 일반 블록 4칸 중 한 칸에 실려 나오는 아이템 (`L`, `C`, `$`) |
| 아이템 블록 | 일반 블록 대신 전용 모양으로 나오는 아이템 (무게추, `B`) |

## 2. 아이템 목록

| ID | 문자 | 이름 | 출처 | 형태 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| `IT-L` | `L` | 줄 삭제 | 요구사항 제시 (2차 p8) | 블록에 붙음 | 필수 |
| `IT-W` | `@` | 무게추 | 요구사항 제시 (2차 p9) | 너비 4칸 전용 블록 | 필수 |
| `IT-C` | `C` | 연쇄 삭제 | 팀 구상 (2차 p7 "3개 이상") | 블록에 붙음 | 필수 |
| `IT-B` | `B` | 폭탄 | 팀 구상 | 1칸 전용 블록 | 필수 |
| `IT-D` | `$` | 점수 2배 | 팀 구상 | 블록에 붙음 | 필수 |

팀 구상 아이템은 2차 요구사항이 3개 이상을 요구하므로 세 가지 모두 필수입니다.

## 3. 공통 요구사항

| ID | 요구사항 | 우선순위 | 수용 기준 | 관련 테스트 |
| --- | --- | --- | --- | --- |
| `IT-COM-01` | 시작 메뉴의 "아이템 모드"로 시작함. 보드·조작·점수·속도·난이도는 일반 모드와 같음 (R2-10) | 필수 | AC1 아이템 모드를 고르면 아이템 공급자가 붙은 게임이 시작됨. AC2 일반 모드에서는 10줄 이상 지워도 아이템이 나오지 않음 | `AppCompositionTest`, `ScreenRouterTest`, `ItemPieceSourceTest.withoutAnyItemKindsOnlyPlainPiecesAppear` |
| `IT-COM-02` | 게임 전체 누적 삭제 줄 수가 10, 20, 30…을 넘을 때마다 아이템 1개. 기준을 넘은 순간 NEXT에 새로 나타나는 블록이 아이템임 (R2-12) | 필수 | AC1 10줄 전에는 아이템이 없음. AC2 10줄을 넘긴 순간 NEXT의 블록이 아이템임. AC3 한 번에 기준을 여러 개 넘으면 그 수만큼 연달아 나옴. AC4 `L`로 지운 줄도 카운트에 들어감 | `ItemPieceSourceTest` (`noItemAppearsBeforeTenLinesAreCleared`, `reachingTenLinesPutsAnItemOnTheNextNewlyShownPreview`, `passingSeveralThresholdsAtOnceGivesThatManyItemsInARow`, `thresholdsCountTheWholeGameNotEachClear`), `RequiredItemsTest.lineItemContributesToTheNextTenLineItemThreshold` |
| `IT-COM-03` | 아이템이 나올 때마다 5종 중 하나를 같은 확률로 고름 | 높음 | AC1 다섯 종류가 고르게 나옴 | `ItemPieceSourceTest.itemsArePickedEvenlyFromTheKinds`, `ItemCatalogTest` |
| `IT-COM-04` | 블록에 붙는 아이템은 원래 나올 일반 블록의 무작위 한 칸에 붙고 블록과 함께 회전함. 아이템 블록은 원래 나올 일반 블록 대신 나옴 (R2-13) | 필수 | AC1 아이템이 블록의 모든 칸에 붙을 수 있고 회전해도 같은 칸을 따라감. AC2 아이템 블록은 다음 일반 블록 자리를 대신함 | `RequiredItemsTest.lineItemCanAttachToEveryCellAndFollowsThatCellThroughRotation`, `SoloBlockTest`, `ItemPieceSourceTest.anAttachedItemLandsOnARandomCellOfTheBlock` |
| `IT-COM-05` | 아이템 칸은 아이템마다 다른 문자를 반전 배경(블록 색 바탕)으로 표시함. 무게추는 모든 칸을 `@@`로 그림. 색맹 모드에서도 문자로 구분됨 (R2-14, R2-19) | 필수 | AC1 다섯 아이템의 문자가 모두 다름. AC2 화면에 아이템 문자가 반전색으로 그려짐 | `ItemCatalogTest.containsAllFiveItemsEachWithItsOwnLetter`, `TextRendererTest`, `ColorPaletteTest` |
| `IT-COM-06` | 아이템은 블록이 고정될 때 한 번 발동하고 사라짐. 고정 뒤 보드에는 아이템 표시가 남지 않음. 무게추의 칸 삭제만 낙하 중에 일어남 | 높음 | AC1 고정 뒤 보드의 어느 칸에도 아이템이 없음 | `ItemAttackTest.noItemSurvivesOnTheBoardAfterALockSoAttackLinesNeverCarryOne`, `TeamItemsInEngineTest` |
| `IT-COM-07` | 처리 순서는 블록 고정 → 아이템 효과 → 꽉 찬 줄 삭제 → (대전) 받은 공격 줄 추가 → 다음 블록 | 높음 | AC1 아이템 효과로 꽉 찬 줄이 깨지면 그 줄은 지워지지 않음. AC2 공격 줄은 아이템 효과 뒤에 들어옴 | `ItemAttackTest.aChainClearThatBreaksTheFullRowsLeavesNothingToAttackWith`, `PlayerEngineAttackLinesTest.itemEffectsOnTheLockedPieceRunBeforeAttackLinesArrive` |
| `IT-COM-08` | 아이템으로 줄 단위로 지운 줄은 일반 줄 삭제와 같은 방식으로 점수를 받고 레벨·10줄 카운트에 들어감. 칸 단위로 지운 칸에는 점수가 없음 | 높음 | AC1 `L`로 지운 줄의 점수와 LINES가 일반 줄과 같음. AC2 `C`·`B`·무게추가 지운 칸에는 점수가 없음 | `RequiredItemScoringTest`, `DoubleScoreTest.otherItemsDoNotChangeTheScore` |
| `IT-COM-09` | 줄 삭제 연출: 지울 칸만 60ms 흰색으로 깜박인 뒤 240ms 동안 가운데부터 양쪽으로 사라짐. 연출 중 누른 이동·회전·드롭은 보관했다가 연출 뒤 순서대로 적용함. 무게추의 칸 삭제는 연출 없이 바로 적용함 | 보통 | AC1 연출 중 입력이 버려지지 않고 순서대로 적용됨 | `LineClearAnimationTest`, `AnimationInputTest` |
| `IT-COM-10` | 아이템 모드 기록은 일반 모드와 따로 저장하고, 스코어보드에서 `아이템 / 난이도`로 나눠 보여 줌 (R2-11) | 필수 | AC1 스코어보드에서 ↑↓로 모드를 바꾸면 아이템 모드 기록만 보임 | `ScoreboardScreenTest`, `BinaryScoreRepositoryTest` |

## 4. 요구사항 제시 아이템

### 4.1 `IT-L` 줄 삭제 `L`

```
I블록 예:  OOOL   OOLO   OLOO   LOOO   (붙는 위치 무작위)
```

| 항목 | 내용 |
| --- | --- |
| 설명 | 블록에 붙어 나오며, 고정되면 `L`이 있던 줄을 지움 |
| 형태 | 일반 블록의 무작위 한 칸에 문자 `L` |
| 발동 조건 | `L`이 붙은 블록이 고정될 때 |
| 효과 | `L`이 있는 줄을 꽉 차 있지 않아도 지우고 위 줄을 한 칸씩 내림 |
| 점수 | 지운 줄은 일반 줄 삭제와 같은 방식. 같은 고정에서 꽉 찬 줄도 지워지면 합쳐서 한 번의 줄 삭제로 계산하고, 겹친 줄은 한 번만 셈 |
| 우선순위 | 필수 (R2-15) |

| ID | 수용 기준 | 관련 테스트 |
| --- | --- | --- |
| `IT-L-01/AC1` | 빈칸이 있는 줄에서 `L`이 고정되면 그 줄이 지워지고 위 줄이 내려옴 | `RequiredItemsTest.lineItemClearsAnIncompleteRowAndShiftsTheRowsAbove` |
| `IT-L-01/AC2` | `L` 줄과 꽉 찬 줄이 함께 지워지면 겹친 줄은 한 번만 셈 | `RequiredItemsTest.itemRowAndFullRowAreCombinedAndAnOverlappingRowIsCountedOnce` |
| `IT-L-01/AC3` | 점수와 LINES가 일반 줄 삭제와 같게 오름 | `RequiredItemScoringTest.lineClearScoresAndCountsLikeANormalLineIncludingTheDoubleScoreBonus` |

### 4.2 `IT-W` 무게추 `@`

```
 @@
@@@@
```

| 항목 | 내용 |
| --- | --- |
| 설명 | 일반 블록 대신 나오는 너비 4칸 블록. 내려가면서 아래 칸을 지움 |
| 형태 | 위 2칸 / 아래 4칸의 전용 블록. 모든 칸을 `@` 무늬로 그림(게임 격자 한 칸당 `@@`). 반전색은 쓰지 않음. 코드의 내부 식별 문자는 `W` |
| 발동 조건 | 하강을 시도하다 처음 아래가 막힌 순간부터 좌우 이동이 잠기고 칸을 지우기 시작함 |
| 효과 | 자동 낙하·내리기마다 아래 4칸 폭의 칸을 지우며 한 칸씩 내려감. 한 번에 떨어뜨리기는 같은 경로를 바닥까지 지움. 바닥에 닿으면 무게추 모양의 일반 칸으로 남음 |
| 점수 | 하강 거리와 지운 칸에는 점수 없음. 고정 뒤 꽉 찬 줄이 생기면 일반 줄 삭제 점수 |
| 우선순위 | 필수 (R2-16) |

세부 규칙 (우선순위 높음, 2026-10-22 팀 결정)

- 옆으로 움직여 블록 위에 올라서거나, 스폰 직후 바로 아래에 블록이 있는 것만으로는 잠기지 않음. 다음 하강 시도에서 아래가 막힌 순간 잠김
- 잠금 판정은 아래 칸을 지우기 전에 함. 칸을 지워 다시 공중에 떠도 잠금은 유지됨
- 회전할 수 없고 벽을 넘을 수 없음. 고정 유예 시간은 따로 두지 않음
- 자기 4칸 폭 밖이나 자기 경로 위의 칸은 지우지 않음. 스폰 위치의 칸을 지워 게임을 이어가게 하지 않음

| ID | 수용 기준 | 관련 테스트 |
| --- | --- | --- |
| `IT-W-01/AC1` | 6칸·4열 모양이고 회전하지 않으며 벽을 넘지 않음 | `RequiredItemsTest.weightHasSixCellsFourColumnsAndCannotRotateOrCrossTheWalls` |
| `IT-W-01/AC2` | 하강 시도에서 막히기 전까지 좌우로 움직일 수 있고, 막힌 뒤에는 잠김 | `RequiredItemsTest.weightCanMoveAboveBlocksUntilDescentCrushesTheFirstRow`, `movingOverASuspendedCellAllowsEscapeUntilDescentAndThenKeepsTheLock`, `RequiredItemScoringTest.weightCanMoveUntilTheNextGravityOrSoftDropHitsAnObstacle` |
| `IT-W-01/AC3` | 자기 4열의 칸만 지우고, 한 번에 떨어뜨리기와 반복 낙하의 결과가 같음 | `RequiredItemsTest.weightOnlyCrushesItsFourColumnsAndNeverClearsAboveItsPath`, `hardDropAndRepeatedGravityProduceTheSameBoardAndDistance` |
| `IT-W-01/AC4` | 하강 거리와 지운 칸에는 점수가 없고, 무게추가 채운 꽉 찬 줄은 일반 점수 | `RequiredItemScoringTest.weightEarnsNoDropOrCrushPointsAndTheFollowingNormalPieceStillDoes`, `aFullRowCompletedByTheWeightStillUsesNormalLineScoring` |

## 5. 팀 구상 아이템

### 5.1 `IT-C` 연쇄 삭제 `C`

```
O = O블록, I = I블록, C = 아이템 칸 (C가 붙은 블록은 O블록)

고정 전          고정 후
. . . . . .      . . . . . .
O C O O . .      . . . . . .    ← C가 붙은 O블록과 맞닿은 O블록이 모두 사라짐
O O O O . .      . . . . . .
I I I I . .      I I I I . .    ← 다른 종류(I블록)는 남음
```

| 항목 | 내용 |
| --- | --- |
| 설명 | 블록에 붙어 나오며, 고정되면 맞닿은 같은 종류 블록을 한꺼번에 지움 |
| 형태 | 일반 블록의 무작위 한 칸에 문자 `C` |
| 발동 조건 | `C`가 붙은 블록이 고정될 때. 고정된 블록에 같은 종류 블록이 하나 이상 맞닿아 있어야 지움 |
| 효과 | `C` 칸에서 시작해 상하좌우로 이어진 같은 종류 블록 칸을 모두 지움 (고정된 블록 자신 포함). 지운 칸 위의 블록은 내려오지 않음 |
| 점수 | 칸 단위 삭제라 점수 없음 |
| 우선순위 | 필수 (R2-17) |

| ID | 요구사항 | 우선순위 | 수용 기준 | 관련 테스트 |
| --- | --- | --- | --- | --- |
| `IT-C-01` | 이어진 같은 종류 칸을 모두 지우고 다른 종류는 남김 | 필수 | AC1 같은 종류 덩어리 전체가 사라지고 다른 종류는 그대로 | `ChainClearItemTest.removesEveryConnectedCellOfTheSameKindButLeavesOtherKinds`, `TeamItemsInEngineTest.aChainClearBlockRemovesTheTouchingBlocksOfItsKind` |
| `IT-C-02` | 맞닿은 같은 종류가 없으면 아무것도 지우지 않음 | 높음 | AC1 자기 블록만 그 종류이면 보드가 바뀌지 않음 | `ChainClearItemTest.doesNothingWhenOnlyItsOwnPieceHasThatKind`, `ItemAttackTest.aChainClearWithoutMatchingNeighboursLetsTheFullRowsAttackAsUsual` |
| `IT-C-03` | 대각선으로만 닿은 칸은 이어진 것으로 보지 않음 | 높음 | AC1 대각선 칸은 남음 | `ChainClearItemTest.diagonalNeighborsAreNotConnected` |
| `IT-C-04` | 테트로미노가 아닌 칸(무게추가 남긴 칸, 공격 칸)은 덩어리에 들지 않음 | 높음 | AC1 무게추·공격 칸은 남음 | `ChainClearItemTest.ignoresCellsThatAreNotTetrominoes`, `ItemBattleTest.attackCellsAreNeverPartOfAChainAndStillCountForLines` |
| `IT-C-05` | 연쇄 삭제가 꽉 찬 줄을 깨면 그 줄은 지워지지 않음 (처리 순서 `IT-COM-07`) | 높음 | AC1 줄 삭제가 없고 칸만 사라짐 | `ItemAttackTest.aChainClearThatBreaksTheFullRowsLeavesNothingToAttackWith` |

### 5.2 `IT-B` 폭탄 `B`

```
떨어지는 폭탄      고정 직후 (중심 기준 3×3 삭제)
    B
                   . . . . .        . . . . .
. ## ## ## .       . ## B  ## .  →  . .      . .
. ## ## ## .       . ## ## ## .     . .      . .
```

| 항목 | 내용 |
| --- | --- |
| 설명 | 일반 블록 대신 나오는 1칸 블록. 고정되면 주위 3×3을 지움 |
| 형태 | 1칸 전용 블록, 문자 `B`. 회전하지 않음. 좌우 이동·내리기·한 번에 떨어뜨리기는 일반 블록과 같음 |
| 발동 조건 | 폭탄이 고정될 때 |
| 효과 | 자기 칸을 중심으로 한 3×3(자기 칸 포함)을 모두 지움. 보드 밖은 무시. 지운 칸 위의 블록은 내려오지 않음 |
| 점수 | 칸 단위 삭제라 점수 없음 |
| 우선순위 | 필수 (R2-17) |

| ID | 요구사항 | 우선순위 | 수용 기준 | 관련 테스트 |
| --- | --- | --- | --- | --- |
| `IT-B-01` | 고정 위치 중심 3×3을 지움 | 필수 | AC1 9칸이 비고 그 밖은 남음 | `BombItemTest.removesTheThreeByThreeAreaAroundItself`, `TeamItemsInEngineTest.aBombBlockFallsWithoutRotatingAndBlowsUpAroundWhereItLands` |
| `IT-B-02` | 보드 가장자리·모서리에서는 보드 안쪽만 지움 | 높음 | AC1 예외 없이 보드 안 칸만 지움 | `BombItemTest.theAreaIsClippedAtTheBoardEdge`, `theAreaIsClippedAtTheBoardCorner` |
| `IT-B-03` | 1칸이고 회전하지 않음 | 높음 | AC1 회전해도 모양이 그대로 | `SoloBlockTest.theShapeHasOneCellAndDoesNotRotate` |
| `IT-B-04` | 폭탄이 줄의 마지막 빈칸을 채워도 곧바로 주위를 지우므로 줄 삭제가 없음 | 높음 | AC1 줄 삭제가 없음 | `ItemAttackTest.aBombThatFillsTheLastGapStillClearsNoLine` |

### 5.3 `IT-D` 점수 2배 `$`

```
게임 화면 오른쪽 점수 표시
SCORE x2 (3)     ← 남은 적용 횟수
1234
```

| 항목 | 내용 |
| --- | --- |
| 설명 | 블록에 붙어 나오며, 고정된 동작부터 줄을 지운 동작 3번의 점수를 2배로 함 |
| 형태 | 일반 블록의 무작위 한 칸에 문자 `$` |
| 발동 조건 | `$`가 붙은 블록이 고정될 때. 그 고정이 줄을 지우지 않아도 효과는 시작됨 |
| 효과 | 그 동작부터 줄을 지운 동작 3번의 점수(떨어진 칸 점수 + 줄 삭제 점수)를 2배로 계산. 보드는 바뀌지 않고 고정된 블록은 일반 칸으로 남음 |
| 점수 | 위 효과 그대로. 효과가 남아 있는 동안 SCORE 옆에 `x2 (남은 횟수)` 표시 |
| 우선순위 | 필수 (R2-17) |

| ID | 요구사항 | 우선순위 | 수용 기준 | 관련 테스트 |
| --- | --- | --- | --- | --- |
| `IT-D-01` | 자기 고정부터 줄을 지운 동작 3번을 2배로 함. 줄을 지우지 않은 고정은 횟수를 쓰지 않음 | 필수 | AC1 3번째까지 2배, 4번째는 1배 | `DoubleScoreTest.doublesTheNextThreeLineClearsStartingWithTheItemsOwnLock`, `anItemThatClearsNothingStillStartsTheEffect` |
| `IT-D-02` | 그 동작의 점수 전체(떨어진 칸 + 줄 삭제)를 2배로 함 | 높음 | AC1 한 번에 떨어뜨리기로 줄을 지우면 두 점수 모두 2배 | `DoubleScoreTest.doublesBothDropAndLineScoresWhenHardDropClearsLines` |
| `IT-D-03` | 효과 중 다시 얻으면 남은 횟수를 3으로 다시 채움 (누적하지 않음) | 높음 | AC1 남은 횟수가 3이 됨 | `DoubleScoreTest.gettingAnotherOneResetsTheCountInsteadOfAddingUp` |
| `IT-D-04` | 보드에는 영향이 없고 일반 칸으로 남음 | 높음 | AC1 고정된 칸이 일반 칸임 | `TeamItemsInEngineTest.aDoubleScoreBlockLeavesThePlainCellOnTheBoard` |
| `IT-D-05` | 효과 중 SCORE 옆에 `x2 (남은 횟수)`를 표시하고 최종 점수에 반영함 | 보통 | AC1 표시와 결과 점수가 맞음 | `DoubleScoreTest.theEffectIsCarriedToTheFinalResult`, `TextRendererTest`, `BattleRendererTest.doubleScoreIndicatorDoesNotOverwriteEitherPlayersScore` |

## 6. 대전 모드에서의 아이템

로컬 대전과 P2P 대전의 아이템 모드는 3장~5장의 규칙을 그대로 쓰고, 아이템이 나온다는 점 외에는 일반 대전과 같습니다 (R3-04). 공격과의 관계는 요구사항이 팀에 맡긴 부분(R3-18)이라 2026-11-07 팀 안건 ⑭·⑮·⑰·㉔로 정했습니다.

### 6.1 아이템별 공격

공격은 한 번의 고정으로 2줄 이상을 **줄 단위로** 지웠을 때만 생기고, 지운 줄에서 마지막 블록의 칸을 비운 모양이 넘어갑니다 (R3-06).

| 아이템 | 공격이 되는가 | 넘어가는 모양 |
| --- | --- | --- |
| `L` 줄 삭제 | 줄 단위 삭제라 공격. 꽉 찬 줄과 합쳐 2줄 이상이면 공격 | 아이템 효과 뒤 남은 칸 그대로, 마지막 블록 칸은 비움. 지운 줄에 마지막 블록 칸만 있었다면 빈 줄이 넘어가 받은 쪽 보드가 한 줄 올라감 |
| `C` 연쇄 삭제, `B` 폭탄 | 칸 단위 삭제라 공격 아님. 효과로 꽉 찬 줄이 깨지면 그 줄도 지워지지 않음 | - |
| 무게추 `@` | 내려오며 지운 칸은 공격 아님. 고정으로 2줄 이상 지우면 공격 | 무게추의 최종 고정 칸을 비운 모양 |
| `$` 점수 2배 | 일반 블록과 같음 | 일반 블록과 같음. 점수에만 영향 |

### 6.2 대전 요구사항

| ID | 요구사항 | 우선순위 | 수용 기준 | 관련 테스트 |
| --- | --- | --- | --- | --- |
| `IT-BAT-01` | 줄 단위 삭제(꽉 찬 줄, `L`)만 공격이 됨 (안건 ⑭) | 높음 | AC1 `L`과 꽉 찬 줄로 2줄을 지우면 공격. AC2 `C`·`B`로 지운 칸은 공격 아님 | `AttackRuleTest.aRowClearedByTheLineClearItemCountsTowardsTheAttack`, `cellsRemovedByABombDoNotAttack`, `ItemAttackTest` |
| `IT-BAT-02` | `L` 공격은 남은 빈칸을 그대로 보내고, 마지막 블록 칸만 있던 줄은 빈 줄로 보냄 | 높음 | AC1 받은 쪽 대기열·보드에 빈 줄과 빈칸이 같은 순서로 들어감 | `ItemAttackTest.aLineClearOnARowHoldingOnlyThePieceSendsAnEmptyLine`, `ItemBattleTest.linesClearedWithTheLineClearItemReachTheOpponentWithTheirGaps` |
| `IT-BAT-03` | 무게추가 내려오며 지운 칸은 공격 아님. 고정으로 지운 줄은 최종 고정 칸을 비워 보냄 (안건 ⑮) | 높음 | AC1 1줄 삭제로는 공격 없음. AC2 2줄 이상이면 무게추 칸이 빈 모양 | `ItemAttackTest.cellsAWeightCrushesOnTheWayDownDoNotAttack`, `AttackRuleTest.aWeightEmptiesItsFinalCellsInTheAttack` |
| `IT-BAT-04` | 공격 줄에는 아이템이 없고 회색 공격 칸만 넘어감 (안건 ㉔) | 높음 | AC1 공격 줄의 칸은 빈칸이나 공격 칸뿐 | `AttackRuleTest.attackLinesCarryNoItemsAndOnlyAttackCells`, `ItemAttackTest.noItemSurvivesOnTheBoardAfterALockSoAttackLinesNeverCarryOne` |
| `IT-BAT-05` | 받은 공격 칸은 `C` 덩어리에 들지 않고, 꽉 찬 줄의 일부면 함께 지워져 다시 공격이 됨 | 높음 | AC1 연쇄 삭제 뒤에도 공격 칸이 남음. AC2 공격 칸이 든 꽉 찬 줄도 줄 수에 들어감 | `ItemBattleTest.attackCellsAreNeverPartOfAChainAndStillCountForLines` |
| `IT-BAT-06` | 아이템 효과(`C`·`B`의 보드 변화, `$`의 점수)는 쓴 사람에게만 적용됨 | 높음 | AC1 상대 보드·점수가 바뀌지 않음 | `ItemBattleTest.aBombOrAChainClearOnlyChangesTheBoardOfThePlayerWhoUsedIt`, `theDoubleScoreItemOnlyBoostsThePlayerWhoUsedIt` |
| `IT-BAT-07` | 기본 블록 순서는 두 사람이 같고, 아이템은 각자 지운 줄 수로 나옴. 아이템 블록은 원래 블록 자리에 대신 나와 뒤 순서를 밀지 않음 (안건 ⑰) | 높음 | AC1 아이템이 아닌 블록의 순서가 두 사람이 같음. AC2 무게추·폭탄·붙는 아이템 모두에서 성립 | `ItemBattleTest.itemsFollowEachPlayersOwnClearsWhileThePlainPiecesStayShared`, `SharedPieceSequenceTest` |
| `IT-BAT-08` | P2P 아이템 대전도 같은 공격 규칙으로 같은 모양을 보냄 (빈 줄 포함) | 높음 | AC1 상대 대기열과 보드에 같은 모양이 도착함 | `P2pItemAttackTest.lineClearItemAttacksArriveWithTheSameShapeAsInALocalBattle` |
| `IT-BAT-09` | 대전 화면에서도 각 플레이어의 아이템 문자와 `x2` 표시가 서로 겹치지 않음 | 보통 | AC1 두 플레이어 점수 표시가 모두 보임 | `BattleRendererTest.doubleScoreIndicatorDoesNotOverwriteEitherPlayersScore` |

## 7. 직접 확인 방법

자동 테스트 외에 실행한 게임에서 확인하는 방법입니다. 디버그 모드(`--debug`)를 쓰면 원하는 아이템을 바로 받을 수 있습니다.

| 확인 항목 | 방법 | 관련 ID |
| --- | --- | --- |
| 10줄 후 아이템 등장 | 아이템 모드에서 누적 10줄을 지운 순간 NEXT에 아이템이 나오는지 확인 | `IT-COM-02` |
| 일반 모드에는 아이템 없음 | 일반 모드에서 10줄 이상 지워도 아이템이 나오지 않는지 확인 | `IT-COM-01` |
| `L` | 빈칸이 있는 줄에 `L`을 고정 → 그 줄이 지워지고 위 줄이 내려오며 점수와 LINES 증가 | `IT-L-01` |
| `@` | 쌓인 블록 위로 옆걸음 → 다음 하강 전에는 좌우 이동 가능 → 하강 중 막히면 잠금·칸 삭제, 지운 뒤에도 잠금 유지. 낙하 점수 없음 | `IT-W-01` |
| `C` | 같은 종류 블록에 붙여 고정 → 이어진 같은 종류가 사라짐. 다른 종류에만 붙이면 아무 일 없음 | `IT-C-01`, `IT-C-02` |
| `B` | 블록 위에 떨어뜨림 → 주위 3×3이 사라짐 | `IT-B-01` |
| `$` | 고정 뒤 SCORE 옆에 `x2 (3)` → 줄을 지울 때마다 숫자가 줄고 점수가 2배로 오름 | `IT-D-01`, `IT-D-05` |
| 스코어보드 | 게임이 끝나면 순위표에 `아이템 / 난이도`로 따로 표시. 메뉴의 스코어보드에서 ↑↓로 전환 | `IT-COM-10` |
| 대전 `L` 공격 | 대전 아이템 모드에서 `L`과 꽉 찬 줄로 2줄을 지움 → 상대의 받을 줄 영역에 빈칸 모양 그대로 표시 | `IT-BAT-02` |
| 대전 폭탄·연쇄 | 대전에서 `B`나 `C`를 씀 → 내 보드만 바뀌고 상대에게 공격이 가지 않음 | `IT-BAT-01`, `IT-BAT-06` |

## 8. 변경 관리

규칙을 바꿀 때는 팀 논의로 정하고, 안건·결정·근거를 [미확정 사항](open-questions.md)에 남긴 뒤 이 문서와 [설계 정리](item-spec.md)를 함께 고칩니다. 이 문서의 수용 기준과 관련 테스트가 맞지 않으면 PR 리뷰에서 함께 확인합니다.

| 날짜 | 버전 | 변경 | 근거 |
| --- | --- | --- | --- |
| 2026-10-14 | v2.0.0 이전 | 팀 아이템 `C`·`B`·`$` 초안 | PR #8 |
| 2026-10-22 | v2.0.0 | 무게추 표시 `@`, 하강 충돌 시점 잠금, `$` 재획득 시 3회로 리셋, 팀 아이템 3종 확정 | 2차 안건, PR #24·#25 |
| 2026-11-07 | v3.0.0 | 대전 모드의 아이템과 공격 규칙(⑭·⑮·⑰·㉔) | 3차 안건 |
| 2026-11-22 | v3.0.0 | 요구사항 명세 형식(ID, 우선순위, 수용 기준, 관련 테스트)으로 다시 씀 | 카드 27 (U3-04) |
