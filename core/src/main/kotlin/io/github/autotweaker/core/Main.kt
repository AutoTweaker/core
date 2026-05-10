/*
 * AutoTweaker
 * Copyright (C) 2026  WhiteElephant-abc
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/*
 * 日志规范
 *
 * 1. 模板: 动作-对象-结果，用过去时或现在完成时
 *    正确: "Sent user message  agentId=xxx  sessionId=xxx"
 *    正确: "Has loaded 3 external adapters  adapterDir=xxx"
 *    错误: "Sending user message..."  "Send user message"
 *
 * 2. 首字母大写，不加句号
 *    正确: "Settings initialized"
 *    错误: "settings initialized."  "Settings initialized."
 *
 * 3. 变量表示: key=value，空格分隔多个字段，无逗号
 *    正确: "agentId=abc  model=deepseek-chat  statusCode=200"
 *    错误: "agentId:abc, model:deepseek-chat"
 *
 * 4. 关键标识: 每条日志必须包含所属组件的标识
 *    - agentId: Agent 核心流转、phase、llm 调用
 *    - sessionId: Session 创建/恢复/销毁
 *    - tool: 工具调用
 *    - provider / model: LLM 提供者
 *    - 标识在最前或紧跟动作之后
 *
 * 5. 级别:
 *    - INFO:   正常生命周期事件（启动、初始化、创建、完成）
 *    - DEBUG:  内部细节（参数、中间状态、进入/退出）
 *    - WARN:   可恢复异常、重试、降级（不传异常对象）
 *    - ERROR:  不可恢复异常（必须传异常对象，让框架记堆栈）
 *
 * 6. 异常: catch 时描述业务上下文，最后一个参数传异常
 *    正确: logger.error("Failed to execute tool  agentId={}  tool={}  callId={}", agentId, tool, callId, e)
 *    错误: logger.error("Error: ${e.message}")  // 丢失堆栈
 *
 * 7. 变量注入: 用 SLF4J 占位符（{}）注入变量，禁止 Kotlin 字符串模板
 *    正确: logger.info("Agent created  agentId={}  model={}", agentId, model)
 *    错误: logger.info("Agent created  agentId=${agentId}  model=${model}")
 *    异常对象放在最后: logger.error("Failed config  key={}", key, e)
 *
 * 8. 肯定/否定一致:
 *    - 成功: "Settings initialized"  "Container started"
 *    - 失败: "Failed to initialize settings"  "Failed to start container"
 */

package io.github.autotweaker.core

fun main() {
	AutoTweaker.start()
	val latch = java.util.concurrent.CountDownLatch(1)
	Runtime.getRuntime().addShutdownHook(Thread { latch.countDown() })
	latch.await()
}
