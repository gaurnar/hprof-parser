package edu.tufts.eaftan.hprofparser.viewer.gui.util;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstanceObjectField;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstancePrimitiveField;

public final class ObjectViewUtil {

    private ObjectViewUtil() {
        throw new UnsupportedOperationException();
    }

    public static String showFieldValue(HeapDumpClassInstanceField field) {
        if (field instanceof HeapDumpClassInstancePrimitiveField) {
            return ((HeapDumpClassInstancePrimitiveField) field).getValueAsString();
        } else { // HeapDumpClassInstanceObjectField
            return "<" + ((HeapDumpClassInstanceObjectField) field).getTypeName() + ">";
        }
    }

}
