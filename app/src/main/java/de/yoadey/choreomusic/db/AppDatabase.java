package de.yoadey.choreomusic.db;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import de.yoadey.choreomusic.model.Song;
import de.yoadey.choreomusic.model.Track;

@Database(entities = {Song.class, Track.class}, version = 4, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract RoomSongDao songDao();

    public abstract RoomTrackDao trackDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE 'SONG' ADD 'AMPLITUDES' BLOB");
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE 'SONG' ADD 'FILE_SUPPORTS_TRACKS' INTEGER DEFAULT 1 NOT NULL");
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE 'TRACK' ADD 'COLOR' INTEGER DEFAULT 0 NOT NULL");
        }
    };
}
