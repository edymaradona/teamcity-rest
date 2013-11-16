package jetbrains.buildServer.server.rest.data;

import java.util.Date;
import jetbrains.BuildServerCreator;
import jetbrains.buildServer.responsibility.BuildTypeResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntry;
import jetbrains.buildServer.responsibility.ResponsibilityEntryEx;
import jetbrains.buildServer.server.rest.ApiUrlBuilder;
import jetbrains.buildServer.server.rest.PathTransformer;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationFinder;
import jetbrains.buildServer.server.rest.data.investigations.InvestigationWrapper;
import jetbrains.buildServer.server.rest.data.investigations.ResponsibilityEntryBridge;
import jetbrains.buildServer.server.rest.model.buildType.Investigation;
import jetbrains.buildServer.server.rest.model.buildType.Investigations;
import jetbrains.buildServer.serverSide.BuildTypeEx;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.ProjectEx;
import jetbrains.buildServer.serverSide.impl.problems.BuildProblemInfoImpl;
import jetbrains.buildServer.serverSide.impl.projects.ProjectManagerImpl;
import jetbrains.buildServer.tests.TestName;
import jetbrains.buildServer.users.SUser;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Yegor.Yarko
 *         Date: 11.11.13
 */
@Test
public class InvestigationFinderTest extends BaseServerTestCase {
  public static final String FAIL_TEST2_NAME = "fail.test2";
  public static final String PROBLEM_IDENTITY = "myUniqueProblem";
  private InvestigationFinder myInvestigationFinder;
  private ProjectManagerImpl myProjectManager;
  private BuildTypeEx myBuildType;
  private SUser myUser;

  @Override
  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myProjectManager = myFixture.getProjectManager();
    final ProjectFinder projectFinder = new ProjectFinder(myProjectManager);
    final UserFinder userFinder = new UserFinder(myFixture);
    final ResponsibilityEntryBridge responsibilityEntryBridge = new ResponsibilityEntryBridge(myFixture.getResponsibilityFacadeEx(), myFixture.getResponsibilityFacadeEx(),
                                                                                              myFixture.getResponsibilityFacadeEx());
    myInvestigationFinder = new InvestigationFinder(responsibilityEntryBridge, projectFinder, null, userFinder);
  }

  @Test
  public void testBuildTypeInvestigation() throws Exception {
    createFailingBuild();
    myFixture.getResponsibilityFacadeEx().setBuildTypeResponsibility(myBuildType, createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));


    final PagedSearchResult<InvestigationWrapper> result = myInvestigationFinder.getItems((String)null);
    assertEquals(1, result.myEntries.size());
    final InvestigationWrapper investigation1 = result.myEntries.get(0);
    assertEquals(true, investigation1.isBuildType());
    assertEquals(false, investigation1.isProblem());
    assertEquals(false, investigation1.isTest());
    assertEquals("BuildType", investigation1.getType());

    final BuildTypeResponsibilityEntry buildTypeRE = investigation1.getBuildTypeRE();
    assertEquals(true, buildTypeRE != null);

    assertEquals(myUser, investigation1.getResponsibleUser());
    assertEquals(ResponsibilityEntry.State.TAKEN, investigation1.getState());
  }

  @Test
  public void testBuildTypeInvestigationModel() throws Exception {
    createFailingBuild();
    myFixture.getResponsibilityFacadeEx().setBuildTypeResponsibility(myBuildType, createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));

    final PagedSearchResult<InvestigationWrapper> ivestigationWrappers = myInvestigationFinder.getItems((String)null);
    ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });

    final Investigations investigations = new Investigations(ivestigationWrappers.myEntries, null, myServer, apiUrlBuilder);

    assertEquals(1, investigations.count);
    final Investigation investigation = investigations.items.get(0);
    assertEquals(myBuildType.getId(), investigation.id);
    assertEquals("TAKEN", investigation.state);
    assertEquals((Long)myUser.getId(), investigation.responsible.getId());
    assertEquals("The comment", investigation.assignment.text);
    assertEquals("BuildType", investigation.scope.type);
    assertEquals(myBuildType.getExternalId(), investigation.scope.buildType.id);
    assertEquals(null, investigation.scope.project);
  }

  @Test(enabled = false)
  public void testTestInvestigationModel() throws Exception {
    createFailingBuild();

    myFixture.getResponsibilityFacadeEx().setTestNameResponsibility(new TestName(FAIL_TEST2_NAME), myProject.getProjectId(),
                                                                    createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));

    final PagedSearchResult<InvestigationWrapper> ivestigationWrappers = myInvestigationFinder.getItems((String)null);
    ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });

    final Investigations investigations = new Investigations(ivestigationWrappers.myEntries, null, myServer, apiUrlBuilder);

    assertEquals(1, investigations.count);
    final Investigation investigation = investigations.items.get(0);
    assertEquals(null, investigation.id);
    assertEquals("TAKEN", investigation.state);
    assertEquals((Long)myUser.getId(), investigation.responsible.getId());
    assertEquals("The comment", investigation.assignment.text);
    assertEquals("Test", investigation.scope.type);

    assertEquals(FAIL_TEST2_NAME, investigation.scope.test.name);
    assertEquals(myProject.getExternalId(), investigation.scope.project.id);
  }

  @Test(enabled = false)
  public void testProblemInvestigationModel() throws Exception {
    createFailingBuild();

    myFixture.getResponsibilityFacadeEx().setBuildProblemResponsibility(new BuildProblemInfoImpl(myProject.getProjectId(), getProblemId(PROBLEM_IDENTITY), null), myProject.getProjectId(),
                                                                        createRespEntry(ResponsibilityEntry.State.TAKEN, myUser));

    final PagedSearchResult<InvestigationWrapper> ivestigationWrappers = myInvestigationFinder.getItems((String)null);
    ApiUrlBuilder apiUrlBuilder = new ApiUrlBuilder(new PathTransformer() {
      public String transform(final String path) {
        return path;
      }
    });

    final Investigations investigations = new Investigations(ivestigationWrappers.myEntries, null, myServer, apiUrlBuilder);

    assertEquals(1, investigations.count);
    final Investigation investigation = investigations.items.get(0);
    assertEquals(null, investigation.id);
    assertEquals("TAKEN", investigation.state);
    assertEquals((Long)myUser.getId(), investigation.responsible.getId());
    assertEquals("The comment", investigation.assignment.text);
    assertEquals("Problem", investigation.scope.type);

    assertEquals(PROBLEM_IDENTITY, investigation.scope.problem.identity);
    assertEquals(myProject.getExternalId(), investigation.scope.project.id);
  }

  @Override
  protected ResponsibilityEntryEx createRespEntry(ResponsibilityEntry.State state, SUser user) {
    return new ResponsibilityEntryEx(state, user, user, new Date(), "The comment", ResponsibilityEntry.RemoveMethod.WHEN_FIXED);
  }

  private void createFailingBuild() {
    final ProjectEx rootProject = myProjectManager.getRootProject();
    final ProjectEx project1 = rootProject.createProject("project1", "Project name");
    myBuildType = project1.createBuildType("extId", "bt name");

    startBuild(myBuildType);
    runTestsInRunningBuild(new String[]{"pass.test1"}, new String[]{FAIL_TEST2_NAME}, new String[0]);
    BuildServerCreator.doBuildProblem(getRunningBuild(), PROBLEM_IDENTITY);
    final SFinishedBuild build =  finishBuild(true);

    myUser = createUser("user");
  }
}
