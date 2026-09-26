package com.healththresholds;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.NumberFormatter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;

@Slf4j
class HealthIndicatorsPanel extends PluginPanel
{
	// Index order encodes the marker type: even = percent, odd = HP; 2+ = repeating
	private static final String[] UNITS = {"%", "HP", "Every %", "Every HP"};
	private static final double MAX_PERCENT = 100;
	// 5 digits fits the value field; far above any NPC's max HP
	private static final double MAX_HP = 99_999;
	private static final double MIN_STEP = 1;

	private final List<NpcRule> npcRules;
	private final Consumer<List<NpcRule>> onChange;
	private final Gson gson;
	@Nullable
	private final ColorPickerManager colorPickerManager;

	HealthIndicatorsPanel(List<NpcRule> npcRules, Consumer<List<NpcRule>> onChange, Gson gson, @Nullable ColorPickerManager colorPickerManager)
	{
		this.npcRules = npcRules;
		this.onChange = onChange;
		this.gson = gson;
		this.colorPickerManager = colorPickerManager;
		rebuild();
	}

	private void rebuild()
	{
		removeAll();

		JLabel title = new JLabel("Health Threshold Indicators");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		add(title);

		// Explicit breaks: Swing's HTML wrapping drops a word at the break under a width style
		JLabel help = new JLabel("<html>Add an NPC by name, or a regex.<br>Mark a % or an HP value, or repeat<br>one every % or HP.</html>");
		help.setFont(FontManager.getRunescapeSmallFont());
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(help);

		JButton addNpc = new JButton("Add NPC");
		addNpc.addActionListener(e ->
		{
			npcRules.add(new NpcRule());
			save();
			rebuild();
		});
		add(addNpc);

		JPanel importExport = new JPanel(new GridLayout(1, 2, 4, 0));
		importExport.setOpaque(false);
		JButton importButton = new JButton("Import");
		importButton.setToolTipText("Add NPC rules from JSON on the clipboard");
		importButton.addActionListener(e -> importFromClipboard());
		JButton exportButton = new JButton("Export");
		exportButton.setToolTipText("Copy all NPC rules to the clipboard as JSON");
		exportButton.addActionListener(e -> exportToClipboard());
		importExport.add(importButton);
		importExport.add(exportButton);
		add(importExport);

		for (NpcRule rule : npcRules)
		{
			add(createCard(rule));
		}

		revalidate();
		repaint();
	}

	private JPanel createCard(NpcRule rule)
	{
		JPanel card = new JPanel(new DynamicGridLayout(0, 1, 0, 4));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(6, 6, 6, 6));

		JTextField name = new JTextField(rule.getName());
		name.setToolTipText("NPC name, case-insensitive regex (e.g. Alchemical Hydra)");
		updateNameValidity(name);
		name.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				updateNameValidity(name);
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				updateNameValidity(name);
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				updateNameValidity(name);
			}
		});
		Runnable commitName = () ->
		{
			String text = name.getText().trim();
			if (!text.equals(rule.getName()))
			{
				rule.setName(text);
				save();
			}
		};
		name.addActionListener(e -> commitName.run());
		name.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commitName.run();
			}
		});
		card.add(name);

		for (NpcRule.Marker marker : rule.getMarkers())
		{
			card.add(createMarkerRow(rule, marker));
		}

		JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
		actions.setOpaque(false);
		JButton addMarker = new JButton("Add marker");
		addMarker.addActionListener(e ->
		{
			rule.getMarkers().add(new NpcRule.Marker());
			save();
			rebuild();
		});
		JButton removeNpc = new JButton("Remove");
		removeNpc.setToolTipText("Remove this NPC and its markers");
		removeNpc.addActionListener(e ->
		{
			npcRules.remove(rule);
			save();
			rebuild();
		});
		actions.add(addMarker);
		actions.add(removeNpc);
		card.add(actions);

		return card;
	}

	private JPanel createMarkerRow(NpcRule rule, NpcRule.Marker marker)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);

		// A plain number field rather than a spinner: the sidebar is too narrow for spinner arrows plus the full unit names
		NumberFormatter formatter = new NumberFormatter(new DecimalFormat("0.##"));
		formatter.setValueClass(Double.class);
		applyBounds(formatter, marker.isPercent(), marker.isRepeating());
		JFormattedTextField value = new JFormattedTextField(formatter);
		value.setHorizontalAlignment(JTextField.RIGHT);
		value.setToolTipText("Value; invalid entries revert when you click away");
		value.setValue(clamp(marker.getValue(), marker.isPercent(), marker.isRepeating()));
		value.addPropertyChangeListener("value", e ->
		{
			if (value.getValue() instanceof Number)
			{
				marker.setValue(((Number) value.getValue()).doubleValue());
				save();
			}
		});
		row.add(value, BorderLayout.CENTER);

		int controlHeight = value.getPreferredSize().height;
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		controls.setOpaque(false);

		JComboBox<String> unit = new JComboBox<>(UNITS);
		unit.setToolTipText("% or HP marks once; Every % / Every HP repeats the mark at that interval");
		unit.setPreferredSize(new Dimension(88, controlHeight));
		unit.setSelectedIndex((marker.isRepeating() ? 2 : 0) + (marker.isPercent() ? 0 : 1));
		unit.addActionListener(e ->
		{
			int index = unit.getSelectedIndex();
			boolean percent = index % 2 == 0;
			boolean repeating = index >= 2;
			double clamped = clamp(marker.getValue(), percent, repeating);
			marker.setPercent(percent);
			marker.setRepeating(repeating);
			marker.setValue(clamped);
			applyBounds(formatter, percent, repeating);
			value.setValue(clamped);
			save();
		});
		controls.add(unit);

		JLabel swatch = new JLabel();
		swatch.setOpaque(true);
		swatch.setToolTipText("Marker color");
		swatch.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
		swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		swatch.setPreferredSize(new Dimension(16, controlHeight));
		updateSwatch(swatch, marker.getColor());
		swatch.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				openColorPicker(swatch, marker);
			}
		});
		controls.add(swatch);

		JButton remove = new JButton("X");
		remove.setToolTipText("Remove marker");
		remove.addActionListener(e ->
		{
			rule.getMarkers().remove(marker);
			save();
			rebuild();
		});
		remove.setPreferredSize(new Dimension(20, controlHeight));
		controls.add(remove);

		row.add(controls, BorderLayout.EAST);
		return row;
	}

	private void openColorPicker(JLabel swatch, NpcRule.Marker marker)
	{
		if (colorPickerManager == null)
		{
			return;
		}

		RuneliteColorPicker picker = colorPickerManager.create(
			SwingUtilities.windowForComponent(this), new Color(marker.getColor(), true), "Marker color", false);
		picker.setLocation(swatch.getLocationOnScreen());
		picker.setOnColorChange(c ->
		{
			marker.setColor(c.getRGB());
			updateSwatch(swatch, c.getRGB());
		});
		picker.setOnClose(c -> save());
		picker.setVisible(true);
	}

	private void exportToClipboard()
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(gson.toJson(npcRules)), null);
		JOptionPane.showMessageDialog(this, "Copied " + npcRules.size() + " NPC rule(s) to the clipboard.",
			"Export", JOptionPane.INFORMATION_MESSAGE);
	}

	private void importFromClipboard()
	{
		List<NpcRule> imported;
		try
		{
			String json = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
			log.debug("import: clipboard held {} characters", json == null ? -1 : json.length());
			imported = NpcRule.sanitize(gson.fromJson(json, new TypeToken<ArrayList<NpcRule>>()
			{
			}.getType()));
			log.debug("import: parsed {} rules", imported.size());
		}
		// Reading the Windows clipboard can also throw IllegalStateException when another process holds it
		catch (Exception e)
		{
			log.warn("import: could not read rules from the clipboard", e);
			JOptionPane.showMessageDialog(this, "Couldn't read the clipboard: " + e.getMessage(),
				"Import", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (imported.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "The clipboard doesn't contain exported NPC rules.",
				"Import", JOptionPane.WARNING_MESSAGE);
			return;
		}

		List<NpcRule> chosen = ImportDialog.show(this, npcRules, imported);
		if (chosen == null || chosen.isEmpty())
		{
			log.debug("import: cancelled");
			return;
		}

		int replaced = ImportPlan.conflicts(npcRules, chosen).size();
		List<NpcRule> merged = ImportPlan.apply(npcRules, chosen);
		npcRules.clear();
		npcRules.addAll(merged);
		save();
		rebuild();
		log.debug("import: {} rules chosen, {} replaced, {} total", chosen.size(), replaced, npcRules.size());
	}

	private void save()
	{
		onChange.accept(npcRules);
	}

	private static double clamp(double value, boolean percent, boolean repeating)
	{
		return Math.max(repeating ? MIN_STEP : 0, Math.min(value, percent ? MAX_PERCENT : MAX_HP));
	}

	private static void applyBounds(NumberFormatter formatter, boolean percent, boolean repeating)
	{
		formatter.setMinimum(repeating ? MIN_STEP : 0.0);
		formatter.setMaximum(percent ? MAX_PERCENT : MAX_HP);
	}

	private static void updateSwatch(JLabel swatch, int argb)
	{
		// Opaque Swing components can't paint translucent backgrounds cleanly, so show the solid color
		swatch.setBackground(new Color(argb & 0xFFFFFF));
	}

	private static void updateNameValidity(JTextField name)
	{
		boolean valid = RuleCompiler.compilePattern(name.getText()) != null;
		name.setForeground(valid ? Color.WHITE : ColorScheme.PROGRESS_ERROR_COLOR);
	}

	static BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 5, 16, 6);
		g.setColor(new Color(0, 180, 0));
		g.fillRect(1, 6, 9, 4);
		g.setColor(new Color(180, 0, 0));
		g.fillRect(10, 6, 5, 4);
		g.setColor(Color.WHITE);
		g.fillRect(5, 3, 1, 10);
		g.fillRect(10, 3, 1, 10);
		g.dispose();
		return icon;
	}
}
