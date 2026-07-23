package com.horse.schedules.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ReservationMemberDayGuardId implements Serializable, Comparable<ReservationMemberDayGuardId> {

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "lesson_date", nullable = false)
	private LocalDate lessonDate;

	protected ReservationMemberDayGuardId() {
	}

	private ReservationMemberDayGuardId(Long memberId, LocalDate lessonDate) {
		this.memberId = memberId;
		this.lessonDate = lessonDate;
	}

	public static ReservationMemberDayGuardId of(Long memberId, LocalDate lessonDate) {
		return new ReservationMemberDayGuardId(memberId, lessonDate);
	}

	@Override
	public int compareTo(ReservationMemberDayGuardId other) {
		final int memberComparison = memberId.compareTo(other.memberId);
		return memberComparison != 0 ? memberComparison : lessonDate.compareTo(other.lessonDate);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ReservationMemberDayGuardId that)) {
			return false;
		}
		return Objects.equals(memberId, that.memberId) && Objects.equals(lessonDate, that.lessonDate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberId, lessonDate);
	}

	public Long getMemberId() {
		return memberId;
	}

	public LocalDate getLessonDate() {
		return lessonDate;
	}
}
