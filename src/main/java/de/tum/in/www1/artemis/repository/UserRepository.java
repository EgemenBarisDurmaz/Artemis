package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.repository.specs.UserSpecs.*;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.UserPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the User entity.<br>
 * <br>
 * <p>
 * <b>Note</b>: Please keep in mind that the User entities are soft-deleted when adding new queries to this repository.
 * If you don't need deleted user entities, add `WHERE user.isDeleted IS FALSE` to your query.
 * </p>
 */
@Profile(PROFILE_CORE)
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    String USERS_CACHE = "users";

    String FILTER_INTERNAL = "INTERNAL";

    String FILTER_EXTERNAL = "EXTERNAL";

    String FILTER_ACTIVATED = "ACTIVATED";

    String FILTER_DEACTIVATED = "DEACTIVATED";

    String FILTER_WITH_REG_NO = "WITH_REG_NO";

    String FILTER_WITHOUT_REG_NO = "WITHOUT_REG_NO";

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByActivationKey(String activationKey);

    Optional<User> findOneByResetKey(String resetKey);

    Optional<User> findOneByEmailIgnoreCase(String email);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByEmailIgnoreCase(String email);

    Optional<User> findOneByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Optional<User> findOneWithGroupsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "authorities" })
    Optional<User> findOneWithAuthoritiesByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByEmail(String email);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesByLoginAndIsInternal(String login, boolean isInternal);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Optional<User> findOneWithGroupsAndAuthoritiesById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIdIn(Set<Long> ids);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "organizations" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndOrganizationsById(Long id);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "organizations" })
    Optional<User> findOneWithGroupsAndAuthoritiesAndOrganizationsByLogin(String userLogin);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities", "guidedTourSettings" })
    Optional<User> findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findOneWithLearningPathsByLogin(String login);

    @EntityGraph(type = LOAD, attributePaths = { "learningPaths" })
    Optional<User> findWithLearningPathsById(long userId);

    Long countByIsDeletedIsFalseAndGroupsContains(String groupName);

    @Query("""
            SELECT DISTINCT user
            FROM User user
            WHERE user.isDeleted IS FALSE
                AND (
                    LOWER(user.email) = LOWER(:searchInput)
                    OR LOWER(user.login) = LOWER(:searchInput)
                )
            """)
    List<User> findAllByEmailOrUsernameIgnoreCase(@Param("searchInput") String searchInput);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(String groupName);

    Set<User> findAllByIsDeletedIsFalseAndGroupsContains(String groupName);

    @Query("""
            SELECT new de.tum.in.www1.artemis.domain.ConversationNotificationRecipientSummary (
                user.id,
                user.login,
                user.firstName,
                user.lastName,
                user.langKey,
                user.email,
                CASE WHEN cp.isMuted = TRUE THEN TRUE ELSE FALSE END,
                CASE WHEN cp.isHidden IS TRUE THEN TRUE ELSE FALSE END,
                CASE WHEN ug.group = :teachingAssistantGroupName
                    OR ug.group = :editorGroupName
                    OR ug.group = :instructorGroupName
                THEN TRUE ELSE FALSE END
            )
            FROM User user
                JOIN UserGroup ug ON ug.userId = user.id
                LEFT JOIN ConversationParticipant cp ON cp.user = user AND cp.conversation.id = :conversationId
            WHERE user.isDeleted IS FALSE
                AND (
                    ug.group = :studentGroupName
                    OR ug.group = :teachingAssistantGroupName
                    OR ug.group = :editorGroupName
                    OR ug.group = :instructorGroupName
                )
            """)
    Set<ConversationNotificationRecipientSummary> findAllNotificationRecipientsInCourseForConversation(@Param("conversationId") long conversationId,
            @Param("studentGroupName") String studentGroupName, @Param("teachingAssistantGroupName") String teachingAssistantGroupName,
            @Param("editorGroupName") String editorGroupName, @Param("instructorGroupName") String instructorGroupName);

    /**
     * Searches for users in a group by their login or full name.
     *
     * @param groupName   Name of group in which to search for users
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups
            WHERE user.isDeleted IS FALSE
                AND :groupName MEMBER OF user.groups
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    List<User> searchByLoginOrNameInGroup(@Param("groupName") String groupName, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users in groups by their full name.
     *
     * @param groupNames List of names of groups in which to search for users
     * @param nameOfUser Name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT user
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted IS FALSE
                AND (
                    userGroup IN :groupNames
                    AND CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:nameOfUser%
                 )
            ORDER BY CONCAT_WS(' ', user.firstName, user.lastName)
            """)
    List<User> searchByNameInGroups(@Param("groupNames") Set<String> groupNames, @Param("nameOfUser") String nameOfUser);

    /**
     * Search for all users by login or name in a group
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to DTOs
     */
    @Query(value = """
            SELECT user
            FROM User user
                LEFT JOIN FETCH user.groups
            WHERE user.isDeleted IS FALSE
                AND :groupName MEMBER OF user.groups
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """, countQuery = """
            SELECT COUNT(user)
            FROM User user
            WHERE user.isDeleted IS FALSE
                AND :groupName MEMBER OF user.groups
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    Page<User> searchAllByLoginOrNameInGroup(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupName") String groupName);

    @Query(value = """
            SELECT user
            FROM User user
                LEFT JOIN FETCH user.groups userGroup
            WHERE user.isDeleted IS FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                ) AND user.id <> :idOfUser
            ORDER BY CONCAT_WS(' ', user.firstName, user.lastName)
            """, countQuery = """
            SELECT COUNT(user)
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted IS FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                ) AND user.id <> :idOfUser
            ORDER BY CONCAT_WS(' ', user.firstName, user.lastName)
            """)
    Page<User> searchAllByLoginOrNameInGroupsNotUserId(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames,
            @Param("idOfUser") long idOfUser);

    /**
     * Search for all users by login or name within the provided groups
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupNames  Names of groups in which to search for users
     * @return All users matching search criteria
     */
    @Query("""
            SELECT user
            FROM User user
                LEFT JOIN user.groups userGroup
            WHERE user.isDeleted IS FALSE
                AND userGroup IN :groupNames
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    Page<User> searchAllByLoginOrNameInGroups(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("groupNames") Set<String> groupNames);

    @Query(value = """
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted IS FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """, countQuery = """
            SELECT DISTINCT user
            FROM User user
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted IS FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    Page<User> searchAllByLoginOrNameInConversation(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId);

    @Query(value = """
            SELECT DISTINCT user
            FROM User user
                JOIN FETCH user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted IS FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                ) AND userGroup IN :groupNames
            """, countQuery = """
            SELECT DISTINCT user
            FROM User user
                JOIN user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted IS FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                ) AND userGroup IN :groupNames
            """)
    Page<User> searchAllByLoginOrNameInConversationWithCourseGroups(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId,
            @Param("groupNames") Set<String> groupNames);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    @Query("""
            SELECT DISTINCT user
            FROM User user
                JOIN user.groups userGroup
                JOIN ConversationParticipant conversationParticipant ON conversationParticipant.user.id = user.id
                JOIN Conversation conversation ON conversation.id = conversationParticipant.conversation.id
            WHERE user.isDeleted IS FALSE
                AND conversation.id = :conversationId
                AND (
                    :loginOrName = ''
                    OR user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                ) AND conversationParticipant.isModerator IS TRUE
            """)
    Page<User> searchChannelModeratorsByLoginOrNameInConversation(Pageable pageable, @Param("loginOrName") String loginOrName, @Param("conversationId") long conversationId);

    /**
     * Search for all users by login or name in a group and convert them to {@link UserDTO}
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @param groupName   Name of group in which to search for users
     * @return all users matching search criteria in the group converted to {@link UserDTO}
     */
    default Page<UserDTO> searchAllUsersByLoginOrNameInGroupAndConvertToDTO(Pageable pageable, String loginOrName, String groupName) {
        Page<User> users = searchAllByLoginOrNameInGroup(pageable, loginOrName, groupName);
        return users.map(UserDTO::new);
    }

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    List<User> findAllWithGroupsByIsDeletedIsFalseAndGroupsContainsAndRegistrationNumberIn(String groupName, Set<String> registrationNumbers);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    List<User> findAllWithGroupsByIsDeletedIsFalseAndGroupsContainsAndLoginIn(String groupName, Set<String> logins);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndLoginIn(Set<String> logins);

    /**
     * Searches for users by their login or full name.
     *
     * @param page        Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @return list of found users that match the search criteria
     */
    @Query("""
            SELECT user
            FROM User user
            WHERE user.isDeleted IS FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
            """)
    Page<User> searchAllByLoginOrName(Pageable page, @Param("loginOrName") String loginOrName);

    /**
     * Searches for users in a course by their login or full name.
     *
     * @param page        Pageable related info (e.g. for page size)
     * @param loginOrName Either a login (e.g. ga12abc) or name (e.g. Max Mustermann) by which to search
     * @param courseId    Id of the course the user has to be a member of
     * @return list of found users that match the search criteria
     */
    @Query(value = """
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups
                JOIN Course course ON course.id = :courseId
            WHERE user.isDeleted IS FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
                AND (course.studentGroupName MEMBER OF user.groups
                    OR course.teachingAssistantGroupName MEMBER OF user.groups
                    OR course.editorGroupName MEMBER OF user.groups
                    OR course.instructorGroupName MEMBER OF user.groups
                )
            """, countQuery = """
            SELECT COUNT(DISTINCT user)
            FROM User user
                JOIN Course course ON course.id = :courseId
            WHERE user.isDeleted IS FALSE
                AND (
                    user.login LIKE :#{#loginOrName}%
                    OR CONCAT_WS(' ', user.firstName, user.lastName) LIKE %:#{#loginOrName}%
                )
                AND (course.studentGroupName MEMBER OF user.groups
                    OR course.teachingAssistantGroupName MEMBER OF user.groups
                    OR course.editorGroupName MEMBER OF user.groups
                    OR course.instructorGroupName MEMBER OF user.groups
                )
            """)
    Page<User> searchAllByLoginOrNameInCourse(Pageable page, @Param("loginOrName") String loginOrName, @Param("courseId") long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "groups" })
    Page<User> findAllWithGroupsByIsDeletedIsFalse(Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "groups", "authorities" })
    Set<User> findAllWithGroupsAndAuthoritiesByIsDeletedIsFalse();

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.lastNotificationRead = :lastNotificationRead
            WHERE user.id = :userId
            """)
    void updateUserNotificationReadDate(@Param("userId") long userId, @Param("lastNotificationRead") ZonedDateTime lastNotificationRead);

    /**
     * Update user notification hide until property for current user
     * I.e. updates the filter that hides all notifications with a creation/notification date prior to the set value.
     * If the value is null then all notifications should be shown.
     * (Not to be confused with notification settings. This filter is based on the notification date alone)
     *
     * @param userId                of the user
     * @param hideNotificationUntil indicates a time that is used to filter all notifications that are prior to it
     *                                  (if null -> show all notifications)
     */
    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.hideNotificationsUntil = :hideNotificationUntil
            WHERE user.id = :userId
            """)
    void updateUserNotificationVisibility(@Param("userId") long userId, @Param("hideNotificationUntil") ZonedDateTime hideNotificationUntil);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE User user
            SET user.langKey = :languageKey
            WHERE user.id = :userId
            """)
    void updateUserLanguageKey(@Param("userId") long userId, @Param("languageKey") String languageKey);

    @Modifying
    @Transactional
    @Query("""
            UPDATE User user
            SET user.irisAccepted = :acceptDatetime
            WHERE user.id = :userId
            """)
    void updateIrisAcceptedToDate(@Param("userId") long userId, @Param("acceptDatetime") ZonedDateTime acceptDatetime);

    @Query("""
            SELECT DISTINCT user
            FROM User user
                LEFT JOIN FETCH user.groups
            WHERE user.isDeleted IS FALSE
                AND :groupName MEMBER OF user.groups
                AND user NOT IN :ignoredUsers
            """)
    Set<User> findAllInGroupContainingAndNotIn(@Param("groupName") String groupName, @Param("ignoredUsers") Set<User> ignoredUsers);

    @Query("""
            SELECT DISTINCT team.students AS student
            FROM Team team
                JOIN team.students st
            WHERE st.isDeleted IS FALSE
                AND team.exercise.course.id = :courseId
                AND team.shortName = :teamShortName
            """)
    Set<User> findAllInTeam(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    /**
     * Get all managed users
     *
     * @param userSearch used to find users
     * @return all users
     */
    default Page<UserDTO> getAllManagedUsers(UserPageableSearchDTO userSearch) {
        // Prepare filter
        final var searchTerm = userSearch.getSearchTerm();
        var sorting = Sort.by(userSearch.getSortedColumn());
        sorting = userSearch.getSortingOrder() == SortingOrder.ASCENDING ? sorting.ascending() : sorting.descending();
        final var sorted = PageRequest.of(userSearch.getPage(), userSearch.getPageSize(), sorting);

        // List of authorities that a user should match at least one
        Set<String> authorities = userSearch.getAuthorities();
        var modifiedAuthorities = authorities.stream().map(auth -> Role.ROLE_PREFIX + auth).collect(Collectors.toSet());

        // Internal or external users or both
        final var internal = userSearch.getOrigins().contains(FILTER_INTERNAL);
        final var external = userSearch.getOrigins().contains(FILTER_EXTERNAL);

        // Activated or deactivated users or both
        var activated = userSearch.getStatus().contains(FILTER_ACTIVATED);
        var deactivated = userSearch.getStatus().contains(FILTER_DEACTIVATED);

        // Course Ids
        var courseIds = userSearch.getCourseIds();

        // Users without registration numbers or with registration numbers
        var noRegistrationNumber = userSearch.getRegistrationNumbers().contains(FILTER_WITHOUT_REG_NO);
        var withRegistrationNumber = userSearch.getRegistrationNumbers().contains(FILTER_WITH_REG_NO);

        Specification<User> specification = Specification.where(distinct()).and(notSoftDeleted()).and(getSearchTermSpecification(searchTerm))
                .and(getInternalOrExternalSpecification(internal, external)).and(getActivatedOrDeactivatedSpecification(activated, deactivated))
                .and(getAuthoritySpecification(modifiedAuthorities, courseIds)).and(getCourseSpecification(courseIds, modifiedAuthorities))
                .and(getAuthorityAndCourseSpecification(courseIds, modifiedAuthorities))
                .and(getWithOrWithoutRegistrationNumberSpecification(noRegistrationNumber, withRegistrationNumber));

        return findAll(specification, sorted).map(user -> {
            user.setVisibleRegistrationNumber();
            return new UserDTO(user);
        });
    }

    /**
     * Search for all users by login or name
     *
     * @param pageable    Pageable configuring paginated access (e.g. to limit the number of records returned)
     * @param loginOrName Search query that will be searched for in login and name field
     * @return all users matching search criteria
     */
    default Page<UserDTO> searchAllUsersByLoginOrName(Pageable pageable, String loginOrName) {
        Page<User> users = searchAllByLoginOrName(pageable, loginOrName);
        return users.map(UserDTO::new);
    }

    /**
     * @return existing user object by current user login
     */
    @NotNull
    default User getUser() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Retrieve a user by its login, or else throw exception
     *
     * @param login the login of the user to search
     * @return the user entity if it exists
     */
    @NotNull
    default User getUserByLoginElseThrow(String login) {
        return findOneByLogin(login).orElseThrow(() -> new EntityNotFoundException("User: " + login));
    }

    /**
     * Get user with user groups and authorities of currently logged-in user
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAndAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithGroupsAndAuthoritiesByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with authorities of currently logged-in user
     *
     * @return currently logged-in user
     */
    default User getUserWithAuthorities() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithAuthoritiesByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups, authorities and organizations of currently logged-in user
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAndAuthoritiesAndOrganizations() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithGroupsAndAuthoritiesAndOrganizationsByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    /**
     * Get user with user groups, authorities and guided tour settings of currently logged-in user
     * Note: this method should only be invoked if the guided tour settings are really needed
     *
     * @return currently logged-in user
     */
    @NotNull
    default User getUserWithGroupsAuthoritiesAndGuidedTourSettings() {
        String currentUserLogin = getCurrentUserLogin();
        Optional<User> user = findOneWithGroupsAuthoritiesAndGuidedTourSettingsByLogin(currentUserLogin);
        return unwrapOptionalUser(user, currentUserLogin);
    }

    @NotNull
    private User unwrapOptionalUser(Optional<User> optionalUser, String currentUserLogin) {
        return optionalUser.orElseThrow(() -> new EntityNotFoundException("No user found with login: " + currentUserLogin));
    }

    private String getCurrentUserLogin() {
        Optional<String> currentUserLogin = SecurityUtils.getCurrentUserLogin();
        if (currentUserLogin.isPresent()) {
            return currentUserLogin.get();
        }
        throw new EntityNotFoundException("ERROR: No current user login found!");
    }

    /**
     * Get user with user groups and authorities with the username (i.e. user.getLogin() or principal.getName())
     *
     * @param username the username of the user who should be retrieved from the database
     * @return the user that belongs to the given principal with eagerly loaded groups and authorities
     */
    @NotNull
    default User getUserWithGroupsAndAuthorities(@NotNull String username) {
        Optional<User> user = findOneWithGroupsAndAuthoritiesByLogin(username);
        return unwrapOptionalUser(user, username);
    }

    /**
     * Get user with authorities with the username (i.e. user.getLogin() or principal.getName())
     *
     * @param username the username of the user who should be retrieved from the database
     * @return the user that belongs to the given principal with eagerly loaded authorities
     */
    default User getUserWithAuthorities(@NotNull String username) {
        Optional<User> user = findOneWithAuthoritiesByLogin(username);
        return unwrapOptionalUser(user, username);
    }

    /**
     * Finds a single user with groups and authorities using the registration number
     *
     * @param registrationNumber user registration number as string
     * @return the user with groups and authorities
     */
    default Optional<User> findUserWithGroupsAndAuthoritiesByRegistrationNumber(String registrationNumber) {
        if (!StringUtils.hasText(registrationNumber)) {
            return Optional.empty();
        }
        return findOneWithGroupsAndAuthoritiesByRegistrationNumber(registrationNumber);
    }

    /**
     * Finds a single user with groups and authorities using the login name
     *
     * @param login user login string
     * @return the user with groups and authorities
     */
    default Optional<User> findUserWithGroupsAndAuthoritiesByLogin(String login) {
        if (!StringUtils.hasText(login)) {
            return Optional.empty();
        }
        return findOneWithGroupsAndAuthoritiesByLogin(login);
    }

    /**
     * Finds a single user with groups and authorities using the email
     *
     * @param email user email string
     * @return the user with groups and authorities
     */
    default Optional<User> findUserWithGroupsAndAuthoritiesByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }
        return findOneWithGroupsAndAuthoritiesByEmail(email);
    }

    @NotNull
    default User findByIdWithGroupsAndAuthoritiesElseThrow(long userId) {
        return findOneWithGroupsAndAuthoritiesById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * Find user with eagerly loaded groups, authorities and organizations by its id
     *
     * @param userId the id of the user to find
     * @return the user with groups, authorities and organizations if it exists, else throw exception
     */
    @NotNull
    default User findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(long userId) {
        return findOneWithGroupsAndAuthoritiesAndOrganizationsById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * Find user with eagerly loaded learning paths by its id
     *
     * @param userId the id of the user to find
     * @return the user with learning paths if it exists, else throw exception
     */
    @NotNull
    default User findWithLearningPathsByIdElseThrow(long userId) {
        return findWithLearningPathsById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }

    /**
     * Get students by given course
     *
     * @param course object
     * @return students for given course
     */
    default Set<User> getStudents(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getStudentGroupName());
    }

    /**
     * Get tutors by given course
     *
     * @param course object
     * @return tutors for given course
     */
    default Set<User> getTutors(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getTeachingAssistantGroupName());
    }

    /**
     * Get editors by given course
     *
     * @param course object
     * @return editors for given course
     */
    default Set<User> getEditors(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getEditorGroupName());
    }

    /**
     * Get all instructors for a given course
     *
     * @param course The course for which to fetch all instructors
     * @return instructors for the given course
     */
    default Set<User> getInstructors(Course course) {
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getInstructorGroupName());
    }

    /**
     * Finds all users that are part of the specified group, but are not contained in the collection of excluded users
     *
     * @param groupName     The group by which all users should get filtered
     * @param excludedUsers The users that should get ignored/excluded
     * @return users who are in the given group except the excluded ones
     */
    default Set<User> findAllUserInGroupAndNotIn(String groupName, Collection<User> excludedUsers) {
        // For an empty list, we have to use another query, because Hibernate builds an invalid query with empty lists
        if (!excludedUsers.isEmpty()) {
            return findAllInGroupContainingAndNotIn(groupName, new HashSet<>(excludedUsers));
        }
        return findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(groupName);
    }

    default Long countUserInGroup(String groupName) {
        return countByIsDeletedIsFalseAndGroupsContains(groupName);
    }

    /**
     * Update user notification read date for current user
     *
     * @param userId the user for which the notification read date should be updated
     */
    default void updateUserNotificationReadDate(long userId) {
        updateUserNotificationReadDate(userId, ZonedDateTime.now());
    }

    @Query(value = """
            SELECT *
            FROM jhi_user u
            WHERE is_deleted IS FALSE
                AND REGEXP_LIKE(u.email, :emailPattern)
            """, nativeQuery = true)
    List<User> findAllMatchingEmailPattern(@Param("emailPattern") String emailPattern);

    /**
     * Add organization to user, if not contained already
     *
     * @param userId       the id of the user to add to the organization
     * @param organization the organization to add to the user
     */
    default void addOrganizationToUser(Long userId, Organization organization) {
        User user = findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(userId);
        if (!user.getOrganizations().contains(organization)) {
            user.getOrganizations().add(organization);
            save(user);
        }
    }

    /**
     * Remove organization from user, if currently contained
     *
     * @param userId       the id of the user to remove from the organization
     * @param organization the organization to remove from the user
     */
    default void removeOrganizationFromUser(Long userId, Organization organization) {
        User user = findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(userId);
        if (user.getOrganizations().contains(organization)) {
            user.getOrganizations().remove(organization);
            save(user);
        }
    }

    /**
     * Return true if the current users' login matches the provided login
     *
     * @param login user login
     * @return true if both logins match
     */
    default boolean isCurrentUser(String login) {
        return SecurityUtils.getCurrentUserLogin().map(currentLogin -> currentLogin.equals(login)).orElse(false);
    }

    default User findByIdElseThrow(long userId) throws EntityNotFoundException {
        return findById(userId).orElseThrow(() -> new EntityNotFoundException("User", userId));
    }
}
