package services.ui;

import services.PlaylistGeneratorApp;
import models.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Graphical User Interface for the Playlist Generator application
 */
public class GuiLauncher {
    private PlaylistGeneratorApp app;
    private ApplicationConfig config;
    private JFrame mainFrame;
    private JTabbedPane tabbedPane;
    
    public void start(ApplicationConfig config) {
        this.config = config;
        this.app = new PlaylistGeneratorApp(config.getDefaultUserId());
        
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set look and feel: " + e.getMessage());
        }
        
        // Create and show GUI
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }
    
    private void createAndShowGUI() {
        // Create main frame
        mainFrame = new JFrame("Playlist Generator");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 600);
        mainFrame.setLocationRelativeTo(null);
        
        // Create tabbed pane
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Generate Playlist", createGeneratePanel());
        tabbedPane.addTab("User Preferences", createPreferencesPanel());
        tabbedPane.addTab("Manage Playlists", createPlaylistsPanel());
        tabbedPane.addTab("Import Data", createImportPanel());
        
        // Add to frame
        mainFrame.add(tabbedPane);
        
        // Add menu bar
        mainFrame.setJMenuBar(createMenuBar());
        
        // Show frame
        mainFrame.setVisible(true);
    }
    
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        // File menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.add(new JMenuItem("Sync Data"));
        fileMenu.add(new JMenuItem("Export Playlist"));
        fileMenu.addSeparator();
        
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        
        // Help menu
        JMenu helpMenu = new JMenu("Help");
        helpMenu.add(new JMenuItem("Documentation"));
        helpMenu.add(new JMenuItem("About"));
        
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        
        return menuBar;
    }
    
    private JPanel createGeneratePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Form panel
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        
        JTextField nameField = new JTextField(20);
        JSpinner songCountSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
        JComboBox<String> strategyCombo = new JComboBox<>(new String[]{"Random", "Popular", "Diverse", "Balanced"});
        
        formPanel.add(new JLabel("Playlist Name:"), gbc);
        gbc.gridx = 1;
        formPanel.add(nameField, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Song Count:"), gbc);
        gbc.gridx = 1;
        formPanel.add(songCountSpinner, gbc);
        
        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JLabel("Selection Strategy:"), gbc);
        gbc.gridx = 1;
        formPanel.add(strategyCombo, gbc);
        
        // Genre selection
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JPanel genrePanel = new JPanel(new BorderLayout());
        genrePanel.setBorder(BorderFactory.createTitledBorder("Select Genres"));
        JList<String> genreList = new JList<>(new String[]{"Rock", "Pop", "Hip-Hop", "Jazz", "Classical", "Electronic"});
        genreList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        genrePanel.add(new JScrollPane(genreList), BorderLayout.CENTER);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        formPanel.add(genrePanel, gbc);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton generateButton = new JButton("Generate Playlist");
        generateButton.addActionListener(e -> {
            PlaylistParameters params = new PlaylistParameters();
            params.setName(nameField.getText());
            params.setSongCount((Integer)songCountSpinner.getValue());
            
            switch(strategyCombo.getSelectedIndex()) {
                case 0:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.RANDOM);
                    break;
                case 1:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.POPULAR);
                    break;
                case 2:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.DIVERSE);
                    break;
                default:
                    params.setSelectionStrategy(PlaylistParameters.PlaylistSelectionStrategy.BALANCED);
            }
            
            List<String> selectedGenres = genreList.getSelectedValuesList();
            for (String genre : selectedGenres) {
                params.addGenre(genre);
            }
            
            app.generatePlaylist(params);
            JOptionPane.showMessageDialog(mainFrame, "Playlist generated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(generateButton);
        
        // Add components to main panel
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createPreferencesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        
        // Favorite Artists section
        formPanel.add(new JLabel("Favorite Artists:"), gbc);
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        
        JTextArea artistsArea = new JTextArea(5, 20);
        artistsArea.setLineWrap(true);
        formPanel.add(new JScrollPane(artistsArea), gbc);
        
        // Favorite Genres section
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        formPanel.add(new JLabel("Favorite Genres:"), gbc);
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        
        JTextArea genresArea = new JTextArea(5, 20);
        genresArea.setLineWrap(true);
        formPanel.add(new JScrollPane(genresArea), gbc);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Save Preferences");
        saveButton.addActionListener(e -> {
            Map<String, Object> prefs = new HashMap<>();
            
            // Parse artists and genres
            String[] artists = artistsArea.getText().split("\n");
            String[] genres = genresArea.getText().split("\n");
            
            prefs.put("favorite_artists", artists);
            prefs.put("favorite_genres", genres);
            
            app.updateUserPreferences(prefs);
            JOptionPane.showMessageDialog(mainFrame, "Preferences updated successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
        });
        buttonPanel.add(saveButton);
        
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createPlaylistsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Create a split pane
        JSplitPane splitPane = new JSplitPane();
        splitPane.setDividerLocation(200);
        
        // Playlists list
        DefaultListModel<String> playlistModel = new DefaultListModel<>();
        playlistModel.addElement("Chill Vibes");
        playlistModel.addElement("Workout Mix");
        playlistModel.addElement("Top Hits 2023");
        
        JList<String> playlistList = new JList<>(playlistModel);
        JScrollPane playlistScroll = new JScrollPane(playlistList);
        
        // Songs table
        String[] columnNames = {"Title", "Artist", "Duration"};
        Object[][] data = {
            {"Lo-fi Dreams", "ChillBeats", "3:24"},
            {"Relaxing Waves", "AmbientSounds", "5:12"}
        };
        
        JTable songsTable = new JTable(data, columnNames);
        JScrollPane songsScroll = new JScrollPane(songsTable);
        
        splitPane.setLeftComponent(playlistScroll);
        splitPane.setRightComponent(songsScroll);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton deleteButton = new JButton("Delete Playlist");
        JButton exportButton = new JButton("Export to File");
        
        buttonPanel.add(deleteButton);
        buttonPanel.add(exportButton);
        
        panel.add(splitPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createImportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.gridx = 0;
        gbc.gridy = 0;
        
        // File selection
        formPanel.add(new JLabel("Import File:"), gbc);
        gbc.gridx = 1;
        
        JTextField filePathField = new JTextField(20);
        filePathField.setEditable(false);
        formPanel.add(filePathField, gbc);
        
        gbc.gridx = 2;
        JButton browseButton = new JButton("Browse");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(mainFrame);
            if (result == JFileChooser.APPROVE_OPTION) {
                filePathField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });
        formPanel.add(browseButton, gbc);
        
        // Import source selection
        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("Import from API:"), gbc);
        
        gbc.gridx = 1;
        String[] apis = {"Spotify", "Last.fm", "MusicBrainz", "Local Files"};
        JComboBox<String> apiCombo = new JComboBox<>(apis);
        formPanel.add(apiCombo, gbc);
        
        // Button panel
        JPanel buttonPanel = new JPanel();
        JButton importButton = new JButton("Import Data");
        importButton.addActionListener(e -> {
            if (!filePathField.getText().isEmpty() || apiCombo.getSelectedIndex() != 3) {
                // Import would happen here
                JOptionPane.showMessageDialog(mainFrame, "Data imported successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Please select a file or API to import from.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        buttonPanel.add(importButton);
        
        panel.add(formPanel, BorderLayout.NORTH);
        panel.add(new JPanel(), BorderLayout.CENTER); // Empty panel to fill space
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    // Static class to represent application configuration
    public static class ApplicationConfig {
        private String defaultUserId;
        private boolean firstRun;
        
        public String getDefaultUserId() {
            return defaultUserId;
        }
        
        public void setDefaultUserId(String userId) {
            this.defaultUserId = userId;
        }
        
        public boolean isFirstRun() {
            return firstRun;
        }
        
        public void setFirstRun(boolean firstRun) {
            this.firstRun = firstRun;
        }
    }
}