package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.exam.ExamQuizQuestionsGenerator;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the StudentExam entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface StudentExamRepository extends JpaRepository<StudentExam, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Optional<StudentExam> findWithExercisesById(Long studentExamId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.submissionPolicy
                LEFT JOIN FETCH se.examSessions
            WHERE se.id = :studentExamId
            """)
    Optional<StudentExam> findWithExercisesSubmissionPolicyAndSessionsById(@Param("studentExamId") long studentExamId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.testRun = :isTestRun
                AND se.exam.id = :examId
                AND se.user.id = :userId
            """)
    Optional<StudentExam> findWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId, @Param("isTestRun") boolean isTestRun);

    // Normally, there should only be one student exam for the same user/exam pair (except test runs for instructors)
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.testRun IS FALSE
                AND se.exam.id = :examId
                AND se.user.id = :userId
            """)
    List<StudentExam> findAllWithExercisesByUserIdAndExamId(@Param("userId") long userId, @Param("examId") long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.studentParticipations sp
                LEFT JOIN FETCH sp.submissions s
            WHERE se.id = :studentExamId
            	AND se.testRun = :isTestRun
            """)
    Optional<StudentExam> findWithExercisesParticipationsSubmissionsById(@Param("studentExamId") long studentExamId, @Param("isTestRun") boolean isTestRun);

    @Query("""
            SELECT se
            FROM StudentExam se
            WHERE se.exam.id = :examId
                AND se.testRun IS FALSE
            """)
    Set<StudentExam> findByExamId(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(DISTINCT se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
                AND se.testRun IS FALSE
            """)
    long countByExamId(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.examSessions
            WHERE se.exam.id = :examId
            	AND se.testRun IS FALSE
            """)
    Set<StudentExam> findByExamIdWithSessions(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises
            WHERE se.exam.id = :examId
            """)
    Set<StudentExam> findAllWithExercisesByExamId(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.exam.id = :examId
            	AND se.testRun IS FALSE
            """)
    Set<StudentExam> findAllWithoutTestRunsWithExercisesByExamId(@Param("examId") long examId);

    @Query("""
            SELECT se
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.testRun IS TRUE
            """)
    List<StudentExam> findAllTestRunsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.testRun IS TRUE
            """)
    long countTestRunsByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.started IS TRUE
            	AND se.testRun IS FALSE
            """)
    long countStudentExamsStartedByExamIdIgnoreTestRuns(@Param("examId") Long examId);

    @Query("""
            SELECT COUNT(se)
            FROM StudentExam se
            WHERE se.exam.id = :examId
            	AND se.submitted IS TRUE
            	AND se.testRun IS FALSE
            """)
    long countStudentExamsSubmittedByExamIdIgnoreTestRuns(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.studentParticipations sp
                LEFT JOIN FETCH sp.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor a
            WHERE se.exam.id = :examId
            	AND se.testRun IS TRUE
            	AND se.user.id = sp.student.id
            """)
    List<StudentExam> findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(@Param("examId") Long examId);

    /**
     * It might happen that multiple test exams exist for a combination of userId/examId, that's why we return a set here.
     *
     * @param userId the id of the user
     * @return all student exams for the given user
     */
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exam exam
                LEFT JOIN FETCH se.exercises e
                LEFT JOIN FETCH e.submissionPolicy spo
                LEFT JOIN FETCH e.studentParticipations sp
                LEFT JOIN FETCH sp.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE se.user.id = sp.student.id
                  AND se.user.id = :userId
            """)
    Set<StudentExam> findAllWithExercisesSubmissionPolicyParticipationsSubmissionsResultsAndFeedbacksByUserId(@Param("userId") long userId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.exam.id = :examId
            	AND se.testRun IS TRUE
            	AND se.user.id = :userId
            """)
    List<StudentExam> findAllTestRunsWithExercisesByExamIdForUser(@Param("examId") Long examId, @Param("userId") Long userId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
            WHERE se.testRun IS FALSE
            	AND se.exam.id = :examId
            	AND se.user.id = :userId
            """)
    Optional<StudentExam> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    /**
     * Checks if any StudentExam exists for the given user (student) id in the given course.
     *
     * @param courseId the id of the course which should have the exam.
     * @param examId   the id of the exam
     * @param userId   the id of the user (student) who may or may not have a StudentExam
     * @return True if the given user id has a matching StudentExam in the given exam and course, else false.
     */
    boolean existsByExam_CourseIdAndExamIdAndUserId(long courseId, long examId, long userId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.testRun IS FALSE
            	AND e.id = :exerciseId
            	AND se.user.id = :userId
            """)
    Optional<StudentExam> findByExerciseIdAndUserId(@Param("exerciseId") Long exerciseId, @Param("userId") Long userId);

    @Query("""
            SELECT MAX(se.workingTime)
            FROM StudentExam se
            WHERE se.testRun IS FALSE
            	AND se.exam.id = :examId
            """)
    Optional<Integer> findMaxWorkingTimeByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se.workingTime
            FROM StudentExam se
            WHERE se.testRun IS FALSE
            	AND se.exam.id = :examId
            """)
    Set<Integer> findAllDistinctWorkingTimesByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT u
            FROM StudentExam se
                LEFT JOIN se.user u
            WHERE se.testRun IS FALSE
            	AND se.exam.id = :examId
            """)
    Set<User> findUsersWithStudentExamsForExam(@Param("examId") Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises
            WHERE se.exam.id = :examId
            	AND se.submitted IS FALSE
            	AND se.testRun IS FALSE
            """)
    Set<StudentExam> findAllUnsubmittedWithExercisesByExamId(@Param("examId") Long examId);

    List<StudentExam> findAllByExamId_AndTestRunIsTrue(Long examId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
            WHERE se.user.id = :userId
                AND se.exam.course.id = :courseId
                AND se.exam.testExam IS TRUE
                AND se.testRun IS FALSE
            """)
    List<StudentExam> findStudentExamForTestExamsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises exercises
            WHERE se.exam.id = :examId
                AND se.user.id = :userId
                AND se.submitted IS FALSE
                AND se.testRun IS FALSE
                AND se.exam.testExam IS TRUE
            """)
    List<StudentExam> findUnsubmittedStudentExamsForTestExamsWithExercisesByExamIdAndUserId(@Param("examId") Long examId, @Param("userId") Long userId);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE StudentExam s
            SET s.submitted = TRUE,
                s.submissionDate = :submissionDate
            WHERE s.id = :studentExamId
            """)
    void submitStudentExam(@Param("studentExamId") Long studentExamId, @Param("submissionDate") ZonedDateTime submissionDate);

    @Modifying
    @Transactional // ok because of modifying query
    @Query("""
            UPDATE StudentExam s
            SET s.started = TRUE,
                s.startedDate = :startedDate
            WHERE s.id = :studentExamId
            """)
    void startStudentExam(@Param("studentExamId") Long studentExamId, @Param("startedDate") ZonedDateTime startedDate);

    @NotNull
    default StudentExam findByIdElseThrow(Long studentExamId) throws EntityNotFoundException {
        return findById(studentExamId).orElseThrow(() -> new EntityNotFoundException("Student Exam", studentExamId));
    }

    /**
     * Return the StudentExam of the participation's user, if possible
     *
     * @param exercise      that is possibly part of an exam
     * @param participation the participation of the student
     * @return an optional StudentExam, which is empty if the exercise is not part of an exam or the student exam hasn't been created
     */
    default Optional<StudentExam> findStudentExam(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var examUser = participation.getStudent().orElseThrow(() -> new EntityNotFoundException("Exam Participation with " + participation.getId() + " has no student!"));
            return findByExerciseIdAndUserId(exercise.getId(), examUser.getId());
        }
        return Optional.empty();
    }

    /**
     * Return the individual due date for the exercise of the participation's user
     * <p>
     * For exam exercises, this depends on the StudentExam's working time
     *
     * @param exercise      that is possibly part of an exam
     * @param participation the participation of the student
     * @return the time from which on submissions are not allowed, for exercises that are not part of an exam, this is just the due date.
     */
    @Nullable
    default ZonedDateTime getIndividualDueDate(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            var studentExam = findStudentExam(exercise, participation).orElse(null);
            if (studentExam == null) {
                return exercise.getDueDate();
            }
            return studentExam.getExam().getStartDate().plusSeconds(studentExam.getWorkingTime());
        }
        else {
            return ExerciseDateService.getDueDate(participation).orElse(null);
        }
    }

    /**
     * Get one student exam by id with exercises.
     *
     * @param studentExamId the id of the student exam
     * @return the student exam with exercises
     */
    @NotNull
    default StudentExam findByIdWithExercisesElseThrow(Long studentExamId) {
        return findWithExercisesById(studentExamId).orElseThrow(() -> new EntityNotFoundException("Student exam", studentExamId));
    }

    /**
     * Get one student exam by id with exercises, programming exercise submission policy and sessions
     *
     * @param studentExamId the id of the student exam
     * @return the student exam with exercises
     */
    @NotNull
    default StudentExam findByIdWithExercisesSubmissionPolicyAndSessionsElseThrow(Long studentExamId) {
        return findWithExercisesSubmissionPolicyAndSessionsById(studentExamId).orElseThrow(() -> new EntityNotFoundException("Student exam", studentExamId));
    }

    /**
     * Get the maximal working time of all student exams for the exam with the given id.
     *
     * @param examId the id of the exam
     * @return the maximum of all student exam working times for the given exam
     * @throws EntityNotFoundException if no student exams could be found
     */
    @NotNull
    default Integer findMaxWorkingTimeByExamIdElseThrow(Long examId) {
        return findMaxWorkingTimeByExamId(examId).orElseThrow(() -> new EntityNotFoundException("No student exams found for exam id " + examId));
    }

    /**
     * Generates random exams for each user in the given users set and saves them.
     *
     * @param exam                       exam for which the individual student exams will be generated
     * @param users                      users for which the individual exams will be generated
     * @param examQuizQuestionsGenerator generator to generate quiz questions for the exam
     * @return List of StudentExams generated for the given users
     */
    default List<StudentExam> createRandomStudentExams(Exam exam, Set<User> users, ExamQuizQuestionsGenerator examQuizQuestionsGenerator) {
        List<StudentExam> studentExams = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        long numberOfOptionalExercises = exam.getNumberOfExercisesInExam() - exam.getExerciseGroups().stream().filter(ExerciseGroup::getIsMandatory).count();

        // Determine the default working time by computing the duration between start and end date of the exam
        int defaultWorkingTime = exam.getWorkingTime();

        // Prepare indices of mandatory and optional exercise groups to preserve order of exercise groups
        List<Integer> indicesOfMandatoryExerciseGroups = new ArrayList<>();
        List<Integer> indicesOfOptionalExerciseGroups = new ArrayList<>();
        for (int i = 0; i < exam.getExerciseGroups().size(); i++) {
            if (Boolean.TRUE.equals(exam.getExerciseGroups().get(i).getIsMandatory())) {
                indicesOfMandatoryExerciseGroups.add(i);
            }
            else {
                indicesOfOptionalExerciseGroups.add(i);
            }
        }

        for (User user : users) {
            // Create one student exam per user
            StudentExam studentExam = new StudentExam();

            studentExam.setWorkingTime(defaultWorkingTime);
            studentExam.setExam(exam);
            studentExam.setUser(user);
            studentExam.setSubmitted(false);
            studentExam.setTestRun(false);

            // Add a random exercise for each exercise group if the index of the exercise group is in assembledIndices
            List<Integer> assembledIndices = assembleIndicesListWithRandomSelection(indicesOfMandatoryExerciseGroups, indicesOfOptionalExerciseGroups, numberOfOptionalExercises);
            for (Integer index : assembledIndices) {
                // We get one random exercise from all preselected exercise groups
                studentExam.addExercise(selectRandomExercise(random, exam.getExerciseGroups().get(index)));
            }

            // Apply random exercise order
            if (Boolean.TRUE.equals(exam.getRandomizeExerciseOrder())) {
                Collections.shuffle(studentExam.getExercises());
            }
            List<QuizQuestion> quizQuestions = examQuizQuestionsGenerator.generateQuizQuestionsForExam(exam.getId());
            studentExam.setQuizQuestions(quizQuestions);

            studentExams.add(studentExam);
        }
        studentExams = saveAll(studentExams);
        return studentExams;
    }

    private List<Integer> assembleIndicesListWithRandomSelection(List<Integer> mandatoryIndices, List<Integer> optionalIndices, Long numberOfOptionalExercises) {
        // Add all mandatory indices
        List<Integer> indices = new ArrayList<>(mandatoryIndices);

        // Add as many optional indices as numberOfOptionalExercises
        if (numberOfOptionalExercises > 0) {
            Collections.shuffle(optionalIndices);
            indices.addAll(optionalIndices.stream().limit(numberOfOptionalExercises).toList());
        }

        // Sort the indices to preserve the original order
        Collections.sort(indices);
        return indices;
    }

    private Exercise selectRandomExercise(SecureRandom random, ExerciseGroup exerciseGroup) {
        List<Exercise> exercises = new ArrayList<>(exerciseGroup.getExercises());
        int randomIndex = random.nextInt(exercises.size());
        return exercises.get(randomIndex);
    }

    /**
     * Generates the student exams randomly based on the exam configuration and the exercise groups
     * Important: the passed exams needs to include the registered users, exercise groups and exercises (eagerly loaded)
     *
     * @param exam                       with eagerly loaded registered users, exerciseGroups and exercises loaded
     * @param examQuizQuestionsGenerator generator to generate quiz questions for the exam
     * @return the list of student exams with their corresponding users
     */
    default List<StudentExam> generateStudentExams(final Exam exam, ExamQuizQuestionsGenerator examQuizQuestionsGenerator) {
        final var existingStudentExams = findByExamId(exam.getId());
        // https://jira.spring.io/browse/DATAJPA-1367 deleteInBatch does not work, because it does not cascade the deletion of existing exam sessions, therefore use deleteAll
        deleteAll(existingStudentExams);

        Set<User> users = exam.getRegisteredUsers();

        // StudentExams are saved in the called method
        return createRandomStudentExams(exam, users, examQuizQuestionsGenerator);
    }

    /**
     * Get all student exams for the given exam id with quiz questions.
     *
     * @param ids the ids of the student exams
     * @return the list of student exams with quiz questions
     */
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.quizQuestions qq
            WHERE se.id IN :ids
            """)
    List<StudentExam> findAllWithEagerQuizQuestionsById(@Param("ids") List<Long> ids);

    /**
     * Get all student exams for the given exam id with exercises.
     *
     * @param ids the ids of the student exams
     * @return the list of student exams with exercises
     */
    @Query("""
            SELECT DISTINCT se
            FROM StudentExam se
                LEFT JOIN FETCH se.exercises e
            WHERE se.id IN :ids
            """)
    List<StudentExam> findAllWithEagerExercisesById(@Param("ids") List<Long> ids);
}
