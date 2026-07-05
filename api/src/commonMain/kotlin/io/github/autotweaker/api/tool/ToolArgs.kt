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

package io.github.autotweaker.api.tool

/**
 * 表示 [Tool] 的调用参数，可以为数据类或密封类，数据类表示此工具只有一个 function，密封类 [ToolArgs] 的所有子类（下文称为子类）的类名将作为 function 名。对于数据类，AutoTweaker 会生成一个名为 `run` 的 function。
 *
 * [ToolArgs] 内的字段可以包含几乎任何类型，[Int]、[Map]、数据类、密封类、嵌套的密封类。为一个字段提供默认值可以让这个字段可选，但也仅仅是可选，LLM 只知道这个参数可选，看不到默认值，除非在描述中声明。
 *
 * [ToolArgs] 的子类类名、参数名将转换为 LLM 看到的工具声明，`PascalCase` 将被转换为 `snake_case`，所以尽管写符合 kotlin 风格的类名、字段名，LLM 会看到符合 Json 习惯的 `snake_case` 命名。由于这些命名本身直接呈现给 LLM，形参直接作为字段名，子类类名拼接到工具名称（来自 [Tool.name]）后，例如 `read-summarize`。
 *
 * 可以参考 [io.github.autotweaker.api.types.tool.args] 下 AutoTweaker 内置工具的声明，如 [io.github.autotweaker.api.types.tool.args.ReadArgs]
 *
 * 子类类名、字段名都绝对不能打 `@SerialName` 标签，这会导致 LLM 看到的参数声明与实际字段不匹配，从而导致 LLM 的调用请求在反序列化时失败。[ToolArgs] 实现本身打了没事，但是没任何用。
 *
 * @see io.github.autotweaker.api.types.tool.args.ReadArgs
 */
interface ToolArgs
