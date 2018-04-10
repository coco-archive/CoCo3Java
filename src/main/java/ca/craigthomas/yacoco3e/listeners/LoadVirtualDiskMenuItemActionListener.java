/*
 * Copyright (C) 2017 Craig Thomas
 * This project uses an MIT style license - see LICENSE for details.
 */
package ca.craigthomas.yacoco3e.listeners;

import ca.craigthomas.yacoco3e.components.Emulator;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * An ActionListener that will load a virtual disk.
 */
public class LoadVirtualDiskMenuItemActionListener implements ActionListener
{
    // The main emulator class
    private Emulator emulator;
    // The drive number to apply the loaded disk to
    private int drive;

    public LoadVirtualDiskMenuItemActionListener(int drive, Emulator emulator) {
        super();
        this.emulator = emulator;
        this.drive = drive;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        emulator.openVirtualDiskFileDialog(drive);
    }
}
