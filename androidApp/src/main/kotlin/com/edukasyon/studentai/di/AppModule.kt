package com.edukasyon.studentai.di

import android.content.Context
import androidx.room.Room
import com.edukasyon.studentai.BuildConfig
import com.edukasyon.studentai.core.ai.AiService
import com.edukasyon.studentai.core.ai.AiServiceProvider
import com.edukasyon.studentai.core.network.AiApiService
import com.edukasyon.studentai.core.network.HolidayApi
import com.edukasyon.studentai.data.local.StudentAiDatabase
import com.edukasyon.studentai.data.local.MIGRATION_1_2
import com.edukasyon.studentai.data.local.MIGRATION_2_3
import com.edukasyon.studentai.data.local.MIGRATION_3_4
import com.edukasyon.studentai.data.local.MIGRATION_4_5
import com.edukasyon.studentai.data.local.MIGRATION_5_6
import com.edukasyon.studentai.data.local.MIGRATION_6_7
import com.edukasyon.studentai.data.local.MIGRATION_7_8
import com.edukasyon.studentai.data.local.MIGRATION_8_9
import com.edukasyon.studentai.data.local.MIGRATION_9_10
import com.edukasyon.studentai.data.repository.AiConversationRepositoryImpl
import com.edukasyon.studentai.data.repository.AssignmentRepositoryImpl
import com.edukasyon.studentai.data.repository.CalendarRepositoryImpl
import com.edukasyon.studentai.data.repository.ExamRepositoryImpl
import com.edukasyon.studentai.data.repository.FlashcardRepositoryImpl
import com.edukasyon.studentai.data.repository.GradeRepositoryImpl
import com.edukasyon.studentai.data.repository.JeviRepositoryImpl
import com.edukasyon.studentai.data.repository.LectureFileRepositoryImpl
import com.edukasyon.studentai.data.repository.NoteRepositoryImpl
import com.edukasyon.studentai.data.repository.QuizRepositoryImpl
import com.edukasyon.studentai.data.repository.ScheduleRepositoryImpl
import com.edukasyon.studentai.data.repository.SearchRepositoryImpl
import com.edukasyon.studentai.data.repository.SubjectRepositoryImpl
import com.edukasyon.studentai.data.repository.TaskRepositoryImpl
import com.edukasyon.studentai.data.repository.UserRepositoryImpl
import com.edukasyon.studentai.domain.repository.AiConversationRepository
import com.edukasyon.studentai.domain.repository.AssignmentRepository
import com.edukasyon.studentai.domain.repository.CalendarRepository
import com.edukasyon.studentai.domain.repository.ExamRepository
import com.edukasyon.studentai.domain.repository.FlashcardRepository
import com.edukasyon.studentai.domain.repository.GradeRepository
import com.edukasyon.studentai.domain.repository.JeviRepository
import com.edukasyon.studentai.domain.repository.LectureFileRepository
import com.edukasyon.studentai.domain.repository.NoteRepository
import com.edukasyon.studentai.domain.repository.QuizRepository
import com.edukasyon.studentai.domain.repository.ScheduleRepository
import com.edukasyon.studentai.domain.repository.SearchRepository
import com.edukasyon.studentai.domain.repository.SubjectRepository
import com.edukasyon.studentai.domain.repository.TaskRepository
import com.edukasyon.studentai.domain.repository.UserRepository
import com.edukasyon.studentai.domain.usecase.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StudentAiDatabase {
        val builder = Room.databaseBuilder(context, StudentAiDatabase::class.java, "studentai.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
            )
        if (BuildConfig.DEBUG) {
            // Recover from schema validation failures during development without manual app-data clears.
            builder.fallbackToDestructiveMigration()
        }
        return builder.build()
    }

    @Provides fun provideUserDao(db: StudentAiDatabase) = db.userDao()
    @Provides fun provideSubjectDao(db: StudentAiDatabase) = db.subjectDao()
    @Provides fun provideScheduleDao(db: StudentAiDatabase) = db.scheduleDao()
    @Provides fun provideTaskDao(db: StudentAiDatabase) = db.taskDao()
    @Provides fun provideSubtaskDao(db: StudentAiDatabase) = db.subtaskDao()
    @Provides fun provideAssignmentDao(db: StudentAiDatabase) = db.assignmentDao()
    @Provides fun provideExamDao(db: StudentAiDatabase) = db.examDao()
    @Provides fun provideNoteDao(db: StudentAiDatabase) = db.noteDao()
    @Provides fun provideNoteTagDao(db: StudentAiDatabase) = db.noteTagDao()
    @Provides fun provideFlashcardDao(db: StudentAiDatabase) = db.flashcardDao()
    @Provides fun provideJeviDeckDao(db: StudentAiDatabase) = db.jeviDeckDao()
    @Provides fun provideJeviReviewRecordDao(db: StudentAiDatabase) = db.jeviReviewRecordDao()
    @Provides fun provideQuizDao(db: StudentAiDatabase) = db.quizDao()
    @Provides fun provideQuizQuestionDao(db: StudentAiDatabase) = db.quizQuestionDao()
    @Provides fun provideStudySessionDao(db: StudentAiDatabase) = db.studySessionDao()
    @Provides fun provideStudyPlanDao(db: StudentAiDatabase) = db.studyPlanDao()
    @Provides fun provideStudyPlanItemDao(db: StudentAiDatabase) = db.studyPlanItemDao()
    @Provides fun provideCalendarEventDao(db: StudentAiDatabase) = db.calendarEventDao()
    @Provides fun provideGradeEntryDao(db: StudentAiDatabase) = db.gradeEntryDao()
    @Provides fun provideSyncMetadataDao(db: StudentAiDatabase) = db.syncMetadataDao()
    @Provides fun provideAiConversationDao(db: StudentAiDatabase) = db.aiConversationDao()
    @Provides fun provideCachedHolidayDao(db: StudentAiDatabase) = db.cachedHolidayDao()
    @Provides fun provideLectureFileDao(db: StudentAiDatabase) = db.lectureFileDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    @Provides @Singleton
    fun provideOkHttp(deviceIdInterceptor: com.edukasyon.studentai.core.network.AiSafetyHeadersInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // The schedule-analysis endpoint can take 30–60s on the vision model
        // even after a small image. The backend's SAFETY_REQUEST_TIMEOUT_MS
        // defaults to 90s, so the client must wait at least that long or
        // the read fails with SocketTimeoutException mid-response.
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .addInterceptor(deviceIdInterceptor)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }.build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.AI_BACKEND_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides @Singleton
    fun provideAiApi(retrofit: Retrofit): AiApiService = retrofit.create(AiApiService::class.java)

    @Provides @Singleton
    fun provideHolidayApi(client: OkHttpClient, json: Json): HolidayApi {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://date.nager.at/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(HolidayApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindUserRepo(impl: UserRepositoryImpl): UserRepository
    @Binds @Singleton abstract fun bindScheduleRepo(impl: ScheduleRepositoryImpl): ScheduleRepository
    @Binds @Singleton abstract fun bindTaskRepo(impl: TaskRepositoryImpl): TaskRepository
    @Binds @Singleton abstract fun bindAssignmentRepo(impl: AssignmentRepositoryImpl): AssignmentRepository
    @Binds @Singleton abstract fun bindExamRepo(impl: ExamRepositoryImpl): ExamRepository
    @Binds @Singleton abstract fun bindNoteRepo(impl: NoteRepositoryImpl): NoteRepository
    @Binds @Singleton abstract fun bindGradeRepo(impl: GradeRepositoryImpl): GradeRepository
    @Binds @Singleton abstract fun bindSubjectRepo(impl: SubjectRepositoryImpl): SubjectRepository
    @Binds @Singleton abstract fun bindCalendarRepo(impl: CalendarRepositoryImpl): CalendarRepository
    @Binds @Singleton abstract fun bindFlashcardRepo(impl: FlashcardRepositoryImpl): FlashcardRepository
    @Binds @Singleton abstract fun bindJeviRepo(impl: JeviRepositoryImpl): JeviRepository
    @Binds @Singleton abstract fun bindQuizRepo(impl: QuizRepositoryImpl): QuizRepository
    @Binds @Singleton abstract fun bindAiConversationRepo(impl: AiConversationRepositoryImpl): AiConversationRepository
    @Binds @Singleton abstract fun bindSearchRepo(impl: SearchRepositoryImpl): SearchRepository
    @Binds @Singleton abstract fun bindLectureFileRepo(impl: LectureFileRepositoryImpl): LectureFileRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds @Singleton abstract fun bindAiService(impl: AiServiceProvider): AiService
}

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides @Singleton
    fun provideGetTodayScheduleUseCase(repo: ScheduleRepository) =
        GetTodayScheduleUseCase(repo)

    @Provides @Singleton
    fun provideGetAllScheduleUseCase(repo: ScheduleRepository) =
        GetAllScheduleUseCase(repo)

    @Provides @Singleton
    fun provideGetScheduleByDayUseCase(repo: ScheduleRepository) =
        GetScheduleByDayUseCase(repo)

    @Provides @Singleton
    fun provideAddScheduleItemUseCase(repo: ScheduleRepository) =
        AddScheduleItemUseCase(repo)

    @Provides @Singleton
    fun provideUpdateScheduleItemUseCase(repo: ScheduleRepository) =
        UpdateScheduleItemUseCase(repo)

    @Provides @Singleton
    fun provideDeleteScheduleItemUseCase(repo: ScheduleRepository) =
        DeleteScheduleItemUseCase(repo)

    @Provides @Singleton
    fun provideSearchScheduleUseCase(repo: ScheduleRepository) =
        SearchScheduleUseCase(repo)

    @Provides @Singleton
    fun provideGetAllTasksUseCase(repo: TaskRepository) =
        GetAllTasksUseCase(repo)

    @Provides @Singleton
    fun provideGetUpcomingTasksUseCase(repo: TaskRepository) =
        GetUpcomingTasksUseCase(repo)

    @Provides @Singleton
    fun provideCreateTaskUseCase(repo: TaskRepository) =
        CreateTaskUseCase(repo)

    @Provides @Singleton
    fun provideUpdateTaskUseCase(repo: TaskRepository) =
        UpdateTaskUseCase(repo)

    @Provides @Singleton
    fun provideCompleteTaskUseCase(repo: TaskRepository) =
        CompleteTaskUseCase(repo)

    @Provides @Singleton
    fun provideUncompleteTaskUseCase(repo: TaskRepository) =
        UncompleteTaskUseCase(repo)

    @Provides @Singleton
    fun provideInsertSubtaskUseCase(repo: TaskRepository) =
        InsertSubtaskUseCase(repo)

    @Provides @Singleton
    fun provideUpdateSubtaskUseCase(repo: TaskRepository) =
        UpdateSubtaskUseCase(repo)

    @Provides @Singleton
    fun provideDeleteSubtaskUseCase(repo: TaskRepository) =
        DeleteSubtaskUseCase(repo)

    @Provides @Singleton
    fun provideDeleteTaskUseCase(repo: TaskRepository) =
        DeleteTaskUseCase(repo)

    @Provides @Singleton
    fun provideSearchTasksUseCase(repo: TaskRepository) =
        SearchTasksUseCase(repo)

    @Provides @Singleton
    fun provideGetAllAssignmentsUseCase(repo: AssignmentRepository) =
        GetAllAssignmentsUseCase(repo)

    @Provides @Singleton
    fun provideSaveAssignmentUseCase(repo: AssignmentRepository) =
        SaveAssignmentUseCase(repo)

    @Provides @Singleton
    fun provideDeleteAssignmentUseCase(repo: AssignmentRepository) =
        DeleteAssignmentUseCase(repo)

    @Provides @Singleton
    fun provideGetAllExamsUseCase(repo: ExamRepository) =
        GetAllExamsUseCase(repo)

    @Provides @Singleton
    fun provideGetUpcomingExamsUseCase(repo: ExamRepository) =
        GetUpcomingExamsUseCase(repo)

    @Provides @Singleton
    fun provideSaveExamUseCase(repo: ExamRepository) =
        SaveExamUseCase(repo)

    @Provides @Singleton
    fun provideDeleteExamUseCase(repo: ExamRepository) =
        DeleteExamUseCase(repo)

    @Provides @Singleton
    fun provideDuplicateExamUseCase(repo: ExamRepository) =
        DuplicateExamUseCase(repo)

    @Provides @Singleton
    fun provideGetAllNotesUseCase(repo: NoteRepository) =
        GetAllNotesUseCase(repo)

    @Provides @Singleton
    fun provideSaveNoteUseCase(repo: NoteRepository) =
        SaveNoteUseCase(repo)

    @Provides @Singleton
    fun provideDeleteNoteUseCase(repo: NoteRepository) =
        DeleteNoteUseCase(repo)

    @Provides @Singleton
    fun provideSearchNotesUseCase(repo: NoteRepository) =
        SearchNotesUseCase(repo)

    @Provides @Singleton
    fun provideGetGradesUseCase(repo: GradeRepository) =
        GetGradesUseCase(repo)

    @Provides @Singleton
    fun provideSaveGradeUseCase(repo: GradeRepository) =
        SaveGradeUseCase(repo)

    @Provides @Singleton
    fun provideDeleteGradeUseCase(repo: GradeRepository) =
        DeleteGradeUseCase(repo)

    @Provides @Singleton
    fun provideCalculateWeightedGradeUseCase(repo: GradeRepository) =
        CalculateWeightedGradeUseCase(repo)

    @Provides @Singleton
    fun provideGetAllSubjectsUseCase(repo: SubjectRepository) =
        GetAllSubjectsUseCase(repo)

    @Provides @Singleton
    fun provideSaveSubjectUseCase(repo: SubjectRepository) =
        SaveSubjectUseCase(repo)

    @Provides @Singleton
    fun provideGetCalendarEventsUseCase(repo: CalendarRepository) =
        GetCalendarEventsUseCase(repo)

    @Provides @Singleton
    fun provideSaveCalendarEventUseCase(repo: CalendarRepository) =
        SaveCalendarEventUseCase(repo)

    @Provides @Singleton
    fun provideGetFlashcardsUseCase(repo: FlashcardRepository) =
        GetFlashcardsUseCase(repo)

    @Provides @Singleton
    fun provideSaveFlashcardsUseCase(repo: FlashcardRepository) =
        SaveFlashcardsUseCase(repo)

    @Provides @Singleton
    fun provideUpdateFlashcardUseCase(repo: FlashcardRepository) =
        UpdateFlashcardUseCase(repo)

    @Provides @Singleton
    fun provideSaveQuizUseCase(repo: QuizRepository) =
        SaveQuizUseCase(repo)

    @Provides @Singleton
    fun provideObserveQuizzesUseCase(repo: QuizRepository) =
        ObserveQuizzesUseCase(repo)

    @Provides @Singleton
    fun provideGetQuizUseCase(repo: QuizRepository) =
        GetQuizUseCase(repo)

    @Provides @Singleton
    fun provideAiChatUseCase(ai: AiService) =
        AiChatUseCase(ai)

    @Provides @Singleton
    fun provideAiSummarizeUseCase(ai: AiService) =
        AiSummarizeUseCase(ai)

    @Provides @Singleton
    fun provideAiGenerateFlashcardsUseCase(ai: AiService) =
        AiGenerateFlashcardsUseCase(ai)

    @Provides @Singleton
    fun provideAiGenerateQuizUseCase(ai: AiService) =
        AiGenerateQuizUseCase(ai)

    @Provides @Singleton
    fun provideAiGenerateStudyPlanUseCase(ai: AiService) =
        AiGenerateStudyPlanUseCase(ai)

    @Provides @Singleton
    fun provideAiAnalyzeScheduleUseCase(ai: AiService) =
        AiAnalyzeScheduleUseCase(ai)

    @Provides @Singleton
    fun provideAiAnalyzeAssignmentUseCase(ai: AiService) =
        AiAnalyzeAssignmentUseCase(ai)

    @Provides @Singleton
    fun provideSaveAssignmentBreakdownToPlannerUseCase(repo: TaskRepository) =
        SaveAssignmentBreakdownToPlannerUseCase(repo)

    @Provides @Singleton
    fun provideGlobalSearchUseCase(repo: SearchRepository) =
        GlobalSearchUseCase(repo)

    @Provides @Singleton
    fun provideGetUserUseCase(repo: UserRepository) =
        GetUserUseCase(repo)

    @Provides @Singleton
    fun provideSaveUserUseCase(repo: UserRepository) =
        SaveUserUseCase(repo)
}
