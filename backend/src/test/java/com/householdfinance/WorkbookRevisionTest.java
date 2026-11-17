package com.householdfinance;

import com.householdfinance.entity.AccountEntity;
import com.householdfinance.repository.*;
import com.householdfinance.service.WorkbookIngestionService;
import com.householdfinance.service.WorkbookReviewBoundaryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:workbook-revision;DB_CLOSE_DELAY=-1")
@org.junit.jupiter.api.extension.ExtendWith(com.householdfinance.access.OwnerFixture.class)
class WorkbookRevisionTest {
    @Autowired WorkbookIngestionService service;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired MonthlyCommitmentRepository commitments;
    @Autowired TransactionRepository transactions;

    @BeforeEach void resetPlanner() {
        transactions.deleteAll();commitments.deleteAll();categories.deleteAll();accounts.deleteAll();
        accounts.save(new AccountEntity("synthetic-cash","Synthetic cash","Bank",100));
    }

    @Test void replaceValidationCannotEraseAnEditMadeAfterReview() throws Exception {
        var validation=service.validateWorkbookLoad(WorkbookReviewBoundaryTest.workbook(),"REPLACE");
        accounts.save(new AccountEntity("synthetic-cash","Synthetic cash","Bank",50));
        assertThrows(IllegalArgumentException.class,()->service.executeValidatedLoad(request(validation)));
        assertEquals(50,accounts.findById("synthetic-cash").orElseThrow().getBalance());
        assertEquals(0,commitments.count());
    }

    @Test void rollbackCannotOverwriteLaterPlannerEdits() throws Exception {
        var validation=service.validateWorkbookLoad(WorkbookReviewBoundaryTest.workbook(),"MERGE");
        var load=service.executeValidatedLoad(request(validation));
        accounts.save(new AccountEntity("synthetic-cash","Synthetic cash","Bank",50));
        assertThrows(IllegalArgumentException.class,()->service.rollbackLoad(load.executionId()));
        assertEquals(50,accounts.findById("synthetic-cash").orElseThrow().getBalance());
        assertEquals(1,commitments.count());
        assertEquals("APPLIED",service.getLoadSummary(load.executionId()).status());
    }

    @Test void unchangedPlannerCanRollBackOnlyOnce() throws Exception {
        var validation=service.validateWorkbookLoad(WorkbookReviewBoundaryTest.workbook(),"MERGE");
        var load=service.executeValidatedLoad(request(validation));
        assertEquals("ROLLED_BACK",service.rollbackLoad(load.executionId()).status());
        assertEquals(100,accounts.findById("synthetic-cash").orElseThrow().getBalance());
        assertEquals(0,commitments.count());
        assertThrows(IllegalArgumentException.class,()->service.rollbackLoad(load.executionId()));
    }

    private WorkbookIngestionService.WorkbookLoadExecuteRequest request(WorkbookIngestionService.WorkbookLoadValidationResult validation) {
        return new WorkbookIngestionService.WorkbookLoadExecuteRequest(validation.validationId(),validation.confirmationToken(),validation.mode());
    }
}
