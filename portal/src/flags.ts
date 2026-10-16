// Prompts for the child-welfare reviewer, drawn from the research avoid list. They point a reader at wording to
// check; they are not a safety check and pass nothing on their own.

export interface FlagInput {
  text: string;
  cardCategories: string[];
  gesture?: string;
  sensitive: boolean;
}

const RULES: [RegExp, string][] = [
  [/\?.*\?/s, "More than one question at once (stacked questions)"],
  [/\bor\b/i, "Either/or question: the subject may pick one of the two offered"],
  [/,\s*(isn't|aren't|wasn't|weren't|didn't|don't|doesn't|won't|haven't|hasn't|right|okay|ok)\b[^?]*\?\s*$/i, "Tag question"],
  [/\bthe\b/i, 'Uses "the": check it does not presuppose something the subject has not said'],
  [
    /\b(someone|somebody|anyone|anybody|who|he|she|they)\b.*\b(hurt|hit|touch|touched|harm|afraid|scared|did)\b/i,
    "May presuppose a person or a harm the subject has not mentioned (ABE 3.71)",
  ],
];

export function flags(q: FlagInput): string[] {
  const out = RULES.filter(([re]) => re.test(q.text)).map(([, why]) => why);
  if (q.sensitive && q.cardCategories.includes("body")) {
    out.push("Body-part card in a sensitive question: no body maps or anatomical cards as a way into abuse questions");
  }
  if (q.gesture && /thumb|palm|stop/.test(q.gesture)) {
    out.push("Gesture may read as thumb-up, thumb-down or open palm: not to be used as yes, no or stop");
  }
  return out;
}
