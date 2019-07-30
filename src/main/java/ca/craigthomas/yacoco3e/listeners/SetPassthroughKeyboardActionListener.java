/*
 * Copyright (C) 2017-2019 Craig Thomas
 * This project uses an MIT style license - see LICENSE for details.
 */
package ca.craigthomas.yacoco3e.listeners;

import ca.craigthomas.yacoco3e.components.EmulatedKeyboard;
import ca.craigthomas.yacoco3e.components.Emulator;
import ca.craigthomas.yacoco3e.components.PassthroughKeyboard;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * An ActionListener that will quit the emulator.
 */
public class SetPassthroughKeyboardActionListener extends AbstractFileChooserListener implements ActionListener
{
    private Emulator emulator;
    private JRadioButtonMenuItem emulated;
    private JRadioButtonMenuItem passthrough;

    public SetPassthroughKeyboardActionListener(Emulator emulator, JRadioButtonMenuItem passthrough, JRadioButtonMenuItem emulated) {
        super();
        this.emulator = emulator;
        this.emulated = emulated;
        this.passthrough = passthrough;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        setPassthroughKeyboard();
    }

    /**
     * Opens a dialog box to prompt the user to choose a cassette file
     * to open for playback.
     */
    public void setPassthroughKeyboard() {
        emulator.switchKeyListener(new PassthroughKeyboard());
        emulated.setSelected(false);
        passthrough.setSelected(true);
    }
}
