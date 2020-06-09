package edu.tufts.eaftan.hprofparser.viewer.gui;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceClassInstanceField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceObjectArrayField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceObjectField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstancePrimitiveArrayField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpObject;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpObjectArrayInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpPrimitiveArrayInstance;
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

public class ObjectViewerForm extends JFrame {

    private static final int ARRAY_ELEMENTS_BATCH_SIZE = 10;

    private JTable objectPropertiesTable;
    private JPanel rootPanel;
    private JButton prevButton;
    private JButton nextButton;

    private final HprofViewer viewer;

    private List<ViewedObject> objectHistoryList = new ArrayList<>();
    private int instanceHistoryPosition;

    public ObjectViewerForm(HprofViewer viewer, JFrame parent) {
        this.viewer = viewer;

        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                objectHistoryList = null;
            }
        });

        prevButton.addActionListener(e -> goToObject(--instanceHistoryPosition));

        nextButton.addActionListener(e -> goToObject(++instanceHistoryPosition));

        objectPropertiesTable.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (objectPropertiesTable.getSelectedRow() == -1) {
                    return;
                }

                ViewedObject currentViewedObject = objectHistoryList.get(instanceHistoryPosition);

                if (e.getClickCount() == 2) {
                    currentViewedObject.handleDoubleClick(objectPropertiesTable.getSelectedRow());
                } else if (e.getClickCount() == 1) {
                    currentViewedObject.handleClick(objectPropertiesTable.getSelectedRow());
                }
            }
        });

        prevButton.setEnabled(false);
        nextButton.setEnabled(false);

        setContentPane(rootPanel);
        pack();
    }

    public void showInstance(HeapDumpClassInstance classInstance) {
        addAndShowObject(new ViewedClassInstance(classInstance));
    }

    public void showObjectArray(long arrayObjId) {
        addAndShowObject(new ViewedObjectArrayInstance(arrayObjId));
    }

    public void showPrimitiveArray(long arrayObjId) {
        addAndShowObject(new ViewedPrimitiveArrayInstance(arrayObjId));
    }

    private void addAndShowObject(ViewedObject object) {
        if (instanceHistoryPosition < objectHistoryList.size() - 1) {
            objectHistoryList.subList(instanceHistoryPosition + 1, objectHistoryList.size()).clear();
        }

        objectHistoryList.add(object);

        instanceHistoryPosition = objectHistoryList.size() - 1;

        object.show();
        updatePrevNextButtonsState();
    }

    private void goToObject(int newInstanceHistoryPosition) {
        instanceHistoryPosition = newInstanceHistoryPosition;
        objectHistoryList.get(instanceHistoryPosition).show();
        updatePrevNextButtonsState();
    }

    private void updatePrevNextButtonsState() {
        int lastHistoryPosition = objectHistoryList.size() - 1;

        nextButton.setEnabled(instanceHistoryPosition < lastHistoryPosition);
        prevButton.setEnabled(instanceHistoryPosition > 0);
    }

    private abstract static class ViewedObject {
        abstract void show();
        abstract void handleClick(int elementIndex);
        abstract void handleDoubleClick(int elementIndex);
    }

    private abstract class ViewedArrayObject<T> extends ViewedObject {
        protected final long objId;
        protected List<T> loadedElements;
        private boolean hasMoreElements;

        protected ViewedArrayObject(long objId) {
            this.objId = objId;

            loadedElements = loadElements(0, ARRAY_ELEMENTS_BATCH_SIZE);
            hasMoreElements = loadedElements.size() == ARRAY_ELEMENTS_BATCH_SIZE;
        }

        abstract List<T> loadElements(int offset, int limit);

        abstract String getElementRowText(T element, int index);

        @Override
        void show() {
            if (loadedElements.isEmpty()) {
                TableModel model = new NonEditableDefaultTableModel(new Object[] {""}, 1);
                model.setValueAt("<no elements>", 0, 0);
                objectPropertiesTable.setModel(model);
                return;
            }

            TableModel model = new NonEditableDefaultTableModel(
                new Object[] {""}, hasMoreElements ? loadedElements.size() + 1 : loadedElements.size());

            for (int i = 0; i < loadedElements.size(); i++) {
                model.setValueAt(getElementRowText(loadedElements.get(i), i), i, 0);
            }

            if (hasMoreElements) {
                model.setValueAt("click to load more...", loadedElements.size(), 0);
            }

            objectPropertiesTable.setModel(model);
        }

        @Override
        void handleClick(int elementIndex) {
            if (elementIndex != loadedElements.size()) {
                // only "click to load more..." is clickable
                return;
            }

            loadMoreAndUpdateModel();
        }

        private void loadMoreAndUpdateModel() {
            List<T> newElements = loadElements(loadedElements.size(), ARRAY_ELEMENTS_BATCH_SIZE);

            hasMoreElements = newElements.size() == ARRAY_ELEMENTS_BATCH_SIZE;

            NonEditableDefaultTableModel model = (NonEditableDefaultTableModel) objectPropertiesTable.getModel();
            model.removeRow(loadedElements.size()); // "click to load more..." row

            for (int i = 0; i < newElements.size(); i++) {
                model.addRow(new Object[]{getElementRowText(newElements.get(i), loadedElements.size() + i)});
            }

            if (hasMoreElements) {
                model.addRow(new Object[]{"click to load more..."});
            }

            loadedElements.addAll(newElements);
        }
    }

    private class ViewedClassInstance extends ViewedObject {
        final HeapDumpClassInstance instance;

        private ViewedClassInstance(HeapDumpClassInstance instance) {
            this.instance = instance;
        }

        @Override
        void show() {
            setTitle(String.format("%s id=%s", instance.getClassName(), instance.getId()));

            if (instance.getInstanceFields().isEmpty()) {
                TableModel model = new NonEditableDefaultTableModel(new Object[] {""}, 1);
                model.setValueAt("<no visible fields>", 0, 0);
                objectPropertiesTable.setModel(model);
                return;
            }

            TableModel model = new NonEditableDefaultTableModel(new Object[] {"field", "value"},
                                                                instance.getInstanceFields().size());

            for (int i = 0; i < instance.getInstanceFields().size(); i++) {
                HeapDumpClassInstanceField field = instance.getInstanceFields().get(i);

                model.setValueAt(field.getFieldName(), i, 0);
                model.setValueAt(ObjectViewUtil.showFieldValue(field), i, 1);
            }

            objectPropertiesTable.setModel(model);
        }

        @Override
        void handleClick(int elementIndex) {
            // doing nothing
        }

        @Override
        void handleDoubleClick(int elementIndex) {
            HeapDumpClassInstanceField instanceFieldToView =
                instance.getInstanceFields().get(objectPropertiesTable.getSelectedRow());

            if (!(instanceFieldToView instanceof HeapDumpClassInstanceObjectField)) {
                return;
            }

            long objId = ((HeapDumpClassInstanceObjectField) instanceFieldToView).getObjId();

            if (objId == 0) {
                return;
            }

            if (instanceFieldToView instanceof HeapDumpClassInstanceClassInstanceField) {
                showInstance(viewer.showClassInstance(objId));
            } else if (instanceFieldToView instanceof HeapDumpClassInstanceObjectArrayField) {
                showObjectArray(objId);
            } else if (instanceFieldToView instanceof HeapDumpClassInstancePrimitiveArrayField) {
                showPrimitiveArray(objId);
            }
        }
    }

    private class ViewedObjectArrayInstance extends ViewedArrayObject<HeapDumpObject> {

        private ViewedObjectArrayInstance(long objId) {
            super(objId);
        }

        @Override
        List<HeapDumpObject> loadElements(int offset, int limit) {
            return viewer.listObjectArrayElements(objId, offset, limit);
        }

        @Override
        String getElementRowText(HeapDumpObject element, int index) {
            // TODO type name
           return String.format("#%d", index + 1);
        }

        @Override
        void show() {
            setTitle(String.format("Object array id=%s", objId)); // TODO type name

            super.show();
        }

        @Override
        void handleDoubleClick(int elementIndex) {
            if (elementIndex == loadedElements.size()) {
                // "click to load more..." is not double-clickable
                return;
            }

            HeapDumpObject objectToView = loadedElements.get(objectPropertiesTable.getSelectedRow());

            if (objectToView.getId() == 0) {
                return; // null
            }

            if (objectToView instanceof HeapDumpClassInstance) {
                showInstance((HeapDumpClassInstance) objectToView);
            } else if (objectToView instanceof HeapDumpObjectArrayInstance) {
                showObjectArray(objectToView.getId());
            } else if (objectToView instanceof HeapDumpPrimitiveArrayInstance) {
                showPrimitiveArray(objectToView.getId());
            }
        }
    }

    private class ViewedPrimitiveArrayInstance extends ViewedArrayObject<String> {

        private ViewedPrimitiveArrayInstance(long objId) {
            super(objId);
        }

        @Override
        List<String> loadElements(int offset, int limit) {
            return viewer.listPrimitiveArrayElements(objId, offset, limit);
        }

        @Override
        String getElementRowText(String element, int index) {
            return String.format("#%d %s", index + 1, element);
        }

        @Override
        void show() {
            setTitle(String.format("Primitive array id=%s", objId)); // TODO type name

            super.show();
        }

        @Override
        void handleDoubleClick(int elementIndex) {
            // doing nothing
        }
    }
}
