package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.notification.Notification;

/**
 * Spring Data repository for the Notification entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // we can try to use something like TREAT(notification AS GroupNotification) here to avoid warnings
    @Query("""
            SELECT notification
            FROM Notification notification
                LEFT JOIN notification.course
                LEFT JOIN notification.recipient
            WHERE notification.notificationDate > :hideUntil
                AND (
                    (TYPE(notification) = GroupNotification
                        AND ((
                                notification.course.instructorGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.INSTRUCTOR
                            ) OR (
                                notification.course.teachingAssistantGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.TA
                            ) OR (
                                notification.course.editorGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.EDITOR
                            ) OR (
                                notification.course.studentGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.STUDENT
                            )
                        )
                    ) OR (TYPE(notification) = SingleUserNotification
                        AND notification.recipient.login = :login
                        AND (notification.title NOT IN :titlesToNotLoadNotification OR notification.title IS NULL)
                    ) OR (TYPE(notification) = TutorialGroupNotification
                        AND notification.tutorialGroup.id IN :tutorialGroupIds
                    )
                )
            """)
    Page<Notification> findAllNotificationsForRecipientWithLogin(@Param("currentGroups") Set<String> currentGroups, @Param("login") String login,
            @NotNull @Param("hideUntil") ZonedDateTime hideUntil, @Param("tutorialGroupIds") Set<Long> tutorialGroupIds,
            @Param("titlesToNotLoadNotification") Set<String> titlesToNotLoadNotification, Pageable pageable);

    @Query("""
            SELECT notification
            FROM Notification notification
                LEFT JOIN notification.course
                LEFT JOIN notification.recipient
            WHERE notification.notificationDate > :hideUntil
                AND (
                    (TYPE(notification) = GroupNotification
                        AND (notification.title NOT IN :deactivatedTitles OR notification.title IS NULL)
                        AND ((
                                notification.course.instructorGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.INSTRUCTOR
                            ) OR (
                                notification.course.teachingAssistantGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.TA
                            ) OR (
                                notification.course.editorGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.EDITOR
                            ) OR (
                                notification.course.studentGroupName IN :currentGroups
                                AND notification.type = de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.STUDENT
                            )
                        )
                    ) OR (TYPE(notification) = SingleUserNotification
                        AND notification.recipient.login = :login
                        AND (notification.title NOT IN :titlesToNotLoadNotification OR notification.title IS NULL)
                        AND (notification.title NOT IN :deactivatedTitles OR notification.title IS NULL)
                    ) OR (type(notification) = TutorialGroupNotification
                        AND notification.tutorialGroup.id IN :tutorialGroupIds
                        AND (notification.title NOT IN :deactivatedTitles OR notification.title IS NULL)
                    )
                )
            """)
    Page<Notification> findAllNotificationsFilteredBySettingsForRecipientWithLogin(@Param("currentGroups") Set<String> currentGroups, @Param("login") String login,
            @NotNull @Param("hideUntil") ZonedDateTime hideUntil, @Param("deactivatedTitles") Set<String> deactivatedTitles, @Param("tutorialGroupIds") Set<Long> tutorialGroupIds,
            @Param("titlesToNotLoadNotification") Set<String> titlesToNotLoadNotification, Pageable pageable);
}
