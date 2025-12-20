package win.hik.tofchizi.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tofchizi.db"
        private const val DATABASE_VERSION = 2
        const val TABLE_RECORDS = "records"
        const val COLUMN_ID = "_id"
        const val COLUMN_DISTANCE = "distance"
        const val COLUMN_PITCH = "pitch"
        const val COLUMN_YAW = "yaw"
        const val COLUMN_AZIMUTH = "azimuth"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_NOTE = "note"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_RECORDS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DISTANCE INTEGER,
                $COLUMN_PITCH REAL,
                $COLUMN_YAW REAL,
                $COLUMN_AZIMUTH REAL DEFAULT 0,
                $COLUMN_TIMESTAMP INTEGER,
                $COLUMN_NOTE TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_RECORDS ADD COLUMN $COLUMN_AZIMUTH REAL DEFAULT 0")
        }
    }

    fun addRecord(record: MeasurementRecord): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DISTANCE, record.distance)
            put(COLUMN_PITCH, record.pitch)
            put(COLUMN_YAW, record.yaw)
            put(COLUMN_AZIMUTH, record.azimuth)
            put(COLUMN_TIMESTAMP, record.timestamp)
            put(COLUMN_NOTE, record.note)
        }
        return db.insert(TABLE_RECORDS, null, values)
    }

    fun getAllRecords(): List<MeasurementRecord> {
        val list = mutableListOf<MeasurementRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_RECORDS, null, null, null, null, null, "$COLUMN_TIMESTAMP DESC")
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val dist = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DISTANCE))
                val p = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_PITCH))
                val y = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_YAW))
                val az = if (cursor.getColumnIndex(COLUMN_AZIMUTH) != -1) 
                    cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_AZIMUTH)) else 0f
                val ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val note = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE)) ?: ""
                list.add(MeasurementRecord(id, dist, p, y, az, ts, note))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
    
    fun deleteAllRecords() {
        val db = writableDatabase
        db.delete(TABLE_RECORDS, null, null)
    }
    
    fun trimRecords(limit: Int) {
        if (limit <= 0) return
        val db = writableDatabase
        // Keep top N latest records, delete the rest
        // SQLite: DELETE FROM records WHERE _id NOT IN (SELECT _id FROM records ORDER BY timestamp DESC LIMIT N)
        val sql = "DELETE FROM $TABLE_RECORDS WHERE $COLUMN_ID NOT IN (SELECT $COLUMN_ID FROM $TABLE_RECORDS ORDER BY $COLUMN_TIMESTAMP DESC LIMIT $limit)"
        db.execSQL(sql)
    }

    fun deleteRecords(ids: List<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) {
                db.delete(TABLE_RECORDS, "$COLUMN_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateNote(id: Long, note: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NOTE, note)
        }
        db.update(TABLE_RECORDS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }
}
