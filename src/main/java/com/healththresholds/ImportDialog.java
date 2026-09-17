package com.healththresholds;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

/**
 * Asks which of the rules on the clipboard to import, flagging the ones that would replace
 * a rule already in the list.
 */
class ImportDialog extends JDialog
{
	private static final Color CONFLICT_TEXT = new Color(228, 165, 60);
	private static final Color CONFLICT_ROW = new Color(58, 48, 30);

	private final Map<JCheckBox, NpcRule> rows = new LinkedHashMap<>();
	private final Set<String> conflicts;
	private final JLabel summary = new JLabel();
	private final JButton importButton = new JButton();

	@Nullable
	private List<NpcRule> selected;

	private ImportDialog(Window owner, List<NpcRule> imported, Set<String> conflicts)
	{
		super(owner, "Import NPC rules", ModalityType.APPLICATION_MODAL);
		this.conflicts = conflicts;

		JPanel root = new JPanel(new BorderLayout(0, 10));
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);
		root.setBorder(new EmptyBorder(12, 12, 12, 12));
		root.add(createHeader(imported, conflicts), BorderLayout.NORTH);
		root.add(createList(imported), BorderLayout.CENTER);
		root.add(createFooter(), BorderLayout.SOUTH);

		setContentPane(root);
		setMinimumSize(new Dimension(460, 320));
		setPreferredSize(new Dimension(460, Math.min(560, 210 + imported.size() * 32)));
		pack();
		setLocationRelativeTo(owner);
		updateSummary();
	}

	/**
	 * @return the rules to import, or null if cancelled
	 */
	@Nullable
	static List<NpcRule> show(Component parent, List<NpcRule> existing, List<NpcRule> imported)
	{
		Set<String> conflicts = ImportPlan.conflicts(existing, imported);
		// Conflicts first: they're the rows worth reading before importing
		List<NpcRule> ordered = new ArrayList<>(imported);
		ordered.sort(Comparator.comparing(rule -> conflicts.contains(ImportPlan.key(rule)) ? 0 : 1));

		ImportDialog dialog = new ImportDialog(
			javax.swing.SwingUtilities.getWindowAncestor(parent), ordered, conflicts);
		dialog.setVisible(true);
		return dialog.selected;
	}

	private JPanel createHeader(List<NpcRule> imported, Set<String> conflicts)
	{
		JPanel header = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		header.setOpaque(false);

		JLabel title = new JLabel("Import " + imported.size() + " NPC rule" + (imported.size() == 1 ? "" : "s"));
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title);

		JLabel subtitle = new JLabel(conflicts.isEmpty()
			? "None of these are in your list yet."
			: conflicts.size() + " are already in your list. Importing those replaces what you have.");
		subtitle.setFont(FontManager.getRunescapeSmallFont());
		subtitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.add(subtitle);

		JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
		quick.setOpaque(false);
		quick.add(quickButton("All", rule -> true));
		quick.add(quickButton("None", rule -> false));
		quick.add(quickButton("Only new", rule -> !conflicts.contains(ImportPlan.key(rule))));
		header.add(quick);

		return header;
	}

	private JButton quickButton(String text, java.util.function.Predicate<NpcRule> shouldCheck)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.addActionListener(e ->
		{
			rows.forEach((box, rule) -> box.setSelected(shouldCheck.test(rule)));
			updateSummary();
		});
		return button;
	}

	private JScrollPane createList(List<NpcRule> imported)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		list.setBorder(new EmptyBorder(6, 6, 6, 6));

		for (NpcRule rule : imported)
		{
			list.add(createRow(rule));
			list.add(Box.createVerticalStrut(2));
		}

		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private JPanel createRow(NpcRule rule)
	{
		boolean conflict = conflicts.contains(ImportPlan.key(rule));

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(conflict ? CONFLICT_ROW : ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(5, 6, 5, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JCheckBox box = new JCheckBox(rule.getName(), true);
		box.setOpaque(false);
		box.setForeground(Color.WHITE);
		box.setFont(FontManager.getRunescapeFont());
		box.addActionListener(e -> updateSummary());
		rows.put(box, rule);
		row.add(box, BorderLayout.WEST);

		JLabel detail = new JLabel(conflict ? "replaces yours" : describe(rule));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(conflict ? CONFLICT_TEXT : ColorScheme.LIGHT_GRAY_COLOR);
		detail.setHorizontalAlignment(SwingConstants.RIGHT);
		detail.setToolTipText(describe(rule));
		row.add(detail, BorderLayout.EAST);

		return row;
	}

	private JPanel createFooter()
	{
		JPanel footer = new JPanel(new BorderLayout(8, 0));
		footer.setOpaque(false);

		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		footer.add(summary, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);

		JButton cancel = new JButton("Cancel");
		cancel.setFont(FontManager.getRunescapeSmallFont());
		cancel.addActionListener(e -> dispose());
		buttons.add(cancel);

		importButton.setFont(FontManager.getRunescapeSmallFont());
		importButton.addActionListener(e ->
		{
			selected = checkedRules();
			dispose();
		});
		buttons.add(importButton);
		footer.add(buttons, BorderLayout.EAST);

		// Enter shouldn't replace rules by reflex, so Cancel takes the default
		getRootPane().setDefaultButton(cancel);
		return footer;
	}

	private List<NpcRule> checkedRules()
	{
		List<NpcRule> checked = new ArrayList<>();
		rows.forEach((box, rule) ->
		{
			if (box.isSelected())
			{
				checked.add(rule);
			}
		});
		return checked;
	}

	private void updateSummary()
	{
		int replacing = 0;
		int adding = 0;
		for (NpcRule rule : checkedRules())
		{
			if (conflicts.contains(ImportPlan.key(rule)))
			{
				replacing++;
			}
			else
			{
				adding++;
			}
		}

		int total = adding + replacing;
		int unchecked = rows.size() - total;
		StringBuilder text = new StringBuilder(adding + " new, " + replacing + " replace");
		if (unchecked > 0)
		{
			text.append(", ").append(unchecked).append(" unchecked");
		}
		summary.setText(text.toString());

		importButton.setText("Import " + total);
		importButton.setEnabled(total > 0);
	}

	private static String describe(NpcRule rule)
	{
		StringBuilder marks = new StringBuilder();
		for (NpcRule.Marker marker : rule.getMarkers())
		{
			if (marks.length() > 0)
			{
				marks.append(", ");
			}
			marks.append(new Threshold(marker.getValue(), marker.isPercent(), marker.isRepeating(), Color.WHITE).describe());
		}
		return marks.toString();
	}
}
