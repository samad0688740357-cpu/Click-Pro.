package id.biz.rrndev.autoclicker.data

import android.content.Context
import id.biz.rrndev.autoclicker.R
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity
import id.biz.rrndev.autoclicker.data.repository.FlowRepository

/**
 * Seeds the database with an example "Tap Center" flow on first launch.
 * Call this from the Application class or a ViewModel init block if the DB is empty.
 *
 * The example flow taps the approximate center of a 1080×1920 screen twice,
 * demonstrating how coordinates, delays, and loop settings work.
 */
object DatabaseSeeder {

    suspend fun seedIfEmpty(context: Context, repository: FlowRepository) {
        // getAllFlows() is a cold Flow; check early via collecting one value
        var isEmpty = true
        repository.getAllFlows().collect { list ->
            isEmpty = list.isEmpty()
            return@collect
        }
        if (!isEmpty) return

        val exampleFlow = ClickFlowEntity(
            name = context.getString(R.string.example_double_tap_center),
            loopCount = 3,
            loopDelayMs = 1000L
        )
        val examplePoints = listOf(
            // First tap – center of a 1080×1920 screen
            ClickPointEntity(
                flowId = 0L, // will be overwritten by saveFlowWithPoints
                x = 540f,
                y = 960f,
                delayMs = 200L,
                durationMs = 50L,
                repeatCount = 1,
                order = 0
            ),
            // Second tap – slightly offset to simulate a double-tap
            ClickPointEntity(
                flowId = 0L,
                x = 540f,
                y = 960f,
                delayMs = 150L,
                durationMs = 50L,
                repeatCount = 1,
                order = 1
            )
        )
        repository.saveFlowWithPoints(exampleFlow, examplePoints)
    }
}
