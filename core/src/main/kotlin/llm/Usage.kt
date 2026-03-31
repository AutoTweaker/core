package io.github.whiteelephant.autotweaker.core.llm

/**
 * 表示令牌使用情况。
 * 统计本次API调用消耗的令牌数量。
 *
 * @property totalTokens 总token数量
 * @property contextWindowUsage 上下文窗口占用量（百分比）
 * @property inputCost 输入花费（单位：元）
 * @property outputCost 输出花费（单位：元）
 * @property totalCost 总花费（单位：元）
 */
data class Usage(
    val totalTokens: Int,
    val contextWindowUsage: Double,
    val inputCost: Double,
    val outputCost: Double,
    val totalCost: Double
)