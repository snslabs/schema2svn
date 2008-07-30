package com.itci.schema2svn;

import com.itci.Schema2svn;
import oracle.jdbc.OracleDriver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

public class DDLFetcher {
    private List schemaList;
    private List objectTypes;
    private String connectionUrl;
    private File destinationDirectory;
    private java.util.Date updatedAfter;
    private Connection connection;
    private PreparedStatement objectNamesStatement;
    private PreparedStatement tableMetadataStatement;
    private PreparedStatement metadataStatement;
    private static final String OBJECT_NAMES_SQL = "select OWNER, OBJECT_NAME, OBJECT_TYPE, LAST_DDL_TIME from all_objects " +
            "where owner = ? and object_type = ?";

    public DDLFetcher(List schemaList, List objectTypes, String connectionUrl, File destinationDirectory, java.util.Date updatedAfter) {
        this.schemaList = schemaList;
        this.objectTypes = objectTypes;
        this.connectionUrl = connectionUrl;
        this.destinationDirectory = destinationDirectory;
        this.updatedAfter = updatedAfter;
    }

    public Result fetch() {
        try {
            DriverManager.registerDriver(new OracleDriver());
        }
        catch (SQLException e) {
            System.out.println("Cannot register Oracle Driver");
            e.printStackTrace();
            return null;
        }
        long maxTimestamp = updatedAfter==null?0:updatedAfter.getTime();
        try {
            connection = DriverManager.getConnection(connectionUrl);

            initStatements();
            System.out.println("Checking modifications since " + (updatedAfter!=null?updatedAfter.toString():"0"));
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
                    if(updatedAfter!= null){
                        objectNamesStatement.setTimestamp(3, new Timestamp(updatedAfter.getTime()));
                    }
                    try{
                        final ResultSet rs = objectNamesStatement.executeQuery();
                        while (rs.next()) {
                            System.out.println(rs.getString(1) + " : " + rs.getString(2) + " : " + rs.getString(3)+":"+rs.getTimestamp(4));
                            getAndStoreDDL(typeDirectory, rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4));
                            if(rs.getTimestamp(4).getTime()>maxTimestamp){
                                maxTimestamp = rs.getTimestamp(4).getTime();
                            }
                            modificationCounter++;
                        }
                        rs.close();
                    }
                    catch(SQLException e){
                        System.out.println("ERROR " + e.getMessage());
                    }

                }

            }
            System.out.println("Detected " + modificationCounter +" change(s)");
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
            if (connection != null) {
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
        }
        else {
            objectNamesStatement = connection.prepareStatement(
                    OBJECT_NAMES_SQL + " and LAST_DDL_TIME > ?");
        }
        metadataStatement = connection.prepareStatement(
                "select " +
                        "dbms_metadata.get_ddl(?,?,?), dbms_metadata.get_dependent_ddl('OBJECT_GRANT',?,?) " +
                        "from dual"
        );
    }

    char[] buff = new char[1024];

    private void getAndStoreDDL(File typeDirectory, String schemaName, String objectName, String objectType, Timestamp timestamp) throws SQLException, IOException {
        metadataStatement.setString(1, objectType.replaceAll("\\s", "_"));
        metadataStatement.setString(2, objectName);
        metadataStatement.setString(3, schemaName);
        metadataStatement.setString(4, objectName);
        metadataStatement.setString(5, schemaName);

        final ResultSet rs = metadataStatement.executeQuery();
        if (rs.next()) {
            final File file = new File(typeDirectory, objectName.toUpperCase() + ".sql");
            final FileWriter fw = new FileWriter(file);
            fw.write("-- LAST DDL : " + Schema2svn.SDF.format(timestamp) +"\n");
            saveClobToWriter(rs.getClob(1), fw, objectType.equals("TABLE") ? ScriptProcessor.TABLE : null);
            fw.write("\n/");
            fw.write("\n\n-- GRANTS\n");
            saveClobToWriter(rs.getClob(2), fw, ScriptProcessor.GRANTS);
            fw.flush();
            fw.close();
        }
        rs.close();

    }

    private void saveClobToWriter(Clob clob, FileWriter fw) throws IOException, SQLException {
        this.saveClobToWriter(clob, fw, null);
    }

    private void saveClobToWriter(Clob clob, FileWriter fw, ScriptProcessor scriptProcessor) throws IOException, SQLException {
        final Reader characterStream = clob.getCharacterStream();
        char[] buff = new char[1024];
        int cnt;
        if (scriptProcessor == null) {
            while ((cnt = characterStream.read(buff)) != -1) {
                fw.write(buff, 0, cnt);
            }
        }
        else {
            StringBuffer sb = new StringBuffer();
            while ((cnt = characterStream.read(buff)) != -1) {
                sb.append(new String(buff, 0, cnt));
            }
            fw.write(scriptProcessor.processScript(sb.toString()));

        }
    }
    public static class Result{
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
