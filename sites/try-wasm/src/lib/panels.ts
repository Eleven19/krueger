export type Panel = 'matches' | 'cst' | 'ast' | 'prettyQuery' | 'settings';

export type PanelInfo = {
  id: Panel;
  label: string;
  icon: string;
};

/**
 * Tabs in the activity bar that own visible result content. The
 * `settings` panel is intentionally NOT in this list — it is rendered as
 * a separate gear button anchored to the bottom of the activity bar so
 * it sits visually apart from the result tabs (mirrors VS Code's
 * activity-bar layout, where Settings lives at the bottom rail).
 */
export const panels: PanelInfo[] = [
  { id: 'matches', label: 'Matches', icon: '⇢' },
  { id: 'cst', label: 'CST', icon: '◇' },
  { id: 'ast', label: 'AST', icon: '◆' },
  { id: 'prettyQuery', label: 'Canonical Query', icon: '≡' }
];

export const settingsPanel: PanelInfo = {
  id: 'settings',
  label: 'Settings',
  icon: '⚙'
};

export const allPanels: PanelInfo[] = [...panels, settingsPanel];

export const defaultPanel: Panel = 'matches';

export function panelLabel(panel: Panel): string {
  return allPanels.find((candidate) => candidate.id === panel)?.label ?? panel;
}
