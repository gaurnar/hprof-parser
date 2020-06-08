package edu.tufts.eaftan.hprofparser.viewer.gui;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceObjectField;
import edu.tufts.eaftan.hprofparser.viewer.gui.util.NonEditableDefaultTableModel;
import edu.tufts.eaftan.hprofparser.viewer.gui.util.ObjectViewUtil;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.MouseInputAdapter;
import javax.swing.table.TableModel;

public class ClassInstanceViewerForm extends JFrame {

    public interface HeapDumpClassInstanceProvider {
        HeapDumpClassInstance provide(long instanceId);
    }

    private JTable objectPropertiesTable;
    private JPanel rootPanel;
    private JButton prevButton;
    private JButton nextButton;

    private final HeapDumpClassInstanceProvider instanceProvider;

    private List<HeapDumpClassInstance> classInstanceHistoryList = new ArrayList<>();
    private int instanceHistoryPosition;

    public ClassInstanceViewerForm(HeapDumpClassInstance firstInstance,
                                   HeapDumpClassInstanceProvider instanceProvider,
                                   JFrame parent) {
        setLocationRelativeTo(parent);

        this.instanceProvider = instanceProvider;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                classInstanceHistoryList = null;
            }
        });

        prevButton.addActionListener(e -> goToInstance(--instanceHistoryPosition));

        nextButton.addActionListener(e -> goToInstance(++instanceHistoryPosition));

        objectPropertiesTable.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || objectPropertiesTable.getSelectedRow() == -1) {
                    return;
                }

                HeapDumpClassInstance currentInstance = classInstanceHistoryList.get(instanceHistoryPosition);

                HeapDumpClassInstanceField instanceFieldToView =
                    currentInstance.getInstanceFields().get(objectPropertiesTable.getSelectedRow());

                if (!(instanceFieldToView instanceof HeapDumpClassInstanceObjectField)) {
                    return;
                }

                long objIdToView = ((HeapDumpClassInstanceObjectField) instanceFieldToView).getObjId();

                if (objIdToView == 0) {
                    return;
                }

                showInstance(instanceProvider.provide(objIdToView));
            }
        });

        showInstance(firstInstance);

        setContentPane(rootPanel);
        pack();
    }

    public void showInstance(HeapDumpClassInstance instance) {
        if (instanceHistoryPosition < classInstanceHistoryList.size() - 1) {
            classInstanceHistoryList.subList(instanceHistoryPosition + 1, classInstanceHistoryList.size()).clear();
        }

        classInstanceHistoryList.add(instance);
        fillInterfaceForInstance(instance);

        instanceHistoryPosition = classInstanceHistoryList.size() - 1;
        updatePrevNextButtonsState();
    }

    public void goToInstance(int newInstanceHistoryPosition) {
        instanceHistoryPosition = newInstanceHistoryPosition;
        fillInterfaceForInstance(classInstanceHistoryList.get(instanceHistoryPosition));
        updatePrevNextButtonsState();
    }

    private void updatePrevNextButtonsState() {
        int lastHistoryPosition = classInstanceHistoryList.size() - 1;

        nextButton.setEnabled(instanceHistoryPosition < lastHistoryPosition);
        prevButton.setEnabled(instanceHistoryPosition > 0);
    }

    private void fillInterfaceForInstance(HeapDumpClassInstance instance) {
        setTitle(String.format("%s id=%s", instance.getClassName(), instance.getId()));

        TableModel model = new NonEditableDefaultTableModel(new Object[] {"field", "value"},
                                                            instance.getInstanceFields().size());

        for (int i = 0; i < instance.getInstanceFields().size(); i++) {
            HeapDumpClassInstanceField field = instance.getInstanceFields().get(i);

            model.setValueAt(field.getFieldName(), i, 0);
            model.setValueAt(ObjectViewUtil.showFieldValue(field), i, 1);
        }

        objectPropertiesTable.setModel(model);
    }
}
