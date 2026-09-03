package com.horse.families.presentation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.horse.families.application.FamilyGroupCommandService;
import com.horse.families.presentation.dto.FamilyGroupCreateRequest;
import com.horse.families.presentation.dto.FamilyGroupResponse;
import com.horse.families.presentation.dto.FamilyMemberAddRequest;
import com.horse.families.presentation.dto.FamilyMembershipResponse;
import com.horse.families.presentation.dto.FamilyReasonRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/family-groups")
@ApiResponses({
	@ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "401", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "403", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse"))),
	@ApiResponse(responseCode = "409", content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ErrorResponse")))
})
public class AdminFamilyGroupCommandController {

	private final FamilyGroupCommandService service;

	public AdminFamilyGroupCommandController(FamilyGroupCommandService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(operationId = "createFamilyGroup")
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "201", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = FamilyGroupResponse.class)))
	public FamilyGroupResponse create(
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody FamilyGroupCreateRequest request
	) {
		return FamilyGroupResponse.from(service.create(
			request.name(),
			adminSubject,
			request.reason()));
	}

	@PostMapping("/{groupId}/members")
	@Operation(operationId = "addFamilyGroupMember")
	@ResponseStatus(HttpStatus.CREATED)
	@ApiResponse(responseCode = "201", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = FamilyMembershipResponse.class)))
	public FamilyMembershipResponse addMember(
		@PathVariable long groupId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody FamilyMemberAddRequest request
	) {
		return FamilyMembershipResponse.from(service.addMember(
			groupId,
			request.memberId(),
			adminSubject,
			request.reason()));
	}

	@DeleteMapping("/{groupId}/members/{memberId}")
	@Operation(operationId = "removeFamilyGroupMember")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = FamilyMembershipResponse.class)))
	public FamilyMembershipResponse removeMember(
		@PathVariable long groupId,
		@PathVariable long memberId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody FamilyReasonRequest request
	) {
		return FamilyMembershipResponse.from(service.removeMember(
			groupId,
			memberId,
			adminSubject,
			request.reason()));
	}

	@PostMapping("/{groupId}/dissolution")
	@Operation(operationId = "dissolveFamilyGroup")
	@ApiResponse(responseCode = "200", content = @Content(
		mediaType = "application/json",
		schema = @Schema(implementation = FamilyGroupResponse.class)))
	public FamilyGroupResponse dissolve(
		@PathVariable long groupId,
		@AuthenticationPrincipal(expression = "subject") String adminSubject,
		@Valid @RequestBody FamilyReasonRequest request
	) {
		return FamilyGroupResponse.from(service.dissolve(
			groupId,
			adminSubject,
			request.reason()));
	}
}
