package de.tum.in.www1.artemis.service.connectors.lti;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotNull;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lti.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.ArtemisAuthenticationProvider;
import de.tum.in.www1.artemis.security.lti.Lti13TokenRetriever;
import de.tum.in.www1.artemis.service.OnlineCourseConfigurationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
@Profile("lti")
public class Lti13Service {

    private static final String EXERCISE_PATH_PATTERN = "/courses/{courseId}/exercises/{exerciseId}";

    private static final Logger log = LoggerFactory.getLogger(Lti13Service.class);

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    private final Lti13ResourceLaunchRepository launchRepository;

    private final LtiService ltiService;

    private final ResultRepository resultRepository;

    private final Lti13TokenRetriever tokenRetriever;

    private final OnlineCourseConfigurationService onlineCourseConfigurationService;

    private final LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository;

    private final ArtemisAuthenticationProvider artemisAuthenticationProvider;

    private final RestTemplate restTemplate;

    public Lti13Service(UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository, Lti13ResourceLaunchRepository launchRepository,
            LtiService ltiService, ResultRepository resultRepository, Lti13TokenRetriever tokenRetriever, OnlineCourseConfigurationService onlineCourseConfigurationService,
            RestTemplate restTemplate, ArtemisAuthenticationProvider artemisAuthenticationProvider, LtiPlatformConfigurationRepository ltiPlatformConfigurationRepository) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.ltiService = ltiService;
        this.launchRepository = launchRepository;
        this.resultRepository = resultRepository;
        this.tokenRetriever = tokenRetriever;
        this.onlineCourseConfigurationService = onlineCourseConfigurationService;
        this.restTemplate = restTemplate;
        this.artemisAuthenticationProvider = artemisAuthenticationProvider;
        this.ltiPlatformConfigurationRepository = ltiPlatformConfigurationRepository;
    }

    /**
     * Performs an LTI 1.3 exercise launch with the LTI parameters contained in launchRequest.
     * If the launch was successful the user is added to the target exercise group (e.g. the course).
     *
     * @param ltiIdToken           the id token for the user launching the request
     * @param clientRegistrationId the clientRegistrationId of the source LMS
     */
    public void performLaunch(OidcIdToken ltiIdToken, String clientRegistrationId) {

        String targetLinkUrl = ltiIdToken.getClaim(Claims.TARGET_LINK_URI);
        Optional<Exercise> targetExercise = getExerciseFromTargetLink(targetLinkUrl);
        if (targetExercise.isEmpty()) {
            String message = "No exercise to launch at " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("Exercise not found", "LTI", "ltiExerciseNotFound");
        }
        Exercise exercise = targetExercise.get();

        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        OnlineCourseConfiguration onlineCourseConfiguration = course.getOnlineCourseConfiguration();
        if (onlineCourseConfiguration == null) {
            String message = "Exercise is not related to course for target link url: " + targetLinkUrl;
            log.error(message);
            throw new BadRequestAlertException("LTI is not configured for this course", "LTI", "ltiNotConfigured");
        }

        Optional<String> optionalUsername = artemisAuthenticationProvider.getUsernameForEmail(ltiIdToken.getEmail());

        if (!onlineCourseConfiguration.isRequireExistingUser() && optionalUsername.isEmpty()) {
            SecurityContextHolder.getContext().setAuthentication(ltiService.createNewUserFromLaunchRequest(ltiIdToken.getEmail(),
                    createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration), ltiIdToken.getGivenName(), ltiIdToken.getFamilyName()));

        }

        String username = optionalUsername.orElseGet(() -> createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration));
        User user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(username).orElseThrow();
        ltiService.onSuccessfulLtiAuthentication(user, targetExercise.get());
        Lti13LaunchRequest launchRequest = launchRequestFrom(ltiIdToken, clientRegistrationId);

        createOrUpdateResourceLaunch(launchRequest, user, targetExercise.get());

        ltiService.authenticateLtiUser(ltiIdToken.getEmail(), createUsernameFromLaunchRequest(ltiIdToken, onlineCourseConfiguration), ltiIdToken.getGivenName(),
                ltiIdToken.getFamilyName(), onlineCourseConfiguration.isRequireExistingUser());
    }

    /**
     * Gets the username for the LTI user prefixed with the configured user prefix
     *
     * @param ltiIdToken                the token holding the launch information
     * @param onlineCourseConfiguration the configuration for the online course
     * @return the username for the LTI user
     */
    @NotNull
    public String createUsernameFromLaunchRequest(OidcIdToken ltiIdToken, OnlineCourseConfiguration onlineCourseConfiguration) {
        String username;

        if (StringUtils.hasLength(ltiIdToken.getPreferredUsername())) {
            username = ltiIdToken.getPreferredUsername();
        }
        else if (StringUtils.hasLength(ltiIdToken.getGivenName()) && StringUtils.hasLength(ltiIdToken.getFamilyName())) {
            username = ltiIdToken.getGivenName() + ltiIdToken.getFamilyName();
        }
        else {
            String userEmail = ltiIdToken.getEmail();
            username = userEmail.substring(0, userEmail.indexOf('@')); // Get the initial part of the user's email
        }
        username = username.replace(" ", "");

        return onlineCourseConfiguration.getUserPrefix() + "_" + username;
    }

    private Lti13LaunchRequest launchRequestFrom(OidcIdToken ltiIdToken, String clientRegistrationId) {
        try {
            return new Lti13LaunchRequest(ltiIdToken, clientRegistrationId);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Could not create LTI 1.3 launch request with provided idToken: " + ex.getMessage());
        }
    }

    /**
     * This method is pinged on new exercise results. It sends a message to the related LTI 1.3 platforms with the new score.
     *
     * @param participation The exercise participation for which a new result is available
     */
    public void onNewResult(StudentParticipation participation) {
        Course course = courseRepository.findByIdWithEagerOnlineCourseConfigurationElseThrow(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());

        if (!course.isOnlineCourse()) {
            log.error("Could not transmit score to external LMS for course {}:", course.getTitle());
            return;
        }

        participation.getStudents().forEach(student -> {
            // there can be multiple launches for one exercise and student if the student has used more than one LTI 1.3 platform
            // to launch the exercise (for example multiple lms)
            Collection<LtiResourceLaunch> launches = launchRepository.findByUserAndExercise(student, participation.getExercise());

            if (launches.isEmpty()) {
                return;
            }

            Optional<Result> result = resultRepository.findFirstWithSubmissionAndFeedbacksTestCasesByParticipationIdOrderByCompletionDateDesc(participation.getId());

            if (result.isEmpty()) {
                log.error("onNewResult triggered for participation {} but no result could be found", participation.getId());
                return;
            }

            String concatenatedFeedbacks = result.get().getFeedbacks().stream().map(Feedback::getDetailText).collect(Collectors.joining(". "));

            launches.forEach(launch -> {
                LtiPlatformConfiguration returnPlatform = launch.getLtiPlatformConfiguration();
                ClientRegistration returnClient = onlineCourseConfigurationService.getClientRegistration(returnPlatform);
                submitScore(launch, returnClient, concatenatedFeedbacks, result.get().getScore());

            });
        });
    }

    protected void submitScore(LtiResourceLaunch launch, ClientRegistration clientRegistration, String comment, Double score) {
        String scoreLineItemUrl = getScoresUrl(launch.getScoreLineItemUrl());
        if (scoreLineItemUrl == null) {
            return;
        }

        String token = tokenRetriever.getToken(clientRegistration, Scopes.AGS_SCORE);

        if (token == null) {
            log.error("Could not transmit score to {}: missing token", clientRegistration.getClientId());
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/vnd.ims.lis.v1.score+json"));
        headers.setBearerAuth(token);
        String body = getScoreBody(launch.getSub(), comment, score);
        HttpEntity<String> httpRequest = new HttpEntity<>(body, headers);
        try {
            restTemplate.postForEntity(scoreLineItemUrl, httpRequest, Object.class);
            log.info("Submitted score for {} to client {}", launch.getUser().getLogin(), clientRegistration.getClientId());
        }
        catch (HttpClientErrorException e) {
            String message = "Could not submit score for " + launch.getUser().getLogin() + " to client " + clientRegistration.getClientId() + ": " + e.getMessage();
            log.error(message);
        }
    }

    private String getScoresUrl(String lineItemUrl) {
        if (!StringUtils.hasLength(lineItemUrl)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(lineItemUrl);
        int index = lineItemUrl.indexOf("?");
        if (index == -1) {
            return builder.append("/scores").toString();
        }
        return builder.insert(index, "/scores").toString(); // Adds "/scores" before the "?" in case there are query parameters
    }

    private String getScoreBody(String userId, String comment, Double score) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("userId", userId);
        requestBody.addProperty("timestamp", (new DateTime()).toString());
        requestBody.addProperty("activityProgress", "Submitted");
        requestBody.addProperty("gradingProgress", "FullyGraded");
        requestBody.addProperty("comment", comment);
        requestBody.addProperty("scoreGiven", score);
        requestBody.addProperty("scoreMaximum", 100D);
        return requestBody.toString();
    }

    /**
     * Returns an Optional of an Exercise that was referenced by targetLinkUrl.
     *
     * @param targetLinkUrl to retrieve an Exercise
     * @return the Exercise or nothing otherwise
     */
    private Optional<Exercise> getExerciseFromTargetLink(String targetLinkUrl) {
        AntPathMatcher matcher = new AntPathMatcher();

        String targetLinkPath;
        try {
            targetLinkPath = (new URL(targetLinkUrl)).getPath();
        }
        catch (MalformedURLException ex) {
            log.info("Malformed target link url: {}", targetLinkUrl);
            return Optional.empty();
        }

        if (!matcher.match(EXERCISE_PATH_PATTERN, targetLinkPath)) {
            log.info("Could not extract exerciseId and courseId from target link: {}", targetLinkUrl);
            return Optional.empty();
        }
        Map<String, String> pathVariables = matcher.extractUriTemplateVariables(EXERCISE_PATH_PATTERN, targetLinkPath);

        String exerciseId = pathVariables.get("exerciseId");

        Optional<Exercise> exerciseOpt = exerciseRepository.findById(Long.valueOf(exerciseId));

        if (exerciseOpt.isEmpty()) {
            log.info("Could not find exercise or course for target link url: {}", targetLinkUrl);
            return Optional.empty();
        }

        return exerciseOpt;
    }

    private void createOrUpdateResourceLaunch(Lti13LaunchRequest launchRequest, User user, Exercise exercise) {
        Optional<LtiResourceLaunch> launchOpt = launchRepository.findByIssAndSubAndDeploymentIdAndResourceLinkId(launchRequest.getIss(), launchRequest.getSub(),
                launchRequest.getDeploymentId(), launchRequest.getResourceLinkId());

        LtiResourceLaunch launch = launchOpt.orElse(LtiResourceLaunch.from(launchRequest));

        Lti13AgsClaim agsClaim = launchRequest.getAgsClaim();
        // we do support LTI 1.3 Assigment and Grading Services SCORE publish service
        if (agsClaim != null) {
            launch.setScoreLineItemUrl(agsClaim.getLineItem());
        }

        launch.setExercise(exercise);
        launch.setUser(user);

        Optional<LtiPlatformConfiguration> ltiPlatformConfiguration = ltiPlatformConfigurationRepository.findByRegistrationId(launchRequest.getClientRegistrationId());
        if (ltiPlatformConfiguration.isPresent()) {
            launch.setLtiPlatformConfiguration(ltiPlatformConfiguration.get());
        }

        launchRepository.save(launch);
    }

    /**
     * Build the response for the LTI launch.
     *
     * @param uriComponentsBuilder the uri builder to add the query params to
     * @param response             the response to add the JWT cookie to
     */
    public void buildLtiResponse(UriComponentsBuilder uriComponentsBuilder, HttpServletResponse response) {
        ltiService.buildLtiResponse(uriComponentsBuilder, response);
    }

    /**
     * Builds a response indicating the need for successful login with the associated username.
     *
     * @param response   The HttpServletResponse object.
     * @param ltiIdToken The OIDC ID token with the LTI email address.
     */
    public void buildLtiEmailInUseResponse(HttpServletResponse response, OidcIdToken ltiIdToken) {
        Optional<String> optionalUsername = artemisAuthenticationProvider.getUsernameForEmail(ltiIdToken.getEmail());

        if (optionalUsername.isPresent()) {
            String sanitizedUsername = getSanitizedUsername(optionalUsername.get());
            response.addHeader("ltiSuccessLoginRequired", sanitizedUsername);
        }
        ltiService.prepareLogoutCookie(response);
    }

    private String getSanitizedUsername(String username) {
        // Remove \r and LF \n characters to prevent HTTP response splitting
        return username.replaceAll("[\r\n]", "");
    }

    /**
     * Initiates the deep linking process for a course based on the provided LTI ID token and client registration ID.
     *
     * @param ltiIdToken           The ID token containing the deep linking information.
     * @param clientRegistrationId The client registration ID associated with the LTI platform.
     * @throws BadRequestAlertException if LTI is not configured.
     */
    public void startDeepLinking(OidcIdToken ltiIdToken, String clientRegistrationId) {

        Optional<LtiPlatformConfiguration> ltiPlatformConfiguration = ltiPlatformConfigurationRepository.findByRegistrationId(clientRegistrationId);
        if (ltiPlatformConfiguration.isEmpty()) {
            throw new BadRequestAlertException("Configuration not found for this client registration ID:" + clientRegistrationId, "LTI", "ltiNotConfigured");
        }

        ltiService.authenticateLtiUser(ltiIdToken.getEmail(), ltiIdToken.getPreferredUsername(), ltiIdToken.getGivenName(), ltiIdToken.getFamilyName(), true);
    }
}
