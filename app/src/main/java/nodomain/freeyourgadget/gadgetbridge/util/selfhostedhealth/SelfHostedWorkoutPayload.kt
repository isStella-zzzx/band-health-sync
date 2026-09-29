package nodomain.freeyourgadget.gadgetbridge.util.selfhostedhealth

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Allowlisted, non-location projection of existing Huawei/BaseActivitySummary records.
 * No raw entities, details, addresses or free-form workout names reach serialization.
 */
data class WorkoutSummaryInput(
    val localId: Long, val rawType: Int, val activityKind: Int,
    val startSeconds: Long, val endSeconds: Long, val activeSeconds: Long,
    val totalSeconds: Long?, val distanceMeters: Double?, val activeCalories: Double?,
    val averageHeartRate: Int?, val minHeartRate: Int?, val maxHeartRate: Int?
)

object SelfHostedWorkoutPayload {
    @JvmStatic
    fun attach(base: SelfHostedHealthPayloadSet, rows: List<WorkoutSummaryInput>, zone: ZoneId, captured: Long): SelfHostedHealthPayloadSet {
        // Exact object/byte preservation for legacy uploads when no workouts exist.
        if (rows.isEmpty()) return base
        val days = base.days.associateBy { it.date }.toMutableMap()
        val grouped = rows.sortedWith(compareBy({ it.startSeconds }, { it.localId })).groupBy {
            Instant.ofEpochSecond(it.startSeconds).atZone(zone).toLocalDate().toString()
        }
        for ((date, workouts) in grouped) {
            require(workouts.size <= 128) { "workout_summary_day_limit" }
            val body = days[date]?.body?.let { JSONObject(it.toString()) } ?: JSONObject().put("date", date)
            val array = JSONArray()
            val identities = HashSet<String>()
            for (w in workouts) {
                val id = identity(w)
                require(identities.add(id)) { "workout_summary_duplicate" }
                require(w.endSeconds > w.startSeconds && w.endSeconds - w.startSeconds <= 7 * 86400 &&
                    captured >= w.endSeconds && w.activeSeconds in 0..(w.endSeconds - w.startSeconds)) { "workout_summary_time" }
                require(w.totalSeconds == null || w.totalSeconds in w.activeSeconds..(w.endSeconds - w.startSeconds)) { "workout_summary_total" }
                require(w.distanceMeters == null || (w.distanceMeters.isFinite() && w.distanceMeters in 0.0..10000000.0))
                require(w.activeCalories == null || (w.activeCalories.isFinite() && w.activeCalories in 0.0..10000000.0))
                for (hr in listOf(w.averageHeartRate, w.minHeartRate, w.maxHeartRate)) require(hr == null || (hr in 1..300 && hr != 255))
                require(w.minHeartRate == null || w.maxHeartRate == null || w.minHeartRate <= w.maxHeartRate)
                require(w.averageHeartRate == null || ((w.minHeartRate == null || w.averageHeartRate >= w.minHeartRate) &&
                    (w.maxHeartRate == null || w.averageHeartRate <= w.maxHeartRate)))
                array.put(JSONObject().put("id", id).put("raw_type", w.rawType).put("activity_kind", w.activityKind)
                    .put("start_time", timestamp(w.startSeconds, zone)).put("end_time", timestamp(w.endSeconds, zone))
                    .put("timezone", zone.id).put("captured_at", timestamp(captured, zone))
                    .put("active_seconds", w.activeSeconds)
                    .put("total_seconds", w.totalSeconds ?: JSONObject.NULL)
                    .put("distance_meters", w.distanceMeters ?: JSONObject.NULL)
                    .put("active_calories", w.activeCalories ?: JSONObject.NULL)
                    .put("average_heart_rate", w.averageHeartRate ?: JSONObject.NULL)
                    .put("min_heart_rate", w.minHeartRate ?: JSONObject.NULL)
                    .put("max_heart_rate", w.maxHeartRate ?: JSONObject.NULL))
            }
            body.put("workouts", array)
            days[date] = SelfHostedHealthDay(date, body)
        }
        return SelfHostedHealthPayloadSet(days.toSortedMap().values.toList(), base.sleepUploadedThrough)
    }
    @JvmStatic
    fun identity(w: WorkoutSummaryInput): String {
        require(w.localId in 1..999999999999999L && w.rawType in 0..255 && w.activityKind >= 0)
        return "gbw1:${w.localId}:${w.startSeconds}:${w.rawType}:${w.activityKind}"
    }
    private fun timestamp(seconds: Long, zone: ZoneId) =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochSecond(seconds).atZone(zone))
}
