package xin.v5ai.nb.common.platform;

import java.time.LocalDate;

/**
 * 应用当日用量快照。
 *
 * @param appKey     应用标识
 * @param usageDate  日期
 * @param modelCalls 当日模型调用次数
 * @param tokens     当日 Token 用量
 */
public record AppUsage(String appKey, LocalDate usageDate, long modelCalls, long tokens) {
}
