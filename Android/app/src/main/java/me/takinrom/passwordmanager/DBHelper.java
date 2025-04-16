package me.takinrom.passwordmanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "database";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_NAME = "data";
    private static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME + " (" +
            "id INTEGER NOT NULL PRIMARY KEY," +
            "service TEXT NOT NULL," +
            "login TEXT NOT NULL," +
            "encrypted_password TEXT NOT NULL" +
            ")";


    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        sqLiteDatabase.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(sqLiteDatabase);
    }

    public Account[] getAllRecords() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT id, service, login FROM " + TABLE_NAME, null)) {
            Account[] accounts = new Account[cursor.getCount()];
            Log.i("count", Integer.toString(cursor.getCount()));
            int i = 0;
            while (cursor.moveToNext()) {
                Log.i("position", Integer.toString(cursor.getPosition()));
                accounts[i++] = new Account(cursor.getInt(0), cursor.getString(1), cursor.getString(2));
            }
            return accounts;
        }
    }

    public void addNewRecord(String service, String login, String encryptedPassword) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("service", service);
        contentValues.put("login", login);
        contentValues.put("encrypted_password", encryptedPassword);
        db.insert(TABLE_NAME, null, contentValues);
    }

    public String getEncryptedPassword(int id) {
        SQLiteDatabase db = getReadableDatabase();
        String[] args = {Integer.toString(id),};
        try (Cursor cursor = db.rawQuery("SELECT encrypted_password FROM " + TABLE_NAME + " WHERE id=?", args)) {
            cursor.moveToNext();
            return cursor.getString(0);
        }
    }
}
