# MVP 3.2: 가족 쿠폰과 회원 클래스

## 목표

Coupon 소유권과 회원별 기승 이력을 보존하면서 가족 공유 권한, progression baseline과
promotion hold를 감사 가능한 구조로 제공한다.

## 포함 범위

- 관리자 가족 그룹과 구성원 관리
- 가족 전체 유효 쿠폰 선택·점유·사용·반환
- 예약 회원, 쿠폰 원소유자와 가족 그룹 snapshot 이력
- 가족 구성원의 교차 동시 예약
- 실제 기승 횟수와 관리자 시작 baseline을 반영한 일반 클래스 progression
- 특수 승인 시 대마장 속보 threshold 부족분만 고정하는 progression 인정 credit
- progression과 독립적인 관리자 promotion hold 안전 상한
- 기존 특수 승인 자격과 promotion hold의 상호 배타 계약
- Horse 운영 이후 기승 횟수 집계 오류 조정 원장
- 관련 관리자 웹과 OpenAPI 계약

## 제외 범위

- Coupon 레코드 물리 병합과 가족 합산 잔액
- 회원의 가족 그룹 직접 관리
- 가족 대표자와 전용 가족 이관 Command
- 다른 가족 구성원을 대신하는 예약
- 영구적인 일반 클래스 manual override와 기승마다 독립 누적하는 별도 progression counter
- 시스템 도입 이전 기승 횟수·날짜·기간 복원
- 반복 수업, 주간 일정과 외부 Sheets 동기화

## 완료 조건

Phase B 전체 Gate와 OpenAPI diff가 승인된 뒤 `M4-02~M4-06`을 시작할 수 있다.
