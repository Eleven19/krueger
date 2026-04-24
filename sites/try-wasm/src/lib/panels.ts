export type Panel = 'matches' | 'cst' | 'ast' | 'prettyQuery';

export type PanelInfo = {
  id: Panel;
  label: string;
  icon: string;
};

export const panels: PanelInfo[] = [
  { id: 'matches', label: 'Matches', icon: '⇢' },
  { id: 'cst', label: 'CST', icon: '◇' },
  { id: 'ast', label: 'AST', icon: '◆' },
  { id: 'prettyQuery', label: 'Canonical Query', icon: '≡' }
];

export const defaultPanel: Panel = 'matches';

export function panelLabel(panel: Panel): string {
  return panels.find((candidate) => candidate.id === panel)?.label ?? panel;
}
