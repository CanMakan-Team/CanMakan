package com.canmakan.backend.admin;

import com.canmakan.backend.analytics.dto.ConsumerTrendsResponse;
import com.canmakan.backend.analytics.service.ConsumerTrendsService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** System Admin HTTP endpoints. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final ConsumerTrendsService consumerTrendsService;

    @GetMapping("/consumer-trends")
    public ConsumerTrendsResponse getConsumerTrends(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return consumerTrendsService.generateTrends(from, to, limit);
    }
}
