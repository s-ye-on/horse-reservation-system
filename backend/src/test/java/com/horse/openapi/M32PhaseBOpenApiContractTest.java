package com.horse.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.horse.TestcontainersConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class M32PhaseBOpenApiContractTest {

	private static final List<String> GENERAL_GRADES = List.of(
		"FIRST_RIDE",
		"ROUND_BEGINNER",
		"ROUND_TROT",
		"LARGE_ARENA_BEGINNER",
		"LARGE_ARENA_TROT",
		"CANTER_BEGINNER",
		"CANTER");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void Phase_B_가족_클래스_Coupon_snapshot과_If_Match_계약을_공개한다() throws Exception {
		final JsonNode document = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());

		assertOperation(document, "/api/admin/family-groups", "get", "getFamilyGroups");
		assertOperation(document, "/api/admin/family-groups", "post", "createFamilyGroup");
		assertOperation(document, "/api/admin/family-groups/member-candidates", "get",
			"getFamilyMemberCandidates");
		assertOperation(document, "/api/admin/family-groups/{groupId}/members", "get",
			"getFamilyGroupMembers");
		assertOperation(document, "/api/admin/family-groups/{groupId}/members", "post",
			"addFamilyGroupMember");
		assertOperation(document, "/api/admin/family-groups/{groupId}/members/{memberId}", "delete",
			"removeFamilyGroupMember");
		assertOperation(document, "/api/admin/family-groups/{groupId}/dissolution", "post",
			"dissolveFamilyGroup");
		assertOperation(document, "/api/admin/family-groups/{groupId}/audit-logs", "get",
			"getFamilyGroupAuditLogs");

		assertProgressionCommand(document, "/api/admin/members/{memberId}/class-progression/baseline", "put",
			"setMemberProgressionBaseline");
		assertProgressionCommand(document, "/api/admin/members/{memberId}/class-progression/baseline", "delete",
			"removeMemberProgressionBaseline");
		assertProgressionCommand(document, "/api/admin/members/{memberId}/class-progression/promotion-hold", "put",
			"setMemberPromotionHold");
		assertProgressionCommand(document, "/api/admin/members/{memberId}/class-progression/promotion-hold", "delete",
			"removeMemberPromotionHold");
		assertProgressionCommand(document,
			"/api/admin/members/{memberId}/class-progression/special-approval-credit", "put",
			"correctMemberSpecialApprovalCredit");
		assertProgressionCommand(document,
			"/api/admin/members/{memberId}/class-progression/ride-count-adjustments", "post",
			"adjustMemberActualCompletedRideCount");
		assertOperation(document, "/api/admin/members/{memberId}/class-progression/preview", "post",
			"previewMemberClassProgression");
		assertOperation(document, "/api/admin/members/{memberId}/class-progression/audit-logs", "get",
			"getMemberClassProgressionAuditLogs");

		assertRequired(document, "FamilyGroupCreateRequest", "name", "reason");
		assertRequired(document, "FamilyMemberAddRequest", "memberId", "reason");
		assertRequired(document, "MemberClassProgressionPreviewResponse", "stateToken", "current", "expected");
		assertRequired(document, "MemberProgressionBaselineRequest", "baselineClass", "reason");
		assertRequired(document, "MemberPromotionHoldRequest", "promotionHoldClass", "reason");
		assertRequired(document, "MemberProgressionCreditCorrectionRequest",
			"specialApprovalProgressionCredit", "reason");
		assertRequired(document, "MemberRideCountAdjustmentRequest", "delta", "reason");

		assertEnum(document, "AdminMemberResponse", "progressionClass", GENERAL_GRADES);
		assertEnum(document, "AdminMemberResponse", "effectiveClass", GENERAL_GRADES);
		assertEnum(document, "MemberClassProgressionProjectionResponse", "progressionClass", GENERAL_GRADES);
		assertEnum(document, "MemberClassProgressionProjectionResponse", "effectiveClass", GENERAL_GRADES);
		assertEnum(document, "MemberProgressionBaselineRequest", "baselineClass", GENERAL_GRADES);
		assertEnum(document, "MemberPromotionHoldRequest", "promotionHoldClass", GENERAL_GRADES);

		assertNullable(document, "FamilyGroupSummaryResponse", "dissolvedAt");
		assertNullable(document, "FamilyGroupAuditResponse", "memberId");
		assertNullable(document, "FamilyGroupAuditResponse", "fromState");
		assertNullable(document, "FamilyGroupAuditResponse", "toState");
		assertNullable(document, "AdminMemberResponse", "progressionManagementStartedAt");
		assertNullable(document, "AdminMemberResponse", "progressionBaselineClass");
		assertNullable(document, "AdminMemberResponse", "promotionHoldClass");
		assertNullable(document, "MemberClassProgressionProjectionResponse", "baselineClass");
		assertNullable(document, "MemberClassProgressionProjectionResponse", "promotionHoldClass");
		assertNullable(document, "MemberClassProgressionAuditResponse", "fromState");
		assertNullable(document, "MemberClassProgressionAuditResponse", "toState");
		assertRequired(document, "FamilyGroupAuditStateResponse", "groupId", "status");
		assertRequired(document, "MemberClassProgressionAuditStateResponse",
			"actualCompletedRideCount", "progressionValue", "progressionClass", "effectiveClass",
			"specialApprovalProgressionCredit", "rideCountDelta");

		assertRequired(document, "MemberCouponUsageResponse", "usageLogId", "couponId", "reservationId",
			"memberId", "couponOwnerMemberId", "familyGroupId", "action", "countDelta", "occurredAt", "actorType");
		assertNullable(document, "MemberCouponUsageResponse", "reservationId");
		assertNullable(document, "MemberCouponUsageResponse", "familyGroupId");

		final JsonNode stateToken = document.at(
			"/components/schemas/MemberClassProgressionPreviewResponse/properties/stateToken");
		assertThat(stateToken.get("pattern").asText()).isEqualTo("^\\\"[0-9a-f]{64}\\\"$");
		assertThat(stateToken.get("example").asText()).startsWith("\"").endsWith("\"");
	}

	private static void assertProgressionCommand(
		JsonNode document,
		String path,
		String method,
		String operationId
	) {
		assertOperation(document, path, method, operationId);
		final JsonNode parameters = operation(document, path, method).get("parameters");
		assertThat(parameters.valueStream()).anySatisfy(parameter -> {
			assertThat(parameter.get("name").asText()).isEqualTo("If-Match");
			assertThat(parameter.get("in").asText()).isEqualTo("header");
			assertThat(parameter.get("schema").get("type").asText()).isEqualTo("string");
			assertThat(parameter.get("description").asText()).contains("quoted strong entity-tag", "그대로 전달");
		});
	}

	private static void assertOperation(JsonNode document, String path, String method, String operationId) {
		final JsonNode operation = operation(document, path, method);
		assertThat(operation.get("operationId").asText()).isEqualTo(operationId);
		final JsonNode security = operation.has("security") ? operation.get("security") : document.get("security");
		assertThat(security.at("/0/bearerAuth").isArray()).isTrue();
	}

	private static JsonNode operation(JsonNode document, String path, String method) {
		return document.at("/paths/" + path.replace("/", "~1") + "/" + method);
	}

	private static void assertRequired(JsonNode document, String schemaName, String... fields) {
		final List<String> required = document.at("/components/schemas/" + schemaName + "/required")
			.valueStream()
			.map(JsonNode::asText)
			.toList();
		assertThat(required).contains(fields);
	}

	private static void assertNullable(JsonNode document, String schemaName, String field) {
		final JsonNode schema = document.at("/components/schemas/" + schemaName + "/properties/" + field);
		final boolean nullableType = schema.path("type").valueStream()
			.map(JsonNode::asText)
			.anyMatch("null"::equals);
		final boolean nullableOneOf = schema.path("oneOf").valueStream()
			.anyMatch(option -> "null".equals(option.path("type").asText()));
		assertThat(nullableType || nullableOneOf).isTrue();
	}

	private static void assertEnum(JsonNode document, String schemaName, String field, List<String> expected) {
		assertThat(document.at("/components/schemas/" + schemaName + "/properties/" + field + "/enum")
			.valueStream()
			.map(JsonNode::asText)
			.toList())
			.containsExactlyElementsOf(expected);
	}
}
