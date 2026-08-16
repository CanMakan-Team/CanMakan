package com.canmakan.backend.admin;

import com.canmakan.backend.admin.dto.AdminUserSummaryResponse;
import com.canmakan.backend.admin.dto.UpdateAccountStatusRequest;
import com.canmakan.backend.admin.dto.UpdateAccountStatusResponse;
import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.dto.UsageStatisticsResponse;
import com.canmakan.backend.analytics.service.ConsumerTrendsService;
import com.canmakan.backend.analytics.service.UsageStatisticsService;
import com.canmakan.backend.shared.security.AuthUserDetails;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** System Admin HTTP endpoints. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final ConsumerTrendsService consumerTrendsService;
    private final UserAccountManagementService userAccountManagementService;
    private final UsageStatisticsService usageStatisticsService;

    @GetMapping("/usage-statistics")
    public UsageStatisticsResponse getUsageStatistics(
            @RequestParam(name = "periodDays", required = false, defaultValue = "7") int periodDays
    ) {
        return usageStatisticsService.generate(periodDays);
    }

    @GetMapping("/consumer-trends")
    public ConsumerTrendsResponse getConsumerTrends(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category
    ) {
        return consumerTrendsService.generateTrends(from, to, limit, category);
    }

    @GetMapping("/users")
    public List<AdminUserSummaryResponse> listUsers(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "active", required = false) Boolean active
    ) {
        return userAccountManagementService.listAccounts(query, role, active);
    }

    @PatchMapping("/users/{userId}/status")
    public UpdateAccountStatusResponse updateUserStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAccountStatusRequest request,
            @AuthenticationPrincipal AuthUserDetails principal
    ) {
        return userAccountManagementService.updateAccountStatus(
                principal.getUserId(),
                userId,
                request
        );
    }
}
