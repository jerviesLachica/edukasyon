const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const {
  solveMathProblem,
  generateTabularComparison,
  generateSmartStudyFallback,
} = require('../ai/SmartStudyFallback');

describe('SmartStudyFallback - Math Operations', () => {
  it('solves simple linear equation with step-by-step and table', () => {
    const res = solveMathProblem('solve 2x + 4 = 10');
    assert.ok(res);
    assert.match(res, /Mathematical Solution/);
    assert.match(res, /x = 3/);
    assert.match(res, /\| Step \| Operation \| Result \|/);
  });

  it('computes square root with table and LaTeX', () => {
    const res = solveMathProblem('sqrt(144)');
    assert.ok(res);
    assert.match(res, /\\sqrt\{144\} = 12/);
    assert.match(res, /\| Operation \| Input \| Result \|/);
  });

  it('evaluates basic arithmetic expressions', () => {
    const res = solveMathProblem('calculate 25 * 4');
    assert.ok(res);
    assert.match(res, /100/);
    assert.match(res, /\| Expression \| Operation Type \| Calculated Value \|/);
  });

  it('explains quadratic formula with LaTeX and table', () => {
    const res = solveMathProblem('explain quadrtatic formula to me');
    assert.ok(res);
    assert.match(res, /Quadratic Formula/);
    assert.match(res, /x = \\frac\{-b \\pm \\sqrt\{b\^2 - 4ac\}\}\{2a\}/);
    assert.match(res, /Discriminant/);
    assert.match(res, /\| Step \| Operation \| Result \|/);
  });

  it('solves specific quadratic equation step-by-step', () => {
    const res = solveMathProblem('solve x^2 - 5x + 6 = 0');
    assert.ok(res);
    assert.match(res, /Mathematical Solution/);
    assert.match(res, /x_1 = 3/);
    assert.match(res, /x_2 = 2/);
  });
});

describe('SmartStudyFallback - Columns & Rows Tables', () => {
  it('generates structured table for mitosis vs meiosis', () => {
    const res = generateTabularComparison('make a table comparing mitosis and meiosis');
    assert.ok(res);
    assert.match(res, /\| Feature \/ Aspect \| Mitosis \| Meiosis \|/);
    assert.match(res, /\| :--- \| :--- \| :--- \|/);
    assert.match(res, /Somatic/);
    assert.match(res, /gametes/i);
  });

  it('generates structured physics table for speed vs velocity', () => {
    const res = generateTabularComparison('compare speed and velocity in a table');
    assert.ok(res);
    assert.match(res, /\| Concept \| Definition \| Scalar or Vector\? \|/);
    assert.match(res, /Scalar/);
    assert.match(res, /Vector/);
  });

  it('generates structured biology table for prokaryotes vs eukaryotes', () => {
    const res = generateTabularComparison('compare prokaryotic vs eukaryotic cells in a table');
    assert.ok(res);
    assert.match(res, /Nucleoid/i);
    assert.match(res, /membrane-bound/i);
  });

  it('generates structured chemistry table for ionic vs covalent bonds', () => {
    const res = generateTabularComparison('make a table comparing ionic and covalent bonds');
    assert.ok(res);
    assert.match(res, /transfer of valence electrons/i);
    assert.match(res, /sharing of valence electron/i);
  });
});

describe('SmartStudyFallback - Full Fallback Generator', () => {
  it('handles math query gracefully on fallback', () => {
    const fallback = generateSmartStudyFallback({ message: '2x + 6 = 18' });
    assert.ok(fallback.reply);
    assert.match(fallback.reply, /x = 6/);
    assert.match(fallback.reply, /\| Step \| Operation \| Result \|/);
  });

  it('handles table query gracefully on fallback', () => {
    const fallback = generateSmartStudyFallback({ message: 'create a table comparing DNA and RNA' });
    assert.ok(fallback.reply);
    assert.match(fallback.reply, /\| Property \| DNA/);
  });

  it('generates interactive flashcard deck with actions on flashcard requests', () => {
    const fallback = generateSmartStudyFallback({ message: 'make flashcards on photosynthesis', subject: 'Biology' });
    assert.ok(fallback.reply);
    assert.match(fallback.reply, /create_flashcard_deck/);
    assert.match(fallback.reply, /Photosynthesis/);
    assert.equal(fallback.model, 'smart-offline-deck');
  });

  it('generates interactive quiz with actions on quiz requests', () => {
    const fallback = generateSmartStudyFallback({ message: 'quiz me on calculus derivatives' });
    assert.ok(fallback.reply);
    assert.match(fallback.reply, /launch_practice_quiz/);
    assert.equal(fallback.model, 'smart-offline-quiz');
  });

  it('provides structured study response with summary table for general study queries', () => {
    const fallback = generateSmartStudyFallback({ message: 'How do I study for my exam?' });
    assert.ok(fallback.reply);
    assert.match(fallback.reply, /\| Component \| Focus Area \| Recommended Study Action \|/);
  });
});
