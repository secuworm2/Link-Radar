package com.secuworm.endpointcollector.presentation;

import com.secuworm.endpointcollector.domain.EndpointRecord;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class EndpointTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = new String[]{"Endpoint", "Host", "Source URL", "Count"};
    private List<EndpointRecord> rows = new ArrayList<>();

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        EndpointRecord row = rows.get(rowIndex);
        if (columnIndex == 0) {
            return row.getEndpointUrl();
        }
        if (columnIndex == 1) {
            return row.getHost();
        }
        if (columnIndex == 2) {
            return row.getSourceUrl();
        }
        if (columnIndex == 3) {
            return row.getCount();
        }
        return "";
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    public void setRecords(List<EndpointRecord> records) {
        this.rows = records == null ? new ArrayList<>() : new ArrayList<>(records);
        fireTableDataChanged();
    }

    public void clear() {
        this.rows = new ArrayList<>();
        fireTableDataChanged();
    }

    public EndpointRecord getRecord(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return null;
        }
        return rows.get(rowIndex);
    }
}
