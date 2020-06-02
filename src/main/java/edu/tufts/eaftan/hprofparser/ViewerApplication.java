package edu.tufts.eaftan.hprofparser;

import edu.tufts.eaftan.hprofparser.viewer.gui.MainViewerForm;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ViewerApplication {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                                          "Failed to set \"look and feel\"!\nError message:\n" + e.getMessage(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> {
            MainViewerForm mainViewerForm = new MainViewerForm();
            mainViewerForm.setLocationRelativeTo(null);
            mainViewerForm.setVisible(true);
        });
    }
}
