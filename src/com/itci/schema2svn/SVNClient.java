package com.itci.schema2svn;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class SVNClient {
    private SVNClientManager svnClientManager;

    public SVNClient() {
        FSRepositoryFactory.setup();

        svnClientManager = SVNClientManager.newInstance();
    }

    public void updateFolder(File dir) throws SVNException{
        svnClientManager.getUpdateClient().doUpdate(dir, SVNRevision.HEAD, true);
    }

    public void addMissing(File dir) throws SVNException {
        svnClientManager.getWCClient().doAdd(dir, true, false, false, true);
    }

    public void commitDir(File dir, String commitMessage) throws SVNException {
        svnClientManager.getCommitClient().doCommit(new File[]{dir}, false, commitMessage, false, true);
    }

    public String getLastNonPhantomCommitMessage(File dir) throws Exception {
        final List result = new ArrayList();
        svnClientManager.getLogClient().doLog(
                new File[]{dir},
                SVNRevision.UNDEFINED,
                SVNRevision.UNDEFINED,
                false, false,
                1l, new ISVNLogEntryHandler(){
            public void handleLogEntry(SVNLogEntry logEntry) throws SVNException {
                result.add(logEntry.getMessage());
            }
        });
        if(result.size() != 1){
            throw new Exception("Incorrect result returned by svn log --limit 1");
        }
        return (String)result.get(0);
    }


    public static void main(String[] args) throws Exception {
        final SVNClient client = new SVNClient();
        System.out.println("Last non-phantom commit message: " +
                client.getLastNonPhantomCommitMessage(
//                        new File("C:\\SVNGoogle\\schema2svn\\")
                        new File("C:/SVNRoot/bunge/local-database/autoQA")
                )
        );
    }
}
