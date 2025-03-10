package de.tum.in.www1.artemis.repository.metis;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.repository.specs.MessageSpecs.*;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the Message (Post) entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ConversationMessageRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {

    /**
     * Generates SQL Query via specifications to find and sort Messages
     *
     * @param postContextFilter filtering and sorting properties for post objects
     * @param pageable          paging object which contains the page number and number of records to fetch
     * @param userId            the id of the user for which the messages should be returned
     * @return returns a Page of Messages
     */
    default Page<Post> findMessages(PostContextFilter postContextFilter, Pageable pageable, long userId) {
        Specification<Post> specification = Specification.where(getConversationSpecification(postContextFilter.getConversationId()))
                .and(getSearchTextSpecification(postContextFilter.getSearchText())).and(getOwnSpecification(postContextFilter.getFilterToOwn(), userId))
                .and(getAnsweredOrReactedSpecification(postContextFilter.getFilterToAnsweredOrReacted(), userId))
                .and(getUnresolvedSpecification(postContextFilter.getFilterToUnresolved()))
                .and(getSortSpecification(true, postContextFilter.getPostSortCriterion(), postContextFilter.getSortingOrder()));

        return findAll(specification, pageable);
    }

    /**
     * Generates SQL Query via specifications to find and sort messages from course-wide
     *
     * @param postContextFilter filtering and sorting properties for post objects
     * @param pageable          paging object which contains the page number and number of records to fetch
     * @param userId            the id of the user for which the messages should be returned
     * @return returns a Page of Messages
     */
    default Page<Post> findCourseWideMessages(PostContextFilter postContextFilter, Pageable pageable, long userId) {
        Specification<Post> specification = Specification.where(getCourseWideChannelsSpecification(postContextFilter.getCourseId()))
                .and(getConversationsSpecification(postContextFilter.getCourseWideChannelIds())).and(getSearchTextSpecification(postContextFilter.getSearchText()))
                .and(getOwnSpecification(postContextFilter.getFilterToOwn(), userId))
                .and(getAnsweredOrReactedSpecification(postContextFilter.getFilterToAnsweredOrReacted(), userId))
                .and(getUnresolvedSpecification(postContextFilter.getFilterToUnresolved()))
                .and(getSortSpecification(true, postContextFilter.getPostSortCriterion(), postContextFilter.getSortingOrder()));

        return findAll(specification, pageable);
    }

    default Post findMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return findById(postId).filter(post -> post.getConversation() != null).orElseThrow(() -> new EntityNotFoundException("Message", postId));
    }

    Integer countByConversationId(Long conversationId);

    @Query("""
            SELECT DISTINCT answer.author
            FROM Post p
                LEFT JOIN p.answers answer
                LEFT JOIN p.conversation c
                LEFT JOIN c.conversationParticipants cp
            WHERE p.id = :postId AND answer.author = cp.user
            """)
    Set<User> findUsersWhoRepliedInMessage(@Param("postId") Long postId);

    /**
     * Finds tags of course-wide messages
     *
     * @param courseId the course
     * @return list of tags
     */
    @Query("""
            SELECT DISTINCT tag
            FROM Post post
                LEFT JOIN post.tags tag
                LEFT JOIN Channel channel ON channel.id = post.conversation.id
            WHERE channel.course.id = :courseId
                AND channel.isCourseWide IS TRUE
            """)
    List<String> findPostTagsForCourse(@Param("courseId") Long courseId);
}
