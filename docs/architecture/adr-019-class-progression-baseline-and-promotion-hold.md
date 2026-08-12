# ADR-019: 클래스 progression baseline과 promotion hold

## 상태

`ACCEPTED`

## 배경

기존 운영 회원은 시스템에 기록된 완료 기승이 0회여도 이미 높은 클래스에서 탈 수 있다.
영구 `manualClassOverride`가 자동 계산을 가리면 이후 정상 기승으로 승급할 수 없고, 높은 클래스를
표현하려고 실제로 타지 않은 횟수를 추가하면 사실 기록이 훼손된다. 반대로 안전상 클래스를 낮게
유지하는 판단은 경력 인정과 다른 책임이다.

MVP 3.2는 실제 횟수, 기존 경력 인정, 특수 승인 시점의 최소 progression 인정과 안전 상한을
각각 분리하되 기승마다 독립 누적하는 별도 progression counter는 만들지 않는다.

## 검토한 대안

### A. 영구 manual override

`manualClassOverride ?? calculatedClass`로 실제 클래스를 정한다. 상·하향 지정은 쉽지만
override가 자동 승급을 영구적으로 가리고 경력 인정과 안전 제한을 혼합하므로 선택하지 않는다.

### B. Anchor baseline과 promotion hold

기존 경력은 실제 횟수에 더하는 파생 인정분으로 표현하고, 안전 제한은 별도 class 상한으로
표현한다. 자동 progression과 사실 횟수를 보존하므로 선택한다.

### C. 실제 횟수와 progression 횟수의 독립 누적

두 counter를 별도로 저장한다. correction과 완료 처리마다 동기화 책임과 감사 복잡도가 커지므로
선택하지 않는다.

### Special Approval 최소 인정 모델

특수 승인 때 baseline을 대마장 속보로 다시 설정하는 방식은 시스템 도입 이전 경력이라는 baseline
의미를 깨고 승인 전에 Horse에서 완료한 횟수를 다시 더할 수 있어 선택하지 않는다. 매 조회에서
`max(progressionValue, LARGE_ARENA_TROT threshold)`만 적용하는 방식은 threshold 아래에서 승인된
뒤 다음 일반 기승 1회가 progression을 1 높이지 못하므로 선택하지 않는다.

승인 직전 progression과 threshold의 차이만 `specialApprovalProgressionCredit`에 한 번 더하는
방식을 선택한다. 이 값은 관리자 실력 인정의 영속 결과이지 실제 기승 횟수나 기승마다 증가하는
별도 progression counter가 아니다.

## 용어

- `actualCompletedRideCount`: 실제 완료 일반 기승과 사실 기반 조정을 반영한 횟수다.
- `progression baseline`: 관리자가 인정한 시작 클래스의 최소 threshold를 Anchor로 삼는 기존 경력 인정이다.
- `specialApprovalProgressionCredit`: 특수 승인 시 대마장 속보 threshold에 미달한 progression
  부족분만 영속하는 관리자 인정 값이다.
- `progressionValue`: 실제 횟수, baseline 파생 인정분과 특수 승인 progression 인정분으로
  계산하는 threshold 판정 값이다.
- `progressionClass`: progressionValue가 도달한 가장 높은 일반 클래스다. 기존
  `calculatedClass`보다 baseline 포함 의미가 명확한 이름이다.
- `promotionHoldClass`: 안전·실력 판단으로 설정하는 일반 클래스 상한이다.
- `effectiveClass`: 실제 예약 자격 판단에 사용하는 최종 일반 클래스다.

일반 클래스 순서와 threshold는 명시적인 Domain 정책으로 관리한다. Java enum `ordinal()`에
의존하지 않는다. MVP 3.2의 일반 클래스 threshold는 다음과 같다.

| 일반 클래스 | 최소 threshold |
|---|---:|
| 왕초보 | 0 |
| 원형초보 | 1 |
| 원형 속보 | 6 |
| 대마장초보 | 21 |
| 대마장 속보 | 26 |
| 구보초보 | 70 |
| 구보 | 100 |

## Baseline Anchor 결정

baseline 설정 시 다음 의미를 고정한다.

- 관리자가 지정한 시작 일반 클래스
- 해당 클래스의 최소 threshold `T`
- 설정 시점의 실제 일반 기승 횟수 `B`

baseline의 파생 인정분 `C`와 현재 progression 값 `P`는 다음 의미다.

```text
C = max(0, T - B)
P = currentActualCompletedRideCount + C + specialApprovalProgressionCredit
```

`C`는 baseline과 Anchor에서 계산되는 값이며 별도 누적 counter가 아니다. 특수 승인 인정분이
없을 때는 0이다. 예를 들어 실제 횟수
0에서 threshold 26인 대마장 속보 baseline을 설정하면 `C=26`, `P=26`이다. 이후 실제 완료가
10회 늘면 `P=36`이 되고 70에 도달하면 자동으로 구보초보로 승급한다.

baseline은 최소 인정 시작점이다. 정상 사용에서는 실제 횟수로 이미 도달한 progression을
낮추는 용도로 쓰지 않는다. 다만 잘못 설정된 경력 정보를 교정하기 위해 설정·변경·해제를
허용한다.

- baseline 변경은 현재 실제 횟수를 새 Anchor로 삼고 새 클래스 threshold에서 파생 인정분을 다시 계산한다.
- 하향 변경과 해제는 일반 강등 기능이 아니라 잘못된 baseline 교정이다.
- baseline을 해제하면 `C=0`이 되어 실제 횟수만으로 progressionClass를 즉시 다시 계산한다.
- 교정 결과 progressionClass와 effectiveClass가 낮아질 수 있다.
- M32-08은 변경 전 현재 값과 변경 후 예상 progressionClass·effectiveClass를 관리자에게 제시해야 한다.

## 시스템 도입 이전과 이후의 책임 경계

시스템 도입 이전 경력은 관리자가 승인한 시작 클래스 baseline으로 요약하고 종결한다. 과거에
정확히 몇 회, 어느 날짜와 기간에 탔는지 복원하지 않으며 지정 클래스의 중간 progression도
추정하지 않는다. baseline 이전 경력을 이후 ride-count adjustment로 progression에 다시 넣지 않는다.

회원별 Horse progression 관리 시작 이후부터는 Horse에 기록된 일반 기승 완료가 progression의
근거다. 기존 회원의 최초 baseline 승인은 이 경계를 함께 확정하며 이후 baseline 교정은 경계를
다시 쓰지 않는다. 예를 들어
`T=26`, `B=0`인 대마장 속보 baseline 뒤 Horse에서 44회를 완료하면 `P=70`이 되어 구보초보로
승급한다. M32-07은 이 Horse 관리 구간에서 발생한 누락·중복 집계 오류만 정정한다.

따라서 PRE_ANCHOR·POST_ANCHOR 사용자 입력, 과거 수업 날짜·기간 복원과 Anchor 재기준화는
도입하지 않는다. 이 경계로 시스템 도입 이전 경력이 baseline과 조정 원장에 중복 반영되는
상황 자체를 만들지 않는다.

Horse progression 관리 시작 경계는 현재 baseline 값에서 역산하지 않는 회원별 write-once
의미다. 기존 회원은 최초 baseline 승인, Horse에서 처음부터 관리하는 신규 회원은 회원 progression
초기화가 이 경계를 만든다. M32-06은 이 경계를 클래스 progression 상태의 단일 원천으로 영속하고
최초 설정 감사를 남긴다. M32-07은 경계를 변경하지 않고 경계 이후 집계 오류만 보정한다.
구체적인 Entity·column 이름은 M32-06에서 기존 모델에 맞춰 결정한다.

## Promotion Hold 결정

마장마술·장애물 특수 승인이 모두 비활성인 회원에게만 관리자가 현재 progressionClass 이하의
일반 클래스를 promotionHoldClass로 설정할 수 있다. 이는 다음 승급 한 단계만 막는 플래그가
아니라 안전상 허용할 최고 클래스다.

```text
effectiveClass = lowerOf(progressionClass, promotionHoldClass)
```

hold가 없으면 effectiveClass는 progressionClass다. `lowerOf`는 명시적인 클래스 순서와
threshold 정책을 사용한다.

- progressionClass가 구보초보여도 대마장 속보를 hold 상한으로 지정할 수 있다.
- hold 중에도 실제 완료 횟수와 progression은 계속 누적하고 progressionClass는 상승할 수 있다.
- hold는 자동 만료하지 않으며 관리자가 명시적으로 변경하거나 해제할 때까지 유지한다.
- 해제하면 중간 단계를 강제하지 않고 그 시점의 progressionClass로 즉시 복귀한다.
- baseline과 hold는 동시에 존재할 수 있다. baseline은 progression 시작점을 높이고 hold는
  effectiveClass의 안전 상한을 제한한다.
- 별도 downgrade override는 만들지 않는다.

## Special Approval과 일반 progression 인정

마장마술·장애물 승인은 해당 특수 클래스 예약 자격과 함께 일반 progression의 최소 대마장 속보
수준을 인정하는 관리자 판단이다. 대마장 속보 threshold를 `S`, 승인 직전 progressionValue를
`P_beforeApproval`, 이번 승인에서 추가할 인정분을 `D`라고 한다. `S`는 승인 Command가 현재의
명시적 일반 클래스 catalog에서 조회하며 숫자를 별도 하드코딩하지 않는다.

```text
D = max(0, S - P_beforeApproval)
specialApprovalProgressionCreditAfter = specialApprovalProgressionCreditBefore + D
P_afterApproval = P_beforeApproval + D = max(P_beforeApproval, S)
```

threshold `S` 자체를 기존 progression에 더하지 않는다. 예를 들어 `P_beforeApproval=20`, `S=26`
이면 `D=6`, 승인 직후 progression은 26이다. 이후 Horse에서 일반 기승 1회를 완료하면 27이 된다.
승인 전 Horse 완료 20회를 `26+20`으로 다시 계산하지 않는다. 실제 완료 횟수와 baseline은 이
Command로 변경하지 않는다.

- 이미 progression이 `S` 이상이면 `D=0`이며 구보초보·구보 자격을 낮추지 않는다.
- 두 번째 종류의 특수 승인이나 해제 후 재승인도 현재 progression으로 `D`를 계산하므로 부족분이
  없으면 인정분을 중복 누적하지 않는다.
- 승인 후 완료된 일반 기승만 progression을 1회씩 높인다. 마장마술·장애물 완료 횟수는 일반
  progression에 들어가지 않는다.
- 구보초보·구보는 각각 70·100의 정상 일반 progression threshold에 도달해야 열린다.
- 특수 승인 해제는 특수 클래스 예약 자격만 제거하고 이미 기록한 인정분은 자동 회수하지 않는다.
- 잘못된 실력 인정은 실제 완료 횟수, baseline과 Horse progression 관리 시작 경계를 바꾸지 않는
  별도 관리자 progression 인정 교정 Command로 변경하며 전후 값과 사유를 감사한다. 하향 교정은
  모든 특수 승인을 먼저 명시적으로 해제한 뒤 수행한다. M32-07의 기승 횟수 보정을 이 목적으로
  사용하지 않는다.

Promotion Hold와 Special Approval은 상호 배타다.

- `Special Approval ACTIVE + Promotion Hold ACTIVE` 상태를 허용하지 않는다.
- hold가 활성인 회원에게 특수 승인을 부여하려면 관리자가 hold를 먼저 명시적으로 해제한다.
- 특수 승인 하나라도 활성인 회원에게 hold를 설정하려면 관리자가 모든 특수 승인을 먼저 해제한다.
- 시스템은 어느 상태도 자동 해제하거나 우선 적용하지 않는다.
- 각 Command는 관리자, 시각, 전후 값과 필수 사유를 독립적으로 감사한다.

## Ride Count Adjustment

기승 횟수 보정은 회원별 Horse progression 관리 시작 이후 Horse가 관리한 완료의 누락·중복 같은
집계 오류 정정에만 사용한다. 양수·음수 delta, before, after, 관리자, 시각과 필수 사유를 기록하며
결과는 0 이상이어야 한다.

다음 목적으로 사용하지 않는다.

- 실력이 좋다는 이유로 존재하지 않은 횟수를 추가하는 것: baseline 책임
- 시스템 도입 이전 전체 횟수·날짜·기간과 progression을 복원하는 것: 지원하지 않음
- 안전상 클래스를 낮추는 것: promotion hold 책임
- 자동 승급을 멈추는 것: promotion hold 책임

## 감사와 불변식

baseline 설정·변경·해제, promotion hold 설정·변경·해제, 특수 승인과 progression 인정분 변경,
기승 횟수 보정은 관리자, 시각, 전후 상태와 필수 사유를 append-only로 남긴다.

- actualCompletedRideCount는 사실 기록이며 baseline이 이를 거짓으로 바꾸지 않는다.
- baseline 인정분은 Anchor에서 파생하며 별도 누적하지 않는다.
- specialApprovalProgressionCredit은 승인 직전 부족분만 반영하고 일반·특수 기승 완료로 직접
  증가하지 않는다. 실제 횟수와 baseline을 바꾸거나 중복 반영하지 않는다.
- 특수 승인 해제만으로 specialApprovalProgressionCredit을 낮추지 않는다.
- 시스템 도입 이전 경력은 baseline으로만 표현하고 조정 원장에 다시 넣지 않는다.
- Horse progression 관리 시작 경계는 최초 설정 후 baseline 변경·해제로 다시 쓰지 않는다.
- promotionHoldClass는 설정 시 progressionClass보다 높을 수 없다.
- promotionHoldClass와 마장마술·장애물 승인 중 하나 이상은 동시에 활성일 수 없다.
- 이후 baseline 교정으로 progressionClass가 hold보다 낮아져도 hold는 명시적으로 변경·해제할
  때까지 보존되며 effectiveClass를 올리지 않는다. progression이 다시 상승하면 같은 상한이 다시 적용된다.
- hold는 progression 누적을 멈추지 않는다.
- effectiveClass는 같은 입력에서 항상 결정적이다.
- 기존 완료 수업·Reservation·Coupon 이력은 클래스 교정으로 다시 쓰지 않는다.

## 결과와 후속

- M32-06은 일반 클래스 catalog 전체 전파, Anchor, progressionClass, 특수 승인 progression
  인정분과 교정, promotion hold, Special Approval 상호 배타와 effectiveClass를 구현한다.
- M32-07은 Horse progression 관리 시작 이후 완료 집계 오류의 조정 원장과 동시 보정을 구현한다.
- M32-08은 baseline·hold·특수 승인 상호 배타 안내, 특수 승인 progression 인정분 교정,
  조정 및 변경 전후 예상 결과와 감사를 제공한다.
- 기존 `manualClassOverride` 요구는 progression baseline과 promotion hold로 대체한다.
