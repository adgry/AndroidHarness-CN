package com.androidharness.app.ui.automation

import com.androidharness.app.automation.*
import org.junit.Assert.*
import org.junit.Test

class ScheduleStatusTest {
    private val task = AutomationTask(title = "Build", prompt = "Build", projectId = "p", projectName = "Project",
        schedule = AutomationSchedule.ONCE, nextRunAt = 1000, unmeteredOnly = true)
    @Test fun dueScheduleShowsWhyItIsWaiting() {
        assertEquals("等待不限流量网络", scheduleStatus(task, AutomationConnection(true, false), 1000))
        assertEquals("等待开始", scheduleStatus(task, AutomationConnection(true, true), 1000))
    }
    @Test fun pauseAndCancellationDoNotLookLikeWaitingForNetwork() {
        assertEquals("已暂停", scheduleStatus(task.copy(enabled = false, lastStatus = AutomationStatus.PAUSED), AutomationConnection(), 2000))
        assertEquals("已取消", scheduleStatus(task.copy(enabled = false, lastStatus = AutomationStatus.CANCELLED), AutomationConnection(), 2000))
    }
    @Test fun blockedRunsNeedAttentionEvenWhenConnectionReturns() {
        assertEquals("需要你处理", scheduleStatus(task.copy(enabled = false, lastStatus = AutomationStatus.BLOCKED), AutomationConnection(true, true), 2000))
    }
}
