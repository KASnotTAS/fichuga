package com.example.fichuga

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fichuga.data.AppDatabase
import com.example.fichuga.data.LessonSlotEntity
import com.example.fichuga.data.NotesDao
import com.example.fichuga.data.SemesterEntity
import com.example.fichuga.data.SubjectNoteEntity
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.example.fichuga.data.BellTimeEntity
import androidx.compose.runtime.toMutableStateList

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)

        setContent {
            MaterialTheme {
                ScheduleApp(database)
            }
        }
    }
}

private val weekDays = listOf(
    "Понедельник", "Вторник", "Среда",
    "Четверг", "Пятница", "Суббота"
)

private val shortDays = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ")


@Composable
fun ScheduleApp(database: AppDatabase) {
    val dao = database.notesDao()
    val bellTimes by dao.getAllBellTimes()
        .collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        dao.insertDefaultBellTimes(
            (1..7).map { number ->
                BellTimeEntity(
                    slotNumber = number,
                    startTime = "",
                    endTime = ""
                )
            }
        )
    }
    val scope = rememberCoroutineScope()

    val semesters by dao.getAllSemesters()
        .collectAsState(initial = emptyList())

    var selectedSemesterId by rememberSaveable {
        mutableStateOf<Long?>(null)
    }

    var selectedWeekStart by rememberSaveable {
        mutableStateOf(currentMondayDate())
    }

    var selectedDay by rememberSaveable { mutableIntStateOf(1) }
    var settingsWeek by rememberSaveable { mutableIntStateOf(1) }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSemesterPicker by remember { mutableStateOf(false) }
    var showCreateSemester by remember { mutableStateOf(false) }
    var semesterToDelete by remember {
        mutableStateOf<SemesterEntity?>(null)
    }
    var newSemesterName by remember { mutableStateOf("") }
    var showSubjectNotes by rememberSaveable {
        mutableStateOf(false)
    }
    var selectedSubjectName by rememberSaveable {
        mutableStateOf("")
    }

    LaunchedEffect(semesters) {
        if (selectedSemesterId == null && semesters.isNotEmpty()) {
            selectedSemesterId = semesters.first().id
        }

        if (
            selectedSemesterId != null &&
            semesters.none { it.id == selectedSemesterId }
        ) {
            selectedSemesterId = semesters.firstOrNull()?.id
        }
    }

    val selectedSemester = semesters.firstOrNull {
        it.id == selectedSemesterId
    }

    // Если у семестра ещё не указана начальная дата,
    // сохраняем понедельник текущей недели.
    LaunchedEffect(selectedSemester?.id, selectedSemester?.weekOneStartDate) {
        val semester = selectedSemester

        if (semester != null && semester.weekOneStartDate.isBlank()) {
            dao.initializeWeekOneStartDate(
                semesterId = semester.id,
                date = currentMondayDate()
            )
        }
    }

    val anchorDate = selectedSemester
        ?.weekOneStartDate
        ?.takeIf { it.isNotBlank() }
        ?: currentMondayDate()

    val templateWeek = getTemplateWeek(
        displayedMonday = selectedWeekStart,
        weekOneStart = anchorDate
    )

    val selectedDate = addDaysToDate(
        selectedWeekStart,
        selectedDay - 1
    )

    val lessonSlots by produceState(
        initialValue = emptyList<com.example.fichuga.data.LessonSlotEntity>(),
        selectedSemesterId,
        templateWeek
    ) {
        val semesterId = selectedSemesterId

        if (semesterId == null) {
            value = emptyList()
        } else {
            dao.getLessonSlots(
                semesterId = semesterId,
                weekNumber = templateWeek
            ).collect { value = it }
        }
    }

    val lessonNotes by produceState(
        initialValue = emptyList<com.example.fichuga.data.LessonNoteEntity>(),
        selectedSemesterId,
        selectedDate
    ) {
        val semesterId = selectedSemesterId

        if (semesterId == null) {
            value = emptyList()
        } else {
            dao.getLessonNotesForDate(
                semesterId = semesterId,
                date = selectedDate
            ).collect { value = it }
        }
    }

    var noteSlot by remember {
        mutableStateOf<Int?>(null)
    }

    var showLessonNote by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showSubjectNotes && selectedSemester != null) {
            SubjectNotesScreen(
                dao = dao,
                semester = selectedSemester,
                subjectName = selectedSubjectName,
                onBack = {
                    showSubjectNotes = false
                }
            )
        } else if (showSettings && selectedSemester != null) {
            ScheduleSettingsScreen(
                dao = dao,
                semester = selectedSemester,
                selectedWeek = settingsWeek,
                onWeekChange = { settingsWeek = it },
                onBack = { showSettings = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = "Моё расписание",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                // Календарная неделя и кнопки перемещения.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedWeekStart = addDaysToDate(
                                selectedWeekStart,
                                -7
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("←")
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${formatDisplayDate(selectedWeekStart)} – " +
                                    formatDisplayDate(
                                        addDaysToDate(selectedWeekStart, 6)
                                    ),
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            "Неделя $templateWeek по расписанию",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            selectedWeekStart = addDaysToDate(
                                selectedWeekStart,
                                7
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Text("→")
                    }

                    OutlinedButton(
                        onClick = {
                            settingsWeek = templateWeek
                            showSettings = true
                        },
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 12.dp
                        )
                    ) {
                        Text("⚙️", fontSize = 18.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    shortDays.forEachIndexed { index, shortDay ->
                        val dayNumber = index + 1
                        val selected = selectedDay == dayNumber

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedDay = dayNumber }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shortDay,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = weekDays[selectedDay - 1],
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = formatDisplayDate(selectedDate),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                if (selectedSemester == null) {
                    Text("Сначала создай учебный семестр.")
                } else {
                    for (number in 1..7) {
                        val slot = lessonSlots.firstOrNull {
                            it.dayOfWeek == selectedDay &&
                                    it.slotNumber == number
                        }

                        val noteExists = lessonNotes.any {
                            it.slotNumber == number
                        }

                        LessonRow(
                            number = number,
                            slot = slot,
                            timeRange = bellTimes
                                .firstOrNull { it.slotNumber == number }
                                ?.let {
                                    if (it.startTime.isBlank() && it.endTime.isBlank()) ""
                                    else "${it.startTime}–${it.endTime}"
                                }.orEmpty(),
                            noteExists = noteExists,
                            onNoteClick = {
                                noteSlot = number
                                showLessonNote = true
                            },
                            onSubjectClick = {
                                val subject = slot?.subjectName?.trim().orEmpty()

                                if (subject.isNotEmpty()) {
                                    selectedSubjectName = subject
                                    showSubjectNotes = true
                                }
                            }
                        )

                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Текущий семестр",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = { showSemesterPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        selectedSemester?.name
                            ?: "Выбрать или создать семестр"
                    )
                }
            }
        }
    }

    // Окно заметки к конкретной паре в конкретную дату.
    if (showLessonNote && noteSlot != null && selectedSemester != null) {
        val slotNumber = noteSlot!!

        val currentSlot = lessonSlots.firstOrNull {
            it.dayOfWeek == selectedDay &&
                    it.slotNumber == slotNumber
        }

        val subjectName = currentSlot?.subjectName
            ?.takeIf { it.isNotBlank() }
            ?: "Свободное время"

        val existingNote = lessonNotes.firstOrNull {
            it.slotNumber == slotNumber
        }

        LessonNoteDialog(
            subjectName = subjectName,
            date = selectedDate,
            slotNumber = slotNumber,
            initialText = existingNote?.text.orEmpty(),
            onDismiss = {
                showLessonNote = false
                noteSlot = null
            },
            onSave = { text ->
                scope.launch {
                    if (text.isBlank()) {
                        dao.deleteLessonNote(
                            semesterId = selectedSemester.id,
                            date = selectedDate,
                            slotNumber = slotNumber
                        )
                    } else {
                        dao.saveLessonNote(
                            com.example.fichuga.data.LessonNoteEntity(
                                id = existingNote?.id ?: 0,
                                semesterId = selectedSemester.id,
                                date = selectedDate,
                                slotNumber = slotNumber,
                                subjectName = subjectName,
                                text = text.trim()
                            )
                        )
                    }

                    showLessonNote = false
                    noteSlot = null
                }
            },
            onDelete = {
                scope.launch {
                    dao.deleteLessonNote(
                        semesterId = selectedSemester.id,
                        date = selectedDate,
                        slotNumber = slotNumber
                    )

                    showLessonNote = false
                    noteSlot = null
                }
            }
        )
    }

    if (showSemesterPicker) {
        AlertDialog(
            onDismissRequest = { showSemesterPicker = false },
            title = { Text("Выбор семестра") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (semesters.isEmpty()) {
                        Text("Пока нет созданных семестров.")
                    }

                    semesters.forEach { semester ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    selectedSemesterId = semester.id
                                    settingsWeek = 1
                                    selectedDay = 1
                                    showSemesterPicker = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    semester.name,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            TextButton(
                                onClick = {
                                    showSemesterPicker = false
                                    semesterToDelete = semester
                                }
                            ) {
                                Text("Удалить")
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            newSemesterName = ""
                            showCreateSemester = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Создать семестр")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSemesterPicker = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    if (showCreateSemester) {
        AlertDialog(
            onDismissRequest = { showCreateSemester = false },
            title = { Text("Новый семестр") },
            text = {
                OutlinedTextField(
                    value = newSemesterName,
                    onValueChange = { newSemesterName = it },
                    label = { Text("Название семестра") },
                    placeholder = {
                        Text("Например, Осенний семестр 2026")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            },
            confirmButton = {
                Button(
                    enabled = newSemesterName.isNotBlank(),
                    onClick = {
                        val name = newSemesterName.trim()

                        scope.launch {
                            val id = dao.addSemester(
                                SemesterEntity(
                                    name = name,
                                    weekOneStartDate = currentMondayDate()
                                )
                            )

                            selectedSemesterId = id
                            selectedDay = 1
                            showCreateSemester = false
                            showSemesterPicker = false
                        }
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSemester = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    if (semesterToDelete != null) {
        val semester = semesterToDelete!!

        AlertDialog(
            onDismissRequest = {
                semesterToDelete = null
            },
            title = {
                Text("Удалить семестр?")
            },
            text = {
                Text(
                    "Семестр «${semester.name}», всё его расписание " +
                            "и все заметки будут удалены без возможности восстановления. " +
                            "Другие семестры не пострадают."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            dao.deleteSemesterWithData(semester)

                            if (selectedSemesterId == semester.id) {
                                selectedSemesterId = null
                            }

                            semesterToDelete = null
                        }
                    }
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        semesterToDelete = null
                        showSemesterPicker = true
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}


@Composable
private fun LessonRow(
    number: Int,
    slot: LessonSlotEntity?,
    timeRange: String,
    noteExists: Boolean,
    onNoteClick: () -> Unit,
    onSubjectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (slot == null) {
                    Text(
                        "Нет занятия",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Свободное время",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (slot.lessonType.isBlank()) slot.subjectName
                        else "${slot.subjectName} — ${slot.lessonType}",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onSubjectClick()
                        }
                    )

                    if (slot.room.isNotBlank()) {
                        Text("Аудитория: ${slot.room}", fontSize = 12.sp)
                    }

                    if (timeRange.isNotBlank()) {
                        Text(
                            timeRange,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TextButton(
                onClick = onNoteClick
            ) {
                Text(
                    text = if (noteExists) "★" else "☆",
                    fontSize = 26.sp,
                    color = if (noteExists) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun ScheduleSettingsScreen(
    dao: NotesDao,
    semester: SemesterEntity,
    selectedWeek: Int,
    onWeekChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bellTimes by dao.getAllBellTimes()
        .collectAsState(initial = emptyList())

    var showBellTimeEditor by remember {
        mutableStateOf(false)
    }
    var showWeekStartDatePicker by remember {
        mutableStateOf(false)
    }

    var selectedDay by rememberSaveable { mutableIntStateOf(1) }
    var editingSlot by remember { mutableStateOf<LessonSlotEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var clearWeekConfirmation by remember { mutableStateOf(false) }
    var clearAllConfirmation by remember { mutableStateOf(false) }

    val slots by produceState(
        initialValue = emptyList<LessonSlotEntity>(),
        semester.id,
        selectedWeek
    ) {
        dao.getLessonSlots(semester.id, selectedWeek).collect { value = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Настройки расписания",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        Spacer(Modifier.height(4.dp))
        Text(
            semester.name,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Начало первой недели",
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        val weekOneStartDate = semester.weekOneStartDate

        Text(
            text = if (weekOneStartDate.isNotBlank()) {
                val parts = weekOneStartDate.split("-")

                if (parts.size == 3) {
                    "${parts[2]}.${parts[1]}.${parts[0]}"
                } else {
                    "Дата не задана"
                }
            } else {
                "Дата не выбрана"
            }
        )

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                showWeekStartDatePicker = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выбрать дату")
        }

        Spacer(Modifier.height(20.dp))

        Text("Выбери неделю", fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(1, 2).forEach { week ->
                if (selectedWeek == week) {
                    Button(
                        onClick = { onWeekChange(week) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Неделя $week")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onWeekChange(week) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Неделя $week")
                    }
                }
            }

            OutlinedButton(
                onClick = onBack,
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 12.dp
                )
            ) {
                Text("←", fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Выбери день", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            shortDays.forEachIndexed { index, day ->
                val dayNumber = index + 1

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selectedDay == dayNumber)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedDay = dayNumber }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day,
                        color = if (selectedDay == dayNumber)
                            MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            weekDays[selectedDay - 1],
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        for (number in 1..7) {
            val slot = slots.firstOrNull {
                it.dayOfWeek == selectedDay && it.slotNumber == number
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "$number. " +
                                    (slot?.subjectName?.takeIf { it.isNotBlank() }
                                        ?: "Нет занятия"),
                            fontWeight = FontWeight.Medium
                        )

                        if (slot != null) {
                            val details = listOf(slot.lessonType, slot.room)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")

                            if (details.isNotBlank()) {
                                Text(details, fontSize = 12.sp)
                            }
                        }

                        val time = bellTimes.firstOrNull {
                            it.slotNumber == number
                        }

                        if (time != null &&
                            (time.startTime.isNotBlank() || time.endTime.isNotBlank())
                        ) {
                            Text(
                                "${time.startTime}–${time.endTime}",
                                fontSize = 12.sp
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            editingSlot = slot ?: LessonSlotEntity(
                                semesterId = semester.id,
                                weekNumber = selectedWeek,
                                dayOfWeek = selectedDay,
                                slotNumber = number,
                                subjectName = ""
                            )
                            showEditor = true
                        }
                    ) {
                        Text(if (slot == null) "Добавить" else "Изменить")
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()

        Spacer(Modifier.height(20.dp))

        Text(
            "Расписание звонков",
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        (1..7).forEach { number ->
            val time = bellTimes.firstOrNull {
                it.slotNumber == number
            }

            Text(
                "$number-я пара: " +
                        if (time == null ||
                            (time.startTime.isBlank() && time.endTime.isBlank())
                        ) {
                            "Время не задано"
                        } else {
                            "${time.startTime}–${time.endTime}"
                        }
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showBellTimeEditor = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Настроить время звонков")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()

        Spacer(Modifier.height(16.dp))

        Text("Очистка расписания", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        Text(
            "Заметки к предметам сохранятся. Заметки к отдельным занятиям " +
                    "будут удалены вместе с занятиями.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { clearWeekConfirmation = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Очистить неделю $selectedWeek")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { clearAllConfirmation = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Очистить всё расписание семестра")
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showEditor && editingSlot != null) {
        LessonEditorDialog(
            initialSlot = editingSlot!!,
            onDismiss = { showEditor = false },
            onSave = { updatedSlot ->
                scope.launch {
                    if (updatedSlot.id == 0L) {
                        dao.addLessonSlot(updatedSlot)
                    } else {
                        dao.saveLessonSlot(updatedSlot)
                    }
                    showEditor = false
                }
            },
            onDelete = { slot ->
                scope.launch {
                    dao.deleteLessonSlot(slot)
                    showEditor = false
                }
            }
        )
    }

    if (clearWeekConfirmation) {
        AlertDialog(
            onDismissRequest = { clearWeekConfirmation = false },
            title = { Text("Очистить неделю?") },
            text = {
                Text(
                    "Все занятия недели $selectedWeek будут удалены. " +
                            "Заметки к предметам сохранятся."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            dao.clearWeek(semester.id, selectedWeek)
                            clearWeekConfirmation = false
                        }
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearWeekConfirmation = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (clearAllConfirmation) {
        AlertDialog(
            onDismissRequest = { clearAllConfirmation = false },
            title = { Text("Очистить всё расписание?") },
            text = {
                Text(
                    "Все занятия обеих недель этого семестра будут удалены. " +
                            "Заметки к предметам сохранятся. Это действие нельзя отменить."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            dao.clearSemesterSchedule(semester.id)
                            clearAllConfirmation = false
                        }
                    }
                ) {
                    Text("Очистить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { clearAllConfirmation = false }) {
                    Text("Отмена")
                }
            }
        )
    }
    if (showWeekStartDatePicker) {
        val initialDate = semester.weekOneStartDate
            .takeIf { it.isNotBlank() }
            ?.let { calendarFromDate(it) }
            ?: java.util.Calendar.getInstance()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.timeInMillis
        )

        DatePickerDialog(
            onDismissRequest = {
                showWeekStartDatePicker = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis

                        if (millis != null) {
                            val calendar = java.util.Calendar.getInstance()
                            calendar.timeInMillis = millis

                            val date = dateFromCalendar(calendar)

                            scope.launch {
                                dao.updateWeekOneStartDate(
                                    semesterId = semester.id,
                                    date = date
                                )
                            }
                        }

                        showWeekStartDatePicker = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWeekStartDatePicker = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showBellTimeEditor) {
        BellTimeEditorDialog(
            initialTimes = bellTimes,
            onDismiss = {
                showBellTimeEditor = false
            },
            onSave = { updatedTimes ->
                scope.launch {
                    updatedTimes.forEach { time ->
                        dao.saveBellTime(time)
                    }

                    showBellTimeEditor = false
                }
            }
        )
    }
}

@Composable
private fun LessonEditorDialog(
    initialSlot: LessonSlotEntity,
    onDismiss: () -> Unit,
    onSave: (LessonSlotEntity) -> Unit,
    onDelete: (LessonSlotEntity) -> Unit
) {
    var subjectName by remember(initialSlot) {
        mutableStateOf(initialSlot.subjectName)
    }
    var lessonType by remember(initialSlot) {
        mutableStateOf(initialSlot.lessonType)
    }
    var room by remember(initialSlot) {
        mutableStateOf(initialSlot.room)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initialSlot.id == 0L) "Новое занятие"
                else "Редактирование занятия"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${weekDays[initialSlot.dayOfWeek - 1]}, " +
                            "неделя ${initialSlot.weekNumber}, " +
                            "пара ${initialSlot.slotNumber}",
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("Предмет") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                OutlinedTextField(
                    value = lessonType,
                    onValueChange = { lessonType = it },
                    label = { Text("Тип занятия") },
                    placeholder = { Text("Лекция, семинар…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Корпус / аудитория") },
                    placeholder = { Text("Например, 2/24a") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {
            Button(
                enabled = subjectName.isNotBlank(),
                onClick = {
                    onSave(
                        initialSlot.copy(
                            subjectName = subjectName.trim(),
                            lessonType = lessonType.trim(),
                            room = room.trim()
                        )
                    )
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initialSlot.id != 0L) {
                    TextButton(onClick = { onDelete(initialSlot) }) {
                        Text("Удалить")
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}


@Composable
private fun LessonNoteDialog(
    subjectName: String,
    date: String,
    slotNumber: Int,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember(date, slotNumber, initialText) {
        mutableStateOf(initialText)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка к занятию") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    subjectName,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "${formatDisplayDate(date)} · пара $slotNumber",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст заметки") },
                    placeholder = {
                        Text("Например, что задали или что повторить")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            Row {
                if (initialText.isNotBlank()) {
                    TextButton(onClick = onDelete) {
                        Text("Удалить")
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("Отмена")
                }
            }
        }
    )
}


private fun currentMondayDate(): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.firstDayOfWeek = java.util.Calendar.MONDAY
    calendar.set(
        java.util.Calendar.DAY_OF_WEEK,
        java.util.Calendar.MONDAY
    )

    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

private fun addDaysToDate(date: String, days: Int): String {
    val calendar = calendarFromDate(date)
    calendar.add(java.util.Calendar.DAY_OF_MONTH, days)

    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

private fun calendarFromDate(date: String): java.util.Calendar {
    val parts = date.split("-")
    return java.util.Calendar.getInstance().apply {
        set(
            parts[0].toInt(),
            parts[1].toInt() - 1,
            parts[2].toInt(),
            12,
            0,
            0
        )
        set(java.util.Calendar.MILLISECOND, 0)
    }
}

private fun dateFromCalendar(
    calendar: java.util.Calendar
): String {
    return String.format(
        java.util.Locale.US,
        "%04d-%02d-%02d",
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.DAY_OF_MONTH)
    )
}

private fun formatDisplayDate(date: String): String {
    val parts = date.split("-")
    return "${parts[2]}.${parts[1]}"
}

private fun getTemplateWeek(
    displayedMonday: String,
    weekOneStart: String
): Int {
    val displayed = calendarFromDate(displayedMonday)
    val anchor = calendarFromDate(weekOneStart)

    // Считаем количество календарных дней, не полагаясь
    // на разницу миллисекунд между днями перехода на летнее время.
    val displayedDay = dayNumber(displayed)
    val anchorDay = dayNumber(anchor)

    val weekOffset = Math.floorDiv(displayedDay - anchorDay, 7L)
    return if (Math.floorMod(weekOffset, 2L) == 0L) 1 else 2
}

private fun dayNumber(calendar: java.util.Calendar): Long {
    val utc = java.util.Calendar.getInstance(
        java.util.TimeZone.getTimeZone("UTC")
    )

    utc.clear()
    utc.set(
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH),
        calendar.get(java.util.Calendar.DAY_OF_MONTH),
        12,
        0,
        0
    )

    return Math.floorDiv(utc.timeInMillis, 86_400_000L)
}

@Composable
private fun BellTimeEditorDialog(
    initialTimes: List<BellTimeEntity>,
    onDismiss: () -> Unit,
    onSave: (List<BellTimeEntity>) -> Unit
) {
    val editedTimes = remember(initialTimes) {
        (1..7).map { number ->
            initialTimes.firstOrNull {
                it.slotNumber == number
            } ?: BellTimeEntity(
                slotNumber = number,
                startTime = "",
                endTime = ""
            )
        }.toMutableStateList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Время звонков")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                editedTimes.forEachIndexed { index, time ->
                    Text(
                        "${index + 1}-я пара",
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = time.startTime,
                            onValueChange = {
                                editedTimes[index] =
                                    time.copy(startTime = it)
                            },
                            label = { Text("Начало") },
                            placeholder = { Text("09:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = time.endTime,
                            onValueChange = {
                                editedTimes[index] =
                                    time.copy(endTime = it)
                            },
                            label = { Text("Конец") },
                            placeholder = { Text("10:30") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(editedTimes.toList())
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun SubjectNotesScreen(
    dao: NotesDao,
    semester: SemesterEntity,
    subjectName: String,
    onBack: () -> Unit
) {
    val notes by dao.getSubjectNotes(
        semesterId = semester.id,
        subjectName = subjectName
    ).collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    var editingNote by remember {
        mutableStateOf<SubjectNoteEntity?>(null)
    }
    var isCreatingNote by remember {
        mutableStateOf(false)
    }
    var noteText by remember {
        mutableStateOf("")
    }
    var noteToDelete by remember {
        mutableStateOf<SubjectNoteEntity?>(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Назад")
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = semester.name,
                style = MaterialTheme.typography.labelMedium
            )
        }

        Text(
            text = subjectName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                noteText = ""
                editingNote = null
                isCreatingNote = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("＋ Добавить заметку")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (notes.isEmpty()) {
            Text(
                text = "Пока нет заметок по этому предмету.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = notes,
                    key = { it.id }
                ) { note ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = note.text,
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = {
                                        noteText = note.text
                                        editingNote = note
                                        isCreatingNote = true
                                    }
                                ) {
                                    Text("Изменить")
                                }

                                TextButton(
                                    onClick = {
                                        noteToDelete = note
                                    }
                                ) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isCreatingNote) {
        AlertDialog(
            onDismissRequest = {
                isCreatingNote = false
                editingNote = null
            },
            title = {
                Text(
                    if (editingNote == null) {
                        "Новая заметка"
                    } else {
                        "Изменить заметку"
                    }
                )
            },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Текст заметки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = noteText.trim()

                        if (text.isNotEmpty()) {
                            val existing = editingNote

                            val note = if (existing == null) {
                                SubjectNoteEntity(
                                    semesterId = semester.id,
                                    subjectName = subjectName,
                                    text = text
                                )
                            } else {
                                existing.copy(text = text)
                            }

                            scope.launch {
                                dao.saveSubjectNote(note)
                            }

                            isCreatingNote = false
                            editingNote = null
                        }
                    },
                    enabled = noteText.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isCreatingNote = false
                        editingNote = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                noteToDelete = null
            },
            title = {
                Text("Удалить заметку?")
            },
            text = {
                Text("Это действие нельзя отменить.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val note = noteToDelete

                        if (note != null) {
                            scope.launch {
                                dao.deleteSubjectNote(note)
                            }
                        }

                        noteToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        noteToDelete = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}
