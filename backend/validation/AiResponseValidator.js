/**
 * Feature-specific AI response validators.
 */

function isNonEmptyString(v) {
  return typeof v === 'string' && v.trim().length > 0;
}

class AiResponseValidator {
  validateTextResult(result) {
    if (!isNonEmptyString(result)) {
      return { valid: false, error: 'Empty text result' };
    }
    return { valid: true, data: result.trim() };
  }

  validateChatResult(result) {
    if (!result || typeof result !== 'object') {
      return { valid: false, error: 'Invalid chat response shape' };
    }
    const reply = result.reply;
    if (!isNonEmptyString(reply) && !isNonEmptyString(result.reasoning)) {
      return { valid: false, error: 'Empty chat reply' };
    }
    return { valid: true, data: result };
  }
}

class FlashcardValidator {
  validate(data) {
    if (!data || !Array.isArray(data.cards)) {
      return { valid: false, error: 'Missing cards array' };
    }
    const cards = data.cards.filter(
      (c) => c && isNonEmptyString(c.question) && isNonEmptyString(c.answer)
    );
    if (cards.length === 0) {
      return { valid: false, error: 'No valid flashcards' };
    }
    return { valid: true, data: { cards } };
  }
}

class QuizValidator {
  validate(data) {
    if (!data || !Array.isArray(data.questions)) {
      return { valid: false, error: 'Missing questions array' };
    }
    const questions = data.questions.filter((q) => this.isValidQuestion(q));
    if (questions.length === 0) {
      return { valid: false, error: 'No valid quiz questions' };
    }
    return {
      valid: true,
      data: {
        title: isNonEmptyString(data.title) ? data.title.trim() : 'Generated Quiz',
        questions,
      },
    };
  }

  isValidQuestion(q) {
    if (!q || !isNonEmptyString(q.question)) return false;
    if (!Array.isArray(q.options) || q.options.length < 2) return false;
    if (!isNonEmptyString(q.correctAnswer)) return false;
    const type = String(q.type || '').toUpperCase();
    if (type.includes('TRUE') || type.includes('FALSE')) {
      return q.options.length >= 2;
    }
    const validOptions = q.options.filter((o) => isNonEmptyString(o));
    return validOptions.length >= 2;
  }
}

class ScheduleValidator {
  validate(data) {
    if (!data || !Array.isArray(data.classes)) {
      return { valid: false, error: 'Missing classes array' };
    }
    const DAY_ALIASES = {
      M: 'MONDAY', MON: 'MONDAY', MONDAY: 'MONDAY',
      T: 'TUESDAY', TU: 'TUESDAY', TUE: 'TUESDAY', TUES: 'TUESDAY', TUESDAY: 'TUESDAY',
      W: 'WEDNESDAY', WED: 'WEDNESDAY', WEDNESDAY: 'WEDNESDAY',
      TH: 'THURSDAY', THU: 'THURSDAY', THUR: 'THURSDAY', THURS: 'THURSDAY', THURSDAY: 'THURSDAY',
      R: 'THURSDAY', H: 'THURSDAY',
      F: 'FRIDAY', FRI: 'FRIDAY', FRIDAY: 'FRIDAY',
      S: 'SATURDAY', SAT: 'SATURDAY', SATURDAY: 'SATURDAY',
      U: 'SUNDAY', SU: 'SUNDAY', SUN: 'SUNDAY', SUNDAY: 'SUNDAY',
    };

    // Lenient time parsing: "9:00", "8:00 AM", "0800", "13:30" → canonical "HH:MM".
    const normalizeTime = (value) => {
      if (typeof value !== 'string') return null;
      const raw = value.trim().toUpperCase();
      let match = raw.match(/^(\d{1,2}):(\d{2})\s*(AM|PM)?$/);
      if (!match) match = raw.match(/^(\d{2})(\d{2})$/);
      if (match) {
        let hours = parseInt(match[1], 10);
        const minutes = parseInt(match[2], 10);
        if (minutes > 59 || hours > 24) return null;
        const meridiem = match[3];
        if (meridiem === 'PM' && hours < 12) hours += 12;
        if (meridiem === 'AM' && hours === 12) hours = 0;
        if (hours === 24) hours = 0;
        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
      }
      // "8-9" style ranges → start only.
      match = raw.match(/^(\d{1,2})(AM|PM)?\s*[-–]\s*\d{1,2}(AM|PM)?$/);
      if (match) {
        let hours = parseInt(match[1], 10);
        const meridiem = match[2];
        if (meridiem === 'PM' && hours < 12) hours += 12;
        return `${String(hours).padStart(2, '0')}:00`;
      }
      return null;
    };

    const addHour = (hhmm) => {
      const [hours, minutes] = hhmm.split(':').map(Number);
      return `${String((hours + 1) % 24).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
    };

    const classes = [];
    const uncertainFields = Array.isArray(data.uncertainFields) ? data.uncertainFields.slice() : [];

    data.classes.forEach((c, index) => {
      if (!c || !isNonEmptyString(c.subject)) return;
      const rawDay = String(c.day || c.dayOfWeek || '').trim().toUpperCase();
      const day = DAY_ALIASES[rawDay];
      if (!day) return;

      const startTime = normalizeTime(c.startTime);
      if (!startTime) return; // Without any parseable start time the entry is unusable.

      let endTime = normalizeTime(c.endTime);
      if (!endTime) {
        // Never invent silently — estimate +1h and flag it so the UI can show uncertainty.
        endTime = addHour(startTime);
        uncertainFields.push(`end time for ${c.subject} (${day} ${startTime}) was missing — estimated as ${endTime}`);
      }

      classes.push({
        subject: c.subject.trim(),
        teacher: typeof c.teacher === 'string' && c.teacher.trim() ? c.teacher.trim() : null,
        room: typeof c.room === 'string' && c.room.trim() ? c.room.trim() : null,
        day,
        startTime,
        endTime,
      });
    });

    return {
      valid: true,
      data: { classes, uncertainFields },
    };
  }
}

class AssignmentBreakdownValidator {
  validate(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: 'Invalid assignment breakdown shape' };
    }
    if (!isNonEmptyString(data.title)) {
      return { valid: false, error: 'Missing assignment title' };
    }

    const deadline = this.normalizeDeadline(data.deadline);
    const requirements = this.normalizeStringList(data.requirements);
    const deliverables = this.normalizeStringList(data.deliverables);
    const rubric = this.normalizeStringList(data.rubric);
    const subtasks = this.normalizeSubtasks(data.subtasks);
    const estimatedEffortHours = this.normalizeEffortHours(data.estimatedEffortHours);
    const notes = typeof data.notes === 'string' ? data.notes.trim() : '';

    if (subtasks.length === 0) {
      return { valid: false, error: 'No valid subtasks' };
    }

    return {
      valid: true,
      data: {
        title: data.title.trim().slice(0, 120),
        deadline,
        requirements,
        deliverables,
        rubric,
        subtasks,
        estimatedEffortHours,
        notes,
      },
    };
  }

  normalizeDeadline(value) {
    if (value == null || value === '') return null;
    const raw = String(value).trim();
    const dateOnly = raw.match(/^(\d{4}-\d{2}-\d{2})/);
    if (dateOnly) return dateOnly[1];
    const parsed = Date.parse(raw);
    if (Number.isNaN(parsed)) return null;
    return new Date(parsed).toISOString().slice(0, 10);
  }

  normalizeStringList(value) {
    if (!Array.isArray(value)) return [];
    return value
      .map((item) => (typeof item === 'string' ? item.trim() : ''))
      .filter((item) => item.length > 0)
      .slice(0, 20);
  }

  normalizeSubtasks(value) {
    if (!Array.isArray(value)) return [];
    return value
      .filter((item) => item && isNonEmptyString(item.title))
      .map((item) => ({
        title: String(item.title).trim().slice(0, 200),
        estimatedMinutes: this.clampInt(item.estimatedMinutes, 15, 480, 30),
        dueOffsetDays: this.clampInt(item.dueOffsetDays, 0, 365, 0),
      }))
      .slice(0, 12);
  }

  normalizeEffortHours(value) {
    const num = Number(value);
    if (!Number.isFinite(num) || num <= 0) return 1;
    return Math.round(Math.min(Math.max(num, 0.5), 80) * 10) / 10;
  }

  clampInt(value, min, max, fallback) {
    const num = parseInt(value, 10);
    if (!Number.isFinite(num)) return fallback;
    return Math.min(Math.max(num, min), max);
  }
}

class FocusPlanValidator {
  validate(data) {
    if (!data || typeof data !== 'object') {
      return { valid: false, error: 'Invalid focus plan shape' };
    }

    const totalMinutes = this.clampInt(data.totalMinutes, 15, 240, 0);
    if (totalMinutes <= 0) {
      return { valid: false, error: 'Invalid totalMinutes' };
    }
    if (!Array.isArray(data.blocks) || data.blocks.length === 0) {
      return { valid: false, error: 'Missing blocks array' };
    }

    const allowedTypes = new Set(['STUDY', 'BREAK', 'REVIEW']);
    const blocks = data.blocks
      .filter((b) => b && isNonEmptyString(b.activity))
      .map((b) => {
        const startMinute = this.clampInt(b.startMinute, 0, totalMinutes - 1, 0);
        const endMinute = this.clampInt(b.endMinute, startMinute + 1, totalMinutes, startMinute + 15);
        let type = String(b.type || 'STUDY').toUpperCase();
        if (!allowedTypes.has(type)) type = 'STUDY';
        return {
          startMinute,
          endMinute,
          activity: String(b.activity).trim().slice(0, 120),
          type,
        };
      })
      .filter((b) => b.endMinute > b.startMinute)
      .sort((a, b) => a.startMinute - b.startMinute)
      .slice(0, 12);

    if (blocks.length === 0) {
      return { valid: false, error: 'No valid focus blocks' };
    }

    return {
      valid: true,
      data: {
        totalMinutes,
        blocks,
        breakMinutesBetween: this.clampInt(data.breakMinutesBetween, 1, 15, 5),
      },
    };
  }

  clampInt(value, min, max, fallback) {
    const num = parseInt(value, 10);
    if (!Number.isFinite(num)) return fallback;
    return Math.min(Math.max(num, min), max);
  }
}

module.exports = {
  AiResponseValidator,
  FlashcardValidator,
  QuizValidator,
  ScheduleValidator,
  AssignmentBreakdownValidator,
  FocusPlanValidator,
};
