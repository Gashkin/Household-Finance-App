package com.householdfinance.workspace;
import java.util.*;
/** Construct actual old schemas, rather than merely relabelling a current archive. */
public final class LegacyArchiveFixture {
  public static Map<String,WorkspaceBackupService.TableData> tables(WorkspaceBackupService.Archive archive,int version){
    var names=version==1?WorkspaceBackupService.V1_TABLES:version==2?WorkspaceBackupService.V2_TABLES:version==3?WorkspaceBackupService.V3_TABLES:version==4?WorkspaceBackupService.TABLES:version==5?WorkspaceBackupService.V5_TABLES:version==6?WorkspaceBackupService.V6_TABLES:version==7?WorkspaceBackupService.V7_TABLES:WorkspaceBackupService.ALL_TABLES;
    Map<String,WorkspaceBackupService.TableData> result=new LinkedHashMap<>();
    for(String table:names){var data=archive.tables().get(table);List<Integer> keep=new ArrayList<>();for(int i=0;i<data.columns().size();i++){String column=data.columns().get(i);if(version<5&&Set.of("OWNER_ID","SHARED","ACTOR_ID","SCOPE_REFS").contains(column))continue;if(version<9&&table.equals("LEDGER_ENTRIES")&&column.equals("REVIEWED_MERCHANT"))continue;if(version<7&&table.equals("LEDGER_ENTRIES")&&Set.of("CATEGORY_ID","REVIEW_STATE","LINKED_ENTRY_ID","LINK_TYPE").contains(column))continue;keep.add(i);}
      var columns=keep.stream().map(data.columns()::get).toList();var rows=data.rows().stream().map(row->keep.stream().map(row::get).toList()).toList();result.put(table,new WorkspaceBackupService.TableData(columns,rows));
    }return result;
  }
}
