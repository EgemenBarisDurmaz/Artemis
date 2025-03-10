package de.tum.in.www1.artemis.service.iris.session;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisCompetencyGenerationSession;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettingsType;
import de.tum.in.www1.artemis.repository.iris.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.IrisMessageService;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service to handle the Competency generation subsytem of Iris.
 */
@Service
@Profile("iris")
public class IrisCompetencyGenerationSessionService implements IrisButtonBasedFeatureInterface<IrisCompetencyGenerationSession, List<Competency>> {

    private static final Logger log = LoggerFactory.getLogger(IrisCompetencyGenerationSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisSettingsService irisSettingsService;

    private final IrisSessionRepository irisSessionRepository;

    private final AuthorizationCheckService authCheckService;

    private final IrisCompetencyGenerationSessionRepository irisCompetencyGenerationSessionRepository;

    private final IrisMessageService irisMessageService;

    private final IrisMessageRepository irisMessageRepository;

    public IrisCompetencyGenerationSessionService(IrisConnectorService irisConnectorService, IrisSettingsService irisSettingsService, IrisSessionRepository irisSessionRepository,
            AuthorizationCheckService authCheckService, IrisCompetencyGenerationSessionRepository irisCompetencyGenerationSessionRepository, IrisMessageService irisMessageService,
            IrisMessageRepository irisMessageRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisSettingsService = irisSettingsService;
        this.irisSessionRepository = irisSessionRepository;
        this.authCheckService = authCheckService;
        this.irisCompetencyGenerationSessionRepository = irisCompetencyGenerationSessionRepository;
        this.irisMessageService = irisMessageService;
        this.irisMessageRepository = irisMessageRepository;
    }

    /**
     * Creates a new Iris session for the given course and user or gets an existing one from the last hour.
     *
     * @param course The course to create the session for
     * @param user   The user to create the session for
     * @return The Iris session for the course
     */
    public IrisCompetencyGenerationSession getOrCreateSession(Course course, User user) {
        var existingSession = irisCompetencyGenerationSessionRepository.findFirstByCourseIdAndUserIdOrderByCreationDateDesc(course.getId(), user.getId());
        // Return the newest session if there is one and it is not older than 1 hour
        if (existingSession != null && existingSession.getCreationDate().plusHours(1).isAfter(ZonedDateTime.now())) {
            checkHasAccessTo(user, existingSession);
            checkIsFeatureActivatedFor(existingSession);
            return existingSession;
        }

        var irisSession = new IrisCompetencyGenerationSession();
        irisSession.setCourse(course);
        irisSession.setUser(user);
        checkHasAccessTo(user, irisSession);
        checkIsFeatureActivatedFor(irisSession);
        irisSession = irisSessionRepository.save(irisSession);
        return irisSession;
    }

    /**
     * Adds a user text message to a given IRIS session
     *
     * @param session the IRIS session
     * @param message the message to add
     */
    public void addUserTextMessageToSession(IrisCompetencyGenerationSession session, String message) {
        var userMessage = new IrisMessage();
        userMessage.setSender(IrisMessageSender.USER);
        userMessage.addContent(new IrisTextMessageContent(message));
        irisMessageService.saveMessage(userMessage, session, IrisMessageSender.USER);
    }

    // @formatter:off
    record CompetencyGenerationDTO(
            String courseDescription,
            CompetencyTaxonomy[] taxonomyOptions
    ) {}
    // @formatter:on

    @Override
    public List<Competency> executeRequest(IrisCompetencyGenerationSession session) {

        var userMessageContent = irisMessageRepository.findFirstWithContentBySessionIdAndSenderOrderBySentAtDesc(session.getId(), IrisMessageSender.USER).getContent().get(0);
        if (!(userMessageContent instanceof IrisTextMessageContent) || userMessageContent.getContentAsString() == null) {
            throw new InternalServerErrorException("Unable to get last user message!");
        }
        var courseDescription = userMessageContent.getContentAsString();

        var parameters = new CompetencyGenerationDTO(courseDescription, CompetencyTaxonomy.values());
        var irisSettings = irisSettingsService.getCombinedIrisSettingsFor(session.getCourse(), false);
        try {
            var response = irisConnectorService.sendRequestV2(irisSettings.irisCompetencyGenerationSettings().getTemplate().getContent(),
                    irisSettings.irisCompetencyGenerationSettings().getPreferredModel(), parameters).get();

            var llmMessage = new IrisMessage();
            llmMessage.setSender(IrisMessageSender.LLM);
            llmMessage.addContent(new IrisJsonMessageContent(response.content()));
            irisMessageService.saveMessage(llmMessage, session, IrisMessageSender.LLM);

            return toCompetencies(response.content());
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Unable to generate competencies", e);
            throw new InternalServerErrorException("Unable to generate competencies: " + e.getMessage());
        }
    }

    @Override
    public void checkHasAccessTo(User user, IrisCompetencyGenerationSession irisSession) {
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, irisSession.getCourse(), user);
    }

    @Override
    public void checkIsFeatureActivatedFor(IrisCompetencyGenerationSession irisSession) {
        irisSettingsService.isEnabledForElseThrow(IrisSubSettingsType.COMPETENCY_GENERATION, irisSession.getCourse());
    }

    private List<Competency> toCompetencies(JsonNode content) throws IrisParseResponseException {
        List<Competency> competencies = new ArrayList<>();
        for (JsonNode node : content.get("competencies")) {
            try {
                Competency competency = new Competency();
                competency.setTitle(node.required("title").asText());

                // skip competency if IRIS only replied with a title containing the special response "!done!"
                if (node.get("description") == null && node.get("title").asText().equals("!done!")) {
                    log.info("Received special response \"!done!\", skipping parsing of competency.");
                    continue;
                }
                competency.setDescription(node.required("description").asText());
                competency.setTaxonomy(CompetencyTaxonomy.valueOf(node.required("taxonomy").asText()));

                competencies.add(competency);
            }
            catch (IllegalArgumentException e) {
                log.error("Missing fields, could not parse Competency: " + node.toPrettyString(), e);
            }
        }
        return competencies;
    }
}
