package nodomain.freeyourgadget.gadgetbridge.util.selfhostedhealth

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries

/** Read-only projection of existing normalization; never calls a parser that opens a track file.
 * DBHelper resolves an internal selector only. No hardware or account identity is serialized.
 */
object SelfHostedWorkoutReader {
    fun read(device: GBDevice, fromSeconds: Long, toSeconds: Long): List<WorkoutSummaryInput> {
        if (device.deviceCoordinator !is HuaweiCoordinator) return emptyList()
        return GBApplication.acquireDbReadOnly().use { db ->
            val selector = DBHelper.getDevice(device, db.daoSession) ?: return@use emptyList()
            // Explicit columns: never load GPX paths, raw blobs or coordinates from either table.
            db.database.rawQuery("""
                SELECT h.WORKOUT_ID,h.TYPE,b.ACTIVITY_KIND,b.START_TIME,b.END_TIME,b.SUMMARY_DATA,
                       h.DURATION,h.TOTAL_TIME,h.DISTANCE,h.CALORIES
                FROM BASE_ACTIVITY_SUMMARY b JOIN HUAWEI_WORKOUT_SUMMARY_SAMPLE h
                  ON b.DEVICE_ID=h.DEVICE_ID AND b.START_TIME=h.START_TIMESTAMP*1000
                WHERE b.DEVICE_ID=? AND b.START_TIME>=? AND b.START_TIME<? AND b.END_TIME<=?
                ORDER BY b.START_TIME,h.WORKOUT_ID
            """.trimIndent(), arrayOf(selector.id.toString(), (fromSeconds * 1000).toString(),
                (toSeconds * 1000).toString(), (toSeconds * 1000).toString())).use { cursor ->
                val rows = mutableListOf<WorkoutSummaryInput>()
                while (cursor.moveToNext()) {
                    // Acquisition already created this normalized summary. Do not reparse Bluetooth,
                    // detail samples or route files in the upload worker.
                    val summary = ActivitySummaryData.fromJson(if (cursor.isNull(5)) null else cursor.getString(5))
                    fun number(key: String): Double? = summary.getNumber(key, null)?.toDouble()
                    fun raw(index: Int): Long? = if (cursor.isNull(index)) null else cursor.getLong(index).takeIf { it >= 0 }
                    fun integral(value: Double?): Long? {
                        if (value == null) return null
                        require(value.isFinite() && value >= 0 && value == value.toLong().toDouble()) { "workout_summary_number" }
                        return value.toLong()
                    }
                    val active = integral(number(ActivitySummaryEntries.ACTIVE_SECONDS)) ?: raw(6)
                        ?: throw IllegalArgumentException("workout_summary_duration_missing")
                    require(cursor.getLong(3) % 1000L == 0L && cursor.getLong(4) % 1000L == 0L)
                    rows.add(WorkoutSummaryInput(
                        cursor.getLong(0), cursor.getInt(1) and 255, cursor.getInt(2),
                        cursor.getLong(3) / 1000, cursor.getLong(4) / 1000, active, raw(7),
                        number(ActivitySummaryEntries.DISTANCE_METERS) ?: raw(8)?.toDouble(),
                        number(ActivitySummaryEntries.CALORIES_BURNT) ?: raw(9)?.toDouble(),
                        integral(number(ActivitySummaryEntries.HR_AVG))?.toInt(),
                        integral(number(ActivitySummaryEntries.HR_MIN))?.toInt(),
                        integral(number(ActivitySummaryEntries.HR_MAX))?.toInt()
                    ))
                }
                rows
            }
        }
    }
}
