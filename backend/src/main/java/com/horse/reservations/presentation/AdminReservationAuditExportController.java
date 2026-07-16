package com.horse.reservations.presentation;

import java.nio.charset.StandardCharsets;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.horse.reservations.application.AdminReservationAuditExportService;
import com.horse.reservations.presentation.dto.AdminReservationAuditExportRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminReservationAuditExportController {

	private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");
	private static final String FILE_NAME = "reservation-audit.csv";

	private final AdminReservationAuditExportService service;

	public AdminReservationAuditExportController(AdminReservationAuditExportService service) {
		this.service = service;
	}

	@GetMapping(value = "/export", produces = "text/csv")
	public ResponseEntity<byte[]> export(
		@Valid @ParameterObject @ModelAttribute AdminReservationAuditExportRequest request
	) {
		final byte[] content = service.export(
			request.keyword(),
			request.reservationId(),
			request.occurredDateFrom(),
			request.occurredDateTo(),
			request.actorType(),
			request.changeType());
		return ResponseEntity.ok()
			.contentType(CSV_MEDIA_TYPE)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
				.filename(FILE_NAME, StandardCharsets.UTF_8)
				.build()
				.toString())
			.cacheControl(CacheControl.noStore())
			.body(content);
	}
}
