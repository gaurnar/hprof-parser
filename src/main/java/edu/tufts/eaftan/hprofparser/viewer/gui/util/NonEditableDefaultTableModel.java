package edu.tufts.eaftan.hprofparser.viewer.gui.util;

import javax.swing.table.DefaultTableModel;

public class NonEditableDefaultTableModel extends DefaultTableModel {

    public NonEditableDefaultTableModel(Object[] columnNames, int rowCount) {
        super(columnNames, rowCount);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
