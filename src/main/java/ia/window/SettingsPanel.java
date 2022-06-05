package ia.window;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import ia.terrain.Terrain;
import ia.window.AttractorsPanel.ColorFocus;
import ia.window.LayeredNetworkPanel.Alignment;

@SuppressWarnings("serial")
class SettingsPanel extends JPanel {
	record Settings(AttractorsPanel.Settings forAttractors, LayeredNetworkPanel.Settings forLayers) {
	}

	private static abstract class SubSettingsPanel<T> extends JPanel {
		public abstract T settings();
	}

	private final Settings settings;

	public SettingsPanel(Terrain terrain) {
		this.setLayout(new GridBagLayout());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = GridBagConstraints.RELATIVE;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.BASELINE_LEADING;
		constraints.weightx = 1;
		constraints.weighty = 1;

		SubSettingsPanel<AttractorsPanel.Settings> attractorsPanel = createAttractorsPanel(terrain);
		this.add(attractorsPanel, constraints);

		SubSettingsPanel<LayeredNetworkPanel.Settings> layeredNetworkPanel = createLayeredNetworkPanel();
		this.add(layeredNetworkPanel, constraints);

		this.settings = new Settings(attractorsPanel.settings(), layeredNetworkPanel.settings());
	}

	record FieldDefinition<T> (JLabel label, JComponent field, Supplier<T> reader) {
	}

	private SubSettingsPanel<AttractorsPanel.Settings> createAttractorsPanel(Terrain terrain) {
		AttractorsPanel.Settings[] settings = { null };
		SubSettingsPanel<AttractorsPanel.Settings> attractorsPanel = new SubSettingsPanel<AttractorsPanel.Settings>() {
			@Override
			public AttractorsPanel.Settings settings() {
				return settings[0];
			}
		};
		attractorsPanel.setBorder(new TitledBorder("Attractors"));

		Consumer<FieldDefinition<?>> fieldAdder = createSettingFieldAdder(attractorsPanel);

		FieldDefinition<Integer> maxStartPositions = createField(//
				"Max start positions", terrain.width() * terrain.height(), //
				Objects::toString, Integer::parseInt//
		);
		fieldAdder.accept(maxStartPositions);
		maxStartPositions.field.setEnabled(false);// TODO Remove to make it changeable

		FieldDefinition<Integer> maxRunsPerStartPosition = createField(//
				"Max runs per start position", 1, //
				Objects::toString, Integer::parseInt//
		);
		fieldAdder.accept(maxRunsPerStartPosition);

		FieldDefinition<Integer> maxIterationsPerRun = createField(//
				"Max iterations per run", terrain.width() + terrain.height(), //
				Objects::toString, Integer::parseInt//
		);
		fieldAdder.accept(maxIterationsPerRun);

		FieldDefinition<Integer> runAutoStopThreshold = createField(//
				"Run auto-stop threshold", 10, //
				Objects::toString, Integer::parseInt//
		);
		fieldAdder.accept(runAutoStopThreshold);

		FieldDefinition<ColorFocus> colorFocus = createField(//
				"Color focus", ColorFocus.MIN_MAX, //
				Objects::toString, ColorFocus::valueOf//
		);
		fieldAdder.accept(colorFocus);
		colorFocus.field.setEnabled(false);// TODO Remove to make it changeable

		settings[0] = new AttractorsPanel.Settings(//
				maxStartPositions.reader, //
				maxRunsPerStartPosition.reader, //
				maxIterationsPerRun.reader, //
				runAutoStopThreshold.reader, //
				colorFocus.reader//
		);

		return attractorsPanel;
	}
	
	private SubSettingsPanel<LayeredNetworkPanel.Settings> createLayeredNetworkPanel() {
		LayeredNetworkPanel.Settings[] settings = { null };
		SubSettingsPanel<LayeredNetworkPanel.Settings> layeredNetworkPanel = new SubSettingsPanel<LayeredNetworkPanel.Settings>() {
			@Override
			public LayeredNetworkPanel.Settings settings() {
				return settings[0];
			}
		};
		layeredNetworkPanel.setBorder(new TitledBorder("Layers"));

		Consumer<FieldDefinition<?>> fieldAdder = createSettingFieldAdder(layeredNetworkPanel);

		FieldDefinition<LayeredNetworkPanel.Alignment> alignment = createList(//
				"Alignment", List.of(Alignment.values()), Alignment.CENTER //
		);
		fieldAdder.accept(alignment);

		settings[0] = new LayeredNetworkPanel.Settings(//
				alignment.reader//
		);

		return layeredNetworkPanel;
	}

	private <T> Consumer<FieldDefinition<?>> createSettingFieldAdder(SubSettingsPanel<T> panel) {
		panel.setLayout(new GridBagLayout());
		GridBagConstraints labelConstraints = new GridBagConstraints();
		labelConstraints.gridx = 0;
		labelConstraints.gridy = 0;
		labelConstraints.anchor = GridBagConstraints.BASELINE_LEADING;
		labelConstraints.insets = new Insets(0, 5, 0, 5);
		GridBagConstraints fieldConstraints = new GridBagConstraints();
		fieldConstraints.gridx = 1;
		fieldConstraints.gridy = 0;
		fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
		fieldConstraints.weightx = 1;
		fieldConstraints.insets = new Insets(0, 0, 0, 5);

		return definition -> {
			panel.add(definition.label(), labelConstraints);
			panel.add(definition.field(), fieldConstraints);
			definition.label().setLabelFor(definition.field);// TODO Assign mnemonics
			labelConstraints.gridy++;
			fieldConstraints.gridy++;
		};
	}

	private <T> FieldDefinition<T> createField(String label, T defaultValue, Function<T, String> fieldWriter,
			Function<String, T> fieldReader) {
		JLabel jLabel = new JLabel(label + ":");
		JTextField field = new JTextField(fieldWriter.apply(defaultValue));
		Supplier<T> reader = () -> fieldReader.apply(field.getText());
		return new FieldDefinition<T>(jLabel, field, reader);
	}
	
	private <T> FieldDefinition<T> createList(String label, List<T> values, T defaultValue) {
		JLabel jLabel = new JLabel(label + ":");
		JComboBox<T> field = new JComboBox<>(new Vector<>(values));
		field.setSelectedItem(defaultValue);
		return new FieldDefinition<T>(jLabel, field, () -> field.getItemAt(field.getSelectedIndex()));
	}

	public Settings settings() {
		return settings;
	}
}
