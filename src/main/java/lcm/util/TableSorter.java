// code by lcm
package lcm.util;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/** TableSorter is a decorator for TableModels; adding sorting functionality to a
 * supplied TableModel. TableSorter does not store or copy the data in its
 * TableModel; instead it maintains a map from the row indexes of the view to
 * the row indexes of the model. As requests are made of the sorter (like
 * getValueAt(row, col)) they are passed to the underlying model after the row
 * numbers have been translated via the internal mapping array. This way, the
 * TableSorter appears to hold another copy of the table with the rows in a
 * different order.
 * <p/>
 * TableSorter registers itself as a listener to the underlying model, just as
 * the JTable itself would. Events recieved from the model are examined,
 * sometimes manipulated (typically widened), and then passed on to the
 * TableSorter's listeners (typically the JTable). If a change to the model has
 * invalidated the order of TableSorter's rows, a note of this is made and the
 * sorter will resort the rows the next time a value is requested.
 * <p/>
 * When the tableHeader property is set, either by using the setTableHeader()
 * method or the two argument constructor, the table header may be used as a
 * complete UI for TableSorter. The default renderer of the tableHeader is
 * decorated with a renderer that indicates the sorting status of each column.
 * In addition, a mouse listener is installed with the following behavior:
 * <ul>
 * <li>Mouse-click: Clears the sorting status of all other columns and advances
 * the sorting status of that column through three values: {NOT_SORTED,
 * ASCENDING, DESCENDING} (then back to NOT_SORTED again).
 * <li>SHIFT-mouse-click: Clears the sorting status of all other columns and
 * cycles the sorting status of the column through the same three values, in the
 * opposite order: {NOT_SORTED, DESCENDING, ASCENDING}.
 * <li>CONTROL-mouse-click and CONTROL-SHIFT-mouse-click: as above except that
 * the changes to the column do not cancel the statuses of columns that are
 * already sorting - giving a way to initiate a compound sort.
 * </ul>
 * <p/>
 * This is a long overdue rewrite of a class of the same name that first
 * appeared in the swing table demos in 1997.
 *
 * @author Philip Milne
 * @author Brendon McLean
 * @author Dan van Enckevort
 * @author Parwinder Sekhon
 * @version 2.0 02/27/04 */
public class TableSorter extends AbstractTableModel {
  public static final int DESCENDING = -1;
  public static final int NOT_SORTED = 0;
  public static final int ASCENDING = 1;
  private static final Directive EMPTY_DIRECTIVE = new Directive(-1, NOT_SORTED);
  @SuppressWarnings("rawtypes")
  public static final Comparator COMPARABLE_COMAPRATOR = new Comparator() {
    @Override
    @SuppressWarnings("unchecked")
    public int compare(Object o1, Object o2) {
      return ((Comparable) o1).compareTo(o2);
    }
  };
  @SuppressWarnings("rawtypes")
  public static final Comparator LEXICAL_COMPARATOR = new Comparator() {
    @Override
    public int compare(Object o1, Object o2) {
      return o1.toString().compareTo(o2.toString());
    }
  };
  // ---
  protected TableModel tableModel;
  private Row[] viewToModel;
  private int[] modelToView;
  private JTableHeader tableHeader;
  private final MouseListener mouseListener;
  private final TableModelListener tableModelListener;
  @SuppressWarnings("rawtypes")
  private Map columnComparators = new HashMap();
  @SuppressWarnings("rawtypes")
  private List sortingColumns = new ArrayList();

  public TableSorter() {
    mouseListener = new MouseHandler();
    tableModelListener = new TableModelHandler();
  }

  public TableSorter(TableModel tableModel) {
    this();
    setTableModel(tableModel);
  }

  public TableSorter(TableModel tableModel, JTableHeader tableHeader) {
    this();
    setTableHeader(tableHeader);
    setTableModel(tableModel);
  }

  private void clearSortingState() {
    viewToModel = null;
    modelToView = null;
  }

  public TableModel getTableModel() {
    return tableModel;
  }

  public synchronized void setTableModel(TableModel _tableModel) {
    if (tableModel != null)
      tableModel.removeTableModelListener(tableModelListener);
    tableModel = _tableModel;
    if (tableModel != null)
      tableModel.addTableModelListener(tableModelListener);
    clearSortingState();
    fireTableStructureChanged();
  }

  public synchronized JTableHeader getTableHeader() {
    return tableHeader;
  }

  public synchronized void setTableHeader(JTableHeader _tableHeader) {
    if (tableHeader != null) {
      tableHeader.removeMouseListener(mouseListener);
      TableCellRenderer defaultRenderer = tableHeader.getDefaultRenderer();
      if (defaultRenderer instanceof SortableHeaderRenderer) {
        tableHeader.setDefaultRenderer(((SortableHeaderRenderer) defaultRenderer).tableCellRenderer);
      }
    }
    tableHeader = _tableHeader;
    if (tableHeader != null) {
      tableHeader.addMouseListener(mouseListener);
      tableHeader.setDefaultRenderer(new SortableHeaderRenderer(tableHeader.getDefaultRenderer()));
    }
  }

  public synchronized boolean isSorting() {
    return sortingColumns.size() != 0;
  }

  private Directive getDirective(int column) {
    for (int i = 0; i < sortingColumns.size(); ++i) {
      Directive directive = (Directive) sortingColumns.get(i);
      if (directive.column == column)
        return directive;
    }
    return EMPTY_DIRECTIVE;
  }

  public synchronized int getSortingStatus(int column) {
    return getDirective(column).direction;
  }

  private void sortingStatusChanged() {
    clearSortingState();
    fireTableDataChanged();
    if (tableHeader != null)
      tableHeader.repaint();
  }

  @SuppressWarnings("unchecked")
  public synchronized void setSortingStatus(int column, int status) {
    Directive directive = getDirective(column);
    if (directive != EMPTY_DIRECTIVE)
      sortingColumns.remove(directive);
    if (status != NOT_SORTED)
      sortingColumns.add(new Directive(column, status));
    sortingStatusChanged();
  }

  protected Icon getHeaderRendererIcon(int column, int size) {
    Directive directive = getDirective(column);
    if (directive == EMPTY_DIRECTIVE)
      return null;
    return new Arrow(directive.direction == DESCENDING, size, sortingColumns.indexOf(directive));
  }

  private void cancelSorting() {
    sortingColumns.clear();
    sortingStatusChanged();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  public synchronized void setColumnComparator(Class type, Comparator comparator) {
    if (comparator == null) {
      columnComparators.remove(type);
    } else {
      columnComparators.put(type, comparator);
    }
  }

  @SuppressWarnings("rawtypes")
  protected Comparator getComparator(int column) {
    Class columnType = tableModel.getColumnClass(column);
    Comparator comparator = (Comparator) columnComparators.get(columnType);
    if (comparator != null) {
      return comparator;
    }
    if (Comparable.class.isAssignableFrom(columnType)) {
      return COMPARABLE_COMAPRATOR;
    }
    return LEXICAL_COMPARATOR;
  }

  private synchronized Row[] getViewToModel() {
    if (viewToModel == null) {
      int tableModelRowCount = tableModel.getRowCount();
      viewToModel = new Row[tableModelRowCount];
      for (int row = 0; row < tableModelRowCount; row++) {
        viewToModel[row] = new Row(row);
      }
      if (isSorting()) {
        Arrays.sort(viewToModel);
      }
    }
    return viewToModel;
  }

  public synchronized int modelIndex(int viewIndex) {
    return getViewToModel()[viewIndex].modelIndex;
  }

  private int[] getModelToView() {
    if (modelToView == null) {
      int n = getViewToModel().length;
      modelToView = new int[n];
      for (int i = 0; i < n; i++) {
        modelToView[modelIndex(i)] = i;
      }
    }
    return modelToView;
  }

  // TableModel interface methods
  @Override
  public synchronized int getRowCount() {
    return tableModel == null ? 0 : tableModel.getRowCount();
  }

  @Override
  public synchronized int getColumnCount() {
    return tableModel == null ? 0 : tableModel.getColumnCount();
  }

  @Override
  public synchronized String getColumnName(int column) {
    return tableModel.getColumnName(column);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public synchronized Class getColumnClass(int column) {
    return tableModel.getColumnClass(column);
  }

  @Override
  public synchronized boolean isCellEditable(int row, int column) {
    return tableModel.isCellEditable(modelIndex(row), column);
  }

  @Override
  public synchronized Object getValueAt(int row, int column) {
    return tableModel.getValueAt(modelIndex(row), column);
  }

  @Override
  public synchronized void setValueAt(Object aValue, int row, int column) {
    tableModel.setValueAt(aValue, modelIndex(row), column);
  }

  // Helper classes
  @SuppressWarnings("rawtypes")
  private class Row implements Comparable {
    private final int modelIndex;

    public Row(int index) {
      this.modelIndex = index;
    }

    @Override
    @SuppressWarnings("unchecked")
    public int compareTo(Object o) {
      int row1 = modelIndex;
      int row2 = ((Row) o).modelIndex;
      for (Iterator it = sortingColumns.iterator(); it.hasNext();) {
        Directive directive = (Directive) it.next();
        int column = directive.column;
        Object o1 = tableModel.getValueAt(row1, column);
        Object o2 = tableModel.getValueAt(row2, column);
        int comparison = 0;
        // Define null less than everything, except null.
        if (o1 == null && o2 == null) {
          comparison = 0;
        } else if (o1 == null) {
          comparison = -1;
        } else if (o2 == null) {
          comparison = 1;
        } else {
          comparison = getComparator(column).compare(o1, o2);
        }
        if (comparison != 0) {
          return directive.direction == DESCENDING ? -comparison : comparison;
        }
      }
      return 0;
    }
  }

  private class TableModelHandler implements TableModelListener {
    @Override
    public void tableChanged(TableModelEvent e) {
      synchronized (TableSorter.this) {
        // If we're not sorting by anything, just pass the event along.
        if (!isSorting()) {
          clearSortingState();
          fireTableChanged(e);
          return;
        }
        // If the table structure has changed, cancel the sorting; the
        // sorting columns may have been either moved or deleted from
        // the model.
        if (e.getFirstRow() == TableModelEvent.HEADER_ROW) {
          cancelSorting();
          fireTableChanged(e);
          return;
        }
        // We can map a cell event through to the view without widening
        // when the following conditions apply:
        //
        // a) all the changes are on one row (e.getFirstRow() ==
        // e.getLastRow()) and,
        // b) all the changes are in one column (column !=
        // TableModelEvent.ALL_COLUMNS) and,
        // c) we are not sorting on that column
        // (getSortingStatus(column) == NOT_SORTED) and,
        // d) a reverse lookup will not trigger a sort (modelToView !=
        // null)
        //
        // Note: INSERT and DELETE events fail this test as they have
        // column == ALL_COLUMNS.
        //
        // The last check, for (modelToView != null) is to see if
        // modelToView
        // is already allocated. If we don't do this check; sorting can
        // become
        // a performance bottleneck for applications where cells
        // change rapidly in different parts of the table. If cells
        // change alternately in the sorting column and then outside of
        // it this class can end up re-sorting on alternate cell updates
        // -
        // which can be a performance problem for large tables. The last
        // clause avoids this problem.
        int column = e.getColumn();
        if (e.getFirstRow() == e.getLastRow() && column != TableModelEvent.ALL_COLUMNS && getSortingStatus(column) == NOT_SORTED && modelToView != null) {
          int viewIndex = getModelToView()[e.getFirstRow()];
          fireTableChanged(new TableModelEvent(TableSorter.this, viewIndex, viewIndex, column, e.getType()));
          return;
        }
        // Something has happened to the data that may have invalidated
        // the row order.
        clearSortingState();
        fireTableDataChanged();
        return;
      }
    }
  }

  private class MouseHandler extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      JTableHeader h = (JTableHeader) e.getSource();
      TableColumnModel columnModel = h.getColumnModel();
      int viewColumn = columnModel.getColumnIndexAtX(e.getX());
      int column = columnModel.getColumn(viewColumn).getModelIndex();
      if (column != -1) {
        int status = getSortingStatus(column);
        if (!e.isControlDown()) {
          cancelSorting();
        }
        // Cycle the sorting states through {NOT_SORTED, ASCENDING,
        // DESCENDING} or
        // {NOT_SORTED, DESCENDING, ASCENDING} depending on whether
        // shift is pressed.
        status = status + (e.isShiftDown() ? -1 : 1);
        status = (status + 4) % 3 - 1; // signed mod, returning {-1, 0,
                                       // 1}
        setSortingStatus(column, status);
      }
    }
  }

  private class SortableHeaderRenderer implements TableCellRenderer {
    private TableCellRenderer tableCellRenderer;

    public SortableHeaderRenderer(TableCellRenderer tableCellRenderer) {
      this.tableCellRenderer = tableCellRenderer;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c = tableCellRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      if (c instanceof JLabel) {
        JLabel l = (JLabel) c;
        l.setHorizontalTextPosition(JLabel.LEFT);
        int modelColumn = table.convertColumnIndexToModel(column);
        l.setIcon(getHeaderRendererIcon(modelColumn, l.getFont().getSize()));
      }
      return c;
    }
  }

  private static class Directive {
    private final int column;
    private final int direction;

    public Directive(int column, int direction) {
      this.column = column;
      this.direction = direction;
    }
  }
}
