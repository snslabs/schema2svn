package com.itci;

import com.itci.schema2svn.DDLFetcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;

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
        Date updateAfter = null;
        if(args.length>2){
            System.out.println(args[2]);
            try {
                updateAfter = SDF.parse(args[2].replaceAll("-d",""));
            }
            catch (ParseException e) {
                System.out.println("Incorrect date format!\nUse " +DATE_PATTERN);
                try {
                    updateAfter = SDF.parse("2001.01.01.00.00.00.000");
                } catch (ParseException e1) {
                    e1.printStackTrace();
                    return;
                }
//                return;
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
        final DDLFetcher fetcher = new DDLFetcher(schemaList, objectTypeList, args[0] , new File(args[1]), updateAfter);
        final DDLFetcher.Result result = fetcher.fetch();
        if (result == null){
            System.exit(1);
        }
    }
}
