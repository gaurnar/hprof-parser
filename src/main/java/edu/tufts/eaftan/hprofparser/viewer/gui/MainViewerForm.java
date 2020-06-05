package edu.tufts.eaftan.hprofparser.viewer.gui;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpArrayInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClass;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceObjectField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstancePrimitiveField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassObject;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpObject;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpObjectArray;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpPrimitiveArray;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpPrimitiveArrayInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpType;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;

public class MainViewerForm extends JFrame {

    private static final int CLASS_INSTANCES_BATCH_SIZE = 10;

    private JButton loadDumpButton;
    private JTree dumpTree;
    private JPanel rootPanel;
    private JTable objectPropertiesTable;

    private DefaultTreeModel dumpTreeModel;
    private HprofViewer viewer;

    public MainViewerForm() {
        setTitle("Hprof Viewer");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        dumpTreeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Use \"Load dump...\" to open .hprof file"));
        dumpTree.setModel(dumpTreeModel);

        clearObjectPropertiesTableAndShowHint();

        setContentPane(rootPanel);
        pack();

        loadDumpButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();

            int result = fileChooser.showOpenDialog(MainViewerForm.this);

            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();

                loadDumpButton.setEnabled(false);

                WaitDialog waitDialog = new WaitDialog();
                waitDialog.setLocationRelativeTo(MainViewerForm.this);

                SwingUtilities.invokeLater(() -> {
                    try {
                        viewer = new HprofViewer(selectedFile);
                        fillTypeNodes();
                    } catch (Throwable t) {
                        showError(t, "Failed to load dump!");
                        t.printStackTrace();
                    }
                    waitDialog.dispose();
                    loadDumpButton.setEnabled(true);
                });

                waitDialog.setVisible(true);
            }
        });

        dumpTree.addTreeSelectionListener(e -> {
            if (e.getNewLeadSelectionPath() == null) {
                clearObjectPropertiesTableAndShowHint();
                return;
            }

            DefaultMutableTreeNode treeNode =
                (DefaultMutableTreeNode) e.getNewLeadSelectionPath().getLastPathComponent();
            if (treeNode.getUserObject() instanceof ClassInstanceNodeData) {
                ClassInstanceNodeData instanceData = (ClassInstanceNodeData) treeNode.getUserObject();

                List<String> fieldNames = instanceData.fieldPreviews.keySet().stream()
                    .sorted()
                    .collect(Collectors.toList());

                TableModel model;

                if (fieldNames.isEmpty()) {
                    model = new NonEditableDefaultTableModel(1, 1);
                    model.setValueAt("<no visible fields>", 0, 0);
                } else {
                    model = new NonEditableDefaultTableModel(fieldNames.size(), 2);

                    for (int i = 0; i < fieldNames.size(); i++) {
                        String fieldName = fieldNames.get(i);
                        model.setValueAt(fieldName, i, 0);
                        model.setValueAt(instanceData.fieldPreviews.get(fieldName), i, 1);
                    }
                }

                objectPropertiesTable.setModel(model);

            } else {
                clearObjectPropertiesTableAndShowHint();

                if (treeNode.getUserObject() instanceof LoadMoreInstancesPlaceholder) {
                    LoadMoreInstancesPlaceholder placeholder = (LoadMoreInstancesPlaceholder) treeNode.getUserObject();

                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) treeNode.getParent();

                    if (parentNode.getUserObject() instanceof TypeNodeData) {
                        TypeNodeData typeNodeData = (TypeNodeData) parentNode.getUserObject();

                        if (typeNodeData.viewerType instanceof HeapDumpClass) {
                            HeapDumpClass heapDumpClass = (HeapDumpClass) typeNodeData.viewerType;

                            SwingUtilities.invokeLater(() -> {
                                try {
                                    List<HeapDumpClassInstance> instances =
                                        viewer.listClassInstances(heapDumpClass.getClassId(),
                                                                  placeholder.offset,
                                                                  CLASS_INSTANCES_BATCH_SIZE);

                                    treeNode.removeFromParent();

                                    addClassInstancesToTypeNode(placeholder.offset, instances, parentNode);
                                } catch (Throwable t) {
                                    showError(t, "Failed to list children!");
                                    t.printStackTrace();
                                }
                            });
                        } else if (typeNodeData.viewerType instanceof HeapDumpObjectArray) {
                            // TODO remove copy paste with above
                            HeapDumpObjectArray objectArray = (HeapDumpObjectArray) typeNodeData.viewerType;

                            SwingUtilities.invokeLater(() -> {
                                try {
                                    List<HeapDumpArrayInstance> instances =
                                        viewer.listObjectArrayInstances(objectArray.getElementClassId(),
                                                                        placeholder.offset,
                                                                        CLASS_INSTANCES_BATCH_SIZE);

                                    treeNode.removeFromParent();

                                    addArrayInstancesToTypeNode(placeholder.offset, instances, parentNode);
                                } catch (Throwable t) {
                                    showError(t, "Failed to list children!");
                                    t.printStackTrace();
                                }
                            });
                        } else if (typeNodeData.viewerType instanceof HeapDumpPrimitiveArray) {
                            // TODO remove copy paste with above
                            HeapDumpPrimitiveArray primitiveArray = (HeapDumpPrimitiveArray) typeNodeData.viewerType;

                            SwingUtilities.invokeLater(() -> {
                                try {
                                    List<HeapDumpArrayInstance> instances =
                                        viewer.listPrimitiveArrayInstances(primitiveArray.getPrimitiveType(),
                                                                           placeholder.offset,
                                                                           CLASS_INSTANCES_BATCH_SIZE);

                                    treeNode.removeFromParent();

                                    addArrayInstancesToTypeNode(placeholder.offset, instances, parentNode);
                                } catch (Throwable t) {
                                    showError(t, "Failed to list children!");
                                    t.printStackTrace();
                                }
                            });
                        }
                    } else if (parentNode.getUserObject() instanceof ArrayInstanceNodeData) {
                        ArrayInstanceNodeData arrayInstanceNodeData =
                            (ArrayInstanceNodeData) parentNode.getUserObject();

                        if (arrayInstanceNodeData.isPrimitive) {
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    List<String> values = viewer.listPrimitiveArrayElements(arrayInstanceNodeData.id,
                                                                                            placeholder.offset,
                                                                                            CLASS_INSTANCES_BATCH_SIZE);

                                    treeNode.removeFromParent();

                                    // TODO remove copy paste
                                    addLoadableObjectsToNode(placeholder.offset, values,
                                                             (value, index) ->
                                                                 new DefaultMutableTreeNode(
                                                                     String.format("#%d %s", index + 1, value)),
                                                             parentNode);
                                } catch (Throwable t) {
                                    showError(t, "Failed to list children!");
                                    t.printStackTrace();
                                }
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    List<HeapDumpObject> objects =
                                        viewer.listObjectArrayElements(arrayInstanceNodeData.id, 0,
                                                                       CLASS_INSTANCES_BATCH_SIZE);

                                    treeNode.removeFromParent();

                                    // TODO remove copy paste
                                    addLoadableObjectsToNode(placeholder.offset, objects, (heapDumpObject, index) -> {
                                        if (heapDumpObject == null) {
                                            return new DefaultMutableTreeNode(String.format("#%d <null>", index + 1));
                                        } else if (heapDumpObject instanceof HeapDumpClassObject) {
                                            HeapDumpClassObject classObject = (HeapDumpClassObject) heapDumpObject;
                                            return new DefaultMutableTreeNode(String.format(
                                                "#%d <Class<%s>>", index + 1, classObject.getClassName()));
                                        } else if (heapDumpObject instanceof HeapDumpClassInstance) {
                                            return createClassInstanceTreeNode((HeapDumpClassInstance) heapDumpObject, index);
                                        } else if (heapDumpObject instanceof HeapDumpArrayInstance) {
                                            return createArrayTreeNode((HeapDumpArrayInstance) heapDumpObject, index);
                                        } else {
                                            throw new RuntimeException("unexpected object type");
                                        }
                                    }, parentNode);
                                } catch (Throwable t) {
                                    showError(t, "Failed to list children!");
                                    t.printStackTrace();
                                }
                            });
                        }
                    }
                }
            }
        });

        dumpTree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();

                if (treeNode.getUserObject() instanceof TypeNodeData) {
                    if (!treeNode.getChildAt(0).toString().equals("loading...")) {
                        // we have already loaded some children
                        return;
                    }

                    TypeNodeData typeData = (TypeNodeData) treeNode.getUserObject();

                    if (typeData.viewerType instanceof HeapDumpClass) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                List<HeapDumpClassInstance> instances =
                                    viewer.listClassInstances(((HeapDumpClass) typeData.viewerType).getClassId(),
                                                              0, CLASS_INSTANCES_BATCH_SIZE);

                                treeNode.removeAllChildren();

                                addClassInstancesToTypeNode(0, instances, treeNode);
                            } catch (Throwable t) {
                                showError(t, "Failed to list children!");
                                t.printStackTrace();
                            }
                        });
                    } else if (typeData.viewerType instanceof HeapDumpObjectArray) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                List<HeapDumpArrayInstance> instances = viewer.listObjectArrayInstances(
                                    ((HeapDumpObjectArray) typeData.viewerType).getElementClassId(),
                                    0, CLASS_INSTANCES_BATCH_SIZE);

                                treeNode.removeAllChildren();

                                addArrayInstancesToTypeNode(0, instances, treeNode);
                            } catch (Throwable t) {
                                showError(t, "Failed to list children!");
                                t.printStackTrace();
                            }
                        });
                    } else if (typeData.viewerType instanceof HeapDumpPrimitiveArray) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                List<HeapDumpArrayInstance> instances = viewer.listPrimitiveArrayInstances(
                                    ((HeapDumpPrimitiveArray) typeData.viewerType).getPrimitiveType(),
                                    0, CLASS_INSTANCES_BATCH_SIZE);

                                treeNode.removeAllChildren();

                                addArrayInstancesToTypeNode(0, instances, treeNode);
                            } catch (Throwable t) {
                                showError(t, "Failed to list children!");
                                t.printStackTrace();
                            }
                        });
                    }
                } else if (treeNode.getUserObject() instanceof ArrayInstanceNodeData) {
                    ArrayInstanceNodeData arrayData = (ArrayInstanceNodeData) treeNode.getUserObject();

                    if (arrayData.isPrimitive) {
                        List<String> values = viewer.listPrimitiveArrayElements(arrayData.id,
                                                                                0,
                                                                                CLASS_INSTANCES_BATCH_SIZE);

                        treeNode.removeAllChildren();

                        addLoadableObjectsToNode(0, values,
                                                 (value, index) ->
                                                     new DefaultMutableTreeNode(
                                                         String.format("#%d %s", index + 1, value)),
                                                 treeNode);
                    } else {
                        List<HeapDumpObject> objects = viewer.listObjectArrayElements(arrayData.id,
                                                                                        0,
                                                                                        CLASS_INSTANCES_BATCH_SIZE);

                        treeNode.removeAllChildren();

                        addLoadableObjectsToNode(0, objects, (heapDumpObject, index) -> {
                            if (heapDumpObject == null) {
                                return new DefaultMutableTreeNode(String.format("#%d <null>", index + 1));
                            } else if (heapDumpObject instanceof HeapDumpClassObject) {
                                HeapDumpClassObject classObject = (HeapDumpClassObject) heapDumpObject;
                                return new DefaultMutableTreeNode(String.format(
                                    "#%d <Class<%s>>", index + 1, classObject.getClassName()));
                            } else if (heapDumpObject instanceof HeapDumpClassInstance) {
                                return createClassInstanceTreeNode((HeapDumpClassInstance) heapDumpObject, index);
                            } else if (heapDumpObject instanceof HeapDumpArrayInstance) {
                                return createArrayTreeNode((HeapDumpArrayInstance) heapDumpObject, index);
                            } else {
                                throw new RuntimeException("unexpected object type");
                            }
                        }, treeNode);
                    }
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // TODO free objects?
            }
        });
    }

    private void showError(Throwable t, String s) {
        JOptionPane.showMessageDialog(MainViewerForm.this,
                                      String.format(s + "\n%s: %s", t.getClass().getSimpleName(), t.getMessage()),
                                      "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void fillTypeNodes() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Types");

        dumpTreeModel = new DefaultTreeModel(root);

        viewer.listTypes().forEach(heapDumpType -> root.add(createTypeTreeNode(heapDumpType)));

        dumpTree.setModel(dumpTreeModel);
    }

    private MutableTreeNode createTypeTreeNode(HeapDumpType heapDumpType) {
        TypeNodeData typeNodeData = new TypeNodeData(heapDumpType);

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(typeNodeData);
        node.add(new DefaultMutableTreeNode("loading..."));
        return node;
    }

    private MutableTreeNode createClassInstanceTreeNode(HeapDumpClassInstance instance, int index) {
        Map<String, String> fieldPreviews = instance.getInstanceFields().stream()
            .collect(Collectors.toMap(HeapDumpClassInstanceField::getFieldName, field -> {
                if (field instanceof HeapDumpClassInstancePrimitiveField) {
                    return ((HeapDumpClassInstancePrimitiveField) field).getValueAsString();
                } else { // HeapDumpClassInstanceObjectField
                    return "<" + ((HeapDumpClassInstanceObjectField) field).getTypeName() + ">";
                }
            }));

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new ClassInstanceNodeData(instance.getId(),
                                                                                           index,
                                                                                           fieldPreviews));
        node.setAllowsChildren(false);
        return node;
    }

    private MutableTreeNode createArrayTreeNode(HeapDumpArrayInstance instance, int index) {
        ArrayInstanceNodeData arrayNodeData =
            new ArrayInstanceNodeData(instance.getId(), index, instance instanceof HeapDumpPrimitiveArrayInstance);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(arrayNodeData);
        node.add(new DefaultMutableTreeNode("loading..."));
        return node;
    }

    private void addClassInstancesToTypeNode(int offset,
                                             List<HeapDumpClassInstance> instances,
                                             DefaultMutableTreeNode typeNode) {
        addLoadableObjectsToNode(offset, instances,
                                 this::createClassInstanceTreeNode,
                                 typeNode);
    }

    private void addArrayInstancesToTypeNode(int offset,
                                             List<HeapDumpArrayInstance> instances,
                                             DefaultMutableTreeNode typeNode) {
        addLoadableObjectsToNode(offset, instances,
                                 this::createArrayTreeNode,
                                 typeNode);
    }

    private <T>
    void addLoadableObjectsToNode(int offset, List<T> instances,
                                  BiFunction<T, Integer, MutableTreeNode> childNodeCreatorFunction,
                                  DefaultMutableTreeNode node) {
        for (int i = 0; i < instances.size(); i++) {
            node.add(childNodeCreatorFunction.apply(instances.get(i), offset + i));
        }

        if (instances.size() == CLASS_INSTANCES_BATCH_SIZE) {
            LoadMoreInstancesPlaceholder placeholder =
                new LoadMoreInstancesPlaceholder(offset + CLASS_INSTANCES_BATCH_SIZE);
            node.add(new DefaultMutableTreeNode(placeholder));
        } // else we have no more instances

        dumpTreeModel.reload(node);
    }

    private void clearObjectPropertiesTableAndShowHint() {
        TableModel tableModel = new DefaultTableModel(1, 1);
        tableModel.setValueAt("Select object to view its properties", 0, 0);
        objectPropertiesTable.setModel(tableModel);
    }

    private static class TypeNodeData {
        final HeapDumpType viewerType;

        private TypeNodeData(HeapDumpType viewerType) {
            this.viewerType = viewerType;
        }

        @Override
        public String toString() {
            return String.format("%s (%d)", viewerType.getName(), viewerType.getInstancesCount());
        }
    }

    private static class ClassInstanceNodeData {
        final long id;
        final int index;
        final Map<String, String> fieldPreviews;

        private ClassInstanceNodeData(long id, int index,
                                      Map<String, String> fieldPreviews) {
            this.id = id;
            this.index = index;
            this.fieldPreviews = fieldPreviews;
        }

        @Override
        public String toString() {
            return "#" + (index + 1);
        }
    }

    private static class ArrayInstanceNodeData {
        final long id;
        final int index;
        final boolean isPrimitive;

        private ArrayInstanceNodeData(long id, int index, boolean isPrimitive) {
            this.id = id;
            this.index = index;
            this.isPrimitive = isPrimitive;
        }

        @Override
        public String toString() {
            return "#" + (index + 1);
        }
    }

    private static class LoadMoreInstancesPlaceholder {
        final int offset;

        private LoadMoreInstancesPlaceholder(int offset) {
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "load more....";
        }
    }

    private static class NonEditableDefaultTableModel extends DefaultTableModel {

        public NonEditableDefaultTableModel(int rowCount, int columnCount) {
            super(rowCount, columnCount);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}
