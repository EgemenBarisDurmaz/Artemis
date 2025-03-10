import { AfterViewInit, Component, Input, OnChanges, OnDestroy, OnInit, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { faCogs, faUserCheck, faUserSlash } from '@fortawesome/free-solid-svg-icons';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { IncludedInOverallScore } from 'app/entities/exercise.model';
import { Observable, Subject, Subscription } from 'rxjs';
import { AthenaService } from 'app/assessment/athena.service';
import { ProgrammingExerciseTestScheduleDatePickerComponent } from 'app/exercises/programming/shared/lifecycle/programming-exercise-test-schedule-date-picker.component';
import { every } from 'lodash-es';

@Component({
    selector: 'jhi-programming-exercise-lifecycle',
    templateUrl: './programming-exercise-lifecycle.component.html',
    styleUrls: ['./programming-exercise-test-schedule-picker.scss'],
})
export class ProgrammingExerciseLifecycleComponent implements AfterViewInit, OnDestroy, OnInit, OnChanges {
    @Input() exercise: ProgrammingExercise;
    @Input() isExamMode: boolean;
    @Input() readOnly: boolean;

    @ViewChildren(ProgrammingExerciseTestScheduleDatePickerComponent) datePickerComponents: QueryList<ProgrammingExerciseTestScheduleDatePickerComponent>;

    readonly assessmentType = AssessmentType;
    readonly IncludedInOverallScore = IncludedInOverallScore;

    formValid: boolean;
    formEmpty: boolean;
    formValidChanges = new Subject<boolean>();

    inputfieldSubscriptions: (Subscription | undefined)[] = [];
    datePickerChildrenSubscription?: Subscription;

    // Icons
    faCogs = faCogs;
    faUserCheck = faUserCheck;
    faUserSlash = faUserSlash;

    isAthenaEnabled$: Observable<boolean> | undefined;

    constructor(
        private translateService: TranslateService,
        private exerciseService: ExerciseService,
        private athenaService: AthenaService,
    ) {}

    /**
     * If the programming exercise does not have an id, set the assessment Type to AUTOMATIC
     */
    ngOnInit(): void {
        if (!this.exercise.id) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
        }
        this.isAthenaEnabled$ = this.athenaService.isEnabled();
    }

    ngAfterViewInit() {
        this.setupDateFieldSubscriptions();
        this.datePickerChildrenSubscription = this.datePickerComponents.changes.subscribe(() => this.setupDateFieldSubscriptions());
    }

    ngOnChanges(simpleChanges: SimpleChanges) {
        if (simpleChanges.exercise) {
            const newExercise = simpleChanges.exercise.currentValue;
            if (this.exerciseService.hasDueDateError(newExercise)) {
                // Checking for due date errors and ordering the calls to avoid updating exampleSolutionPublicationDate twice.
                this.updateReleaseDate(newExercise.releaseDate);
                this.updateExampleSolutionPublicationDate(newExercise.dueDate);
            } else {
                this.updateExampleSolutionPublicationDate(newExercise.dueDate);
                this.updateReleaseDate(newExercise.releaseDate);
            }
        }
    }

    ngOnDestroy() {
        this.datePickerChildrenSubscription?.unsubscribe();
        this.unsubscribeDateFieldSubscriptions();
    }

    calculateFormStatus() {
        const datePickers = this.datePickerComponents.toArray();
        this.formValid = every(datePickers, (picker) => picker.dateInput.valid);
        this.formEmpty = !every(datePickers, (picker) => picker.selectedDate);
        this.formValidChanges.next(this.formValid);
    }

    setupDateFieldSubscriptions() {
        this.unsubscribeDateFieldSubscriptions();
        this.datePickerComponents
            .toArray()
            .forEach((picker) => this.inputfieldSubscriptions.push(picker.dateInput?.valueChanges?.subscribe(() => setTimeout(() => this.calculateFormStatus()))));
    }

    unsubscribeDateFieldSubscriptions() {
        for (const subscription of this.inputfieldSubscriptions) {
            subscription?.unsubscribe();
        }
    }

    toggleManualFeedbackRequests() {
        this.exercise.allowManualFeedbackRequests = !this.exercise.allowManualFeedbackRequests;
        if (this.exercise.allowManualFeedbackRequests) {
            this.exercise.assessmentDueDate = undefined;
            this.exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        }
    }

    /**
     * Toggles the assessment type between AUTOMATIC (only tests in repo will be run using build plans) and
     * SEMI_AUTOMATIC (After all automatic tests have been run, the tutors will have to make a final manual assessment)
     *
     */
    toggleAssessmentType() {
        if (this.exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            this.exercise.assessmentType = AssessmentType.AUTOMATIC;
            this.exercise.assessmentDueDate = undefined;
            this.exercise.allowComplaintsForAutomaticAssessments = false;
            this.exercise.feedbackSuggestionsEnabled = false;
        } else {
            this.exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            this.exercise.allowComplaintsForAutomaticAssessments = false;
            this.exercise.allowManualFeedbackRequests = false;
        }
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleComplaintsType() {
        this.exercise.allowComplaintsForAutomaticAssessments = !this.exercise.allowComplaintsForAutomaticAssessments;
    }

    /**
     * Toggles the value for allowing complaints for automatic assessment between true and false
     */
    toggleReleaseTests() {
        this.exercise.releaseTestsWithExampleSolution = !this.exercise.releaseTestsWithExampleSolution;
    }

    /**
     * Sets the new release date and updates "start date", "due date" and "after due date" if the release date is after them
     * Does not propagate changes to dates other than release date if readOnly is true.
     *
     * @param newReleaseDate The new release date
     */
    updateReleaseDate(newReleaseDate?: dayjs.Dayjs) {
        this.exercise.releaseDate = newReleaseDate;
        if (this.readOnly) {
            // Changes from parent component are allowed but no cascading changes should be made in read-only mode.
            return;
        }
        if (this.exerciseService.hasStartDateError(this.exercise)) {
            this.updateStartDate(newReleaseDate);
            // Will handle due date and example solution
            return;
        }
        const safeStartOrReleaseDate = this.exercise.startDate ?? newReleaseDate;
        if (this.exerciseService.hasDueDateError(this.exercise) && safeStartOrReleaseDate) {
            this.updateDueDate(safeStartOrReleaseDate);
        }
        this.updateExampleSolutionPublicationDate(safeStartOrReleaseDate);
    }

    /**
     * Sets the new start date and updates "due date" and "after due date" if the start date is after the due date
     * Does not propagate changes to dates other than start date if readOnly is true.
     *
     * @param newStartDate The new start date
     */
    updateStartDate(newStartDate?: dayjs.Dayjs) {
        this.exercise.startDate = newStartDate;
        if (this.readOnly) {
            // Changes from parent component are allowed but no cascading changes should be made in read-only mode.
            return;
        }
        if (this.exerciseService.hasDueDateError(this.exercise)) {
            this.updateDueDate(newStartDate!);
        }
        this.updateExampleSolutionPublicationDate(newStartDate);
    }

    /**
     * Updates the due Date of the programming exercise
     * @param dueDate the new dueDate
     */
    private updateDueDate(dueDate: dayjs.Dayjs) {
        alert(this.translateService.instant('artemisApp.programmingExercise.timeline.alertNewDueDate'));
        this.exercise.dueDate = dueDate;

        // If the new due date is after the "After Due Date", then we have to set the "After Due Date" to the new due date
        const afterDue = this.exercise.buildAndTestStudentSubmissionsAfterDueDate;
        if (afterDue && dueDate.isAfter(afterDue)) {
            this.exercise.buildAndTestStudentSubmissionsAfterDueDate = dueDate;
            alert(this.translateService.instant('artemisApp.programmingExercise.timeline.alertNewAfterDueDate'));
        }
    }

    /**
     * Updates the example solution publication date of the programming exercise if it is set and not after release or due date.
     * Due date check is not performed if exercise is not included in the grade.
     * This method is a no-op if readOnly is true.
     *
     * @param newReleaseOrDueDate the new exampleSolutionPublicationDate if it is after the current exampleSolutionPublicationDate
     */
    updateExampleSolutionPublicationDate(newReleaseOrDueDate?: dayjs.Dayjs) {
        if (!this.readOnly && this.exerciseService.hasExampleSolutionPublicationDateError(this.exercise)) {
            const message =
                newReleaseOrDueDate && dayjs(newReleaseOrDueDate).isSame(this.exercise.dueDate)
                    ? 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsDueDate'
                    : 'artemisApp.programmingExercise.timeline.alertNewExampleSolutionPublicationDateAsReleaseDate';
            alert(this.translateService.instant(message));
            this.exercise.exampleSolutionPublicationDate = newReleaseOrDueDate;
            if (!newReleaseOrDueDate) {
                this.exercise.releaseTestsWithExampleSolution = false;
            }
        }
    }
}
