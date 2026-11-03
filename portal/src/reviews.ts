// Verdicts live in this browser only; the JSON export is the review record.

export type Verdict = "A" | "C" | "R";
/** Sections of the export: gesture, phrase (pid.card), question, ui, greeting, clip (sentence). */
export type Section = "gesture" | "phrase" | "question" | "ui" | "greeting" | "clip";

export interface Review {
  section: Section;
  id: string;
  role: string;
  verdict: Verdict | null;
  notes: string;
}

export const FORMAT = "signsetu-content-review";
const STORE = "signsetu.reviews.v1";

export const keyOf = (r: Pick<Review, "section" | "id" | "role">) => `${r.role}|${r.section}|${r.id}`;

export function load(): Record<string, Review> {
  try {
    const all: unknown = JSON.parse(localStorage.getItem(STORE) ?? "{}");
    if (!all || typeof all !== "object" || Array.isArray(all)) return {};
    // A hand-edited or older store may hold entries without notes; those would break the export, so they are dropped.
    return Object.fromEntries(
      Object.entries(all as Record<string, Review>).filter(([, r]) => r && typeof r === "object" && typeof r.notes === "string"),
    );
  } catch {
    return {};
  }
}

export function save(all: Record<string, Review>) {
  try {
    localStorage.setItem(STORE, JSON.stringify(all));
  } catch {
    // Private windows can refuse storage; the export still works for this visit.
  }
}

export function exportJson(all: Record<string, Review>, now = new Date()): string {
  const reviews = Object.values(all)
    .filter((r) => r.verdict || r.notes.trim())
    .sort((a, b) => keyOf(a).localeCompare(keyOf(b)));
  return JSON.stringify({ format: FORMAT, version: 1, exported: now.toISOString(), reviews }, null, 2);
}
