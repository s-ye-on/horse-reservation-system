package com.horse.reservations.domain;

import java.time.LocalDateTime;

public enum ReservationAction {

	CHANGE {
		@Override
		void validate(Reservation reservation, LocalDateTime actionAt) {
			reservation.validateCanChange(actionAt);
		}
	},
	CANCEL {
		@Override
		void validate(Reservation reservation, LocalDateTime actionAt) {
			reservation.validateCanCancel(actionAt);
		}
	},
	COMPLETE {
		@Override
		void validate(Reservation reservation, LocalDateTime actionAt) {
			reservation.validateCanComplete(actionAt);
		}
	},
	NO_SHOW {
		@Override
		void validate(Reservation reservation, LocalDateTime actionAt) {
			reservation.validateCanNoShow(actionAt);
		}
	},
	APPROVE {
		@Override
		void validate(Reservation reservation, LocalDateTime actionAt) {
			reservation.validateCanApprove(actionAt);
		}
	};

	abstract void validate(Reservation reservation, LocalDateTime actionAt);
}
