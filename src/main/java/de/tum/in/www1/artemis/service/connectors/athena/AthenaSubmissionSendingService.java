package de.tum.in.www1.artemis.service.connectors.athena;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.NetworkingException;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.service.dto.athena.ExerciseDTO;
import de.tum.in.www1.artemis.service.dto.athena.SubmissionDTO;

/**
 * Service for sending submissions to the Athena service for further processing
 * so that Athena can later give feedback suggestions on new submissions.
 */
@Service
@Profile("athena")
public class AthenaSubmissionSendingService {

    private static final int SUBMISSIONS_PER_REQUEST = 100;

    private static final Logger log = LoggerFactory.getLogger(AthenaSubmissionSendingService.class);

    private final SubmissionRepository submissionRepository;

    private final AthenaConnector<RequestDTO, ResponseDTO> connector;

    private final AthenaModuleUrlHelper athenaModuleUrlHelper;

    private final AthenaDTOConverter athenaDTOConverter;

    /**
     * Creates a new AthenaSubmissionSendingService.
     */
    public AthenaSubmissionSendingService(@Qualifier("athenaRestTemplate") RestTemplate athenaRestTemplate, SubmissionRepository submissionRepository,
            AthenaModuleUrlHelper athenaModuleUrlHelper, AthenaDTOConverter athenaDTOConverter) {
        this.submissionRepository = submissionRepository;
        connector = new AthenaConnector<>(athenaRestTemplate, ResponseDTO.class);
        this.athenaModuleUrlHelper = athenaModuleUrlHelper;
        this.athenaDTOConverter = athenaDTOConverter;
    }

    private record RequestDTO(ExerciseDTO exercise, List<SubmissionDTO> submissions) {
    }

    private record ResponseDTO(String data) {
    }

    /**
     * Calls the remote Athena service to submit a job for calculating automatic feedback
     *
     * @param exercise the exercise the automatic assessments should be calculated for
     */
    public void sendSubmissions(Exercise exercise) {
        sendSubmissions(exercise, 1);
    }

    /**
     * Calls the remote Athena service to submit a job for calculating automatic feedback
     *
     * @param exercise   the exercise the automatic assessments should be calculated for
     * @param maxRetries number of retries before the request will be canceled
     */
    public void sendSubmissions(Exercise exercise, int maxRetries) {
        if (!exercise.getFeedbackSuggestionsEnabled()) {
            throw new IllegalArgumentException("The Exercise does not have feedback suggestions enabled.");
        }

        log.debug("Start Athena Submission Sending Service for Exercise '{}' (#{}).", exercise.getTitle(), exercise.getId());

        // Find all submissions for exercise (later we will support others)
        Pageable pageRequest = PageRequest.of(0, SUBMISSIONS_PER_REQUEST);
        while (true) {
            Page<Submission> submissions = submissionRepository.findLatestSubmittedSubmissionsByExerciseId(exercise.getId(), pageRequest);
            sendSubmissions(exercise, submissions.toSet(), maxRetries);
            if (submissions.isLast()) {
                break;
            }
            pageRequest = pageRequest.next();
        }
    }

    /**
     * Calls the remote Athena service to submit a Job for calculating automatic feedback
     *
     * @param exercise    the exercise the automatic assessments should be calculated for
     * @param submissions the submissions to send
     * @param maxRetries  number of retries before the request will be canceled
     */
    public void sendSubmissions(Exercise exercise, Set<Submission> submissions, int maxRetries) {
        Set<Submission> filteredSubmissions = new HashSet<>(submissions);

        // filter submissions with an open participation (because of individual due dates)
        filteredSubmissions.removeIf(submission -> {
            var individualDueDate = submission.getParticipation().getIndividualDueDate();
            return individualDueDate != null && individualDueDate.isAfter(ZonedDateTime.now());
        });

        if (filteredSubmissions.isEmpty()) {
            log.info("No submissions found to send (total: {}, filtered: 0)", submissions.size());
            return;
        }

        log.info("Calling Athena to calculate automatic feedback for {} submissions (skipped {} because of individual due date filter)", filteredSubmissions.size(),
                submissions.size() - filteredSubmissions.size());

        try {
            final RequestDTO request = new RequestDTO(athenaDTOConverter.ofExercise(exercise),
                    filteredSubmissions.stream().map((submission) -> athenaDTOConverter.ofSubmission(exercise.getId(), submission)).toList());
            ResponseDTO response = connector.invokeWithRetry(athenaModuleUrlHelper.getAthenaModuleUrl(exercise.getExerciseType()) + "/submissions", request, maxRetries);
            log.info("Athena (calculating automatic feedback) responded: {}", response.data);
        }
        catch (NetworkingException error) {
            log.error("Error while calling Athena", error);
        }
    }

}
