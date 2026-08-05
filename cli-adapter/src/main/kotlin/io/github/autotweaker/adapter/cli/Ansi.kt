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

package io.github.autotweaker.adapter.cli

@Suppress("unused")
/** ANSI 终端控制序列。 */
object Ansi {
	/** ESC (U+001B)，所有 ANSI 控制序列的前缀字节。 */
	private const val ESC = "\u001b"
	
	/** SCP &mdash; 保存当前光标位置。 */
	const val SAVE = "${ESC}[s"
	
	/** RCP &mdash; 恢复之前保存的光标位置。 */
	const val RESTORE = "${ESC}[u"
	
	/** DECTCEM &mdash; 隐藏光标。 */
	const val HIDE_CURSOR = "${ESC}[?25l"
	
	/** DECTCEM &mdash; 显示光标。 */
	const val SHOW_CURSOR = "${ESC}[?25h"
	
	/** EL &mdash; 清除整行。 */
	const val CLEAR_LINE = "${ESC}[2K"
	
	/** EL &mdash; 从光标清至行末。 */
	const val CLEAR_LINE_TO_END = "${ESC}[0K"
	
	/** ED &mdash; 清屏，光标回到原点。 */
	const val CLEAR_SCREEN = "${ESC}[2J"
	
	/** ED &mdash; 从光标清至屏幕末尾。 */
	const val CLEAR_SCREEN_TO_END = "${ESC}[0J"
	
	/** IL &mdash; 在光标处插入空行，后续行下移。 */
	const val INSERT_LINE = "${ESC}[L"
	
	/** DL &mdash; 删除光标所在行，后续行上滚。 */
	const val DELETE_LINE = "${ESC}[M"
	
	/** SGR &mdash; 重置所有样式。 */
	const val RESET = "${ESC}[0m"
	
	/** SGR &mdash; 粗体/高亮。 */
	const val BOLD = "${ESC}[1m"
	
	/** SGR &mdash; 暗淡/细体。 */
	const val DIM = "${ESC}[2m"
	
	/** SGR &mdash; 斜体。 */
	const val ITALIC = "${ESC}[3m"
	
	/** SGR &mdash; 下划线。 */
	const val UNDERLINE = "${ESC}[4m"
	
	/** SGR &mdash; 前景色设为黑色。 */
	const val BLACK = "${ESC}[30m"
	
	/** SGR &mdash; 前景色设为红色。 */
	const val RED = "${ESC}[31m"
	
	/** SGR &mdash; 前景色设为绿色。 */
	const val GREEN = "${ESC}[32m"
	
	/** SGR &mdash; 前景色设为黄色。 */
	const val YELLOW = "${ESC}[33m"
	
	/** SGR &mdash; 前景色设为蓝色。 */
	const val BLUE = "${ESC}[34m"
	
	/** SGR &mdash; 前景色设为品红。 */
	const val MAGENTA = "${ESC}[35m"
	
	/** SGR &mdash; 前景色设为青色。 */
	const val CYAN = "${ESC}[36m"
	
	/** SGR &mdash; 前景色设为白色。 */
	const val WHITE = "${ESC}[37m"
	
	/** SGR &mdash; 背景色设为黑色。 */
	const val BG_BLACK = "${ESC}[40m"
	
	/** SGR &mdash; 背景色设为红色。 */
	const val BG_RED = "${ESC}[41m"
	
	/** SGR &mdash; 背景色设为绿色。 */
	const val BG_GREEN = "${ESC}[42m"
	
	/** SGR &mdash; 背景色设为黄色。 */
	const val BG_YELLOW = "${ESC}[43m"
	
	/** SGR &mdash; 背景色设为蓝色。 */
	const val BG_BLUE = "${ESC}[44m"
	
	/** SGR &mdash; 背景色设为品红。 */
	const val BG_MAGENTA = "${ESC}[45m"
	
	/** SGR &mdash; 背景色设为青色。 */
	const val BG_CYAN = "${ESC}[46m"
	
	/** SGR &mdash; 背景色设为白色。 */
	const val BG_WHITE = "${ESC}[47m"
	
	/** 进入备选屏幕缓冲区。 */
	const val ALT_SCREEN_ON = "${ESC}[?1049h"
	
	/** 退出备选屏幕缓冲区，恢复主屏幕。 */
	const val ALT_SCREEN_OFF = "${ESC}[?1049l"
	
	/** DECSTBM &mdash; 重置滚动区域为全屏。 */
	const val SCROLL_RESET = "${ESC}[r"
	
	/** 用 [codes] 包裹 [text]，末尾自动追加 [RESET]。 */
	fun styled(text: String, vararg codes: String) = codes.joinToString("") + text + RESET
	
	/** CUP &mdash; 光标定位到第 [row] 行第 [col] 列。 */
	fun to(row: Int, col: Int) = "${ESC}[$row;${col}H"
	
	/** CUU &mdash; 光标上移 [n] 行。 */
	fun up(n: Int = 1) = "${ESC}[${n}A"
	
	/** CUD &mdash; 光标下移 [n] 行。 */
	fun down(n: Int = 1) = "${ESC}[${n}B"
	
	/** CUB &mdash; 光标左移 [n] 列。 */
	fun left(n: Int = 1) = "${ESC}[${n}D"
	
	/** CUF &mdash; 光标右移 [n] 列。 */
	fun right(n: Int = 1) = "${ESC}[${n}C"
	
	/** DECSTBM &mdash; 设置滚动区域，[top] 到 [bottom] 行参与滚动。 */
	fun scrollRegion(top: Int, bottom: Int) = "${ESC}[$top;${bottom}r"
	
	/** OSC 0 &mdash; 设置终端窗口标题，以 BEL 终止。 */
	fun title(text: String) = "${ESC}]0;$text\u0007"
}
