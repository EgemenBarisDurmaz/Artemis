package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.NotificationSetting;
import de.tum.in.www1.artemis.domain.User;

/**
 * Spring Data repository for the NotificationSetting entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    @Query("""
            SELECT notificationSetting
            FROM NotificationSetting notificationSetting
                LEFT JOIN FETCH notificationSetting.user user
            WHERE user.id = :userId
            """)
    Set<NotificationSetting> findAllNotificationSettingsForRecipientWithId(@Param("userId") long userId);

    @Query("""
            SELECT notificationSetting
            FROM NotificationSetting notificationSetting
                LEFT JOIN FETCH notificationSetting.user user
            WHERE user.id IN :userIds
            """)
    Set<NotificationSetting> findAllNotificationSettingsForRecipientsWithId(@Param("userIds") List<Long> userIds);

    @EntityGraph(type = LOAD, attributePaths = { "user.groups", "user.authorities" })
    @Query("""
            SELECT setting
            FROM NotificationSetting setting
            WHERE setting.settingId = :settingId
                AND setting.email IS TRUE
            """)
    Set<NotificationSetting> findAllNotificationSettingsForUsersWhoEnabledSpecifiedEmailSettingWithEagerGroupsAndAuthorities(@Param("settingId") String settingId);

    /**
     * @param settingId of the notification setting (e.g. NOTIFICATION__WEEKLY_SUMMARY__BASIC_WEEKLY_SUMMARY)
     * @return all users who enabled the provided email notification setting with eager groups and authorities
     */
    default Set<User> findAllUsersWhoEnabledSpecifiedEmailNotificationSettingWithEagerGroupsAndAuthorities(String settingId) {
        return findAllNotificationSettingsForUsersWhoEnabledSpecifiedEmailSettingWithEagerGroupsAndAuthorities(settingId).stream().map(NotificationSetting::getUser)
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the ids for all muted conversations of the user with the provided id
     *
     * @param userId id of the user
     * @return ids of the conversations the user has muted
     */
    @Query("""
            SELECT cp.conversation.id
            FROM ConversationParticipant cp
            WHERE cp.user.id = :userId AND (cp.isMuted IS TRUE OR cp.isHidden IS TRUE)
            """)
    Set<Long> findMutedConversations(@Param("userId") long userId);
}
