package edu.tufts.eaftan.hprofparser.viewer.gui;

import javax.swing.JDialog;
import javax.swing.JPanel;

public class WaitDialog extends JDialog {

    private JPanel contentPane;

    public WaitDialog() {
        setContentPane(contentPane);
        setModal(true);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);

        pack();
    }
}
