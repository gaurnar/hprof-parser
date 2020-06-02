package edu.tufts.eaftan.hprofparser.viewer.gui;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClass;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstance;
import java.io.File;
import java.util.List;
import java.util.Map;
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
                        fillClassNodes();
                    } catch (Throwable t) {
                        JOptionPane.showMessageDialog(MainViewerForm.this,
                                                      String.format("Failed to load dump!\n%s: %s",
                                                                    t.getClass().getSimpleName(), t.getMessage()),
                                                      "Error", JOptionPane.ERROR_MESSAGE);
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
            if (treeNode.getUserObject() instanceof InstanceNodeData) {
                InstanceNodeData instanceData = (InstanceNodeData) treeNode.getUserObject();

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

                    SwingUtilities.invokeLater(() -> {
                        try {
                            List<HeapDumpClassInstance> instances =
                                viewer.listClassInstances(placeholder.typeId, placeholder.offset,
                                                          CLASS_INSTANCES_BATCH_SIZE);

                            treeNode.removeFromParent();

                            addInstancesToTypeNode(placeholder.offset, instances, parentNode);
                        } catch (Throwable t) {
                            JOptionPane.showMessageDialog(MainViewerForm.this,
                                                          String.format("Failed to list children!\n%s: %s",
                                                                        t.getClass().getSimpleName(),
                                                                        t.getMessage()),
                                                          "Error", JOptionPane.ERROR_MESSAGE);
                            t.printStackTrace();
                        }
                    });
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
                    SwingUtilities.invokeLater(() -> {
                        try {
                            List<HeapDumpClassInstance> instances =
                                viewer.listClassInstances(typeData.typeId, 0, CLASS_INSTANCES_BATCH_SIZE);

                            treeNode.removeAllChildren();

                            addInstancesToTypeNode(0, instances, treeNode);
                        } catch (Throwable t) {
                            JOptionPane.showMessageDialog(MainViewerForm.this,
                                                          String.format("Failed to list children!\n%s: %s",
                                                                        t.getClass().getSimpleName(),
                                                                        t.getMessage()),
                                                          "Error", JOptionPane.ERROR_MESSAGE);
                            t.printStackTrace();
                        }
                    });
                }
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
                // TODO free objects?
            }
        });
    }

    private void fillClassNodes() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Types");

        dumpTreeModel = new DefaultTreeModel(root);

        viewer.listClasses().forEach(heapDumpClass -> root.add(createTypeTreeNode(heapDumpClass)));

        dumpTree.setModel(dumpTreeModel);
    }

    private MutableTreeNode createTypeTreeNode(HeapDumpClass heapDumpClass) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new TypeNodeData(heapDumpClass.getName(),
                                                                                  heapDumpClass.getId(),
                                                                                  heapDumpClass.getInstancesCount()));
        node.add(new DefaultMutableTreeNode("loading..."));
        return node;
    }

    private MutableTreeNode createInstanceTreeNode(HeapDumpClassInstance instance, int index) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new InstanceNodeData(-1, // TODO
                                                                                      index,
                                                                                      instance.getFieldValuePreviews()));
        node.setAllowsChildren(false);
        return node;
    }

    private void addInstancesToTypeNode(int offset,
                                        List<HeapDumpClassInstance> instances,
                                        DefaultMutableTreeNode typeNode) {
        TypeNodeData typeData = (TypeNodeData) typeNode.getUserObject();

        for (int i = 0; i < instances.size(); i++) {
            typeNode.add(createInstanceTreeNode(instances.get(i), offset + i));
        }

        if (instances.size() == CLASS_INSTANCES_BATCH_SIZE) {
            typeNode.add(
                new DefaultMutableTreeNode(new LoadMoreInstancesPlaceholder(typeData.typeId,
                                                                            offset + CLASS_INSTANCES_BATCH_SIZE)));
        } // else we have no more instances

        dumpTreeModel.reload(typeNode);
    }

    private void clearObjectPropertiesTableAndShowHint() {
        TableModel tableModel = new DefaultTableModel(1, 1);
        tableModel.setValueAt("Select object to view its properties", 0, 0);
        objectPropertiesTable.setModel(tableModel);
    }

    private static class TypeNodeData {
        final long typeId;
        final String typeName;
        final int instancesCount;

        private TypeNodeData(String typeName, long typeId, int instancesCount) {
            this.typeId = typeId;
            this.typeName = typeName;
            this.instancesCount = instancesCount;
        }

        @Override
        public String toString() {
            return String.format("%s (%d)", typeName, instancesCount);
        }
    }

    private static class InstanceNodeData {
        final long id;
        final int index;
        final Map<String, String> fieldPreviews;

        private InstanceNodeData(long id, int index,
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

    private static class LoadMoreInstancesPlaceholder {
        final long typeId;
        final int offset;

        private LoadMoreInstancesPlaceholder(long typeId, int offset) {
            this.typeId = typeId;
            this.offset = offset;
        }

        @Override
        public String toString() {
            return "load more instances....";
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
