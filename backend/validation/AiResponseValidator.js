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
    const days = new Set(['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']);
    const timePattern = /^\d{2}:\d{2}$/;

    const classes = data.classes.filter((c) => {
      if (!c || !isNonEmptyString(c.subject)) return false;
      const day = String(c.day || c.dayOfWeek || '').toUpperCase();
      if (!days.has(day)) return false;
      if (!timePattern.test(c.startTime) || !timePattern.test(c.endTime)) return false;
      return true;
    });

    return {
      valid: true,
      data: {
        classes,
        uncertainFields: Array.isArray(data.uncertainFields) ? data.uncertainFields : [],
      },
    };
  }
}

module.exports = {
  AiResponseValidator,
  FlashcardValidator,
  QuizValidator,
  ScheduleValidator,
};
