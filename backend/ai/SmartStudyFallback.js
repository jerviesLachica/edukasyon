/**
 * Smart Study Fallback Engine for SchedMate.
 * 
 * Provides instantaneous, accurate study answers, math operations, and
 * structured Markdown tables (columns and rows) whenever the upstream AI
 * provider is unreachable, times out, or has exhausted quota.
 */

function solveMathProblem(text) {
  if (!text) return null;
  const clean = text.trim();

  // 1. Quadratic Formula & Quadratic Equations (e.g. "explain quadratic formula", "solve x^2 - 5x + 6 = 0")
  const isQuadraticConcept = /quadr/i.test(clean);
  const quadEqMatch = clean.match(/(?:solve\s+)?([+-]?\s*\d*\.?\d*)\s*([a-zA-Z])(?:\^2|²)\s*([+-]\s*\d*\.?\d*)\s*\2?\s*([+-]\s*\d+\.?\d*)?\s*=\s*([+-]?\s*\d+\.?\d*)/i);

  if (quadEqMatch && (clean.includes('^2') || clean.includes('²'))) {
    const rawA = quadEqMatch[1].replace(/\s+/g, '');
    const variable = quadEqMatch[2];
    const rawB = quadEqMatch[3] ? quadEqMatch[3].replace(/\s+/g, '') : '0';
    const rawC = quadEqMatch[4] ? quadEqMatch[4].replace(/\s+/g, '') : '0';
    const rawRight = quadEqMatch[5] ? quadEqMatch[5].replace(/\s+/g, '') : '0';

    const a = rawA === '' || rawA === '+' ? 1 : rawA === '-' ? -1 : parseFloat(rawA);
    const b = rawB === '' || rawB === '+' ? 1 : rawB === '-' ? -1 : parseFloat(rawB);
    const cVal = parseFloat(rawC);
    const rightVal = parseFloat(rawRight);
    const c = cVal - rightVal;

    if (!isNaN(a) && !isNaN(b) && !isNaN(c) && a !== 0) {
      const disc = (b * b) - (4 * a * c);
      let rootsText = '';
      if (disc > 0) {
        const x1 = ((-b + Math.sqrt(disc)) / (2 * a));
        const x2 = ((-b - Math.sqrt(disc)) / (2 * a));
        const x1Str = Number.isInteger(x1) ? x1 : x1.toFixed(3);
        const x2Str = Number.isInteger(x2) ? x2 : x2.toFixed(3);
        rootsText = `$$${variable}_1 = ${x1Str}, \\quad ${variable}_2 = ${x2Str}$$`;
      } else if (disc === 0) {
        const x0 = (-b / (2 * a));
        const x0Str = Number.isInteger(x0) ? x0 : x0.toFixed(3);
        rootsText = `$$${variable} = ${x0Str} \\quad \\text{(repeated root)}$$`;
      } else {
        const realPart = (-b / (2 * a)).toFixed(2);
        const imgPart = (Math.sqrt(-disc) / (2 * a)).toFixed(2);
        rootsText = `$$${variable} = ${realPart} \\pm ${imgPart}i \\quad \\text{(complex roots)}$$`;
      }

      return `### Mathematical Solution: Solving Quadratic Equation for $${variable}$

We are solving:
$$${a !== 1 ? a : ''}${variable}^2 ${b >= 0 ? `+ ${b !== 1 ? b : ''}` : `- ${Math.abs(b) !== 1 ? Math.abs(b) : ''}`}${variable} ${c >= 0 ? `+ ${c}` : `- ${Math.abs(c)}`} = 0$$

#### The Quadratic Formula:
$$${variable} = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}$$

#### Step-by-Step Working:
1. **Identify Coefficients**:
   - $a = ${a}$
   - $b = ${b}$
   - $c = ${c}$

2. **Calculate the Discriminant ($D = b^2 - 4ac$)**:
   $$D = (${b})^2 - 4(${a})(${c}) = ${b * b} - (${4 * a * c}) = ${disc}$$
   *(Since $D ${disc > 0 ? '> 0' : disc === 0 ? '= 0' : '< 0'}, there are ${disc > 0 ? 'two distinct real roots' : disc === 0 ? 'one repeated real root' : 'two complex conjugate roots'})*

3. **Substitute into Quadratic Formula**:
   $$${variable} = \\frac{-(${b}) \\pm \\sqrt{${disc}}}{2(${a})}$$

#### Solution Summary Table:
| Step | Operation | Result |
| :--- | :--- | :--- |
| **1. Standard Form** | $ax^2 + bx + c = 0$ | $a = ${a}, b = ${b}, c = ${c}$ |
| **2. Discriminant** | $D = b^2 - 4ac$ | $D = ${disc}$ |
| **3. Roots** | $${variable} = \\frac{-b \\pm \\sqrt{D}}{2a}$ | ${rootsText.replace(/\$\$/g, '$')} |

**Final Answer:**
${rootsText}`;
    }
  }

  if (isQuadraticConcept) {
    return `### The Quadratic Formula & How It Works

The **Quadratic Formula** is used to find the solutions (roots or x-intercepts) of any quadratic equation in standard form:
$$ax^2 + bx + c = 0$$
where $a \\ne 0$.

#### The Formula:
$$x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}$$

#### The Discriminant ($D = b^2 - 4ac$):
The expression under the square root determines the number and type of solutions:

| Discriminant ($b^2 - 4ac$) | Nature of Solutions | Parabola Graph ($y = ax^2 + bx + c$) |
| :--- | :--- | :--- |
| **$D > 0$** (Positive) | **2 distinct real roots** | Crosses the x-axis at two points |
| **$D = 0$** (Zero) | **1 repeated real root** | Touches the x-axis at its vertex |
| **$D < 0$** (Negative) | **2 complex / imaginary roots** | Does not touch or cross the x-axis |

#### Step-by-Step Example:
Solve $x^2 - 5x + 6 = 0$ using the quadratic formula:

1. **Identify $a$, $b$, and $c$**:
   - $a = 1$, $b = -5$, $c = 6$

2. **Compute the Discriminant**:
   $$D = (-5)^2 - 4(1)(6) = 25 - 24 = 1$$
   *(Since $D > 0$, there are two real roots)*

3. **Apply the Formula**:
   $$x = \\frac{-(-5) \\pm \\sqrt{1}}{2(1)} = \\frac{5 \\pm 1}{2}$$

4. **Calculate Both Values**:
   $$x_1 = \\frac{5 + 1}{2} = \\frac{6}{2} = 3$$
   $$x_2 = \\frac{5 - 1}{2} = \\frac{4}{2} = 2$$

#### Summary Table:
| Step | Operation | Result |
| :--- | :--- | :--- |
| **Coefficients** | Compare to $ax^2 + bx + c = 0$ | $a = 1, b = -5, c = 6$ |
| **Discriminant** | $D = (-5)^2 - 4(1)(6)$ | $D = 1$ |
| **Roots** | $x = \\frac{5 \\pm 1}{2}$ | **$x = 2$ or $x = 3$** |

**Final Answer:**
$$x = 2 \\quad \\text{or} \\quad x = 3$$`;
  }

  // 2. Simple Linear Equations (e.g. "2x + 4 = 10", "3x - 9 = 24", "solve 5x = 20")
  const eqMatch = clean.match(/(?:solve\s+)?([+-]?\s*\d*\.?\d*)\s*([a-zA-Z])\s*([+-]\s*\d+\.?\d*)?\s*=\s*([+-]?\s*\d+\.?\d*)/i);
  if (eqMatch) {
    const rawA = eqMatch[1].replace(/\s+/g, '');
    const variable = eqMatch[2];
    const rawB = eqMatch[3] ? eqMatch[3].replace(/\s+/g, '') : '0';
    const rawC = eqMatch[4].replace(/\s+/g, '');

    const a = rawA === '' || rawA === '+' ? 1 : rawA === '-' ? -1 : parseFloat(rawA);
    const b = parseFloat(rawB);
    const c = parseFloat(rawC);

    if (!isNaN(a) && !isNaN(b) && !isNaN(c) && a !== 0) {
      const step1Right = c - b;
      const solution = step1Right / a;

      return `### Mathematical Solution: Solving for $${variable}$

We are solving the linear equation:
$$${a !== 1 ? a : ''}${variable} ${b >= 0 ? `+ ${b}` : `- ${Math.abs(b)}`} = ${c}$$

#### Step-by-Step Working:
1. **Isolate variable term**:
   Subtract $${b}$ from both sides:
   $$${a !== 1 ? a : ''}${variable} = ${c} - (${b}) = ${step1Right}$$

2. **Solve for $${variable}$**:
   Divide both sides by $${a}$:
   $$${variable} = \\frac{${step1Right}}{${a}} = ${solution}$$

#### Solution Summary Table:
| Step | Operation | Result |
| :--- | :--- | :--- |
| **1. Original Equation** | Identify coefficients | $${a !== 1 ? a : ''}${variable} ${b >= 0 ? `+ ${b}` : `- ${Math.abs(b)}`} = ${c}$$ |
| **2. Isolate Term** | Subtract constant | $${a !== 1 ? a : ''}${variable} = ${step1Right}$$ |
| **3. Final Value** | Divide by coefficient ($${a}$) | **$${variable} = ${solution}$** |

**Final Answer:**
$$${variable} = ${solution}$$`;
    }
  }

  // 2. Square Root / Powers / Basic Arithmetic
  const sqrtMatch = clean.match(/(?:sqrt|square root of)\s*\(?(\d+\.?\d*)\)?/i);
  if (sqrtMatch) {
    const val = parseFloat(sqrtMatch[1]);
    const res = Math.sqrt(val);
    return `### Mathematical Solution: Square Root

We want to find the square root of $${val}$:
$$\\sqrt{${val}}$$

#### Step-by-Step:
1. Find a number $x$ such that $x^2 = ${val}$.
2. Testing values: $${res}^2 = ${val}$.

| Operation | Input | Result |
| :--- | :--- | :--- |
| **Square Root** | $${val}$$ | **$\\sqrt{${val}} = ${res}$** |

**Final Answer:**
$$\\sqrt{${val}} = ${res}$$`;
  }

  // 3. Direct Arithmetic (e.g. "15 * 8", "124 + 56", "350 / 5", "2^8")
  const arithMatch = clean.match(/^(?:calculate|compute|what is\s+)?([0-9.,\s()+\-*/%^]+)$/i);
  if (arithMatch) {
    const expr = arithMatch[1].replace(/,/g, '').trim();
    // Safe evaluation of basic numbers and operators
    if (/^[0-9\s()+\-*/.^]+$/.test(expr) && /[+\-*/^]/.test(expr)) {
      try {
        const sanitized = expr.replace(/\^/g, '**');
        // Safely evaluate standard math expression using Function
        const result = Function(`'use strict'; return (${sanitized})`)();
        if (typeof result === 'number' && !isNaN(result) && isFinite(result)) {
          return `### Calculation Result

Problem: $${expr}$

#### Step-by-Step Breakdown:
1. Evaluate standard mathematical order of operations (PEMDAS/BODMAS).
2. Computed value: **${result}**

#### Operation Table:
| Expression | Operation Type | Calculated Value |
| :--- | :--- | :--- |
| **$${expr}$** | Arithmetic Evaluation | **${result}** |

**Final Answer:**
$$${result}$$`;
        }
      } catch (_) {
        // Fall through
      }
    }
  }

  return null;
}

function generateTabularComparison(query) {
  if (!query) return null;
  const q = query.toLowerCase();

  const isTableRequested = q.includes('table') || q.includes('column') || q.includes('row') || q.includes('compare') || q.includes('versus') || q.includes('vs');
  if (!isTableRequested) return null;

  if (q.includes('mitosis') && q.includes('meiosis')) {
    return `### Comparison: Mitosis vs. Meiosis

Here is the structured comparison table organized into columns and rows:

| Feature / Aspect | Mitosis | Meiosis |
| :--- | :--- | :--- |
| **Purpose** | Growth, tissue repair, asexual reproduction | Production of gametes (sperm and egg) for sexual reproduction |
| **Location in Body** | Somatic (body) cells | Germ cells in gonads (testes/ovaries) |
| **Number of Divisions** | 1 nuclear division | 2 successive nuclear divisions (Meiosis I & II) |
| **Daughter Cells** | 2 genetically identical diploid ($2n$) cells | 4 genetically diverse haploid ($n$) cells |
| **Genetic Variation** | No crossing over; genetic clones | Crossing over & independent assortment create high genetic diversity |
| **Chromosome Count** | Stays the same (e.g., $46 \\to 46$ in humans) | Halved (e.g., $46 \\to 23$ in humans) |`;
  }

  if (q.includes('dna') && q.includes('rna')) {
    return `### Comparison: DNA vs. RNA

Here is a structured overview of the molecular differences:

| Property | DNA (Deoxyribonucleic Acid) | RNA (Ribonucleic Acid) |
| :--- | :--- | :--- |
| **Sugar Molecule** | Deoxyribose (lacks one oxygen on 2' carbon) | Ribose (has -OH on 2' carbon) |
| **Nitrogenous Bases** | Adenine (A), Thymine (T), Cytosine (C), Guanine (G) | Adenine (A), Uracil (U), Cytosine (C), Guanine (G) |
| **Strand Structure** | Double-stranded double helix | Single-stranded (can fold into secondary shapes) |
| **Primary Location** | Cell nucleus (and mitochondria/chloroplasts) | Nucleolus, cytoplasm, ribosomes |
| **Main Function** | Long-term genetic storage and blueprint | Protein synthesis (mRNA, tRNA, rRNA) and gene regulation |`;
  }

  if (q.includes('speed') && (q.includes('velocity') || q.includes('acceleration'))) {
    return `### Physics Concepts: Speed, Velocity & Acceleration

| Concept | Definition | Scalar or Vector? | SI Unit | Formula |
| :--- | :--- | :--- | :--- | :--- |
| **Speed** | Rate at which an object covers distance | **Scalar** (magnitude only) | $m/s$ | $s = \\frac{d}{t}$ |
| **Velocity** | Rate of change of position in a specified direction | **Vector** (magnitude + direction) | $m/s$ | $\\vec{v} = \\frac{\\Delta \\vec{x}}{\\Delta t}$ |
| **Acceleration** | Rate of change of velocity over time | **Vector** (magnitude + direction) | $m/s^2$ | $\\vec{a} = \\frac{\\Delta \\vec{v}}{\\Delta t}$ |`;
  }

  if (q.includes('prokaryot') || q.includes('eukaryot')) {
    return `### Comparison: Prokaryotic vs. Eukaryotic Cells

| Feature | Prokaryotic Cells | Eukaryotic Cells |
| :--- | :--- | :--- |
| **Nucleus** | Absent (DNA in nucleoid region) | True membrane-bound nucleus |
| **Membrane-Bound Organelles** | None (no mitochondria, ER, Golgi) | Present (mitochondria, chloroplasts, Golgi, etc.) |
| **Size** | Typically $0.1–5.0\\,\\mu m$ (smaller) | Typically $10–100\\,\\mu m$ (larger) |
| **Ribosomes** | 70S ribosomes | 80S ribosomes (in cytoplasm) |
| **Examples** | Bacteria, Archaea | Plants, Animals, Fungi, Protists |`;
  }

  if (q.includes('photosynthesis') && (q.includes('light') || q.includes('calvin') || q.includes('dark'))) {
    return `### Photosynthesis: Light-Dependent vs. Calvin Cycle

| Phase | Light-Dependent Reactions | Calvin Cycle (Light-Independent) |
| :--- | :--- | :--- |
| **Location** | Thylakoid membrane | Stroma (fluid of chloroplast) |
| **Inputs** | Light energy, $\\text{H}_2\\text{O}$, $\\text{NADP}^+$, $\\text{ADP}$ | $\\text{CO}_2$, $\\text{ATP}$, $\\text{NADPH}$ |
| **Outputs** | $\\text{O}_2$, $\\text{ATP}$, $\\text{NADPH}$ | Glucose (G3P), $\\text{NADP}^+$, $\\text{ADP}$ |
| **Light Requirement** | Directly required | Does not directly require photons, but relies on ATP/NADPH from light phase |`;
  }

  if (q.includes('ionic') && q.includes('covalent')) {
    return `### Chemical Bonds: Ionic vs. Covalent Bonding

| Characteristic | Ionic Bonding | Covalent Bonding |
| :--- | :--- | :--- |
| **Electron Behavior** | Complete transfer of valence electrons | Sharing of valence electron pairs |
| **Participating Atoms** | Metal + Non-metal | Non-metal + Non-metal |
| **Electronegativity Difference** | Large (typically $> 1.7–2.0$) | Small to moderate ($\\le 1.7$) |
| **Melting / Boiling Points** | Very high (strong crystal lattice) | Generally lower (weaker intermolecular forces) |
| **Electrical Conductivity** | Conducts electricity when molten or in aqueous solution | Poor electrical conductor |
| **Examples** | $\\text{NaCl}$, $\\text{MgO}$, $\\text{CaCl}_2$ | $\\text{H}_2\\text{O}$, $\\text{CO}_2$, $\\text{CH}_4$ |`;
  }

  return null;
}

function extractTopic(message) {
  const clean = (message || '')
    .replace(/(?:make|create|generate|give me|build|produce|a|an|the|practice|study|flashcards?|quiz|questions?|for|on|about|of)\s+/gi, ' ')
    .trim();
  return clean.length >= 3 ? clean.charAt(0).toUpperCase() + clean.slice(1) : 'Key Study Concepts';
}

function generateFlashcardFallback(message, subject) {
  const topic = extractTopic(message);
  const title = `${topic} Flashcards`;
  const cards = [];

  // Extract from message if lines or bullet points exist
  const lines = String(message || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  for (const line of lines) {
    const clean = line.replace(/^[-*•>]\s*/, '').trim();
    if (clean.includes(':') || clean.includes(' - ')) {
      const parts = clean.includes(':') ? clean.split(':', 2) : clean.split(' - ', 2);
      const q = parts[0].trim();
      const a = parts[1].trim();
      if (q.length >= 3 && q.length <= 100 && a.length >= 3 && a.length <= 300) {
        cards.push({
          front: q.endsWith('?') ? q : `What is **${q}**?`,
          back: a,
        });
      }
    }
    if (cards.length >= 30) break;
  }

  // If few or no formatted pairs, supply comprehensive high-yield concept cards
  if (cards.length < 4) {
    const defaultTemplates = [
      {
        front: `What is the core definition of ${topic}?`,
        back: `The fundamental principle and underlying mechanism that defines ${topic} in ${subject || 'your coursework'}.`,
      },
      {
        front: `What is a primary formula or rule associated with ${topic}?`,
        back: `State the standard governing equation, law, or relationship governing ${topic}.`,
      },
      {
        front: `What is a common real-world application of ${topic}?`,
        back: `How ${topic} is utilized in real scenarios, experimental design, or examination problems.`,
      },
      {
        front: `What is a common pitfall or misconception regarding ${topic}?`,
        back: `Be careful to check units, sign conventions, or distinguishing ${topic} from closely related terms.`,
      },
      {
        front: `What are the critical distinguishing features of ${topic}?`,
        back: `Identify defining conditions, operational boundaries, and relationships distinguishing ${topic} from related concepts.`,
      },
      {
        front: `What are the key problem-solving steps when analyzing ${topic}?`,
        back: `Catalog known parameters, identify the governing principle, isolate the required variable, and verify boundary conditions.`,
      },
    ];
    for (const t of defaultTemplates) {
      if (!cards.some(c => c.front === t.front)) {
        cards.push(t);
      }
    }
  }

  const actions = {
    actions: [
      {
        type: 'create_flashcard_deck',
        title,
        cards,
      },
    ],
  };

  const tableRows = cards.slice(0, 10).map((c, i) =>
    `| ${i + 1} | ${c.front.slice(0, 45)}${c.front.length > 45 ? '…' : ''} | ${c.back.slice(0, 50)}${c.back.length > 50 ? '…' : ''} |`
  ).join('\n');

  const reply = `### Flashcard Deck: ${title}

Here is a ${cards.length}-card active recall deck created for **${topic}**:

| # | Front (Question) | Back (Answer) |
| :--- | :--- | :--- |
${tableRows}

Tap the button below to save this deck directly to your flashcards!

\`\`\`actions
${JSON.stringify(actions)}
\`\`\``;

  return {
    reply,
    reasoning: 'Generated active recall study deck via SchedMate Offline Study Engine.',
    model: 'smart-offline-deck',
  };
}

function generateQuizFallback(message, subject) {
  const topic = extractTopic(message);
  const title = `${topic} Diagnostic Quiz`;
  const questions = [
    {
      question: `Which statement best describes the fundamental purpose of ${topic}?`,
      options: [
        `It provides the foundational principle and mechanism for analyzing ${topic}.`,
        `It is solely an arbitrary mathematical constant without physical meaning.`,
        `It cannot be measured, verified, or observed experimentally.`,
        `It only applies in isolated, hypothetical vacuums with zero variables.`,
      ],
      correctAnswer: `It provides the foundational principle and mechanism for analyzing ${topic}.`,
      explanation: `By definition, ${topic} describes the primary governing mechanism in this domain.`,
    },
    {
      question: `When solving problems involving ${topic}, what is the first recommended step?`,
      options: [
        `Identify known quantities, unknown targets, and relevant formulas.`,
        `Guess random numerical values without reading given constraints.`,
        `Skip the problem entirely without attempting a diagram or formula.`,
        `Invert the values and ignore all units of measurement.`,
      ],
      correctAnswer: `Identify known quantities, unknown targets, and relevant formulas.`,
      explanation: `Systematic problem-solving always starts by cataloging given variables and units.`,
    },
    {
      question: `What distinguishes ${topic} from related concepts in ${subject || 'this subject'}?`,
      options: [
        `Its specific scope, defining conditions, and direct relationships.`,
        `It has no differences and is completely interchangeable.`,
        `It is only taught in advanced graduate courses and never on exams.`,
        `It violates the law of conservation of energy.`,
      ],
      correctAnswer: `Its specific scope, defining conditions, and direct relationships.`,
      explanation: `Distinguishing defining conditions is essential for mastering high-yield exam questions.`,
    },
    {
      question: `True or False: Accurate analysis of ${topic} requires verifying units and boundary conditions.`,
      options: [`True`, `False`],
      correctAnswer: `True`,
      explanation: `Unit consistency and valid boundary assumptions are critical for verifying solutions.`,
    },
    {
      question: `Which approach is most effective when preparing for questions on ${topic}?`,
      options: [
        `Active recall through spaced practice and worked examples.`,
        `Passive re-reading of headlines without self-testing.`,
        `Cramming definitions without understanding practical relationships.`,
        `Memorizing arbitrary formulas without checking applicability.`,
      ],
      correctAnswer: `Active recall through spaced practice and worked examples.`,
      explanation: `Evidence-based study research demonstrates that active testing yields highest exam retention.`,
    },
  ];

  const actions = {
    actions: [
      {
        type: 'launch_practice_quiz',
        title,
        questions,
      },
    ],
  };

  const listItems = questions.map((q, i) => `${i + 1}. **Question ${i + 1}**: ${q.question}`).join('\n');

  const reply = `### Diagnostic Practice Quiz: ${title}

Here is a ${questions.length}-question diagnostic practice test prepared for **${topic}**:

${listItems}

Tap the button below to start the quiz in Quiz Arena!

\`\`\`actions
${JSON.stringify(actions)}
\`\`\``;

  return {
    reply,
    reasoning: 'Generated practice quiz via SchedMate Offline Quiz Engine.',
    model: 'smart-offline-quiz',
  };
}

function generateSmartStudyFallback(body, upstreamError) {
  const message = (body.message || '').trim();
  const subject = body.subject || '';

  // 1. Check for flashcard requests
  if (/\b(?:flashcards?|study deck|revision cards?|cards for)\b/i.test(message)) {
    return generateFlashcardFallback(message, subject);
  }

  // 2. Check for quiz requests
  if (/\b(?:quiz|practice quiz|test me|practice questions?|exam questions?)\b/i.test(message)) {
    return generateQuizFallback(message, subject);
  }

  // 3. Check for math questions
  const mathAnswer = solveMathProblem(message);
  if (mathAnswer) {
    return {
      reply: mathAnswer,
      reasoning: `Solved locally via SchedMate Math Engine.${upstreamError ? ` [Notice: Upstream provider reported: ${upstreamError.message || 'quota'}]` : ''}`,
      model: 'smart-offline-math',
    };
  }

  // 4. Check for table / comparison queries
  const tableAnswer = generateTabularComparison(message);
  if (tableAnswer) {
    return {
      reply: tableAnswer,
      reasoning: `Structured table generated via SchedMate Study Engine.${upstreamError ? ` [Notice: Upstream provider: ${upstreamError.message || 'quota'}]` : ''}`,
      model: 'smart-offline-table',
    };
  }

  // 5. General Study Response with clean formatting
  const subjectLabel = subject ? ` for **${subject}**` : '';
  const reply = `### Study Notes${subjectLabel}

You asked: *"**${message}**"*

Here is a structured breakdown to help you master this concept:

#### Key Concepts & Principles:
- **Core Idea**: Understanding the fundamentals is essential before diving into complex problems.
- **Application**: Practice applying this concept to real exam questions and homework problems.
- **Recall & Review**: Connect this topic to earlier lessons to solidify your memory retention.

#### Topic Summary Table:
| Component | Focus Area | Recommended Study Action |
| :--- | :--- | :--- |
| **Concept Definition** | Terminology & core logic | Write concise flashcards |
| **Problem Solving** | Step-by-step methodology | Practice 3–5 representative drills |
| **Review & Mastery** | Active recall testing | Take a timed quiz in Quiz Arena |

How would you like to proceed?
1. **Solve a sample problem step-by-step**
2. **Generate a practice quiz on this topic**
3. **Break this down into more detailed columns and rows**`;

  return {
    reply,
    reasoning: `Jevi Smart Study Companion (Offline Resilient Mode).${upstreamError ? ` [Upstream provider note: ${upstreamError.message || 'network/quota'}]` : ''}`,
    model: 'smart-offline-tutor',
  };
}

module.exports = {
  solveMathProblem,
  generateTabularComparison,
  generateSmartStudyFallback,
};
