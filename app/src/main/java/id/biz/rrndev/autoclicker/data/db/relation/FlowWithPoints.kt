package id.biz.rrndev.autoclicker.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import id.biz.rrndev.autoclicker.data.entity.ClickFlowEntity
import id.biz.rrndev.autoclicker.data.entity.ClickPointEntity

/**
 * Room relation that loads a [ClickFlowEntity] together with all its [ClickPointEntity] children
 * in a single query. Used by the repository to return complete flow data.
 */
data class FlowWithPoints(
    @Embedded val flow: ClickFlowEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "flowId"
    )
    val points: List<ClickPointEntity>
)
