package org.sqlite.database;

import java.util.Locale;

/**
 * Created by jeremiahstephenson on 1/20/15.
 */
public class DatabaseUtils extends android.database.DatabaseUtils {

    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_SELECT = 1;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_UPDATE = 2;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_ATTACH = 3;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_BEGIN = 4;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_COMMIT = 5;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_ABORT = 6;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_PRAGMA = 7;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_DDL = 8;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_UNPREPARED = 9;
    /** One of the values returned by {@link #getSqlStatementType(String)}. */
    public static final int STATEMENT_OTHER = 99;

    /**
     * Returns one of the following which represent the type of the given SQL statement.
     * <ol>
     *   <li>{@link #STATEMENT_SELECT}</li>
     *   <li>{@link #STATEMENT_UPDATE}</li>
     *   <li>{@link #STATEMENT_ATTACH}</li>
     *   <li>{@link #STATEMENT_BEGIN}</li>
     *   <li>{@link #STATEMENT_COMMIT}</li>
     *   <li>{@link #STATEMENT_ABORT}</li>
     *   <li>{@link #STATEMENT_OTHER}</li>
     * </ol>
     * @param sql the SQL statement whose type is returned by this method
     * @return one of the values listed above
     */
    public static int getSqlStatementType(String sql) {
        sql = sql.trim();
        if (sql.length() < 3) {
            return STATEMENT_OTHER;
        }
        String prefixSql = sql.substring(0, 3).toUpperCase(Locale.ROOT);
        if (prefixSql.equals("SEL")) {
            return STATEMENT_SELECT;
        } else if (prefixSql.equals("INS") ||
                prefixSql.equals("UPD") ||
                prefixSql.equals("REP") ||
                prefixSql.equals("DEL")) {
            return STATEMENT_UPDATE;
        } else if (prefixSql.equals("ATT")) {
            return STATEMENT_ATTACH;
        } else if (prefixSql.equals("COM")) {
            return STATEMENT_COMMIT;
        } else if (prefixSql.equals("END")) {
            return STATEMENT_COMMIT;
        } else if (prefixSql.equals("ROL")) {
            return STATEMENT_ABORT;
        } else if (prefixSql.equals("BEG")) {
            return STATEMENT_BEGIN;
        } else if (prefixSql.equals("PRA")) {
            return STATEMENT_PRAGMA;
        } else if (prefixSql.equals("CRE") || prefixSql.equals("DRO") ||
                prefixSql.equals("ALT")) {
            return STATEMENT_DDL;
        } else if (prefixSql.equals("ANA") || prefixSql.equals("DET")) {
            return STATEMENT_UNPREPARED;
        }
        return STATEMENT_OTHER;
    }

}