package de.tum.in.www1.artemis.service.connectors.bamboo;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.atlassian.bamboo.specs.api.builders.AtlassianModule;
import com.atlassian.bamboo.specs.api.builders.BambooKey;
import com.atlassian.bamboo.specs.api.builders.credentials.SharedCredentialsIdentifier;
import com.atlassian.bamboo.specs.api.builders.credentials.SharedCredentialsScope;
import com.atlassian.bamboo.specs.api.builders.docker.DockerConfiguration;
import com.atlassian.bamboo.specs.api.builders.notification.AnyNotificationRecipient;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.permission.PermissionType;
import com.atlassian.bamboo.specs.api.builders.permission.Permissions;
import com.atlassian.bamboo.specs.api.builders.permission.PlanPermissions;
import com.atlassian.bamboo.specs.api.builders.plan.*;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.plan.branches.BranchCleanup;
import com.atlassian.bamboo.specs.api.builders.plan.branches.PlanBranchManagement;
import com.atlassian.bamboo.specs.api.builders.plan.configuration.ConcurrentBuilds;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.repository.VcsChangeDetection;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepository;
import com.atlassian.bamboo.specs.api.builders.repository.VcsRepositoryIdentifier;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.api.builders.task.Task;
import com.atlassian.bamboo.specs.builders.notification.PlanCompletedNotification;
import com.atlassian.bamboo.specs.builders.repository.git.GitRepository;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.model.task.ScriptTaskProperties;
import com.atlassian.bamboo.specs.model.task.TestParserTaskProperties;
import com.atlassian.bamboo.specs.util.BambooServer;

import de.tum.in.www1.artemis.config.ProgrammingLanguageConfiguration;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationBuildPlanException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusRepository;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService.RepositoryCheckoutPath;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;

@Service
@Profile("bamboo")
public class BambooBuildPlanService {

    private static final Logger log = LoggerFactory.getLogger(BambooBuildPlanService.class);

    @Value("${artemis.continuous-integration.user}")
    private String bambooUser;

    @Value("${artemis.user-management.external.admin-group-name}")
    private String adminGroupName;

    @Value("${server.url}")
    private URL artemisServerUrl;

    private final BambooInternalUrlService bambooInternalUrlService;

    @Value("${artemis.version-control.user}")
    private String gitUser;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private final ResourceLoaderService resourceLoaderService;

    private final BambooServer bambooServer;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<AeolusBuildPlanService> aeolusBuildPlanService;

    private final UriService uriService;

    public BambooBuildPlanService(ResourceLoaderService resourceLoaderService, BambooServer bambooServer, Optional<VersionControlService> versionControlService,
            ProgrammingLanguageConfiguration programmingLanguageConfiguration, UriService uriService, BambooInternalUrlService bambooInternalUrlService,
            Optional<AeolusBuildPlanService> aeolusBuildPlanService) {
        this.resourceLoaderService = resourceLoaderService;
        this.bambooServer = bambooServer;
        this.versionControlService = versionControlService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
        this.uriService = uriService;
        this.bambooInternalUrlService = bambooInternalUrlService;
        this.aeolusBuildPlanService = aeolusBuildPlanService;
    }

    /**
     * Creates a Build Plan for a Programming Exercise
     *
     * @param programmingExercise   programming exercise with the required
     *                                  information to create the base build plan
     * @param planKey               the key of the build plan
     * @param repositoryUri         the uri of the assignment repository
     * @param testRepositoryUri     the uri of the test repository
     * @param solutionRepositoryUri the uri of the solution repository
     * @param auxiliaryRepositories List of auxiliary repositories to be included in
     *                                  the build plan
     */
    public void createBuildPlanForExercise(ProgrammingExercise programmingExercise, String planKey, VcsRepositoryUri repositoryUri, VcsRepositoryUri testRepositoryUri,
            VcsRepositoryUri solutionRepositoryUri, List<AuxiliaryRepository.AuxRepoNameWithUri> auxiliaryRepositories) {
        final String planDescription = planKey + " Build Plan for Exercise " + programmingExercise.getTitle();
        final String projectKey = programmingExercise.getProjectKey();
        final String projectName = programmingExercise.getProjectName();
        final boolean recordTestwiseCoverage = Boolean.TRUE.equals(programmingExercise.isTestwiseCoverageEnabled()) && "SOLUTION".equals(planKey);

        String assignedKey = null;

        if (aeolusBuildPlanService.isPresent()) {
            assignedKey = createCustomAeolusBuildPlanForExercise(programmingExercise, projectKey + "-" + planKey, planDescription, repositoryUri, testRepositoryUri,
                    solutionRepositoryUri, auxiliaryRepositories);
        }

        if (assignedKey == null) {
            Plan plan = createDefaultBuildPlan(planKey, planDescription, projectKey, projectName, repositoryUri, testRepositoryUri,
                    programmingExercise.getCheckoutSolutionRepository(), solutionRepositoryUri, auxiliaryRepositories)
                    .stages(createBuildStage(programmingExercise.getProgrammingLanguage(), programmingExercise.getProjectType(), programmingExercise.getPackageName(),
                            programmingExercise.hasSequentialTestRuns(), programmingExercise.isStaticCodeAnalysisEnabled(), programmingExercise.getCheckoutSolutionRepository(),
                            recordTestwiseCoverage, programmingExercise.getAuxiliaryRepositoriesForBuildPlan()));
            bambooServer.publish(plan);
            assignedKey = plan.getKey().toString();
        }

        setBuildPlanPermissionsForExercise(programmingExercise, assignedKey);
    }

    /**
     * Set Build Plan Permissions for admins, instructors, editors and teaching assistants.
     *
     * @param programmingExercise a programming exercise with the required
     *                                information to set the needed build plan
     *                                permissions
     * @param planKey             The name of the source plan
     */
    public void setBuildPlanPermissionsForExercise(ProgrammingExercise programmingExercise, String planKey) {
        // Get course over exerciseGroup in exam mode
        Course course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();

        final String teachingAssistantGroupName = course.getTeachingAssistantGroupName();
        final String editorGroupName = course.getEditorGroupName();
        final String instructorGroupName = course.getInstructorGroupName();
        final PlanPermissions planPermission = generatePlanPermissions(programmingExercise.getProjectKey(), planKey, teachingAssistantGroupName, editorGroupName,
                instructorGroupName, adminGroupName);
        bambooServer.publish(planPermission);
    }

    private Project createBuildProject(String name, String key) {
        return new Project().key(key).name(name);
    }

    private Stage createBuildStage(ProgrammingLanguage programmingLanguage, ProjectType projectType, String packageName, final boolean sequentialBuildRuns,
            Boolean staticCodeAnalysisEnabled, boolean checkoutSolutionRepository, final boolean recordTestwiseCoverage, List<AuxiliaryRepository> auxiliaryRepositories) {
        final var assignmentPath = RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(programmingLanguage);
        final var testPath = RepositoryCheckoutPath.TEST.forProgrammingLanguage(programmingLanguage);
        VcsCheckoutTask checkoutTask;
        if (checkoutSolutionRepository) {
            final var solutionPath = RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(programmingLanguage);
            checkoutTask = createCheckoutTask(assignmentPath, testPath, Optional.of(solutionPath), auxiliaryRepositories);
        }
        else {
            checkoutTask = createCheckoutTask(assignmentPath, testPath, auxiliaryRepositories);
        }
        Stage defaultStage = new Stage("Default Stage");
        Job defaultJob = new Job("Default Job", new BambooKey("JOB1")).cleanWorkingDirectory(true);

        // Xcode has no dockerfile, it only runs on agents (e.g. sb2-agent-0050562fddde)
        if (!ProjectType.XCODE.equals(projectType)) {
            defaultJob.dockerConfiguration(dockerConfigurationFor(programmingLanguage, Optional.ofNullable(projectType)));
        }
        switch (programmingLanguage) {
            case JAVA, KOTLIN -> {
                // the project type can be null for Kotlin exercises and exercises created before the project type was
                // added to Artemis. The project type for these exercises is implicitly Maven (because Gradle did not
                // exist in Artemis yet)
                boolean isMavenProject = ProjectType.isMavenProject(projectType);

                List<Task<?, ?>> defaultTasks = new ArrayList<>();
                defaultTasks.add(checkoutTask);
                List<Task<?, ?>> finalTasks = new ArrayList<>();
                List<Artifact> artifacts = new ArrayList<>();

                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    modifyBuildConfigurationForStaticCodeAnalysisForJavaAndKotlinExercise(isMavenProject, finalTasks, artifacts);
                }

                if (!sequentialBuildRuns) {
                    modifyBuildConfigurationForRegularTestsForJavaAndKotlinExercise(isMavenProject, recordTestwiseCoverage, defaultTasks, finalTasks, artifacts);
                }
                else {
                    modifyBuildConfigurationForSequentialTestsForJavaAndKotlinExercise(isMavenProject, defaultTasks, finalTasks);
                }

                defaultJob.tasks(defaultTasks.toArray(Task[]::new));
                defaultJob.finalTasks(finalTasks.toArray(Task[]::new));
                defaultJob.artifacts(artifacts.toArray(Artifact[]::new));

                return defaultStage.jobs(defaultJob);
            }
            case PYTHON -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "test-reports/*results.xml");
            }
            case C -> {
                // Default tasks:
                final Optional<Path> projectTypeSubdirectory = Optional.of(Path.of(projectType.name().toLowerCase()));
                final var tasks = readScriptTasksFromTemplate(programmingLanguage, projectTypeSubdirectory, null, sequentialBuildRuns, false);
                tasks.add(0, checkoutTask);
                defaultJob.tasks(tasks.toArray(Task[]::new));

                // Final tasks:
                final TestParserTask testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("test-reports/*results.xml");
                defaultJob.finalTasks(testParserTask);

                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    // Create artifacts and a final task for the execution of static code analysis
                    final List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.C);
                    final Artifact[] artifacts = staticCodeAnalysisTools.stream()
                            .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPatterns(tool.getFilePattern()).shared(false))
                            .toArray(Artifact[]::new);
                    defaultJob.artifacts(artifacts);
                    final var scaTasks = readScriptTasksFromTemplate(programmingLanguage, Optional.empty(), null, false, true);
                    defaultJob.finalTasks(scaTasks.toArray(Task[]::new));
                }

                // Do not remove target, so the report can be sent to Artemis
                final ScriptTask cleanupTask = new ScriptTask().description("cleanup").inlineBody("""
                        sudo rm -rf tests/
                        sudo rm -rf assignment/
                        sudo rm -rf test-reports/""");
                defaultJob.finalTasks(cleanupTask);
                defaultStage.jobs(defaultJob);

                return defaultStage;
            }
            case HASKELL, OCAML -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "**/test-reports/*.xml");
            }
            case VHDL, ASSEMBLER -> {
                return createDefaultStage(programmingLanguage, sequentialBuildRuns, checkoutTask, defaultStage, defaultJob, "**/result.xml");
            }
            case SWIFT -> {
                final var isXcodeProject = ProjectType.XCODE.equals(projectType);
                final Optional<Path> subDirectory = isXcodeProject ? Optional.of(Path.of("xcode")) : Optional.empty();
                Map<String, String> replacements = Map.of("${packageName}", packageName);
                var testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/tests.xml");
                if (isXcodeProject) {
                    testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/report.junit");
                    replacements = Map.of("${appName}", packageName);
                }
                final var tasks = readScriptTasksFromTemplate(programmingLanguage, subDirectory, replacements, sequentialBuildRuns, false);
                tasks.add(0, checkoutTask);
                defaultJob.tasks(tasks.toArray(Task[]::new)).finalTasks(testParserTask);
                if (Boolean.TRUE.equals(staticCodeAnalysisEnabled)) {
                    // Create artifacts and a final task for the execution of static code analysis
                    final List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.SWIFT);
                    final Artifact[] artifacts = staticCodeAnalysisTools.stream()
                            .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPatterns(tool.getFilePattern()).shared(false))
                            .toArray(Artifact[]::new);
                    defaultJob.artifacts(artifacts);
                    final var scaTasks = readScriptTasksFromTemplate(programmingLanguage, subDirectory, null, false, true);
                    defaultJob.finalTasks(scaTasks.toArray(Task[]::new));
                }
                if (isXcodeProject) {
                    // add a requirement to be able to run the Xcode build tasks using fastlane
                    final var requirement = new Requirement("system.builder.fastlane.fastlane");
                    defaultJob.requirements(requirement);
                }
                return defaultStage.jobs(defaultJob);
            }
            case EMPTY -> {
                ScriptTask mvnVersionTask = new ScriptTask().description("Print Maven Version").inlineBody("mvn --version").interpreterShell()
                        .location(ScriptTaskProperties.Location.INLINE);
                return defaultStage.jobs(defaultJob.tasks(checkoutTask, mvnVersionTask));
            }
            // this is needed, otherwise the compiler complaints with missing return
            // statement
            default -> throw new IllegalArgumentException("No build stage setup for programming language " + programmingLanguage);
        }
    }

    /**
     * Modify the lists containing default tasks, final tasks and artifacts for executing a static code analysis for
     * Java and Kotlin exercises.
     *
     * @param isMavenProject whether the project is a Maven build (or implicitly a Gradle build)
     * @param finalTasks     the list containing the final tasks for the build plan to be created
     * @param artifacts      the list containing all artifacts for the build plan to be created
     */
    private void modifyBuildConfigurationForStaticCodeAnalysisForJavaAndKotlinExercise(boolean isMavenProject, List<Task<?, ?>> finalTasks, List<Artifact> artifacts) {
        // Create artifacts and a final task for the execution of static code analysis
        List<StaticCodeAnalysisTool> staticCodeAnalysisTools = StaticCodeAnalysisTool.getToolsForProgrammingLanguage(ProgrammingLanguage.JAVA);
        var scaArtifacts = staticCodeAnalysisTools.stream()
                .map(tool -> new Artifact().name(tool.getArtifactLabel()).location("target").copyPatterns(tool.getFilePattern()).shared(false)).toList();

        if (isMavenProject) {
            String command = StaticCodeAnalysisTool.createBuildPlanCommandForProgrammingLanguage(ProgrammingLanguage.JAVA);
            finalTasks.add(new MavenTask().goal(command).jdk("JDK").executableLabel("Maven 3").description("Static Code Analysis").hasTests(false));
        }
        else {
            finalTasks.add(new ScriptTask().inlineBody("./gradlew check -x test").description("Static Code Analysis"));
        }
        artifacts.addAll(scaArtifacts);
    }

    /**
     * Modify the lists containing default and final tasks for executing a non-sequential test run
     *
     * @param isMavenProject         whether the project is a Maven project (or implicitly a Gradle project)
     * @param recordTestwiseCoverage whether the testwise coverage should be recorded
     * @param defaultTasks           the list containing the default tasks for the build plan to be created
     * @param finalTasks             the list containing the final tasks for the build plan to be created
     * @param artifacts              the list containing all artifacts for the build plan to be created
     */
    private void modifyBuildConfigurationForRegularTestsForJavaAndKotlinExercise(boolean isMavenProject, boolean recordTestwiseCoverage, List<Task<?, ?>> defaultTasks,
            List<Task<?, ?>> finalTasks, List<Artifact> artifacts) {
        if (isMavenProject) {
            String goals = "clean test";
            if (recordTestwiseCoverage) {
                // If a testwise coverage should be performed, a custom profile is used for the execution
                goals += " -Pcoverage";
                artifacts.add(new Artifact().name("testwiseCoverageReport").location("target/tia/reports").copyPatterns("tiaTests.json"));
            }

            defaultTasks.add(new MavenTask().goal(goals).jdk("JDK").executableLabel("Maven 3").description("Tests").hasTests(true));

            // the report name of the artifact has to be renamed, since the name contains the latest timestamp and artifact pattern matching
            // returns a folder of artifacts, instead of the individual artifact. The report has to be renamed after the build execution
            if (recordTestwiseCoverage) {
                defaultTasks.add(new ScriptTask().description("Move Report File").inlineBody("mv target/tia/reports/*/testwise-coverage-*.json target/tia/reports/tiaTests.json"));
            }
        }
        else {
            // setting the permission as a final task is required as a workaround because the docker container runs as a root user
            // and creates files that cannot be deleted by Bamboo because it does not have these root permissions
            String testCommand = "./gradlew clean test";
            if (recordTestwiseCoverage) {
                testCommand += " tiaTests --run-all-tests";
                artifacts.add(new Artifact().name("testwiseCoverageReport").location("build/reports/testwise-coverage/tiaTests").copyPatterns("tiaTests.json"));
            }
            defaultTasks.add(new ScriptTask().inlineBody(testCommand).description("Tests"));
            finalTasks.add(new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories("**/test-results/test/*.xml").description("JUnit Parser"));
            finalTasks.add(new ScriptTask().inlineBody("chmod -R 777 ${bamboo.working.directory}").description("Setup working directory for cleanup"));
        }
    }

    /**
     * Modify the lists containing default and final tasks for executing a sequential test run
     *
     * @param isMavenProject whether the project is a Maven project (or implicitly a Gradle project)
     * @param defaultTasks   the list containing the default tasks for the build plan to be created
     * @param finalTasks     the list containing the final tasks for the build plan to be created
     */
    private void modifyBuildConfigurationForSequentialTestsForJavaAndKotlinExercise(boolean isMavenProject, List<Task<?, ?>> defaultTasks, List<Task<?, ?>> finalTasks) {
        if (isMavenProject) {
            defaultTasks
                    .add(new MavenTask().goal("clean test").workingSubdirectory("structural").jdk("JDK").executableLabel("Maven 3").description("Structural tests").hasTests(true));
            defaultTasks.add(new MavenTask().goal("clean test").workingSubdirectory("behavior").jdk("JDK").executableLabel("Maven 3").description("Behavior tests").hasTests(true));
        }
        else {
            // setting the permission as a final task is required as a workaround because the docker container runs as a root user
            // and creates files that cannot be deleted by Bamboo because it does not have these root permissions
            finalTasks.add(new TestParserTask(TestParserTaskProperties.TestType.JUNIT)
                    .resultDirectories("**/test-results/structuralTests/*.xml,**/test-results/behaviorTests/*.xml").description("JUnit Parser"));
            finalTasks.add(new ScriptTask().inlineBody("chmod -R 777 ${bamboo.working.directory}").description("Setup working directory for cleanup"));
            // the script task for executing the behavior tests must not clean the build files because the test parser would not have parsed the tests for the structural tests yet
            defaultTasks.add(new ScriptTask().inlineBody("./gradlew clean structuralTests").description("Structural tests"));
            defaultTasks.add(new ScriptTask().inlineBody("./gradlew behaviorTests").description("Behavior tests"));
        }
    }

    private Stage createDefaultStage(ProgrammingLanguage programmingLanguage, boolean sequentialBuildRuns, VcsCheckoutTask checkoutTask, Stage defaultStage, Job defaultJob,
            String resultDirectories) {
        final var testParserTask = new TestParserTask(TestParserTaskProperties.TestType.JUNIT).resultDirectories(resultDirectories);
        final var tasks = readScriptTasksFromTemplate(programmingLanguage, Optional.empty(), null, sequentialBuildRuns, false);
        tasks.add(0, checkoutTask);
        return defaultStage.jobs(defaultJob.tasks(tasks.toArray(Task[]::new)).finalTasks(testParserTask));
    }

    private Plan createDefaultBuildPlan(String planKey, String planDescription, String projectKey, String projectName, VcsRepositoryUri assignmentRepoUri,
            VcsRepositoryUri testRepoUri, boolean checkoutSolutionRepository, VcsRepositoryUri solutionRepoUri,
            List<AuxiliaryRepository.AuxRepoNameWithUri> auxiliaryRepositories) {

        VersionControlService versionControl = versionControlService.orElseThrow();

        List<GitRepository> planRepositories = new ArrayList<>();
        planRepositories.add(createBuildPlanGitRepository(ASSIGNMENT_REPO_NAME, bambooInternalUrlService.toInternalVcsUrl(assignmentRepoUri).toString(),
                versionControl.getDefaultBranchOfRepository(projectKey, uriService.getRepositorySlugFromRepositoryUri(assignmentRepoUri))));
        planRepositories.add(createBuildPlanGitRepository(TEST_REPO_NAME, bambooInternalUrlService.toInternalVcsUrl(testRepoUri).toString(),
                versionControl.getDefaultBranchOfRepository(projectKey, uriService.getRepositorySlugFromRepositoryUri(testRepoUri))));
        for (var auxRepo : auxiliaryRepositories) {
            planRepositories.add(createBuildPlanGitRepository(auxRepo.name(), bambooInternalUrlService.toInternalVcsUrl(auxRepo.repositoryUri()).toString(),
                    versionControl.getDefaultBranchOfRepository(projectKey, uriService.getRepositorySlugFromRepositoryUri(auxRepo.repositoryUri()))));
        }
        if (checkoutSolutionRepository) {
            planRepositories.add(createBuildPlanGitRepository(SOLUTION_REPO_NAME, bambooInternalUrlService.toInternalVcsUrl(solutionRepoUri).toString(),
                    versionControl.getDefaultBranchOfRepository(projectKey, uriService.getRepositorySlugFromRepositoryUri(solutionRepoUri))));
        }

        return new Plan(createBuildProject(projectName, projectKey), planKey, planKey).description(planDescription)
                .pluginConfigurations(new ConcurrentBuilds().useSystemWideDefault(true)).planRepositories(planRepositories.toArray(VcsRepository[]::new))
                .planBranchManagement(createPlanBranchManagement()).notifications(createNotification());
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath, List<AuxiliaryRepository> auxiliaryRepositories) {
        return createCheckoutTask(assignmentPath, testPath, Optional.empty(), auxiliaryRepositories);
    }

    private VcsCheckoutTask createCheckoutTask(String assignmentPath, String testPath, Optional<String> solutionPath, List<AuxiliaryRepository> auxiliaryRepositories) {
        List<CheckoutItem> checkoutItems = new ArrayList<>();
        checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(TEST_REPO_NAME)).path(testPath));
        checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(ASSIGNMENT_REPO_NAME)).path(assignmentPath));
        for (AuxiliaryRepository repo : auxiliaryRepositories) {
            checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(repo.getName())).path(repo.getCheckoutDirectory()));
        }
        solutionPath.ifPresent(s -> checkoutItems.add(new CheckoutItem().repository(new VcsRepositoryIdentifier().name(SOLUTION_REPO_NAME)).path(s)));
        return new VcsCheckoutTask().description("Checkout Default Repository").checkoutItems(checkoutItems.toArray(CheckoutItem[]::new));
    }

    private PlanBranchManagement createPlanBranchManagement() {
        return new PlanBranchManagement().delete(new BranchCleanup()).notificationForCommitters();
    }

    private Notification createNotification() {
        return new Notification().type(new PlanCompletedNotification())
                .recipients(new AnyNotificationRecipient(new AtlassianModule("de.tum.in.www1.bamboo-server:recipient.server"))
                        .recipientString(artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH));
    }

    private GitRepository createBuildPlanGitRepository(String name, String repositoryUri, String branch) {
        return new GitRepository().name(name).branch(branch).authentication(new SharedCredentialsIdentifier(gitUser).scope(SharedCredentialsScope.GLOBAL))
                .url(repositoryUri.toLowerCase()).shallowClonesEnabled(true).remoteAgentCacheEnabled(false).changeDetection(new VcsChangeDetection());
    }

    private PlanPermissions generatePlanPermissions(String bambooProjectKey, String bambooPlanKey, @Nullable String teachingAssistantGroupName, @Nullable String editorGroupName,
            String instructorGroupName, String adminGroupName) {
        var permissions = new Permissions().userPermissions(bambooUser, PermissionType.EDIT, PermissionType.BUILD, PermissionType.CLONE, PermissionType.VIEW, PermissionType.ADMIN)
                .groupPermissions(adminGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN)
                .groupPermissions(instructorGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN);
        if (editorGroupName != null) {
            permissions = permissions.groupPermissions(editorGroupName, PermissionType.CLONE, PermissionType.BUILD, PermissionType.EDIT, PermissionType.VIEW, PermissionType.ADMIN);
        }
        if (teachingAssistantGroupName != null) {
            permissions = permissions.groupPermissions(teachingAssistantGroupName, PermissionType.VIEW);
        }
        return new PlanPermissions(new PlanIdentifier(bambooProjectKey, bambooPlanKey)).permissions(permissions);
    }

    private List<Task<?, ?>> readScriptTasksFromTemplate(final ProgrammingLanguage programmingLanguage, final Optional<Path> projectTypeSubDirectory,
            final Map<String, String> replacements, final boolean sequentialBuildRuns, final boolean getScaTasks) {
        final Path scriptBasePath = getScriptPattern(programmingLanguage, projectTypeSubDirectory, sequentialBuildRuns, getScaTasks);

        try {
            List<Task<?, ?>> tasks = new ArrayList<>();
            final var scriptResources = Arrays.asList(resourceLoaderService.getResources(scriptBasePath, "*.sh"));
            scriptResources.sort(Comparator.comparing(Resource::getFilename));
            for (final var resource : scriptResources) {
                // 1_some_description.sh --> "some description"
                String fileName = resource.getFilename();
                if (fileName != null) {
                    final var descriptionElements = Arrays.stream(fileName.split("\\.")[0] // cut .sh suffix
                            .split("_")).skip(1) // Remove the index prefix: 1 some description --> some description
                            .toList();
                    final var scriptDescription = String.join(" ", descriptionElements);
                    try (final var inputStream = resource.getInputStream()) {
                        var inlineBody = IOUtils.toString(inputStream, Charset.defaultCharset());
                        if (replacements != null) {
                            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                                inlineBody = inlineBody.replace(replacement.getKey(), replacement.getValue());
                            }
                        }
                        tasks.add(new ScriptTask().description(scriptDescription).inlineBody(inlineBody));
                    }
                }
            }

            return tasks;
        }
        catch (IOException e) {
            throw new ContinuousIntegrationBuildPlanException("Unable to load template build plans", e);
        }
    }

    /**
     * Returns a path pattern that matches all shell scripts that define the build plan steps.
     * <p>
     * The name and number of scripts is different for each exercise type.
     * Therefore, a pattern is returned that matches all {@code sh}-scripts in the specific template directory depending on the exercise features.
     * A resource loader can then load all matching scripts in one go, rather than loading the files individually.
     *
     * @param programmingLanguage     The programming language of the exercise for which a build plan is set up.
     * @param projectTypeSubDirectory The subdirectory where the template files are stored based on the project type of the exercise.
     * @param sequentialBuildRuns     If sequential build runs are enabled for the exercise.
     * @param getScaTasks             If static code analysis is enabled for the exercise.
     * @return A path pattern that matches all shell scripts needed for the build steps in Bamboo.
     */
    private static Path getScriptPattern(final ProgrammingLanguage programmingLanguage, final Optional<Path> projectTypeSubDirectory, final boolean sequentialBuildRuns,
            final boolean getScaTasks) {
        Path pattern = Path.of("templates", "bamboo", programmingLanguage.name().toLowerCase());
        if (projectTypeSubDirectory.isPresent()) {
            pattern = pattern.resolve(projectTypeSubDirectory.get());
        }

        final String projectTypeDir;
        if (getScaTasks) {
            projectTypeDir = "staticCodeAnalysisRuns";
        }
        else if (sequentialBuildRuns) {
            projectTypeDir = "sequentialRuns";
        }
        else {
            projectTypeDir = "regularRuns";
        }

        return pattern.resolve(projectTypeDir);
    }

    /**
     * Assembles a bamboo docker configuration for a given programming exercise and project type
     *
     * @param programmingLanguage the chosen language of the programming exercise
     * @param projectType         the chosen project type of the programming exercise
     * @return bamboo docker configuration
     */
    private DockerConfiguration dockerConfigurationFor(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType) {
        var dockerConfiguration = new DockerConfiguration();

        dockerConfiguration.dockerRunArguments(getDefaultDockerRunArguments());
        dockerConfiguration.image(programmingLanguageConfiguration.getImage(programmingLanguage, projectType));

        return dockerConfiguration;
    }

    /**
     * Get the docker run arguments for a Bamboo DockerConfiguration.
     * The configuration is obtained from the programmingLanguageConfiguration.
     *
     * @return An array of string containing all the configured docker run argument key-value pairs prefixed with two dashes
     */
    private String[] getDefaultDockerRunArguments() {
        return programmingLanguageConfiguration.getDefaultDockerFlags().toArray(String[]::new);
    }

    /**
     * Creates a custom Build Plan for a Programming Exercise using Aeolus. If the build plan could not be created, null is
     * returned. Otherwise, the key of the created build plan is returned. Not the whole key is returned, only the build plan
     * key, without the project key. Example: "PROJECT-BUILDPLANKEY" is created -> "BUILDPLANKEY" is returned, same as the
     * default build plan creation.
     *
     * @param programmingExercise   the programming exercise for which to create the build plan
     * @param buildPlanId           the id of the build plan
     * @param planDescription       the description of the build plan
     * @param repositoryUri         the url of the assignment repository
     * @param testRepositoryUri     the url of the test repository
     * @param solutionRepositoryUri the url of the solution repository
     * @param auxiliaryRepositories List of auxiliary repositories to be included in the build plan
     * @return the key of the created build plan, or null if it could not be created
     */
    private String createCustomAeolusBuildPlanForExercise(ProgrammingExercise programmingExercise, String buildPlanId, String planDescription, VcsRepositoryUri repositoryUri,
            VcsRepositoryUri testRepositoryUri, VcsRepositoryUri solutionRepositoryUri, List<AuxiliaryRepository.AuxRepoNameWithUri> auxiliaryRepositories)
            throws ContinuousIntegrationBuildPlanException {
        if (aeolusBuildPlanService.isEmpty()) {
            return null;
        }
        if (programmingExercise.getBuildPlanConfiguration() == null) {
            // If no custom build plan configuration is present, we will use the default build plan creation
            return null;
        }
        String assignedKey = null;
        try {
            Windfile windfile = programmingExercise.getWindfile();
            Map<String, AeolusRepository> repositories = aeolusBuildPlanService.get().createRepositoryMapForWindfile(programmingExercise.getProgrammingLanguage(),
                    programmingExercise.getBranch(), programmingExercise.getCheckoutSolutionRepository(), repositoryUri, testRepositoryUri, solutionRepositoryUri,
                    auxiliaryRepositories);

            String resultHookUrl = artemisServerUrl + NEW_RESULT_RESOURCE_API_PATH;
            windfile.setPreProcessingMetadata(buildPlanId, programmingExercise.getProjectName(), this.gitUser, resultHookUrl, planDescription, repositories, null);
            String generatedKey = aeolusBuildPlanService.get().publishBuildPlan(windfile, AeolusTarget.BAMBOO);
            /*
             * Aeolus returns the key of the build plan in the format "PROJECT-BUILDPLANKEY". To stay consistent with the
             * default build plan creation, we return only the build plan key.
             */
            if (generatedKey != null && generatedKey.contains("-")) {
                assignedKey = generatedKey.split("-")[1];
            }
            else {
                throw new ContinuousIntegrationBuildPlanException("Could not create custom build plan for exercise " + programmingExercise.getTitle());
            }
        }
        catch (ContinuousIntegrationBuildPlanException e) {
            log.error("Could not create custom build plan for exercise {} with id {}, will create default build plan", programmingExercise.getTitle(), programmingExercise.getId(),
                    e);
        }
        return assignedKey;
    }
}
