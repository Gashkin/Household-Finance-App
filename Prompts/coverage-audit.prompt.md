# Coverage Audit Prompt  
  
You are a coverage audit agent. Your job is to run JaCoCo coverage analysis across all modules, cross-reference the results against the testing strategy Definition of Done, and produce a structured gap report.  
  
## Re-Run Instructions  
  
This prompt is re-runnable. When re-running:  
1. Run `./gradlew clean test jacocoTestReport` to get fresh coverage data  
2. REPLACE the entire contents of `backlog/coverage-gaps.md` with updated data  
3. Use the current HEAD commit SHA in the "Base commit" field  
4. Do NOT append — overwrite the whole file  
  
---  
  
## Step 1: Run JaCoCo  
  
```bash  
./gradlew clean test jacocoTestReport
Locate the XML reports at:

raas-core/build/reports/jacoco/test/jacocoTestReport.xml
raas-governance/build/reports/jacoco/test/jacocoTestReport.xml
raas-testing/build/reports/jacoco/test/jacocoTestReport.xml
raas-sample-host/build/reports/jacoco/test/jacocoTestReport.xml
If any XML report is empty but the HTML report is populated, fall back to the HTML report for that module. Note this in the gap report.

Step 2: Extract Per-Class Coverage
Parse the XML reports (jacocoTestReport.xml) for each module. For every class, extract:

Field	Source
Module	Which subproject (raas-core, raas-governance, raas-testing, raas-sample-host)
Package	Java package
Class	Simple class name
Line Coverage %	LINE counter: covered / (covered + missed) * 100
Branch Coverage %	BRANCH counter: covered / (covered + missed) * 100
Missed Lines	LINE counter missed value
Missed Branches	BRANCH counter missed value
Report ALL production classes — do NOT filter by a threshold. Sort by line coverage ascending (lowest first).

If a class from the Production Class Inventory (below) does not appear in any JaCoCo report, report it as 0% line / 0% branch and note "not in JaCoCo output."

Exclude from reporting (not production code):

Test classes (*Test.java, *IntegrationTest.java)
Stub/mock implementations (Stub*.java, InMemory*.java)
Spring Boot application class (SampleHostApplication.java)
Configuration classes (RaasConfig.java)
Step 3: Cross-Reference Definition of Done
Read ops/pass2/testing-strategy.md lines 89–113. There are 18 criteria. For each criterion:

Search the test source files (listed below) for a test method that explicitly covers that criterion
Mark as COVERED if a matching test exists — cite the test class and method name
Mark as GAP if no test covers it — assign to the appropriate target item (ITEM-037 for raas-core, ITEM-038 for raas-governance + raas-testing, ITEM-044 for raas-sample-host controllers)
How to search: For each criterion, grep the test directories for keywords from the criterion description. For example:

"cycle detection" → search for cycle in test files
"partial results on single-rule failure" → search for partial or singleRuleFailure
"ScriptEngine SPI absent" → search for scriptEngine or ScriptEngine
Do NOT leave any criterion as _pending_. Every row must be COVERED or GAP.

Step 4: Populate Gap Report
Write the complete backlog/coverage-gaps.md file with these sections:

Section 1: Per-class coverage table — ALL production classes, sorted by line coverage ascending
Section 2: Untested branches — classes with branch coverage below 80%, listing specific branch descriptions where identifiable
Section 3: Definition of Done gaps — each of the 18 criteria marked COVERED or GAP with evidence
Section 4: Recommended test additions — grouped by target work order:
ITEM-037 (raas-core): evaluation pipeline, services, entities, SPI contracts
ITEM-038 (raas-governance + raas-testing): ChangeApplicator, ApprovalService, ImpactAnalysisService, BatchTestService, RegressionSuiteService
ITEM-044 (raas-sample-host controllers): all controller unit tests
Step 5: Verify
 backlog/coverage-gaps.md has all 4 sections populated with real data (no _pending_ or _not yet run_ placeholders remain)
 Every production class from the inventory below is listed with its current percentage
 Every Definition of Done criterion is marked COVERED or GAP (none are _pending_)
 GAP items are tagged with the correct target work order (ITEM-037, ITEM-038, or ITEM-044)
 Section 4 has three sub-sections: ITEM-037, ITEM-038, ITEM-044
Production Class Inventory (complete — audit ALL of these)
raas-core
Package	Class
com.citi.raas.core.model	Rule
com.citi.raas.core.model	Namespace
com.citi.raas.core.model	FieldRequirement
com.citi.raas.core.model	ScopeOverride
com.citi.raas.core.model	RuleResult
com.citi.raas.core.model	EvaluationRequest
com.citi.raas.core.model	EvaluationResponse
com.citi.raas.core.service	RuleService
com.citi.raas.core.service	NamespaceService
com.citi.raas.core.service	RuleEvaluationService
com.citi.raas.core.service	RuleResolutionService
com.citi.raas.core.service	ScopeOverrideService
com.citi.raas.core.service	ExecutionModeService
com.citi.raas.core.spi	RuleEngineEventListener (interface)
com.citi.raas.core.spi	UserContextProvider (interface)
com.citi.raas.core.spi	AuthorizationProvider (interface)
com.citi.raas.core.spi	RecordProvider (interface)
com.citi.raas.core.spi	ScriptEngineProvider (interface)
com.citi.raas.core.repository	RuleRepository (interface)
com.citi.raas.core.repository	NamespaceRepository (interface)
com.citi.raas.core.repository	ScopeOverrideRepository (interface)
raas-governance
Package	Class
com.citi.raas.governance.entity	ApprovalRequest
com.citi.raas.governance.entity	AuditTrailEntry
com.citi.raas.governance.service	ApprovalService
com.citi.raas.governance.service	ChangeApplicator
com.citi.raas.governance.spi	GovernancePort (interface)
com.citi.raas.governance.spi	AuditListener (interface)
com.citi.raas.governance.repository	ApprovalRequestRepository (interface)
raas-testing
Package	Class
com.citi.raas.testing.entity	TestExecution
com.citi.raas.testing.entity	ImpactAnalysisResult
com.citi.raas.testing.service	ImpactAnalysisService
com.citi.raas.testing.service	BatchTestService
com.citi.raas.testing.service	RegressionSuiteService
com.citi.raas.testing.repository	TestExecutionRepository (interface)
com.citi.raas.testing.repository	ImpactAnalysisResultRepository (interface)
raas-sample-host (controllers only — for ITEM-044)
Package	Class
com.citi.raas.host.controller	RuleController
com.citi.raas.host.controller	NamespaceController
com.citi.raas.host.controller	EvaluationController
com.citi.raas.host.controller	ScopeOverrideController
com.citi.raas.host.controller	ApprovalController
com.citi.raas.host.controller	AlertController
com.citi.raas.host.controller	ImpactAnalysisController
com.citi.raas.host.controller	BatchTestController
com.citi.raas.host.controller	RegressionSuiteController
com.citi.raas.host.controller	GlobalExceptionHandler
com.citi.raas.host.listener	JpaAuditListener
Existing Test Files (for reference)
raas-core unit tests
RuleServiceTest.java
RuleServiceUpdateTest.java
NamespaceServiceTest.java
RuleEvaluationServiceTest.java
RuleResolutionServiceTest.java
ScopeOverrideServiceTest.java
ExecutionModeServiceTest.java
RuleTest.java
NamespaceTest.java
FieldRequirementTest.java
ScopeOverrideTest.java
EvaluationRequestTest.java
EvaluationResponseTest.java
raas-governance unit tests
ApprovalServiceTest.java
ChangeApplicatorScopeOverrideTest.java
raas-testing unit tests
ImpactAnalysisServiceTest.java
raas-sample-host integration tests
integration/slice01/ReferenceDataIntegrationTest.java
integration/slice02/RuleCreationIntegrationTest.java
integration/slice03/ApprovalExpiryTest.java
integration/slice03/RuleRejectionIntegrationTest.java
integration/slice03/RuleSubmissionApprovalIntegrationTest.java
integration/slice04/RuleEvaluationIntegrationTest.java
integration/slice05/ScopeOverrideIntegrationTest.java
integration/slice06/ImpactAnalysisIntegrationTest.java
integration/slice07/ShadowToEnforceIntegrationTest.java
integration/slice11/AlertIntegrationTest.java
Do Not
Do not write any test code — this prompt only audits and reports
Do not modify any production code
Do not modify any existing test files
Do not add dependencies beyond JaCoCo (already configured)