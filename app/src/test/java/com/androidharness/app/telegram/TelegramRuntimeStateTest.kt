package com.androidharness.app.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class TelegramRuntimeStateTest {
    @Test
    fun `Chinese approval and answer prompts take precedence over active work`() {
        val active = TelegramRuntimeState(running = true, connected = true, activeRequestId = 42)
        assertEquals("等待批准", active.copy(action = "等待你的批准").label)
        assertEquals("等待回答", active.copy(action = "等待你的回答").label)
    }

    @Test
    fun `legacy English prompts still indicate blocked requests`() {
        val active = TelegramRuntimeState(running = true, connected = true, activeRequestId = 42)
        assertEquals("等待批准", active.copy(action = "Waiting for your APPROVAL").label)
        assertEquals("等待回答", active.copy(action = "Waiting for your answer").label)
    }

    @Test
    fun `ordinary active request differs from idle listening`() {
        val idle = TelegramRuntimeState(running = true, connected = true)
        assertEquals("正在监听", idle.label)
        assertEquals("正在处理", idle.copy(activeRequestId = 42, action = "正在撰写回复…").label)
    }

    @Test
    fun `paused and disconnected states take precedence over stale prompt text`() {
        val stale = TelegramRuntimeState(action = "等待你的批准", activeRequestId = 42)
        assertEquals("已暂停", stale.label)
        assertEquals("正在连接", stale.copy(running = true).label)
        assertEquals("正在重新连接", stale.copy(running = true, reconnecting = true).label)
    }
}
