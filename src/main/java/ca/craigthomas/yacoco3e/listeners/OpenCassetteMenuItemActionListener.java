/*
 * Copyright (C) 2017-2019 Craig Thomas
 * This project uses an MIT style license - see LICENSE for details.
 */
package ca.craigthomas.yacoco3e.listeners;

import ca.craigthomas.yacoco3e.components.Cassette;
import ca.craigthomas.yacoco3e.components.Emulator;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * An ActionListener that will quit the emulator.
 */
public class OpenCassetteMenuItemActionListener extends AbstractFileChooserListener implements ActionListener
{
    private Emulator emulator;

    private static final String FILE_CHOOSER_TITLE = "Open Cassette File";
    private static final String CASSETTE_FILE = "Cassette Files (*.cas)";
    private static final String CASSETTE_FILE_EXTENSION = "cas";
    private static final String FILE_OPEN_ERROR = "Error opening file.";
    private static final String FILE_ERROR_TITLE = "File Error";

    public OpenCassetteMenuItemActionListener(Emulator emulator) {
        super();
        this.emulator = emulator;
        this.fileChooserTitle = FILE_CHOOSER_TITLE;
        this.fileFilter = new FileNameExtensionFilter(CASSETTE_FILE, CASSETTE_FILE_EXTENSION);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        openCassetteFileDialog();
    }

    /**
     * Opens a dialog box to prompt the user to choose a cassette file
     * to open for playback.
     */
    public void openCassetteFileDialog() {
        JFrame container = emulator.getContainer();
        JFileChooser chooser = createFileChooser();
        if (chooser.showOpenDialog(container) == JFileChooser.APPROVE_OPTION) {
            Cassette cassette = emulator.getCassette();
            if (!cassette.openFile(chooser.getSelectedFile().toString())) {
                JOptionPane.showMessageDialog(container, FILE_OPEN_ERROR, FILE_ERROR_TITLE, JOptionPane.ERROR_MESSAGE);
            } else {
                cassette.play();
            }
        }
    }

}
