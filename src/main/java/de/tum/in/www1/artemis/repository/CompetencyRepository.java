package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyRepository extends JpaRepository<Competency, Long> {

    @Query("""
            SELECT c
            FROM Competency c
            WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllForCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    /**
     * Fetches a competency with all linked exercises, lecture units, the associated progress, and completion of the specified user.
     * <p>
     * IMPORTANT: We use the entity graph to fetch the lazy loaded data. The fetched data is limited by joining on the user id.
     *
     * @param competencyId the id of the competency that should be fetched
     * @param userId       the id of the user whose progress should be fetched
     * @return the competency
     */
    @Query("""
            SELECT competency
            FROM Competency competency
                LEFT JOIN competency.userProgress progress
                    ON competency.id = progress.competency.id AND progress.user.id = :userId
                LEFT JOIN FETCH competency.exercises
                LEFT JOIN FETCH competency.lectureUnits lectureUnits
                LEFT JOIN lectureUnits.completedUsers completedUsers
                    ON lectureUnits.id = completedUsers.lectureUnit.id AND completedUsers.user.id = :userId
                LEFT JOIN FETCH lectureUnits.lecture
            WHERE competency.id = :competencyId
            """)
    @EntityGraph(type = LOAD, attributePaths = { "userProgress", "lectureUnits.completedUsers" })
    Optional<Competency> findByIdWithExercisesAndLectureUnitsAndProgressForUser(@Param("competencyId") long competencyId, @Param("userId") long userId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndCompletions(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsAndCompletions(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.consecutiveCourses
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithConsecutiveCourses(@Param("competencyId") long competencyId);

    @Query("""
            SELECT pr
            FROM Competency pr
                LEFT JOIN pr.consecutiveCourses c
            WHERE c.id = :courseId
            ORDER BY pr.title
            """)
    Set<Competency> findPrerequisitesByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT COUNT(*)
            FROM Competency pr
                LEFT JOIN pr.consecutiveCourses c
            WHERE c.id = :courseId
            """)
    Long countPrerequisitesByCourseId(@Param("courseId") long courseId);

    /**
     * Query which fetches all competencies for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
                AND (c.title LIKE %:partialTitle% OR c.course.title LIKE %:partialCourseTitle%)
            """)
    Page<Competency> findByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Query which fetches all competencies for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialDescription competency description search term
     * @param partialCourseTitle course title search term
     * @param semester           semester search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
                AND (:partialTitle IS NULL OR c.title LIKE %:partialTitle%)
                AND (:partialDescription IS NULL OR c.description LIKE %:partialDescription%)
                AND (:partialCourseTitle IS NULL OR c.course.title LIKE %:partialCourseTitle%)
                AND (:semester IS NULL OR c.course.semester = :semester)
            """)
    Page<Competency> findForImportAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialDescription") String partialDescription,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("semester") String semester, @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Query which fetches all competencies matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialDescription competency description search term
     * @param partialCourseTitle course title search term
     * @param semester           semester search term
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (:partialTitle IS NULL OR c.title LIKE %:partialTitle%)
                AND (:partialDescription IS NULL OR c.description LIKE %:partialDescription%)
                AND (:partialCourseTitle IS NULL OR c.course.title LIKE %:partialCourseTitle%)
                AND (:semester IS NULL OR c.course.semester = :semester)
            """)
    Page<Competency> findForImport(@Param("partialTitle") String partialTitle, @Param("partialDescription") String partialDescription,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("semester") String semester, Pageable pageable);

    /**
     * Returns the title of the competency with the given id.
     *
     * @param competencyId the id of the competency
     * @return the name/title of the competency or null if the competency does not exist
     */
    @Query("""
            SELECT c.title
            FROM Competency c
            WHERE c.id = :competencyId
            """)
    @Cacheable(cacheNames = "competencyTitle", key = "#competencyId", unless = "#result == null")
    String getCompetencyTitle(@Param("competencyId") long competencyId);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<Competency> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default Competency findByIdWithLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithConsecutiveCoursesElseThrow(long competencyId) {
        return findByIdWithConsecutiveCourses(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdElseThrow(long competencyId) {
        return findById(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return findByIdWithLectureUnits(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsAndProgressForUserElseThrow(long competencyId, long userId) {
        return findByIdWithExercisesAndLectureUnitsAndProgressForUser(competencyId, userId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    long countByCourse(Course course);

    /**
     * Gets all competencies for the given course with the progress of the specified user.
     * <p>
     * The query only fetches data related to specified user. Participations for other users are not included.
     * IMPORTANT: JPA doesn't support JOIN-FETCH-ON statements. To fetch the relevant data we utilize the entity graph annotation.
     * Moving the ON clauses to the WHERE clause would result in significantly different and faulty output.
     *
     * @param courseId the id of the course
     * @param userId   the id of the user
     * @return the competencies with the progress of the user
     */
    @Query("""
            SELECT competency
            FROM Competency competency
                LEFT JOIN competency.userProgress progress
                    ON competency.id = progress.competency.id AND progress.user.id = :userId
            WHERE competency.course.id = :courseId
            """)
    @EntityGraph(type = LOAD, attributePaths = { "userProgress" })
    List<Competency> findByCourseIdWithProgressOfUser(@Param("courseId") long courseId, @Param("userId") long userId);
}
