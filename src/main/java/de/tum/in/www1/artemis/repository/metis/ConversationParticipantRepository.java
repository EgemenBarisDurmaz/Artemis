package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import javax.transaction.Transactional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the ConversationParticipant entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id IN :userIds
            """)
    Set<ConversationParticipant> findConversationParticipantsByConversationIdAndUserIds(@Param("conversationId") Long conversationId, @Param("userIds") Set<Long> userIds);

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
            """)
    Set<ConversationParticipant> findConversationParticipantsByConversationId(@Param("conversationId") Long conversationId);

    @EntityGraph(type = LOAD, attributePaths = { "user.groups", "user.authorities" })
    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
            """)
    Set<ConversationParticipant> findConversationParticipantsWithUserGroupsByConversationId(@Param("conversationId") Long conversationId);

    @Async
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant p
            SET p.lastRead = :now, p.unreadMessagesCount = 0
            WHERE p.user.id = :userId
                AND p.conversation.id = :conversationId
            """)
    void updateLastReadAsync(@Param("userId") Long userId, @Param("conversationId") Long conversationId, @Param("now") ZonedDateTime now);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findConversationParticipantByConversationIdAndUserId(Long conversationId, Long userId);

    default ConversationParticipant findConversationParticipantByConversationIdAndUserIdElseThrow(Long conversationId, Long userId) {
        return this.findConversationParticipantByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Conversation participant not found!"));
    }

    @Query("""
            SELECT DISTINCT conversationParticipant
            FROM ConversationParticipant conversationParticipant
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id = :userId
                AND conversationParticipant.isModerator IS TRUE
            """)
    Optional<ConversationParticipant> findModeratorConversationParticipantByConversationIdAndUserId(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    Integer countByConversationId(Long conversationId);

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByConversationId(Long conversationId);

    /**
     * Increment unreadMessageCount field of ConversationParticipant
     *
     * @param senderId       userId of the sender of the message(Post)
     * @param conversationId conversationId id of the conversation with participants
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount + 1
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id <> :senderId
                AND conversationParticipant.unreadMessagesCount IS NOT NULL
            """)
    void incrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    /**
     * Decrement unreadMessageCount field of ConversationParticipant
     *
     * @param senderId       userId of the sender of the message(Post)
     * @param conversationId conversationId id of the conversation with participants
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE ConversationParticipant conversationParticipant
            SET conversationParticipant.unreadMessagesCount = conversationParticipant.unreadMessagesCount - 1
            WHERE conversationParticipant.conversation.id = :conversationId
                AND conversationParticipant.user.id <> :senderId
                AND conversationParticipant.unreadMessagesCount > 0
                AND conversationParticipant.unreadMessagesCount IS NOT NULL
            """)
    void decrementUnreadMessagesCountOfParticipants(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);
}
