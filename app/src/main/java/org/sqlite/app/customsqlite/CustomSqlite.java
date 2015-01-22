
package org.sqlite.app.customsqlite;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.sqlite.app.customsqlite.util.ExecutorUtil;
import org.sqlite.database.sqlite.SQLiteDatabase;
import org.sqlite.database.sqlite.SQLiteDatabaseCorruptException;
import org.sqlite.database.sqlite.SQLiteOpenHelper;
import org.sqlite.database.sqlite.SQLiteStatement;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;

import bolts.Continuation;
import bolts.Task;

public class CustomSqlite extends Activity {

    private StringBuilder debugText;
    private TextView myTV;          /* Text view widget */
    private ProgressBar myProgress;
    private Button myButton;
    private int myNTest;            /* Number of tests attempted */
    private int myNErr;             /* Number of tests failed */

    File DB_PATH;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        myTV = (TextView)findViewById(R.id.tv_widget);
        myProgress = (ProgressBar)findViewById(R.id.progress);
        myButton = (Button)findViewById(R.id.btnRun);

        myButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTheTests();
            }
        });
    }

    private void reportVersion() {
        SQLiteDatabase db = null;
        SQLiteStatement st;
        String res;

        db = SQLiteDatabase.openOrCreateDatabase(":memory:", null);
        st = db.compileStatement("SELECT sqlite_version()");
        res = st.simpleQueryForString();

        appendString("SQLite version " + res + "\n\n");
    }

    private void testWarning(String name, String warning) {
        appendString("WARNING:" + name + ": " + warning + "\n");
    }

    private void testResult(String name, String res, String expected) {
        appendString(name + "... ");
        myNTest++;

        if (res.equals(expected)) {
            appendString("ok\n");
        } else {
            myNErr++;
            appendString("FAILED\n");
            appendString("   res=     \"" + res + "\"\n");
            appendString("   expected=\"" + expected + "\"\n");
        }
    }

    /*
    ** Test if the database at DB_PATH is encrypted or not. The db
    ** is assumed to be encrypted if the first 6 bytes are anything
    ** other than "SQLite".
    **
    ** If the test reveals that the db is encrypted, return the string
    ** "encrypted". Otherwise, "unencrypted".
    */
    private String dbIsEncrypted() throws Exception {
        FileInputStream in = new FileInputStream(DB_PATH);

        byte[] buffer = new byte[6];
        in.read(buffer, 0, 6);

        String res = "encrypted";
        if (Arrays.equals(buffer, (new String("SQLite")).getBytes())){
            res = "unencrypted";
        }
        return res;
    }

    /*
    ** Test that a database connection may be accessed from a second thread.
    */
    private void threadTest1() {
        SQLiteDatabase.deleteDatabase(DB_PATH);
        final SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);

        String db_path2 = DB_PATH.toString() + "2";

        db.execSQL("CREATE TABLE t1(x, y)");
        db.execSQL("INSERT INTO t1 VALUES (1, 2), (3, 4)");

        Thread t = new Thread( new Runnable() {
            public void run() {
                SQLiteStatement st = db.compileStatement("SELECT sum(x+y) FROM t1");
                String res = st.simpleQueryForString();
                testResult("thread_test_1", res, "10");
            }
        });

        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            // nothing
        }
    }

    /*
    ** Test that a database connection may be accessed from a second thread.
    */
    private void threadTest2() {
        SQLiteDatabase.deleteDatabase(DB_PATH);
        final SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);

        db.execSQL("CREATE TABLE t1(x, y)");
        db.execSQL("INSERT INTO t1 VALUES (1, 2), (3, 4)");

        db.enableWriteAheadLogging();
        db.beginTransactionNonExclusive();
        db.execSQL("INSERT INTO t1 VALUES (5, 6)");

        Thread t = new Thread( new Runnable() {
            public void run() {
                SQLiteStatement st = db.compileStatement("SELECT sum(x+y) FROM t1");
                String res = st.simpleQueryForString();
            }
        });

        t.start();
        String res = "concurrent";

        int i;
        for (i=0; i<20 && t.isAlive(); i++) {
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              // nothing
            }
        }

        if (t.isAlive()) {
            res = "blocked";
        }

        db.endTransaction();
        try {
            t.join();
        } catch (InterruptedException e) {
            // nothing
        }

        if (SQLiteDatabase.hasCodec()) {
            testResult("thread_test_2", res, "blocked");
        } else {
            testResult("thread_test_2", res, "concurrent");
        }
    }

    /*
    ** Use a Cursor to loop through the results of a SELECT query.
    */
    private void csrTest2() throws Exception {
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        String res = "";
        String expect = "";
        int i;
        int nRow = 0;

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("BEGIN");
        for (i=0; i<1000; i++){
            db.execSQL("INSERT INTO t1 VALUES ('one'), ('two'), ('three')");
            expect += ".one.two.three";
        }
        db.execSQL("COMMIT");
        Cursor c = db.rawQuery("SELECT x FROM t1", null);
        if (c != null) {
            boolean bRes;
            for(bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()){
                String x = c.getString(0);
                res = res + "." + x;
            }
        } else {
            testWarning("csr_test_1", "c==NULL");
        }
        testResult("csr_test_2.1", res, expect);

        db.execSQL("BEGIN");
        for (i=0; i<1000; i++) {
            db.execSQL("INSERT INTO t1 VALUES (X'123456'), (X'789ABC'), (X'DEF012')");
            db.execSQL("INSERT INTO t1 VALUES (45), (46), (47)");
            db.execSQL("INSERT INTO t1 VALUES (8.1), (8.2), (8.3)");
            db.execSQL("INSERT INTO t1 VALUES (NULL), (NULL), (NULL)");
        }
        db.execSQL("COMMIT");

        c = db.rawQuery("SELECT x FROM t1", null);
        if (c != null) {
            boolean bRes;
            for (bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()) {
                nRow++;
            }
        } else {
            testWarning("csr_test_1", "c==NULL");
        }

        testResult("csr_test_2.2", "" + nRow, "15000");

        db.close();
    }

    private void csrTest1() throws Exception {
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        String res = "";

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("INSERT INTO t1 VALUES ('one'), ('two'), ('three')");

        Cursor c = db.rawQuery("SELECT x FROM t1", null);
        if (c != null) {
            boolean bRes;
            for (bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()){
                String x = c.getString(0);
                res = res + "." + x;
            }
        } else {
            testWarning("csr_test_1", "c==NULL");
        }
        testResult("csr_test_1.1", res, ".one.two.three");

        db.close();
        testResult("csr_test_1.2", dbIsEncrypted(), "unencrypted");
    }

    private String stringFromT1x(SQLiteDatabase db){
        String res = "";

        Cursor c = db.rawQuery("SELECT x FROM t1", null);
        boolean bRes;
        for (bRes=c.moveToFirst(); bRes; bRes=c.moveToNext()){
            String x = c.getString(0);
            res = res + "." + x;
        }

        return res;
    }

    /*
    ** If this is a SEE build, check that encrypted databases work.
    */
    private void seeTest1() throws Exception {
        if (!SQLiteDatabase.hasCodec()) {
            return;
        }

        SQLiteDatabase.deleteDatabase(DB_PATH);
        String res = "";

        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        db.execSQL("PRAGMA key = 'secretkey'");

        db.execSQL("CREATE TABLE t1(x)");
        db.execSQL("INSERT INTO t1 VALUES ('one'), ('two'), ('three')");

        res = stringFromT1x(db);
        testResult("see_test_1.1", res, ".one.two.three");
        db.close();

        testResult("see_test_1.2", dbIsEncrypted(), "encrypted");

        db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);
        db.execSQL("PRAGMA key = 'secretkey'");
        res = stringFromT1x(db);
        testResult("see_test_1.3", res, ".one.two.three");
        db.close();

        res = "unencrypted";
        try {
            db = SQLiteDatabase.openOrCreateDatabase(DB_PATH.getPath(), null);
            stringFromT1x(db);
        } catch (SQLiteDatabaseCorruptException e){
            res = "encrypted";
        } finally {
            db.close();
        }
        testResult("see_test_1.4", res, "encrypted");

        res = "unencrypted";
        try {
            db = SQLiteDatabase.openOrCreateDatabase(DB_PATH.getPath(), null);
            db.execSQL("PRAGMA key = 'otherkey'");
            stringFromT1x(db);
        } catch (SQLiteDatabaseCorruptException e){
            res = "encrypted";
        } finally {
            db.close();
        }
        testResult("see_test_1.5", res, "encrypted");
    }

    class MyHelper extends SQLiteOpenHelper {
        public MyHelper(Context ctx){
            super(ctx, DB_PATH.getPath(), null, 1);
        }
        public void onConfigure(SQLiteDatabase db){
            db.execSQL("PRAGMA key = 'secret'");
        }
        public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE t1(x)");
        }
        public void onUpgrade(SQLiteDatabase db, int iOld, int iNew){
        }
    }

    /*
    ** If this is a SEE build, check that SQLiteOpenHelper still works.
    */
    private void seeTest2() throws Exception {
        if (!SQLiteDatabase.hasCodec()) {
            return;
        }

        SQLiteDatabase.deleteDatabase(DB_PATH);

        MyHelper helper = new MyHelper(this);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.execSQL("INSERT INTO t1 VALUES ('x'), ('y'), ('z')");

        String res = stringFromT1x(db);
        testResult("see_test_2.1", res, ".x.y.z");
        testResult("see_test_2.2", dbIsEncrypted(), "encrypted");

        helper.close();
        helper = new MyHelper(this);
        db = helper.getReadableDatabase();
        testResult("see_test_2.3", res, ".x.y.z");

        db = helper.getWritableDatabase();
        testResult("see_test_2.4", res, ".x.y.z");

        testResult("see_test_2.5", dbIsEncrypted(), "encrypted");
    }

    private void ftsTest1() throws Exception {
        SQLiteDatabase.deleteDatabase(DB_PATH);
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(DB_PATH, null);

        db.rawQuery("SELECT load_extension('libunicodesn', 'sqlite3_extension_init')", null);
        db.execSQL("CREATE VIRTUAL TABLE v1 USING fts3(name, tokenize=unicodesn)");

        final String[] names = getResources().getStringArray(R.array.dummy_names);

        for (String name : names) {
            db.execSQL("INSERT INTO v1 VALUES ('" + name + "')");
        }

        Cursor c = db.rawQuery("SELECT * FROM v1 WHERE name MATCH ?", new String[]{"Joanna"});

        if (c != null && c.moveToFirst()) {
            testResult("fts_text_1.0", String.valueOf(c.getCount()), "1");
        }

        if (c != null) {
            c.close();
        }

        db.close();
    }

    private void runTheTests() {
        System.loadLibrary("sqliteX");
        DB_PATH = getApplicationContext().getDatabasePath("test.db");
        DB_PATH.mkdirs();

        myTV.setText("");
        myNErr = 0;
        myNTest = 0;

        debugText = new StringBuilder();
        myProgress.setVisibility(View.VISIBLE);

        Task.callInBackground(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                try {
                    reportVersion();
                    csrTest1();
                    csrTest2();
                    threadTest1();
                    threadTest2();
                    seeTest1();
                    seeTest2();
                    ftsTest1();
                    return null;
                } catch(Exception e) {
                    appendString("Exception: " + e.toString() + "\n");
                    appendString(android.util.Log.getStackTraceString(e) + "\n");
                }
                return null;
            }
        }).continueWith(new Continuation<Object, Object>() {
            @Override
            public Object then(Task<Object> objectTask) throws Exception {
                appendString("\n" + myNErr + " errors from " + myNTest + " tests\n");
                myTV.setText(debugText.toString());
                myProgress.setVisibility(View.GONE);
                return null;
            }
        }, ExecutorUtil.getThreadExecutor);
    }

    private void appendString(String data) {
        if (debugText == null) {
            debugText = new StringBuilder();
        }
        debugText.append(data);
    }
}

