package com.householdfinance.workspace;
import java.util.*;
/** Construct actual old schemas, rather than merely relabelling a current archive. */
public final class LegacyArchiveFixture {
  public static Map<String,WorkspaceBackupService.TableData> tables(WorkspaceBackupService.Archive archive,int version){
    var names=version==1?WorkspaceBackupService.V1_TABLES:version==2?WorkspaceBackupService.V2_TABLES:version==3?WorkspaceBackupService.V3_TABLES:WorkspaceBackupService.TABLES;
    Map<String,WorkspaceBackupService.TableData> result=new LinkedHashMap<>();
    for(String table:names){var data=archive.tables().get(table);List<Integer> keep=new ArrayList<>();for(int i=0;i<data.columns().size();i++)if(!Set.of("OWNER_ID","SHARED","ACTOR_ID","SCOPE_REFS").contains(data.columns().get(i)))keep.add(i);
      var columns=keep.stream().map(data.columns()::get).toList();var rows=data.rows().stream().map(row->keep.stream().map(row::get).toList()).toList();result.put(table,new WorkspaceBackupService.TableData(columns,rows));
    }return result;
  }
}
