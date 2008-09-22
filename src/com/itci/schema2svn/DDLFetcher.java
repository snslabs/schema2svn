package com.itci.schema2svn;

import com.itci.Schema2svn;
import oracle.jdbc.OracleDriver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.sql.*;
import java.util.List;
import java.util.ArrayList;

public class DDLFetcher {
    private List schemaList;
    private List objectTypes;
    private String connectionUrl;
    private File destinationDirectory;
    private java.util.Date updatedAfter;
    private Connection connection;
    private boolean isConnectionExternal = false;
    private PreparedStatement objectNamesStatement;
    private PreparedStatement tableMetadataStatement;
    private PreparedStatement ddlStatement;
    private static final String OBJECT_NAMES_SQL = "select OWNER, OBJECT_NAME, OBJECT_TYPE, LAST_DDL_TIME from all_objects " +
            "where owner = ? and object_type = ?";
    private PreparedStatement grantsStatement;

    public DDLFetcher(List schemaList, List objectTypes, String connectionUrl, File destinationDirectory, java.util.Date updatedAfter) {
        this.schemaList = schemaList;
        this.objectTypes = objectTypes;
        this.connectionUrl = connectionUrl;
        this.destinationDirectory = destinationDirectory;
        this.updatedAfter = updatedAfter;
    }

    public DDLFetcher(ArrayList schemaList, ArrayList objectTypes, Connection conn, File destinationDirectory, java.util.Date updatedAfter) {
        this.schemaList = schemaList;
        this.objectTypes = objectTypes;
        this.connection = conn;
        this.destinationDirectory = destinationDirectory;
        this.updatedAfter = updatedAfter;
    }

    public Result fetch() {
        
        if(connection == null){
            isConnectionExternal = false;
            try {
                DriverManager.registerDriver(new OracleDriver());
            }
            catch (SQLException e) {
                System.out.println("Cannot register Oracle Driver");
                e.printStackTrace();
                return null;
            }
            try{
                connection = DriverManager.getConnection(connectionUrl);
            }
            catch(SQLException e){
                System.out.println("Cannot connect to database");
                e.printStackTrace();
                return null;
            }
        }
        else{
            isConnectionExternal = true;
        }
        
        long maxTimestamp = updatedAfter == null ? 0 : updatedAfter.getTime();
        try {

            initStatements();
            System.out.println("Checking modifications since " + (updatedAfter != null ? updatedAfter.toString() : "0"));
            System.out.println("Schemas: " + schemaList);
            int modificationCounter = 0;

            for (int i = 0; i < schemaList.size(); i++) {
                String schemaName = ((String) schemaList.get(i)).trim();
                File schemaDirectory = new File(destinationDirectory, schemaName.toUpperCase());
                if (!schemaDirectory.exists()) {
                    schemaDirectory.mkdirs();
                }

                for (int j = 0; j < objectTypes.size(); j++) {
                    String objectType = (String) objectTypes.get(j);
                    File typeDirectory = new File(schemaDirectory, objectType);
                    if (!typeDirectory.exists()) {
                        typeDirectory.mkdirs();
                    }

                    objectNamesStatement.setString(1, schemaName);
                    objectNamesStatement.setString(2, objectType.replaceAll("_", " "));
                    if (updatedAfter != null) {
                        objectNamesStatement.setTimestamp(3, new Timestamp(updatedAfter.getTime()));
                    }
                    try {
                        final ResultSet rs = objectNamesStatement.executeQuery();
                        while (rs.next()) {
                            System.out.println(rs.getString(1) + " : " + rs.getString(2) + " : " + rs.getString(3) + ":" + rs.getTimestamp(4));
                            getAndStoreDDL(typeDirectory, rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4));
                            if (rs.getTimestamp(4).getTime() > maxTimestamp) {
                                maxTimestamp = rs.getTimestamp(4).getTime();
                            }
                            modificationCounter++;
                        }
                        rs.close();
                    }
                    catch (SQLException e) {
                        System.out.println("ERROR " + e.getMessage());
                    }

                }

            }
            System.out.println("Detected " + modificationCounter + " change(s)");
            final String revision = Schema2svn.SDF.format(new Date(maxTimestamp));
            System.out.println("Revision timestamp (max(last_ddl_time)) = " + revision);

            objectNamesStatement.close();

            return new Result(revision, modificationCounter);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        finally {
            if (!isConnectionExternal && connection != null) {
                try {
                    connection.close();
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void initStatements() throws SQLException {
        if (updatedAfter == null) {
            objectNamesStatement = connection.prepareStatement(
                    OBJECT_NAMES_SQL);
        } else {
            objectNamesStatement = connection.prepareStatement(
                    OBJECT_NAMES_SQL + " and LAST_DDL_TIME > ?");
        }
        ddlStatement = connection.prepareStatement(
                "select " +
                        "dbms_metadata.get_ddl(?,?,?) " +
                        "from dual"
        );
        grantsStatement = connection.prepareStatement(
                "select " +
                        "dbms_metadata.get_dependent_ddl('OBJECT_GRANT',?,?) " +
                        "from dual"
        );
    }

    char[] buff = new char[1024];

    private void getAndStoreDDL(File typeDirectory, String schemaName, String objectName, String objectType, Timestamp timestamp) throws SQLException, IOException {
        ddlStatement.setString(1, objectType.replaceAll("\\s", "_"));
        ddlStatement.setString(2, objectName);
        ddlStatement.setString(3, schemaName);
        grantsStatement.setString(1, objectName);
        grantsStatement.setString(2, schemaName);

        final File file = new File(typeDirectory, objectName.toUpperCase() + ".sql");
        final FileWriter fw = new FileWriter(file);
        ResultSet rs = null;
        try {
            rs = ddlStatement.executeQuery();
            fw.write("-- LAST DDL : " + Schema2svn.SDF.format(timestamp) + "\n");
            if (rs.next()) {
                saveClobToWriter(rs.getClob(1), fw, objectType.equals("TABLE") ? ScriptProcessor.TABLE : null);
                fw.write("\n/");
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
        finally {
            if (rs != null) {
                rs.close();
            }
        }

        fw.write("\n\n-- GRANTS\n");
        try {
            rs = grantsStatement.executeQuery();
            if (rs.next()) {
                saveClobToWriter(rs.getClob(1), fw, ScriptProcessor.GRANTS);
            }
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
        finally {
            if (rs != null) {
                rs.close();
            }
        }

        fw.flush();
        fw.close();


    }

    private void saveClobToWriter(Clob clob, FileWriter fw) throws IOException, SQLException {
        this.saveClobToWriter(clob, fw, null);
    }

    private void saveClobToWriter(Clob clob, FileWriter fw, ScriptProcessor scriptProcessor) throws IOException, SQLException {
        try {
            final Reader characterStream = clob.getCharacterStream();
            char[] buff = new char[1024];
            int cnt;
            if (scriptProcessor == null) {
                while ((cnt = characterStream.read(buff)) != -1) {
                    fw.write(buff, 0, cnt);
                }
            } else {
                StringBuffer sb = new StringBuffer();
                while ((cnt = characterStream.read(buff)) != -1) {
                    sb.append(new String(buff, 0, cnt));
                }
                fw.write(scriptProcessor.processScript(sb.toString()));

            }
        }
        catch (Throwable e) {
            System.out.println("Nothing in clob");
        }
    }

    public static class Result {
        private String revision;
        private int modificationCounter;

        private Result(String revision, int modificationCounter) {
            this.revision = revision;
            this.modificationCounter = modificationCounter;
        }

        public String getRevision() {
            return revision;
        }

        public int getModificationCounter() {
            return modificationCounter;
        }
    }
}
