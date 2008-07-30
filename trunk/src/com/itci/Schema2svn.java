package com.itci;

import com.itci.schema2svn.DDLFetcher;
import com.itci.schema2svn.SVNClient;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class Schema2svn {
    private static final String DATE_PATTERN = "yyyy.MM.dd.HH.mm.ss.SSS";
    public static final SimpleDateFormat SDF = new SimpleDateFormat(DATE_PATTERN);
    public static void main(String[] args) {
        if(args ==null || args.length == 0 || "-h".equalsIgnoreCase(args[0]) || "-help".equalsIgnoreCase(args[0])){
            System.out.println("Usage:\n" +
                    "Schema2svn {database_connection} {base_url} [-d2008.07.06.13.24.46.345] {-sSCHEMALIST}\n" +
                    "If no base_url specified it will use current directory.\n" +
                    "If base_url is specified, then it will create a temp directory, checkout svn from that URL, " +
                    "then use that directory as the location for schema files.");
            return;
        }
        System.out.println(args[0]);
        System.out.println(args[1]);
        final File instanceDirectory = new File(args[1]);
        Date updateAfter = null;
        if(args.length>2){
            System.out.println(args[2]);
            try {
                final String source = args[2].replaceAll("-d", "");
                if(source.length() != 0){
                    updateAfter = SDF.parse(source);
                }
                else{
                    updateAfter = null;
                }
            }
            catch (ParseException e) {
                System.out.println("Incorrect date format!\nUse " +DATE_PATTERN);
                System.exit(1);

            }

        }
        
        List schemaList = new ArrayList();
        if(args.length > 3){
            String schemas = args[3].replaceAll("-s"," ");
            schemaList.addAll(Arrays.asList( schemas.split("\\s*,\\s*") ));
        }

        List objectTypeList = new ArrayList();
        objectTypeList.add("TABLE");
        objectTypeList.add("VIEW");
        objectTypeList.add("PROCEDURE");
        objectTypeList.add("FUNCTION");
        objectTypeList.add("SEQUENCE");
        objectTypeList.add("PACKAGE");
        objectTypeList.add("PACKAGE_BODY");
        final SVNClient svnClient = new SVNClient();
        String message = null;
        if(updateAfter == null){
            try{
                svnClient.updateFolder(instanceDirectory);
                message = svnClient.getLastNonPhantomCommitMessage(instanceDirectory);
                try{
                    updateAfter = SDF.parse(message);
                }
                catch(Exception e){
                    updateAfter = null;
                }
            }
            catch(Exception e){
                System.out.println(e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
        final DDLFetcher fetcher = new DDLFetcher(schemaList, objectTypeList, args[0] , instanceDirectory, updateAfter);
        final DDLFetcher.Result result = fetcher.fetch();
        if (result == null){
            System.exit(1);
            return;
        }
        if(result.getModificationCounter() == 0){
            System.out.println("No changes detected. Exiting");
            System.exit(0);
            return;
        }
        try {
            svnClient.addMissing(instanceDirectory);
        }
        catch (SVNException e) {
            System.out.println("Cannot add files to SVN");
            e.printStackTrace();
            System.exit(1);
            return;
        }
        System.out.println("" + result.getModificationCounter() + " Modification detected. Last DDL Time = " + result.getRevision());
        System.out.println("Commiting...");
        try {
            svnClient.commitDir(instanceDirectory, result.getRevision());
        } catch (SVNException e) {
            System.out.println("Cannot commit to SVN: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
