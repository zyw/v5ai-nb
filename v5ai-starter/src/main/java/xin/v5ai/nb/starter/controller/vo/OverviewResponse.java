package xin.v5ai.nb.starter.controller.vo;

public record OverviewResponse(
        long agents,
        long models,
        long knowledgeBases,
        long skills,
        long totalRuns,
        long todayRuns,
        long todayModelCalls,
        long todayTokens
) {
}
